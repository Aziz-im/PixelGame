package game;

import java.awt.*;
import java.awt.geom.AffineTransform;

// Декорации и персонажи сцен (комнаты с видом сбоку). Стоят на полу — ряд 9 (картины и табличка висят выше).
//  script  — сценка: реплики вида "Кто|Текст". Запускается сама, когда Эльза подходит.
//  mash    — после сценки включается мини-игра «вырвись» (жми E / ПРОБЕЛ).
//  prompt  — подсказка «E — ...» для предметов, с которыми можно взаимодействовать вручную (ванна).
public class Prop {
    public enum Kind {
        PAINTING, WHIPS, PILLORY, TOYS, PLAQUE, WORKER, DESK, CABINET, CLOTHES,
        CHRIS, SEBASTIAN, BUFFET, CANTEEN, TRASH, BATH, RAG, DOLL, DRESS, FAMILY, BED,
        KID_BED, TOY_TABLE, DOC, POUCH,      // Алчность: загадки приюта (игрушки детям, поддельная печать)
        CREST_VASE, PEDESTAL,                // Гнев, комната 1: вазы с гербами и пьедесталы
        WALL_ITEM,                           // Гнев, комната 2: ключ, цепь, плеть, дверь (порядок)
        PORTRAIT, FAMILY_PORTRAIT, STASH,    // Гнев, комната 3: портреты по иерархии, большой семейный портрет, тайник
        DRESS_OPTION, MANNEQUIN, DRESS_PORTRAIT,  // Похоть, комната 1: платья на выбор, манекен, старый семейный портрет
        GIRL_RACK, GIRL_MANNEQUIN, GIRLS_PORTRAIT,   // Зависть, комната 1: вешалки, манекены Эльзы и Анны, портрет двух девочек
        TABLE_ITEM, PLACE,                           // Зависть, комната 2: предметы на столе, места Эльзы и Анны
        ANNA, MIRROR,                                // Зависть, комната 3: Анна и зеркало
        FEAST_DISH,                                  // Чревоугодие, комната 1: блюдо на фуршете (тает, если коснуться)
        HALL_TABLE, DINER, FREE_CHAIR,                // Чревоугодие, комната 2: стол слуг, сидящие за ним и «свободный» стул — всё это иллюзия
        BATH_ITEM, HEIGHT_MARK                       // Лень: вещи прошлого у ванны и метки роста на стене
    }

    private static final int T = Room.TILE;
    private static final Color SKIN = new Color(236, 196, 174);

    public final Kind kind;
    public final int col, row;
    public String text = "";               // для таблички: строки через \n
    public String prompt;                  // подсказка «E — ...» (ручное взаимодействие)
    public String[] script;                // сценка (диалог)
    public boolean mash;                   // после сценки — QTE
    public String endText, winText, loseText;
    public String effect;                  // эффект после сценки: "SICK", "SLEEPY"
    public boolean done;
    // --- загадки (Алчность) ---
    public String action;                  // null — просто декорация; иначе E запускает действие (TOY_TAKE, KID, DOC)
    public int want;                       // KID: какую игрушку хочет ребёнок (0..2)
    public boolean forged;                 // DOC: печать подделана
    public boolean solved;                 // KID — получил игрушку, DOC — порван
    public boolean hidden;                 // не рисуется (мешочек до решения загадки)
    public int toyMask = 7;                // TOY_TABLE: какие игрушки ещё лежат на столе (биты 0..2)
    public String label = "";              // имя ребёнка
    // --- загадки (Гнев) ---
    // CREST_VASE / PEDESTAL: want = номер герба; PEDESTAL: solved = ваза Осмаров стоит на нём
    // WALL_ITEM / PORTRAIT (action "SEQ"): want = место в правильном порядке (0..3); placed = номер, под которым игрок его выбрал
    public int placed = -1;
    public boolean reveal;                 // PORTRAIT Анны: проступила надпись «Она получила всё.»
    // --- Похоть ---
    // DRESS_OPTION: want = фасон платья (0..3), revealing = слишком открытое (Эльза его не наденет)
    // MANNEQUIN: placed = фасон платья, надетого на манекен; DRESS_PORTRAIT: want = платье с портрета (правильный ответ)
    public boolean revealing;
    // --- Зависть ---
    // GIRL_RACK: want = платье (0..3); GIRL_MANNEQUIN: want = кто (0 Эльза, 1 Анна), placed = надетое платье, reveal = видна вышитая «Э»
    // TABLE_ITEM: want = предмет (0..2 — Анны, 3..5 — Эльзы); PLACE: want = чьё место (0 Эльза, 1 Анна), mask = положенные предметы
    // MIRROR: input = после сценки открывается окно ввода; placed = 1 простила, 2 возненавидела
    public int mask;
    public boolean input;
    // --- Чревоугодие ---
    // Кошка — индикатор реальности: она тянется только к тому, что существует. Если рядом с предметом стоит catHint,
    // а кошка сидит у Эльзы на плечах, то при приближении один раз покажется подсказка (кошка на него не реагирует / реагирует).
    // FEAST_DISH: want = блюдо (0 мясо, 1 хлеб, 2 фрукты, 3 вино, 4 десерт); solved = к нему прикоснулись, оно тает
    // HALL_TABLE: want = длина стола в клетках, hp = сколько ударов мечом осталось, чтобы развеять иллюзию
    // DINER: want = кто сидит (0 господин, 1 слуга, 2 госпожа); mask = очередь реплики в группе (0..2); active = Эльза рядом, и он бормочет
    // FREE_CHAIR: action SIT — сесть нельзя (это тоже галлюцинация)
    public String catHint;
    public boolean hinted;
    public int hp, flash, fade;
    public boolean active;
    public static final String[] FOOD_NAMES = {"мясо", "хлеб", "фрукты", "вино", "десерт"};
    // Лень: BATH — placed = насколько потемнела вода (0..4); BATH_ITEM — want = вещь (0..3), solved = брошена в воду;
    // HEIGHT_MARK — want = номер метки снизу вверх (0..4), solved = прочитана (над ней видно возраст)
    public static final String[] BITEM_NAMES = {"старую фотографию", "детскую ленточку", "письмо", "стоптанный башмак"};
    public static final int[] HMARK_AGES = {6, 9, 12, 14, 16};
    private static final String[][] DINER_SAY = {
            {"опять она", "лучше бы её не было"},          // господин
            {"что она тут делает", "опять она"},           // слуга
            {"что она тут делает", "лучше бы её не было"}  // госпожа
    };
    public static final int GIRL_ELSA_DRESS = 0, GIRL_ANNA_DRESS = 3;          // что на портрете надето на Эльзе и на Анне
    public static final String[] GDRESS_NAMES = {"серое платье", "платье с заплатками", "розовое шёлковое платье", "кремовое платье с лентой"};
    public static final String[] TITEM_NAMES = {"торт", "подарок", "цветы", "корку хлеба", "жестяную кружку", "огарок свечи"};
    // сценки «вырвись» (Крис, Себастьян): особые настройки
    public String winLine;                 // реплика Эльзы после успеха
    public boolean loseDark;               // при провале — тёмный экран и стон
    public boolean alwaysOpen;             // Себастьян: дверь открывается и при успехе, и при провале (без греха и проклятия)
    public String mashTitle, mashHint;     // свои надписи на шкале (если null — «ВЫРВИСЬ!»)
    public static final int REVEALING_DRESS = 3;                              // фасон «слишком открытого» платья
    public static final String[] DRESS_NAMES = {"бордовое", "синее", "зелёное", "откровенное"};
    private static final Color[] DRESS_COLORS = {new Color(150, 36, 66), new Color(52, 84, 160),
            new Color(40, 128, 96), new Color(40, 16, 36)};
    public static final int OSMAR_CREST = 0;                                  // герб Осмаров — звезда о семи лучах
    public static final String[] ITEM_NAMES = {"ключ", "цепь", "плеть", "дверь"};   // порядок: ключ → цепь → плеть → дверь
    public static final String[] FAMILY_NAMES = {"ОТЕЦ", "МАТЬ", "СТАРШИЙ СЫН", "АННА"};   // порядок иерархии семьи
    public static final String[] TOY_NAMES = {"кубики", "мишку", "мячик"};
    private Cat bedCat;                    // кошка на кровати (для Kind.BED)
    private int age;                       // кадров после завершения (для исчезновения)

    public Prop(Kind kind, int col, int row) {
        this.kind = kind;
        this.col = col;
        this.row = row;
    }

    // персонаж, с которым Эльза говорит (он отвечает молчанием или репликами)
    public static Prop talk(Kind kind, int col, int row, String endText, String... script) {
        Prop p = new Prop(kind, col, row);
        p.script = script;
        p.endText = endText;
        return p;
    }

    // персонаж, от которого нужно вырваться: сначала диалог, потом мини-игра
    public static Prop mash(Kind kind, int col, int row, String winText, String loseText, String... script) {
        Prop p = new Prop(kind, col, row);
        p.script = script;
        p.mash = true;
        p.winText = winText;
        p.loseText = loseText;
        return p;
    }

    // предмет, с которым можно взаимодействовать клавишей E (загадки)
    public static Prop action(Kind kind, int col, int row, String action) {
        Prop p = new Prop(kind, col, row);
        p.action = action;
        return p;
    }

    public static Prop plaque(int col, int row, String text) {
        Prop p = new Prop(Kind.PLAQUE, col, row);
        p.text = text;
        return p;
    }

    // иллюзия столовой: её можно развеять ударом меча
    public boolean isIllusion() {
        return kind == Kind.HALL_TABLE || kind == Kind.DINER || kind == Kind.FREE_CHAIR;
    }

    // область, в которую должен попасть клинок
    public Rectangle box() {
        return switch (kind) {
            case HALL_TABLE -> new Rectangle(col * T, row * T + 18, Math.max(1, want) * T, 34);
            case DINER -> new Rectangle(col * T + 6, row * T - 14, 36, 62);
            default -> new Rectangle(col * T + 8, row * T + 6, 32, 42);
        };
    }

    public int centerX() {
        return col * T + T / 2;
    }

    public int centerY() {
        return row * T + T / 2;
    }

