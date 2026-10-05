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

    // 7 локаций-башен. В каждой: коридор с 4 железными дверями -> 4 комнаты -> дверь СПУСК -> дорога ловушек вниз.
    private static final int LOCS = 7;
    private static final int HALL_COLS = 30;                              // ширина коридора в клетках
    private static final int[] DOOR_COL = {7, 19, 7, 19};                 // колонки 4 дверей: комнаты 1-2 в верхней стене, 3-4 — в нижней
    private static int doorRowInHall(int n) { return n <= 2 ? 3 : 8; }    // ряд двери в стене коридора
    private static int hallReturnRow(int n) { return n <= 2 ? 4 : 7; }    // клетка у двери внутри коридора (куда возвращает из комнаты)
    // Порядок локаций (двери открываются по очереди): 1 Гнев, 2 Алчность, 3 Зависть, 4 Похоть, 5 Чревоугодие, 6 Уныние, 7 Гордыня
    private static final Theme[] SIN_THEMES = {Theme.WRATH, Theme.GREED, Theme.ENVY, Theme.LUST,
            Theme.GLUTTONY, Theme.SLOTH, Theme.PRIDE};
    private static final String[] SIN_NAMES = {"Гнев", "Алчность", "Зависть", "Похоть",
            "Чревоугодие", "Уныние", "Гордыня"};

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
    private int checkTotal = CHECK_TIME;                  // длина текущей проверки (для полоски)
    private Vase checkVase;

    private String message = "";
    private int messageTimer;

    // диалог Эльзы (реплики вида "Кто|Текст"); null — диалога нет
    private static final int DIALOG_TYPE = 2;             // кадров на одну букву
    private String[] dialog;
    private int dialogIdx, dialogT;
    private Prop dialogProp;

    // мини-игра «вырвись»: после диалога с Крисом / Себастьяном жми E или ПРОБЕЛ как можно быстрее
    private static final int MASH_TIME = 540;             // 9 секунд на попытку
    private static final double MASH_GAIN = 0.055;        // сколько даёт одно нажатие
    private static final double MASH_DECAY = 0.0035;      // на сколько шкала падает каждый кадр (он держит)
    private Prop mashProp;                                // null — мини-игры нет
    private double mashProgress;                          // 0..1: при 1 — вырвалась, при 0 — не удержалась
    private int mashTimer, mashPulse;
    private final Set<Integer> mashDown = new HashSet<>(); // зажатые клавиши (автоповтор клавиатуры не считается)
    private int titleTimer;                               // заставка с названием комнаты
    private boolean won;                                  // прошёл всю игру
    private final Sins sins = new Sins();                 // система грехов (Рай / Ад)
    private Sins.Ending ending = Sins.Ending.HEAVEN;
    private Cutscene cutscene;                           // вступительная катсцена (null — не идёт)
    private EndingCutscene epilogue;                  // катсцена-эпилог после экрана Ад/Рай (null — не идёт)
    private int victoryTimer;                         // сколько кадров уже висит экран Ад/Рай
    private static final int EPILOGUE_DELAY = 375;    // через сколько кадров запустится эпилог (16 мс на кадр ≈ 6 секунд)

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

        addHierarchyListener(e -> {
            if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0 && isShowing()) {
                SwingUtilities.invokeLater(this::requestFocusInWindow);
            }
        });

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

    // Комната-грех с видом сбоку: слева сцена с выбором (E / L), потом ворота 'G', за ними дорога ловушек к выходу '2'.
    private Room sin(int idx, MapBuilder b, String sub, Decision choice, String story) {
        Room r = sinRoom(idx, b, sub, choice.col - 2, story);   // табличка с историей рядом со сценой
        r.goal = Room.Goal.CHOICE;
        r.goalLabel = "сделай выбор";
        r.choice = choice;
        return r;
    }

    // Общая основа комнаты-греха (без цели): цель задаёт вызывающий — выбор E/L или, например, ESCAPE (вырваться).
    private Room sinRoom(int idx, MapBuilder b, String sub, int signCol, String story) {
        String name = SIN_NAMES[idx - 1];
        Room r = new Room(idx + ". " + name, b.build());
        r.sideView = true;
        r.theme = SIN_THEMES[idx - 1];
        r.title = name.toUpperCase();
        r.subtitle = sub;
        r.exitChar = '2';
        r.exitLocked = true;
        r.addSign(signCol, story);
        r.addDoor('1', hallKey(idx), DOOR_COL[3], hallReturnRow(4));
        r.addDoor('2', hallKey(idx), DOOR_COL[3], hallReturnRow(4));
        return r;
    }

    // Обозначения карт: # стена/платформа, ^ шипы, P яма, G ворота, V ваза, C кот, T тотем,
    //   K корона, R роза, A угощение, Z сфера сна, 1/2 двери.
    // Пол — ряд 9 (по нему ходит игрок), пол-стена — ряд 10. Блок в ряду 9 = препятствие в 1 клетку,
    // платформа в ряду 8 = 2 клетки (предел прыжка), поэтому лестницы делаем шагами по 1 клетке.
    // Обычные ямы делаем шириной 1 клетка (их легко перепрыгнуть даже при замедлении), широкие — только с хрупким мостом.
    private void buildRooms() {
        // ---------- 7 ЛОКАЦИЙ: коридор + комнаты 1-3 + дорога спуска (4-я комната — грех, ниже) ----------
        for (int loc = 1; loc <= LOCS; loc++) {
            buildHall(loc);
            if (loc != LOCS) for (int n = 1; n <= 3; n++) add(trialKey(loc, n), trial(loc, n));   // Гордыня — одна большая комната
            add(roadKey(loc), road(loc));
        }

        // В каждой комнате: слева сцена с выбором (E — первый вариант, L — второй), потом ворота 'G' и дорога ловушек.

        // ---------- 1. ГНЕВ (красный): тотем ярости ----------
        MapBuilder b1 = MapBuilder.side(66);
        b1.set(9, 9, '#');
        gate(b1, 40);
        pit(b1, 54, 54);
        b1.set(61, 9, '^');
        Room r1 = sin(1, b1, "Тотем ярости смотрит на тебя. Сломать его или сдержаться?",
                new Decision(Decision.Kind.TOTEM, 33, 9,
                        new Decision.Option("Сдержаться и пройти мимо", true, "Ты опустил меч. Ярость отпустила тебя."),
                        new Decision.Option("Разбить тотем", false, "Тотем разбит, но гнев взял над тобой верх.")),
                "Тотем ярости. Сдержись — или дай гневу волю");
        flame(r1, 13, 0);
        flame(r1, 22, 90);
        flame(r1, 44, 0);
        hidden(r1, 47);
        flame(r1, 51, 70);
        flame(r1, 58, 140);
        add("s1", r1);

        // ---------- 2. АЛЧНОСТЬ (золотой): кабинет сотрудников приюта, деньги на столе ----------
        MapBuilder b2 = MapBuilder.side(60);
        b2.set(8, 9, '#');
        pit(b2, 15, 15);
        gate(b2, 34);
        b2.set(47, 9, '^');
        pit(b2, 55, 55);
        Room r2 = sin(2, b2, "Кабинет сотрудников приюта. На столе лежат деньги. Взять или нет?",
                new Decision(Decision.Kind.MONEY, 25, 9,
                        new Decision.Option("Не брать деньги", true, "Ты не тронул деньги приюта. На душе стало легко."),
                        new Decision.Option("Взять деньги", false, "Ты забрал деньги сирот. Алчность прокляла тебя.")),
                "Деньги приюта. Они тебе нужнее — или сиротам?");
        r2.props.add(Prop.plaque(19, 3, "КАБИНЕТ СОТРУДНИКОВ"));
        r2.props.add(new Prop(Prop.Kind.CABINET, 18, 9));
        r2.props.add(new Prop(Prop.Kind.CABINET, 20, 9));
        r2.props.add(Prop.talk(Prop.Kind.DESK, 25, 9, null,
                "Эльза|Деньги приюта... Мне они сейчас так нужны.",
                "Эльза|Но это деньги сирот."));
        hidden(r2, 38, 43);
        saw(r2, 49, 53);
        add("s2", r2);

        // ---------- 3. ЗАВИСТЬ (зелёный): дочь ----------
        MapBuilder b3 = MapBuilder.side(64);
        b3.set(12, 9, '^');
        pit(b3, 15, 15);
        gate(b3, 38);
        pit(b3, 41, 45);                          // пропасть под хрупким мостом
        pit(b3, 57, 57);
        Room r3 = sin(3, b3, "Анна, дочь Осмаров, жила в роскоши. Простить её или поднять меч?",
                new Decision(Decision.Kind.GIRL, 29, 9,
                        new Decision.Option("Простить Анну", true, "Эльза простила Анну. Зависть отступила."),
                        new Decision.Option("Поднять меч на Анну", false, "Эльза подняла меч на Анну. Зависть победила.")),
                "Анна Осмар. У неё было всё, чего не было у тебя");
        r3.props.add(Prop.plaque(29, 3, "КОМНАТА АННЫ ОСМАР"));
        r3.props.add(Prop.talk(Prop.Kind.DOLL, 22, 9, null,
                "Эльза|Комната Анны, дочери Осмаров. Платья, игрушки, тёплая постель...",
                "Эльза|У неё было всё. А у меня не было ничего.",
                "Эльза|Почему ей — всё, а мне — ничего?"));
        hidden(r3, 8, 20, 24);
        crumble(r3, 41, 45, 10);
        hidden(r3, 49);
        saw(r3, 51, 55);
        add("s3", r3);

        // ---------- 4. ПОХОТЬ (розовый): встреча с тенью из прошлого ----------
        MapBuilder b4 = MapBuilder.side(66);
        b4.set(12, 9, '^');
        pit(b4, 24, 27);
        gate(b4, 39);
        pit(b4, 50, 50);
        Room r4 = sinRoom(4, b4, "Крис не отпустит просто так. Вырвись из его рук!", 30,
                "Крис — тот, кто не давал тебе покоя во время службы и заточения");
        r4.goal = Room.Goal.ESCAPE;
        r4.goalLabel = "вырвись";
        r4.props.add(Prop.mash(Prop.Kind.CHRIS, 34, 9,
                "Эльза вырвалась и убежала от Криса. Похоть не взяла над ней верх.",
                "Эльза не смогла вырваться. Она согрешила похотью.",
                "Крис|Вот ты где, Эльза. Я скучал по тебе.",
                "Эльза|Крис... Не подходи ко мне.",
                "Крис|Пойдём со мной. Уединимся, как раньше. Тебя никто не найдёт.",
                "Эльза|Нет! Я больше не твоя вещь. Не трогай меня!"));
        crumble(r4, 24, 27, 10);
        flame(r4, 43, 0);
        hidden(r4, 47);
        saw(r4, 54, 58);
        flame(r4, 61, 100);
        add("s4", r4);

        // ---------- 5. ЧРЕВОУГОДИЕ (оранжевый): накрытый стол ----------
        MapBuilder b5 = MapBuilder.side(64);
        b5.set(10, 9, '^');
        pit(b5, 24, 24);
        gate(b5, 37);
        pit(b5, 49, 49);
        b5.set(56, 9, '^');
        Room r5 = sin(5, b5, "Комната отходов. Единственная настоящая рыба — отдать кошке или съесть?",
                new Decision(Decision.Kind.FISH, 31, 9,
                        new Decision.Option("Отдать рыбу кошке", true, "Эльза отдала рыбу кошке. Та громко мурчит."),
                        new Decision.Option("Съесть рыбу самой", false, "Эльза съела рыбу сама. Чревоугодие победило.")),
                "Рыба. Кошка смотрит на неё голодными глазами");
        r5.props.add(Prop.plaque(20, 3, "КОМНАТА ОТХОДОВ"));
        for (int c : new int[]{13, 16}) r5.props.add(new Prop(Prop.Kind.TRASH, c, 9));
        r5.props.add(Prop.talk(Prop.Kind.TRASH, 20, 9, null,
                "Эльза|Отходы и мусор... Вот где я ела в плену. Объедки, кости, гнилые корки.",
                "Эльза|Я рылась в этих отбросах, лишь бы не умереть с голоду."));
        r5.props.add(Prop.talk(Prop.Kind.TRASH, 27, 9, null,
                "Кошка|Мяу! Мяу-мяу!",
                "Эльза|Рыба... Настоящая! Первая настоящая еда за столько времени.",
                "Эльза|Я так голодна... Но кошка тоже голодна."));
        hidden(r5, 40, 53);
        saw(r5, 42, 46);
        flame(r5, 59, 40);
        add("s5", r5);

        // ---------- 6. УНЫНИЕ (серо-голубой): течение сна тянет назад ----------
        MapBuilder b6 = MapBuilder.side(66);
        b6.set(8, 9, '^');
        pit(b6, 18, 18);
        gate(b6, 36);
        pit(b6, 43, 43);
        b6.set(59, 9, '^');
        Room r6 = sin(6, b6, "Её комната в том доме. Лечь на тряпку или взять игрушку и идти?",
                new Decision(Decision.Kind.RAG, 30, 9,
                        new Decision.Option("Лечь на тряпку и остаться", false, "Эльза свернулась на тряпке и не захотела вставать. Уныние победило."),
                        new Decision.Option("Взять игрушку и идти дальше", true, "Эльза взяла игрушку и встала. Уныние отступило.")),
                "Её комната: тряпка и одна игрушка");
        r6.props.add(Prop.plaque(24, 3, "КОМНАТА ЭЛЬЗЫ"));
        r6.props.add(Prop.talk(Prop.Kind.PLAQUE, 24, 9, null,
                "Эльза|Моя комната в том доме... Если это можно так назвать.",
                "Эльза|Здесь почти ничего не было. Тряпка, чтобы укрываться от мокрого и холодного пола.",
                "Эльза|И одна игрушка. Больше у меня не было ничего."));
        r6.wind = 1;
        hidden(r6, 22, 47);
        flame(r6, 39, 0);
        saw(r6, 49, 53);
        flame(r6, 56, 90);
        add("s6", r6);

        // ---------- 7. ГОРДЫНЯ (синий + золото): одна большая комната — галерея прошлой жизни и псевдо-джинн ----------
        MapBuilder b7 = MapBuilder.side(100);
        gate(b7, 84);
        b7.set(95, 9, '^');
        Room r7 = sin(7, b7, "Джинн обещает вернуть прежнюю жизнь. Согласиться или отказаться?",
                new Decision(Decision.Kind.GENIE, 77, 9,
                        new Decision.Option("Да", false, "Эльза сказала «да». Гордыня потянула назад, но джинн был лишь миражом."),
                        new Decision.Option("Нет", true, "Эльза сказала «нет». Мираж растаял, гордыня склонилась.")),
                "Джинн. Он обещает вернуть всё, что ты потеряла");
        r7.props.add(Prop.plaque(10, 3, "ГАЛЕРЕЯ ПРОШЛОГО"));
        for (int c : new int[]{6, 9, 15, 18, 21}) r7.props.add(new Prop(Prop.Kind.DRESS, c, 9));
        r7.props.add(Prop.talk(Prop.Kind.DRESS, 12, 9, null,
                "Эльза|Мои старые платья... Шёлк, кружева, бархат.",
                "Эльза|Когда-то я носила их и чувствовала себя особенной."));
        r7.props.add(Prop.talk(Prop.Kind.FAMILY, 28, 9, null,
                "Эльза|Мама... Папа... Я помню этот день.",
                "Эльза|Мы сидели за одним столом и смеялись. Я была их гордостью.",
                "Эльза|А потом всё исчезло."));
        for (int c : new int[]{34, 38, 46, 50}) r7.props.add(new Prop(Prop.Kind.PAINTING, c, 6));
        r7.props.add(Prop.plaque(42, 3, "ПРЕЖНЯЯ ЖИЗНЬ"));
        r7.props.add(Prop.talk(Prop.Kind.PAINTING, 42, 6, null,
                "Эльза|Наш дом, наш сад, наши слуги... Мне кланялись.",
                "Эльза|Я гордилась всем этим. Слишком гордилась."));
        for (int c : new int[]{55, 58, 64, 67}) r7.props.add(new Prop(Prop.Kind.DRESS, c, 9));
        r7.props.add(Prop.talk(Prop.Kind.DRESS, 61, 9, null,
                "Эльза|Бал... Я танцевала до рассвета, и все смотрели только на меня.",
                "Эльза|Как же я скучаю по этой жизни."));
        r7.props.add(Prop.talk(Prop.Kind.PLAQUE, 71, 9, null,
                "Джинн|Эльза... Я вижу твоё сердце.",
                "Джинн|Хочешь, я верну тебе всё? Платья, семью, дом, твоё имя?",
                "Эльза|Ты правда можешь это сделать?",
                "Джинн|Могу. Скажи только «да» или «нет»."));
        hidden(r7, 88, 92);
        flame(r7, 90, 60);
        add("s7", r7);
    }

    // ====== КЛЮЧИ КОМНАТ ======
    private static String hallKey(int loc)         { return "h" + loc; }
    private static String roadKey(int loc)         { return "road" + loc; }
    private static String trialKey(int loc, int n) { return n == 4 ? "s" + loc : "r" + loc + "_" + n; }   // 4-я комната = грех s1..s7
    private static boolean isHall(Room r)          { return r != null && r.key != null && r.key.matches("h[1-7]"); }
    private static boolean isGluttonyRoom(Room r)  { return r != null && r.key != null && r.key.matches("r5_[1-3]|s5"); }

    // {локация, номер комнаты} по ключу комнаты-испытания; null — если это не испытание
    private static int[] parseTrial(String key) {
        if (key == null) return null;
        if (key.matches("s[1-7]")) return new int[]{key.charAt(1) - '0', 4};
        if (key.matches("r[1-7]_[1-3]")) return new int[]{key.charAt(1) - '0', key.charAt(3) - '0'};
        return null;
    }

    private int roomsDone(int loc) {
        int n = 0;
        for (int i = 1; i <= 4; i++) if (done.contains(trialKey(loc, i))) n++;
        return n;
    }

    // ====== КОРИДОР ЛОКАЦИИ: 4 железные двери по порядку + дверь СПУСК ======
    private void buildHall(int loc) {
        MapBuilder hb = new MapBuilder(HALL_COLS, 12, '#');
        hb.rect(1, 4, HALL_COLS - 2, 7, '.');
        for (int i = loc == LOCS ? 3 : 0; i < 4; i++) hb.set(DOOR_COL[i], doorRowInHall(i + 1), (char) ('1' + i));   // у Гордыни одна дверь
        hb.set(HALL_COLS - 1, 6, '8');
        if (loc == 2) hb.set(5, 5, 'C');                       // в коридоре Алчности сидит кошка — она пойдёт с Эльзой до конца игры
        Room hall = new Room("Коридор · " + SIN_NAMES[loc - 1], hb.build());
        hall.theme = Theme.HALL;
        hall.basement = true;                                  // кирпичные стены и железные двери (общее для всех локаций)
        hall.look = Room.Look.VOID;                            // почти чёрное оформление
        hall.title = "ЛОКАЦИЯ " + loc;
        hall.subtitle = loc == LOCS ? SIN_NAMES[loc - 1] + ": одна большая комната — твоё прошлое. Берегись стрел!"
                : SIN_NAMES[loc - 1] + ": четыре двери открываются по порядку. Берегись стрел!";
        for (int i = loc == LOCS ? 3 : 0; i < 4; i++) {
            char c = (char) ('1' + i);
            hall.addDoor(c, trialKey(loc, i + 1), 3, 9);
            hall.doorTint.put(c, SIN_THEMES[loc - 1].base);
            hall.doorLabel.put(c, loc == LOCS ? "ПРОШЛОЕ" : "КОМНАТА " + (i + 1));
        }
        hall.addDoor('8', roadKey(loc), 3, 9);
        hall.doorTint.put('8', new Color(240, 200, 60));
        hall.doorLabel.put('8', "СПУСК");
        hall.exitChar = '8';
        hall.exitLocked = true;
        // стрелометы в стенах коридора: сверху стреляют вниз, снизу — вверх (стрела пересекает весь проход)
        hall.traps.add(Trap.arrow(11, 3, 0, 1, 4, 0));
        hall.traps.add(Trap.arrow(15, 8, 0, -1, 4, 60));
        hall.traps.add(Trap.arrow(25, 3, 0, 1, 4, 110));
        hall.traps.add(Trap.arrow(27, 8, 0, -1, 4, 30));
        add(hallKey(loc), hall);
    }

    // двери комнат открываются по порядку: доступна только следующая, остальные заперты (пройденные тоже)
    private void refreshHallLocks(int loc) {
        Room hall = rooms.get(hallKey(loc));
        hall.lockedDoors.clear();
        int next = loc == LOCS ? (done.contains("s" + LOCS) ? -1 : 3) : roomsDone(loc);
        for (int i = 0; i < 4; i++) if (i != next) hall.lockedDoors.add((char) ('1' + i));
    }

    // ====== ГЕНЕРАЦИЯ ДОРОГИ ЛОВУШЕК ======
    private static final int T_PIT = 0, T_SPIKE = 1, T_BLOCK = 2, T_HIDDEN = 3, T_FLAME = 4, T_SAW = 5, T_BRIDGE = 6;

    private int pickTrap(int level, Random rnd) {
        int r = rnd.nextInt(100);
        if (r < 25) return T_PIT;
        if (r < 40) return T_SPIKE;
        if (r < 50) return T_BLOCK;
        if (r < 65) return T_HIDDEN;
        if (r < 80) return level >= 2 ? T_FLAME : T_SPIKE;
        if (r < 92) return level >= 3 ? T_SAW : T_HIDDEN;
        return level >= 3 ? T_BRIDGE : T_PIT;
    }

    // план: {тип, колонка, случайное число (фаза огня)}. Между препятствиями всегда >= 3 свободных клеток — путь проходим.
    private List<int[]> planRoad(int from, int to, int level, Random rnd) {
        List<int[]> plan = new ArrayList<>();
        int c = from;
        while (true) {
            int type = pickTrap(level, rnd);
            int width = type == T_SAW ? 5 : type == T_BRIDGE ? 3 : 1;
            if (c + width > to) break;
            plan.add(new int[]{type, c, rnd.nextInt(200)});
            c += width + Math.max(3, 6 - level / 2 + rnd.nextInt(2));
        }
        return plan;
    }

    private void applyRoadMap(MapBuilder b, List<int[]> plan) {
        for (int[] t : plan) {
            switch (t[0]) {
                case T_PIT    -> pit(b, t[1], t[1]);
                case T_SPIKE  -> b.set(t[1], 9, '^');
                case T_BLOCK  -> b.set(t[1], 9, '#');
                case T_BRIDGE -> pit(b, t[1], t[1] + 2);
                default       -> { }
            }
        }
    }

    private void applyRoadTraps(Room r, List<int[]> plan) {
        for (int[] t : plan) {
            switch (t[0]) {
                case T_HIDDEN -> hidden(r, t[1]);
                case T_FLAME  -> flame(r, t[1], t[2]);
                case T_SAW    -> saw(r, t[1], t[1] + 4);
                case T_BRIDGE -> crumble(r, t[1], t[1] + 2, 10);
                default       -> { }
            }
        }
    }

    // Комнаты 1-3 каждой локации: слева цель (свеча / предметы / тотемы), ворота 'G', дальше дорога ловушек к выходу.
    // В одной локации три комнаты всегда с тремя разными целями; глубже — больше ловушек.
    private Room trial(int loc, int n) {
        if (n == fillerIndex(loc)) return fillerRoom(loc, n);   // в каждой локации одна комната — «пустышка» с одними ловушками
        int cols = (loc == 1 && n == 2) ? 66 : 56 + loc * 2;    // музей Гнева длиннее: за воротами комната наказаний
        MapBuilder b = MapBuilder.side(cols);
        Random rnd = new Random(loc * 1000L + n * 17L);
        int kind = (loc + n) % 3;                              // 0 свеча, 1 сбор предметов, 2 тотемы, 3 встреча (вырваться)
        if (loc == 2 && n == 3) kind = 1;                      // приют: вместо тотемов — розы, работница уходит молча
        if (loc == 4 && n == 1) kind = 1;                      // Похоть: комната с нарядами — розы вместо тотемов
        if ((loc == 5 || loc == 6) && (n == 1 || n == 2)) kind = 4;           // Чревоугодие: фуршет и столовая — сценки без цели
        if (loc == 4 && n == 3) kind = 3;                      // Похоть: Себастьян
        b.set(8, 9, '#');
        if (loc >= 2) pit(b, 27, 27);
        switch (kind) {
            case 3, 4 -> { }                                        // комната-встреча: предметов нет, только сценка
            case 0 -> vaseLayout(b, loc);                           // много ваз: на полу и на полках
            case 1 -> {
                char ch = loc % 2 == 1 && loc != 1 ? 'Z' : 'R';      // в первой локации — розы
                if (loc == 1) {                                      // середину комнаты занимает семья Осмаров
                    b.set(12, 9, ch);
                    b.set(24, 9, ch);
                    b.set(27, 9, ch);
                } else {
                    b.set(13, 9, ch);
                    b.set(18, 8, ch);
                    b.set(23, 9, ch);
                }
            }
            default -> {
                b.set(14, 9, 'T');
                b.set(20, 9, 'T');
                if (loc >= 4) b.set(25, 9, 'T');
            }
        }
        if (kind != 4) gate(b, 30);
        List<int[]> plan = (loc == 1 && n == 2)
                ? List.of(new int[]{T_HIDDEN, 42, 0}, new int[]{T_PIT, 46, 0}, new int[]{T_FLAME, 50, 40},
                new int[]{T_SPIKE, 55, 0}, new int[]{T_HIDDEN, 59, 0})      // комната наказаний: ловушки вручную
                : planRoad(33, cols - 5, loc, rnd);
        applyRoadMap(b, plan);

        Room r = new Room(SIN_NAMES[loc - 1] + " · комната " + n, b.build());
        r.sideView = true;
        r.theme = SIN_THEMES[loc - 1];
        r.title = SIN_NAMES[loc - 1].toUpperCase();
        r.exitChar = '2';
        r.exitLocked = true;
        String hint;
        switch (kind) {
            case 4 -> {
                r.goal = Room.Goal.NONE;
                r.goalLabel = "";
                r.exitLocked = false;
                r.subtitle = "Комната " + n + " из 4: " + (loc == 6
                        ? (n == 1 ? "ванна, которой у неё не было месяцами" : "тёплая кровать с кошкой")
                        : "еда, которую ей не давали");
                hint = "Подойди ближе. Здесь нет цели: поговори и иди к выходу";
            }
            case 3 -> {
                r.goal = Room.Goal.ESCAPE;
                r.goalLabel = "вырвись";
                r.subtitle = "Комната " + n + " из 4: поговори и вырвись из его рук";
                hint = "Подойди к нему. Когда он схватит — жми E или ПРОБЕЛ как можно быстрее!";
            }
            case 0 -> {
                r.goal = Room.Goal.CANDLE;
                r.goalLabel = "найди свечу";
                r.checkTime = 60 + loc * 20;                        // чем глубже, тем дольше стоять над вазой
                r.subtitle = "Комната " + n + " из 4: найди свечу среди " + r.vases.size() + " ваз";
                hint = "Свеча в одной из " + r.vases.size() + " ваз. E — проверить (" + String.format("%.1f", r.checkTime / 60.0) + " с, стой смирно), L — разбить (штраф!)";
                vaseGuards(r, loc);
            }
            case 1 -> {
                r.goal = Room.Goal.COLLECT;
                r.goalLabel = "собери предметы";
                r.subtitle = "Комната " + n + " из 4: собери все предметы";
                hint = "Собери все светящиеся предметы — тогда откроются ворота";
            }
            default -> {
                r.goal = Room.Goal.TOTEMS;
                r.goalLabel = "разбей тотемы";
                r.subtitle = "Комната " + n + " из 4: разбей тотемы мечом";
                hint = "Тотемы разбиваются мечом: L, три удара на каждый";
            }
        }
        r.addSign(11, hint);
        r.addDoor('1', hallKey(loc), DOOR_COL[n - 1], hallReturnRow(n));
        r.addDoor('2', hallKey(loc), DOOR_COL[n - 1], hallReturnRow(n));
        applyRoadTraps(r, plan);
        if (loc == 1) decorateWrath(r, n);
        else if (loc == 2) decorateGreed(r, n);
        else if (loc == 4) decorateLust(r, n);
        else if (loc == 5) decorateGluttony(r, n);
        else if (loc == 6) decorateSloth(r, n);
        return r;
    }


    // Комната с вазами: пол + полки (платформы в ряду 8, на них вазы в ряду 7). Колонку 27 (яма) и ворота (30) не трогаем.
    private void vaseLayout(MapBuilder b, int loc) {
        int[] floor = loc == 1 ? new int[]{13, 15, 18, 20, 23, 25} : new int[]{12, 14, 16, 19, 21, 24, 26};
        int[] shelf = loc == 1 ? new int[]{17, 24} : loc >= 4 ? new int[]{15, 18, 23, 25} : new int[]{15, 23};
        for (int c : floor) b.set(c, 9, 'V');
        for (int c : shelf) {
            b.set(c, 8, '#');                                   // полка в 2 клетки над полом — достать прыжком
            b.set(c, 7, 'V');
        }
    }

    // Ловушки среди ваз: пока Эльза стоит над вазой, шипы могут выскочить — любой урон прерывает проверку.
    private void vaseGuards(Room r, int loc) {
        if (loc >= 2) hidden(r, 17, 20, 25);
        if (loc >= 4) flame(r, 13, 40);
    }

    // В каждой локации одна из комнат 1-3 — «пустышка»: ни цели, ни ворот, только ловушки (прыжки, стрелы, огонь, шипы).
    // Какая именно — зависит от локации: 1 → комната 1, 2 → комната 2, 3 → комната 3, 4 (Похоть) → комната 2, дальше по кругу.
    private static int fillerIndex(int loc) { return loc == 4 ? 2 : loc == 5 ? 3 : (loc + 2) % 3 + 1; }

    private Room fillerRoom(int loc, int n) {
        int cols = 72 + loc * 2;
        MapBuilder b = MapBuilder.side(cols);
        Random rnd = new Random(loc * 1000L + n * 17L + 3);
        int level = Math.min(7, loc + 2);

        b.set(8, 9, '#');                                      // первый блок: учимся прыгать
        List<int[]> p1 = planRoad(13, 31, level, rnd);         // яма, шипы, огонь, пила...
        applyRoadMap(b, p1);

        int h = 34;                                            // горка из ступеней по 1 клетке: прыгай вверх и вниз
        b.set(h, 9, '#');
        b.rect(h + 1, 8, h + 2, 9, '#');
        b.set(h + 3, 9, '#');

        b.set(48, 9, '#');                                     // блоки-стрелометы: стрела летит по полу, её надо перепрыгнуть
        b.set(58, 9, '#');
        List<int[]> p2 = planRoad(62, cols - 5, level, rnd);
        applyRoadMap(b, p2);

        Room r = new Room(SIN_NAMES[loc - 1] + " · комната " + n, b.build());
        r.sideView = true;
        r.theme = SIN_THEMES[loc - 1];
        r.title = SIN_NAMES[loc - 1].toUpperCase();
        r.subtitle = "Комната " + n + " из 4: только ловушки — просто дойди до выхода";
        r.goal = Room.Goal.NONE;
        r.exitChar = '2';
        r.exitLocked = false;
        if (loc == 1) {                                        // в первой локации это ещё и короткое обучение
            r.addSign(4, "A / D или стрелки — ходьба");
            r.addSign(6, "ПРОБЕЛ — прыжок. Перепрыгни блок впереди");
        }
        r.addSign(11, "Здесь нет цели: прыгай, уклоняйся от огня, шипов и пил");
        r.addSign(40, "Стрелы летят вдоль пола — перепрыгни их, когда бойница мигнёт");
        r.addDoor('1', hallKey(loc), DOOR_COL[n - 1], hallReturnRow(n));
        r.addDoor('2', hallKey(loc), DOOR_COL[n - 1], hallReturnRow(n));
        applyRoadTraps(r, p1);
        applyRoadTraps(r, p2);
        r.traps.add(Trap.arrow(48, 9, -1, 0, 6, 0));
        r.traps.add(Trap.arrow(58, 9, -1, 0, 6, 85));
        return r;
    }

    // Декорации и реплики Эльзы в комнатах Гнева (локация 1). Реплика запускается сама, когда Эльза подходит к предмету.
    private void decorateWrath(Room r, int n) {
        if (n == 2) {                                          // музей Осмаров, за воротами — комната наказаний
            r.props.add(Prop.plaque(18, 3, "КОЛЛЕКЦИЯ СЕМИ ОСМАРОВ"));
            for (int c : new int[]{10, 16, 19, 25}) r.props.add(new Prop(Prop.Kind.PAINTING, c, 6));
            r.props.add(Prop.talk(Prop.Kind.PAINTING, 13, 6, null,
                    "Эльза|Эта ваза из коллекции семи Осмаров, я их ненавижу..."));
            hidden(r, 16, 22);                                 // пара ловушек на полу между вазами

            r.props.add(Prop.plaque(35, 3, "КОМНАТА НАКАЗАНИЙ"));
            r.props.add(Prop.talk(Prop.Kind.WHIPS, 36, 9, null,
                    "Эльза|А это плеть и кнуты, которыми мистер П меня наказывал. Что оно тут делает?"));
            r.props.add(new Prop(Prop.Kind.PILLORY, 39, 9));
            r.props.add(new Prop(Prop.Kind.WHIPS, 53, 9));
            r.props.add(new Prop(Prop.Kind.PILLORY, 57, 9));
        } else if (n == 3) {                                   // комната семьи Осмаров
            r.props.add(Prop.plaque(18, 3, "СЕМЬЯ ОСМАРОВ"));
            for (int c : new int[]{11, 15, 21, 25}) r.props.add(new Prop(Prop.Kind.PAINTING, c, 6));
            r.props.add(Prop.talk(Prop.Kind.FAMILY, 18, 9, null,
                    "Эльза|Семья Осмаров... Ненавижу их. Особенно её — Анну."));
        }
    }

    // Алчность: детский приют «Дэкса». Комната 1 — игрушки и табличка, комната 2 — пустышка, комната 3 — молчаливая работница.
    // Комната 4 (грех) — кабинет сотрудников с деньгами, она собрана в buildRooms().
    private void decorateGreed(Room r, int n) {
        if (n == 1) {
            r.props.add(Prop.plaque(21, 3, "ДЕТСКИЙ ПРИЮТ ДЛЯ СИРОТ «ДЭКСА»\nОткрыт для всех нуждающихся.\nДа будет вами доволен Бог."));
            for (int c : new int[]{18, 20, 23, 25}) r.props.add(new Prop(Prop.Kind.TOYS, c, 9));
        } else if (n == 3) {
            for (int c : new int[]{16, 26}) r.props.add(new Prop(Prop.Kind.TOYS, c, 9));
            // псевдодиалог: Эльза говорит, работница отвечает только молчанием и уходит
            r.props.add(Prop.talk(Prop.Kind.WORKER, 21, 9, "Работница молча вышла из комнаты.",
                    "Эльза|Что вы тут делаете?",
                    "Работница|...",
                    "Эльза|Вас же заставляли издеваться надо мной.",
                    "Работница|...",
                    "Эльза|Вы меня ждали. А потом предали.",
                    "Работница|...",
                    "Эльза|Вам не нужны деньги?",
                    "Работница|...",
                    "Эльза|Ответьте!",
                    "Работница|..."));
        }
    }

    // Похоть: комната 1 — вешалки с нарядами, комната 2 — пустышка, комната 3 — Себастьян (вырваться), комната 4 — Крис (в buildRooms()).
    private void decorateLust(Room r, int n) {
        if (n == 1) {
            for (int c : new int[]{16, 25}) r.props.add(new Prop(Prop.Kind.CLOTHES, c, 9));
            r.props.add(Prop.talk(Prop.Kind.CLOTHES, 20, 9, null,
                    "Эльза|Именно в этих нарядах меня заставляли ходить перед господами и перед семьёй Осмаров.",
                    "Эльза|Даже смотреть на них противно."));
        } else if (n == 3) {
            r.props.add(Prop.mash(Prop.Kind.SEBASTIAN, 22, 9,
                    "Эльза вырвалась и убежала от Себастьяна.",
                    "Эльза не смогла вырваться. Она согрешила похотью.",
                    "Себастьян|Эльза... Я искал тебя.",
                    "Эльза|Себастьян? Ты же помог мне сбежать...",
                    "Себастьян|Помог. А теперь пойдём со мной. Уединимся ненадолго — никто не узнает.",
                    "Эльза|Нет. Я не хочу. Отпусти меня!"));
        }
    }

    // Чревоугодие: комната 1 — фуршет Осмаров, комната 2 — столовая для слуг (в обеих еда — галлюцинации),
    // комната 3 — пустышка с ловушками, комната 4 (грех) — отходы и рыба, она собрана в buildRooms().
    private void decorateGluttony(Room r, int n) {
        if (n == 1) {
            r.props.add(Prop.plaque(18, 3, "ФУРШЕТ СЕМЬИ ОСМАРОВ"));
            for (int c : new int[]{15, 21}) r.props.add(new Prop(Prop.Kind.BUFFET, c, 9));
            r.props.add(Prop.talk(Prop.Kind.BUFFET, 18, 9,
                    "Еда растаяла, как дым. Это были лишь видения.",
                    "Эльза|Фуршет семьи Осмаров... Столько еды. А мне они никогда не давали даже крошки.",
                    "Эльза|Я так голодна... Может, хоть кусочек?",
                    "Эльза|Нет... Еда тает в руках. Это галлюцинации. Съесть ничего нельзя."));
        } else if (n == 2) {
            r.props.add(Prop.plaque(18, 3, "СТОЛОВАЯ ДЛЯ СЛУГ"));
            for (int c : new int[]{14, 22}) r.props.add(new Prop(Prop.Kind.CANTEEN, c, 9));
            r.props.add(Prop.talk(Prop.Kind.CANTEEN, 18, 9,
                    "Похлёбка рассыпалась пылью. Это тоже были лишь видения.",
                    "Эльза|Столовая для слуг. Даже сюда меня не пускали.",
                    "Эльза|Слуги гнали меня от стола, смеялись и презирали. Говорили: «Тебе здесь не место».",
                    "Эльза|Я протягиваю руку к миске, а она пуста. И это тоже галлюцинация."));
        }
    }

    // Уныние: комната 1 — ванна (от горячей воды Эльзе становится плохо), комната 2 — кровать с кошкой (спать нельзя),
    // комната 3 — пустышка с ловушками, комната 4 (грех) — её комната в том доме, она собрана в buildRooms().
    private void decorateSloth(Room r, int n) {
        if (n == 1) {
            r.props.add(Prop.plaque(18, 3, "ВАННАЯ КОМНАТА"));
            Prop bath = Prop.talk(Prop.Kind.BATH, 18, 9,
                    "Горячая вода оказалась слишком тяжёлой для ослабевшего тела.",
                    "Эльза|Ванна... Настоящая, с тёплой водой. Я не мылась столько месяцев.",
                    "Эльза|Я только окунусь на минутку...",
                    "Эльза|Ох... Голова кружится, перед глазами темнеет. Мне плохо...");
            bath.effect = "SICK";
            r.props.add(bath);
        } else if (n == 2) {
            r.props.add(Prop.plaque(20, 3, "СПАЛЬНЯ"));
            Prop bed = Prop.talk(Prop.Kind.BED, 20, 9,
                    "Эльза отвернулась от кровати. Кошка обиженно мяукнула.",
                    "Кошка|Мяу... Мур-мур. (кошка свернулась на подушке и зовёт Эльзу лечь рядом)",
                    "Эльза|Кошка, ты залезла на кровать? Хочешь, чтобы я поспала?",
                    "Эльза|Так тепло... Так хочется закрыть глаза хоть на минуту.",
                    "Эльза|Нет. Если усну — не встану. Нужно идти дальше.");
            bed.effect = "SLEEPY";
            r.props.add(bed);
        }
    }

    // эффект после сценки (ванна, кровать)
    private String propEffect(Prop p) {
        if (p.effect == null) return "";
        switch (p.effect) {
            case "SICK":
                player.hp = Math.max(1, player.hp - 2);
                poisonTimer = Math.max(poisonTimer, 300);
                slowTimer = 300;
                return "Эльзе плохо: минус сердца, яд и замедление";
            case "SLEEPY":
                slowTimer = 300;
                return "Сон навалился на плечи: замедление";
            default:
                return "";
        }
    }

    // ====== СПУСК: НАБОР «СЦЕН» ======
    // Дорога собирается из сцен (сочетаний ловушек). Каждая сцена проходима сама по себе, а между сценами всегда
    // 3+ свободных клетки, поэтому всю дорогу пройти можно. Порядок и набор сцен случайные, чем глубже — тем больше видов.
    private static final int SC_DOUBLE = 0, SC_TRIO = 1, SC_BRIDGE = 2, SC_HILL = 3, SC_TURRET = 4,
            SC_CEIL = 5, SC_SAW = 6, SC_RHYTHM = 7, SC_ROCKS = 8;
    private static final int[] SCENE_MIN_LEVEL = {1, 3, 2, 2, 1, 2, 3, 1, 1};
    private static final int BASE_SCENES = 9;                  // обычные сцены 0..8 выбираются случайно
    // фирменные сцены локаций (ставятся на дороге гарантированно, 2 раза): у каждого греха своя «изюминка»
    private static final int SC_FIREWAVE = 9, SC_GOLDBRIDGE = 10, SC_TWINSAWS = 11, SC_THORNS = 12, SC_CROSSFIRE = 13;
    // фирменная сцена по локациям 1..7 (Леность — течение, Гордыня — лучшее из всего)
    private static final int[] SIGNATURE = {SC_FIREWAVE, SC_GOLDBRIDGE, SC_TWINSAWS, SC_THORNS, SC_CROSSFIRE, SC_BRIDGE, SC_FIREWAVE};
    // любимые обычные сцены локации: они выпадают втрое чаще, поэтому у каждого спуска свой характер
    private static final int[][] FAVORED = {
            {SC_HILL, SC_RHYTHM},                // Гнев: огонь и шипы
            {SC_BRIDGE, SC_DOUBLE},              // Алчность: мосты над пропастью
            {SC_SAW, SC_ROCKS},                  // Зависть: пилы и камни
            {SC_RHYTHM, SC_CEIL},                // Похоть: шипы и стрелы сверху
            {SC_TURRET, SC_CEIL},                // Чревоугодие: стрелы со всех сторон
            {SC_DOUBLE, SC_TRIO},                // Леность: прыжки против течения
            {SC_SAW, SC_TURRET, SC_HILL},        // Гордыня: всего понемногу
    };

    private void buildHardRoad(MapBuilder b, List<java.util.function.Consumer<Room>> tr, int from, int to, int level, Random rnd) {
        int c = from, last = -1, idx = 0;
        int sig = SIGNATURE[Math.min(level, SIGNATURE.length) - 1];
        int[] fav = FAVORED[Math.min(level, FAVORED.length) - 1];
        while (true) {
            int kind;
            if (idx == 1 || idx == 4 || idx == 8) {                           // фирменная сцена локации
                kind = sig;
            } else {
                List<Integer> pool = new ArrayList<>();
                for (int k = 0; k < BASE_SCENES; k++) {
                    if (k == last || level < SCENE_MIN_LEVEL[k]) continue;
                    int weight = 1;
                    for (int f : fav) if (f == k) weight = 3;
                    for (int w = 0; w < weight; w++) pool.add(k);
                }
                kind = pool.get(rnd.nextInt(pool.size()));
            }
            int width = sceneWidth(kind, level, rnd);
            if (c + width > to) break;
            buildScene(kind, c, level, b, tr, rnd);
            last = kind;
            idx++;
            c += width + Math.max(3, 5 - level / 3) + rnd.nextInt(2);        // свободный участок между сценами
        }
    }

    private int sceneWidth(int kind, int level, Random rnd) {
        return switch (kind) {
            case SC_DOUBLE -> 4;
            case SC_TRIO -> 7;
            case SC_BRIDGE -> level >= 5 ? 5 : level >= 4 ? 4 : 3;
            case SC_HILL -> 3;
            case SC_TURRET -> 5;
            case SC_CEIL -> 6;
            case SC_SAW -> 9;
            case SC_RHYTHM -> level >= 4 ? 10 : 7;
            case SC_FIREWAVE -> 9;
            case SC_GOLDBRIDGE -> 7;
            case SC_TWINSAWS -> 13;
            case SC_THORNS -> 13;
            case SC_CROSSFIRE -> 11;
            default -> 7;                                                      // камни
        };
    }

    private void buildScene(int kind, int c, int level, MapBuilder b, List<java.util.function.Consumer<Room>> tr, Random rnd) {
        int ph = rnd.nextInt(200);
        switch (kind) {
            case SC_DOUBLE -> {                       // яма, два шага, шип: два прыжка подряд
                pit(b, c, c);
                b.set(c + 3, 9, '^');
            }
            case SC_TRIO -> {                         // три ямы с ритмом «прыжок - шаг - прыжок»
                pit(b, c, c);
                pit(b, c + 3, c + 3);
                pit(b, c + 6, c + 6);
            }
            case SC_BRIDGE -> {                       // хрупкий мост над ямой; на глубине в нём появляется дыра
                int n = sceneWidth(kind, level, rnd);
                pit(b, c, c + n - 1);
                if (n == 5 && rnd.nextBoolean()) {
                    for (int k : new int[]{0, 1, 3, 4}) { int col = c + k; tr.add(r -> r.traps.add(Trap.crumble(col, 10))); }
                } else {
                    for (int k = 0; k < n; k++) { int col = c + k; tr.add(r -> r.traps.add(Trap.crumble(col, 10))); }
                }
            }
            case SC_HILL -> {                         // холм 1-2-1, на вершине огненная струя: дождись паузы
                b.set(c, 9, '#');
                b.set(c + 1, 8, '#');
                b.set(c + 1, 9, '#');
                b.set(c + 2, 9, '#');
                tr.add(r -> r.traps.add(Trap.flame(c + 1, 7, ph)));
            }
            case SC_TURRET -> {                       // стрелострел в блоке: стрелы летят низко над полом — перепрыгни и залезь на блок
                b.set(c + 4, 9, '#');
                tr.add(r -> r.traps.add(Trap.arrow(c + 4, 9, -1, 0, 5, ph)));
            }
            case SC_CEIL -> {                         // стрелы падают с потолка по очереди: между ними есть безопасный карман
                b.set(c + 1, 1, '#');
                b.set(c + 4, 1, '#');
                tr.add(r -> r.traps.add(Trap.arrow(c + 1, 1, 0, 1, 8, 0)));
                tr.add(r -> r.traps.add(Trap.arrow(c + 4, 1, 0, 1, 8, 85)));
            }
            case SC_SAW -> {                          // пила на полу, а за ней шипы, которые выскакивают
                tr.add(r -> r.traps.add(Trap.saw(c, c + 4, 9)));
                tr.add(r -> r.traps.add(Trap.hidden(c + 8, 9)));
            }
            case SC_RHYTHM -> {                       // шип - скрытые шипы - шип (на глубине ещё одни скрытые)
                b.set(c, 9, '^');
                b.set(c + 6, 9, '^');
                tr.add(r -> r.traps.add(Trap.hidden(c + 3, 9)));
                if (level >= 4) tr.add(r -> r.traps.add(Trap.hidden(c + 9, 9)));
            }
            case SC_FIREWAVE -> {                     // «волна огня»: три струи по очереди, жди паузу у каждой
                tr.add(r -> r.traps.add(Trap.flame(c + 1, 9, 0)));
                tr.add(r -> r.traps.add(Trap.flame(c + 4, 9, 70)));
                tr.add(r -> r.traps.add(Trap.flame(c + 7, 9, 140)));
            }
            case SC_GOLDBRIDGE -> {                   // длинный хрупкий мост, а над ним огненная струя
                pit(b, c, c + 6);
                for (int k = 0; k < 7; k++) { int col = c + k; tr.add(r -> r.traps.add(Trap.crumble(col, 10))); }
                tr.add(r -> r.traps.add(Trap.flame(c + 2, 9, ph)));
            }
            case SC_TWINSAWS -> {                     // две пилы подряд
                tr.add(r -> r.traps.add(Trap.saw(c, c + 4, 9)));
                tr.add(r -> r.traps.add(Trap.saw(c + 8, c + 12, 9)));
            }
            case SC_THORNS -> {                       // терновник: шип, скрытые шипы, шип, скрытые шипы, шип
                b.set(c, 9, '^');
                b.set(c + 6, 9, '^');
                b.set(c + 12, 9, '^');
                tr.add(r -> r.traps.add(Trap.hidden(c + 3, 9)));
                tr.add(r -> r.traps.add(Trap.hidden(c + 9, 9)));
            }
            case SC_CROSSFIRE -> {                    // два стрелострела подряд: перепрыгни стрелу и залезь на блок
                b.set(c + 2, 9, '#');
                b.set(c + 8, 9, '#');
                tr.add(r -> r.traps.add(Trap.arrow(c + 2, 9, -1, 0, 5, ph)));
                tr.add(r -> r.traps.add(Trap.arrow(c + 8, 9, -1, 0, 5, (ph + 85) % 170)));
            }
            default -> {                              // два камня в потолке: подойди — задрожит, отойди — упадёт
                tr.add(r -> r.traps.add(Trap.rock(c + 1, 1)));
                tr.add(r -> r.traps.add(Trap.rock(c + 5, 1)));
            }
        }
    }

    private static final String[] ROAD_SUB = {
            "Спуск Гнева: волны огня и шипы — не торопись",
            "Спуск Алчности: золотой мост над пропастью",
            "Спуск Зависти: пилы и камни, зеркало твоих страхов",
            "Спуск Похоти: терновник — каждый шаг колет",
            "Спуск Чревоугодия: стрелы летят со всех сторон",
            "Спуск Лености: течение сна тянет назад — не останавливайся",
            "Последний спуск — к самому дну: всё, что ты прошла"};

    // Дорога спуска между локациями: длинная дорога ловушек вниз. Назад дороги нет.
    private Room road(int loc) {
        int cols = 90 + loc * 4;
        MapBuilder b = MapBuilder.side(cols);
        b.set(1, 9, '.');
        Random rnd = new Random(System.nanoTime() ^ (loc * 7777L));   // каждая новая игра — новая дорога
        List<java.util.function.Consumer<Room>> roadTraps = new ArrayList<>();
        buildHardRoad(b, roadTraps, 8, cols - 6, loc, rnd);

        Room r = new Room("Спуск · " + SIN_NAMES[loc - 1], b.build());
        r.sideView = true;
        r.theme = SIN_THEMES[loc - 1];
        r.title = "СПУСК";
        r.look = Room.Look.VOID;                               // спуск между локациями — почти чёрный
        r.subtitle = ROAD_SUB[loc - 1];
        if (loc == 6) r.wind = 1;                              // спуск Лености: течение сна тянет назад
        r.goal = Room.Goal.NONE;
        r.exitChar = '2';
        r.exitLocked = false;
        r.addDoor('2', loc < LOCS ? hallKey(loc + 1) : "END", 2, 5);
        for (java.util.function.Consumer<Room> add : roadTraps) add.accept(r);
        return r;
    }

    // подсказка, если игрок стоит у запертой двери коридора
    private String lockedDoorHint() {
        if (!isHall(current)) return null;
        int col = player.centerX() / Room.TILE, row = player.centerY() / Room.TILE;
        for (int dc = -1; dc <= 1; dc++) {
            for (int dr = -1; dr <= 1; dr++) {
                char c = current.tileAt(col + dc, row + dr);
                if (current.lockedDoors.contains(c)) {
                    return current.doneDoors.contains(c) ? "Эта дверь уже пройдена"
                            : "Дверь заперта: сначала пройди предыдущую комнату";
                }
            }
        }
        return null;
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
        boolean training = current.key != null && current.key.startsWith("r1_");   // в первой локации штрафа нет
        if (!training) sinDeed(false);
        String curse = training ? "В первой локации штрафа нет, но дальше он будет!" : applyCurse();
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

    // ====== ДИАЛОГИ ======
    private void checkProps() {
        for (Prop p : current.props) {
            if (p.script == null || p.done) continue;
            boolean near = Math.abs(p.centerX() - player.centerX()) < 90
                    && (current.sideView || Math.abs(p.centerY() - player.centerY()) < 90);
            if (near) { startDialog(p); return; }
        }
    }

    private void startDialog(Prop p) {
        dialog = p.script;
        dialogProp = p;
        dialogIdx = 0;
        dialogT = 0;
        up = down = left = right = jump = false;
        player.moving = false;
        checkTimer = 0;
        checkVase = null;
    }

    private String dialogSpeaker() {
        String l = dialog[dialogIdx];
        int i = l.indexOf('|');
        return i < 0 ? "" : l.substring(0, i);
    }

    private String dialogText() {
        String l = dialog[dialogIdx];
        int i = l.indexOf('|');
        return i < 0 ? l : l.substring(i + 1);
    }

    // первое нажатие дописывает реплику, второе — переходит к следующей
    private void advanceDialog() {
        if (dialog == null) return;
        int full = dialogText().length() * DIALOG_TYPE;
        if (dialogT < full) { dialogT = full; return; }
        if (++dialogIdx >= dialog.length) endDialog(); else dialogT = 0;
    }

    private void endDialog() {
        Prop p = dialogProp;
        dialog = null;
        dialogProp = null;
        if (p == null) return;
        if (p.mash) {                                        // после сценки — «вырвись»; done выставит finishMash
            startMash(p);
            return;
        }
        p.done = true;
        String txt = p.endText == null ? "" : p.endText;
        String eff = propEffect(p);
        if (!eff.isEmpty()) txt = txt.isEmpty() ? eff : txt + "\n" + eff;
        if (!txt.isEmpty()) showMessage(txt);
    }

    // ====== МИНИ-ИГРА «ВЫРВИСЬ» ======
    // Шкала всё время падает (он держит), каждое нажатие E / ПРОБЕЛ её поднимает. Заполнила — вырвалась.
    // Шкала упала до нуля или вышло время — не удержалась: плохой поступок (грех Похоти) и проклятие.
    private void startMash(Prop p) {
        mashProp = p;
        messageTimer = 0;                                    // старые сообщения не мешают шкале
        mashProgress = 0.4;
        mashTimer = MASH_TIME;
        mashPulse = 0;
        mashDown.clear();
    }

    private void mashPress() {
        mashProgress = Math.min(1.0, mashProgress + MASH_GAIN);
        mashPulse = 6;
        if (mashProgress >= 1.0) finishMash(true);           // победа фиксируется сразу, до того как шкала успеет упасть
    }

    private void updateMash() {
        if (mashPulse > 0) mashPulse--;
        mashTimer--;
        mashProgress -= MASH_DECAY;
        if (mashProgress <= 0 || mashTimer <= 0) finishMash(false);
    }

    private void finishMash(boolean win) {
        Prop p = mashProp;
        mashProp = null;
        mashDown.clear();
        p.done = true;                                       // персонаж уходит (исчезает)
        String effect;
        if (win) {
            player.hp = player.maxHp;
            effect = "Сердца восстановлены";
        } else {
            int[] id = parseTrial(current.key);              // проигрыш в любой комнате локации = грех этой локации (s1..s7)
            String sinKey = id == null ? current.key : "s" + id[0];
            if (sins.bad(sinKey)) System.out.println("Плохих поступков: " + sins.badCount());
            effect = applyCurse();
        }
        unlockExit(null);
        showMessage((win ? p.winText : p.loseText) + "\n" + effect);
    }

    private void drawMash(Graphics2D g) {
        int top = 82;                                        // панель вверху, чтобы не закрывать Эльзу и персонажа на полу
        g.setColor(new Color(0, 0, 0, 175));
        g.fillRect(0, top, Room.VIEW_W, 150);
        g.setColor(current.theme.accent);
        g.fillRect(0, top + 150, Room.VIEW_W, 3);

        g.setFont(new Font("Monospaced", Font.BOLD, 34));
        g.setColor(new Color(255, 90, 90));
        drawCentered(g, "ВЫРВИСЬ!", top + 50);
        g.setFont(new Font("Monospaced", Font.BOLD, 18));
        g.setColor(Color.WHITE);
        drawCentered(g, "Жми E или ПРОБЕЛ как можно быстрее — не дай себя удержать", top + 78);

        int w = 560, h = 26, x = (Room.VIEW_W - w) / 2, y = top + 98;
        double pr = Math.max(0, Math.min(1, mashProgress));
        g.setColor(new Color(20, 20, 30));
        g.fillRect(x - 3, y - 3, w + 6, h + 6);
        g.setColor(Theme.mix(new Color(220, 60, 60), new Color(90, 220, 120), pr));
        g.fillRect(x, y, (int) (w * pr), h);
        if (mashPulse > 0) {
            g.setColor(new Color(255, 255, 255, 90));
            g.fillRect(x, y, (int) (w * pr), h);
        }
        g.setColor(Color.WHITE);
        g.drawRect(x - 3, y - 3, w + 5, h + 5);
        g.setColor(new Color(240, 200, 60));                 // оставшееся время
        g.fillRect(x, y + h + 10, (int) (w * (mashTimer / (double) MASH_TIME)), 5);
    }

    private void drawDialog(Graphics2D g) {
        String full = dialogText();
        int n = Math.min(full.length(), dialogT / DIALOG_TYPE);
        int h = 130, y = Room.VIEW_H - h;
        g.setColor(new Color(0, 0, 0, 215));
        g.fillRect(0, y, Room.VIEW_W, h);
        g.setColor(current.theme.accent);
        g.fillRect(0, y, Room.VIEW_W, 3);
        g.setFont(new Font("Monospaced", Font.BOLD, 20));
        g.setColor(new Color(240, 200, 60));
        g.drawString(dialogSpeaker(), 40, y + 32);
        g.setFont(new Font("Monospaced", Font.BOLD, 22));
        g.setColor(Color.WHITE);
        drawWrapped(g, full, n, 40, y + 66, Room.VIEW_W - 80, 30);
        g.setFont(new Font("Monospaced", Font.PLAIN, 13));
        g.setColor(new Color(150, 150, 180));
        String hint = "ПРОБЕЛ — дальше    ESC — пропустить";
        g.drawString(hint, Room.VIEW_W - g.getFontMetrics().stringWidth(hint) - 14, y + h - 10);
    }

    // перенос по словам; печатает только первые n символов (строки не «прыгают» при наборе)
    private static void drawWrapped(Graphics2D g, String full, int n, int x, int y, int maxW, int lh) {
        FontMetrics fm = g.getFontMetrics();
        int pos = 0;
        while (pos < full.length()) {
            int end = pos, lastSpace = -1;
            while (end < full.length() && fm.stringWidth(full.substring(pos, end + 1)) <= maxW) {
                if (full.charAt(end) == ' ') lastSpace = end;
                end++;
            }
            int lineEnd = end >= full.length() ? full.length() : (lastSpace > pos ? lastSpace : end);
            if (n > pos) g.drawString(full.substring(pos, Math.min(lineEnd, n)), x, y);
            y += lh;
            pos = lineEnd;
            if (pos < full.length() && full.charAt(pos) == ' ') pos++;
        }
    }

    // ====== ИНВЕНТАРЬ ======
    private void resetInventory() {
        inventory = new Inventory();
        Item sword = Item.sword("Меч", "Урон 2. Закалённая сталь", 2, new Color(200, 215, 235));
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
        for (int loc = 1; loc <= LOCS; loc++) refreshHallLocks(loc);
        pet = null;
        slowTimer = poisonTimer = darkTimer = weakTimer = 0;
        slowPermanent = false;
        checkTimer = 0;
        checkVase = null;
        dialog = null;
        dialogProp = null;
        mashProp = null;
        mashDown.clear();
        won = false;
        victoryTimer = 0;
        epilogue = null;
        sins.reset();
        respawnCol = 2;
        respawnRow = 5;
        enterRoom(rooms.get(hallKey(1)));                        // Эльза появляется в коридоре первой локации
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
        showMessage("Коридор Гнева. WASD — ходьба, берегись стрел. Первая дверь — вверху слева");
        cutscene = new Cutscene();                // сначала катсцена №1, потом игра
        timer.start();
    }

    public void stop() {
        timer.stop();
        adminHide();
        godMode = false;
        cutscene = null;
        epilogue = null;
        victoryTimer = 0;
        dialog = null;
        dialogProp = null;
        mashProp = null;
        mashDown.clear();
        up = down = left = right = jump = false;
        invOpen = false;
    }
    // Пауза (меню по ESC): мир замирает, но ничего не сбрасывается — потом можно продолжить с того же места
    public void pause() {
        timer.stop();
        adminHide();
        up = down = left = right = jump = false;
        mashDown.clear();
    }

    public void resume() {
        timer.start();
        requestFocusInWindow();
    }

    // Где остановился игрок — подпись на кнопке «ПРОДОЛЖИТЬ» в главном меню
    public String progressText() {
        if (current == null) return "";
        String k = current.key == null ? "" : current.key;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\d").matcher(k);
        return (m.find() ? "Локация " + m.group() + " · " : "") + current.name;
    }
    private void enterRoom(Room r) {
        current = r;
        titleTimer = r.title != null ? 170 : 0;
        if (r.choice != null) r.choice.showCat = pet == null;     // кошка на плечах — вторую у рыбы не рисуем
    }

    // ================= ЛОГИКА =================
    private void update() {
        if (cutscene != null) {                                  // идёт катсцена — мир стоит
            cutscene.update();
            if (cutscene.finished()) cutscene = null;
            return;
        }
        if (epilogue != null) {                                  // идёт эпилог — мир стоит
            epilogue.update();
            if (epilogue.finished()) {
                epilogue = null;
                window.showMenu();
            }
            return;
        }
        if (won && ++victoryTimer >= EPILOGUE_DELAY) {           // экран Ад/Рай показан ~6 секунд — запускаем эпилог
            epilogue = new EndingCutscene(ending);
            return;
        }
        if (messageTimer > 0) messageTimer--;
        if (titleTimer > 0) titleTimer--;
        if (invOpen || won) return;                              // в инвентаре мир стоит
        if (dialog != null) { dialogT++; return; }               // во время реплики мир тоже стоит
        if (mashProp != null) { updateMash(); return; }          // во время «вырвись» мир тоже стоит

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

        // ----- кошка из коридора Алчности: сама подходит к Эльзе и остаётся с ней до конца игры -----
        Cat stray = current.cat;
        if (stray != null && pet == null && isHall(current) && stray.state == Cat.State.SITTING
                && Math.hypot(stray.centerX() - player.centerX(), stray.centerY() - player.centerY()) < 100) {
            stray.state = Cat.State.FOLLOWING;
            stray.onShoulder = true;                          // как и спасённый кот — на плечах: не упадёт в ямы и не отстанет
            pet = stray;
            current.cat = null;
            showMessage("Из тени вышла кошка и запрыгнула тебе на плечи. Теперь она с тобой до конца пути. Мяу!");
        }

        // ----- кот -----
        if (current.cat != null && current.sideView) current.cat.move(current, 0, 4);   // сидит на полу
        if (pet != null) pet.follow(current, player);
        if (current.cat != null && current.cat.flash > 0) current.cat.flash--;

        if (pet != null && isGluttonyRoom(current) && tick % 1200 == 0 && messageTimer == 0)
            showMessage("Кошка мяукает: «Мяу-мяу! Рыба... в последней комнате!»");
        checkProps();
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
        if (!godMode) player.hp--;
        if (player.hp <= 0) { faint(); return; }
        showMessage("Ты упал в яму!" + (godMode ? "" : " Минус сердце"));
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

        Decision near = nearChoice();                           // L — второй вариант выбора
        if (near != null) {
            resolveChoice(near, near.optL);
            return;
        }

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
        sinDeed(false);
        current.cat = null;
        slowPermanent = true;                                 // постоянное замедление
        Item reward = Item.sword("Тёмный меч", "Урон 4. Достался тебе от кота", 4, new Color(150, 90, 220));
        if (inventory.add(reward)) {
            showMessage("Ты получил «Тёмный меч» (I — экипировать), но навсегда замедлен. Ворота открыты");
        } else {
            showMessage("Инвентарь полон, меч потерян. Замедление осталось навсегда. Ворота открыты");
        }
    }

    // поступок в текущей комнате. Для других испытаний вызывай так же: sinDeed(false) — плохой, sinDeed(true) — хороший
    private void sinDeed(boolean good) {
        if (good) sins.good(current.key);
        else if (sins.bad(current.key)) System.out.println("Плохих поступков: " + sins.badCount());
    }

    // ближайший невыбранный «выбор» в радиусе действия
    private Decision nearChoice() {
        Decision c = current.choice;
        if (c == null || c.decided) return null;
        return Math.hypot(c.centerX() - player.centerX(), c.centerY() - player.centerY()) < 80 ? c : null;
    }

    // игрок выбрал вариант: засчитываем добрый / плохой поступок, открываем ворота
    private void resolveChoice(Decision c, Decision.Option opt) {
        if (c.decided) return;
        c.pick(opt);
        sinDeed(opt.good());
        String effect;
        if (opt.good()) {
            player.hp = player.maxHp;
            effect = "Чистая совесть: сердца восстановлены";
        } else {
            effect = applyCurse();
        }
        unlockExit(null);
        showMessage(opt.result() + "\n" + effect);
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
        Decision ch = nearChoice();
        if (ch != null) {                                     // E — первый вариант выбора
            resolveChoice(ch, ch.optE);
            return;
        }
        if (nearCat()) {                                      // спасти кота: он садится на плечи
            Cat c = current.cat;
            c.state = Cat.State.FOLLOWING;
            c.onShoulder = true;
            pet = c;
            sinDeed(true);
            current.cat = null;
            showMessage("Ты спас кота. Он сидит у тебя на плечах. Мяу! Ворота открыты");
            return;
        }
        Vase v = nearVase();
        if (v != null) {                                      // безопасная проверка вазы
            checkVase = v;
            checkTotal = Math.max(1, current.checkTime);
            checkTimer = checkTotal;
        }
    }

    private void hurtPlayer(int dmg, int fromX, int fromY) {
        if (godMode) return;                                  // админ: бессмертие
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
        if (r.key != null && r.key.startsWith("road")) {          // спустился в следующую локацию
            int loc = r.key.charAt(4) - '0';
            if (loc < LOCS) showMessage("Ты спустился глубже: локация " + (loc + 1));
            return;
        }
        int[] id = parseTrial(r.key);
        if (id == null || !done.add(r.key)) return;
        int loc = id[0];
        Room hall = rooms.get(hallKey(loc));
        hall.doneDoors.add((char) ('0' + id[1]));
        sins.good(r.key);                                          // для комнат-грехов (s1..s7); для остальных ничего не делает
        refreshHallLocks(loc);
        int n = roomsDone(loc);
        if (n == 4 || loc == LOCS) {
            hall.exitLocked = false;
            showMessage("Локация пройдена! В конце коридора открыта дверь СПУСК");
        } else {
            showMessage("Комната пройдена (" + n + "/4). Откроется следующая дверь");
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
                ending = sins.ending();
                won = true;
                up = down = left = right = jump = false;
                return;
            }
            enterRoom(rooms.get(door.targetRoom()));
            if (pet != null && isGluttonyRoom(current))
                showMessage("Кошка на плечах мяукает: «Мяу! Иди в последнюю комнату — там рыба!»");
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
        if (cutscene != null) {
            cutscene.draw((Graphics2D) g);
            return;
        }
        if (epilogue != null) {
            epilogue.draw((Graphics2D) g);
            return;
        }
        int cx = (int) Math.round(camX);
        int cy = (int) Math.round(camY);

        Graphics2D world = (Graphics2D) g.create();
        int shake = mashProp != null ? (tick % 2 == 0 ? 3 : -3) : 0;   // экран трясётся, пока Эльза вырывается
        world.translate(-cx + shake, -cy);
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
            world.fillRect(bx, by, (int) (44 * (1 - checkTimer / (double) checkTotal)), 6);
        }
        world.dispose();

        Graphics2D overlay = (Graphics2D) g.create();
        if (current.sideView) drawVignette(overlay);
        if (darkTimer > 0) drawDarkness(overlay, player.centerX() - cx, player.centerY() - cy);
        overlay.dispose();

        drawHud((Graphics2D) g);
        drawTitleCard((Graphics2D) g);
        if (dialog != null) drawDialog((Graphics2D) g);
        if (mashProp != null) drawMash((Graphics2D) g);
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
        boolean heaven = ending == Sins.Ending.HEAVEN;
        int W = Room.VIEW_W, H = Room.VIEW_H, cx = W / 2;

        g.setPaint(heaven ? new GradientPaint(0, 0, new Color(255, 250, 215), 0, H, new Color(150, 200, 255))
                : new GradientPaint(0, 0, new Color(20, 0, 0), 0, H, new Color(170, 30, 10)));
        g.fillRect(0, 0, W, H);

        if (heaven) {                                                    // лучи света и фигура бога
            g.setColor(new Color(255, 255, 255, 70));
            for (int i = -3; i <= 3; i++)
                g.fillPolygon(new int[]{cx, cx + i * 90 - 30, cx + i * 90 + 30}, new int[]{40, H, H}, 3);
            g.setColor(new Color(255, 230, 120));
            g.fillOval(cx - 40, 56, 80, 14);                             // нимб
            g.setColor(new Color(250, 225, 195));
            g.fillRect(cx - 18, 76, 36, 34);                             // голова
            g.setColor(new Color(235, 235, 235));
            g.fillRect(cx - 26, 70, 52, 12);                             // волосы и борода
            g.fillRect(cx - 18, 100, 36, 24);
            g.setColor(Color.WHITE);
            g.fillRect(cx - 40, 122, 80, 120);                           // одеяние
            g.fillRect(cx - 100, 128, 60, 24);
            g.fillRect(cx + 40, 128, 60, 24);
            g.setColor(Color.BLACK);
            g.fillRect(cx - 10, 90, 4, 4);
            g.fillRect(cx + 6, 90, 4, 4);
        } else {                                                         // демон
            g.setColor(new Color(40, 0, 0));
            g.fillRect(0, H - 70, W, 70);
            g.setColor(new Color(255, 120, 20));
            for (int i = 0; i < 12; i++) g.fillRect(i * 90 + 10, H - 70 - (i * 37 % 30), 40, 30);
            g.setColor(new Color(120, 15, 25));
            g.fillRect(cx - 40, 110, 80, 130);                           // тело
            g.fillRect(cx - 30, 56, 60, 56);                             // голова
            g.setColor(new Color(240, 230, 200));
            g.fillPolygon(new int[]{cx - 30, cx - 52, cx - 16}, new int[]{62, 20, 56}, 3);     // рога
            g.fillPolygon(new int[]{cx + 30, cx + 52, cx + 16}, new int[]{62, 20, 56}, 3);
            g.setColor(new Color(255, 220, 40));
            g.fillRect(cx - 20, 76, 14, 8);
            g.fillRect(cx + 6, 76, 14, 8);
            g.setColor(Color.BLACK);
            g.fillRect(cx - 16, 100, 32, 6);
            g.setColor(new Color(120, 15, 25));
            g.fillRect(cx - 110, 120, 70, 20);
            g.fillRect(cx + 40, 120, 70, 20);
        }

        g.setFont(new Font("Monospaced", Font.BOLD, 56));
        g.setColor(heaven ? new Color(60, 90, 160) : new Color(255, 200, 60));
        drawCentered(g, heaven ? "РАЙ" : "АД", 300);
        g.setFont(new Font("Monospaced", Font.BOLD, 18));
        g.setColor(heaven ? new Color(40, 60, 110) : Color.WHITE);
        drawCentered(g, "Добрых: " + sins.goodCount() + "   Плохих: " + sins.badCount()
                + "   (Рай — от " + Sins.HEAVEN_LIMIT + " добрых, Ад — от " + Sins.HELL_LIMIT + " плохих)", 340);
        g.setFont(new Font("Monospaced", Font.PLAIN, 16));
        g.setColor(heaven ? new Color(40, 60, 110) : new Color(230, 200, 190));
        drawCentered(g, "Enter — дальше (эпилог начнётся сам)", 380);
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
        if (isHall(r)) {
            return r.exitLocked ? (r.key.charAt(1) - '0' == LOCS ? "Цель: войди в большую комнату Гордыни"
                    : "Цель: пройди 4 комнаты по порядку (" + roomsDone(r.key.charAt(1) - '0') + "/4)")
                    : "Цель: спустись — дверь СПУСК в конце коридора";
        }
        if (r.key != null && r.key.startsWith("road")) return "Цель: пройди дорогу ловушек и спустись глубже";
        String open = r.gateCol >= 0 ? "Ворота открыты — пройди ловушки к выходу" : "Выход справа открыт";
        return switch (r.goal) {
            case CANDLE  -> r.exitLocked ? "Цель: найди свечу среди ваз" : open;
            case COLLECT -> r.exitLocked ? "Цель: " + r.goalLabel + " (" + r.takenCount() + "/" + r.pickups.size() + ")" : open;
            case TOTEMS  -> r.exitLocked ? "Цель: разбей тотемы (осталось " + r.totemsLeft() + ")" : open;
            case CAT     -> r.exitLocked ? "Цель: спаси кота (E) или убей его (L)" : open;
            case CHOICE  -> r.exitLocked ? "Цель: сделай выбор (E или L)" : open;
            case ESCAPE  -> r.exitLocked ? "Цель: подойди к нему и вырвись (E / ПРОБЕЛ)" : open;
            default      -> "Цель: дойди до выхода справа";
        };
    }

    private void drawRight(Graphics2D g, String text, int y) {
        int w = g.getFontMetrics().stringWidth(text);
        g.drawString(text, Room.VIEW_W - w - 12, y);
    }

    // Шкала грехов: 7 меток по порядку локаций (Гнев ... Гордыня).
    // тусклая — выбор ещё не сделан, светлая с галочкой — добрый поступок, тёмно-красная с крестом — плохой.
    private void drawSinGauge(Graphics2D g) {
        int x0 = 196, y0 = 26, w = 22, h = 12, gap = 5;
        g.setFont(new Font("Monospaced", Font.BOLD, 11));
        g.setColor(new Color(200, 190, 215));
        g.drawString("ГРЕХИ", x0, 21);
        for (int i = 0; i < SIN_THEMES.length; i++) {
            int x = x0 + i * (w + gap);
            Theme th = SIN_THEMES[i];
            int st = sins.state(i);
            g.setColor(new Color(8, 6, 12));
            g.fillRect(x - 1, y0 - 1, w + 2, h + 2);                       // рамка
            if (st == 0) {
                g.setColor(Theme.mix(th.base, Color.BLACK, 0.78));
                g.fillRect(x, y0, w, h);
                g.setColor(Theme.mix(th.base, Color.BLACK, 0.40));
                g.fillRect(x, y0, w, 2);
            } else if (st == 1) {
                g.setColor(Theme.mix(th.accent, Color.WHITE, 0.45));
                g.fillRect(x, y0, w, h);
                g.setColor(Color.WHITE);
                g.fillRect(x, y0, w, 2);
                g.setColor(new Color(30, 110, 60));                       // галочка
                g.fillRect(x + 7, y0 + 6, 2, 3);
                g.fillRect(x + 9, y0 + 8, 2, 2);
                g.fillRect(x + 11, y0 + 5, 2, 4);
                g.fillRect(x + 13, y0 + 3, 2, 3);
            } else {
                g.setColor(new Color(70, 8, 18));
                g.fillRect(x, y0, w, h);
                g.setColor(new Color(150, 20, 34));
                g.fillRect(x, y0, w, 2);
                Stroke old = g.getStroke();
                g.setStroke(new BasicStroke(2f));
                g.setColor(new Color(255, 80, 80));                       // крест
                g.drawLine(x + 6, y0 + 3, x + w - 7, y0 + h - 3);
                g.drawLine(x + w - 7, y0 + 3, x + 6, y0 + h - 3);
                g.setStroke(old);
            }
        }
        int bad = sins.badCount();
        g.setFont(new Font("Monospaced", Font.BOLD, 12));
        g.setColor(bad >= Sins.HELL_LIMIT - 1 ? new Color(255, 100, 100) : new Color(210, 200, 225));
        g.drawString("Ад " + bad + "/" + Sins.HELL_LIMIT, x0 + SIN_THEMES.length * (w + gap) + 4, y0 + 10);
    }

    private void drawHud(Graphics2D g) {
        g.setColor(new Color(0, 0, 0, 110));
        g.fillRect(0, 0, Room.VIEW_W, 76);
        for (int i = 0; i < player.maxHp; i++) drawHeart(g, 12 + i * 26, 10, i < player.hp);
        drawSinGauge(g);

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

        if (messageTimer > 0) {                                // сообщение сверху (чтобы не закрывать сцену): строки разделены \n
            String[] lines = message.split("\n");
            for (int i = 0; i < lines.length; i++) {
                drawBanner(g, lines[i], 150 + i * 36, new Color(120, 140, 190));
            }
        }
        if (!invOpen) {
            Room.Sign sign = nearSign();
            if (sign != null) drawBanner(g, sign.text(), 108, current.theme.accent);
            Decision nc = nearChoice();
            if (nc != null) {
                drawBanner(g, "E — " + nc.optE.label() + "      L — " + nc.optL.label(), Room.VIEW_H - 24, current.theme.accent);
            } else if (nearCat()) {
                drawBanner(g, "E — спасти кота      L — ударить мечом", Room.VIEW_H - 24, new Color(240, 200, 60));
            } else if (checkTimer == 0 && nearVase() != null) {
                drawBanner(g, "E — проверить (" + String.format("%.1f", current.checkTime / 60.0) + " с, не двигайся)      L — разбить (штраф!)", Room.VIEW_H - 24, new Color(240, 200, 60));
            }
            String lockHint = lockedDoorHint();
            if (lockHint != null) drawBanner(g, lockHint, Room.VIEW_H - 24, new Color(200, 80, 80));
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
            g.rotate(-Math.PI / 4);
            g.scale(0.85, 0.85);
            g.translate(-30, 0);
            SwordArt.draw(g, it.blade);
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

    // ================= АДМИН-ПАНЕЛЬ (Ctrl+Shift+5) =================
    // Окно с кнопками лежит в Main.java (класс AdminPanel), а вся логика — здесь, чтобы иметь доступ к состоянию игры.
    private AdminPanel admin;
    private boolean godMode;                                  // бессмертие: удары и ямы не отнимают сердца

    // Ctrl+Shift+5 — по коду клавиши, по расширенному коду (другие раскладки) и по символу ('5' / '%'), плюс цифра на NumPad
    static boolean isAdminCombo(KeyEvent e) {
        if (!e.isControlDown() || !e.isShiftDown()) return false;
        char ch = e.getKeyChar();
        return e.getKeyCode() == KeyEvent.VK_5 || e.getKeyCode() == KeyEvent.VK_NUMPAD5
                || e.getExtendedKeyCode() == KeyEvent.VK_5 || ch == '5' || ch == '%';
    }

    // Ловим сочетание на уровне всего окна, а не только когда фокус на игровой панели
    // (фокус легко уходит на кнопку или диалог, и тогда обычный KeyListener молчит).
    private final KeyEventDispatcher adminHotkey = ev -> {
        if (ev.getID() == KeyEvent.KEY_PRESSED && isShowing() && isAdminCombo(ev)) {
            toggleAdmin();
            return true;                                      // нажатие «съедено», дальше в игру не идёт
        }
        // ESC: если фокус ушёл с игровой панели, обычный KeyListener молчит — обрабатываем здесь
        if (ev.getID() == KeyEvent.KEY_PRESSED && ev.getKeyCode() == KeyEvent.VK_ESCAPE
                && isShowing() && !isFocusOwner() && windowActive()) {
            keyPressed(ev);
            requestFocusInWindow();                           // и возвращаем фокус игре
            return true;
        }
        return false;
    };

    // активно ли окно игры (отдельным методом, потому что лямбда в поле выше не может читать final-поле window)
    private boolean windowActive() {
        Window w = SwingUtilities.getWindowAncestor(this);
        return w != null && w.isActive();
    }

    @Override public void addNotify() {
        super.addNotify();
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(adminHotkey);
    }

    @Override public void removeNotify() {
        KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(adminHotkey);
        super.removeNotify();
    }

    private void toggleAdmin() {
        if (current == null) return;
        try {
            if (admin == null) admin = new AdminPanel(this, window);
            if (admin.isVisible()) {
                admin.close();
                showMessage("Админ-панель закрыта");
            } else {
                admin.open();
                showMessage("Админ-панель открыта (Ctrl+Shift+5 — закрыть)");
            }
        } catch (Throwable ex) {                              // не молчим: показываем причину прямо на экране
            ex.printStackTrace();
            admin = null;
            showMessage("Админ-панель не открылась: " + ex);
            try {
                java.io.StringWriter sw = new java.io.StringWriter();
                ex.printStackTrace(new java.io.PrintWriter(sw));
                JOptionPane.showMessageDialog(this, sw.toString(), "Ошибка админ-панели", JOptionPane.ERROR_MESSAGE);
            } catch (Throwable ignore) { }
        }
        requestFocusInWindow();
    }

    private void adminHide() { if (admin != null) admin.close(); }

    // сбрасывает всё «временное» состояние перед скачком в другое место
    private void adminReset() {
        cutscene = null;
        epilogue = null;
        victoryTimer = 0;
        dialog = null;
        dialogProp = null;
        mashProp = null;
        mashDown.clear();
        checkTimer = 0;
        checkVase = null;
        invOpen = false;
        up = down = left = right = jump = false;
    }

    // номер локации по ключу комнаты (h3, road3, r3_1, s3); -1 — не принадлежит локации
    private static int locOf(String key) {
        if (key == null) return -1;
        int[] id = parseTrial(key);
        if (id != null) return id[0];
        if (key.matches("h[1-7]")) return key.charAt(1) - '0';
        if (key.matches("road[1-7]")) return key.charAt(4) - '0';
        return -1;
    }

    private void teleportTo(Room r, int col, int row) {
        adminReset();
        won = false;
        enterRoom(r);
        respawnCol = col;
        respawnRow = row;
        player.setTile(col, row);
        player.vy = 0;
        player.onGround = false;
        player.invul = 60;
        player.facing = r.sideView ? Player.Dir.RIGHT : Player.Dir.DOWN;
        safeX = player.x;
        safeY = player.y;
        if (pet != null && !pet.onShoulder) { pet.x = player.x; pet.y = player.y; }
        snapCamera();
    }

    // ставит игрока на клетку с символом ch (дверь выхода); false — такой клетки в комнате нет
    private boolean teleportToTile(char ch) {
        for (int r = 0; r < current.rows(); r++) {
            for (int c = 0; c < current.cols(); c++) {
                if (current.tileAt(c, r) != ch) continue;
                player.setTile(c, r);
                player.vy = 0;
                player.onGround = false;
                safeX = player.x;
                safeY = player.y;
                snapCamera();
                return true;
            }
        }
        return false;
    }

    // засчитывает все комнаты локации и открывает дверь СПУСК
    private void completeLocation(int loc) {
        Room hall = rooms.get(hallKey(loc));
        for (int n = 1; n <= 4; n++) {
            if (loc == LOCS && n < 4) continue;               // у Гордыни одна комната
            String key = trialKey(loc, n);
            done.add(key);
            hall.doneDoors.add((char) ('0' + n));
            sins.good(key);                                   // для комнат-грехов пропуск = добрый исход
        }
        refreshHallLocks(loc);
        hall.exitLocked = false;
    }

    // ----- то, что вызывает AdminPanel -----
    boolean adminGod() { return godMode; }

    void adminSetGod(boolean on) {
        godMode = on;
        showMessage("Админ: бессмертие " + (on ? "включено" : "выключено"));
    }

    void adminHeal() {
        player.hp = player.maxHp;
        slowTimer = poisonTimer = darkTimer = weakTimer = 0;
        slowPermanent = false;
        showMessage("Админ: сердца восстановлены, эффекты сняты");
    }

    String adminStatus() {
        if (current == null) return " ";
        return "<html>" + current.name + " [" + current.key + "]<br>Плохих поступков: " + sins.badCount()
                + " · добрых: " + sins.goodCount() + (godMode ? " · БЕССМЕРТИЕ" : "") + "</html>";
    }

    // все комнаты по порядку прохождения
    java.util.List<String> adminRoomKeys() {
        List<String> out = new ArrayList<>();
        for (int loc = 1; loc <= LOCS; loc++) {
            out.add(hallKey(loc));
            if (loc != LOCS) for (int n = 1; n <= 3; n++) out.add(trialKey(loc, n));
            out.add(trialKey(loc, 4));
            out.add(roadKey(loc));
        }
        out.removeIf(k -> !rooms.containsKey(k));
        return out;
    }

    String adminRoomName(String key) {
        Room r = rooms.get(key);
        return r == null ? key : r.name;
    }

    // телепорт в начало комнаты (в коридор — туда, где игра стартует; в остальные — как после входа в дверь)
    void adminGoto(String key) {
        Room r = rooms.get(key);
        if (r == null) return;
        if (isHall(r)) teleportTo(r, 2, 5); else teleportTo(r, 3, 9);
        showMessage("Админ: телепорт — " + r.name);
    }

    // пропустить текущее испытание: выбор делается «добрым», выход открывается, игрок встаёт на дверь
    void adminSkipRoom() {
        if (current == null || won) return;
        if (isHall(current)) { adminSkipLocation(); return; }
        adminReset();
        Decision c = current.choice;
        if (c != null && !c.decided) resolveChoice(c, c.optE.good() ? c.optE : c.optL);
        current.exitLocked = false;
        if (!teleportToTile(current.exitChar)) {
            showMessage("Админ: выход открыт, но двери в этой комнате нет");
            return;
        }
        showMessage("Админ: комната пропущена");
    }

    // пройти все четыре комнаты текущей локации и встать у двери СПУСК
    void adminSkipLocation() {
        if (current == null || won) return;
        int loc = locOf(current.key);
        if (loc < 0) return;
        completeLocation(loc);
        if (!current.key.startsWith("road")) teleportTo(rooms.get(hallKey(loc)), HALL_COLS - 4, 6);
        showMessage("Админ: локация " + loc + " пройдена, дверь СПУСК открыта");
    }

    // засчитать все 7 локаций (двери коридоров открыты, дальше можно идти вручную или телепортом)
    void adminSkipAll() {
        if (current == null) return;
        for (int loc = 1; loc <= LOCS; loc++) completeLocation(loc);
        showMessage("Админ: все испытания засчитаны");
    }

    // финал: forced = null — концовка по реальным выборам, иначе принудительно Рай / Ад
    void adminFinish(Sins.Ending forced) {
        if (current == null) return;
        adminReset();
        ending = forced != null ? forced : sins.ending();
        won = true;
    }

    // ================= КЛАВИШИ =================
    // Буквы проверяем и по коду клавиши, и по символу — чтобы L и E работали и на русской раскладке (Д, У).
    private static boolean isKey(KeyEvent e, int code, char ru) {
        return e.getKeyCode() == code || Character.toLowerCase(e.getKeyChar()) == ru;
    }

    @Override
    public void keyPressed(KeyEvent e) {
        int k = e.getKeyCode();

        if (isAdminCombo(e)) {                                           // админ-панель — Ctrl+Shift+5
            toggleAdmin();
            return;
        }

        if (cutscene != null) {
            if (k == KeyEvent.VK_ESCAPE) cutscene.skip();
            else if (k == KeyEvent.VK_SPACE || k == KeyEvent.VK_ENTER || isKey(e, KeyEvent.VK_E, 'у')) cutscene.advance();
            return;
        }

        if (epilogue != null) {
            if (k == KeyEvent.VK_ESCAPE) epilogue.skip();
            else if (k == KeyEvent.VK_SPACE || k == KeyEvent.VK_ENTER || isKey(e, KeyEvent.VK_E, 'у')) epilogue.advance();
            return;
        }

        if (won) {                                                       // Enter / ESC на экране Ад/Рай — сразу к эпилогу
            if (k == KeyEvent.VK_ENTER || k == KeyEvent.VK_ESCAPE) victoryTimer = EPILOGUE_DELAY;
            return;
        }

        if (mashProp != null) {                                          // «вырвись»: считаем только новые нажатия
            int code = isKey(e, KeyEvent.VK_E, 'у') ? KeyEvent.VK_E : k;
            if ((code == KeyEvent.VK_SPACE || code == KeyEvent.VK_E || code == KeyEvent.VK_ENTER) && mashDown.add(code)) mashPress();
            return;
        }

        if (dialog != null) {                                            // идёт реплика Эльзы
            if (k == KeyEvent.VK_ESCAPE) endDialog();
            else if (k == KeyEvent.VK_SPACE || k == KeyEvent.VK_ENTER || isKey(e, KeyEvent.VK_E, 'у')) advanceDialog();
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
            case KeyEvent.VK_ESCAPE -> SwingUtilities.invokeLater(window::showPause);   // после обработки события, иначе ESC сразу «нажмёт» кнопку меню паузы
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {
        mashDown.remove(e.getKeyCode());
        if (isKey(e, KeyEvent.VK_E, 'у')) mashDown.remove(KeyEvent.VK_E);
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