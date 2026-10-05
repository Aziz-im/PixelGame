package game;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Random;

// Катсцена-эпилог «История Эльзы». Запускается из GamePanel через несколько секунд после экрана АД / РАЙ.
// Все кадры рисуются в маленький холст 240x144 и растягиваются в 4 раза без сглаживания — получается настоящий пиксель-арт.
// Управление: ПРОБЕЛ / ENTER / E — дальше (первое нажатие дописывает реплику), ESC — пропустить всё.
public class EndingCutscene {
    private static final int W = Room.VIEW_W, H = Room.VIEW_H;      // размер окна (960x576)
    private static final int LW = 240, LH = 144, SC = 4;            // маленький холст и во сколько раз он увеличивается
    private static final int TYPE = 2;                              // кадров на одну букву реплики
    private static final int START = 20;                            // через сколько кадров после появления кадра начинается текст
    private static final Color GOLD = new Color(240, 200, 60);

    // ===== то, что можно менять =====
    private static final String SPEAKER = "Голос";                  // подпись над репликой
    private static final String BOOK_TITLE = "PATH TO HEAVEN";      // надпись на обложке
    private static final String NOVEL = "NOVEL";                    // акцентная надпись

    private static final Color HAIR = new Color(245, 202, 3);
    private static final Color SKIN = new Color(236, 196, 174);
    private static final Color RAGS = new Color(104, 78, 138);      // лиловое рваное платье (как в главном меню)
    private static final Color DARK = new Color(24, 18, 30);

    private enum Scene { CHILDHOOD, BETRAYAL, CAPTIVE, ANNA, SEBASTIAN, SLUMS, DESTROY, BOOK, CREDITS }

    // title — подпись кадра в верхней полосе, line — реплика голоса (null = без текста), dur — длительность в кадрах
    private record Beat(Scene scene, String title, String line, int dur) {}

    private static Beat b(Scene s, String title, String line) {
        return new Beat(s, title, line, START + line.length() * TYPE + 200);
    }

    // ===== титры (после книги) =====
    private static final String CREDITS_HEAD = "Над игрой работали";
    private static final String[][] CREDITS = {                     // {должность, имя}
            {"Сценарист", "Чынгыз"},
            {"Главный кодер", "Азиз"},
            {"Художница", "Назик"},
            {"Группировщик", "Арзыбек"},
            {"Разнорабочий", "Атай"},
            {"Ментор", "Керемет Эже"},
    };
    private static final float CREDITS_SPEED = 1.0f;                // пикселей экрана за кадр (меньше — медленнее)
    private static final int CREDITS_HEAD_H = 120, CREDITS_GAP = 110;
    private static final int CREDITS_LEN = CREDITS_HEAD_H + CREDITS.length * CREDITS_GAP;   // высота всего текста
    private static final int CREDITS_DUR = (int) ((CREDITS_LEN + H) / CREDITS_SPEED) + 90;

    private static final int DESTROY_FIRST = 6;                      // номер первого бита кадра «Разрушение»

    private static final Beat[] BEATS = {
            b(Scene.CHILDHOOD, "1 · ДЕТСТВО",
                    "Когда-то Эльза жила в любви и достатке. У неё были родители, дом и будущее."),
            b(Scene.BETRAYAL, "2 · ПРЕДАТЕЛЬСТВО",
                    "Но родственники завидовали им. Они подделали доказательства и отняли всё."),
            b(Scene.CAPTIVE, "3 · ПЛЕН",
                    "Эльза осталась в их доме. Её морили голодом, избивали и унижали."),
            b(Scene.ANNA, "4 · АННА",
                    "Анна получала всё то, чего Эльза лишилась. И постепенно Эльза начала её ненавидеть."),
            b(Scene.SEBASTIAN, "5 · СЕБАСТЬЯН",
                    "Но однажды Себастьян помог ей сбежать."),
            b(Scene.SLUMS, "6 · ТРУЩОБЫ",
                    "Свобода не спасла её. Она голодала, пряталась и постоянно боялась, что её найдут."),
            // кадр 7 состоит из трёх реплик подряд на одной и той же картинке
            b(Scene.DESTROY, "7 · РАЗРУШЕНИЕ", "Она мечтала избавиться от страданий.."),
            b(Scene.DESTROY, "7 · РАЗРУШЕНИЕ", "И тут она обрела надежду, иллюзию выхода."),
            b(Scene.DESTROY, "7 · РАЗРУШЕНИЕ", "Но не всем мечтам суждено сбыться…"),
            new Beat(Scene.BOOK, "", null, 600),
            new Beat(Scene.CREDITS, "", null, CREDITS_DUR),
    };

    private static final String[] WHISPERS = {"ты виновата...", "они найдут тебя", "беги", "слабая...",
            "голодная...", "никому не нужна", "не уйдёшь", "это ты..."};

    private final Sins.Ending ending;
    private final BufferedImage img = new BufferedImage(LW, LH, BufferedImage.TYPE_INT_RGB);
    private Graphics2D p;                                           // кисть маленького холста
    private Color eyes = DARK;                                      // цвет глаз у person()
    private int beat, t, st, tick;                                  // t — время реплики, st — время кадра (не сбрасывается между репликами кадра 7)

    public EndingCutscene(Sins.Ending ending) { this.ending = ending; }

    public boolean finished() { return beat >= BEATS.length; }
    public void skip()        { beat = BEATS.length; }

    public void update() {
        if (finished()) return;
        tick++;
        st++;
        if (++t >= BEATS[beat].dur()) next();
    }

    // кнопка «дальше»: сначала дописывает реплику, потом переключает кадр
    public void advance() {
        if (finished()) return;
        Beat b = BEATS[beat];
        if (b.scene() == Scene.BOOK && t < 200) return;             // дать посмотреть на книгу
        if (b.scene() == Scene.CREDITS && t < 90) return;
        if (b.line() != null && t < START + b.line().length() * TYPE) {
            t = START + b.line().length() * TYPE;
        } else {
            next();
        }
    }

    private void next() {
        Scene old = BEATS[beat].scene();
        beat++;
        t = 0;
        if (!finished() && BEATS[beat].scene() != old) st = 0;
    }