    // ================= ОТРИСОВКА =================
    public void draw(Graphics2D g, int tick) {
        if (hidden) return;
        if (done) age++;
        int x = col * T, y = row * T;

        if (done && kind == Kind.WORKER) x += Math.min(age, 70);          // работница молча уходит прочь
        float a = 1f;
        if (done && (kind == Kind.CHRIS || kind == Kind.SEBASTIAN || kind == Kind.WORKER)) a = Math.max(0f, 1f - age / 60f);   // уходят
        if (solved && isIllusion()) a = Math.max(0f, 1f - ++fade / 70f);       // иллюзия рассеивается
        if (a <= 0f) return;
        Composite old = g.getComposite();
        if (a < 1f) g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, a));

        final int fx = x, fy = y;
        switch (kind) {
            case PAINTING -> drawPainting(g, x, y);
            case WHIPS -> drawWhips(g, x, y);
            case PILLORY -> drawPillory(g, x, y);
            case TOYS -> drawToys(g, x, y);
            case PLAQUE -> drawPlaque(g, x, y);
            case WORKER -> {
                person(g, x + 24, y + 46, new Color(150, 150, 150), new Color(96, 100, 112), new Color(40, 40, 50), true, 1.0, true);
                g.setColor(new Color(224, 224, 218));                              // фартук
                g.fillRect(x + 19, y + 17, 10, 20);
            }
            case DESK -> drawDesk(g, x, y, tick);
            case CABINET -> drawCabinet(g, x, y);
            case CLOTHES -> drawClothes(g, x, y);
            case CHRIS -> {
                person(g, x + 24, y + 46, new Color(30, 24, 28), new Color(52, 52, 68), new Color(30, 30, 40), false, 1.0, false);
                g.setColor(new Color(190, 40, 60));                                // галстук
                g.fillRect(x + 23, y + 14, 3, 12);
            }
            case SEBASTIAN -> {
                person(g, x + 24, y + 46, new Color(112, 72, 40), new Color(52, 76, 120), new Color(40, 40, 52), false, 1.0, false);
                g.setColor(new Color(240, 200, 60));                               // пуговицы ливреи
                g.fillRect(x + 23, y + 16, 3, 3);
                g.fillRect(x + 23, y + 23, 3, 3);
                if (!done) {                                                       // он протягивает руку
                    g.setColor(new Color(52, 76, 120));
                    g.fillRect(x + 4, y + 17, 10, 4);
                    g.setColor(SKIN);
                    g.fillRect(x - 1, y + 16, 6, 5);
                }
            }
            case BUFFET -> drawBuffet(g, x, y, tick);
            case CANTEEN -> drawCanteen(g, x, y);
            case TRASH -> drawTrash(g, x, y, tick);
            case BATH -> drawBath(g, x, y, tick);
            case RAG -> drawRag(g, x, y);
            case DOLL -> drawDoll(g, x, y);
            case DRESS -> drawDress(g, x, y, tick);
            case FAMILY -> drawFamily(g, x, y, tick);
            case BED -> drawBedWithCat(g, x, y, tick);
            case KID_BED -> big(g, x, y, 1.3, () -> drawKidBed(g, fx, fy));
            case TOY_TABLE -> big(g, x, y, 1.5, () -> drawToyTable(g, fx, fy));
            case DOC -> big(g, x, y, 1.9, () -> drawDoc(g, fx, fy));
            case POUCH -> drawPouch(g, x, y, tick);
            case CREST_VASE -> drawCrestVase(g, x, y);
            case PEDESTAL -> drawPedestal(g, x, y, tick);
            case WALL_ITEM -> drawWallItem(g, x, y);
            case PORTRAIT -> drawPortrait(g, x, y, tick);
            case FAMILY_PORTRAIT -> drawFamilyPortrait(g, x, y);
            case STASH -> drawStash(g, x, y, tick);
            case DRESS_OPTION -> drawDressOption(g, x, y);
            case MANNEQUIN -> drawMannequin(g, x, y);
            case DRESS_PORTRAIT -> big(g, x, y, 1.5, () -> drawDressPortrait(g, fx, fy));
            case GIRL_RACK -> drawGirlRack(g, x, y);
            case GIRL_MANNEQUIN -> drawGirlMannequin(g, x, y, tick);
            case GIRLS_PORTRAIT -> big(g, x, y, 1.5, () -> drawGirlsPortrait(g, fx, fy));
            case TABLE_ITEM -> drawTableItem(g, x, y);
            case PLACE -> drawPlace(g, x, y);
            case ANNA -> drawAnna(g, x, y, tick);
            case MIRROR -> big(g, x, y, 1.6, () -> drawMirror(g, fx, fy, tick));
            case FEAST_DISH -> drawFeastDish(g, x, y, tick);
            case HALL_TABLE -> drawHallTable(g, x, y, tick);
            case DINER -> drawDiner(g, x, y, tick);
            case FREE_CHAIR -> drawFreeChair(g, x, y, tick);
            case BATH_ITEM -> drawBathItem(g, x, y, tick);
            case HEIGHT_MARK -> drawHeightMark(g, x, y);
        }
        g.setComposite(old);
    }

    // человечек спереди: cx — центр, fy — линия ног, s — масштаб
    private static void person(Graphics2D g, int cx, int fy, Color hair, Color top, Color bottom,
                               boolean dress, double s, boolean bun) {
        AffineTransform old = g.getTransform();
        g.translate(cx, fy);
        g.scale(s, s);
        g.setColor(new Color(0, 0, 0, 70));
        g.fillOval(-15, -4, 30, 8);
        g.setColor(bottom);                                        // ноги
        g.fillRect(-8, -14, 6, 14);
        g.fillRect(2, -14, 6, 14);
        g.setColor(top);                                           // туловище / платье
        if (dress) g.fillPolygon(new int[]{-9, 9, 15, -15}, new int[]{-32, -32, -8, -8}, 4);
        else g.fillRect(-10, -34, 20, 22);
        g.fillRect(-14, -32, 4, 16);                               // руки
        g.fillRect(10, -32, 4, 16);
        g.setColor(SKIN);
        g.fillRect(-14, -17, 4, 4);
        g.fillRect(10, -17, 4, 4);
        g.fillRect(-7, -48, 14, 15);                               // голова
        g.setColor(hair);
        g.fillRect(-8, -50, 16, 7);
        g.fillRect(-8, -50, 3, 15);
        g.fillRect(5, -50, 3, 15);
        if (bun) g.fillOval(-5, -57, 10, 8);
        g.setColor(Color.BLACK);
        g.fillRect(-4, -41, 2, 2);
        g.fillRect(2, -41, 2, 2);
        g.setTransform(old);
    }

    private static void shadow(Graphics2D g, int x, int y, int w) {
        g.setColor(new Color(0, 0, 0, 60));
        g.fillOval(x + (T - w) / 2, y + 42, w, 6);
    }

    // ---------- музей / наказания ----------
    private void drawPainting(Graphics2D g, int x, int y) {
        g.setColor(new Color(0, 0, 0, 50));
        g.fillRect(x + 6, y + 4, 40, 52);
        g.setColor(new Color(196, 154, 52));                       // золочёная рама
        g.fillRect(x + 2, y, 44, 52);
        g.setColor(new Color(120, 90, 30));
        g.fillRect(x + 6, y + 4, 36, 44);
        int v = col % 3;
        g.setColor(v == 0 ? new Color(110, 150, 200) : v == 1 ? new Color(206, 150, 120) : new Color(96, 112, 96));
        g.fillRect(x + 8, y + 6, 32, 40);
        g.setColor(new Color(58, 88, 60));
        g.fillPolygon(new int[]{x + 8, x + 24, x + 40}, new int[]{y + 46, y + 26, y + 46}, 3);
        g.setColor(new Color(250, 232, 150));
        g.fillOval(x + 28, y + 10, 8, 8);
    }

    private void drawWhips(Graphics2D g, int x, int y) {
        shadow(g, x, y, 44);
        g.setColor(new Color(90, 60, 36));                         // стойка
        g.fillRect(x + 4, y + 4, 5, 44);
        g.fillRect(x + 39, y + 4, 5, 44);
        g.fillRect(x + 2, y + 2, 44, 6);
        Stroke old = g.getStroke();
        g.setStroke(new BasicStroke(2f));
        for (int i = 0; i < 3; i++) {                              // плети
            int hx = x + 16 + i * 8;
            g.setColor(new Color(112, 72, 40));
            g.fillRect(hx - 1, y + 8, 4, 10);
            g.setColor(new Color(36, 26, 22));
            g.drawLine(hx + 1, y + 18, hx + 1 + (i % 2 == 0 ? -4 : 4), y + 42);
        }
        g.setStroke(old);
        g.setColor(new Color(150, 154, 168));                      // цепь
        for (int k = 0; k < 5; k++) g.fillRect(x + 11 + (k % 2) * 2, y + 8 + k * 7, 3, 5);
    }

    private void drawPillory(Graphics2D g, int x, int y) {
        shadow(g, x, y, 46);
        g.setColor(new Color(96, 64, 38));
        g.fillRect(x + 6, y + 8, 6, 40);                           // столбы
        g.fillRect(x + 36, y + 8, 6, 40);
        g.fillRect(x + 2, y + 44, 44, 4);
        g.setColor(new Color(130, 90, 52));
        g.fillRect(x + 4, y + 18, 40, 6);                          // доски с отверстиями
        g.fillRect(x + 4, y + 26, 40, 6);
        g.setColor(new Color(24, 16, 12));
        g.fillOval(x + 12, y + 22, 8, 6);
        g.fillOval(x + 28, y + 22, 8, 6);
    }

    // ---------- приют ----------
    private void drawToys(Graphics2D g, int x, int y) {
        shadow(g, x, y, 40);
        toyType(g, x, y, col % 3);
    }

    // игрушка по типу: 0 — кубики, 1 — мишка, 2 — мячик и юла
    private static void toyType(Graphics2D g, int x, int y, int v) {
        if (v == 0) {                                              // кубики
            g.setColor(new Color(210, 70, 70));
            g.fillRect(x + 6, y + 30, 16, 16);
            g.setColor(new Color(70, 120, 210));
            g.fillRect(x + 24, y + 30, 16, 16);
            g.setColor(new Color(240, 200, 60));
            g.fillRect(x + 15, y + 15, 16, 15);
            g.setColor(new Color(255, 255, 255, 160));
            g.fillRect(x + 10, y + 34, 6, 3);
            g.fillRect(x + 28, y + 34, 6, 3);
        } else if (v == 1) {                                       // мишка
            g.setColor(new Color(150, 100, 60));
            g.fillOval(x + 12, y + 24, 24, 22);
            g.fillOval(x + 14, y + 8, 20, 18);
            g.fillOval(x + 11, y + 6, 8, 8);
            g.fillOval(x + 29, y + 6, 8, 8);
            g.setColor(new Color(214, 170, 120));
            g.fillOval(x + 19, y + 16, 10, 8);
            g.setColor(Color.BLACK);
            g.fillRect(x + 19, y + 13, 2, 2);
            g.fillRect(x + 27, y + 13, 2, 2);
            g.fillRect(x + 23, y + 18, 3, 2);
        } else {                                                   // мячик и юла
            g.setColor(new Color(220, 60, 70));
            g.fillOval(x + 6, y + 26, 20, 20);
            g.setColor(Color.WHITE);
            g.fillRect(x + 8, y + 34, 16, 4);
            g.setColor(new Color(80, 160, 220));
            g.fillPolygon(new int[]{x + 28, x + 42, x + 35}, new int[]{y + 30, y + 30, y + 46}, 3);
            g.setColor(new Color(240, 200, 60));
            g.fillRect(x + 34, y + 24, 2, 7);
        }
    }

    // иконка игрушки с масштабом (на столе, на рисунке, в руках)
    public static void toyIcon(Graphics2D g, int x, int y, int type, double sc) {
        AffineTransform old = g.getTransform();
        g.translate(x, y);
        g.scale(sc, sc);
        toyType(g, 0, 0, type);
        g.setTransform(old);
    }

    private void drawPlaque(Graphics2D g, int x, int y) {
        if (text.isEmpty()) return;                                // пустая табличка = невидимый запуск сценки
        String[] lines = text.split("\n");
        Font old = g.getFont();
        g.setFont(new Font("Monospaced", Font.BOLD, 14));
        FontMetrics fm = g.getFontMetrics();
        int w = 0;
        for (String s : lines) w = Math.max(w, fm.stringWidth(s));
        w += 28;
        int h = lines.length * 20 + 16;
        int px = x + T / 2 - w / 2, py = y;
        g.setColor(new Color(90, 90, 100));                        // цепи
        g.fillRect(px + 10, py - 22, 3, 22);
        g.fillRect(px + w - 13, py - 22, 3, 22);
        g.setColor(new Color(0, 0, 0, 70));
        g.fillRect(px + 4, py + 4, w, h);
        g.setColor(new Color(110, 76, 40));
        g.fillRect(px, py, w, h);
        g.setColor(new Color(240, 200, 60));
        g.drawRect(px + 3, py + 3, w - 7, h - 7);
        g.setColor(new Color(250, 240, 215));
        for (int i = 0; i < lines.length; i++) g.drawString(lines[i], px + 14, py + 26 + i * 20);
        g.setFont(old);
    }

    // ---------- кабинет ----------
    private void drawDesk(Graphics2D g, int x, int y, int tick) {
        g.setColor(new Color(0, 0, 0, 60));
        g.fillOval(x - 6, y + 42, 60, 6);
        g.setColor(new Color(96, 62, 34));
        g.fillRect(x - 2, y + 30, 5, 18);
        g.fillRect(x + 45, y + 30, 5, 18);
        g.setColor(new Color(130, 86, 46));
        g.fillRect(x - 6, y + 26, 60, 6);
        g.setColor(new Color(170, 120, 70));
        g.fillRect(x - 6, y + 26, 60, 2);
        g.setColor(new Color(236, 232, 220));                      // бумаги
        g.fillRect(x + 2, y + 20, 14, 6);
        g.fillRect(x + 18, y + 22, 10, 4);
        g.setColor(new Color(60, 60, 70));                         // лампа
        g.fillRect(x + 38, y + 22, 10, 4);
        g.fillRect(x + 42, y + 12, 2, 10);
        g.setColor(new Color(250, 214, 110));
        g.fillPolygon(new int[]{x + 36, x + 50, x + 46, x + 40}, new int[]{y + 14, y + 14, y + 8, y + 8}, 4);
    }

    private void drawCabinet(Graphics2D g, int x, int y) {
        shadow(g, x, y, 40);
        g.setColor(new Color(100, 108, 120));
        g.fillRect(x + 6, y + 4, 36, 44);
        g.setColor(new Color(136, 146, 160));
        g.fillRect(x + 6, y + 4, 36, 4);
        g.setColor(new Color(70, 76, 88));
        for (int i = 0; i < 3; i++) {
            g.fillRect(x + 9, y + 12 + i * 12, 30, 2);
            g.fillRect(x + 20, y + 17 + i * 12, 8, 3);
        }
    }

    // ---------- похоть ----------
    private void drawClothes(Graphics2D g, int x, int y) {
        shadow(g, x, y, 44);
        g.setColor(new Color(110, 110, 124));
        g.fillRect(x + 4, y + 4, 3, 44);
        g.fillRect(x + 41, y + 4, 3, 44);
        g.fillRect(x + 2, y + 4, 44, 3);
        Color[] cs = {new Color(190, 40, 70), new Color(30, 24, 40), new Color(220, 120, 170), new Color(120, 40, 120)};
        for (int i = 0; i < 4; i++) {
            int gx = x + 9 + i * 8;
            g.setColor(cs[(i + col) % 4]);
            g.fillPolygon(new int[]{gx, gx + 8, gx + 10, gx - 2}, new int[]{y + 8, y + 8, y + 34, y + 34}, 4);
            g.setColor(new Color(250, 230, 236, 170));
            g.fillRect(gx - 1, y + 31, 10, 3);
        }
    }

    // ---------- чревоугодие ----------
    private void drawBuffet(Graphics2D g, int x, int y, int tick) {
        shadow(g, x, y, 56);
        g.setColor(new Color(70, 52, 40));
        g.fillRect(x + 4, y + 40, 4, 8);
        g.fillRect(x + 40, y + 40, 4, 8);
        g.setColor(new Color(240, 238, 230));                      // скатерть
        g.fillRect(x - 4, y + 30, 56, 12);
        g.setColor(new Color(200, 198, 190));
        for (int i = 0; i < 6; i++) g.fillRect(x - 4 + i * 10, y + 40, 6, 4);
        g.setColor(new Color(236, 214, 120));                      // подсвечник
        g.fillRect(x + 40, y + 16, 3, 14);
        g.setColor(new Color(255, 190, 60));
        g.fillOval(x + 39, y + 10 - (tick / 8) % 2, 5, 7);
    }

    private void drawCanteen(Graphics2D g, int x, int y) {
        shadow(g, x, y, 60);
        g.setColor(new Color(74, 52, 34));
        g.fillRect(x - 6, y + 30, 4, 18);
        g.fillRect(x + 50, y + 30, 4, 18);
        g.setColor(new Color(104, 74, 46));
        g.fillRect(x - 10, y + 28, 68, 6);
        g.setColor(new Color(84, 60, 38));                         // скамья
        g.fillRect(x - 2, y + 40, 52, 4);
        g.setColor(new Color(110, 108, 100));                      // миски
        g.fillOval(x + 2, y + 24, 12, 6);
        g.fillOval(x + 30, y + 24, 12, 6);
    }

    private void drawTrash(Graphics2D g, int x, int y, int tick) {
        shadow(g, x, y, 48);
        g.setColor(new Color(44, 48, 40));
        g.fillPolygon(new int[]{x, x + 10, x + 22, x + 34, x + 48}, new int[]{y + 47, y + 30, y + 20, y + 28, y + 47}, 5);
        g.setColor(new Color(76, 68, 48));
        g.fillRect(x + 8, y + 36, 12, 6);
        g.setColor(new Color(100, 102, 84));
        g.fillRect(x + 26, y + 30, 8, 10);
        g.setColor(new Color(214, 206, 180));                      // кости
        g.fillRect(x + 30, y + 40, 10, 2);
        g.fillRect(x + 14, y + 28, 6, 2);
        g.setColor(Color.BLACK);                                   // муха
        int fx = x + 24 + (int) (Math.cos(tick * 0.2) * 14), fy = y + 12 + (int) (Math.sin(tick * 0.31) * 6);
        g.fillRect(fx, fy, 2, 2);
    }

    // ---------- лень ----------
    private void drawBath(Graphics2D g, int x, int y, int tick) {
        g.setColor(new Color(0, 0, 0, 60));
        g.fillOval(x - 8, y + 42, 64, 6);
        g.setColor(new Color(176, 140, 60));
        g.fillRect(x + 2, y + 38, 5, 10);
        g.fillRect(x + 41, y + 38, 5, 10);
        g.setColor(new Color(226, 224, 214));
        g.fillRect(x - 4, y + 22, 56, 18);
        g.setColor(new Color(244, 242, 234));
        g.fillRect(x - 6, y + 18, 60, 6);
        int lv = Math.max(0, Math.min(4, placed));                 // 0 — чистая вода, 4 — чёрная
        g.setColor(Theme.mix(new Color(130, 176, 196), new Color(14, 16, 24), lv / 4.0));   // вода
        g.fillRect(x - 2, y + 22, 52, 5);
        g.setColor(new Color(255, 255, 255, 180 - lv * 40));
        for (int i = 0; i < 3; i++) {
            int by = y + 16 - (tick / 4 + i * 7) % 12;
            g.fillOval(x + 8 + i * 14, by, 6, 6);
        }
    }

    private void drawRag(Graphics2D g, int x, int y) {
        g.setColor(new Color(50, 60, 70, 140));                    // сырость
        g.fillOval(x - 2, y + 41, 52, 7);
        g.setColor(new Color(100, 92, 84));
        g.fillPolygon(new int[]{x + 4, x + 12, x + 26, x + 40, x + 46, x + 30, x + 14},
                new int[]{y + 45, y + 36, y + 34, y + 38, y + 45, y + 47, y + 47}, 7);
        g.setColor(new Color(72, 66, 60));
        g.fillRect(x + 14, y + 38, 14, 2);
        g.fillRect(x + 28, y + 42, 10, 2);
        g.setColor(new Color(120, 106, 90));
        g.fillRect(x + 20, y + 36, 6, 3);
    }

    private void drawDoll(Graphics2D g, int x, int y) {
        shadow(g, x, y, 30);
        g.setColor(new Color(140, 96, 70));
        g.fillRect(x + 16, y + 30, 16, 14);
        g.fillRect(x + 10, y + 32, 6, 4);
        g.fillRect(x + 32, y + 32, 6, 4);
        g.setColor(new Color(214, 190, 150));
        g.fillOval(x + 14, y + 16, 20, 18);
        g.setColor(new Color(170, 80, 50));
        g.fillRect(x + 16, y + 14, 16, 4);
        g.setColor(Color.BLACK);
        g.fillRect(x + 19, y + 23, 3, 3);
        g.fillRect(x + 27, y + 23, 3, 3);
        g.fillRect(x + 21, y + 29, 6, 1);
    }

    // кровать, на которой свернулась кошка
    private void drawBedWithCat(Graphics2D g, int x, int y, int tick) {
        g.setColor(new Color(0, 0, 0, 70));
        g.fillOval(x + 6, y + 40, 36, 8);
        g.setColor(new Color(110, 70, 90));                        // рама и матрас
        g.fillRect(x + 2, y + 34, 44, 12);
        g.setColor(new Color(200, 170, 200));
        g.fillRect(x + 4, y + 30, 40, 6);
        g.setColor(new Color(236, 236, 242));                      // подушка
        g.fillRect(x + 4, y + 26, 12, 6);
        if (bedCat == null) bedCat = new Cat(col, row);
        bedCat.draw(g, tick);
    }

    // ---------- гордыня ----------
    private void drawDress(Graphics2D g, int x, int y, int tick) {
        Color[] pal = {new Color(130, 44, 76), new Color(44, 76, 140), new Color(34, 106, 86),
                new Color(156, 116, 44), new Color(116, 64, 146)};
        Color c = pal[(col / 2) % pal.length];
        g.setColor(new Color(0, 0, 0, 50));
        g.fillOval(x + 2, y + 42, 44, 6);
        g.setColor(new Color(96, 74, 52));                         // манекен
        g.fillRect(x + 23, y - 12, 3, 58);
        g.fillRect(x + 12, y + 44, 24, 4);
        g.setColor(new Color(200, 170, 130));
        g.fillOval(x + 19, y - 28, 10, 14);
        g.setColor(c);                                             // корсаж и юбка
        g.fillPolygon(new int[]{x + 17, x + 31, x + 29, x + 19}, new int[]{y - 12, y - 12, y + 8, y + 8}, 4);
        int sw = (int) (Math.sin(tick * 0.04 + col) * 2);
        g.fillPolygon(new int[]{x + 19, x + 29, x + 44 + sw, x + 4 + sw}, new int[]{y + 8, y + 8, y + 44, y + 44}, 4);
        g.setColor(Theme.mix(c, Color.WHITE, 0.35));
        g.fillRect(x + 22, y - 10, 4, 18);
        g.fillRect(x + 6 + sw, y + 40, 36, 4);
        g.setColor(new Color(240, 200, 60));
        g.fillRect(x + 18, y + 6, 12, 3);
    }

    // воспоминание: отец, мать и девочка — полупрозрачные, как призраки прошлого
    private void drawFamily(Graphics2D g, int x, int y, int tick) {
        float a = (float) (0.55 + 0.12 * Math.sin(tick * 0.05 + col));
        Composite old = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, a));
        g.setColor(new Color(255, 236, 170, 80));
        g.fillOval(x - 30, y - 14, 130, 70);
        person(g, x - 6, y + 46, new Color(70, 50, 36), new Color(70, 80, 110), new Color(40, 40, 52), false, 1.05, false);
        person(g, x + 30, y + 46, new Color(110, 70, 44), new Color(160, 80, 100), new Color(110, 60, 70), true, 1.0, false);
        person(g, x + 62, y + 46, new Color(245, 202, 3), new Color(240, 200, 220), new Color(200, 160, 180), true, 0.7, false);
        g.setComposite(old);
    }

    // ---------- Алчность: загадки приюта ----------
    // рисует предмет крупнее (от середины низа клетки), чтобы мелкие детали — печати, рисунки — были видны
    private static void big(Graphics2D g, int x, int y, double sc, Runnable draw) {
        AffineTransform old = g.getTransform();
        g.translate(x + T / 2.0, y + T);
        g.scale(sc, sc);
        g.translate(-(x + T / 2.0), -(y + T));
        draw.run();
        g.setTransform(old);
    }

    // кровать ребёнка: над ней висит его рисунок — именно эту игрушку он хочет
    private void drawKidBed(Graphics2D g, int x, int y) {
        shadow(g, x, y, 58);
        Color[] blankets = {new Color(70, 110, 170), new Color(170, 80, 110), new Color(90, 150, 90)};
        Color bl = blankets[Math.floorMod(want, 3)];
        g.setColor(new Color(96, 62, 34));                         // изголовье и ножки
        g.fillRect(x - 4, y + 20, 5, 28);
        g.fillRect(x + 47, y + 30, 5, 18);
        g.setColor(new Color(130, 86, 46));                        // рама
        g.fillRect(x - 4, y + 32, 56, 6);
        g.setColor(new Color(225, 225, 215));                      // подушка
        g.fillRect(x, y + 25, 14, 8);
        g.setColor(solved ? bl : Theme.mix(bl, Color.GRAY, 0.45)); // одеяло
        g.fillRect(x + 12, y + 27, 36, 7);
        if (solved) {                                              // ребёнок улыбается, игрушка рядом
            g.setColor(SKIN);
            g.fillOval(x + 1, y + 15, 12, 12);
            g.setColor(new Color(60, 40, 30));
            g.fillRect(x + 4, y + 19, 2, 2);
            g.fillRect(x + 9, y + 19, 2, 2);
            g.fillRect(x + 5, y + 23, 5, 1);
            toyIcon(g, x + 24, y + 6, want, 0.55);
        }
        // рисунок ребёнка на стене + имя
        int px = x + 4, py = y - 66;
        g.setColor(new Color(0, 0, 0, 60));
        g.fillRect(px + 3, py + 3, 42, 38);
        g.setColor(new Color(240, 232, 200));
        g.fillRect(px, py, 42, 38);
        g.setColor(new Color(190, 170, 120));
        g.drawRect(px, py, 41, 37);
        toyIcon(g, px + 4, py + 2, want, 0.72);
        g.setColor(new Color(200, 60, 60));                        // кнопка
        g.fillRect(px + 19, py - 2, 4, 4);
        Font old = g.getFont();
        g.setFont(new Font("Monospaced", Font.BOLD, 13));
        g.setColor(Color.BLACK);
        g.drawString(label, px + 1, py - 5);
        g.setColor(new Color(250, 236, 190));
        g.drawString(label, px, py - 6);
        g.setFont(old);
    }

    // стол с игрушками и карточками с именами детей
    private void drawToyTable(Graphics2D g, int x, int y) {
        g.setColor(new Color(0, 0, 0, 60));
        g.fillOval(x - 8, y + 42, 64, 6);
        g.setColor(new Color(96, 62, 34));
        g.fillRect(x - 2, y + 30, 5, 18);
        g.fillRect(x + 45, y + 30, 5, 18);
        g.setColor(new Color(130, 86, 46));
        g.fillRect(x - 6, y + 26, 60, 6);
        g.setColor(new Color(170, 120, 70));
        g.fillRect(x - 6, y + 26, 60, 2);
        for (int t = 0; t < 3; t++) if ((toyMask >> t & 1) != 0) toyIcon(g, x - 4 + t * 19, y + 5, t, 0.45);
        String[] names = {"ТИМ", "МИЛА", "ЛЕВ"};                   // карточки с именами на крае стола
        Font old = g.getFont();
        g.setFont(new Font("Monospaced", Font.BOLD, 8));
        for (int i = 0; i < 3; i++) {
            g.setColor(new Color(240, 232, 200));
            g.fillRect(x - 4 + i * 19, y + 34, 18, 10);
            g.setColor(new Color(60, 40, 30));
            g.drawString(names[i], x - 3 + i * 19, y + 42);
        }
        g.setFont(old);
    }

    // документ с печатью и монетой на маленьком столе; у подделки лучей меньше, чем у настоящей (7)
    private void drawDoc(Graphics2D g, int x, int y) {
        g.setColor(new Color(0, 0, 0, 60));
        g.fillOval(x - 4, y + 42, 56, 6);
        g.setColor(new Color(96, 62, 34));
        g.fillRect(x - 2, y + 30, 5, 18);
        g.fillRect(x + 45, y + 30, 5, 18);
        g.setColor(new Color(130, 86, 46));
        g.fillRect(x - 4, y + 28, 56, 5);
        g.setColor(new Color(170, 120, 70));
        g.fillRect(x - 4, y + 28, 56, 2);
        if (solved && forged) {                                    // порванная бумага: две половинки
            g.setColor(new Color(236, 232, 220));
            g.fillPolygon(new int[]{x + 2, x + 20, x + 17, x + 2}, new int[]{y + 16, y + 20, y + 28, y + 28}, 4);
            g.fillPolygon(new int[]{x + 24, x + 42, x + 42, x + 21}, new int[]{y + 21, y + 14, y + 28, y + 28}, 4);
            return;
        }
        g.setColor(new Color(236, 232, 220));                      // бумага
        g.fillRect(x + 2, y + 8, 30, 20);
        g.setColor(new Color(150, 146, 136));
        for (int i = 0; i < 3; i++) g.fillRect(x + 5, y + 11 + i * 4, 16, 1);
        // печать
        int cx = x + 25, cy = y + 20, rays = forged ? 5 : 7;
        g.setColor(forged ? new Color(214, 120, 60) : new Color(170, 30, 40));
        for (int i = 0; i < rays; i++) {
            double a = -Math.PI / 2 + i * 2 * Math.PI / rays;
            g.fillRect(cx + (int) Math.round(Math.cos(a) * 8) - 1, cy + (int) Math.round(Math.sin(a) * 8) - 1, 3, 3);
        }
        g.fillOval(cx - 5, cy - 5, 10, 10);
        g.setColor(forged ? new Color(150, 80, 40) : new Color(110, 14, 24));
        g.fillOval(cx - 2, cy - 2, 4, 4);
        g.setColor(new Color(240, 200, 60));                       // монета
        g.fillOval(x + 36, y + 22, 9, 6);
        g.setColor(new Color(172, 124, 28));
        g.drawOval(x + 36, y + 22, 8, 5);
    }

    // ---------- Гнев: музей, комната наказаний, семья Осмаров ----------
    // герб: щит с эмблемой. 0 — звезда о семи лучах (Осмары), 1 — корона, 2 — башня, 3 — полумесяц
    public static void crestIcon(Graphics2D g, int cx, int cy, int id, double sc) {
        AffineTransform old = g.getTransform();
        g.translate(cx, cy);
        g.scale(sc, sc);
        Color[] field = {new Color(150, 30, 40), new Color(50, 80, 160), new Color(40, 110, 70), new Color(84, 84, 110)};
        Color gold = new Color(240, 200, 60);
        g.setColor(new Color(40, 24, 20));
        g.fillPolygon(new int[]{-14, 14, 14, 0, -14}, new int[]{-16, -16, 6, 18, 6}, 5);
        g.setColor(field[Math.floorMod(id, 4)]);
        g.fillPolygon(new int[]{-12, 12, 12, 0, -12}, new int[]{-14, -14, 5, 15, 5}, 5);
        g.setColor(gold);
        switch (Math.floorMod(id, 4)) {
            case 0 -> {                                            // семиконечная звезда
                int[] xs = new int[14], ys = new int[14];
                for (int i = 0; i < 14; i++) {
                    double a = -Math.PI / 2 + i * Math.PI / 7;
                    double r = i % 2 == 0 ? 10 : 4.5;
                    xs[i] = (int) Math.round(Math.cos(a) * r);
                    ys[i] = (int) Math.round(-2 + Math.sin(a) * r);
                }
                g.fillPolygon(xs, ys, 14);
            }
            case 1 -> g.fillPolygon(new int[]{-8, -8, -4, 0, 4, 8, 8}, new int[]{4, -7, -2, -9, -2, -7, 4}, 7);   // корона
            case 2 -> {                                            // башня
                g.fillRect(-5, -6, 10, 14);
                g.fillRect(-6, -9, 3, 4);
                g.fillRect(-1, -9, 3, 4);
                g.fillRect(4, -9, 3, 4);
                g.setColor(new Color(40, 24, 20));
                g.fillRect(-2, 2, 4, 6);
            }
            default -> {                                           // полумесяц
                g.fillOval(-8, -10, 16, 16);
                g.setColor(field[3]);
                g.fillOval(-3, -12, 14, 16);
            }
        }
        g.setTransform(old);
    }

    private static void vaseShape(Graphics2D g, int x, int y, int crest) {
        g.setColor(new Color(150, 144, 128));                      // горлышко и ободок
        g.fillRect(x + 18, y + 8, 12, 10);
        g.fillRect(x + 15, y + 6, 18, 5);
        g.setColor(new Color(214, 206, 184));                      // фарфоровое тело
        g.fillOval(x + 9, y + 16, 30, 30);
        g.setColor(new Color(255, 255, 255, 80));
        g.fillRect(x + 13, y + 22, 4, 10);
        crestIcon(g, x + 24, y + 31, crest, 0.62);
    }

    private void drawCrestVase(Graphics2D g, int x, int y) {
        shadow(g, x, y, 36);
        vaseShape(g, x, y, want);
    }

    private void drawPedestal(Graphics2D g, int x, int y, int tick) {
        shadow(g, x, y, 44);
        if (solved) {                                              // ваза Осмаров встала на своё место
            g.setColor(new Color(255, 235, 140, 60 + (int) (Math.sin(tick * 0.08) * 20)));
            g.fillOval(x - 8, y - 38, T + 16, 60);
            vaseShape(g, x, y - 32, placed);
        }
        g.setColor(new Color(110, 112, 128));                      // постамент
        g.fillRect(x + 8, y + 18, 32, 28);
        g.setColor(new Color(150, 152, 170));
        g.fillRect(x + 4, y + 12, 40, 7);
        g.fillRect(x + 4, y + 43, 40, 4);
        g.setColor(new Color(76, 78, 92));
        g.fillRect(x + 8, y + 18, 3, 28);
        crestIcon(g, x + 24, y + 31, want, 0.6);                   // табличка с гербом на передней грани
    }

    // предмет на деревянной стойке: ключ, цепь, плеть, дверь (want = какой это предмет)
    private void drawWallItem(Graphics2D g, int x, int y) {
        shadow(g, x, y, 40);
        g.setColor(new Color(90, 60, 36));
        g.fillRect(x + 6, y + 4, 36, 42);
        g.setColor(new Color(130, 90, 52));
        g.fillRect(x + 8, y + 6, 32, 38);
        Stroke old = g.getStroke();
        switch (want) {
            case 0 -> {                                            // ключ
                g.setColor(new Color(240, 200, 60));
                g.fillOval(x + 17, y + 9, 14, 14);
                g.setColor(new Color(130, 90, 52));
                g.fillOval(x + 21, y + 13, 6, 6);
                g.setColor(new Color(240, 200, 60));
                g.fillRect(x + 22, y + 22, 4, 18);
                g.fillRect(x + 26, y + 32, 6, 3);
                g.fillRect(x + 26, y + 37, 5, 3);
            }
            case 1 -> {                                            // цепь
                g.setStroke(new BasicStroke(3f));
                g.setColor(new Color(170, 176, 192));
                for (int i = 0; i < 4; i++) {
                    if (i % 2 == 0) g.drawOval(x + 19, y + 8 + i * 8, 10, 12);
                    else g.drawOval(x + 22, y + 8 + i * 8, 4, 12);
                }
                g.setStroke(old);
            }
            case 2 -> {                                            // плеть
                g.setColor(new Color(112, 72, 40));
                g.fillRect(x + 21, y + 8, 6, 12);
                g.setStroke(new BasicStroke(3f));
                g.setColor(new Color(30, 22, 20));
                g.drawLine(x + 24, y + 20, x + 30, y + 28);
                g.drawLine(x + 30, y + 28, x + 20, y + 34);
                g.drawLine(x + 20, y + 34, x + 28, y + 41);
                g.setStroke(old);
            }
            default -> {                                           // дверь
                g.setColor(new Color(40, 44, 54));
                g.fillRect(x + 14, y + 9, 20, 32);
                g.setColor(new Color(100, 108, 120));
                g.fillRect(x + 16, y + 11, 16, 28);
                g.setColor(new Color(14, 14, 20));
                g.fillRect(x + 19, y + 14, 10, 7);
                g.setColor(new Color(240, 200, 60));
                g.fillRect(x + 28, y + 28, 3, 5);
            }
        }
        badge(g, x + 33, y - 2);
    }

    // номер, под которым предмет/портрет уже выбран в цепочке
    private void badge(Graphics2D g, int bx, int by) {
        if (!solved || placed < 1) return;
        g.setColor(new Color(40, 150, 80));
        g.fillOval(bx, by, 16, 16);
        g.setColor(Color.WHITE);
        Font old = g.getFont();
        g.setFont(new Font("Monospaced", Font.BOLD, 12));
        g.drawString(String.valueOf(placed), bx + 5, by + 12);
        g.setFont(old);
    }

    // портрет на мольберте: want — кто изображён (0 отец, 1 мать, 2 старший сын, 3 Анна)
    private void drawPortrait(Graphics2D g, int x, int y, int tick) {
        shadow(g, x, y, 40);
        g.setColor(new Color(90, 60, 36));                         // мольберт
        g.fillRect(x + 8, y + 36, 4, 12);
        g.fillRect(x + 36, y + 36, 4, 12);
        g.setColor(new Color(196, 154, 52));                       // рама
        g.fillRect(x + 6, y + 2, 36, 42);
        g.setColor(new Color(58, 38, 46));
        g.fillRect(x + 9, y + 5, 30, 36);
        Color[] hair = {new Color(40, 30, 30), new Color(110, 70, 44), new Color(96, 64, 40), new Color(214, 130, 50)};
        Color[] cloth = {new Color(40, 50, 100), new Color(120, 50, 120), new Color(40, 110, 70), new Color(236, 130, 176)};
        int w = Math.floorMod(want, 4);
        g.setColor(cloth[w]);                                      // плечи
        g.fillPolygon(new int[]{x + 11, x + 37, x + 34, x + 14}, new int[]{y + 41, y + 41, y + 30, y + 30}, 4);
        g.setColor(SKIN);                                          // лицо
        g.fillOval(x + 17, y + 12, 14, 17);
        g.setColor(hair[w]);
        g.fillArc(x + 16, y + 9, 16, 12, 0, 180);
        if (w == 1) g.fillOval(x + 20, y + 5, 8, 6);               // пучок
        if (w == 3) {                                              // длинные волосы и бантик
            g.fillRect(x + 14, y + 14, 4, 16);
            g.fillRect(x + 30, y + 14, 4, 16);
            g.setColor(new Color(220, 40, 70));
            g.fillRect(x + 21, y + 8, 6, 3);
        }
        g.setColor(Color.BLACK);
        g.fillRect(x + 20, y + 19, 2, 2);
        g.fillRect(x + 26, y + 19, 2, 2);
        if (w == 0) {                                              // у отца усы
            g.setColor(hair[0]);
            g.fillRect(x + 20, y + 24, 8, 2);
        }
        badge(g, x + 33, y - 8);
        if (reveal) {                                              // подсказка к зависти
            Font old = g.getFont();
            g.setFont(new Font("Monospaced", Font.BOLD, 14));
            String s = "Она получила всё.";
            int sw = g.getFontMetrics().stringWidth(s);
            int pulse = 190 + (int) (Math.sin(tick * 0.1) * 50);
            g.setColor(new Color(0, 0, 0, 180));
            g.fillRect(x + 24 - sw / 2 - 6, y - 40, sw + 12, 22);
            g.setColor(new Color(230, 50, 60, Math.min(255, pulse)));
            g.drawString(s, x + 24 - sw / 2, y - 24);
            g.setFont(old);
        }
    }

    // большой семейный портрет: в центре — Анна, по краям родители и старший брат
    private void drawFamilyPortrait(Graphics2D g, int x, int y) {
        g.setColor(new Color(0, 0, 0, 60));
        g.fillRect(x - 60, y + 6, 180, 104);
        g.setColor(new Color(196, 154, 52));                       // золочёная рама
        g.fillRect(x - 66, y, 180, 104);
        g.setColor(new Color(120, 90, 30));
        g.fillRect(x - 60, y + 6, 168, 92);
        g.setColor(new Color(74, 34, 44));                         // бархатный фон
        g.fillRect(x - 56, y + 10, 160, 84);
        int fy = y + 92;
        person(g, x - 36, fy, new Color(40, 30, 30), new Color(40, 50, 100), new Color(30, 30, 40), false, 1.0, false);          // отец
        person(g, x + 84, fy, new Color(110, 70, 44), new Color(120, 50, 120), new Color(90, 40, 90), true, 1.0, true);         // мать
        person(g, x - 4, fy, new Color(96, 64, 40), new Color(40, 110, 70), new Color(30, 40, 40), false, 0.85, false);          // старший сын
        person(g, x + 24, fy, new Color(214, 130, 50), new Color(236, 130, 176), new Color(200, 100, 150), true, 1.0, false);   // Анна в центре
        g.setColor(new Color(255, 220, 120, 70));                  // её чуть подсвечивают
        g.fillOval(x + 4, y + 6, 40, 20);
    }

    // тайник: каменная ниша; открывается, когда портреты расставлены верно
    private void drawStash(Graphics2D g, int x, int y, int tick) {
        shadow(g, x, y, 44);
        g.setColor(new Color(60, 58, 70));
        g.fillRect(x + 4, y + 10, 40, 36);
        g.setColor(new Color(90, 88, 104));
        g.fillRect(x + 4, y + 10, 40, 4);
        g.setColor(new Color(20, 16, 24));
        g.fillRect(x + 10, y + 18, 28, 26);
        if (!solved) {                                             // закрыто плитой с замком
            g.setColor(new Color(112, 114, 130));
            g.fillRect(x + 10, y + 18, 28, 26);
            g.setColor(new Color(240, 200, 60));
            g.fillRect(x + 20, y + 26, 8, 8);
            g.setColor(new Color(40, 30, 20));
            g.fillRect(x + 23, y + 29, 2, 4);
        } else {                                                   // открыто: внутри что-то светится
            g.setColor(new Color(255, 235, 140, 90 + (int) (Math.sin(tick * 0.1) * 30)));
            g.fillOval(x + 8, y + 16, 32, 28);
            g.setColor(new Color(236, 228, 200));
            g.fillRect(x + 16, y + 28, 16, 12);                    // старое письмо
            g.setColor(new Color(150, 130, 100));
            g.fillRect(x + 18, y + 31, 12, 1);
            g.fillRect(x + 18, y + 34, 9, 1);
        }
    }

    // ---------- Похоть: гардеробная ----------
    // платье: cx — центр, top — верх лифа; высота около 50 при масштабе 1. Фасон отличается цветом и деталью.
    public static void dressShape(Graphics2D g, int cx, int top, int id, double sc) {
        AffineTransform old = g.getTransform();
        g.translate(cx, top);
        g.scale(sc, sc);
        int k = Math.floorMod(id, 4);
        Color c = DRESS_COLORS[k];
        Color hi = Theme.mix(c, Color.WHITE, 0.45);
        Color dk = Theme.mix(c, Color.BLACK, 0.35);
        if (k == REVEALING_DRESS) {                                // тонкие бретельки, глубокий вырез, короткая юбка с разрезом
            g.setColor(c);
            g.fillRect(-6, -2, 2, 8);
            g.fillRect(4, -2, 2, 8);
            g.fillPolygon(new int[]{-7, 7, 5, -5}, new int[]{4, 4, 20, 20}, 4);
            g.setColor(SKIN);
            g.fillPolygon(new int[]{-5, 5, 0}, new int[]{4, 4, 16}, 3);
            g.setColor(c);
            g.fillPolygon(new int[]{-5, 5, 11, -11}, new int[]{20, 20, 36, 36}, 4);
            g.setColor(SKIN);
            g.fillPolygon(new int[]{5, 11, 5}, new int[]{21, 36, 36}, 3);
            g.setColor(new Color(220, 60, 90));
            g.fillRect(-11, 34, 17, 2);
        } else {
            g.setColor(c);
            g.fillPolygon(new int[]{-7, 7, 5, -5}, new int[]{0, 0, 20, 20}, 4);             // лиф
            g.fillPolygon(new int[]{-5, 5, 16, -16}, new int[]{20, 20, 50, 50}, 4);          // пышная юбка
            g.setColor(dk);
            g.fillRect(-14, 46, 28, 4);
            if (k == 0) {                                          // белый воротник
                g.setColor(Color.WHITE);
                g.fillRect(-7, 0, 14, 3);
                g.fillRect(-2, 3, 4, 3);
            } else if (k == 1) {                                   // золотой пояс и пуговицы
                g.setColor(new Color(240, 200, 60));
                g.fillRect(-5, 17, 10, 3);
                g.fillRect(-1, 5, 2, 2);
                g.fillRect(-1, 10, 2, 2);
            } else {                                               // кружевные рукава и подол
                g.setColor(hi);
                g.fillRect(-11, 1, 4, 14);
                g.fillRect(7, 1, 4, 14);
                g.fillRect(-14, 46, 28, 2);
            }
        }
        g.setTransform(old);
    }

    // платье на мини-вешалке: E — примерить на манекен
    private void drawDressOption(Graphics2D g, int x, int y) {
        if (solved) return;                                        // правильное платье уже на манекене
        shadow(g, x, y, 34);
        g.setColor(new Color(110, 110, 124));
        g.fillRect(x + 9, y + 2, 2, 46);
        g.fillRect(x + 37, y + 2, 2, 46);
        g.fillRect(x + 9, y + 2, 30, 2);
        g.fillRect(x + 23, y + 4, 2, 6);
        dressShape(g, x + 24, y + 10, want, 0.75);
    }

    // манекен: пока без платья — голый, потом на нём платье с портрета
    private void drawMannequin(Graphics2D g, int x, int y) {
        shadow(g, x, y, 40);
        int cx = x + 24;
        g.setColor(new Color(96, 74, 52));
        g.fillRect(cx - 1, y + 8, 3, 38);
        g.fillRect(cx - 12, y + 44, 24, 4);
        g.setColor(new Color(200, 170, 130));
        g.fillOval(cx - 5, y - 10, 10, 13);
        if (solved && placed >= 0) {
            dressShape(g, cx, y + 3, placed, 0.88);
        } else {
            g.setColor(new Color(190, 160, 120));
            g.fillPolygon(new int[]{cx - 7, cx + 7, cx + 5, cx - 5}, new int[]{y + 3, y + 3, y + 24, y + 24}, 4);
        }
    }

    // старый семейный портрет: отец, девочка и мать в платье, которое нужно найти (want)
    private void drawDressPortrait(Graphics2D g, int x, int y) {
        g.setColor(new Color(0, 0, 0, 60));
        g.fillRect(x - 5, y - 12, 64, 64);
        g.setColor(new Color(196, 154, 52));                       // золочёная рама
        g.fillRect(x - 8, y - 16, 64, 64);
        g.setColor(new Color(120, 90, 30));
        g.fillRect(x - 5, y - 13, 58, 58);
        g.setColor(new Color(104, 82, 66));                        // выцветший, сепия
        g.fillRect(x - 2, y - 10, 52, 52);
        person(g, x + 6, y + 42, new Color(40, 30, 30), new Color(60, 62, 84), new Color(40, 40, 52), false, 0.55, false);   // отец
        person(g, x + 43, y + 42, new Color(245, 202, 3), new Color(240, 200, 220), new Color(200, 160, 180), true, 0.4, false);   // девочка
        int mx = x + 24;                                           // мать — в центре, платье крупно
        g.setColor(new Color(214, 190, 160));
        g.fillOval(mx - 5, y - 3, 10, 12);
        g.setColor(new Color(90, 58, 40));
        g.fillArc(mx - 6, y - 5, 12, 10, 0, 180);
        g.fillOval(mx - 4, y - 8, 8, 5);
        dressShape(g, mx, y + 8, want, 0.62);
        g.setColor(new Color(40, 30, 30));
        g.fillRect(mx - 5, y + 39, 10, 3);
        g.setColor(new Color(255, 255, 255, 18));                  // блик на стекле
        g.fillPolygon(new int[]{x - 2, x + 16, x + 4, x - 2}, new int[]{y - 10, y - 10, y + 10, y + 16}, 4);
    }

    // ---------- Зависть ----------
    // детское платье: cx — центр, top — верх лифа; высота около 38 при масштабе 1
    public static void girlDress(Graphics2D g, int cx, int top, int id, double sc, boolean mark) {
        AffineTransform old = g.getTransform();
        g.translate(cx, top);
        g.scale(sc, sc);
        int k = Math.floorMod(id, 4);
        Color base = switch (k) {
            case 0 -> new Color(124, 124, 130);
            case 1 -> new Color(98, 70, 50);
            case 2 -> new Color(224, 120, 160);
            default -> new Color(242, 228, 192);
        };
        g.setColor(base);
        g.fillRect(-9, 1, 3, 10);                                  // рукава
        g.fillRect(6, 1, 3, 10);
        g.fillPolygon(new int[]{-6, 6, 5, -5}, new int[]{0, 0, 14, 14}, 4);                 // лиф
        g.fillPolygon(new int[]{-5, 5, 12, -12}, new int[]{14, 14, 38, 38}, 4);             // юбка
        g.setColor(Theme.mix(base, Color.BLACK, 0.3));
        g.fillRect(-12, 35, 24, 3);
        switch (k) {
            case 0 -> {                                            // заплатки, потёртость
                g.setColor(new Color(156, 122, 88));
                g.fillRect(-6, 24, 5, 5);
                g.setColor(new Color(92, 96, 110));
                g.fillRect(3, 28, 5, 4);
                g.setColor(new Color(90, 90, 96));
                g.fillRect(-2, 18, 3, 1);
            }
            case 1 -> {                                            // передник
                g.setColor(new Color(214, 204, 172));
                g.fillPolygon(new int[]{-4, 4, 7, -7}, new int[]{4, 4, 36, 36}, 4);
                g.fillRect(-6, 1, 2, 4);
                g.fillRect(4, 1, 2, 4);
            }
            case 2 -> {                                            // кружево и бантик
                g.setColor(Color.WHITE);
                g.fillRect(-12, 33, 24, 2);
                g.fillRect(-6, 0, 12, 2);
                g.setColor(new Color(250, 200, 220));
                g.fillRect(-3, 13, 6, 3);
            }
            default -> {                                           // синяя лента и бант
                g.setColor(new Color(60, 100, 190));
                g.fillRect(-6, 13, 12, 3);
                g.fillRect(-5, 15, 3, 6);
                g.fillRect(2, 15, 3, 6);
                g.fillRect(-9, 1, 3, 2);
                g.fillRect(6, 1, 3, 2);
                if (mark) {                                        // вышитая буква на подоле
                    Font f = g.getFont();
                    g.setFont(new Font("Monospaced", Font.BOLD, 13));
                    g.setColor(new Color(214, 160, 30));
                    g.drawString("Э", -4, 32);
                    g.setFont(f);
                }
            }
        }
        g.setTransform(old);
    }

    // предмет на праздничном столе, в клетке 48x48 (низ предмета — около y = 32)
    public static void tableItem(Graphics2D g, int x, int y, int id, double sc) {
        AffineTransform old = g.getTransform();
        g.translate(x, y);
        g.scale(sc, sc);
        switch (id) {
            case 0 -> {                                            // торт
                g.setColor(new Color(214, 214, 224));
                g.fillOval(6, 26, 36, 8);
                g.setColor(new Color(240, 170, 190));
                g.fillRect(12, 14, 24, 14);
                g.setColor(new Color(252, 246, 240));
                g.fillRect(12, 11, 24, 5);
                g.setColor(new Color(210, 40, 60));
                g.fillOval(21, 6, 6, 6);
                g.setColor(new Color(250, 220, 90));
                g.fillRect(23, 0, 2, 6);
            }
            case 1 -> {                                            // подарок
                g.setColor(new Color(70, 110, 200));
                g.fillRect(12, 14, 24, 18);
                g.setColor(new Color(50, 80, 160));
                g.fillRect(10, 10, 28, 6);
                g.setColor(new Color(240, 200, 60));
                g.fillRect(22, 10, 4, 22);
                g.fillOval(15, 2, 9, 8);
                g.fillOval(24, 2, 9, 8);
            }
            case 2 -> {                                            // цветы в вазе
                g.setColor(new Color(50, 140, 70));
                g.fillRect(19, 12, 2, 12);
                g.fillRect(27, 12, 2, 12);
                g.setColor(new Color(170, 200, 224));
                g.fillRect(17, 22, 14, 11);
                g.setColor(new Color(220, 40, 70));
                g.fillOval(10, 6, 11, 11);
                g.setColor(new Color(246, 130, 176));
                g.fillOval(26, 5, 11, 11);
                g.setColor(new Color(250, 220, 80));
                g.fillOval(18, 1, 11, 11);
            }
            case 3 -> {                                            // сухая корка хлеба
                g.setColor(new Color(150, 104, 58));
                g.fillPolygon(new int[]{9, 30, 40, 34, 12}, new int[]{26, 20, 27, 33, 33}, 5);
                g.setColor(new Color(204, 158, 96));
                g.fillPolygon(new int[]{14, 29, 35, 30, 15}, new int[]{27, 23, 28, 31, 31}, 5);
            }
            case 4 -> {                                            // жестяная кружка
                g.setColor(new Color(130, 134, 146));
                g.fillPolygon(new int[]{15, 33, 31, 17}, new int[]{14, 14, 33, 33}, 4);
                g.setColor(new Color(176, 180, 192));
                g.fillRect(14, 12, 20, 3);
                g.setColor(new Color(130, 134, 146));
                g.fillRect(33, 18, 5, 3);
                g.fillRect(36, 18, 3, 10);
                g.fillRect(31, 26, 6, 3);
                g.setColor(new Color(90, 94, 106));
                g.fillRect(19, 22, 3, 6);
            }
            default -> {                                           // огарок свечи
                g.setColor(new Color(120, 120, 130));
                g.fillOval(10, 29, 28, 6);
                g.setColor(new Color(226, 214, 184));
                g.fillRect(20, 20, 8, 12);
                g.setColor(new Color(60, 50, 40));
                g.fillRect(23, 17, 2, 3);
                g.setColor(new Color(250, 190, 60));
                g.fillOval(22, 11, 4, 7);
            }
        }
        g.setTransform(old);
    }

    // вешалка с одним детским платьем; взятое платье с вешалки пропадает
    private void drawGirlRack(Graphics2D g, int x, int y) {
        if (solved) return;
        shadow(g, x, y, 34);
        g.setColor(new Color(110, 110, 124));
        g.fillRect(x + 9, y + 2, 2, 46);
        g.fillRect(x + 37, y + 2, 2, 46);
        g.fillRect(x + 9, y + 2, 30, 2);
        g.fillRect(x + 23, y + 4, 2, 5);
        girlDress(g, x + 24, y + 9, want, 0.95, false);
    }

    // манекен Эльзы или Анны: пока без платья — голый; потом на нём платье с портрета
    private void drawGirlMannequin(Graphics2D g, int x, int y, int tick) {
        shadow(g, x, y, 40);
        int cx = x + 24;
        if (solved && reveal) {                                    // Эльза поняла, чьё это платье
            g.setColor(new Color(255, 220, 120, 60 + (int) (Math.sin(tick * 0.08) * 20)));
            g.fillOval(cx - 26, y - 6, 52, 56);
        }
        g.setColor(new Color(96, 74, 52));
        g.fillRect(cx - 1, y + 8, 3, 38);
        g.fillRect(cx - 12, y + 44, 24, 4);
        g.setColor(new Color(200, 170, 130));
        g.fillOval(cx - 5, y - 8, 10, 12);
        if (solved && placed >= 0) {
            girlDress(g, cx, y + 4, placed, 0.95, reveal && placed == GIRL_ANNA_DRESS);
        } else {
            g.setColor(new Color(190, 160, 120));
            g.fillPolygon(new int[]{cx - 6, cx + 6, cx + 5, cx - 5}, new int[]{y + 4, y + 4, y + 22, y + 22}, 4);
        }
        String name = want == 0 ? "ЭЛЬЗА" : "АННА";
        Font old = g.getFont();
        g.setFont(new Font("Monospaced", Font.BOLD, 12));
        int w = g.getFontMetrics().stringWidth(name);
        g.setColor(Color.BLACK);
        g.drawString(name, cx - w / 2 + 1, y - 13);
        g.setColor(new Color(250, 236, 190));
        g.drawString(name, cx - w / 2, y - 14);
        g.setFont(old);
    }

    // семейный портрет двух девочек: слева Эльза, справа Анна — как они были одеты
    private void drawGirlsPortrait(Graphics2D g, int x, int y) {
        g.setColor(new Color(0, 0, 0, 60));
        g.fillRect(x - 5, y - 12, 64, 64);
        g.setColor(new Color(196, 154, 52));
        g.fillRect(x - 8, y - 16, 64, 64);
        g.setColor(new Color(120, 90, 30));
        g.fillRect(x - 5, y - 13, 58, 58);
        g.setColor(new Color(104, 82, 66));
        g.fillRect(x - 2, y - 10, 52, 52);
        int[] cxs = {x + 13, x + 35};
        int[] dresses = {GIRL_ELSA_DRESS, GIRL_ANNA_DRESS};
        Color[] hair = {new Color(245, 202, 3), new Color(214, 130, 50)};
        for (int i = 0; i < 2; i++) {
            int cx = cxs[i];
            g.setColor(hair[i]);
            g.fillOval(cx - 7, y - 4, 14, 14);
            g.setColor(new Color(236, 196, 174));
            g.fillOval(cx - 5, y - 1, 10, 11);
            g.setColor(hair[i]);
            g.fillArc(cx - 6, y - 4, 12, 9, 0, 180);
            g.setColor(Color.BLACK);
            g.fillRect(cx - 3, y + 4, 1, 1);
            g.fillRect(cx + 2, y + 4, 1, 1);
            girlDress(g, cx, y + 10, dresses[i], 0.72, false);
            g.setColor(new Color(40, 30, 30));
            g.fillRect(cx - 5, y + 37, 4, 3);
            g.fillRect(cx + 1, y + 37, 4, 3);
        }
        g.setColor(new Color(255, 255, 255, 18));
        g.fillPolygon(new int[]{x - 2, x + 16, x + 4, x - 2}, new int[]{y - 10, y - 10, y + 10, y + 16}, 4);
    }

    // кусок праздничного стола с одним предметом (взятый предмет пропадает, стол остаётся)
    private void drawTableItem(Graphics2D g, int x, int y) {
        g.setColor(new Color(0, 0, 0, 60));
        g.fillOval(x - 2, y + 44, 52, 6);
        g.setColor(new Color(96, 62, 34));
        if ("L".equals(label)) g.fillRect(x + 2, y + 36, 5, 12);
        if ("R".equals(label)) g.fillRect(x + 41, y + 36, 5, 12);
        g.setColor(new Color(236, 232, 226));                      // скатерть
        g.fillRect(x, y + 30, T, 9);
        g.setColor(new Color(214, 208, 200));
        g.fillRect(x, y + 37, T, 3);
        if (!solved) tableItem(g, x + 2, y + 1, want, 0.92);
    }

    // место за столом: у Анны — скатерть с золотой каймой, у Эльзы — пустая тарелка
    private void drawPlace(Graphics2D g, int x, int y) {
        boolean anna = want == 1;
        shadow(g, x, y, 56);
        g.setColor(new Color(96, 62, 34));
        g.fillRect(x - 2, y + 36, 5, 12);
        g.fillRect(x + 45, y + 36, 5, 12);
        g.setColor(anna ? new Color(244, 238, 230) : new Color(110, 80, 50));
        g.fillRect(x - 6, y + 30, 60, 9);
        if (anna) {
            g.setColor(new Color(240, 200, 60));
            g.fillRect(x - 6, y + 37, 60, 2);
        } else {
            g.setColor(new Color(80, 56, 34));
            g.fillRect(x - 6, y + 33, 60, 1);
            g.fillRect(x + 20, y + 30, 1, 9);
        }
        g.setColor(anna ? new Color(230, 230, 240) : new Color(150, 146, 136));   // тарелка
        g.fillOval(x + 8, y + 24, 32, 9);
        g.setColor(anna ? new Color(240, 200, 60) : new Color(110, 106, 98));
        g.drawOval(x + 8, y + 24, 31, 8);
        if (!anna) {
            g.setColor(new Color(60, 56, 50));                     // трещина на тарелке
            g.drawLine(x + 20, y + 27, x + 25, y + 30);
        }
        int n = 0;
        for (int i = 0; i < 6; i++) {
            if ((mask >> i & 1) == 0) continue;
            tableItem(g, x - 2 + n * 16, y + 8, i, 0.5);
            n++;
        }
        String name = anna ? "МЕСТО АННЫ" : "МЕСТО ЭЛЬЗЫ";
        Font old = g.getFont();
        g.setFont(new Font("Monospaced", Font.BOLD, 11));
        int w = g.getFontMetrics().stringWidth(name);
        g.setColor(Color.BLACK);
        g.drawString(name, x + 24 - w / 2 + 1, y + 63);
        g.setColor(new Color(250, 236, 190));
        g.drawString(name, x + 24 - w / 2, y + 62);
        g.setFont(old);
    }

    // Анна стоит спокойно и не выглядит злой
    private void drawAnna(Graphics2D g, int x, int y, int tick) {
        g.setColor(new Color(255, 226, 190, 40));
        g.fillOval(x - 14, y - 12, 76, 64);
        int sway = (int) Math.round(Math.sin(tick * 0.04));
        person(g, x + 24, y + 46 + sway / 2, new Color(214, 130, 50), new Color(236, 130, 176), new Color(200, 100, 150), true, 1.05, false);
        g.setColor(new Color(220, 40, 70));                        // бантик
        g.fillRect(x + 20, y - 8, 8, 3);
        g.setColor(new Color(214, 130, 50));                       // длинные волосы
        g.fillRect(x + 14, y - 4, 3, 22);
        g.fillRect(x + 31, y - 4, 3, 22);
    }

    // зеркало: в нём не Эльза, а Анна. После ответа: простила — отражение теплеет, возненавидела — стекло трескается
    private void drawMirror(Graphics2D g, int x, int y, int tick) {
        g.setColor(new Color(0, 0, 0, 60));
        g.fillOval(x + 4, y + 42, 40, 6);
        g.setColor(new Color(70, 46, 30));                         // рама
        g.fillRect(x + 5, y - 8, 38, 58);
        g.setColor(new Color(196, 154, 52));
        g.fillRect(x + 7, y - 6, 34, 54);
        g.setColor(new Color(150, 188, 204));                      // стекло
        g.fillRect(x + 10, y - 3, 28, 48);
        g.setColor(new Color(200, 226, 236));
        g.fillRect(x + 10, y - 3, 28, 10);
        Composite old = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, placed == 1 ? 0.85f : 0.7f));
        Shape clip = g.getClip();
        g.clipRect(x + 10, y - 3, 28, 48);
        if (placed == 1) person(g, x + 24, y + 44, new Color(245, 202, 3), new Color(198, 46, 64), new Color(30, 24, 40), false, 0.8, false);
        else person(g, x + 24, y + 44, new Color(214, 130, 50), new Color(236, 130, 176), new Color(200, 100, 150), true, 0.8, false);
        g.setClip(clip);
        g.setComposite(old);
        g.setColor(new Color(255, 255, 255, 50));
        g.fillPolygon(new int[]{x + 12, x + 22, x + 12}, new int[]{y - 2, y - 2, y + 24}, 3);
        if (placed == 2) {                                         // трещины
            g.setColor(new Color(30, 20, 30));
            g.drawLine(x + 24, y + 6, x + 14, y + 22);
            g.drawLine(x + 24, y + 6, x + 34, y + 18);
            g.drawLine(x + 24, y + 6, x + 26, y + 36);
        } else if (!solved && (tick / 20) % 4 == 0) {              // изредка по стеклу пробегает блик
            g.setColor(new Color(255, 255, 255, 90));
            g.fillRect(x + 30, y + 4, 3, 30);
        }
    }


    // ---------- Чревоугодие ----------
    // блюдо на фуршете: скатерть остаётся всегда, а само блюдо после касания тает дымком
    private void drawFeastDish(Graphics2D g, int x, int y, int tick) {
        g.setColor(new Color(0, 0, 0, 60));
        g.fillOval(x - 2, y + 44, 52, 6);
        g.setColor(new Color(240, 238, 230));                      // скатерть
        g.fillRect(x, y + 30, T, 12);
        g.setColor(new Color(200, 198, 190));
        for (int i = 0; i < 3; i++) g.fillRect(x + 4 + i * 16, y + 40, 8, 4);

        if (solved) fade++;
        float a = solved ? Math.max(0f, 1f - fade / 80f) : 1f;
        if (solved) {                                              // дымок на месте растаявшего блюда
            for (int i = 0; i < 3; i++) {
                int wy = y + 22 - fade / 2 - i * 7;
                int wx = x + 14 + i * 10 + (int) (Math.sin(fade * 0.15 + i) * 4);
                g.setColor(new Color(235, 235, 242, (int) (110 * Math.max(0f, 1f - fade / 70f))));
                g.fillOval(wx, wy, 8, 8);
            }
        }
        if (a <= 0f) return;

        Composite old = g.getComposite();
        AffineTransform keep = g.getTransform();
        if (a < 1f) g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, a));
        g.translate(0, -fade / 4);                                 // блюдо поднимается и растворяется
        switch (want % 5) {
            case 0 -> {                                            // жаркое на кости
                g.setColor(new Color(150, 84, 40));
                g.fillOval(x + 6, y + 14, 30, 16);
                g.setColor(new Color(196, 124, 62));
                g.fillOval(x + 11, y + 16, 14, 6);
                g.setColor(new Color(236, 230, 210));
                g.fillRect(x + 34, y + 20, 9, 3);
                g.fillOval(x + 41, y + 18, 5, 6);
            }
            case 1 -> {                                            // хлеб
                g.setColor(new Color(190, 130, 60));
                g.fillOval(x + 5, y + 18, 38, 12);
                g.setColor(new Color(150, 96, 40));
                for (int i = 0; i < 3; i++) g.fillRect(x + 14 + i * 8, y + 20, 2, 6);
                g.setColor(new Color(224, 170, 96));
                g.fillOval(x + 10, y + 19, 16, 4);
            }
            case 2 -> {                                            // чаша с фруктами
                g.setColor(new Color(150, 120, 80));
                g.fillOval(x + 7, y + 22, 34, 9);
                g.setColor(new Color(200, 40, 40));
                g.fillOval(x + 11, y + 13, 12, 12);
                g.setColor(new Color(130, 176, 60));
                g.fillOval(x + 23, y + 14, 11, 11);
                g.setColor(new Color(110, 50, 130));
                for (int i = 0; i < 4; i++) g.fillOval(x + 15 + (i % 2) * 5 + i * 3, y + 10 + (i % 2) * 4, 5, 5);
            }
            case 3 -> {                                            // бутылка и бокал вина
                g.setColor(new Color(30, 70, 40));
                g.fillRect(x + 9, y + 10, 9, 20);
                g.fillRect(x + 11, y + 2, 5, 9);
                g.setColor(new Color(220, 190, 90));
                g.fillRect(x + 9, y + 18, 9, 5);
                g.setColor(new Color(210, 216, 232, 200));
                g.fillRect(x + 30, y + 22, 3, 8);
                g.fillRect(x + 26, y + 29, 11, 2);
                g.fillPolygon(new int[]{x + 25, x + 38, x + 35, x + 28}, new int[]{y + 10, y + 10, y + 22, y + 22}, 4);
                g.setColor(new Color(150, 20, 40));
                g.fillPolygon(new int[]{x + 27, x + 36, x + 35, x + 28}, new int[]{y + 15, y + 15, y + 21, y + 21}, 4);
            }
            default -> {                                           // десерт: торт с вишней
                g.setColor(new Color(214, 214, 224));
                g.fillOval(x + 6, y + 25, 36, 8);
                g.setColor(new Color(240, 170, 190));
                g.fillRect(x + 12, y + 14, 24, 14);
                g.setColor(new Color(252, 246, 240));
                g.fillRect(x + 12, y + 11, 24, 5);
                g.setColor(new Color(210, 40, 60));
                g.fillOval(x + 21, y + 6, 6, 6);
            }
        }
        if (!solved && (tick / 12 + want) % 3 == 0) {              // мираж: над блюдом дрожит воздух
            g.setColor(new Color(255, 255, 255, 60));
            g.fillRect(x + 18 + (want % 3) * 5, y + 4, 2, 7);
        }
        g.setTransform(keep);
        g.setComposite(old);
    }

    // длинный стол слуг: иллюзия. Удар мечом заставляет его дрожать и «рябить»
    private void drawHallTable(Graphics2D g, int x, int y, int tick) {
        if (flash > 0) flash--;
        int w = Math.max(1, want) * T;
        int sh = flash > 0 ? (tick % 2 == 0 ? -3 : 3) : 0;
        g.setColor(new Color(0, 0, 0, 60));
        g.fillOval(x - 4, y + 44, w + 8, 6);
        g.setColor(new Color(74, 52, 34));                         // ножки
        for (int cx = 6; cx < w; cx += 3 * T) g.fillRect(x + cx + sh, y + 34, 5, 14);
        g.fillRect(x + w - 11 + sh, y + 34, 5, 14);
        g.setColor(new Color(104, 74, 46));                        // столешница
        g.fillRect(x - 4 + sh, y + 26, w + 8, 8);
        g.setColor(new Color(140, 100, 62));
        g.fillRect(x - 4 + sh, y + 26, w + 8, 2);
        for (int cx = 16; cx < w - 8; cx += T) {                   // миски и кружки
            g.setColor(new Color(112, 110, 102));
            g.fillOval(x + cx + sh, y + 21, 14, 7);
            g.setColor(new Color(170, 168, 160));
            g.fillRect(x + cx + 20 + sh, y + 18, 5, 8);
        }
        // мираж: полупрозрачная рябь, а после ударов — трещины света
        g.setColor(new Color(220, 230, 255, 26));
        for (int i = 0; i < 4; i++) g.fillRect(x + sh, y + 16 + i * 8 + (int) (Math.sin(tick * 0.1 + i) * 2), w, 2);
        if (hp < 3) {
            g.setColor(new Color(255, 255, 255, 120 + (tick / 3 % 2) * 60));
            for (int i = 0; i < (3 - hp) * 3; i++) {
                int cx = (i * 97 + 31) % Math.max(1, w - 20);
                g.fillRect(x + cx, y + 22 + (i % 3) * 5, 3, 12);
                g.fillRect(x + cx, y + 22 + (i % 3) * 5, 14, 2);
            }
        }
    }

    // сидящий за столом: господин, слуга или госпожа. Рядом с Эльзой «вылезают» реплики
    private void drawDiner(Graphics2D g, int x, int y, int tick) {
        int k = Math.floorMod(want, 3);
        int bob = (tick / 40 + col) % 2;
        switch (k) {
            case 0 -> person(g, x + 24, y + 42 + bob, new Color(46, 36, 36), new Color(124, 34, 50), new Color(40, 30, 36), false, 1.0, false);
            case 1 -> person(g, x + 24, y + 42 + bob, new Color(112, 92, 62), new Color(112, 114, 122), new Color(62, 62, 72), false, 1.0, false);
            default -> person(g, x + 24, y + 42 + bob, new Color(92, 60, 44), new Color(110, 60, 140), new Color(80, 44, 100), true, 1.0, true);
        }
        if (k == 0) {                                              // золотые пуговицы господина
            g.setColor(new Color(240, 200, 60));
            g.fillRect(x + 23, y + 14 + bob, 3, 3);
            g.fillRect(x + 23, y + 21 + bob, 3, 3);
        }
        if (!active || solved) return;
        int cyc = tick % 300;                                      // в каждой группе говорит один гость за раз (mask = его очередь 0..2)
        int phase = cyc - mask * 100;
        if (phase < 0 || phase >= 100) return;
        int idx = (tick / 300 + k) % DINER_SAY[k].length;
        String s = DINER_SAY[k][idx];
        float al = Math.min(1f, Math.min(phase / 15f, (100 - phase) / 15f));
        Font old = g.getFont();
        g.setFont(new Font("Monospaced", Font.BOLD, 13));
        int sw = g.getFontMetrics().stringWidth(s);
        int bx = x + 24 - sw / 2, by = y - 30 - (phase / 8);
        g.setColor(new Color(0, 0, 0, (int) (150 * al)));
        g.fillRect(bx - 5, by - 13, sw + 10, 19);
        g.setColor(new Color(236, 90, 96, (int) (255 * al)));
        g.drawString(s, bx, by);
        g.setFont(old);
    }

    // «свободный» стул: приглашает сесть, но это иллюзия — по краям он слегка мерцает
    private void drawFreeChair(Graphics2D g, int x, int y, int tick) {
        int pulse = 36 + (int) (Math.sin(tick * 0.07) * 14);
        g.setColor(new Color(255, 230, 150, pulse));
        g.fillOval(x - 6, y + 4, T + 12, 46);
        g.setColor(new Color(0, 0, 0, 60));
        g.fillOval(x + 6, y + 44, 36, 6);
        g.setColor(new Color(86, 58, 34));
        g.fillRect(x + 12, y + 8, 6, 36);                          // спинка
        g.fillRect(x + 12, y + 30, 28, 6);                         // сиденье
        g.fillRect(x + 12, y + 36, 5, 10);
        g.fillRect(x + 35, y + 36, 5, 10);
        g.setColor(new Color(150, 36, 52));                        // подушка
        g.fillRect(x + 17, y + 27, 22, 4);
        if ((tick / 9) % 5 == 0) {                                 // мерцание: слишком уж идеальный стул
            g.setColor(new Color(255, 255, 255, 90));
            g.fillRect(x + 12, y + 8, 28, 3);
        }
    }

    // ---------- Лень: ванная и её бывшая комната ----------
    // вещь из прошлого рядом с ванной: фотография, ленточка, письмо, башмак (брошенная в воду — исчезает)
    private void drawBathItem(Graphics2D g, int x, int y, int tick) {
        if (solved) return;
        shadow(g, x, y, 30);
        g.setColor(Theme.alpha(new Color(255, 235, 150), 30 + (int) (Math.sin(tick * 0.08 + col) * 10)));
        g.fillOval(x + 4, y + 14, 40, 34);
        switch (Math.floorMod(want, 4)) {
            case 0 -> {                                            // фотография в рамке
                g.setColor(new Color(110, 76, 40));
                g.fillRect(x + 12, y + 20, 24, 26);
                g.setColor(new Color(214, 200, 168));
                g.fillRect(x + 15, y + 23, 18, 20);
                g.setColor(new Color(120, 104, 90));
                g.fillOval(x + 20, y + 26, 8, 8);
                g.fillRect(x + 18, y + 34, 12, 8);
            }
            case 1 -> {                                            // голубая ленточка бантом
                g.setColor(new Color(60, 100, 190));
                g.fillPolygon(new int[]{x + 24, x + 12, x + 12}, new int[]{y + 34, y + 26, y + 42}, 3);
                g.fillPolygon(new int[]{x + 24, x + 36, x + 36}, new int[]{y + 34, y + 26, y + 42}, 3);
                g.fillRect(x + 21, y + 31, 6, 6);
                g.fillRect(x + 18, y + 40, 4, 6);
                g.fillRect(x + 27, y + 40, 4, 6);
            }
            case 2 -> {                                            // письмо с печатью
                g.setColor(new Color(236, 228, 200));
                g.fillRect(x + 10, y + 26, 28, 18);
                g.setColor(new Color(170, 150, 120));
                g.drawLine(x + 10, y + 26, x + 24, y + 36);
                g.drawLine(x + 38, y + 26, x + 24, y + 36);
                g.setColor(new Color(170, 30, 40));
                g.fillOval(x + 21, y + 33, 7, 7);
            }
            default -> {                                           // стоптанный детский башмак
                g.setColor(new Color(96, 66, 44));
                g.fillRect(x + 12, y + 28, 12, 16);
                g.fillRect(x + 12, y + 38, 26, 8);
                g.setColor(new Color(60, 40, 28));
                g.fillRect(x + 12, y + 44, 26, 2);
                g.setColor(new Color(150, 120, 90));
                g.fillRect(x + 14, y + 30, 8, 2);
            }
        }
    }

    // метка роста на дверном косяке: чем выше царапина, тем старше Эльза; после прочтения над ней появляется возраст
    private void drawHeightMark(Graphics2D g, int x, int y) {
        int top = y - 58;
        g.setColor(new Color(0, 0, 0, 50));
        g.fillRect(x + 16, top + 4, 20, 104);
        g.setColor(new Color(92, 62, 36));                         // косяк
        g.fillRect(x + 14, top, 20, 104);
        g.setColor(new Color(130, 90, 52));
        g.fillRect(x + 14, top, 3, 104);
        g.setColor(new Color(60, 40, 24));
        for (int i = 0; i < 12; i++) g.fillRect(x + 28, top + 6 + i * 8, 4, 1);
        int my = y + 38 - Math.max(0, want) * 22;                  // высота царапины
        g.setColor(solved ? new Color(240, 200, 60) : new Color(30, 18, 10));
        g.fillRect(x + 14, my, 20, 3);
        if (!solved) return;
        String s = HMARK_AGES[Math.floorMod(want, HMARK_AGES.length)] + " ЛЕТ";
        Font old = g.getFont();
        g.setFont(new Font("Monospaced", Font.BOLD, 11));
        int w = g.getFontMetrics().stringWidth(s);
        g.setColor(new Color(0, 0, 0, 170));
        g.fillRect(x + 24 - w / 2 - 3, my - 17, w + 6, 14);
        g.setColor(new Color(250, 236, 190));
        g.drawString(s, x + 24 - w / 2, my - 6);
        g.setFont(old);
    }

    private void drawPouch(Graphics2D g, int x, int y, int tick) {
        shadow(g, x, y, 30);
        g.setColor(Theme.alpha(new Color(255, 215, 90), 40 + (int) (Math.sin(tick * 0.08) * 14)));
        g.fillOval(x + 2, y + 14, 44, 36);
        g.setColor(new Color(120, 82, 46));
        g.fillOval(x + 10, y + 24, 28, 22);
        g.setColor(new Color(90, 60, 32));
        g.fillRect(x + 18, y + 20, 12, 6);
        g.setColor(new Color(240, 200, 60));
        g.fillRect(x + 18, y + 25, 12, 2);
        g.fillOval(x + 30, y + 40, 8, 5);
        g.setColor(new Color(250, 236, 140));
        g.fillRect(x + 20, y + 33, 3, 3);
    }
}


