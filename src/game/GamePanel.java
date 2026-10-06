package game;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.io.IOException;
import java.io.File;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
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
    private static final int    ILLUSION_HITS = 3;        // сколько ударов мечом нужно, чтобы развеять иллюзию столовой
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
    // пиксельные фоны PixelBackground, в том же порядке локаций
    private static final PixelBackground.Sin[] PX_SINS = {PixelBackground.Sin.WRATH, PixelBackground.Sin.GREED,
            PixelBackground.Sin.ENVY, PixelBackground.Sin.LUST, PixelBackground.Sin.GLUTTONY,
            PixelBackground.Sin.SLOTH, PixelBackground.Sin.PRIDE};
    // какая картинка комнаты PixelBackground стоит в n-й комнате локации (0 = общий фон греха)
    private static int pxRoomFor(int loc, int n) {
        if (loc == 1) return n == 2 ? 2 : n <= 3 ? 1 : 0;       // Гнев: музей и семья Осмаров — музей портретов, комната 2 — комната наказаний
        if (loc == 2) return n == 1 ? 1 : 3;                    // Алчность: приют — рваные игрушки, работница и кабинет — кабинет сотрудников
        return n;                                               // у остальных номера совпадают (лишние уйдут в общий фон)
    }
    private static final String[] SIN_NAMES = {"Гнев", "Алчность", "Зависть", "Похоть",
            "Чревоугодие", "Уныние", "Гордыня"};

    private final GameWindow window;
    private final Map<String, Room> rooms = new HashMap<>();
    private final Set<String> done = new HashSet<>();     // пройденные испытания
    private Room current;
    private final Player player = new Player();
    private Cat pet;                                      // кот, которого ты спас
    private int carryCrest = -1;                          // Гнев, комната 1: герб вазы в руках (-1 — пусто)
    private Prop carryCrestProp;                          // та ваза на стойке, которую Эльза сейчас держит (со стойки она исчезает)
    private int inputLock;                                // кадры после реплики, когда E / L ещё ничего не выбирают (защита от случайного выбора)
    private int dialogCool;                               // кадры после входа в комнату / конца реплики, когда новая реплика не стартует
    private final Map<Integer, Long> heldAt = new HashMap<>();   // зажатые E / L: автоповтор клавиатуры не считается новым нажатием
    private int carryGDress = -1;                        // Зависть, комната 1: платье в руках (0..3; -1 — пусто)
    private int carryTItem = -1;                          // Зависть, комната 2: предмет со стола в руках (0..5; -1 — пусто)
    private Prop inputProp;                               // Зависть, комната 3: зеркало с открытым окном ввода (null — окна нет)
    private final StringBuilder inputBuf = new StringBuilder();
    private String inputMsg = "";
    private int carryToy = -1;                            // Алчность, комната 1: игрушка в руках (0 кубики, 1 мишка, 2 мячик; -1 — пусто)
    private int tick;
    // Игровой цикл с фиксированным шагом 60 обновлений в секунду. Обычный Timer(16) на Windows срабатывает
    // примерно раз в 31 мс (будто 30 к/с) — из-за этого герой «ползал». Здесь сколько времени прошло,
    // столько шагов и делается, поэтому скорость не зависит от лагов отрисовки.
    private static final long STEP_NS = 1_000_000_000L / 60;
    private long lastNs, accNs;
    private final Timer timer = new Timer(4, e -> loop());

    private void loop() {
        long now = System.nanoTime();
        if (lastNs == 0) lastNs = now;
        accNs = Math.min(accNs + (now - lastNs), STEP_NS * 4);      // после долгой паузы не «догоняем» кучу шагов
        lastNs = now;
        boolean stepped = false;
        while (accNs >= STEP_NS && timer.isRunning()) {
            accNs -= STEP_NS;
            tick++;
            update();
            stepped = true;
        }
        if (stepped) repaint();
    }

    private void startLoop() { lastNs = 0; accNs = 0; timer.start(); }

    private boolean up, down, left, right, jump;
    private int respawnCol, respawnRow;                   // куда вернёт после потери сознания
    private int safeX, safeY;                             // последнее безопасное место (после падения в яму)
    private double camX, camY;

    // эффекты (в кадрах)
    private int slowTimer, poisonTimer, darkTimer, weakTimer;
    private String blackText = "…ах…";                  // что написано на тёмном экране (стон / «Zzz…»)
    private int distortTimer;                             // Лень: экран «плывёт» (Эльзе плохо в ванне)
    private double sleepDark;                             // Лень, комната 2: чем дольше Эльза не ложится, тем темнее экран (0..1)
    // Гордыня
    private double prideDark, prideTarget;                // комната 1: чем больше верных платьев, тем темнее (0..1)
    private Prop jigProp;                                 // комната 2: открыт пазл с картиной семьи
    private final int[] jig = new int[6];                 // jig[слот] = номер куска, который лежит в этом слоте
    private boolean jigReady;                             // куски уже перемешаны
    private int jigCur, jigSel = -1, jigWin;              // курсор, выбранный кусок, кадры показа собранной картины
    private boolean sleepThought;                         // мысль «если усну…» уже прозвучала
    private int blackTimer;                               // тёмный экран на секунду (провал «вырвись» у Криса): мир стоит, слышен стон
    private boolean slowPermanent;                        // после убийства кота

    // проверка вазы
    private int checkTimer;
    private int checkTotal = CHECK_TIME;                  // длина текущей проверки (для полоски)
    private Vase checkVase;

    private String message = "";
    private int messageTimer;

    // диалог Эльзы (реплики вида "Кто|Текст"); null — диалога нет
    private static final int DIALOG_TYPE = 3;             // кадров на одну букву
    private String[] dialog;
    private int dialogIdx, dialogT;
    private Prop dialogProp;
    private final Set<String> catIntro = new HashSet<>();     // в каких комнатах Чревоугодия кошка уже что-то «сказала» при входе

    // мини-игра «вырвись»: после диалога с Крисом / Себастьяном жми E как можно быстрее
    private static final int MASH_TIME = 480;             // 9 секунд на попытку
    private static final double MASH_GAIN = 0.045;        // сколько даёт одно нажатие
    private static final double MASH_DECAY = 0.0045;      // на сколько шкала падает каждый кадр (он держит)
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
        setBackground(Color.BLACK);
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
                    if (slotRect(i).contains(View.toLogical(GamePanel.this, e.getPoint()))) {
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
        r.pxSin = PX_SINS[idx - 1];
        r.pxRoom = idx == 1 ? 0 : roomsIn(idx);                // комната-грех — последняя комната локации
        r.title = name.toUpperCase();
        r.subtitle = sub;
        r.exitChar = '2';
        r.exitLocked = true;
        r.addSign(signCol, story);
        r.addDoor('1', hallKey(idx), DOOR_COL[roomsIn(idx) - 1], hallReturnRow(roomsIn(idx)));
        r.addDoor('2', hallKey(idx), DOOR_COL[roomsIn(idx) - 1], hallReturnRow(roomsIn(idx)));
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
            for (int n = 1; n < roomsIn(loc); n++) add(trialKey(loc, n), trial(loc, n));
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
        Room r2 = sin(2, b2, "Кабинет сотрудников. На столе лежит мешочек с деньгами. Взять или нет?",
                new Decision(Decision.Kind.MONEY, 25, 9,
                        new Decision.Option("Не брать", true, "Ты не тронул деньги приюта. На душе стало легко."),
                        new Decision.Option("Взять", false, "Ты забрал деньги сирот. Алчность прокляла тебя.")),
                "Деньги приюта. Они тебе нужнее — или сиротам?");
        r2.props.add(Prop.plaque(19, 3, "КАБИНЕТ СОТРУДНИКОВ"));
        r2.props.add(new Prop(Prop.Kind.CABINET, 18, 9));
        r2.props.add(new Prop(Prop.Kind.CABINET, 20, 9));
        r2.props.add(Prop.talk(Prop.Kind.DESK, 25, 9, null,
                "Эльза|Вот они.",
                "Эльза|Столько денег...",
                "Эльза|За них можно купить еду. Можно купить свободу. Можно купить молчание.",
                "Пауза.",
                "Эльза|А можно ли купить прощение?"));
        hidden(r2, 38, 43);
        saw(r2, 49, 53);
        add("s2", r2);

        // ---------- 3. ЗАВИСТЬ (зелёный): комнаты 1 — две девочки, 2 — праздничный стол, 3 — Анна (зеркало, ответ вписывается словом) ----------
        MapBuilder b3 = MapBuilder.side(64);
        b3.set(12, 9, '^');
        pit(b3, 15, 15);
        gate(b3, 38);
        pit(b3, 41, 45);                          // пропасть под хрупким мостом
        pit(b3, 57, 57);
        Room r3 = sinRoom(3, b3, "Анна стоит в центре комнаты. Перед тобой зеркало: что ты ей ответишь?", 28,
                "Анна Осмар. У неё было всё, чего не было у тебя");
        r3.goal = Room.Goal.PUZZLE;
        r3.goalLabel = "ответь зеркалу";
        r3.props.add(Prop.plaque(29, 3, "КОМНАТА АННЫ ОСМАР"));
        r3.props.add(new Prop(Prop.Kind.ANNA, 23, 9));
        Prop annaTalk = Prop.talk(Prop.Kind.PLAQUE, 20, 9, null,
                "Эльза|Анна...",
                "Эльза|Ты ведь ничего мне не сделала.",
                "Пауза.",
                "Эльза|Тогда почему я так тебя ненавижу?",
                "Анна молчит.",
                "Эльза|Я смотрела на тебя и видела всю жизнь, которую у меня забрали.");
        r3.props.add(annaTalk);
        Prop mirror = Prop.talk(Prop.Kind.MIRROR, 32, 9, null,
                "Перед Эльзой зеркало.",
                "Эльза|Это же... не я.",
                "Эльза|В зеркале — Анна.",
                "Пауза.",
                "Эльза|Что я должна ей сказать?");
        mirror.input = true;                                   // после сценки открывается окно ввода: «ненавижу» или «прощаю»
        r3.props.add(mirror);
        hidden(r3, 8, 18);
        crumble(r3, 41, 45, 10);
        hidden(r3, 49);
        saw(r3, 51, 55);
        add("s3", r3);

        // ---------- 4. ПОХОТЬ (розовый): комнаты 1 — одежда, 2 — Крис (вырваться), 3 — Себастьян (грех-комната, дверь откроется в любом случае) ----------
        MapBuilder b4 = MapBuilder.side(66);
        b4.set(12, 9, '^');
        pit(b4, 24, 27);
        gate(b4, 39);
        pit(b4, 50, 50);
        Room r4 = sinRoom(4, b4, "Себастьян не нападает. Он протягивает руку и обещает открыть дверь.", 30,
                "Себастьян — тот, кто однажды открыл ей дверь");
        r4.goal = Room.Goal.ESCAPE;
        r4.goalLabel = "открой дверь";
        Prop seb = Prop.mash(Prop.Kind.SEBASTIAN, 34, 9,
                "Себастьян открыл дверь. Ворота открыты — пройди дорогу ловушек к выходу.",
                "Себастьян открыл дверь. Ворота открыты — пройди дорогу ловушек к выходу.",
                "Себастьян протягивает Эльзе руку.",
                "Эльза|Ты...",
                "Пауза.",
                "Эльза|Почему именно здесь?",
                "Себастьян|Пойдём. Я знаю выход.",
                "Эльза|Я уже однажды тебе поверила.",
                "Себастьян|Но я же не обманул тебя тогда.");
        seb.alwaysOpen = true;                                 // тапалка есть, но дверь откроется в любом случае
        seb.winLine = "Спасибо... что тогда ты действительно открыл дверь.";
        seb.mashTitle = "ОТКРОЙ ДВЕРЬ!";
        seb.mashHint = "Жми E — вместе с Себастьяном, пока дверь не поддастся";
        r4.props.add(seb);
        crumble(r4, 24, 27, 10);
        flame(r4, 43, 0);
        hidden(r4, 47);
        saw(r4, 54, 58);
        flame(r4, 61, 100);
        add("s4", r4);

        // ---------- 5. ЧРЕВОУГОДИЕ (оранжевый): комнаты 1 — фуршет (еда тает), 2 — столовая слуг (иллюзия), 3 — мусорная (настоящая рыба) ----------
        MapBuilder b5 = MapBuilder.side(64);
        b5.set(10, 9, '^');
        pit(b5, 24, 24);
        gate(b5, 37);
        pit(b5, 49, 49);
        b5.set(56, 9, '^');
        Room r5 = sin(5, b5, "Мусорная комната. Единственная настоящая рыба — отдать кошке или съесть?",
                new Decision(Decision.Kind.FISH, 31, 9,
                        new Decision.Option("Отдать рыбу кошке", true, "Эльза отдала рыбу кошке. Та громко мурчит."),
                        new Decision.Option("Съесть рыбу самой", false, "Эльза съела рыбу сама. Чревоугодие победило.")),
                "Рыба. Единственное настоящее здесь — и кошка смотрит на неё голодными глазами");
        r5.pxRoom = 4;                                           // фон «комната отходов» (у Чревоугодия в PixelBackground четыре картинки, а комнат три)
        r5.props.add(Prop.plaque(20, 3, "МУСОРНАЯ КОМНАТА"));
        Prop heap = new Prop(Prop.Kind.TRASH, 13, 9);
        heap.catHint = "Кошка на плечах замяукала и потянулась к объедкам: они настоящие.";
        r5.props.add(heap);
        r5.props.add(new Prop(Prop.Kind.TRASH, 16, 9));
        r5.props.add(Prop.talk(Prop.Kind.TRASH, 20, 9, null,
                "Свалка отходов. На полу — куски еды.",
                "Эльза|Вот где я действительно ела.",
                "Эльза|Не за столом.",
                "Эльза|Не на кухне.",
                "Эльза|Там, где никто не видел."));
        r5.props.add(Prop.talk(Prop.Kind.TRASH, 27, 9, null,
                "Кошка|Мяу! Мяу-мяу!",
                "Эльза|Ты тоже голодна?"));
        hidden(r5, 40, 53);
        saw(r5, 42, 46);
        flame(r5, 59, 40);
        add("s5", r5);

        // ---------- 6. ЛЕНЬ (серо-голубой): течение сна тянет назад. Комнаты: 1 ванная, 2 кровать с кошкой, 3 её бывшая комната ----------
        MapBuilder b6 = MapBuilder.side(66);
        b6.set(8, 9, '^');
        pit(b6, 18, 18);
        gate(b6, 36);
        pit(b6, 43, 43);
        b6.set(59, 9, '^');
        Room r6 = sin(6, b6, "Её бывшая комната. Прочти метки роста, потом: лечь на тряпку или взять игрушку и идти?",
                new Decision(Decision.Kind.RAG, 33, 9,
                        new Decision.Option("Лечь на тряпку и остаться", false, "Эльза свернулась на тряпке и не захотела вставать. Лень победила."),
                        new Decision.Option("Взять игрушку и идти дальше", true, "Эльза взяла игрушку и встала. Лень отступила.")),
                "Её комната: тряпка, старая игрушка и метки роста на стене");
        r6.pxRoom = 4;                                           // фон «пустая комната (её угол)»: у Лени четыре картинки, а комнат три
        r6.props.add(Prop.plaque(24, 3, "БЫВШАЯ КОМНАТА ЭЛЬЗЫ"));
        r6.props.add(Prop.talk(Prop.Kind.PLAQUE, 19, 9, null,
                "Почти пустая комната. Тряпка на полу. Старая игрушка.",
                "Эльза|Это всё, что осталось.",
                "Эльза|На косяках... царапины. Кто-то когда-то мерил мой рост.",
                "Эльза|Я помню их по порядку. Снизу вверх."));
        int[] markOrder = shuffled(5, RND, true);                // какая по высоте метка стоит в какой клетке — вперемешку
        int[] markCols = {21, 23, 25, 27, 29};
        for (int i = 0; i < markCols.length; i++) {
            Prop mark = Prop.action(Prop.Kind.HEIGHT_MARK, markCols[i], 9, "HMARK");
            mark.want = markOrder[i];
            r6.props.add(mark);
        }
        r6.wind = 1;
        hidden(r6, 12, 47);
        flame(r6, 39, 0);
        saw(r6, 49, 53);
        flame(r6, 56, 90);
        add("s6", r6);

        // ---------- 7. ГОРДЫНЯ (синий + золото): комнаты 1 — платья, 2 — семейный зал (пазл), 3 — пустой зал и псевдо-джинн ----------
        MapBuilder b7 = MapBuilder.side(66);
        gate(b7, 40);
        b7.set(61, 9, '^');
        Room r7 = sin(7, b7, "Комната 3 из 3: пустой зал. Джинн просит выбрать — вернуть всё или обрести покой",
                new Decision(Decision.Kind.GENIE, 28, 9,
                        new Decision.Option("Вернуть всё, что у меня было", false, "Эльза захотела вернуть прежнюю жизнь. Гордыня потянула назад, но джинн был лишь миражом."),
                        new Decision.Option("Попасть в рай и закончить страдания", true, "Эльза выбрала покой. Мираж растаял, гордыня склонилась.")),
                "Джинн. Он предлагает выбор: вернуть всё, что у тебя было, или попасть в рай");
        r7.props.add(Prop.plaque(22, 3, "ПУСТОЙ ЗАЛ"));
        r7.props.add(Prop.talk(Prop.Kind.PLAQUE, 20, 9, null,
                "Огромная пустая комната. Ни мебели, ни окон — только эхо.",
                "Перед Эльзой появляется фигура, похожая на джинна.",
                "Джинн|ты столько всего сделала, но…",
                "Джинн|выбери, чего ты хочешь больше:",
                "Джинн|попасть в рай и закончить страдания",
                "Джинн|или вернуть всё, что у тебя было."));
        hidden(r7, 47, 56);
        flame(r7, 51, 60);
        add("s7", r7);
    }

    // ====== КЛЮЧИ КОМНАТ ======
    private static String hallKey(int loc)         { return "h" + loc; }
    private static String roadKey(int loc)         { return "road" + loc; }
    // сколько комнат в локации: у Алчности три (приют, работница, кабинет), у Зависти три (две девочки, праздничный стол, Анна), у Похоти три (одежда, Крис, Себастьян), у Чревоугодия три (фуршет, столовая слуг, мусорная), у Гордыни три (платья, семейный зал, джинн), только у Гнева четыре; последняя комната — грех
    private static int roomsIn(int loc) { return loc == 1 ? 4 : 3; }
    private static String trialKey(int loc, int n) { return n == roomsIn(loc) ? "s" + loc : "r" + loc + "_" + n; }   // последняя комната = грех s1..s7
    private static boolean isHall(Room r)          { return r != null && r.key != null && r.key.matches("h[1-7]"); }
    private static boolean isGluttonyRoom(Room r)  { return r != null && r.key != null && r.key.matches("r5_[1-2]|s5"); }

    // {локация, номер комнаты} по ключу комнаты-испытания; null — если это не испытание
    private static int[] parseTrial(String key) {
        if (key == null) return null;
        if (key.matches("s[1-7]")) return new int[]{key.charAt(1) - '0', roomsIn(key.charAt(1) - '0')};
        if (key.matches("r[1-7]_[1-3]")) return new int[]{key.charAt(1) - '0', key.charAt(3) - '0'};
        return null;
    }

    private int roomsDone(int loc) {
        int n = 0;
        for (int i = 1; i <= roomsIn(loc); i++) if (done.contains(trialKey(loc, i))) n++;
        return n;
    }

    // ====== КОРИДОР ЛОКАЦИИ: 4 железные двери по порядку + дверь СПУСК ======
    private void buildHall(int loc) {
        MapBuilder hb = new MapBuilder(HALL_COLS, 12, '#');
        hb.rect(1, 4, HALL_COLS - 2, 7, '.');
        for (int i = 0; i < roomsIn(loc); i++) hb.set(DOOR_COL[i], doorRowInHall(i + 1), (char) ('1' + i));
        hb.set(HALL_COLS - 1, 6, '8');
        if (loc == 2) hb.set(5, 5, 'C');                       // в коридоре Алчности сидит кошка — она пойдёт с Эльзой до конца игры
        Room hall = new Room("Коридор · " + SIN_NAMES[loc - 1], hb.build());
        hall.theme = Theme.HALL;
        hall.basement = true;                                  // кирпичные стены и железные двери (общее для всех локаций)
        hall.look = Room.Look.VOID;                            // почти чёрное оформление
        hall.title = "ЛОКАЦИЯ " + loc;
        hall.subtitle = SIN_NAMES[loc - 1] + (roomsIn(loc) == 3 ? ": три двери открываются по порядку. Берегись стрел!" : ": четыре двери открываются по порядку. Берегись стрел!");
        for (int i = 0; i < roomsIn(loc); i++) {
            char c = (char) ('1' + i);
            hall.addDoor(c, trialKey(loc, i + 1), 3, 9);
            hall.doorTint.put(c, SIN_THEMES[loc - 1].base);
            hall.doorLabel.put(c, "КОМНАТА " + (i + 1));
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
        int next = roomsDone(loc);
        for (int i = 0; i < roomsIn(loc); i++) if (i != next) hall.lockedDoors.add((char) ('1' + i));
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
        if (loc > 2 && loc != 3 && loc != 4 && loc != 5 && loc != 7 && n == fillerIndex(loc)) return fillerRoom(loc, n);   // в каждой локации одна комната — «пустышка» с одними ловушками
        int cols = 56 + loc * 2;
        MapBuilder b = MapBuilder.side(cols);
        Random rnd = new Random(loc * 1000L + n * 17L);
        int kind = (loc + n) % 3;                              // 0 свеча, 1 сбор предметов, 2 тотемы, 3 встреча (вырваться)
        if (loc <= 2) kind = 5;                                // Гнев (музей, наказания, семья) и Алчность (приют, печать): комнаты-загадки
        if (loc == 3 && n <= 2) kind = 5;                      // Зависть: две загадки
        if (loc == 4 && n == 1) kind = 5;                      // Похоть: комната с нарядами — загадка с платьем
        if (loc == 4 && n == 2) kind = 3;                      // Похоть: Крис
        if (loc == 5 && n <= 2) kind = 5;                      // Чревоугодие: две иллюзии — фуршет (еда тает) и столовая слуг (стул и гости — галлюцинация)
        if (loc == 6) kind = n == 1 ? 5 : 6;
        if (loc == 7) kind = 5;                                // Гордыня: платья и пазл с семьёй                   // Лень: ванная — загадка, кровать — выбор «спать / не спать»
        b.set(8, 9, '#');
        if (loc >= 2) pit(b, 27, 27);
        switch (kind) {
            case 3, 4, 5, 6 -> { }                                     // комната-встреча / загадка: предметов-целей на карте нет
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
        List<int[]> plan = planRoad(33, cols - 5, loc, rnd);
        applyRoadMap(b, plan);

        Room r = new Room(SIN_NAMES[loc - 1] + " · комната " + n, b.build());
        r.sideView = true;
        r.theme = SIN_THEMES[loc - 1];
        r.pxSin = PX_SINS[loc - 1];
        r.pxRoom = pxRoomFor(loc, n);
        r.title = SIN_NAMES[loc - 1].toUpperCase();
        r.exitChar = '2';
        r.exitLocked = true;
        String hint;
        switch (kind) {
            case 4 -> {
                r.goal = Room.Goal.NONE;
                r.goalLabel = "";
                r.exitLocked = false;
                r.subtitle = "Комната " + n + " из " + roomsIn(loc) + ": " + (loc == 6
                        ? (n == 1 ? "ванна, которой у неё не было месяцами" : "тёплая кровать с кошкой")
                        : "еда, которую ей не давали");
                hint = "Подойди ближе. Здесь нет цели: поговори и иди к выходу";
            }
            case 5 -> {
                r.goal = Room.Goal.PUZZLE;
                if (loc == 1) {                                     // Гнев: три загадки
                    r.goalLabel = n == 1 ? "найди вазу Осмаров" : n == 2 ? "расставь предметы" : "расставь портреты";
                    r.subtitle = n == 1 ? "Музей Осмаров: найди вазу с гербом семьи"
                            : n == 2 ? "Комната наказаний: расставь предметы по порядку"
                            : "Семья Осмаров: расставь портреты по иерархии";
                    hint = n == 1 ? "Возьми вазу (E) и поставь на пьедестал с гербом Осмаров (E). Неверная ваза вернётся на место"
                            : n == 2 ? "Выбирай предметы клавишей E в правильном порядке. Ошибёшься — всё начнётся заново"
                            : "Ставь портреты (E) по порядку семьи. Ошибёшься — всё начнётся заново";
                } else if (loc == 3) {                              // Зависть: две девочки и праздничный стол
                    r.goalLabel = n == 1 ? "одень манекены" : "разложи вещи";
                    r.subtitle = n == 1 ? "Комната 1 из 3: две девочки — одень манекены Эльзы и Анны"
                            : "Комната 2 из 3: праздничный стол — что принадлежало Эльзе, а что Анне?";
                    hint = n == 1 ? "Возьми платье с вешалки (E) и надень на манекен (E) так, как одеты девочки на семейном портрете"
                            : "Возьми предмет со стола (E) и положи (E) на место того, кому он принадлежал — Эльзы или Анны";
                } else if (loc == 5) {                              // Чревоугодие: фуршет и столовая слуг
                    r.goalLabel = n == 1 ? "прикоснись к еде" : "развей иллюзию";
                    r.subtitle = n == 1 ? "Комната 1 из 3: фуршет Осмаров — еда, которую ей не давали"
                            : "Комната 2 из 3: столовая слуг — для Эльзы нет места";
                    hint = n == 1 ? "Возьми блюда со стола (E). Присмотрись к ним — и к кошке: она тянется только к настоящему"
                            : "Свободный стул — приманка? Иллюзии рассеиваются мечом (L). Кошка на плечах не обманется";
                } else if (loc == 7) {                              // Гордыня: платья и пазл
                    r.goalLabel = n == 1 ? "выбери настоящие платья" : "собери картину";
                    r.subtitle = n == 1 ? "Комната 1 из 3: платья — выбери те, что были её настоящим прошлым"
                            : "Комната 2 из 3: семейный зал — собери картину семьи";
                    hint = n == 1 ? "Настоящие платья Эльзы просты и потёрты, на подоле вышита «Э». Выбирай их (E) — но с каждым верным выбором в комнате темнее"
                            : "Подойди к разбитой картине на мольберте (E). Стрелки — выбрать кусок, E — взять и поменять местами";
                } else if (loc == 6) {                              // Лень: ванная
                    r.goalLabel = "реши, что оставить";
                    r.subtitle = "Комната 1 из 3: ванная — смыть прошлое или оставить?";
                    hint = "Подойди к ванне. Потом: E у вещи — бросить в воду, E у ванны — отойти и оставить всё. Вода темнеет";
                } else if (loc == 4) {                              // Похоть: гардеробная
                    r.goalLabel = "подбери платье";
                    r.subtitle = "Комната 1 из 3: старая гардеробная — подбери платье для манекена";
                    hint = "Посмотри на старый семейный портрет и надень на манекен (E) такое же платье. Ошибёшься — платье останется висеть";
                } else {
                    r.goalLabel = n == 1 ? "раздай игрушки" : "найди подделку";
                    r.subtitle = n == 1 ? "Детский приют «Дэкса»: игрушки для сирот" : "Работница приюта: документы с печатями";
                    hint = n == 1 ? "Возьми игрушку со стола (E) и отдай тому ребёнку, чей рисунок на неё похож: E у кровати"
                            : "На столах — документы с печатью Османов. Одна подделана: найди её и порви (E)";
                }
            }
            case 6 -> {                                             // Лень, комната 2: кошка зовёт лечь
                r.goal = Room.Goal.CHOICE;
                r.goalLabel = "сделай выбор";
                r.subtitle = "Комната 2 из 3: кровать с кошкой — лечь спать или идти дальше?";
                r.choice = new Decision(Decision.Kind.BED, 20, 9,
                        new Decision.Option("Лечь спать", false, "Эльза легла рядом с кошкой и закрыла глаза. Лень победила."),
                        new Decision.Option("Не спать — идти дальше", true, "Эльза отвернулась от кровати. Кошка обиженно мяукнула."));
                hint = "Кошка зовёт лечь. Чем дольше сопротивляешься, тем темнее экран. E — лечь, L — не спать";
            }
            case 3 -> {
                r.goal = Room.Goal.ESCAPE;
                r.goalLabel = "вырвись";
                r.subtitle = "Комната " + n + " из " + roomsIn(loc) + ": поговори и вырвись из его рук";
                hint = "Подойди к нему. Когда он схватит — жми E как можно быстрее!";
            }
            case 0 -> {
                r.goal = Room.Goal.CANDLE;
                r.goalLabel = "найди свечу";
                r.checkTime = 75 + loc * 25;                        // чем глубже, тем дольше стоять над вазой
                r.subtitle = "Комната " + n + " из " + roomsIn(loc) + ": найди свечу среди " + r.vases.size() + " ваз";
                hint = "Свеча в одной из " + r.vases.size() + " ваз. E — проверить (" + String.format("%.1f", r.checkTime / 60.0) + " с, стой смирно), L — разбить (штраф!)";
                vaseGuards(r, loc);
            }
            case 1 -> {
                r.goal = Room.Goal.COLLECT;
                r.goalLabel = "собери предметы";
                r.subtitle = "Комната " + n + " из " + roomsIn(loc) + ": собери все предметы";
                hint = "Собери все светящиеся предметы — тогда откроются ворота";
            }
            default -> {
                r.goal = Room.Goal.TOTEMS;
                r.goalLabel = "разбей тотемы";
                r.subtitle = "Комната " + n + " из " + roomsIn(loc) + ": разбей тотемы мечом";
                hint = "Тотемы разбиваются мечом: L, три удара на каждый";
            }
        }
        r.addSign(11, hint);
        if (loc == 1 && n == 1) {                              // первая комната игры: заодно короткое обучение
            r.addSign(4, "A / D или стрелки — ходьба");
            r.addSign(6, "ПРОБЕЛ — прыжок. Перепрыгни блок впереди");
        }
        r.addDoor('1', hallKey(loc), DOOR_COL[n - 1], hallReturnRow(n));
        r.addDoor('2', hallKey(loc), DOOR_COL[n - 1], hallReturnRow(n));
        applyRoadTraps(r, plan);
        if (loc == 1) decorateWrath(r, n);
        else if (loc == 2) decorateGreed(r, n);
        else if (loc == 3) decorateEnvy(r, n);
        else if (loc == 4) decorateLust(r, n);
        else if (loc == 5) decorateGluttony(r, n);
        else if (loc == 6) decorateSloth(r, n);
        else if (loc == 7) decoratePride(r, n);
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
        r.pxSin = PX_SINS[loc - 1];
        r.pxRoom = pxRoomFor(loc, n);
        r.title = SIN_NAMES[loc - 1].toUpperCase();
        r.subtitle = "Комната " + n + " из " + roomsIn(loc) + ": только ловушки — просто дойди до выхода";
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

    // перемешанный порядок 0..n-1; если shuffleAll == false, исходный порядок не допускается
    private static int[] shuffled(int n, Random rnd, boolean allowSame) {
        int[] a = new int[n];
        do {
            List<Integer> l = new ArrayList<>();
            for (int i = 0; i < n; i++) l.add(i);
            java.util.Collections.shuffle(l, rnd);
            for (int i = 0; i < n; i++) a[i] = l.get(i);
        } while (!allowSame && java.util.Arrays.equals(a, java.util.stream.IntStream.range(0, n).toArray()));
        return a;
    }

    // Гнев (3 комнаты-загадки + грех): 1 — музей Осмаров (ваза с гербом), 2 — комната наказаний (ключ → цепь → плеть → дверь),
    // 3 — семья Осмаров (портреты по иерархии, тайник). Реплика Эльзы запускается сама, когда она подходит.
    private void decorateWrath(Room r, int n) {
        Random rnd = new Random();
        if (n == 1) {                                          // музей: найди вазу с гербом Осмаров и поставь на нужный пьедестал
            r.props.add(Prop.plaque(18, 3, "МУЗЕЙ ОСМАРОВ\nГерб рода — звезда о семи лучах"));
            for (int c : new int[]{14, 20, 26}) r.props.add(new Prop(Prop.Kind.PAINTING, c, 6));
            r.props.add(Prop.talk(Prop.Kind.PLAQUE, 10, 9, null,
                    "Эльза|Эта ваза... Я помню её.",
                    "Эльза|Они говорили, что она стоит дороже, чем жизнь некоторых людей.",
                    "Эльза|Странно... тогда я ещё не понимала, насколько они были правы."));
            int[] vaseCols = {12, 14, 16, 18};
            int[] crests = shuffled(4, rnd, true);             // на четырёх вазах — четыре разных герба
            for (int i = 0; i < 4; i++) {
                Prop v = Prop.action(Prop.Kind.CREST_VASE, vaseCols[i], 9, "CREST_TAKE");
                v.want = crests[i];
                r.props.add(v);
            }
            int[] pedCols = {22, 25, 28};
            int[] pedCrests = shuffled(3, rnd, true);          // на трёх пьедесталах — гербы 0..2, Осмары среди них
            for (int i = 0; i < 3; i++) {
                Prop ped = Prop.action(Prop.Kind.PEDESTAL, pedCols[i], 9, "PEDESTAL");
                ped.want = pedCrests[i];
                r.props.add(ped);
            }
            hidden(r, 20);                                     // ловушка между вазами и пьедесталами
        } else if (n == 2) {                                   // комната наказаний
            r.props.add(Prop.plaque(15, 3, "КОМНАТА НАКАЗАНИЙ"));
            r.props.add(Prop.plaque(24, 3, "Сначала — то, что отпирает.\nЗатем — то, что удерживает.\nПотом — то, что учит.\nИ только тогда — выход."));
            r.props.add(Prop.talk(Prop.Kind.PLAQUE, 10, 9, null,
                    "Эльза|А это место я помню.",
                    "Эльза|Странно... я ведь старалась забыть его.",
                    "Эльза|Почему здесь всё выглядит так, будто меня ждали?"));
            r.props.add(Prop.talk(Prop.Kind.WHIPS, 12, 9, null,
                    "Эльза замечает плеть.",
                    "Эльза|Мистер П.",
                    "Эльза|Он всегда говорил, что боль делает людей послушными."));
            r.props.add(new Prop(Prop.Kind.PILLORY, 27, 9));
            int[] itemCols = {14, 17, 20, 23};
            int[] order = shuffled(4, rnd, false);             // предметы стоят вперемешку
            for (int i = 0; i < 4; i++) {
                Prop it = Prop.action(Prop.Kind.WALL_ITEM, itemCols[i], 9, "SEQ");
                it.want = order[i];                            // 0 ключ, 1 цепь, 2 плеть, 3 дверь — место в правильном порядке
                r.props.add(it);
            }
        } else if (n == 3) {                                   // семья Осмаров
            r.props.add(Prop.plaque(13, 3, "СЕМЬЯ ОСМАРОВ"));
            r.props.add(Prop.plaque(20, 3, "Дом Осмаров держался на порядке:\nот старших — к младшим."));
            r.props.add(Prop.talk(Prop.Kind.PLAQUE, 10, 9, null,
                    "Эльза|Семья Осмаров...",
                    "Эльза|Я когда-то думала, что они просто забрали мой дом.",
                    "Эльза|Оказывается, они забрали гораздо больше."));
            r.props.add(Prop.talk(Prop.Kind.FAMILY_PORTRAIT, 24, 5, null,
                    "Эльза смотрит на Анну.",
                    "Эльза|А её я помню лучше всех."));
            int[] easelCols = {13, 15, 17, 19};
            int[] who = shuffled(4, rnd, false);               // портреты стоят вперемешку: 0 отец, 1 мать, 2 старший сын, 3 Анна
            for (int i = 0; i < 4; i++) {
                Prop pt = Prop.action(Prop.Kind.PORTRAIT, easelCols[i], 9, "SEQ");
                pt.want = who[i];
                r.props.add(pt);
            }
            r.props.add(new Prop(Prop.Kind.STASH, 28, 9));
        }
    }

    // Алчность (3 комнаты): 1 — детский приют (раздай игрушки детям), 2 — работница и поддельная печать,
    // 3 (грех) — кабинет сотрудников с деньгами, она собрана в buildRooms().
    private void decorateGreed(Room r, int n) {
        if (n == 1) {
            r.props.add(Prop.plaque(21, 3, "ДЕТСКИЙ ПРИЮТ ДЛЯ СИРОТ «ДЭКСА»\nОткрыт для всех нуждающихся.\nДа будет вами доволен Бог."));
            r.props.add(Prop.talk(Prop.Kind.PLAQUE, 15, 9, null,
                    "Эльза|Открыто для всех нуждающихся...",
                    "Эльза|Забавно.",
                    "Эльза|Когда нуждалась я, никто почему-то не открыл дверь."));
            r.props.add(Prop.action(Prop.Kind.TOY_TABLE, 12, 9, "TOY_TAKE"));
            String[] kids = {"ТИМ", "МИЛА", "ЛЕВ"};
            int[] wants = {1, 2, 0};                             // Тим — мишка, Мила — мячик, Лев — кубики
            int[] cols = {17, 21, 25};
            for (int i = 0; i < 3; i++) {
                Prop kid = Prop.action(Prop.Kind.KID_BED, cols[i], 9, "KID");
                kid.label = kids[i];
                kid.want = wants[i];
                r.props.add(kid);
            }
            Prop pouch = new Prop(Prop.Kind.POUCH, 14, 9);       // спрятан, пока загадка не решена
            pouch.hidden = true;
            r.props.add(pouch);
        } else if (n == 2) {
            r.props.add(Prop.talk(Prop.Kind.WORKER, 14, 9, "Работница молча вышла из комнаты.",
                    "Эльза|Что вы здесь делаете?",
                    "Тишина.",
                    "Эльза|Вы ведь меня помните...",
                    "Тишина.",
                    "Эльза|Вас заставляли лгать обо мне.",
                    "Тишина.",
                    "Эльза|Вы меня предали.",
                    "Тишина.",
                    "Эльза|И всё ради чего?.. Денег?",
                    "Тишина."));
            r.props.add(Prop.plaque(22, 3, "ПЕЧАТЬ ОСМАНОВ\nНастоящая — с семью лучами."));
            int forged = new Random().nextInt(3);                // какой из трёх документов поддельный — каждая игра по-новому
            int[] cols = {19, 22, 25};
            for (int i = 0; i < 3; i++) {
                Prop doc = Prop.action(Prop.Kind.DOC, cols[i], 9, "DOC");
                doc.forged = i == forged;
                r.props.add(doc);
            }
        }
    }

    // Зависть: комната 1 — две девочки (одень манекены Эльзы и Анны по портрету), комната 2 — праздничный стол
    // (что принадлежало Эльзе, а что Анне), комната 3 (грех) — Анна и зеркало, она собрана в buildRooms().
    private void decorateEnvy(Room r, int n) {
        Random rnd = new Random();
        if (n == 1) {
            r.props.add(Prop.talk(Prop.Kind.PLAQUE, 10, 9, null,
                    "Эльза|Когда-то у нас было почти одинаковое детство.",
                    "Эльза|Только почему-то её жизнь продолжилась... а моя закончилась."));
            r.props.add(Prop.plaque(14, 3, "СТАРАЯ ОДЕЖДА ЭЛЬЗЫ"));
            r.props.add(Prop.plaque(24, 3, "ПЛАТЬЯ АННЫ"));
            int[] left = rnd.nextBoolean() ? new int[]{0, 1} : new int[]{1, 0};      // слева — старая одежда Эльзы
            int[] right = rnd.nextBoolean() ? new int[]{2, 3} : new int[]{3, 2};     // справа — красивые платья Анны (среди них то самое)
            int[] lc = {13, 15}, rc = {23, 25};
            for (int i = 0; i < 2; i++) {
                Prop a = Prop.action(Prop.Kind.GIRL_RACK, lc[i], 9, "GDRESS");
                a.want = left[i];
                r.props.add(a);
                Prop b = Prop.action(Prop.Kind.GIRL_RACK, rc[i], 9, "GDRESS");
                b.want = right[i];
                r.props.add(b);
            }
            Prop me = Prop.action(Prop.Kind.GIRL_MANNEQUIN, 17, 9, "GMANN");
            me.want = 0;
            r.props.add(me);
            r.props.add(new Prop(Prop.Kind.GIRLS_PORTRAIT, 19, 5));
            Prop her = Prop.action(Prop.Kind.GIRL_MANNEQUIN, 21, 9, "GMANN");
            her.want = 1;
            r.props.add(her);
        } else if (n == 2) {
            r.props.add(Prop.talk(Prop.Kind.PLAQUE, 10, 9, null,
                    "Эльза|Я не хотела её ненавидеть.",
                    "Эльза|Честно.",
                    "Эльза|Я просто хотела хоть раз получить то, что получала она.",
                    "Пауза.",
                    "Эльза|Наверное... именно тогда всё и началось."));
            r.props.add(Prop.plaque(16, 3, "ПРАЗДНИЧНЫЙ СТОЛ"));
            Prop hint = Prop.plaque(19, 5, "Зависть начинается там, где чужое счастье\nкажется доказательством твоего несчастья.");
            hint.hidden = true;                                // проступает, когда всё разложено верно
            r.props.add(hint);
            Prop pe = Prop.action(Prop.Kind.PLACE, 13, 9, "TPLACE");
            pe.want = 0;
            r.props.add(pe);
            Prop pa = Prop.action(Prop.Kind.PLACE, 25, 9, "TPLACE");
            pa.want = 1;
            r.props.add(pa);
            int[] order = shuffled(6, rnd, true);              // шесть предметов вперемешку: три Анны, три Эльзы
            for (int i = 0; i < 6; i++) {
                Prop it = Prop.action(Prop.Kind.TABLE_ITEM, 16 + i, 9, "TITEM");
                it.want = order[i];
                it.label = i == 0 ? "L" : i == 5 ? "R" : "";
                r.props.add(it);
            }
        }
    }

    // Похоть: комната 1 — гардеробная (платье для манекена по семейному портрету), комната 2 — Крис (вырваться),
    // комната 3 (грех) — Себастьян, она собрана в buildRooms().
    private void decorateLust(Room r, int n) {
        if (n == 1) {
            r.props.add(Prop.plaque(21, 3, "ГАРДЕРОБНАЯ"));
            r.props.add(Prop.talk(Prop.Kind.CLOTHES, 12, 9, null,
                    "Эльза|Мне говорили, что дворянка должна выглядеть красиво.",
                    "Пауза.",
                    "Эльза|Но я не выбирала, что надеть.",
                    "Эльза смотрит на платье.",
                    "Эльза|Некоторые вещи человек запоминает не потому, что они красивые."));
            r.props.add(new Prop(Prop.Kind.CLOTHES, 26, 9));      // вешалки с дорогими платьями; среди них и слишком открытые
            Prop portrait = new Prop(Prop.Kind.DRESS_PORTRAIT, 18, 5);
            int target = new Random().nextInt(3);                  // платье на портрете — одно из трёх приличных
            portrait.want = target;
            r.props.add(portrait);
            int[] order = shuffled(4, new Random(), true);         // платья стоят вперемешку: три обычных и одно слишком открытое
            int[] cols = {14, 16, 20, 22};
            for (int i = 0; i < 4; i++) {
                Prop d = Prop.action(Prop.Kind.DRESS_OPTION, cols[i], 9, "DRESS");
                d.want = order[i];
                d.revealing = order[i] == Prop.REVEALING_DRESS;
                r.props.add(d);
            }
            r.props.add(new Prop(Prop.Kind.MANNEQUIN, 24, 9));
        } else if (n == 2) {
            Prop chris = Prop.mash(Prop.Kind.CHRIS, 22, 9,
                    "Эльза вырвалась и убежала от Криса. Похоть не взяла над ней верх.",
                    "Эльза не смогла вырваться. Она согрешила похотью.",
                    "Из тени выходит Крис.",
                    "Крис|Не бойся, Эльза. Тебе нечего бояться.",
                    "Крис|Пойдём со мной. Я отведу тебя отсюда.",
                    "Эльза|Я уже слышала эти слова.",
                    "Эльза|«Не бойся».",
                    "Эльза|«Я ничего тебе не сделаю».",
                    "Эльза|«Доверься мне».",
                    "Пауза.",
                    "Эльза|Почему каждый раз после этих слов мне становилось только страшнее?");
            chris.winLine = "На этот раз... я не останусь.";
            chris.loseDark = true;                                 // при провале — тёмный экран и стон
            r.props.add(chris);
        }
    }

    // Чревоугодие: комната 1 — фуршет Осмаров (еда тает от прикосновения), комната 2 — столовая слуг (стол, гости и стул — иллюзия),
    // комната 3 (грех) — мусорная с настоящей рыбой, она собрана в buildRooms().
    // Кошка у Эльзы на плечах — индикатор реальности: она реагирует только на то, что существует на самом деле.
    private void decorateGluttony(Room r, int n) {
        if (n == 1) {
            r.props.add(Prop.plaque(18, 3, "ФУРШЕТ СЕМЬИ ОСМАРОВ"));
            r.props.add(Prop.talk(Prop.Kind.PLAQUE, 12, 9, null,            // невидимый запуск сценки
                    "Огромный стол. Мясо, хлеб, фрукты, вино, десерты.",
                    "Эльза|Я помню этот запах.",
                    "Эльза|Странно... когда-то я ненавидела эти пиры.",
                    "Пауза.",
                    "Эльза|А теперь я готова съесть всё."));
            r.props.add(new Prop(Prop.Kind.BUFFET, 14, 9));
            for (int i = 0; i < 5; i++) {                                   // блюда лежат подряд, как один длинный стол
                Prop dish = Prop.action(Prop.Kind.FEAST_DISH, 15 + i, 9, "FOOD");
                dish.want = i;
                if (i == 0) dish.catHint = "Кошка на плечах даже не повернула голову: настоящую еду она бы учуяла.";
                r.props.add(dish);
            }
            r.props.add(new Prop(Prop.Kind.BUFFET, 20, 9));
        } else if (n == 2) {
            r.props.add(Prop.plaque(19, 3, "СТОЛОВАЯ СЛУГ"));
            r.props.add(Prop.talk(Prop.Kind.PLAQUE, 10, 9, null,
                    "Большой стол. На одном конце сидят господа, на другом — слуги. Но для Эльзы нет места.",
                    "Эльза|Даже здесь мне не разрешали сидеть.",
                    "Эльза|Забавно.",
                    "Эльза|Я была хозяйкой этого дома... но для одного куска хлеба была ниже всех."));
            int[][] guests = {{14, 0, 0}, {15, 2, 1}, {16, 0, 2}, {22, 1, 0}, {23, 1, 1}, {24, 1, 2}};   // {колонка, кто, очередь реплики}: господа слева, слуги справа
            for (int[] gst : guests) {
                Prop d = new Prop(Prop.Kind.DINER, gst[0], 9);
                d.want = gst[1];
                d.mask = gst[2];
                if (gst[0] == 14) d.catHint = "Кошка на плечах даже не смотрит на гостей: ни запаха, ни тепла — их там нет.";
                r.props.add(d);
            }
            Prop table = new Prop(Prop.Kind.HALL_TABLE, 13, 9);             // стол рисуется поверх гостей и закрывает им ноги
            table.want = 13;
            table.hp = ILLUSION_HITS;
            r.props.add(table);
            Prop chair = Prop.action(Prop.Kind.FREE_CHAIR, 19, 9, "SIT");   // единственное свободное место
            chair.catHint = "Кошка прижала уши и не хочет смотреть на стул: он не настоящий.";
            r.props.add(chair);
        }
    }

    // Лень: комната 1 — ванная (попытка помыться, потом загадка «что бросить в воду, а что оставить»),
    // комната 2 — кровать с кошкой (выбор «спать / не спать», экран темнеет), комната 3 (грех) — её бывшая комната, она собрана в buildRooms().
    private void decorateSloth(Room r, int n) {
        if (n == 1) {
            r.props.add(Prop.plaque(18, 3, "ВАННАЯ КОМНАТА"));
            Prop bath = Prop.talk(Prop.Kind.BATH, 18, 9,
                    "Горячая вода оказалась слишком тяжёлой для ослабевшего тела.",
                    "Старая ванна.",
                    "Эльза|Я даже не помню, когда последний раз принимала ванну.",
                    "Она подходит к воде.",
                    "Эльза|Я хочу просто смыть всё.",
                    "Эльза пытается принять ванну. Экран начинает искажаться.",
                    "Эльзе становится плохо.",
                    "Эльза|Нет...",
                    "Эльза|Это не смывается.");
            bath.effect = "SICK";
            r.props.add(bath);
            int[] itemCols = {11, 13, 23, 25};                  // вещи из её прошлого: фотография, ленточка, письмо, башмак
            for (int i = 0; i < itemCols.length; i++) {
                Prop it = Prop.action(Prop.Kind.BATH_ITEM, itemCols[i], 9, "BITEM");
                it.want = i;
                r.props.add(it);
            }
        } else if (n == 2) {
            r.props.add(Prop.plaque(18, 3, "СПАЛЬНЯ"));
            r.props.add(Prop.talk(Prop.Kind.PLAQUE, 14, 9, null,
                    "Кошка лежит на кровати. Она выглядит очень спокойно.",
                    "Эльза|Ты хочешь, чтобы я легла?",
                    "Кошка смотрит на неё.",
                    "Эльза|Наверное, ты права.",
                    "Пауза.",
                    "Эльза|Можно ведь просто закрыть глаза."));
        }
    }

    // Гордыня: комната 1 — зал платьев (выбери настоящие; каждый верный выбор затемняет комнату), комната 2 — семейный зал (пазл),
    // комната 3 (грех) — пустой зал с псевдо-джинном, она собрана в buildRooms().
    private void decoratePride(Room r, int n) {
        if (n == 1) {
            r.props.add(Prop.plaque(19, 3, "ЗАЛ ПЛАТЬЕВ"));
            r.props.add(Prop.talk(Prop.Kind.PLAQUE, 12, 9, null,
                    "Большая комната с манекенами. На них — старые платья Эльзы.",
                    "Эльза|Это я.",
                    "Пауза.",
                    "Эльза|До всего этого.",
                    "Эльза|До голода.",
                    "Эльза|До страха.",
                    "Эльза|До них.",
                    "Она касается платья.",
                    "Эльза|Я хочу снова стать ею."));
            int[] order = shuffled(6, new Random(), true);         // три настоящих платья (0..2) и три «из мечты» (3..5) стоят вперемешку
            int[] cols = {14, 16, 18, 20, 22, 24};
            for (int i = 0; i < 6; i++) {
                Prop d = Prop.action(Prop.Kind.PRIDE_DRESS, cols[i], 9, "PDRESS");
                d.want = order[i];
                d.fake = order[i] >= 3;
                r.props.add(d);
            }
        } else if (n == 2) {
            r.props.add(Prop.plaque(18, 3, "СЕМЕЙНЫЙ ЗАЛ"));
            r.props.add(Prop.talk(Prop.Kind.FAMILY, 14, 9, null,
                    "Образы родителей Эльзы. Они улыбаются.",
                    "Эльза|Мама... папа...",
                    "Эльза|Я всё ещё помню вас.",
                    "Она подходит ближе.",
                    "Эльза|Я ведь должна была жить так всегда.",
                    "Пауза.",
                    "Эльза|Почему вы оставили меня одну?"));
            r.props.add(Prop.action(Prop.Kind.FAMILY_PUZZLE, 22, 9, "JIGSAW"));
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
                distortTimer = 300;                              // экран плывёт
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
            case 0:  slowTimer = 900;   return "Проклятие: замедление (15 с)";
            case 1:  poisonTimer = 600; return "Яд: ты теряешь здоровье (10 с)";
            case 2:  darkTimer = 900;   return "Тьма: почти ничего не видно (15 с)";
            default: weakTimer = 900;   return "Слабость: урон меньше на 1 (15 с)";
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

    // ====== ЗАГАДКИ АЛЧНОСТИ ======
    // короткая сценка из нескольких реплик (без привязки к предмету в комнате)
    private void say(String endText, String... lines) {
        startDialog(Prop.talk(Prop.Kind.PLAQUE, 0, 0, endText, lines));
    }

    // ближайший предмет, с которым можно взаимодействовать клавишей E
    private Prop nearAction() {
        if (current == null || dialog != null || mashProp != null) return null;
        Prop best = null;
        double bd = 64;
        for (Prop p : current.props) {
            if (p.action == null || p.solved || p.hidden) continue;
            double dx = Math.abs(p.centerX() - player.centerX());
            if (dx < bd && Math.abs(p.centerY() - player.centerY()) < 90) { bd = dx; best = p; }
        }
        return best;
    }

    private String actionPrompt(Prop p) {
        return switch (p.action) {
            case "TOY_TAKE" -> carryToy < 0 ? "E — взять игрушку" : "E — взять другую игрушку";
            case "KID" -> carryToy < 0 ? "Сначала возьми игрушку со стола" : "E — отдать " + Prop.TOY_NAMES[carryToy];
            case "DOC" -> "E — порвать документ";
            case "FOOD" -> "E — взять: " + Prop.FOOD_NAMES[p.want % 5];
            case "SIT" -> "E — сесть на свободное место";
            case "GDRESS" -> carryGDress < 0 ? "E — взять: " + Prop.GDRESS_NAMES[p.want] : "E — взять другое платье";
            case "GMANN" -> carryGDress < 0 ? "Сначала возьми платье с вешалки" : "E — надеть на манекен: " + (p.want == 0 ? "ЭЛЬЗА" : "АННА");
            case "TITEM" -> "E — взять: " + Prop.TITEM_NAMES[p.want];
            case "TPLACE" -> carryTItem < 0 ? "Сначала возьми предмет со стола" : "E — положить на место " + (p.want == 0 ? "Эльзы" : "Анны");
            case "MIRROR" -> "E — посмотреть в зеркало";
            case "DRESS" -> p.revealing ? "E — осмотреть платье" : "E — надеть на манекен: " + Prop.DRESS_NAMES[p.want] + " платье";
            case "CREST_TAKE" -> carryCrest < 0 ? "E — взять вазу" : "E — взять другую вазу";
            case "PEDESTAL" -> carryCrest < 0 ? "Сначала возьми вазу" : "E — поставить вазу";
            case "SEQ" -> p.kind == Prop.Kind.PORTRAIT ? "E — поставить портрет: " + Prop.FAMILY_NAMES[p.want]
                    : "E — выбрать: " + Prop.ITEM_NAMES[p.want];
            case "PDRESS" -> "E — выбрать: " + Prop.PDRESS_NAMES[p.want];
            case "JIGSAW" -> "E — сложить картину";
            case "BITEM" -> "E — бросить в воду: " + Prop.BITEM_NAMES[Math.floorMod(p.want, 4)];
            case "BLEAVE" -> "E — отойти от воды и оставить всё как есть";
            case "HMARK" -> "E — прочитать метку";
            default -> "E";
        };
    }

    private void doAction(Prop p) {
        switch (p.action) {
            case "TOY_TAKE" -> {
                if (carryToy >= 0) p.toyMask |= 1 << carryToy;           // прежнюю игрушку кладём обратно
                int next = -1;
                for (int k = 1; k <= 3 && next < 0; k++) {
                    int t = ((carryToy < 0 ? -1 : carryToy) + k + 3) % 3;
                    if ((p.toyMask >> t & 1) != 0) next = t;
                }
                if (next < 0) { showMessage("На столе больше нет игрушек"); return; }
                p.toyMask &= ~(1 << next);
                carryToy = next;
                showMessage("В руках: " + Prop.TOY_NAMES[next] + ". Посмотри на рисунки над кроватями");
            }
            case "KID" -> {
                if (carryToy < 0) { showMessage("Сначала возьми игрушку со стола (E)"); return; }
                if (carryToy != p.want) {
                    showMessage(p.label + " хотел(а) не это. Посмотри на рисунок над кроватью");
                    return;
                }
                p.solved = true;
                showMessage(p.label + " получил(а) " + Prop.TOY_NAMES[carryToy] + " и улыбнулся(ась)");
                carryToy = -1;
                int left = 0;
                for (Prop q : current.props) if ("KID".equals(q.action) && !q.solved) left++;
                if (left == 0) toysDone();
            }
            case "FOOD" -> {
                p.solved = true;                                         // блюдо тает от прикосновения
                int gone = 0, total = 0;
                for (Prop q : current.props) if ("FOOD".equals(q.action)) { total++; if (q.solved) gone++; }
                if (gone >= total) foodDone();
                else showMessage(gone == 1 ? "Эльза коснулась блюда — оно растаяло, как дым. Осталось: " + (total - gone)
                        : "Блюдо растаяло. Осталось: " + (total - gone));
            }
            case "SIT" -> {                                              // сесть на свободный стул нельзя: он тоже галлюцинация
                p.solved = true;
                player.hp = Math.max(1, player.hp - 1);
                player.invul = Math.max(player.invul, 40);
                slowTimer = Math.max(slowTimer, 480);
                weakTimer = Math.max(weakTimer, 480);
                showMessage("Эльза села — и упала на пол: стул растаял, он тоже был видением.\nПадение: минус сердце, замедление и слабость. Иллюзия всё ещё стоит — развей её мечом");
            }
            case "CREST_TAKE" -> {
                if (carryCrestProp != null) carryCrestProp.solved = false;   // прежняя ваза возвращается на свою стойку
                p.solved = true;                                             // эта ваза теперь у Эльзы: со стойки исчезает
                carryCrestProp = p;
                carryCrest = p.want;
                showMessage("В руках ваза с гербом (над головой). Найди пьедестал с гербом Осмаров");
            }
            case "PEDESTAL" -> {
                if (carryCrest < 0) { showMessage("Сначала возьми вазу (E)"); return; }
                if (carryCrest != p.want) {
                    returnCrest();
                    showMessage("Герб на вазе не совпадает с гербом пьедестала. Ваза вернулась на свою стойку");
                    return;
                }
                if (p.want != Prop.OSMAR_CREST) {
                    returnCrest();
                    showMessage("Это герб другого рода, не Осмаров. Ваза вернулась на свою стойку");
                    return;
                }
                p.solved = true;
                p.placed = carryCrest;
                carryCrest = -1;
                carryCrestProp = null;                                       // ваза осталась стоять на пьедестале
                unlockExit("Ваза Осмаров встала на свой пьедестал.");
            }
            case "SEQ" -> {
                int progress = 0, total = 0;
                for (Prop q : current.props) if ("SEQ".equals(q.action)) { total++; if (q.solved) progress++; }
                if (p.want != progress) {                                // ошибка: всё возвращается на места
                    for (Prop q : current.props) if ("SEQ".equals(q.action)) { q.solved = false; q.placed = -1; }
                    showMessage("Неверный порядок. Всё вернулось на свои места");
                    return;
                }
                p.solved = true;
                p.placed = ++progress;
                if (progress < total) showMessage("Верно (" + progress + "/" + total + ")");
                else seqDone(p);
            }
            case "GDRESS" -> {
                if (carryGDress >= 0) { Prop prev = findProp("GDRESS", carryGDress); if (prev != null) prev.solved = false; }
                p.solved = true;                                         // платье снято с вешалки
                carryGDress = p.want;
                showMessage("В руках: " + Prop.GDRESS_NAMES[p.want] + ". Посмотри на семейный портрет");
            }
            case "GMANN" -> {
                if (carryGDress < 0) { showMessage("Сначала возьми платье с вешалки (E)"); return; }
                int target = p.want == 0 ? Prop.GIRL_ELSA_DRESS : Prop.GIRL_ANNA_DRESS;
                if (carryGDress != target) {
                    Prop prev = findProp("GDRESS", carryGDress);
                    if (prev != null) prev.solved = false;
                    carryGDress = -1;
                    showMessage("На портрете " + (p.want == 0 ? "Эльза" : "Анна") + " одета не так. Платье вернулось на вешалку");
                    return;
                }
                p.solved = true;
                p.placed = carryGDress;
                carryGDress = -1;
                boolean all = true;
                for (Prop q : current.props) if ("GMANN".equals(q.action) && !q.solved) all = false;
                if (all) girlsDone();
                else showMessage("Платье сидит как на портрете (1/2)");
            }
            case "TITEM" -> {
                if (carryTItem >= 0) { Prop prev = findProp("TITEM", carryTItem); if (prev != null) prev.solved = false; }
                p.solved = true;                                         // предмет взят со стола
                carryTItem = p.want;
                showMessage("В руках: " + Prop.TITEM_NAMES[p.want] + ". Чьё это — Эльзы или Анны?");
            }
            case "TPLACE" -> {
                if (carryTItem < 0) { showMessage("Сначала возьми предмет со стола (E)"); return; }
                int owner = carryTItem < 3 ? 1 : 0;                      // 0..2 — вещи Анны, 3..5 — вещи Эльзы
                if (owner != p.want) {
                    Prop prev = findProp("TITEM", carryTItem);
                    if (prev != null) prev.solved = false;
                    carryTItem = -1;
                    showMessage(p.want == 0 ? "Это принадлежало не Эльзе. Предмет вернулся на стол"
                            : "Это принадлежало не Анне. Предмет вернулся на стол");
                    return;
                }
                p.mask |= 1 << carryTItem;
                carryTItem = -1;
                int placed = 0;
                for (Prop q : current.props) if ("TPLACE".equals(q.action)) placed += Integer.bitCount(q.mask);
                if (placed >= 6) tableDone();
                else showMessage("Верно (" + placed + "/6)");
            }
            case "MIRROR" -> startInput(p);
            case "DRESS" -> {
                if (p.revealing) {                                       // слишком открытый наряд — Эльза его не возьмёт
                    say("", "Эльза|Нет. Это не моё.");
                    return;
                }
                Prop mannequin = null, portrait = null;
                for (Prop q : current.props) {
                    if (q.kind == Prop.Kind.MANNEQUIN) mannequin = q;
                    if (q.kind == Prop.Kind.DRESS_PORTRAIT) portrait = q;
                }
                if (mannequin == null || portrait == null) return;
                if (p.want != portrait.want) {
                    showMessage("Это не то платье. Сверься с семейным портретом на стене");
                    return;
                }
                p.solved = true;                                         // платье переехало на манекен
                mannequin.solved = true;
                mannequin.placed = p.want;
                unlockExit("Платье совпало с портретом.");
            }
            case "DOC" -> {
                if (!p.forged) {
                    showMessage("Печать настоящая: семь лучей. Рвать этот документ нельзя");
                    return;
                }
                p.solved = true;
                unlockExit("Подделка порвана!");
                say("", "Эльза|Значит, всё-таки деньги.");
            }
            case "BITEM" -> bathThrow(p);
            case "PDRESS" -> {
                if (p.fake) {                                            // платье из мечты, а не из жизни
                    p.flash = 30;
                    showMessage("«" + Prop.PDRESS_NAMES[p.want] + "» — из мечты, а не из жизни. На подоле нет её «Э»");
                    return;
                }
                p.solved = true;                                         // настоящее платье: комната темнеет
                int got = prideChosen();
                prideTarget = got / 3.0;
                if (got < 3) {
                    showMessage(got == 1 ? "Это её платье. Свет в комнате стал тусклее (1/3)"
                            : "Ещё одно настоящее платье. Комната темнеет (2/3)");
                } else {
                    unlockExit("Все три платья — настоящие.");
                    say("", "Эльза|Они были моими.",
                            "Эльза|Но почему здесь стало так темно?",
                            "Эльза|Чем сильнее я тянусь к тому времени, тем меньше света вокруг.");
                }
            }
            case "JIGSAW" -> startJigsaw(p);
            case "BLEAVE" -> bathLeave();
            case "HMARK" -> readMark(p);
            default -> { }
        }
    }

    // ====== ЗАГАДКИ ГОРДЫНИ ======
    private int prideChosen() {
        int n = 0;
        for (Prop q : current.props) if (q.kind == Prop.Kind.PRIDE_DRESS && q.solved && !q.fake) n++;
        return n;
    }

    private void updatePride() {
        if (prideDark < prideTarget) prideDark = Math.min(prideTarget, prideDark + 0.006);
        else if (prideDark > prideTarget) prideDark = Math.max(prideTarget, prideDark - 0.02);
    }

    // комната темнеет по краям, вокруг Эльзы остаётся пятно света, которое сжимается с каждым верным платьем
    private void drawPrideDark(Graphics2D g, int px, int py) {
        float k = (float) Math.min(1.0, prideDark);
        float rad = 780f - 470f * k;
        RadialGradientPaint p = new RadialGradientPaint(new Point2D.Float(px, py), rad,
                new float[]{0.25f, 1f},
                new Color[]{new Color(6, 8, 22, (int) (25 * k)), new Color(6, 8, 22, (int) (110 + 120 * k))});
        g.setPaint(p);
        g.fillRect(0, 0, Room.VIEW_W, Room.VIEW_H);
    }

    // пазл 3x2: куски меняются местами по двое
    private void startJigsaw(Prop p) {
        jigProp = p;
        if (!jigReady) {
            Random rnd = new Random();
            int fixed;
            do {
                int[] a = shuffled(6, rnd, true);
                System.arraycopy(a, 0, jig, 0, 6);
                fixed = 0;
                for (int i = 0; i < 6; i++) if (jig[i] == i) fixed++;
            } while (fixed > 1);
            jigReady = true;
        }
        jigCur = 0;
        jigSel = -1;
        jigWin = 0;
        up = down = left = right = jump = false;
        player.moving = false;
        checkTimer = 0;
        checkVase = null;
    }

    private void jigMove(int dc, int dr) {
        int c = (jigCur % 3 + dc + 3) % 3, r = (jigCur / 3 + dr + 2) % 2;
        jigCur = r * 3 + c;
    }

    private void jigSelect() {
        if (jigWin > 0) return;
        if (jigSel < 0) { jigSel = jigCur; return; }
        if (jigSel != jigCur) {
            int t = jig[jigSel];
            jig[jigSel] = jig[jigCur];
            jig[jigCur] = t;
        }
        jigSel = -1;
        boolean ok = true;
        for (int i = 0; i < 6; i++) if (jig[i] != i) ok = false;
        if (ok) jigWin = 110;                                    // секунду-две показываем собранную картину
    }

    private void finishJigsaw() {
        Prop p = jigProp;
        jigProp = null;
        if (p == null) return;
        p.solved = true;
        p.action = null;
        unlockExit("Картина собрана.");
        say("", "Картина вспыхнула тёплым светом. Родители на ней улыбаются.",
                "Эльза|Нет.",
                "Эльза|Не забирайте их снова.");
    }

    private void drawJigsaw(Graphics2D g) {
        g.setColor(new Color(0, 0, 0, 242));
        g.fillRect(0, 0, Room.VIEW_W, Room.VIEW_H);
        int pw = 160, ph = 120, bx = (Room.VIEW_W - 480) / 2, by = (Room.VIEW_H - 240) / 2 + 10;
        g.setFont(new Font("Monospaced", Font.BOLD, 28));
        g.setColor(new Color(250, 230, 170));
        drawCentered(g, "СЕМЬЯ", by - 64);
        g.setFont(new Font("Monospaced", Font.PLAIN, 16));
        g.setColor(Color.WHITE);
        drawCentered(g, jigWin > 0 ? "Они снова вместе." : "Стрелки — выбрать кусок, E — взять и поменять местами, ESC — отойти", by - 34);
        g.setColor(new Color(110, 78, 50));
        g.fillRect(bx - 10, by - 10, 500, 260);
        for (int i = 0; i < 6; i++) {
            int piece = jig[i], dx = bx + (i % 3) * pw, dy = by + (i / 3) * ph;
            Shape oc = g.getClip();
            AffineTransform ot = g.getTransform();
            g.clipRect(dx, dy, pw, ph);
            g.translate(dx - (piece % 3) * pw, dy - (piece / 3) * ph);
            Prop.paintFamilyPicture(g);
            g.setTransform(ot);
            g.setClip(oc);
            g.setColor(piece == i ? new Color(120, 230, 150, 170) : new Color(0, 0, 0, 150));
            g.drawRect(dx + 1, dy + 1, pw - 3, ph - 3);
            if (i == jigSel) {
                g.setColor(new Color(255, 220, 90, 90));
                g.fillRect(dx, dy, pw, ph);
            }
        }
        if (jigWin > 0) {
            g.setColor(new Color(255, 230, 150, 40 + (int) (Math.sin(tick * 0.2) * 20)));
            g.fillRect(bx, by, 480, 240);
        } else {
            Stroke os = g.getStroke();
            g.setStroke(new BasicStroke(3f));
            g.setColor(Color.WHITE);
            g.drawRect(bx + (jigCur % 3) * pw + 2, by + (jigCur / 3) * ph + 2, pw - 5, ph - 5);
            g.setStroke(os);
        }
    }

    // ====== ЗАГАДКИ ЛЕНИ ======
    private Prop findKind(Prop.Kind k) {
        for (Prop q : current.props) if (q.kind == k) return q;
        return null;
    }

    private int thrownCount() {
        int n = 0;
        for (Prop q : current.props) if (q.kind == Prop.Kind.BATH_ITEM && q.solved) n++;
        return n;
    }

    // бросить вещь в воду: вода темнеет; когда она почти чёрная, всё брошенное всплывает обратно
    private void bathThrow(Prop p) {
        Prop bath = findKind(Prop.Kind.BATH);
        if (bath == null || !bath.done) { showMessage("Сначала подойди к ванне"); return; }
        p.solved = true;
        bath.placed = Math.max(0, bath.placed) + 1;
        int thrown = thrownCount();
        if (thrown >= 3) { bathDone(true); return; }
        showMessage("В воду упало: " + Prop.BITEM_NAMES[Math.floorMod(p.want, 4)] + ". " + (thrown == 1 ? "Вода чуть потемнела" : "Вода мутнеет"));
    }

    // отойти от ванны: если что-то уже бросили — вода останется тёмной, брошенное всплывёт
    private void bathLeave() {
        bathDone(thrownCount() > 0);
    }

    private void bathDone(boolean returned) {
        Prop bath = findKind(Prop.Kind.BATH);
        if (bath != null) {
            bath.action = null;
            if (returned) bath.placed = 4;
        }
        for (Prop q : current.props) {
            if (q.kind != Prop.Kind.BATH_ITEM) continue;
            q.action = null;
            if (returned) q.solved = false;                     // всё, что бросили, всплыло обратно
        }
        unlockExit(returned ? "Вода почернела." : "Эльза отошла от воды.");
        if (returned) {
            say("", "Вода почти чёрная. Всё, что Эльза бросила, всплыло обратно.",
                    "Эльза|Это не смывается.",
                    "Эльза|Прошлое нельзя просто смыть. Оно только темнеет, пока я делаю вид, что его нет.");
        } else {
            say("", "Эльза|Нет. Я оставлю всё как есть.", "Эльза|Это тоже я.");
        }
    }

    // метки роста читаются снизу вверх; ошибёшься — всё сначала
    private boolean marksPending() {
        for (Prop q : current.props) if (q.kind == Prop.Kind.HEIGHT_MARK && !q.solved) return true;
        return false;
    }

    private void readMark(Prop p) {
        int expect = 0;
        for (Prop q : current.props) if (q.kind == Prop.Kind.HEIGHT_MARK && q.solved) expect++;
        if (p.want != expect) {
            for (Prop q : current.props) if (q.kind == Prop.Kind.HEIGHT_MARK) q.solved = false;
            showMessage("Эльза росла снизу вверх: читай метки по порядку, начиная с самой низкой");
            return;
        }
        p.solved = true;
        int total = Prop.HMARK_AGES.length;
        if (expect + 1 < total) {
            showMessage("Метка: " + Prop.HMARK_AGES[p.want] + " ЛЕТ (" + (expect + 1) + "/" + total + ")");
            return;
        }
        say("Все метки прочитаны. Теперь можно сделать выбор",
                "Последняя отметка: 16 ЛЕТ.",
                "Эльза|Шестнадцать.",
                "Эльза|Дальше на стене пусто. Меня здесь больше никто не измерял.");
    }

    private Prop findProp(String action, int want) {
        for (Prop q : current.props) if (action.equals(q.action) && q.want == want) return q;
        return null;
    }

    // оба манекена одеты как на портрете: видно, что платье Эльзы теперь на манекене Анны
    private void girlsDone() {
        for (Prop q : current.props) if ("GMANN".equals(q.action) && q.want == 1) q.reveal = true;
        unlockExit("Манекены одеты как на портрете.");
        say("", "Эльза|Подожди...",
                "Эльза|На подоле вышита буква. «Э».",
                "Эльза|Это моё платье. Я носила его, а теперь оно на ней.",
                "Эльза|Всё, что было моим, как-то оказывалось её.");
    }

    // всё разложено верно: на стене проступает надпись
    private void tableDone() {
        for (Prop q : current.props) if (q.kind == Prop.Kind.PLAQUE && q.hidden) q.hidden = false;
        unlockExit("Всё лежит на своих местах.");
        say("", "На стене проступила надпись:",
                "Зависть начинается там, где чужое счастье кажется доказательством твоего несчастья.");
    }

    // ====== ЗЕРКАЛО АННЫ: окно ввода ======
    private void startInput(Prop mirror) {
        inputProp = mirror;
        inputBuf.setLength(0);
        inputMsg = "";
        up = down = left = right = jump = false;
        player.moving = false;
        checkTimer = 0;
        checkVase = null;
    }

    private void submitInput() {
        String t = inputBuf.toString().trim().toLowerCase();
        if (t.contains("ненавиж")) resolveMirror(false);
        else if (t.contains("прощ")) resolveMirror(true);
        else inputMsg = "Слова не находятся. Напиши «ненавижу» или «прощаю»";
    }

    // ответ зеркалу — это и есть выбор комнаты-греха (s3): «прощаю» — добрый поступок, «ненавижу» — плохой
    private void resolveMirror(boolean good) {
        Prop m = inputProp;
        inputProp = null;
        m.solved = true;
        m.action = null;
        m.placed = good ? 1 : 2;
        sinDeed(good);
        String effect;
        if (good) {
            player.hp = player.maxHp;
            effect = "Чистая совесть: сердца восстановлены";
        } else {
            effect = applyCurse();
        }
        unlockExit(null);
        showMessage((good ? "Эльза простила Анну. Зависть отступила." : "Эльза выбрала ненависть. Зависть победила.") + "\n" + effect);
    }

    private void drawInput(Graphics2D g) {
        g.setColor(new Color(0, 0, 0, 200));
        g.fillRect(0, 0, Room.VIEW_W, Room.VIEW_H);
        int cy = Room.VIEW_H / 2;
        g.setFont(new Font("Monospaced", Font.BOLD, 28));
        g.setColor(new Color(200, 235, 210));
        drawCentered(g, "В зеркале — Анна.", cy - 70);
        g.setFont(new Font("Monospaced", Font.PLAIN, 18));
        g.setColor(Color.WHITE);
        drawCentered(g, "Что ты чувствуешь к ней? Напиши ответ:", cy - 36);

        int w = 460, h = 44, x = (Room.VIEW_W - w) / 2, y = cy - 16;
        g.setColor(new Color(20, 28, 24));
        g.fillRect(x, y, w, h);
        g.setColor(current.theme.accent);
        g.drawRect(x, y, w, h);
        g.setFont(new Font("Monospaced", Font.BOLD, 24));
        String shown = inputBuf.toString() + ((tick / 30) % 2 == 0 ? "_" : "");
        g.setColor(Color.WHITE);
        g.drawString(shown, x + 12, y + 31);

        g.setFont(new Font("Monospaced", Font.PLAIN, 16));
        if (!inputMsg.isEmpty()) {
            g.setColor(new Color(255, 120, 120));
            drawCentered(g, inputMsg, y + h + 30);
        }
        g.setColor(new Color(170, 190, 180));
        drawCentered(g, "ненавижу   /   прощаю", y + h + 58);
        drawCentered(g, "Enter — ответить   ·   Esc — отойти от зеркала", y + h + 82);
    }

    // цепочка собрана: комната наказаний — открывается проход; семья Осмаров — открывается тайник и проступает надпись
    private void seqDone(Prop last) {
        if (last.kind == Prop.Kind.PORTRAIT) {
            for (Prop q : current.props) {
                if (q.kind == Prop.Kind.PORTRAIT && q.want == 3) q.reveal = true;
                if (q.kind == Prop.Kind.STASH) q.solved = true;
            }
            unlockExit("Тайник открыт.");
            say("", "На портрете Анны проступила надпись:", "«Она получила всё.»");
        } else {
            unlockExit("Предметы расставлены верно. Проход открыт.");
        }
    }

    // весь фуршет растаял: Эльза понимает, что еда была ненастоящей
    private void foodDone() {
        unlockExit("Весь фуршет растаял.");
        say("", "Эльза|Я знала.", "Эльза|Я просто очень хотела поверить, что это настоящее.");
    }

    // удар мечом по иллюзии столовой: стол, гости и стул рассеиваются после ILLUSION_HITS ударов
    private void hitIllusion() {
        Prop table = null;
        boolean touched = false;
        Rectangle hit = swordHit();
        for (Prop q : current.props) {
            if (!q.isIllusion() || q.solved) continue;
            if (q.kind == Prop.Kind.HALL_TABLE) table = q;
            if (hit.intersects(q.box())) touched = true;
        }
        if (table == null || !touched) return;
        table.hp--;
        table.flash = 10;
        if (table.hp > 0) {
            showMessage("Иллюзия дрожит... (" + (ILLUSION_HITS - table.hp) + "/" + ILLUSION_HITS + ")");
            return;
        }
        for (Prop q : current.props) if (q.isIllusion()) q.solved = true;
        unlockExit("Иллюзия рассеялась: ни стола, ни голосов — только пустая комната.");
    }

    // все игрушки розданы: находится спрятанный мешочек с монетами
    private void toysDone() {
        for (Prop q : current.props) if (q.kind == Prop.Kind.POUCH) q.hidden = false;
        inventory.add(new Item(Item.Type.COIN, "Мешочек монет", "Спрятан в приюте под столом"));
        unlockExit("Все игрушки розданы. Под столом нашёлся спрятанный мешочек с монетами!");
        say("", "Эльза|Им было что отдавать.", "Эльза|Только не мне.");
    }

    // ====== ДИАЛОГИ ======
    private void checkProps() {
        if (dialogCool > 0 || checkTimer > 0) return;              // реплика не должна вылезать сразу после входа / другой реплики
        if (current.sideView && !player.onGround) return;         // и пока Эльза в прыжке или падает
        for (Prop p : current.props) {
            if (p.script == null || p.done) continue;
            boolean near = Math.abs(p.centerX() - player.centerX()) < 70
                    && (current.sideView || Math.abs(p.centerY() - player.centerY()) < 90);
            if (near) { startDialog(p); return; }
        }
    }

    // Кошка — индикатор реальности: у Эльзы на плечах она реагирует только на то, что существует.
    // Подходишь к иллюзии — кошка равнодушна или прижимает уши; к настоящему — оживляется. Подсказка показывается один раз.
    private void checkCatHints() {
        if (messageTimer > 0 || dialog != null || mashProp != null) return;
        for (Prop p : current.props) {
            if (p.catHint == null || p.hinted || p.solved) continue;
            if (Math.abs(p.centerX() - player.centerX()) < 120) {
                p.hinted = true;
                showMessage(p.catHint);
                return;
            }
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
        if (dialog == null || dialogT < 12) return;               // первые кадры реплики пропустить нельзя
        int full = dialogText().length() * DIALOG_TYPE;
        if (dialogT < full) { dialogT = full; return; }
        if (++dialogIdx >= dialog.length) endDialog(); else dialogT = 0;
    }

    private void endDialog() {
        Prop p = dialogProp;
        dialog = null;
        dialogProp = null;
        inputLock = 30;                                      // после реплики E / L полсекунды ничего не выбирают
        dialogCool = 90;                                     // и следующая реплика не вылезает сразу
        if (p == null) return;
        if (p.input) {                                       // зеркало: после сценки — окно ввода (потом E у зеркала откроет его снова)
            p.done = true;
            p.action = "MIRROR";
            startInput(p);
            return;
        }
        if (p.mash) {                                        // после сценки — «вырвись»; done выставит finishMash
            startMash(p);
            return;
        }
        p.done = true;
        if (p.kind == Prop.Kind.BATH) p.action = "BLEAVE";   // после сцены: E — отойти от воды
        String txt = p.endText == null ? "" : p.endText;
        String eff = propEffect(p);
        if (!eff.isEmpty()) txt = txt.isEmpty() ? eff : txt + "\n" + eff;
        if (!txt.isEmpty()) showMessage(txt);
    }

    // ====== МИНИ-ИГРА «ВЫРВИСЬ» ======
    // Шкала всё время падает (он держит), каждое нажатие E её поднимает. Заполнила — вырвалась.
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
        if (p.alwaysOpen) {                                  // Себастьян: дверь открывается в любом случае, без греха и проклятия
            if (win) player.hp = player.maxHp;
            unlockExit(null);
            say(p.winText, "Эльза|" + p.winLine);
            return;
        }
        String effect;
        if (win) {
            player.hp = player.maxHp;
            effect = "Сердца восстановлены";
        } else {
            int[] id = parseTrial(current.key);              // проигрыш в любой комнате локации = грех этой локации (s1..s7)
            String sinKey = id == null ? current.key : "s" + id[0];
            if (sins.bad(sinKey)) System.out.println("Плохих поступков: " + sins.badCount());
            effect = applyCurse();
            if (p.loseDark) blackTimer = 80;                 // тёмный экран на секунду и стон
        }
        unlockExit(null);
        String msg = (win ? p.winText : p.loseText) + "\n" + effect;
        if (win && p.winLine != null) say(msg, "Эльза|" + p.winLine);
        else showMessage(msg);
    }

    private void drawMash(Graphics2D g) {
        int top = 82;                                        // панель вверху, чтобы не закрывать Эльзу и персонажа на полу
        g.setColor(new Color(0, 0, 0, 175));
        g.fillRect(0, top, Room.VIEW_W, 150);
        g.setColor(current.theme.accent);
        g.fillRect(0, top + 150, Room.VIEW_W, 3);

        g.setFont(new Font("Monospaced", Font.BOLD, 34));
        g.setColor(new Color(255, 90, 90));
        drawCentered(g, mashProp.mashTitle != null ? mashProp.mashTitle : "ВЫРВИСЬ!", top + 50);
        g.setFont(new Font("Monospaced", Font.BOLD, 18));
        g.setColor(Color.WHITE);
        drawCentered(g, mashProp.mashHint != null ? mashProp.mashHint : "Жми E как можно быстрее — не дай себя удержать", top + 78);

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
        String hint = "E / ПРОБЕЛ — дальше    ESC — пропустить";
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
        catIntro.clear();
        carryToy = -1;
        carryCrest = -1;
        carryCrestProp = null;
        inputLock = dialogCool = 0;
        heldAt.clear();
        carryGDress = carryTItem = -1;
        inputProp = null;
        slowTimer = poisonTimer = darkTimer = weakTimer = 0;
        distortTimer = 0;
        sleepDark = 0;
        prideDark = prideTarget = 0;
        jigProp = null;
        jigReady = false;
        jigSel = -1;
        jigWin = 0;
        blackTimer = 0;
        slowPermanent = false;
        checkTimer = 0;
        checkVase = null;
        dialog = null;
        dialogProp = null;
        mashProp = null;
        mashDown.clear();
        inputProp = null;
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
        startLoop();
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
        heldAt.clear();
        inputProp = null;
        up = down = left = right = jump = false;
        invOpen = false;
    }
    // Пауза (меню по ESC): мир замирает, но ничего не сбрасывается — потом можно продолжить с того же места
    public void pause() {
        timer.stop();
        adminHide();
        up = down = left = right = jump = false;
        mashDown.clear();
        heldAt.clear();
    }

    public void resume() {
        startLoop();
        requestFocusInWindow();
    }

    // Где остановился игрок — подпись на кнопке «ПРОДОЛЖИТЬ» в главном меню
    public String progressText() {
        if (current == null) return "";
        String k = current.key == null ? "" : current.key;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\d").matcher(k);
        return (m.find() ? "Локация " + m.group() + " · " : "") + current.name;
    }
    // Лень, комната 2: чем дольше Эльза стоит у кровати и не ложится, тем темнее экран
    private void updateSleep() {
        Decision d = current.choice;
        if ("r6_2".equals(current.key) && d != null && !d.decided) {
            double dist = Math.hypot(d.centerX() - player.centerX(), d.centerY() - player.centerY());
            if (dist < 320) sleepDark = Math.min(0.88, sleepDark + 0.0007);
            else sleepDark = Math.max(0, sleepDark - 0.002);
            if (!sleepThought && sleepDark > 0.3) {
                sleepThought = true;
                say("", "Эльза|Если я усну... мне больше ничего не придётся делать.");
            }
        } else if (sleepDark > 0) {
            sleepDark = Math.max(0, sleepDark - 0.02);
        }
    }

    private void enterRoom(Room r) {
        if (current != null && current != r) dropCarried();     // то, что Эльза несла, возвращается на место: не тащим вазу/платье в другую комнату
        dialogCool = 50;                                          // после входа реплики не вылезают сразу
        r.fixFloatingSpikes();
        sleepThought = false;
        current = r;
        int chosen = 0;                                           // Гордыня, комната 1: сколько верных платьев уже выбрано — столько и темноты
        for (Prop q : r.props) if (q.kind == Prop.Kind.PRIDE_DRESS && q.solved && !q.fake) chosen++;
        prideTarget = prideDark = chosen / 3.0;
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
        if (blackTimer > 0) { if (--blackTimer == 0) blackText = "…ах…"; return; }            // тёмный экран: мир стоит
        if (messageTimer > 0) messageTimer--;
        if (titleTimer > 0) titleTimer--;
        if (inputLock > 0) inputLock--;
        if (dialogCool > 0) dialogCool--;
        updatePride();
        if (invOpen || won) return;                              // в инвентаре мир стоит
        if (dialog != null) { dialogT++; settlePlayer(); return; }               // во время реплики мир тоже стоит
        if (inputProp != null) { settlePlayer(); return; }       // и пока открыто окно ввода у зеркала
        if (jigProp != null) { settlePlayer(); if (jigWin > 0 && --jigWin == 0) finishJigsaw(); return; }   // и пока открыт пазл
        if (mashProp != null) { settlePlayer(); updateMash(); return; }          // во время «вырвись» мир тоже стоит

        // ----- эффекты -----
        if (slowTimer > 0) slowTimer--;
        if (darkTimer > 0) darkTimer--;
        if (weakTimer > 0) weakTimer--;
        if (distortTimer > 0) distortTimer--;
        updateSleep();
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
            say("Кошка запрыгнула тебе на плечи. Теперь она с тобой до конца пути. Мяу!",
                    "Эльза|Ты тоже ищешь что-нибудь, что можно забрать?");
        }

        // ----- кот -----
        if (current.cat != null && current.sideView) current.cat.move(current, 0, 4);   // сидит на полу
        if (pet != null) pet.follow(current, player);
        if (current.cat != null && current.cat.flash > 0) current.cat.flash--;

        for (Prop p : current.props)                          // гости столовой бормочут, пока Эльза рядом
            if (p.kind == Prop.Kind.DINER) p.active = Math.abs(p.centerX() - player.centerX()) < 320;
        checkProps();
        if (pet != null) checkCatHints();
        updateGoals();
        checkSpikes();
        trackSafeSpot();
        checkDoor();
        updateCamera();
    }

    // Эльза выходит из комнаты, не поставив то, что несла: всё возвращается на свои места
    private void dropCarried() {
        returnCrest();
        if (carryGDress >= 0) { Prop q = findProp("GDRESS", carryGDress); if (q != null) q.solved = false; }
        carryGDress = -1;
        if (carryTItem >= 0) { Prop q = findProp("TITEM", carryTItem); if (q != null) q.solved = false; }
        carryTItem = -1;
        if (carryToy >= 0) for (Prop q : current.props) if (q.kind == Prop.Kind.TOY_TABLE) q.toyMask |= 1 << carryToy;
        carryToy = -1;
    }

    // ваза возвращается на свою стойку
    private void returnCrest() {
        if (carryCrestProp != null) carryCrestProp.solved = false;
        carryCrestProp = null;
        carryCrest = -1;
    }

    // пока идёт реплика / мини-игра, мир стоит, но Эльза всё равно должна упасть на пол (иначе зависает в воздухе после прыжка)
    private void settlePlayer() {
        if (current == null || !current.sideView) return;
        player.moving = false;
        player.vy = Math.min(player.vy + GRAVITY, MAX_FALL);
        int wantY = (int) Math.round(player.vy);
        int oldY = player.y;
        player.move(current, 0, wantY);
        if (player.y - oldY != wantY) { player.onGround = wantY > 0; player.vy = 0; } else player.onGround = false;
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
        if (invOpen || won || checkTimer > 0 || inputLock > 0 || player.attackCooldown > 0) return;
        player.attackTimer = Player.ATTACK_FRAMES;
        player.attackCooldown = ATTACK_COOLDOWN;

        Decision near = nearChoice();                           // L — второй вариант выбора
        if (near != null) {
            resolveChoice(near, near.optL);
            return;
        }

        Rectangle hit = swordHit();
        int dmg = Math.max(1, (equipped != null ? equipped.damage : 1) - (weakTimer > 0 ? 1 : 0));

        for (Totem t : current.totems) {                      // тотемы ярости
            if (!t.broken && hit.intersects(t.box())) {
                t.hit();
                if (t.broken) showMessage(current.totemsLeft() > 0
                        ? "Тотем разбит! Осталось: " + current.totemsLeft() : "Тотем разбит!");
            }
        }

        hitIllusion();                                        // Чревоугодие: развеять иллюзию столовой

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

    // область, куда попадает клинок при взмахе
    private Rectangle swordHit() {
        int fx = player.facing == Player.Dir.RIGHT ? 1 : player.facing == Player.Dir.LEFT ? -1 : 0;
        int fy = player.facing == Player.Dir.DOWN ? 1 : player.facing == Player.Dir.UP ? -1 : 0;
        return new Rectangle(player.centerX() + fx * 34 - 30, player.centerY() + fy * 34 - 30, 60, 60);
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
        if (c == null || c.decided || marksPending()) return null;   // пока не прочитаны метки роста, выбор недоступен
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
        if ("s7".equals(current.key)) {                       // джинн: при любом выборе экран темнеет, и он говорит «я тебя услышал»
            blackText = "Джинн: «Я тебя услышал»";
            blackTimer = 230;
        }
        if ("r6_2".equals(current.key)) {                     // кровать: свет возвращается; уснула — тёмный экран и слабость
            sleepDark = 0;
            if (!opt.good()) {
                blackText = "Zzz…";
                blackTimer = 150;
                slowTimer = Math.max(slowTimer, 600);
            }
        }
        if (c.kind == Decision.Kind.RAG && opt.good()) {      // её комната: игрушка
            say(opt.result() + "\n" + effect,
                    "Эльза|Когда-то мне было достаточно целого дома.",
                    "Эльза|Теперь мне достаточно этого.");
            return;
        }
        if (c.kind == Decision.Kind.FISH)                     // мусорная комната: Эльза говорит с кошкой
            say(opt.result() + "\n" + effect, opt.good() ? "Эльза|Наверное... ты голоднее меня." : "Эльза|Прости.");
        else showMessage(opt.result() + "\n" + effect);
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
        if (invOpen || won || checkTimer > 0 || inputLock > 0) return;
        Decision cc = current.choice;
        if (cc != null && !cc.decided && marksPending()
                && Math.hypot(cc.centerX() - player.centerX(), cc.centerY() - player.centerY()) < 80) {
            showMessage("Сначала прочти метки роста на стене (E)");
            return;
        }
        Decision ch = nearChoice();
        if (ch != null) {                                     // E — первый вариант выбора
            resolveChoice(ch, ch.optE);
            return;
        }
        Prop ap = nearAction();
        if (ap != null) {                                     // загадки приюта (игрушки, печать)
            doAction(ap);
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
        player.hp = player.maxHp;                                  // комната пройдена — сердца восстанавливаются (иначе 5 сердец на всю игру)
        poisonTimer = 0;
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
        if (n == roomsIn(loc)) {
            hall.exitLocked = false;
            showMessage("Локация пройдена! В конце коридора открыта дверь СПУСК");
        } else {
            showMessage("Комната пройдена (" + n + "/" + roomsIn(loc) + "). Откроется следующая дверь");
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
            if (pet != null && isGluttonyRoom(current) && catIntro.add(current.key)) {
                if (current.key.equals("r5_1"))
                    showMessage("Кошка на плечах насторожилась: она реагирует только на то, что существует на самом деле.");
                else if (current.key.equals("s5"))
                    showMessage("Кошка на плечах принюхалась и тихо замяукала: здесь всё настоящее — и мусор, и рыба.");
            }
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
    // Лень: пока Эльзе плохо, картинка идёт волнами и зеленеет
    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);                                  // чёрные поля по краям при полном экране
        Graphics2D g = (Graphics2D) g0.create();
        View.apply(g, this);                                       // масштаб под размер окна
        paintGame(g);
        g.dispose();
    }

    private java.awt.image.BufferedImage distortBuf;                // буфер эффекта «плывёт» (не создаём новый каждый кадр)

    private void paintGame(Graphics g) {
        if (distortTimer > 0 && current != null && cutscene == null && epilogue == null) {
            if (distortBuf == null) distortBuf = new java.awt.image.BufferedImage(Room.VIEW_W, Room.VIEW_H, java.awt.image.BufferedImage.TYPE_INT_RGB);
            java.awt.image.BufferedImage img = distortBuf;
            Graphics2D ig = img.createGraphics();
            paintScene(ig);
            ig.dispose();
            double k = Math.min(1.0, distortTimer / 60.0);        // в последнюю секунду эффект затухает
            Graphics2D g2 = (Graphics2D) g;
            for (int y = 0; y < Room.VIEW_H; y += 4) {
                int off = (int) Math.round(Math.sin(tick * 0.15 + y * 0.05) * 14 * k);
                g2.drawImage(img, off, y, Room.VIEW_W + off, y + 4, 0, y, Room.VIEW_W, y + 4, null);
            }
            g2.setColor(new Color(60, 130, 60, (int) (70 * k)));
            g2.fillRect(0, 0, Room.VIEW_W, Room.VIEW_H);
        } else {
            paintScene(g);
        }
    }

    private void paintScene(Graphics g) {
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

        if (carryCrest >= 0 && current.sideView) {             // ваза с гербом над головой Эльзы
            Prop.crestIcon(world, player.centerX(), player.y - 50, carryCrest, 0.9);
        }
        if (carryGDress >= 0 && current.sideView) {            // платье в руках над головой Эльзы
            Prop.girlDress(world, player.centerX(), player.y - 62, carryGDress, 0.8, false);
        }
        if (carryTItem >= 0 && current.sideView) {             // предмет со стола над головой Эльзы
            Prop.tableItem(world, player.centerX() - 20, player.y - 70, carryTItem, 0.8);
        }
        if (carryToy >= 0 && current.sideView) {               // игрушка в руках над головой Эльзы
            Prop.toyIcon(world, player.centerX() - 11, player.y - 62, carryToy, 0.5);
        }
        Prop actionProp = nearAction();
        if (actionProp != null) {                              // подсказка у предмета, с которым можно взаимодействовать
            String hint = actionPrompt(actionProp);
            world.setFont(new Font("Monospaced", Font.BOLD, 15));
            int hw = world.getFontMetrics().stringWidth(hint);
            int hx = actionProp.centerX() - hw / 2, hy = (actionProp.row + 1) * Room.TILE + 28;
            world.setColor(new Color(0, 0, 0, 170));
            world.fillRect(hx - 6, hy - 16, hw + 12, 22);
            world.setColor(new Color(250, 236, 190));
            world.drawString(hint, hx, hy);
        }
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
        if (sleepDark > 0.01) drawSleep(overlay);
        if (prideDark > 0.01) drawPrideDark(overlay, player.centerX() - cx, player.centerY() - cy);
        overlay.dispose();

        drawHud((Graphics2D) g);
        drawTitleCard((Graphics2D) g);
        if (dialog != null) drawDialog((Graphics2D) g);
        if (mashProp != null) drawMash((Graphics2D) g);
        if (inputProp != null) drawInput((Graphics2D) g);
        if (jigProp != null) drawJigsaw((Graphics2D) g);
        if (invOpen) drawInventory((Graphics2D) g);
        if (won) drawVictory((Graphics2D) g);
        if (blackTimer > 0) drawBlackout((Graphics2D) g);
    }

    // тёмный экран после неудачного «вырвись» у Криса: чёрный кадр, стон, потом свет возвращается
    private void drawBlackout(Graphics2D g) {
        int a = blackTimer > 55 ? 255 : blackTimer * 255 / 55;
        g.setColor(new Color(0, 0, 0, a));
        g.fillRect(0, 0, Room.VIEW_W, Room.VIEW_H);
        if (blackTimer > 25) {
            g.setFont(new Font("Monospaced", Font.ITALIC | Font.BOLD, 26));
            g.setColor(new Color(170, 60, 70, Math.min(255, a)));
            drawCentered(g, blackText, Room.VIEW_H / 2);
        }
    }

    // Лень, комната 2: экран темнеет и плывут «z»
    private void drawSleep(Graphics2D g) {
        g.setColor(new Color(4, 6, 18, (int) (255 * Math.min(0.9, sleepDark))));
        g.fillRect(0, 0, Room.VIEW_W, Room.VIEW_H);
        g.setFont(new Font("Monospaced", Font.BOLD, 28));
        for (int i = 0; i < 6; i++) {
            int zx = 120 + i * 150 + (int) (Math.sin(tick * 0.02 + i) * 20);
            int zy = Room.VIEW_H - ((tick * (1 + i % 3) / 2 + i * 97) % Room.VIEW_H);
            g.setColor(new Color(170, 190, 255, (int) (140 * Math.min(1.0, sleepDark * 1.5))));
            g.drawString(i % 2 == 0 ? "z" : "Z", zx, zy);
        }
    }

    // Градиенты на весь экран очень дорогие, а при полном экране ещё дороже — поэтому рисуем их один раз в картинки.
    private static BufferedImage vignetteImg, darkImg;

    private void drawVignette(Graphics2D g) {
        if (vignetteImg == null) {
            vignetteImg = new BufferedImage(Room.VIEW_W, Room.VIEW_H, BufferedImage.TYPE_INT_ARGB);
            Graphics2D ig = vignetteImg.createGraphics();
            ig.setPaint(new RadialGradientPaint(
                    new Point2D.Float(Room.VIEW_W / 2f, Room.VIEW_H / 2f), Room.VIEW_W * 0.6f,
                    new float[]{0.55f, 1f},
                    new Color[]{new Color(0, 0, 0, 0), new Color(0, 0, 0, 190)}));
            ig.fillRect(0, 0, Room.VIEW_W, Room.VIEW_H);
            ig.dispose();
        }
        g.drawImage(vignetteImg, 0, 0, null);
    }

    private void drawDarkness(Graphics2D g, int px, int py) {
        final int R = 170;
        if (darkImg == null) {
            darkImg = new BufferedImage(R * 2, R * 2, BufferedImage.TYPE_INT_ARGB);
            Graphics2D ig = darkImg.createGraphics();
            ig.setPaint(new RadialGradientPaint(
                    new Point2D.Float(R, R), R,
                    new float[]{0f, 0.45f, 1f},
                    new Color[]{new Color(0, 0, 0, 0), new Color(0, 0, 0, 0), new Color(0, 0, 0, 240)}));
            ig.fillRect(0, 0, R * 2, R * 2);
            ig.dispose();
        }
        g.setColor(new Color(0, 0, 0, 240));                         // за пределами круга света — сплошная тьма
        g.fillRect(0, 0, Room.VIEW_W, Math.max(0, py - R));
        g.fillRect(0, Math.min(Room.VIEW_H, py + R), Room.VIEW_W, Math.max(0, Room.VIEW_H - (py + R)));
        g.fillRect(0, py - R, Math.max(0, px - R), R * 2);
        g.fillRect(Math.min(Room.VIEW_W, px + R), py - R, Math.max(0, Room.VIEW_W - (px + R)), R * 2);
        g.drawImage(darkImg, px - R, py - R, null);
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

    // картинки-обложки финалов: heaven_bg.png и hell_bg.png лежат рядом с GamePanel.java (как menu_bg.png)
    private static final Map<String, BufferedImage> ENDING_BG = new HashMap<>();

    private static BufferedImage loadEndingBg(String name) {
        if (ENDING_BG.containsKey(name)) return ENDING_BG.get(name);
        BufferedImage img = null;
        try {
            java.net.URL u = GamePanel.class.getResource(name);
            if (u != null) img = ImageIO.read(u);
            else {
                File f = new File("src/game/" + name);               // запасной путь, если запускаешь не из IDE
                if (f.exists()) img = ImageIO.read(f);
            }
        } catch (Exception ignored) { }
        ENDING_BG.put(name, img);
        return img;
    }

    private void drawVictory(Graphics2D g) {
        boolean heaven = ending == Sins.Ending.HEAVEN;
        int W = Room.VIEW_W, H = Room.VIEW_H;

        BufferedImage pic = loadEndingBg(heaven ? "heaven_bg.png" : "hell_bg.png");
        if (pic != null) {                                               // обложка финала — картинка на весь экран
            double k = Math.max(W / (double) pic.getWidth(), H / (double) pic.getHeight());
            int dw = (int) Math.ceil(pic.getWidth() * k), dh = (int) Math.ceil(pic.getHeight() * k);
            int dx = (W - dw) / 2, dy = -(int) ((dh - H) * 0.25);        // чуть выше центра, чтобы лицо не обрезалось
            Object oldHint = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.drawImage(pic, dx, dy, dw, dh, null);
            if (oldHint != null) g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, oldHint);
            g.setPaint(new GradientPaint(0, 250, new Color(8, 6, 14, 0), 0, H, new Color(8, 6, 14, 230)));
            g.fillRect(0, 250, W, H - 250);                              // затемнение снизу под текст
        } else {                                                         // картинки нет — просто фон
            g.setPaint(heaven ? new GradientPaint(0, 0, new Color(255, 250, 215), 0, H, new Color(150, 200, 255))
                    : new GradientPaint(0, 0, new Color(20, 0, 0), 0, H, new Color(170, 30, 10)));
            g.fillRect(0, 0, W, H);
        }
        boolean light = heaven && pic == null;                           // тёмный текст только на светлом запасном фоне

        g.setFont(new Font("Monospaced", Font.BOLD, 56));
        g.setColor(new Color(0, 0, 0, 190));
        drawCentered(g, heaven ? "РАЙ" : "АД", 403);
        g.setColor(light ? new Color(60, 90, 160) : heaven ? new Color(255, 225, 120) : new Color(255, 120, 60));
        drawCentered(g, heaven ? "РАЙ" : "АД", 400);
        g.setFont(new Font("Monospaced", Font.BOLD, 18));
        String stat = "Добрых: " + sins.goodCount() + "   Плохих: " + sins.badCount()
                + "   (Рай — от " + Sins.HEAVEN_LIMIT + " добрых, Ад — от " + Sins.HELL_LIMIT + " плохих)";
        g.setColor(light ? new Color(40, 60, 110) : Color.WHITE);
        drawCentered(g, stat, 450);
        g.setFont(new Font("Monospaced", Font.PLAIN, 16));
        g.setColor(light ? new Color(40, 60, 110) : new Color(225, 225, 235));
        drawCentered(g, "Enter — дальше (эпилог начнётся сам)", 500);
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
            return r.exitLocked ? ("Цель: пройди " + roomsIn(r.key.charAt(1) - '0') + " комнаты по порядку (" + roomsDone(r.key.charAt(1) - '0') + "/" + roomsIn(r.key.charAt(1) - '0') + ")")
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
            case PUZZLE  -> r.exitLocked ? ("ответь зеркалу".equals(r.goalLabel) ? "Цель: подойди к зеркалу и ответь Анне" : "Цель: " + r.goalLabel + " (E у предметов в комнате)") : open;
            case ESCAPE  -> r.exitLocked ? ("вырвись".equals(r.goalLabel) ? "Цель: подойди к нему и вырвись (E)" : "Цель: подойди к Себастьяну и открой дверь (E)") : open;
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
        inputProp = null;
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
        for (int n = 1; n <= roomsIn(loc); n++) {
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
        distortTimer = 0;
        sleepDark = 0;
        prideDark = prideTarget = 0;
        jigProp = null;
        jigReady = false;
        jigSel = -1;
        jigWin = 0;
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
            for (int n = 1; n < roomsIn(loc); n++) out.add(trialKey(loc, n));
            out.add(trialKey(loc, roomsIn(loc)));
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

        int hk = isKey(e, KeyEvent.VK_E, 'у') ? KeyEvent.VK_E : isKey(e, KeyEvent.VK_L, 'д') ? KeyEvent.VK_L : k;
        long nowMs = System.currentTimeMillis();
        Long prevMs = heldAt.put(hk, nowMs);
        boolean fresh = prevMs == null || nowMs - prevMs > 1000;          // false — это автоповтор зажатой клавиши

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

        if (inputProp != null) {                                         // окно ввода у зеркала: печатаем ответ
            if (k == KeyEvent.VK_ENTER) submitInput();
            else if (k == KeyEvent.VK_BACK_SPACE) { if (inputBuf.length() > 0) inputBuf.setLength(inputBuf.length() - 1); }
            else if (k == KeyEvent.VK_ESCAPE) {
                inputProp = null;
                showMessage("Эльза отвернулась от зеркала. Подойди и посмотри ещё раз (E)");
            }
            return;
        }

        if (jigProp != null) {                                           // пазл с картиной семьи
            if (jigWin > 0) return;
            if (k == KeyEvent.VK_ESCAPE) {
                jigProp = null;
                showMessage("Эльза отошла от картины. Подойди и сложи её (E)");
            } else if (k == KeyEvent.VK_LEFT || k == KeyEvent.VK_A) jigMove(-1, 0);
            else if (k == KeyEvent.VK_RIGHT || k == KeyEvent.VK_D) jigMove(1, 0);
            else if (k == KeyEvent.VK_UP || k == KeyEvent.VK_W) jigMove(0, -1);
            else if (k == KeyEvent.VK_DOWN || k == KeyEvent.VK_S) jigMove(0, 1);
            else if (k == KeyEvent.VK_SPACE || k == KeyEvent.VK_ENTER || isKey(e, KeyEvent.VK_E, 'у')) jigSelect();
            return;
        }

        if (mashProp != null) {                                          // «вырвись»: считаем только новые нажатия
            if (isKey(e, KeyEvent.VK_E, 'у') && mashDown.add(KeyEvent.VK_E)) mashPress();   // только E: пробел и Enter не считаются
            return;
        }

        if (dialog != null) {                                            // идёт реплика Эльзы
            if (k == KeyEvent.VK_ESCAPE) endDialog();
            else if (fresh && (k == KeyEvent.VK_SPACE || k == KeyEvent.VK_ENTER || isKey(e, KeyEvent.VK_E, 'у'))) advanceDialog();
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

        if (isKey(e, KeyEvent.VK_L, 'д')) { if (fresh) attack(); return; }          // меч — L
        if (isKey(e, KeyEvent.VK_E, 'у')) { if (fresh) interact(); return; }

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
        heldAt.remove(e.getKeyCode());
        if (isKey(e, KeyEvent.VK_E, 'у')) heldAt.remove(KeyEvent.VK_E);
        if (isKey(e, KeyEvent.VK_L, 'д')) heldAt.remove(KeyEvent.VK_L);
        if (isKey(e, KeyEvent.VK_E, 'у')) mashDown.remove(KeyEvent.VK_E);
        switch (e.getKeyCode()) {
            case KeyEvent.VK_W, KeyEvent.VK_UP -> up = false;
            case KeyEvent.VK_S, KeyEvent.VK_DOWN -> down = false;
            case KeyEvent.VK_A, KeyEvent.VK_LEFT -> left = false;
            case KeyEvent.VK_D, KeyEvent.VK_RIGHT -> right = false;
            case KeyEvent.VK_SPACE -> jump = false;
        }
    }

    @Override public void keyTyped(KeyEvent e) {
        if (inputProp == null) return;
        char c = e.getKeyChar();
        if (c >= 32 && c != 127 && inputBuf.length() < 20) {
            inputBuf.append(c);
            inputMsg = "";
        }
    }
}