    // ================= ОТРИСОВКА =================
    public void draw(Graphics2D g0) {
        if (finished()) return;
        Graphics2D g = (Graphics2D) g0.create();
        Beat b = BEATS[beat];

        p = img.createGraphics();
        switch (b.scene()) {
            case CHILDHOOD -> sceneChildhood();
            case BETRAYAL  -> sceneBetrayal();
            case CAPTIVE   -> sceneCaptive();
            case ANNA      -> sceneAnna();
            case SEBASTIAN -> sceneSebastian();
            case SLUMS     -> sceneSlums();
            case DESTROY   -> sceneDestroy();
            case BOOK      -> sceneBook();
            case CREDITS   -> { }
        }
        p.dispose();

        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, W, H);

        if (b.scene() == Scene.DESTROY) {                           // мир «плывёт»: каждая строка пикселей сдвигается по-своему
            float amp = Math.min(10f, st / 30f) + (beat == DESTROY_FIRST + 2 ? 4f : 0f);
            for (int y = 0; y < LH; y++) {
                int sh = (int) (Math.sin(y * 0.18 + st * 0.10) * amp);
                if ((st / 23) % 5 == 0 && y % 9 < 3) sh += (y % 2 == 0 ? 6 : -6);
                g.drawImage(img, sh * SC, y * SC, sh * SC + W, y * SC + SC, 0, y, LW, y + 1, null);
            }
            drawWhispers(g);
        } else if (b.scene() == Scene.CREDITS) {
            drawCredits(g);
        } else if (b.scene() == Scene.BOOK) {
            g.drawImage(img, 0, 0, W, H, null);
            drawBookText(g);
        } else {                                                    // лёгкое медленное приближение
            float z = 1f + 0.045f * Math.min(1f, st / 420f);
            int dw = (int) (W * z), dh = (int) (H * z);
            g.drawImage(img, (W - dw) / 2, (H - dh) / 2, dw, dh, null);
        }