// ==================== ВАЗА (испытание «найди свечу») ====================
class Vase {
    public enum State { INTACT, CHECKED, BROKEN }

    private static final Color[] COLORS = {
            new Color(176, 98, 62), new Color(72, 132, 142), new Color(120, 86, 160)
    };

    public final int col, row;
    public State state = State.INTACT;
    public boolean hasCandle;
    private final int style;

    public Vase(int col, int row) {
        this.col = col;
        this.row = row;
        this.style = (col * 7 + row * 3) % COLORS.length;
    }

    public Rectangle box()  { return new Rectangle(col * Room.TILE + 8, row * Room.TILE + 6, 32, 40); }
    public int centerX()    { return col * Room.TILE + Room.TILE / 2; }
    public int centerY()    { return row * Room.TILE + Room.TILE / 2; }

    private static Color darker(Color c) {
        return new Color((int) (c.getRed() * 0.7), (int) (c.getGreen() * 0.7), (int) (c.getBlue() * 0.7));
    }

    public void draw(Graphics2D g, int tick) {
        int x = col * Room.TILE, y = row * Room.TILE;
        Color body = COLORS[style];

        if (state == State.BROKEN) {                              // осколки
            g.setColor(body);
            g.fillPolygon(new int[]{x + 8, x + 18, x + 14}, new int[]{y + 46, y + 46, y + 40}, 3);
            g.fillPolygon(new int[]{x + 22, x + 34, x + 28}, new int[]{y + 46, y + 46, y + 38}, 3);
            g.setColor(darker(body));
            g.fillPolygon(new int[]{x + 34, x + 42, x + 40}, new int[]{y + 46, y + 46, y + 42}, 3);
            return;
        }

        g.setColor(new Color(0, 0, 0, 60));
        g.fillOval(x + 8, y + 42, 32, 6);
        g.setColor(darker(body));                                 // горлышко и ободок
        g.fillRect(x + 18, y + 8, 12, 10);
        g.fillRect(x + 15, y + 6, 18, 5);
        g.setColor(body);                                         // тело
        g.fillOval(x + 9, y + 16, 30, 30);
        g.setColor(new Color(255, 255, 255, 60));
        g.fillRect(x + 14, y + 22, 4, 10);

        if (state == State.CHECKED) {                             // проверена: тёмное горлышко и галочка
            g.setColor(new Color(20, 14, 26));
            g.fillOval(x + 17, y + 6, 14, 4);
            g.setColor(new Color(120, 230, 140));
            Stroke old = g.getStroke();
            g.setStroke(new BasicStroke(3));
            g.drawLine(x + 17, y + 30, x + 22, y + 36);
            g.drawLine(x + 22, y + 36, x + 32, y + 24);
            g.setStroke(old);
        }
    }
}


