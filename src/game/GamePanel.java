package game;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public class GamePanel extends JPanel implements KeyListener {
    // ===== настройки =====
    private static final int    SPEED           = 3;      // скорость ходьбы
    private static final int    ATTACK_COOLDOWN = 24;     // пауза между взмахами (кадры)
    private static final double CAM_SMOOTH      = 0.12;   // плавность камеры
    private static final int    SLOT = 72, GAP = 12;      // размеры слотов инвентаря
    private static final Path   BOOK_FILE = Path.of("book.txt");
    private static final Random RND = new Random();

    // физика для комнат с видом сбоку
    private static final double GRAVITY = 0.5, JUMP = 11.0, MAX_FALL = 12.0;

    private static final int CHECK_TIME = 45;             // сколько кадров занимает безопасная проверка вазы

    // Семь грехов: где в коридоре дверь каждого греха и куда возвращаться из комнаты
    private static final int[] HALL_COL = {6, 15, 24, 33, 10, 19, 28};
    private static final int[] HALL_ROW = {4, 4, 4, 4, 7, 7, 7};          // клетка под/над дверью
    private static final Theme[] SIN_THEMES = {Theme.PRIDE, Theme.GREED, Theme.WRATH, Theme.ENVY,
            Theme.LUST, Theme.GLUTTONY, Theme.SLOTH};
    private static final String[] SIN_NAMES = {"Гордыня", "Жадность", "Гнев", "Зависть",
            "Похоть", "Чревоугодие", "Лень"};

    private final GameWindow window;
    private final Map<String, Room> rooms = new HashMap<>();
    private final Set<String> done = new HashSet<>();     // пройденные испытания
    private Room current;
    private final Player player = new Player();
    private Cat pet;                                      // кот, которого ты спас
    private int tick;
    private final Timer timer = new Timer(16, e -> { tick++; update(); repaint(); });

    private boolean up, down, left, right, jump;
    private int respawnCol, respawnRow;                   // куда вернёт после потери сознания
    private int safeX, safeY;                             // последнее безопасное место (после падения в яму)
    private double camX, camY;

    // эффекты (в кадрах)
    private int slowTimer, poisonTimer, darkTimer, weakTimer;
    private boolean slowPermanent;                        // после убийства кота

    // проверка вазы
    private int checkTimer;
    private Vase checkVase;

    private String message = "";
    private int messageTimer;
    private int titleTimer;                               // заставка с названием комнаты
    private boolean won;                                  // прошёл всю игру

    private Inventory inventory = new Inventory();
    private Item equipped;
    private boolean invOpen;
    private int selected;

    public GamePanel(GameWindow window) {
        this.window = window;
        setPreferredSize(new Dimension(Room.VIEW_W, Room.VIEW_H));
        setFocusable(true);
        setFocusTraversalKeysEnabled(false);
        addKeyListener(this);

        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                requestFocusInWindow();
                if (!invOpen) return;                             // удар мечом теперь только на L
                for (int i = 0; i < Inventory.SLOTS; i++) {       // клик по слоту в инвентаре
                    if (slotRect(i).contains(e.getPoint())) {
                        if (selected == i) useSelected();
                        else selected = i;
                        return;
                    }
                }
            }
        });
    }

    // ====== КОМНАТЫ ======
    private void add(String key, Room r) {
        r.key = key;
        rooms.put(key, r);
    }

    // --- помощники для карт ---
    private void pit(MapBuilder b, int c0, int c1)              { b.rect(c0, 10, c1, 11, 'P'); }   // яма в полу
    private void plat(MapBuilder b, int c0, int c1, int row)    { b.rect(c0, row, c1, row, '#'); } // платформа/блок
    private void gate(MapBuilder b, int col)                    { b.rect(col, 1, col, 9, 'G'); }   // ворота испытания

    // --- помощники для ловушек ---
    private void hidden(Room r, int... cols)    { for (int c : cols) r.traps.add(Trap.hidden(c, 9)); }
    private void flame(Room r, int col, int ph) { r.traps.add(Trap.flame(col, 9, ph)); }
    private void saw(Room r, int c0, int c1)    { r.traps.add(Trap.saw(c0, c1, 9)); }
    private void crumble(Room r, int c0, int c1, int row) {
        for (int c = c0; c <= c1; c++) r.traps.add(Trap.crumble(c, row));
    }

    // Комната-грех с видом сбоку: слева зона испытания, потом ворота 'G', за ними дорога ловушек к выходу '2'.
    private Room sin(int idx, MapBuilder b, String sub, Room.Goal goal, String label) {
        String name = SIN_NAMES[idx - 1];
        Room r = new Room(idx + ". " + name, b.build());
        r.sideView = true;
        r.theme = SIN_THEMES[idx - 1];
        r.title = name.toUpperCase();
        r.subtitle = sub;
        r.goal = goal;
        r.goalLabel = label;
        r.exitChar = '2';
        r.exitLocked = goal != Room.Goal.NONE;
        r.addDoor('1', "hall", HALL_COL[idx - 1], HALL_ROW[idx - 1]);
        r.addDoor('2', "hall", HALL_COL[idx - 1], HALL_ROW[idx - 1]);
        return r;
    }

    // Обозначения карт: # стена/платформа, ^ шипы, P яма, G ворота, V ваза, C кот, T тотем,
    //   K корона, R роза, A угощение, Z сфера сна, 1/2 двери.
    // Пол — ряд 9 (по нему ходит игрок), пол-стена — ряд 10. Блок в ряду 9 = препятствие в 1 клетку,
    // платформа в ряду 8 = 2 клетки (предел прыжка), поэтому лестницы делаем шагами по 1 клетке.
    // Обычные ямы делаем шириной 1 клетка (их легко перепрыгнуть даже при замедлении), широкие — только с хрупким мостом.
    private void buildRooms() {
        // ---------- ОБУЧЕНИЕ ----------
        MapBuilder tb = MapBuilder.side(44);
        tb.set(1, 9, '.');                        // назад дороги нет
        tb.set(9, 9, '#');                        // блок в 1 клетку
        pit(tb, 14, 14);                          // яма в 1 клетку
        tb.set(19, 9, '^');                       // шипы
        pit(tb, 29, 31);                          // яма под хрупким мостом
        tb.rect(35, 9, 37, 9, 'V');               // три вазы
        Room tut = new Room("Обучение", tb.build());
        tut.sideView = true;
        tut.theme = Theme.TUTORIAL;
        tut.title = "ОБУЧЕНИЕ";
        tut.subtitle = "Научись ходить, прыгать, избегать ловушек и бить мечом";
        tut.goal = Room.Goal.CANDLE;
        tut.goalLabel = "найди свечу";
        tut.exitChar = '2';
        tut.exitLocked = true;
        tut.addDoor('2', "hall", 3, 5);
        tut.traps.add(Trap.hidden(24, 9));
        crumble(tut, 29, 31, 10);
        tut.addSign(4, "A / D или стрелки — ходьба");
        tut.addSign(7, "ПРОБЕЛ — прыжок. Перепрыгни блок впереди");
        tut.addSign(12, "Яма! Упадёшь — потеряешь сердце. Прыгай через неё");
        tut.addSign(17, "Шипы ранят — перепрыгивай их");
        tut.addSign(22, "Ловушки бывают скрытыми: следи за трещинами в полу");
        tut.addSign(27, "Хрупкий мост рушится под ногами — не задерживайся!");
        tut.addSign(33, "E — безопасно проверить вазу, L — ударить мечом и разбить");
        tut.addSign(40, "Свеча откроет выход. В обучении вазы штрафов не дают");
        add("tutorial", tut);

        // ---------- КОРИДОР 50 x 12: 7 дверей грехов, в конце — закрытая дверь 8 ----------
        MapBuilder hb = new MapBuilder(50, 12, '#');
        hb.rect(1, 4, 48, 7, '.');
        for (int i = 0; i < 7; i++) hb.set(HALL_COL[i], i < 4 ? 3 : 8, (char) ('1' + i));
        hb.set(49, 6, '8');
        Room hall = new Room("Коридор", hb.build());
        hall.theme = Theme.HALL;
        hall.title = "ХРАМ СЕМИ ГРЕХОВ";
        hall.subtitle = "Выбери дверь. Каждая ведёт к одному из грехов";
        for (int i = 0; i < 7; i++) {
            char c = (char) ('1' + i);
            hall.addDoor(c, "s" + (i + 1), 3, 9);
            hall.doorTint.put(c, SIN_THEMES[i].base);
            hall.doorLabel.put(c, SIN_NAMES[i].toUpperCase());
        }
        hall.addDoor('8', "END", 0, 0);
        hall.doorTint.put('8', new Color(240, 200, 60));
        hall.doorLabel.put('8', "ВЫХОД");
        hall.exitChar = '8';
        hall.exitLocked = true;
        add("hall", hall);

        // ---------- 1. ГОРДЫНЯ (синий + золото): взойди на башню и возьми корону ----------
        MapBuilder b1 = MapBuilder.side(64);
        plat(b1, 8, 9, 9);
        plat(b1, 11, 12, 8);
        plat(b1, 14, 15, 7);
        plat(b1, 17, 18, 6);                      // выше — плиты хрупкие (гордыня падает)
        b1.set(27, 2, 'K');
        gate(b1, 38);
        pit(b1, 45, 45);
        b1.set(58, 9, '^');
        Room r1 = sin(1, b1, "Взойди на башню и возьми корону. Верхние плиты хрупкие", Room.Goal.COLLECT, "возьми корону");
        crumble(r1, 20, 21, 5);
        crumble(r1, 23, 24, 4);
        crumble(r1, 26, 27, 3);
        hidden(r1, 42, 50, 60);
        saw(r1, 52, 56);
        add("s1", r1);

        // ---------- 2. ЖАДНОСТЬ (золотой): найди свечу среди ваз ----------
        MapBuilder b2 = MapBuilder.side(60);
        b2.rect(11, 9, 18, 9, 'V');
        b2.rect(12, 8, 17, 8, 'V');
        gate(b2, 34);
        b2.set(47, 9, '^');
        pit(b2, 55, 55);
        Room r2 = sin(2, b2, "Найди свечу среди ваз (E). Разбитая ваза проклинает", Room.Goal.CANDLE, "найди свечу среди ваз");
        hidden(r2, 38, 43);
        saw(r2, 49, 53);
        add("s2", r2);

        // ---------- 3. ГНЕВ (красный): разбей тотемы ярости ----------
        MapBuilder b3 = MapBuilder.side(66);
        b3.set(10, 9, 'T').set(18, 9, 'T').set(26, 9, 'T');
        plat(b3, 28, 29, 9);
        plat(b3, 31, 33, 8);
        b3.set(32, 7, 'T');
        gate(b3, 40);
        pit(b3, 54, 54);
        b3.set(61, 9, '^');
        Room r3 = sin(3, b3, "Разбей 4 тотема мечом (L) — по 3 удара на каждый", Room.Goal.TOTEMS, "разбей тотемы");
        flame(r3, 13, 0);
        flame(r3, 22, 90);
        flame(r3, 44, 0);
        hidden(r3, 47);
        flame(r3, 51, 70);
        flame(r3, 58, 140);
        add("s3", r3);

        // ---------- 4. ЗАВИСТЬ (зелёный): кот — спаси или отними ----------
        MapBuilder b4 = MapBuilder.side(64);
        b4.set(12, 9, '^');
        pit(b4, 15, 15);
        plat(b4, 27, 31, 9);                      // возвышение, на котором сидит кот
        b4.set(29, 8, 'C');
        gate(b4, 38);
        pit(b4, 41, 45);                          // пропасть под хрупким мостом
        pit(b4, 57, 57);
        Room r4 = sin(4, b4, "У кота есть то, чего нет у тебя. Спаси его (E) или отними силу (L)", Room.Goal.CAT, "реши судьбу кота");
        hidden(r4, 8, 20, 24);
        crumble(r4, 41, 45, 10);
        hidden(r4, 49);
        saw(r4, 51, 55);
        add("s4", r4);

        // ---------- 5. ПОХОТЬ (розовый): три розы там, где опаснее всего ----------
        MapBuilder b5 = MapBuilder.side(66);
        b5.rect(10, 9, 11, 9, '^');
        b5.set(12, 9, 'R');                       // роза 1: между двумя рядами шипов
        b5.rect(13, 9, 14, 9, '^');
        pit(b5, 24, 27);
        b5.set(26, 9, 'R');                       // роза 2: на хрупком мосту
        plat(b5, 30, 30, 9);
        plat(b5, 32, 33, 8);
        plat(b5, 35, 36, 7);
        b5.set(36, 6, 'R');                       // роза 3: на верхней ступени
        gate(b5, 39);
        pit(b5, 50, 50);
        Room r5 = sin(5, b5, "Соблазн ведёт в опасные места. Собери 3 розы", Room.Goal.COLLECT, "собери розы");
        crumble(r5, 24, 27, 10);
        flame(r5, 43, 0);
        hidden(r5, 47);
        saw(r5, 54, 58);
        flame(r5, 61, 100);
        add("s5", r5);

        // ---------- 6. ЧРЕВОУГОДИЕ (оранжевый): съешь всё угощение ----------
        MapBuilder b6 = MapBuilder.side(64);
        b6.set(8, 9, 'A');
        b6.set(10, 9, '^');
        b6.set(12, 9, 'A');
        plat(b6, 14, 14, 9);
        plat(b6, 16, 18, 8);
        b6.set(17, 7, 'A');
        b6.set(22, 9, 'A');
        pit(b6, 24, 24);
        b6.set(27, 9, 'A');
        b6.set(33, 9, 'A');
        gate(b6, 37);
        pit(b6, 49, 49);
        b6.set(56, 9, '^');
        Room r6 = sin(6, b6, "Съешь всё угощение. Каждый кусок замедляет тебя", Room.Goal.COLLECT, "съешь угощение");
        hidden(r6, 30, 40, 53);
        saw(r6, 42, 46);
        flame(r6, 59, 40);
        add("s6", r6);

        // ---------- 7. ЛЕНЬ (серо-голубой): течение сна тянет назад, собери 3 сферы ----------
        MapBuilder b7 = MapBuilder.side(66);
        b7.set(8, 9, '^');
        b7.set(12, 9, 'Z');
        plat(b7, 15, 15, 9);
        pit(b7, 18, 18);
        b7.set(24, 9, 'Z');
        plat(b7, 28, 28, 9);
        plat(b7, 30, 31, 8);
        b7.set(31, 7, 'Z');
        gate(b7, 36);
        pit(b7, 43, 43);
        b7.set(59, 9, '^');
        Room r7 = sin(7, b7, "Течение сна тянет назад. Собери 3 сферы снов", Room.Goal.COLLECT, "собери сферы снов");
        r7.wind = 1;
        hidden(r7, 22, 47);
        flame(r7, 39, 0);
        saw(r7, 49, 53);
        flame(r7, 56, 90);
        add("s7", r7);
    }

    // ====== ЭФФЕКТЫ ======
    private int curSpeed() {
        int s = SPEED;
        if (slowTimer > 0) s--;
        if (slowPermanent) s--;
        return Math.max(2, s);                                // не медленнее 2, чтобы ловушки оставались проходимыми
    }

    private String applyCurse() {
        switch (RND.nextInt(4)) {
            case 0:  slowTimer = 600;   return "Проклятие: замедление (10 с)";
            case 1:  poisonTimer = 450; return "Яд: ты теряешь здоровье (7 с)";
            case 2:  darkTimer = 600;   return "Тьма: почти ничего не видно (10 с)";
            default: weakTimer = 600;   return "Слабость: урон меньше на 1 (10 с)";
        }
    }

    // ====== ВАЗЫ ======
    private Vase nearVase() {
        Vase best = null;
        double bd = 72;
        for (Vase v : current.vases) {
            if (v.state != Vase.State.INTACT) continue;
            double d = Math.hypot(v.centerX() - player.centerX(), v.centerY() - player.centerY());
            if (d < bd) { bd = d; best = v; }
        }
        return best;
    }

    private void smashVase(Vase v) {
        v.state = Vase.State.BROKEN;
        boolean training = "tutorial".equals(current.key);   // в обучении штрафа нет
        String curse = training ? "В обучении штрафа нет, но дальше он будет!" : applyCurse();
        if (v.hasCandle) {
            giveCandle();
            showMessage("Ваза разбита, свеча найдена! " + (training ? curse : "Но: " + curse));
        } else {
            showMessage("Ваза пуста. " + curse);
        }
    }

    private void finishCheck() {
        Vase v = checkVase;
        checkVase = null;
        if (v == null || v.state != Vase.State.INTACT) return;
        v.state = Vase.State.CHECKED;
        if (v.hasCandle) giveCandle();
        else showMessage("В вазе пусто");
    }

    private void giveCandle() {
        inventory.add(new Item(Item.Type.CANDLE, "Свеча", "Найдена среди ваз"));
        unlockExit("Ты нашёл свечу!");
    }

    // открывает выход; если в комнате есть ворота — следующая смерть вернёт не к началу, а за ворота
    private void unlockExit(String prefix) {
        current.exitLocked = false;
        String open;
        if (current.gateCol >= 0) {
            respawnCol = current.gateCol + 1;
            respawnRow = 9;
            open = "Ворота открыты — теперь пройди дорогу ловушек";
        } else {
            open = "Выход справа открыт";
        }
        if (prefix != null) showMessage(prefix + " " + open);
    }

    // ====== ИНВЕНТАРЬ ======
    private void resetInventory() {
        inventory = new Inventory();
        Item sword = Item.sword("Меч", "Урон 2. Твой первый меч", 2, new Color(210, 220, 235));
        Item book = new Item(Item.Type.BOOK, "Книга", "Enter — открыть и записать инструкцию");
        book.text = loadBookText();
        inventory.add(sword);
        inventory.add(new Item(Item.Type.COIN, "Монетка", "Твоя монетка"));
        inventory.add(book);
        equip(sword);
        selected = 0;
    }

    private void equip(Item sword) {
        equipped = sword;
        player.blade = sword.blade;
    }

    private Rectangle slotRect(int i) {
        int total = Inventory.SLOTS * SLOT + (Inventory.SLOTS - 1) * GAP;
        int x0 = (Room.VIEW_W - total) / 2;
        return new Rectangle(x0 + i * (SLOT + GAP), Room.VIEW_H / 2 - SLOT / 2, SLOT, SLOT);
    }

    private void openInventory() {
        invOpen = true;
        up = down = left = right = jump = false;
        player.moving = false;
    }

    private void useSelected() {
        Item it = inventory.get(selected);
        if (it == null) return;
        if (it.type == Item.Type.BOOK) {
            editBook(it);
        } else if (it.type == Item.Type.SWORD) {
            equip(it);
            showMessage("В руке: " + it.name + " (урон " + it.damage + ")");
        }
    }

    private void editBook(Item book) {
        JTextArea area = new JTextArea(book.text, 12, 34);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setFont(new Font("Monospaced", Font.PLAIN, 16));

        String[] buttons = {"Сохранить", "Закрыть"};
        JOptionPane pane = new JOptionPane(new JScrollPane(area), JOptionPane.PLAIN_MESSAGE,
                JOptionPane.DEFAULT_OPTION, null, buttons, buttons[0]);
        JDialog dialog = pane.createDialog(window, "Книга — запиши сюда инструкцию");
        dialog.addWindowFocusListener(new WindowAdapter() {
            @Override
            public void windowGainedFocus(WindowEvent e) { area.requestFocusInWindow(); }
        });
        dialog.setVisible(true);

        if (buttons[0].equals(pane.getValue())) {
            book.text = area.getText();
            saveBookText(book.text);
        }
        up = down = left = right = jump = false;
        requestFocusInWindow();
    }

    private String loadBookText() {
        try {
            return Files.exists(BOOK_FILE) ? Files.readString(BOOK_FILE) : "";
        } catch (IOException e) {
            return "";
        }
    }

    private void saveBookText(String text) {
        try {
            Files.writeString(BOOK_FILE, text);
        } catch (IOException e) {
            System.err.println("Не удалось сохранить книгу: " + e.getMessage());
        }
    }

    private void showMessage(String text) {
        message = text;
        messageTimer = 300;
    }

    // ================= СТАРТ / СТОП =================
    public void start() {                       // каждый запуск из меню = новая игра
        rooms.clear();
        done.clear();
        buildRooms();
        pet = null;
        slowTimer = poisonTimer = darkTimer = weakTimer = 0;
        slowPermanent = false;
        checkTimer = 0;
        checkVase = null;
        won = false;
        respawnCol = 3;
        respawnRow = 9;
        enterRoom(rooms.get("tutorial"));
        player.setTile(respawnCol, respawnRow);
        player.hp = player.maxHp;
        player.facing = Player.Dir.RIGHT;
        player.invul = 0;
        player.attackTimer = 0;
        player.attackCooldown = 0;
        player.vy = 0;
        player.onGround = false;
        safeX = player.x;
        safeY = player.y;
        resetInventory();
        invOpen = false;
        snapCamera();
        showMessage("Обучение: иди вправо и читай таблички. Прыжок — ПРОБЕЛ, меч — L");
        timer.start();
    }

    public void stop() {
        timer.stop();
        up = down = left = right = jump = false;
        invOpen = false;
    }

    private void enterRoom(Room r) {
        current = r;
        titleTimer = r.title != null ? 170 : 0;
    }

    // ================= ЛОГИКА =================
    private void update() {
        if (messageTimer > 0) messageTimer--;
        if (titleTimer > 0) titleTimer--;
        if (invOpen || won) return;                              // в инвентаре мир стоит

        // ----- эффекты -----
        if (slowTimer > 0) slowTimer--;
        if (darkTimer > 0) darkTimer--;
        if (weakTimer > 0) weakTimer--;
        if (poisonTimer > 0) {
            poisonTimer--;
            if (poisonTimer % 90 == 0 && player.hp > 1) player.hp--;    // яд не убивает, оставляет 1 сердце
        }

        boolean checking = checkTimer > 0;
        int speed = curSpeed();
        int dx = checking ? 0 : (right ? speed : 0) - (left ? speed : 0);

        if (current.sideView) {
            // ----- вид сбоку: лево/право + гравитация, ПРОБЕЛ = прыжок -----
            player.moving = dx != 0;
            if (player.attackTimer == 0) {
                if (dx > 0) player.facing = Player.Dir.RIGHT;
                else if (dx < 0) player.facing = Player.Dir.LEFT;
                else if (player.facing == Player.Dir.UP || player.facing == Player.Dir.DOWN) player.facing = Player.Dir.RIGHT;
            }
            if (jump && player.onGround && !checking) {
                player.vy = -JUMP;
                player.onGround = false;
            }
            player.vy = Math.min(player.vy + GRAVITY, MAX_FALL);

            int wantY = (int) Math.round(player.vy);
            int oldY = player.y;
            player.move(current, dx, wantY);
            if (player.y - oldY != wantY) {          // упёрлись в пол или потолок
                player.onGround = wantY > 0;
                player.vy = 0;
            } else {
                player.onGround = false;
            }
            if (current.wind != 0 && tick % 2 == 0) player.move(current, -current.wind, 0);   // течение сна (Лень)
        } else {
            // ----- вид сверху: 4 направления + диагонали -----
            int dy = checking ? 0 : (down ? speed : 0) - (up ? speed : 0);
            if (dx != 0 && dy != 0) {
                int diag = Math.max(1, speed - 1);
                dx = Integer.signum(dx) * diag;
                dy = Integer.signum(dy) * diag;
            }
            player.moving = dx != 0 || dy != 0;
            if (player.attackTimer == 0) {                       // во время взмаха взгляд не меняется
                if (dy > 0) player.facing = Player.Dir.DOWN;
                else if (dy < 0) player.facing = Player.Dir.UP;
                else if (dx > 0) player.facing = Player.Dir.RIGHT;
                else if (dx < 0) player.facing = Player.Dir.LEFT;
            }
            player.move(current, dx, dy);
        }

        if (checkTimer > 0 && --checkTimer == 0) finishCheck();

        // ----- таймеры игрока -----
        if (player.attackTimer > 0) player.attackTimer--;
        if (player.attackCooldown > 0) player.attackCooldown--;
        if (player.invul > 0) player.invul--;

        // ----- ловушки -----
        for (Trap t : current.traps) {
            t.update(player, tick);
            Rectangle hz = t.hazard(tick);
            if (hz != null && player.invul == 0 && hz.intersects(player.box())) {
                hurtPlayer(1, hz.x + hz.width / 2, hz.y + hz.height / 2);
            }
        }

        // ----- яма: падение = минус сердце и возврат на последнее безопасное место -----
        if (current.sideView && current.tileAt(player.centerX() / Room.TILE, player.centerY() / Room.TILE) == 'P') {
            fallInPit();
            return;
        }

        // ----- предметы и тотемы -----
        for (Pickup p : current.pickups) {
            if (!p.taken && p.box().intersects(player.box())) collect(p);
        }
        for (Totem t : current.totems) if (t.flash > 0) t.flash--;

        // ----- кот -----
        if (current.cat != null && current.sideView) current.cat.move(current, 0, 4);   // сидит на полу
        if (pet != null) pet.follow(current, player);
        if (current.cat != null && current.cat.flash > 0) current.cat.flash--;

        updateGoals();
        checkSpikes();
        trackSafeSpot();
        checkDoor();
        updateCamera();
    }

    // запоминает место, где можно безопасно постоять (не рядом с ямой, шипами, ловушкой и не на хрупкой плите)
    private void trackSafeSpot() {
        if (!current.sideView || !player.onGround || player.invul > 0) return;
        int col = player.centerX() / Room.TILE;
        int row = (player.y + player.h - 1) / Room.TILE;
        for (int c = col - 1; c <= col + 1; c++) {
            if (current.tileAt(c, row + 1) != '#') return;
            if (current.tileAt(c, row) == '^') return;
        }
        if (current.trapNear(col, 2)) return;
        safeX = player.x;
        safeY = player.y;
    }

    private void fallInPit() {
        checkTimer = 0;
        checkVase = null;
        player.hp--;
        if (player.hp <= 0) { faint(); return; }
        showMessage("Ты упал в яму! Минус сердце");
        player.x = safeX;
        player.y = safeY;
        player.vy = 0;
        player.onGround = false;
        player.invul = 90;
        if (pet != null) { pet.x = player.x; pet.y = player.y; }
        snapCamera();
    }

    private void collect(Pickup p) {
        p.taken = true;
        int got = current.takenCount(), total = current.pickups.size();
        switch (p.kind) {
            case CROWN -> showMessage("Ты взял корону!");
            case ROSE  -> showMessage("Роза " + got + "/" + total);
            case FOOD  -> {
                slowTimer = 300;
                showMessage("Вкусно, но ты тяжелеешь: замедление. Угощение " + got + "/" + total);
            }
            case ORB   -> showMessage("Сфера сна " + got + "/" + total);
        }
    }

    // открывает выход/ворота, когда цель комнаты выполнена
    private void updateGoals() {
        if (!current.exitLocked) return;
        switch (current.goal) {
            case COLLECT -> { if (current.allTaken()) unlockExit("Испытание пройдено!"); }
            case TOTEMS  -> { if (current.totemsLeft() == 0) unlockExit("Все тотемы разбиты!"); }
            case CAT     -> { if (current.cat == null) unlockExit(null); }   // сообщение уже показал выбор с котом
            default      -> { }                                              // CANDLE открывается в giveCandle()
        }
    }

    private void attack() {
        if (invOpen || won || checkTimer > 0 || player.attackCooldown > 0) return;
        player.attackTimer = Player.ATTACK_FRAMES;
        player.attackCooldown = ATTACK_COOLDOWN;

        int fx = player.facing == Player.Dir.RIGHT ? 1 : player.facing == Player.Dir.LEFT ? -1 : 0;
        int fy = player.facing == Player.Dir.DOWN ? 1 : player.facing == Player.Dir.UP ? -1 : 0;
        Rectangle hit = new Rectangle(player.centerX() + fx * 34 - 30, player.centerY() + fy * 34 - 30, 60, 60);
        int dmg = Math.max(1, (equipped != null ? equipped.damage : 1) - (weakTimer > 0 ? 1 : 0));

        for (Totem t : current.totems) {                      // тотемы ярости
            if (!t.broken && hit.intersects(t.box())) {
                t.hit();
                if (t.broken) showMessage(current.totemsLeft() > 0
                        ? "Тотем разбит! Осталось: " + current.totemsLeft() : "Тотем разбит!");
            }
        }

        for (Vase v : current.vases) {                        // разбить вазы = штраф
            if (v.state == Vase.State.INTACT && hit.intersects(v.box())) smashVase(v);
        }

        Cat c = current.cat;
        if (c != null && c.state == Cat.State.SITTING && hit.intersects(c.box())) {
            c.hp -= dmg;
            c.flash = 10;
            if (c.hp <= 0) killCat(c);
        }
    }

    private void killCat(Cat c) {
        c.state = Cat.State.DEAD;
        current.cat = null;
        slowPermanent = true;                                 // постоянное замедление
        Item reward = Item.sword("Тёмный меч", "Урон 4. Достался тебе от кота", 4, new Color(150, 90, 220));
        if (inventory.add(reward)) {
            showMessage("Ты получил «Тёмный меч» (I — экипировать), но навсегда замедлен. Ворота открыты");
        } else {
            showMessage("Инвентарь полон, меч потерян. Замедление осталось навсегда. Ворота открыты");
        }
    }

    private boolean nearCat() {
        Cat c = current.cat;
        return c != null && c.state == Cat.State.SITTING
                && Math.hypot(c.centerX() - player.centerX(), c.centerY() - player.centerY()) < 80;
    }

    private Room.Sign nearSign() {
        for (Room.Sign s : current.signs) {
            if (Math.abs(s.col() * Room.TILE + Room.TILE / 2 - player.centerX()) < 60
                    && Math.abs(s.row() * Room.TILE + Room.TILE / 2 - player.centerY()) < 60) return s;
        }
        return null;
    }

    private void interact() {
        if (invOpen || won || checkTimer > 0) return;
        if (nearCat()) {                                      // спасти кота: он садится на плечи
            Cat c = current.cat;
            c.state = Cat.State.FOLLOWING;
            c.onShoulder = true;
            pet = c;
            current.cat = null;
            showMessage("Ты спас кота. Он сидит у тебя на плечах. Мяу! Ворота открыты");
            return;
        }
        Vase v = nearVase();
        if (v != null) {                                      // безопасная проверка вазы
            checkVase = v;
            checkTimer = CHECK_TIME;
        }
    }

    private void hurtPlayer(int dmg, int fromX, int fromY) {
        checkTimer = 0;
        checkVase = null;
        player.hp -= dmg;
        player.invul = 70;
        player.move(current, Integer.signum(player.centerX() - fromX) * 16,
                Integer.signum(player.centerY() - fromY) * 16);
        if (player.hp <= 0) faint();
    }

    private void faint() {
        showMessage("Ты потерял сознание и очнулся " + (current.gateCol >= 0 && !current.exitLocked
                ? "за воротами" : "у входа в комнату"));
        player.hp = player.maxHp;
        player.setTile(respawnCol, respawnRow);
        player.vy = 0;
        player.onGround = false;
        player.invul = 90;
        poisonTimer = 0;
        safeX = player.x;
        safeY = player.y;
        if (pet != null) { pet.x = player.x; pet.y = player.y; }
        snapCamera();
    }

    private void checkSpikes() {
        int col = player.centerX() / Room.TILE;
        int row = player.centerY() / Room.TILE;
        if (player.invul == 0 && current.tileAt(col, row) == '^') {
            hurtPlayer(1, col * Room.TILE + Room.TILE / 2, row * Room.TILE + Room.TILE / 2);
        }
    }

    // вызывается, когда игрок выходит через открытую дверь-выход
    private void onRoomCompleted(Room r) {
        if ("tutorial".equals(r.key)) {
            showMessage("Обучение пройдено! Выбирай любую из 7 дверей — каждая ведёт к греху");
            return;
        }
        if (r.key == null || !r.key.matches("s[1-7]") || !done.add(r.key)) return;
        Room hall = rooms.get("hall");
        hall.doneDoors.add(r.key.charAt(1));
        if (done.size() == 7) {
            hall.exitLocked = false;
            showMessage("Все 7 грехов побеждены! В конце коридора открылась дверь");
        } else {
            showMessage("Грех побеждён (" + done.size() + "/7)");
        }
    }

    private void checkDoor() {
        int col = player.centerX() / Room.TILE;
        int row = player.centerY() / Room.TILE;
        char t = current.tileAt(col, row);
        Door door = current.doors.get(t);
        if (door != null) {
            if (t == current.exitChar && !current.exitLocked) onRoomCompleted(current);
            if (door.targetRoom().equals("END")) {                // последняя дверь
                won = true;
                up = down = left = right = jump = false;
                return;
            }
            enterRoom(rooms.get(door.targetRoom()));
            respawnCol = door.spawnCol();
            respawnRow = door.spawnRow();
            player.setTile(respawnCol, respawnRow);
            player.vy = 0;
            player.onGround = false;
            player.facing = current.sideView ? Player.Dir.RIGHT : Player.Dir.DOWN;
            checkTimer = 0;
            checkVase = null;
            safeX = player.x;
            safeY = player.y;
            if (pet != null && !pet.onShoulder) { pet.x = player.x; pet.y = player.y; }
            snapCamera();
        }
    }

    // ================= КАМЕРА =================
    private double targetCamX() {
        double t = player.centerX() - Room.VIEW_W / 2.0;
        return Math.max(0, Math.min(t, current.widthPx() - Room.VIEW_W));
    }

    private double targetCamY() {
        double t = player.centerY() - Room.VIEW_H / 2.0;
        return Math.max(0, Math.min(t, current.heightPx() - Room.VIEW_H));
    }

    private void snapCamera() {
        camX = targetCamX();
        camY = targetCamY();
    }

    private void updateCamera() {
        camX += (targetCamX() - camX) * CAM_SMOOTH;
        camY += (targetCamY() - camY) * CAM_SMOOTH;
    }

    // ================= ОТРИСОВКА =================
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (current == null) return;
        int cx = (int) Math.round(camX);
        int cy = (int) Math.round(camY);

        Graphics2D world = (Graphics2D) g.create();
        world.translate(-cx, -cy);
        current.draw(world, cx, cy, tick);

        // все существа рисуем по порядку "ног": кто ниже на экране, тот ближе к нам
        List<Entity> entities = new ArrayList<>();
        if (current.cat != null) entities.add(current.cat);
        if (pet != null) entities.add(pet);
        entities.add(player);
        entities.sort(Comparator.comparingInt(Entity::feetY));
        for (Entity en : entities) en.draw(world, tick);

        if (checkTimer > 0) {                                  // полоска проверки вазы
            int bx = player.centerX() - 22, by = player.y - 46;
            world.setColor(new Color(0, 0, 0, 170));
            world.fillRect(bx - 1, by - 1, 46, 8);
            world.setColor(new Color(120, 230, 140));
            world.fillRect(bx, by, (int) (44 * (1 - checkTimer / (double) CHECK_TIME)), 6);
        }
        world.dispose();

        Graphics2D overlay = (Graphics2D) g.create();
        if (current.sideView) drawVignette(overlay);
        if (darkTimer > 0) drawDarkness(overlay, player.centerX() - cx, player.centerY() - cy);
        overlay.dispose();

        drawHud((Graphics2D) g);
        drawTitleCard((Graphics2D) g);
        if (invOpen) drawInventory((Graphics2D) g);
        if (won) drawVictory((Graphics2D) g);
    }

    private void drawVignette(Graphics2D g) {
        RadialGradientPaint p = new RadialGradientPaint(
                new Point2D.Float(Room.VIEW_W / 2f, Room.VIEW_H / 2f), Room.VIEW_W * 0.6f,
                new float[]{0.55f, 1f},
                new Color[]{new Color(0, 0, 0, 0), new Color(0, 0, 0, 190)});
        g.setPaint(p);
        g.fillRect(0, 0, Room.VIEW_W, Room.VIEW_H);
    }

    private void drawDarkness(Graphics2D g, int px, int py) {
        RadialGradientPaint p = new RadialGradientPaint(
                new Point2D.Float(px, py), 170f,
                new float[]{0f, 0.45f, 1f},
                new Color[]{new Color(0, 0, 0, 0), new Color(0, 0, 0, 0), new Color(0, 0, 0, 240)});
        g.setPaint(p);
        g.fillRect(0, 0, Room.VIEW_W, Room.VIEW_H);
    }

    // заставка при входе: вспышка цвета греха + название
    private void drawTitleCard(Graphics2D g) {
        if (titleTimer <= 0 || current.title == null) return;
        double a = Math.min(1.0, titleTimer / 60.0);
        Theme th = current.theme;
        g.setColor(Theme.alpha(th.base, (int) (95 * a)));
        g.fillRect(0, 0, Room.VIEW_W, Room.VIEW_H);

        g.setFont(new Font("Monospaced", Font.BOLD, 56));
        g.setColor(new Color(0, 0, 0, (int) (170 * a)));
        drawCentered(g, current.title, 243);
        g.setColor(Theme.alpha(th.accent, (int) (255 * a)));
        drawCentered(g, current.title, 240);
        if (current.subtitle != null) {
            g.setFont(new Font("Monospaced", Font.BOLD, 18));
            g.setColor(new Color(0, 0, 0, (int) (170 * a)));
            drawCentered(g, current.subtitle, 283);
            g.setColor(new Color(255, 255, 255, (int) (255 * a)));
            drawCentered(g, current.subtitle, 281);
        }
    }

    private void drawVictory(Graphics2D g) {
        g.setColor(new Color(0, 0, 0, 200));
        g.fillRect(0, 0, Room.VIEW_W, Room.VIEW_H);
        g.setFont(new Font("Monospaced", Font.BOLD, 60));
        g.setColor(new Color(240, 200, 60));
        drawCentered(g, "ПОБЕДА!", 240);
        g.setFont(new Font("Monospaced", Font.BOLD, 20));
        g.setColor(Color.WHITE);
        drawCentered(g, "Ты победил все 7 грехов", 290);
        g.setFont(new Font("Monospaced", Font.PLAIN, 16));
        g.setColor(new Color(190, 200, 225));
        drawCentered(g, "Enter / ESC — в меню", 330);
    }

    private void drawHeart(Graphics2D g, int x, int y, boolean full) {
        g.setColor(full ? new Color(220, 50, 70) : new Color(70, 40, 60));
        g.fillRect(x + 2, y, 6, 4);
        g.fillRect(x + 12, y, 6, 4);
        g.fillRect(x, y + 4, 20, 6);
        g.fillRect(x + 2, y + 10, 16, 4);
        g.fillRect(x + 6, y + 14, 8, 3);
        g.fillRect(x + 8, y + 17, 4, 2);
        if (full) {
            g.setColor(new Color(255, 140, 150));
            g.fillRect(x + 3, y + 4, 4, 3);
        }
    }

    private void drawBanner(Graphics2D g, String text, int y, Color border) {
        g.setFont(new Font("Monospaced", Font.BOLD, 16));
        int w = g.getFontMetrics().stringWidth(text) + 24;
        int x = (Room.VIEW_W - w) / 2;
        g.setColor(new Color(10, 16, 30, 225));
        g.fillRect(x, y - 20, w, 32);
        g.setColor(border);
        g.drawRect(x, y - 20, w - 1, 31);
        g.setColor(Color.WHITE);
        g.drawString(text, x + 12, y + 2);
    }

    private String objectiveText() {
        Room r = current;
        if ("hall".equals(r.key)) {
            return r.exitLocked ? "Цель: победи 7 грехов (" + done.size() + "/7)"
                    : "Цель: иди в дверь ВЫХОД в конце коридора";
        }
        String open = r.gateCol >= 0 ? "Ворота открыты — пройди ловушки к выходу" : "Выход справа открыт";
        return switch (r.goal) {
            case CANDLE  -> r.exitLocked ? "Цель: найди свечу среди ваз" : open;
            case COLLECT -> r.exitLocked ? "Цель: " + r.goalLabel + " (" + r.takenCount() + "/" + r.pickups.size() + ")" : open;
            case TOTEMS  -> r.exitLocked ? "Цель: разбей тотемы (осталось " + r.totemsLeft() + ")" : open;
            case CAT     -> r.exitLocked ? "Цель: спаси кота (E) или убей его (L)" : open;
            default      -> "Цель: дойди до выхода справа";
        };
    }

    private void drawRight(Graphics2D g, String text, int y) {
        int w = g.getFontMetrics().stringWidth(text);
        g.drawString(text, Room.VIEW_W - w - 12, y);
    }

    private void drawHud(Graphics2D g) {
        g.setColor(new Color(0, 0, 0, 110));
        g.fillRect(0, 0, Room.VIEW_W, 76);
        for (int i = 0; i < player.maxHp; i++) drawHeart(g, 12 + i * 26, 10, i < player.hp);

        g.setFont(new Font("Monospaced", Font.BOLD, 16));
        g.setColor(Color.WHITE);
        int curDmg = equipped == null ? 0 : Math.max(1, equipped.damage - (weakTimer > 0 ? 1 : 0));
        String weapon = equipped == null ? "без оружия" : equipped.name + " (урон " + curDmg + ")";
        g.drawString(current.name + "   |   " + weapon, 12, 50);
        g.setFont(new Font("Monospaced", Font.PLAIN, 13));
        g.setColor(new Color(190, 200, 225));
        String hint = current.sideView
                ? "A/D — ходьба   ПРОБЕЛ — прыжок   L — меч   E — взять/проверить   I — инвентарь   ESC — меню"
                : "WASD/стрелки — ходьба   L — меч   E — взять   I — инвентарь   ESC — меню";
        g.drawString(hint, 12, 68);

        // цель и эффекты справа
        g.setFont(new Font("Monospaced", Font.BOLD, 14));
        g.setColor(current.theme.accent);
        drawRight(g, objectiveText(), 22);
        g.setColor(new Color(255, 130, 130));
        int ey = 42;
        List<String> fx = new ArrayList<>();
        if (slowPermanent) fx.add("Замедление (навсегда)");
        if (slowTimer > 0) fx.add("Замедление " + (slowTimer / 60 + 1) + "с");
        if (poisonTimer > 0) fx.add("Яд " + (poisonTimer / 60 + 1) + "с");
        if (darkTimer > 0) fx.add("Тьма " + (darkTimer / 60 + 1) + "с");
        if (weakTimer > 0) fx.add("Слабость " + (weakTimer / 60 + 1) + "с");
        for (String s : fx) { drawRight(g, s, ey); ey += 16; }

        if (messageTimer > 0) drawBanner(g, message, Room.VIEW_H - 70, new Color(120, 140, 190));
        if (!invOpen) {
            Room.Sign sign = nearSign();
            if (sign != null) drawBanner(g, sign.text(), 108, current.theme.accent);
            if (nearCat()) {
                drawBanner(g, "E — спасти кота      L — ударить мечом", Room.VIEW_H - 24, new Color(240, 200, 60));
            } else if (checkTimer == 0 && nearVase() != null) {
                drawBanner(g, "E — проверить (безопасно)      L — разбить (штраф!)", Room.VIEW_H - 24, new Color(240, 200, 60));
            }
        }
    }

    private void drawCentered(Graphics2D g, String text, int y) {
        int w = g.getFontMetrics().stringWidth(text);
        g.drawString(text, (Room.VIEW_W - w) / 2, y);
    }

    private void drawInventory(Graphics2D g) {
        g.setColor(new Color(0, 0, 0, 175));
        g.fillRect(0, 0, Room.VIEW_W, Room.VIEW_H);

        int top = slotRect(0).y;
        g.setFont(new Font("Monospaced", Font.BOLD, 34));
        g.setColor(new Color(240, 200, 60));
        drawCentered(g, "ИНВЕНТАРЬ", top - 40);

        for (int i = 0; i < Inventory.SLOTS; i++) {
            Rectangle r = slotRect(i);
            g.setColor(new Color(40, 40, 70));
            g.fillRect(r.x, r.y, r.width, r.height);
            g.setColor(i == selected ? new Color(240, 200, 60) : new Color(120, 120, 160));
            g.setStroke(new BasicStroke(4));
            g.drawRect(r.x + 2, r.y + 2, r.width - 4, r.height - 4);
            g.setStroke(new BasicStroke(1));

            g.setFont(new Font("Monospaced", Font.BOLD, 14));
            g.setColor(new Color(150, 150, 190));
            g.drawString(String.valueOf(i + 1), r.x + 8, r.y + 18);

            Item it = inventory.get(i);
            if (it != null) {
                drawItemIcon(g, it, r);
                if (it == equipped) {
                    g.setFont(new Font("Monospaced", Font.BOLD, 11));
                    g.setColor(new Color(240, 200, 60));
                    g.drawString("В РУКЕ", r.x + 16, r.y + r.height - 6);
                }
            }
        }

        Item sel = inventory.get(selected);
        g.setFont(new Font("Monospaced", Font.BOLD, 20));
        g.setColor(Color.WHITE);
        drawCentered(g, sel == null ? "Пустой слот" : sel.name + " — " + sel.description, top + SLOT + 50);

        g.setFont(new Font("Monospaced", Font.PLAIN, 15));
        g.setColor(new Color(170, 170, 200));
        drawCentered(g, "Стрелки или 1-" + Inventory.SLOTS + ": выбрать   Enter: использовать/экипировать   I / ESC: закрыть",
                top + SLOT + 85);
    }

    private void drawItemIcon(Graphics2D g, Item it, Rectangle r) {
        int cx = r.x + r.width / 2, cy = r.y + r.height / 2 - 4;
        if (it.type == Item.Type.COIN) {
            g.setColor(new Color(190, 140, 20));
            g.fillOval(cx - 20, cy - 20, 40, 40);
            g.setColor(new Color(240, 200, 60));
            g.fillOval(cx - 16, cy - 16, 32, 32);
            g.setColor(new Color(190, 140, 20));
            g.fillRect(cx - 3, cy - 9, 6, 18);
        } else if (it.type == Item.Type.BOOK) {
            g.setColor(new Color(140, 50, 50));
            g.fillRect(cx - 16, cy - 20, 32, 40);
            g.setColor(new Color(100, 30, 30));
            g.fillRect(cx - 16, cy - 20, 6, 40);
            g.setColor(new Color(240, 235, 210));
            g.fillRect(cx - 10, cy + 16, 26, 4);
            g.setColor(new Color(240, 200, 60));
            g.fillRect(cx - 4, cy - 10, 16, 4);
            g.fillRect(cx - 4, cy - 2, 16, 4);
        } else if (it.type == Item.Type.SWORD) {
            AffineTransform old = g.getTransform();
            g.translate(cx, cy);
            g.rotate(Math.PI / 4);
            g.setColor(it.blade);
            g.fillRect(-3, -26, 6, 34);
            g.setColor(Color.WHITE);
            g.fillRect(-3, -26, 2, 34);
            g.setColor(new Color(240, 200, 60));
            g.fillRect(-10, 8, 20, 4);
            g.setColor(new Color(120, 80, 40));
            g.fillRect(-2, 12, 4, 10);
            g.setTransform(old);
        } else if (it.type == Item.Type.CANDLE) {
            g.setColor(new Color(240, 235, 215));
            g.fillRect(cx - 6, cy - 6, 12, 26);
            g.setColor(new Color(200, 190, 165));
            g.fillRect(cx + 2, cy - 6, 4, 26);
            g.setColor(new Color(60, 50, 50));
            g.fillRect(cx - 1, cy - 12, 2, 6);
            g.setColor(new Color(255, 190, 60));
            g.fillOval(cx - 5, cy - 26, 10, 16);
            g.setColor(new Color(255, 240, 150));
            g.fillOval(cx - 2, cy - 21, 4, 9);
        }
    }

    // ================= КЛАВИШИ =================
    // Буквы проверяем и по коду клавиши, и по символу — чтобы L и E работали и на русской раскладке (Д, У).
    private static boolean isKey(KeyEvent e, int code, char ru) {
        return e.getKeyCode() == code || Character.toLowerCase(e.getKeyChar()) == ru;
    }

    @Override
    public void keyPressed(KeyEvent e) {
        int k = e.getKeyCode();

        if (won) {
            if (k == KeyEvent.VK_ENTER || k == KeyEvent.VK_ESCAPE) window.showMenu();
            return;
        }

        if (invOpen) {
            switch (k) {
                case KeyEvent.VK_I, KeyEvent.VK_TAB, KeyEvent.VK_ESCAPE -> invOpen = false;
                case KeyEvent.VK_LEFT, KeyEvent.VK_A -> selected = (selected + Inventory.SLOTS - 1) % Inventory.SLOTS;
                case KeyEvent.VK_RIGHT, KeyEvent.VK_D -> selected = (selected + 1) % Inventory.SLOTS;
                case KeyEvent.VK_ENTER, KeyEvent.VK_E -> useSelected();
                default -> {
                    if (k >= KeyEvent.VK_1 && k <= KeyEvent.VK_9 && k - KeyEvent.VK_1 < Inventory.SLOTS) {
                        selected = k - KeyEvent.VK_1;
                    }
                }
            }
            return;
        }

        if (isKey(e, KeyEvent.VK_L, 'д')) { attack(); return; }          // меч — L
        if (isKey(e, KeyEvent.VK_E, 'у')) { interact(); return; }

        switch (k) {
            case KeyEvent.VK_W, KeyEvent.VK_UP -> up = true;
            case KeyEvent.VK_S, KeyEvent.VK_DOWN -> down = true;
            case KeyEvent.VK_A, KeyEvent.VK_LEFT -> left = true;
            case KeyEvent.VK_D, KeyEvent.VK_RIGHT -> right = true;
            case KeyEvent.VK_SPACE -> jump = true;                       // прыжок — ПРОБЕЛ
            case KeyEvent.VK_I, KeyEvent.VK_TAB -> openInventory();
            case KeyEvent.VK_ESCAPE -> window.showMenu();
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {
        switch (e.getKeyCode()) {
            case KeyEvent.VK_W, KeyEvent.VK_UP -> up = false;
            case KeyEvent.VK_S, KeyEvent.VK_DOWN -> down = false;
            case KeyEvent.VK_A, KeyEvent.VK_LEFT -> left = false;
            case KeyEvent.VK_D, KeyEvent.VK_RIGHT -> right = false;
            case KeyEvent.VK_SPACE -> jump = false;
        }
    }

    @Override public void keyTyped(KeyEvent e) {}
}