        // полосы, подпись кадра, подсказка, реплика
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, W, 40);
        g.setFont(new Font("Monospaced", Font.BOLD, 16));
        g.setColor(GOLD);
        g.drawString(b.title(), 16, 26);
        g.setFont(new Font("Monospaced", Font.PLAIN, 14));
        g.setColor(new Color(150, 150, 180));
        String hint = "ПРОБЕЛ — дальше    ESC — пропустить";
        g.drawString(hint, W - g.getFontMetrics().stringWidth(hint) - 14, 26);
        drawSubtitle(g, b);

        // затемнение на стыках кадров
        float fade = 0f;
        int inLen = b.scene() == Scene.CREDITS ? 60 : 20;
        if (st < inLen) fade = 1f - st / (float) inLen;
        boolean last = beat == BEATS.length - 1 || BEATS[beat + 1].scene() != b.scene();
        int fadeLen = b.scene() == Scene.BOOK ? 90 : b.scene() == Scene.CREDITS ? 60 : 16;
        if (last && t > b.dur() - fadeLen) fade = Math.max(fade, (t - (b.dur() - fadeLen)) / (float) fadeLen);
        if (fade > 0f) {
            g.setColor(new Color(0, 0, 0, (int) (255 * Math.min(1f, fade))));
            g.fillRect(0, 0, W, H);
        }
        g.dispose();
    }

    private void drawSubtitle(Graphics2D g, Beat b) {
        if (b.line() == null || t < START) return;
        int n = Math.min(b.line().length(), (t - START) / TYPE);
        g.setColor(new Color(0, 0, 0, 205));
        g.fillRect(0, H - 96, W, 96);
        g.setFont(new Font("Monospaced", Font.BOLD, 18));
        g.setColor(GOLD);
        g.drawString(SPEAKER, 40, H - 72);
        g.setFont(new Font("Monospaced", Font.BOLD, 22));
        g.setColor(Color.WHITE);
        wrap(g, b.line(), n, 40, H - 44, W - 80, 28);
    }

    // перенос по словам; печатает только первые n символов (строки не «прыгают» при наборе)
    private static void wrap(Graphics2D g, String full, int n, int x, int y, int maxW, int lh) {
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
            while (pos < full.length() && full.charAt(pos) == ' ') pos++;
        }
    }

    // ================= ПОМОЩНИКИ ХОЛСТА =================
    private void r(Color c, int x, int y, int w, int h) { p.setColor(c); p.fillRect(x, y, w, h); }

    private void sky(Color top, Color bot, int to) {
        for (int y = 0; y < to; y++) r(Theme.mix(top, bot, y / (double) to), 0, y, LW, 1);
    }

    private void bricks(Color line, Color fill, int toY) {
        r(fill, 0, 0, LW, toY);
        for (int row = 0; row * 8 < toY; row++) {
            int y = row * 8;
            r(line, 0, y, LW, 1);
            for (int x = (row % 2) * 10; x < LW; x += 20) r(line, x, y, 1, 8);
        }
    }

    private void vignette(int alpha) {
        p.setColor(new Color(0, 0, 0, alpha));
        for (int i = 0; i < 8; i++) {
            int w = (8 - i) * 4, h = (8 - i) * 3;
            p.fillRect(0, 0, w, LH);
            p.fillRect(LW - w, 0, w, LH);
            p.fillRect(0, 0, LW, h);
            p.fillRect(0, LH - h, LW, h);
        }
    }

    private void glow(int cx, int cy, int r0, int step, int n, Color c, int alpha) {
        for (int k = n; k >= 1; k--) {
            int rr = r0 + k * step;
            p.setColor(Theme.alpha(c, alpha));
            p.fillOval(cx - rr, cy - rr, rr * 2, rr * 2);
        }
    }

    // Человечек. x — центр, fy — линия ног, h — рост; face: 0 — спереди, 1 — вправо, -1 — влево; bow — голова опущена, волосы закрывают лицо
    private void person(int x, int fy, int h, Color hair, Color cloth, Color legs,
                        boolean longHair, boolean dress, int face, boolean bow) {
        person(x, fy, h, hair, cloth, legs, longHair, dress, face, bow, 0);
    }

    // arms — какие руки НЕ рисовать (их кадр рисует сам через limb): 1 — левая на экране, 2 — правая, 3 — обе
    private void person(int x, int fy, int h, Color hair, Color cloth, Color legs,
                        boolean longHair, boolean dress, int face, boolean bow, int arms) {
        int hd = Math.max(8, h * 2 / 9);                            // голова не меньше 8 px, чтобы поместились глаза 2x2
        int legH = h * 4 / 10;
        int bodyH = h - hd - legH;
        int bw = Math.max(5, h / 4 + 1);
        int top = fy - h;
        int bodyTop = top + hd;
        int legW = Math.max(2, bw / 3);
        int armW = Math.max(2, bw / 4);
        Color shade = Theme.mix(cloth, Color.BLACK, 0.25);

        if (dress) {
            r(cloth, x - bw / 2, bodyTop, bw, bodyH);
            r(cloth, x - bw / 2 - 1, bodyTop + bodyH, bw + 2, legH);
            r(Theme.mix(cloth, Color.BLACK, 0.4), x - bw / 2 - 1, fy - 1, bw + 2, 1);
        } else {
            r(legs, x - bw / 2, fy - legH, legW, legH);
            r(legs, x - bw / 2 + bw - legW, fy - legH, legW, legH);
            r(DARK, x - bw / 2, fy - 1, legW + 1, 1);
            r(DARK, x - bw / 2 + bw - legW, fy - 1, legW + 1, 1);
            r(cloth, x - bw / 2, bodyTop, bw, bodyH);
        }
        if ((arms & 1) == 0) {                                      // левая рука опущена вдоль тела
            r(shade, x - bw / 2 - armW, bodyTop, armW, bodyH - 1);
            r(SKIN, x - bw / 2 - armW, bodyTop + bodyH - 2, armW, 2);
        }
        if ((arms & 2) == 0) {                                      // правая рука опущена вдоль тела
            r(shade, x + bw / 2, bodyTop, armW, bodyH - 1);
            r(SKIN, x + bw / 2, bodyTop + bodyH - 2, armW, 2);
        }

        int headTop = top + (bow ? 1 : 0);
        r(SKIN, x - hd / 2, headTop, hd, hd);
        r(hair, x - hd / 2, headTop - 1, hd, Math.max(2, hd / 3));
        if (longHair) {
            r(hair, x - hd / 2 - 1, headTop, 2, hd + hd / 2);
            r(hair, x + hd / 2 - 1, headTop, 2, hd + hd / 2);
        } else {
            r(hair, x - hd / 2, headTop, 1, hd / 3 + 1);
            r(hair, x + hd / 2 - 1, headTop, 1, hd / 3 + 1);
        }
        if (bow) {
            r(hair, x - hd / 2, headTop, hd, hd * 2 / 3 + 1);       // волосы закрывают лицо
        } else {
            int ey = headTop + hd / 2 - 1;                          // глаза 2x2 (раньше были точкой 1x1)
            if (face == 0) {
                r(eyes, x - hd / 2 + 1, ey, 2, 2);
                r(eyes, x + hd / 2 - 3, ey, 2, 2);
                r(new Color(150, 80, 80), x - 1, headTop + hd * 3 / 4 + 1, 3, 1);
            } else {
                int rx = face > 0 ? x + hd / 2 - 3 : x - hd / 2 + 4;   // в профиль видны оба глаза, сдвинутые по взгляду
                r(eyes, rx - 3, ey, 2, 2);
                r(eyes, rx, ey, 2, 2);
            }
        }
    }

    // Рука одним штрихом от плеча (x0,y0) к кисти (x1,y1); th — толщина, кисть рисуется на конце
    private void limb(Color c, int x0, int y0, int x1, int y1, int th) {
        int n = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
        for (int i = 0; i <= n; i++) {
            int px = n == 0 ? x0 : x0 + (x1 - x0) * i / n;
            int py = n == 0 ? y0 : y0 + (y1 - y0) * i / n;
            r(c, px, py, th, th);
        }
        r(SKIN, x1, y1, th, th);
    }

    // Эльза сидит на полу, обняв колени и опустив голову
    private void sitting(int x, int fy, boolean cry) {
        x += (tick / 4) % 2;                                        // дрожит
        Color dark = Theme.mix(RAGS, Color.BLACK, 0.35);
        Color mid = Theme.mix(RAGS, Color.BLACK, 0.18);

        p.setColor(new Color(0, 0, 0, 70));                         // тень
        p.fillOval(x - 12, fy - 3, 24, 6);

        r(dark, x - 10, fy - 4, 20, 4);                             // подол на полу
        r(RAGS, x - 8, fy - 15, 16, 12);                            // колени, прижатые к груди
        r(mid, x - 8, fy - 8, 16, 4);                               // складка платья
        r(RAGS, x - 6, fy - 22, 12, 8);                             // туловище
        r(new Color(110, 60, 120), x + 3, fy - 8, 3, 2);            // синяк

        r(mid, x - 8, fy - 21, 3, 8);                               // руки обхватывают колени
        r(mid, x + 5, fy - 21, 3, 8);
        r(mid, x - 8, fy - 14, 16, 3);
        r(SKIN, x - 2, fy - 14, 4, 3);                              // сцепленные кисти

        r(SKIN, x - 5, fy - 31, 10, 9);                             // голова
        r(HAIR, x - 6, fy - 33, 12, 5);                             // волосы и чёлка
        r(HAIR, x - 6, fy - 29, 2, 13);
        r(HAIR, x + 4, fy - 29, 2, 13);
        r(HAIR, x - 5, fy - 29, 10, 1);
        r(DARK, x - 3, fy - 27, 2, 2);                              // глаза 2x2
        r(DARK, x + 1, fy - 27, 2, 2);
        r(new Color(150, 80, 80), x - 1, fy - 24, 2, 1);            // грустный рот
        if (cry) {
            int d = (tick / 10) % 4;
            r(new Color(150, 200, 255), x - 3, fy - 25 + d, 1, 2);
            r(new Color(150, 200, 255), x + 2, fy - 25 + d, 1, 2);
        }
    }

    private void flame(int x, int y) {
        int f = (tick / 5 + x) % 2;
        r(new Color(255, 210, 90), x, y - f, 2, 3);
        r(new Color(255, 245, 190), x, y + 1 - f, 1, 1);
    }

    private static float ease(float x) { x = Math.max(0f, Math.min(1f, x)); return x * x * (3 - 2 * x); }
    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }

    // ================= КАДР 1: ДЕТСТВО =================
    private void sceneChildhood() {
        sky(new Color(246, 160, 110), new Color(255, 226, 170), 112);
        p.setColor(new Color(255, 240, 190, 120));
        p.fillOval(170, 28, 42, 42);
        p.setColor(new Color(255, 245, 205));
        p.fillOval(178, 36, 26, 26);
        p.setColor(new Color(150, 116, 126));                       // дальние холмы
        p.fillPolygon(new int[]{0, 50, 110, 160, LW, LW, 0}, new int[]{106, 88, 100, 84, 98, 112, 112}, 7);
        for (int y = 112; y < LH; y++) r(Theme.mix(new Color(90, 134, 72), new Color(40, 82, 52), (y - 112) / 32.0), 0, y, LW, 1);

        int mx = 14, my = 52;                                        // поместье
        r(new Color(214, 196, 168), mx, my, 112, 60);
        r(new Color(178, 158, 132), mx, my + 52, 112, 8);
        p.setColor(new Color(124, 62, 52));
        p.fillPolygon(new int[]{mx - 6, mx + 56, mx + 118}, new int[]{my, my - 30, my}, 3);
        r(new Color(96, 48, 44), mx + 88, my - 20, 8, 14);
        Color frame = new Color(120, 84, 60), lit = new Color(255, 214, 110);
        int[][] wins = {{12, 8}, {84, 8}, {12, 34}, {84, 34}, {48, 8}};
        for (int[] w : wins) {
            int wx = mx + w[0], wy = my + w[1];
            r(frame, wx - 1, wy - 1, 18, 18);
            r(lit, wx, wy, 16, 16);
            r(frame, wx + 7, wy, 2, 16);
            r(frame, wx, wy + 7, 16, 2);
        }
        r(new Color(110, 66, 40), mx + 47, my + 30, 18, 30);         // дверь
        p.setColor(new Color(110, 66, 40));
        p.fillOval(mx + 47, my + 24, 18, 12);
        r(GOLD, mx + 60, my + 46, 2, 2);
        r(new Color(170, 150, 128), mx + 43, my + 58, 26, 2);
        p.setColor(new Color(196, 172, 130));                        // дорожка
        p.fillPolygon(new int[]{mx + 47, mx + 65, mx + 92, mx + 22}, new int[]{112, 112, LH, LH}, 4);

        for (int tx : new int[]{138, 230}) {                         // деревья
            r(new Color(86, 56, 36), tx - 2, 100, 4, 16);
            p.setColor(new Color(52, 104, 56));
            p.fillOval(tx - 14, 74, 28, 30);
            p.setColor(new Color(70, 128, 66));
            p.fillOval(tx - 10, 76, 14, 14);
        }

        int fy = 114;                                                // семья
        Color dadC = new Color(46, 52, 86), momC = new Color(90, 120, 190), kidC = new Color(252, 238, 232);
        person(166, fy, 42, new Color(80, 52, 34), dadC, new Color(36, 36, 50), false, false, 0, false, 2);   // у папы скрыта правая рука
        person(206, fy, 40, new Color(96, 60, 40), momC, Color.BLACK, true, true, 0, false, 1);               // у мамы скрыта левая
        person(186, fy, 32, HAIR, kidC, Color.BLACK, true, true, 0, false, 3);                                // у Эльзы обе подняты
        // руки: папа и мама тянутся вниз к ребёнку, Эльза поднимает ладошки им навстречу
        limb(Theme.mix(dadC, Color.BLACK, 0.25), 172, 84, 177, 94, 2);
        limb(Theme.mix(momC, Color.BLACK, 0.25), 199, 84, 195, 94, 2);
        limb(Theme.mix(kidC, Color.BLACK, 0.18), 181, 96, 178, 94, 2);
        limb(Theme.mix(kidC, Color.BLACK, 0.18), 190, 96, 193, 94, 2);

        for (int i = 0; i < 16; i++) {                               // светлячки
            if ((tick / 8 + i) % 3 == 0) continue;
            int fx = (i * 53 + tick / 3) % LW, fyy = 70 + (i * 31) % 60 + (int) (Math.sin(tick * 0.03 + i) * 3);
            r(new Color(255, 240, 170), fx, fyy, 1, 1);
        }
    }

    // ================= КАДР 2: ПРЕДАТЕЛЬСТВО =================
    private void sceneBetrayal() {
        sky(new Color(30, 22, 34), new Color(46, 30, 40), 100);
        for (int y = 100; y < LH; y++) r(Theme.mix(new Color(40, 26, 30), new Color(20, 14, 18), (y - 100) / 44.0), 0, y, LW, 1);
        for (int x = 0; x < LW; x += 24) r(new Color(24, 16, 26), x, 0, 1, 100);
        r(new Color(20, 16, 26), 14, 14, 30, 44);                    // окно с лунным светом
        r(new Color(90, 110, 160), 16, 16, 26, 40);
        r(new Color(20, 16, 26), 28, 16, 2, 40);
        r(new Color(20, 16, 26), 16, 34, 26, 2);
        p.setColor(new Color(120, 150, 210, 34));
        p.fillPolygon(new int[]{16, 42, 110, 60}, new int[]{56, 56, 100, 100}, 4);

        eyes = Color.BLACK;                                          // у родственников чёрные глаза
        person(92, 122, 52, new Color(40, 30, 30), new Color(70, 46, 84), DARK, false, true, 0, false);
        person(130, 122, 56, new Color(160, 160, 160), new Color(36, 70, 56), DARK, false, true, 0, false);
        person(170, 122, 50, new Color(30, 20, 20), new Color(110, 40, 44), DARK, true, true, 0, false);
        eyes = DARK;
        p.setColor(Color.WHITE);                                     // перо в руке среднего
        p.drawLine(121, 96, 125, 105);
        p.setColor(new Color(220, 220, 230));
        p.drawLine(120, 94, 122, 98);

        r(new Color(100, 62, 40), 50, 100, 160, 10);                 // стол
        r(new Color(92, 30, 40), 50, 110, 160, 30);
        r(new Color(74, 22, 32), 50, 134, 160, 2);
        p.setColor(new Color(232, 218, 172));                        // поддельный документ
        p.fillPolygon(new int[]{100, 152, 158, 94}, new int[]{102, 102, 108, 108}, 4);
        r(new Color(120, 100, 80), 106, 104, 40, 1);
        r(new Color(120, 100, 80), 102, 106, 46, 1);
        p.setColor(new Color(180, 30, 40));
        p.fillOval(144, 104, 6, 4);
        r(new Color(224, 208, 160), 64, 103, 16, 4);                 // стопка бумаг
        r(new Color(210, 194, 150), 66, 101, 16, 3);
        r(DARK, 176, 102, 4, 4);                                     // чернильница

        r(new Color(230, 220, 190), 61, 88, 3, 12);                  // свеча
        flame(61, 84);
        glow(62, 88, 6, 8, 5, new Color(255, 190, 90), 12);
        vignette(40);
    }

    // ================= КАДР 3: ПЛЕН =================
    private void sceneCaptive() {
        bricks(new Color(18, 20, 28), new Color(26, 28, 38), 104);
        r(new Color(34, 32, 42), 0, 104, LW, 40);
        for (int x = 0; x < LW; x += 24) r(new Color(24, 22, 30), x, 104, 1, 40);
        r(new Color(24, 22, 30), 0, 118, LW, 1);

        Color iron = new Color(14, 14, 20);                          // окно с решёткой
        r(iron, 172, 16, 34, 46);
        r(new Color(110, 140, 190), 174, 18, 30, 42);
        for (int x = 178; x < 204; x += 7) r(iron, x, 18, 2, 42);
        r(iron, 174, 34, 30, 2);
        p.setColor(new Color(130, 160, 210, 32));
        p.fillPolygon(new int[]{174, 204, 150, 100}, new int[]{60, 60, 104, 104}, 4);
        p.setColor(new Color(130, 160, 210, 40));
        p.fillPolygon(new int[]{100, 150, 172, 118}, new int[]{108, 108, 128, 128}, 4);

        r(new Color(90, 90, 100), 36, 90, 6, 6);                     // кольцо и цепь к ноге
        for (int i = 0; i < 9; i++) r(new Color(110, 110, 120), 43 + i * 5, 94 + i + (int) (Math.sin(i * 0.8) * 1.5), 3, 2);

        sitting(96, 110, true);

        p.setColor(new Color(176, 176, 186));                        // пустая тарелка
        p.fillOval(130, 102, 22, 7);
        p.setColor(new Color(92, 92, 104));
        p.fillOval(134, 103, 14, 4);
        for (int i = 0; i < 36; i++) r(new Color(72, 42, 26), 160 + i, 108 + (int) (Math.sin(i * 0.35) * 3), 1, 2);   // плеть
        r(new Color(120, 76, 40), 154, 107, 8, 3);
        vignette(60);
    }

    // ================= КАДР 4: АННА =================
    private void sceneAnna() {
        r(new Color(58, 24, 30), 0, 0, LW, LH);
        for (int x = 0; x < LW; x += 16) r(new Color(66, 28, 34), x, 0, 8, 96);
        for (int y = 96; y < LH; y++) r(Theme.mix(new Color(84, 50, 40), new Color(40, 24, 24), (y - 96) / 48.0), 0, y, LW, 1);
        for (int sx : new int[]{0, LW - 22}) {                       // шторы
            r(new Color(150, 28, 44), sx, 0, 22, 100);
            r(new Color(110, 20, 34), sx + 6, 0, 3, 100);
            r(new Color(110, 20, 34), sx + 14, 0, 3, 100);
        }
        r(new Color(210, 170, 60), 22, 0, 2, 100);
        r(new Color(210, 170, 60), LW - 24, 0, 2, 100);
        r(new Color(210, 170, 60), 100, 14, 40, 28);                 // портрет
        r(new Color(70, 90, 110), 102, 16, 36, 24);
        r(new Color(180, 150, 60), 118, 0, 4, 10);                   // люстра
        r(new Color(200, 170, 70), 100, 10, 40, 3);
        for (int i = 0; i < 5; i++) {
            int cx = 102 + i * 9;
            r(new Color(235, 225, 190), cx, 5, 2, 5);
            flame(cx, 3);
        }
        glow(120, 14, 20, 10, 6, new Color(255, 200, 110), 9);

        person(150, 112, 44, new Color(40, 26, 24), new Color(236, 150, 170), DARK, true, true, 0, false, 2);   // Анна (правая рука с бокалом рисуется ниже)
        r(GOLD, 147, 78, 7, 1);                                      // украшения
        r(new Color(220, 40, 80), 150, 79, 1, 1);
        r(GOLD, 146, 66, 9, 1);
        r(GOLD, 148, 65, 1, 1);
        r(GOLD, 150, 64, 1, 2);
        r(GOLD, 152, 65, 1, 1);
        r(new Color(200, 70, 80), 148, 74, 5, 1);                    // улыбка
        limb(Theme.mix(new Color(236, 150, 170), Color.BLACK, 0.25), 156, 82, 159, 79, 3);   // рука с бокалом
        r(GOLD, 160, 74, 5, 4);
        r(GOLD, 162, 78, 1, 3);

        float pulse = 0.5f + 0.5f * (float) Math.sin(tick * 0.08);   // ненависть: красная аура вокруг Эльзы
        for (int k = 6; k >= 1; k--) {
            int rx = 8 + k * 9, ry = (int) (rx * 1.35);
            p.setColor(new Color(220, 20, 40, (int) (9 + 12 * pulse)));
            p.fillOval(28 - rx, 94 - ry, rx * 2, ry * 2);
        }
        r(new Color(236, 228, 216), 50, 92, 166, 6);                 // стол
        r(new Color(220, 210, 196), 50, 98, 166, 24);
        for (int x = 58; x < 216; x += 14) r(new Color(204, 194, 180), x, 98, 1, 24);
        r(new Color(210, 170, 60), 50, 120, 166, 2);
        p.setColor(new Color(142, 78, 38));                          // жаркое
        p.fillOval(102, 84, 28, 11);
        p.setColor(new Color(176, 108, 56));
        p.fillOval(106, 85, 14, 5);
        r(new Color(210, 170, 60), 168, 87, 18, 3);                  // фрукты
        r(new Color(210, 50, 50), 171, 83, 4, 4);
        r(new Color(120, 190, 70), 176, 82, 4, 5);
        r(new Color(240, 170, 50), 180, 84, 4, 3);
        for (int gx : new int[]{70, 88, 196}) {                      // кубки
            r(GOLD, gx, 83, 4, 4);
            r(GOLD, gx + 1, 87, 2, 4);
            r(GOLD, gx - 1, 91, 6, 1);
        }
        for (int cx : new int[]{58, 206}) {
            r(new Color(240, 232, 205), cx, 78, 3, 14);
            flame(cx, 74);
        }

        person(28, 114, 42, HAIR, RAGS, DARK, true, true, 0, true);  // Эльза — опустила голову
        vignette(46);
    }

    // ================= КАДР 5: СЕБАСТЬЯН =================
    private void sceneSebastian() {
        bricks(new Color(14, 16, 22), new Color(20, 22, 30), 108);
        r(new Color(28, 28, 36), 0, 108, LW, 36);
        r(new Color(60, 40, 28), 186, 44, 42, 64);                   // дверь
        p.setColor(new Color(60, 40, 28));
        p.fillOval(186, 32, 42, 26);
        for (int x = 192; x < 228; x += 8) r(new Color(44, 28, 20), x, 40, 1, 68);
        r(new Color(30, 30, 36), 186, 60, 42, 4);
        r(new Color(30, 30, 36), 186, 86, 42, 4);
        r(new Color(90, 90, 100), 204, 70, 8, 10);
        r(DARK, 207, 74, 2, 3);

        r(new Color(60, 60, 66), 130, 0, 1, 28);                     // фонарь
        r(new Color(90, 80, 60), 126, 28, 9, 12);
        r(new Color(255, 210, 110), 127, 30, 7, 8);
        double f = 0.9 + 0.1 * Math.sin(tick * 0.2);
        glow(130, 34, 6, 12, 6, new Color(255, 190, 90), (int) (14 * f));

        person(104, 114, 46, new Color(40, 28, 26), new Color(88, 66, 48), new Color(46, 40, 44), false, false, 1, false, 2);   // Себастьян (рука с ключом ниже)
        r(new Color(230, 225, 215), 100, 78, 9, 2);
        person(152, 114, 40, HAIR, RAGS, DARK, true, true, -1, false, 1);                                                    // Эльза (тянется за ключом)

        int ay = 86;
        limb(new Color(104, 80, 58), 110, 81, 122, ay - 1, 3);         // рука Себастьяна с ключом (одна, от плеча)
        limb(Theme.mix(RAGS, Color.BLACK, 0.3), 146, 85, 131, ay + 1, 3);   // рука Эльзы тянется к ключу
        int kx = 124;                                                // ключ
        r(GOLD, kx, ay - 1, 3, 3);
        r(new Color(20, 22, 30), kx + 1, ay, 1, 1);
        r(GOLD, kx + 3, ay, 7, 1);
        r(GOLD, kx + 8, ay + 1, 1, 2);
        r(GOLD, kx + 6, ay + 1, 1, 1);
        if ((tick / 10) % 3 == 0) {                                  // блик на ключе
            r(Color.WHITE, kx + 5, ay - 3, 1, 5);
            r(Color.WHITE, kx + 3, ay - 1, 5, 1);
        }
        vignette(50);
    }

    // ================= КАДР 6: ТРУЩОБЫ =================
    private void sceneSlums() {
        sky(new Color(10, 14, 28), new Color(44, 52, 76), 108);
        glow(180, 30, 10, 8, 3, new Color(150, 170, 220), 14);       // луна
        p.setColor(new Color(222, 226, 240));
        p.fillOval(170, 20, 20, 20);
        r(new Color(190, 196, 214), 175, 26, 4, 3);
        r(new Color(190, 196, 214), 182, 32, 3, 3);

        Random rn = new Random(7);                                   // заброшенные дома (одинаковые каждый кадр)
        int x = -4;
        while (x < LW) {
            int bw = 22 + rn.nextInt(18), bh = 34 + rn.nextInt(44), top = 108 - bh;
            Color c = new Color(18, 20, 32);
            r(c, x, top, bw, bh);
            r(new Color(10, 12, 20), x + bw - 6 - rn.nextInt(6), top, 8, 6 + rn.nextInt(6));   // обвалившийся угол
            for (int wy = top + 10; wy < 100; wy += 14) {
                for (int wx = x + 4; wx < x + bw - 6; wx += 9) {
                    if (rn.nextInt(4) == 0) continue;
                    r(new Color(6, 7, 14), wx, wy, 5, 7);
                    if (rn.nextInt(5) == 0) r(new Color(34, 40, 58), wx + 1, wy, 2, 3);
                }
            }
            if (rn.nextInt(3) == 0) r(c, x + bw / 2, top - 8, 2, 9);
            x += bw + rn.nextInt(6);
        }
        r(new Color(26, 28, 40), 0, 108, LW, 36);
        r(new Color(40, 44, 62), 0, 108, LW, 2);
        p.setColor(new Color(58, 68, 98));
        p.fillOval(20, 112, 44, 5);
        p.fillOval(100, 117, 52, 5);

        r(new Color(30, 30, 38), 51, 50, 2, 60);                     // сломанный фонарь
        r(new Color(40, 40, 50), 46, 46, 12, 5);
        if ((tick / 9) % 7 < 2) {
            p.setColor(new Color(255, 220, 140, 22));
            p.fillPolygon(new int[]{48, 58, 82, 24}, new int[]{52, 52, 112, 112}, 4);
        }

        int px = 10 + st / 6;                                        // вдалеке проходит патруль с фонарём
        r(new Color(8, 8, 12), px, 92, 5, 14);
        r(new Color(8, 8, 12), px + 1, 89, 3, 3);
        r(new Color(255, 170, 60), px + 5, 97, 2, 2);
        glow(px + 6, 98, 2, 4, 3, new Color(255, 170, 60), 18);

        sitting(176, 114, false);                                    // Эльза совсем одна
        r(new Color(170, 130, 70), 158, 112, 4, 2);                  // сухая корка хлеба

        p.setColor(new Color(150, 170, 210, 110));                   // дождь
        for (int i = 0; i < 70; i++) p.fillRect((i * 37 + tick / 2) % LW, (i * 53 + tick * 3) % LH, 1, 4);
        vignette(70);
    }

    // ================= КАДР 7: РАЗРУШЕНИЕ =================
    private void sceneDestroy() {
        int sub = beat - DESTROY_FIRST;                              // 0 — «мечтала», 1 — «надежда», 2 — «не суждено»
        sky(new Color(16, 6, 26), new Color(64, 12, 42), LH);
        p.setColor(new Color(110, 34, 84, 120));                     // пол уходит в перспективу
        for (int i = -6; i <= 6; i++) p.drawLine(LW / 2 + i * 8, 84, LW / 2 + i * 44, LH);
        for (int y = 86; y < LH; y += (y - 80) / 6 + 3) p.drawLine(0, y, LW, y);

        if (sub >= 1) {                                              // иллюзия выхода — дверь света
            float a = sub == 1 ? Math.min(1f, t / 80f)
                    : Math.max(0f, 1f - t / 130f) * (((t / 4) % 5 == 0) ? 0.35f : 1f);
            for (int k = 6; k >= 1; k--) {
                int rr = 14 + k * 14;
                p.setColor(new Color(255, 230, 150, (int) (a * 16)));
                p.fillOval(208 - rr, 56 - rr, rr * 2, rr * 2);
            }
            p.setColor(new Color(255, 248, 210, (int) (255 * a)));
            p.fillRect(198, 40, 22, 44);
            p.fillOval(198, 30, 22, 20);
        }

        Color[] eyeC = {new Color(255, 60, 60), new Color(240, 240, 255)};
        int[][] sh = {{20, 112, 50}, {48, 116, 40}, {148, 114, 46}, {172, 116, 38}, {132, 110, 52}, {228, 116, 42}, {70, 108, 44}};
        int count = Math.min(sh.length, 2 + st / 40);
        for (int i = 0; i < count; i++) {                            // силуэты окружают
            int sx = sh[i][0] + (int) (Math.sin(tick * 0.05 + i) * 2), fy = sh[i][1], h = sh[i][2], hd = h / 4;
            p.setColor(new Color(5, 2, 9));
            p.fillOval(sx - hd / 2, fy - h, hd, hd + 2);
            p.fillPolygon(new int[]{sx - hd, sx + hd, sx + hd + 3, sx - hd - 3},
                    new int[]{fy - h + hd, fy - h + hd, fy, fy}, 4);
            if ((tick / 6 + i) % 9 != 0) {
                r(eyeC[i % 2], sx - hd / 4 - 1, fy - h + hd / 2, 2, 1);
                r(eyeC[i % 2], sx + hd / 4, fy - h + hd / 2, 2, 1);
            }
        }

        int ex = 96 + ((tick / 3) % 2 == 0 ? 0 : 1);                 // Эльза — зажимает уши
        Color sleeve = Theme.mix(RAGS, Color.BLACK, 0.3);
        person(ex, 114, 42, HAIR, RAGS, DARK, true, true, sub >= 1 ? 1 : 0, false, sub == 0 ? 3 : 2);
        if (sub == 0) {                                              // обе руки подняты к ушам
            limb(sleeve, ex - 7, 84, ex - 8, 77, 3);
            limb(sleeve, ex + 5, 84, ex + 6, 77, 3);
        } else {
            limb(sleeve, ex + 4, 83, ex + 21, 86, 3);                // тянется к свету
        }

        if ((st / 37) % 4 == 3 && st % 37 < 5) {                     // вспышки
            p.setColor(new Color(255, 255, 255, 70));
            p.fillRect(0, 0, LW, LH);
        }
        if (sub == 2 && t > 60) {                                    // мираж трескается
            Random rn = new Random(t / 4);
            p.setColor(new Color(255, 255, 255, 200));
            for (int i = 0; i < 6; i++) p.drawLine(rn.nextInt(LW), 0, rn.nextInt(LW), LH);
        }
        vignette(60);
    }

    // шёпот на заднем плане кадра 7 (обычным шрифтом поверх пикселей)
    private void drawWhispers(Graphics2D g) {
        int n = Math.min(WHISPERS.length, 2 + st / 40);
        for (int i = 0; i < n; i++) {
            float pulse = 0.5f + 0.5f * (float) Math.sin(tick * 0.04 + i * 1.7);
            int x = 50 + (i * 263 + 97) % 620 + (int) (Math.sin(tick * 0.03 + i) * 8);
            int y = 90 + (i * 71) % 300;
            int shake = (st / 23) % 5 == 0 ? (tick % 2 == 0 ? 3 : -3) : 0;
            g.setFont(new Font(Font.SERIF, Font.ITALIC, 22 + (i % 3) * 5));
            g.setColor(new Color(225, 205, 235, (int) (40 + 110 * pulse)));
            g.drawString(WHISPERS[i], x + shake, y);
        }
    }

    // ================= КАДР 8: КНИГА =================
    private int[] bookRect() {
        float s = ease(Math.min(1f, st / 140f));
        int bw = (int) lerp(60, 100, s), bh = (int) lerp(76, 120, s);
        return new int[]{LW / 2 - bw / 2, LH / 2 - bh / 2, bw, bh};
    }

    private void sceneBook() {
        r(new Color(8, 6, 14), 0, 0, LW, LH);
        Color glow = ending == Sins.Ending.HEAVEN ? new Color(255, 220, 130) : new Color(255, 110, 60);
        glow(LW / 2, LH / 2, 20, 14, 8, glow, 10);
        for (int i = 0; i < 40; i++) {                               // пылинки в свете
            int dx = (i * 47) % LW, dy = LH - ((i * 29 + st / 2) % LH);
            r(Theme.alpha(glow, 130), dx, dy, 1, 1);
        }
        int[] bk = bookRect();
        int x = bk[0], y = bk[1], bw = bk[2], bh = bk[3];
        r(new Color(0, 0, 0, 120), x + 3, y + 4, bw, bh);
        r(new Color(226, 214, 184), x + 2, y + 3, bw, bh - 2);       // страницы
        r(new Color(122, 38, 44), x, y, bw, bh);                     // обложка
        r(new Color(84, 24, 32), x, y, Math.max(2, bw / 10), bh);
        p.setColor(GOLD);
        p.drawRect(x + bw / 6, y + 4, bw - bw / 6 - 5, bh - 9);
        p.drawRect(x + bw / 6 + 2, y + 6, bw - bw / 6 - 9, bh - 13);
        int ex = x + (int) (bw * 0.575), ey = y + (int) (bh * 0.28), rr = Math.max(4, bw / 10);   // нимб
        p.drawOval(ex - rr, ey - rr, rr * 2, rr * 2);
        for (int i = 0; i < 12; i++) {
            double a = i * Math.PI / 6;
            p.drawLine(ex + (int) (Math.cos(a) * rr * 1.4), ey + (int) (Math.sin(a) * rr * 1.4),
                    ex + (int) (Math.cos(a) * rr * 1.9), ey + (int) (Math.sin(a) * rr * 1.9));
        }
    }

    private void drawBookText(Graphics2D g) {
        int[] bk = bookRect();
        int cx = (bk[0] + (int) (bk[2] * 0.575)) * SC;
        int by = bk[1] * SC, bh = bk[3] * SC;
        float ta = ease((st - 50) / 60f);
        if (ta <= 0f) return;

        g.setFont(new Font(Font.SERIF, Font.BOLD | Font.ITALIC, (int) (bk[2] * SC * 0.058)));
        g.setColor(new Color(240, 200, 60, (int) (255 * ta)));
        centered(g, BOOK_TITLE, cx, by + (int) (bh * 0.58));

        float na = ease((st - 120) / 70f);                           // акцент на NOVEL
        if (na <= 0f) return;
        float pulse = 0.5f + 0.5f * (float) Math.sin(tick * 0.06);
        int size = (int) (bk[2] * SC * 0.145);
        g.setFont(new Font(Font.SERIF, Font.BOLD, size));
        int ny = by + (int) (bh * 0.80);
        Color gl = ending == Sins.Ending.HEAVEN ? new Color(255, 225, 140) : new Color(255, 130, 70);
        for (int i = 4; i >= 1; i--) {                               // пульсирующее свечение
            g.setColor(Theme.alpha(gl, (int) (na * (12 + 16 * pulse))));
            spaced(g, NOVEL, cx - i * 2, ny, 4, true);
            spaced(g, NOVEL, cx + i * 2, ny, 4, true);
            spaced(g, NOVEL, cx, ny - i * 2, 4, true);
            spaced(g, NOVEL, cx, ny + i * 2, 4, true);
        }
        g.setColor(new Color(255, 240, 190, (int) (255 * na)));
        spaced(g, NOVEL, cx, ny, 4, true);
        g.setColor(new Color(240, 200, 60, (int) (220 * na)));       // золотая черта под надписью
        int lw = (int) (bk[2] * SC * 0.5);
        g.fillRect(cx - lw / 2, ny + 16, lw, 3);
    }

    // титры: чёрный фон, медленно плывущий вверх текст «как в кино»
    private void drawCredits(Graphics2D g) {
        g.setColor(new Color(6, 5, 10));
        g.fillRect(0, 0, W, H);
        Color glow = ending == Sins.Ending.HEAVEN ? new Color(255, 220, 130) : new Color(255, 110, 60);
        for (int i = 0; i < 50; i++) {                               // редкие пылинки на фоне
            int dx = (i * 211 + 37) % W, dy = (int) ((H * 2 - (i * 97 + st * (0.2f + (i % 4) * 0.08f))) % H);
            if (dy < 0) dy += H;
            g.setColor(Theme.alpha(glow, 40 + (i % 3) * 25));
            g.fillRect(dx, dy, 2, 2);
        }

        int cx = W / 2;
        float top = H - t * CREDITS_SPEED;                           // верх текста, едет вверх

        g.setFont(new Font(Font.SERIF, Font.BOLD, 38));
        g.setColor(GOLD);
        centered(g, CREDITS_HEAD, cx, (int) top + 40);
        g.setColor(Theme.alpha(GOLD, 160));
        g.fillRect(cx - 120, (int) top + 62, 240, 2);

        for (int i = 0; i < CREDITS.length; i++) {
            int y = (int) top + CREDITS_HEAD_H + i * CREDITS_GAP;
            g.setFont(new Font(Font.SERIF, Font.ITALIC, 22));
            g.setColor(new Color(200, 180, 130));
            centered(g, CREDITS[i][0], cx, y + 20);
            g.setFont(new Font(Font.SERIF, Font.BOLD, 34));
            g.setColor(new Color(245, 240, 230));
            centered(g, CREDITS[i][1], cx, y + 62);
        }

        // плавное исчезновение текста у верхней и нижней кромки
        g.setPaint(new GradientPaint(0, 40, new Color(6, 5, 10), 0, 140, new Color(6, 5, 10, 0)));
        g.fillRect(0, 40, W, 100);
        g.setPaint(new GradientPaint(0, H - 100, new Color(6, 5, 10, 0), 0, H, new Color(6, 5, 10)));
        g.fillRect(0, H - 100, W, 100);
    }

    private static void centered(Graphics2D g, String s, int cx, int y) {
        g.drawString(s, cx - g.getFontMetrics().stringWidth(s) / 2, y);
    }

    // текст с разрядкой между буквами
    private static void spaced(Graphics2D g, String s, int cx, int y, int gap, boolean center) {
        FontMetrics fm = g.getFontMetrics();
        int total = 0;
        for (char c : s.toCharArray()) total += fm.charWidth(c) + gap;
        total -= gap;
        int x = center ? cx - total / 2 : cx;
        for (char c : s.toCharArray()) {
            g.drawString(String.valueOf(c), x, y);
            x += fm.charWidth(c) + gap;
        }
    }
}