// ==================== ТОТЕМ ЯРОСТИ ====================
// Тотем ярости: его нужно разбить мечом (L). Три удара.
class Totem {
    private static final int T = Room.TILE;

    public final int col, row;
    public int hp = 3;
    public int flash;                 // вспышка после удара
    public boolean broken;

    public Totem(int col, int row) {
        this.col = col;
        this.row = row;
    }

    public Rectangle box() { return new Rectangle(col * T + 6, row * T + 2, 36, 44); }

    public void hit() {
        if (broken) return;
        hp--;
        flash = 10;
        if (hp <= 0) broken = true;
    }

    public void draw(Graphics2D g, int tick) {
        int x = col * T, y = row * T;
        if (broken) {                                                  // обломки
            g.setColor(new Color(70, 30, 34));
            g.fillPolygon(new int[]{x + 8, x + 22, x + 16}, new int[]{y + 46, y + 46, y + 36}, 3);
            g.fillPolygon(new int[]{x + 24, x + 40, x + 32}, new int[]{y + 46, y + 46, y + 32}, 3);
            g.setColor(new Color(110, 48, 50));
            g.fillRect(x + 16, y + 42, 10, 4);
            return;
        }
        g.setColor(new Color(0, 0, 0, 70));
        g.fillOval(x + 6, y + 40, 36, 8);
        Color stone = flash > 0 ? new Color(255, 190, 170) : new Color(104, 46, 50);
        Color dark = flash > 0 ? new Color(230, 140, 120) : new Color(62, 26, 32);
        g.setColor(dark);
        g.fillRect(x + 8, y + 8, 32, 38);
        g.setColor(stone);
        g.fillRect(x + 10, y + 6, 28, 36);
        g.fillPolygon(new int[]{x + 10, x + 5, x + 17}, new int[]{y + 10, y - 3, y + 6}, 3);      // рога
        g.fillPolygon(new int[]{x + 38, x + 43, x + 31}, new int[]{y + 10, y - 3, y + 6}, 3);
        g.setColor((tick / 10) % 2 == 0 ? new Color(255, 170, 40) : new Color(230, 90, 30));      // глаза
        g.fillRect(x + 15, y + 16, 6, 5);
        g.fillRect(x + 27, y + 16, 6, 5);
        g.setColor(new Color(20, 8, 10));                                                          // пасть
        g.fillRect(x + 16, y + 30, 16, 4);
        g.setColor(new Color(30, 12, 16));                                                         // трещины
        if (hp <= 2) g.fillRect(x + 22, y + 8, 2, 10);
        if (hp <= 1) {
            g.fillRect(x + 14, y + 22, 10, 2);
            g.fillRect(x + 30, y + 24, 2, 12);
        }
    }
}


// ==================== ПРЕДМЕТЫ ДЛЯ СБОРА ====================
// Предметы, которые нужно собрать в испытании: корона, розы, угощение, сферы снов.
class Pickup {
    public enum Kind { CROWN, ROSE, FOOD, ORB }

    private static final int T = Room.TILE;

    public final Kind kind;
    public final int col, row;
    public boolean taken;

    public Pickup(Kind kind, int col, int row) {
        this.kind = kind;
        this.col = col;
        this.row = row;
    }

    public Rectangle box() { return new Rectangle(col * T + 8, row * T + 8, 32, 36); }

    public void draw(Graphics2D g, int tick) {
        if (taken) return;
        int x = col * T, y = row * T + (int) (Math.sin(tick * 0.08 + col) * 4);   // парит вверх-вниз

        Color glow = switch (kind) {
            case CROWN -> new Color(255, 215, 90);
            case ROSE  -> new Color(255, 100, 160);
            case FOOD  -> new Color(255, 180, 90);
            case ORB   -> new Color(120, 180, 255);
        };
        g.setColor(Theme.alpha(glow, 46));
        g.fillOval(x + 2, y + 4, 44, 44);

        switch (kind) {
            case CROWN -> {
                g.setColor(new Color(240, 190, 40));
                g.fillPolygon(new int[]{x + 8, x + 8, x + 16, x + 24, x + 32, x + 40, x + 40},
                        new int[]{y + 34, y + 14, y + 23, y + 10, y + 23, y + 14, y + 34}, 7);
                g.setColor(new Color(190, 140, 20));
                g.fillRect(x + 8, y + 30, 32, 6);
                g.setColor(new Color(220, 50, 70));
                g.fillRect(x + 22, y + 24, 5, 5);
                g.setColor(new Color(80, 160, 255));
                g.fillRect(x + 12, y + 26, 4, 4);
                g.fillRect(x + 32, y + 26, 4, 4);
            }
            case ROSE -> {
                g.setColor(new Color(40, 140, 60));
                g.fillRect(x + 23, y + 26, 3, 18);
                g.fillOval(x + 26, y + 32, 10, 5);
                g.setColor(new Color(200, 30, 90));
                g.fillOval(x + 14, y + 8, 20, 20);
                g.setColor(new Color(240, 80, 140));
                g.fillOval(x + 18, y + 12, 12, 12);
                g.setColor(new Color(150, 20, 70));
                g.fillOval(x + 21, y + 15, 6, 6);
            }
            case FOOD -> {
                if (col % 2 == 0) {                                    // яблоко
                    g.setColor(new Color(200, 40, 40));
                    g.fillOval(x + 10, y + 14, 28, 28);
                    g.setColor(new Color(240, 100, 90));
                    g.fillRect(x + 16, y + 20, 4, 8);
                    g.setColor(new Color(90, 60, 30));
                    g.fillRect(x + 23, y + 8, 3, 8);
                    g.setColor(new Color(60, 170, 70));
                    g.fillOval(x + 26, y + 8, 10, 6);
                } else {                                               // пирог
                    g.setColor(new Color(170, 110, 50));
                    g.fillOval(x + 6, y + 20, 36, 22);
                    g.setColor(new Color(220, 160, 80));
                    g.fillOval(x + 8, y + 16, 32, 20);
                    g.setColor(new Color(250, 240, 220));
                    g.fillOval(x + 16, y + 18, 16, 8);
                    g.setColor(new Color(220, 50, 60));
                    g.fillOval(x + 21, y + 14, 6, 6);
                }
            }
            case ORB -> {
                int r = 14 + (int) (Math.sin(tick * 0.1 + col) * 2);
                g.setColor(new Color(90, 140, 230));
                g.fillOval(x + 24 - r, y + 26 - r, r * 2, r * 2);
                g.setColor(new Color(160, 210, 255));
                g.fillOval(x + 24 - r + 4, y + 26 - r + 4, r * 2 - 10, r * 2 - 10);
                g.setColor(Color.WHITE);
                g.fillOval(x + 24 - r + 7, y + 26 - r + 6, 7, 5);
            }
        }
    }
}