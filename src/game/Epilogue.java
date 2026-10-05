package game;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

// Катсцена-эпилог (после экрана «АД / РАЙ»): пиксельная история Эльзы в 8 кадрах.
// Управление: ПРОБЕЛ / ENTER / E — дальше (первое нажатие дописывает реплику), ESC — пропустить всё.
// Использование из GamePanel: epilogue = new Epilogue(); в update() -> epilogue.update(); в paint -> epilogue.draw(g);
class Epilogue {
    private static final int W = Room.VIEW_W, H = Room.VIEW_H;
    private static final int GROUND = 420;                // линия «ног» в сценах
    private static final int TYPE = 2;                    // кадров на одну букву реплики
    private static final int TYPE_START = 20;             // через сколько кадров начать печатать
    private static final int HOLD = 170;                  // сколько держать реплику после печати
    private static final Color GOLD = new Color(240, 200, 60);
    private static final Color SKIN = new Color(236, 196, 174);
    private static final Color ELSA_HAIR = new Color(245, 202, 3);
    private static final Color RAGS = new Color(96, 78, 72);
    private static final Color RED = new Color(198, 46, 64), RED_DARK = new Color(128, 26, 46);
    private static final Color DARK = new Color(30, 24, 40);
    private static final Color BOOT = new Color(238, 134, 38);

    // line — реплика рассказчика (null = без реплики), dur — длительность кадра в кадрах анимации
    private record Frame(String caption, String line, int dur) {}

    private static Frame f(String caption, String line) {
        return new Frame(caption, line, TYPE_START + line.length() * TYPE + HOLD);
    }

    private static final Frame[] FRAMES = {
            f("КАДР 1 — ДЕТСТВО",
                    "Когда-то Эльза жила в любви и достатке. У неё были родители, дом и будущее."),
            f("КАДР 2 — ПРЕДАТЕЛЬСТВО",
                    "Но родственники завидовали им. Они подделали доказательства и отняли всё."),
            f("КАДР 3 — ПЛЕН",
                    "Эльза осталась в их доме. Её морили голодом, избивали и унижали."),
            f("КАДР 4 — АННА",
                    "Анна получала всё то, чего Эльза лишилась. И постепенно Эльза начала её ненавидеть."),
            f("КАДР 5 — СЕБАСТЬЯН",
                    "Но однажды Себастьян помог ей сбежать."),
            f("КАДР 6 — ТРУЩОБЫ",
                    "Свобода не спасла её. Она голодала, пряталась и постоянно боялась, что её найдут."),
            f("КАДР 7 — РАЗРУШЕНИЕ",
                    "Она мечтала избавиться от страданий..\nИ тут она обрела надежду, иллюзию выхода.\nНо не всем мечтам суждено сбыться..."),
            new Frame("КАДР 8 — КНИГА", null, 520),
    };

    private int frame, t;
    private BufferedImage buf;                            // для «глюка» в кадре 7

    public boolean finished() { return frame >= FRAMES.length; }
    public void skip()        { frame = FRAMES.length; }

    public void update() {
        if (finished()) return;
        if (++t >= FRAMES[frame].dur()) next();
    }

    // кнопка «дальше»: сначала дописывает реплику, потом переключает кадр
    public void advance() {
        if (finished()) return;
        Frame fr = FRAMES[frame];
        if (fr.line() != null && t < TYPE_START + fr.line().length() * TYPE) {
            t = TYPE_START + fr.line().length() * TYPE;
        } else {
            next();
        }
    }

    private void next() { frame++; t = 0; }

    // ================= ОТРИСОВКА =================
    public void draw(Graphics2D g0) {
        if (finished()) return;
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        Frame fr = FRAMES[frame];

        switch (frame) {
            case 0 -> childhood(g);
            case 1 -> betrayal(g);
            case 2 -> captivity(g);
            case 3 -> anna(g);
            case 4 -> sebastian(g);
            case 5 -> slums(g);
            case 6 -> destruction(g);
            default -> book(g);
        }

        // кинематографичная полоса сверху, подпись кадра, подсказка
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, W, 40);
        g.setFont(new Font("Monospaced", Font.BOLD, 16));
        g.setColor(GOLD);
        g.drawString(fr.caption(), 14, 26);
        g.setFont(new Font("Monospaced", Font.PLAIN, 14));
        g.setColor(new Color(150, 150, 180));
        String hint = "ПРОБЕЛ — дальше    ESC — пропустить";
        g.drawString(hint, W - g.getFontMetrics().stringWidth(hint) - 14, 26);

        if (fr.line() != null) subtitle(g, fr.line());

        // появление из темноты и уход в темноту
        int a = 0;
        int left = fr.dur() - t;
        if (t < 18) a = 255 * (18 - t) / 18;
        if (left < 14) a = Math.max(a, 255 * (14 - left) / 14);
        if (frame == FRAMES.length - 1 && left < 90) a = Math.max(a, 255 * (90 - left) / 90);
        if (a > 0) {
            g.setColor(new Color(0, 0, 0, Math.min(255, a)));
            g.fillRect(0, 0, W, H);
        }
        g.dispose();
    }

    // ---------- реплика внизу с эффектом печатной машинки ----------
    private void subtitle(Graphics2D g, String line) {
        g.setFont(new Font("Monospaced", Font.BOLD, 20));
        FontMetrics fm = g.getFontMetrics();
        List<String> lines = wrap(fm, line, W - 120);
        int shown = Math.max(0, (t - TYPE_START) / TYPE);
        int boxH = 30 + lines.size() * 28;
        int y0 = H - boxH - 14;
        g.setColor(new Color(0, 0, 0, 195));
        g.fillRect(40, y0, W - 80, boxH);
        g.setColor(new Color(GOLD.getRed(), GOLD.getGreen(), GOLD.getBlue(), 120));
        g.drawRect(40, y0, W - 81, boxH - 1);
        int y = y0 + 34;
        int total = 0;
        for (String s : lines) total += s.length() + 1;
        for (String s : lines) {
            if (shown > 0) {
                String part = s.substring(0, Math.min(s.length(), shown));
                g.setColor(Color.WHITE);
                g.drawString(part, (W - fm.stringWidth(s)) / 2, y);
            }
            shown -= s.length() + 1;
            y += 28;
        }
        if ((t - TYPE_START) / TYPE >= total && (t / 20) % 2 == 0) {       // мигающая стрелка «дальше»
            g.setColor(GOLD);
            int ax = W - 70, ay = y0 + boxH - 16;
            g.fillPolygon(new int[]{ax, ax + 14, ax + 7}, new int[]{ay, ay, ay + 8}, 3);
        }
    }

    private static List<String> wrap(FontMetrics fm, String text, int maxW) {
        List<String> out = new ArrayList<>();
        for (String para : text.split("\n")) {
            String cur = "";
            for (String word : para.split(" ")) {
                String test = cur.isEmpty() ? word : cur + " " + word;
                if (fm.stringWidth(test) > maxW && !cur.isEmpty()) {
                    out.add(cur);
                    cur = word;
                } else {
                    cur = test;
                }
            }
            out.add(cur);
        }
        return out;
    }

    // ================= КАДР 1: ДЕТСТВО =================
    private void childhood(Graphics2D g) {
        grad(g, new Color(255, 214, 150), new Color(140, 200, 245));
        glow(g, 140, 130, 120, new Color(255, 240, 180), 120);
        g.setColor(new Color(255, 245, 190));
        g.fillOval(95, 85, 90, 90);
        int cx = (80 + t / 2) % (W + 160) - 80;                            // облако плывёт
        r(g, new Color(255, 255, 255, 190), cx, 100, 90, 18);
        r(g, new Color(255, 255, 255, 190), cx + 20, 86, 50, 18);

        g.setColor(new Color(120, 170, 110));                              // холмы
        g.fillOval(-100, 320, 500, 200);
        g.fillOval(300, 330, 600, 200);
        r(g, new Color(96, 160, 74), 0, 385, W, H - 385);
        for (int i = 0; i < 10; i++) r(g, new Color(110, 175, 84), 0, 392 + i * 20, W, 6);

        // поместье
        r(g, new Color(226, 210, 176), 500, 210, 400, 180);
        r(g, new Color(170, 150, 120), 500, 380, 400, 14);
        g.setColor(new Color(150, 60, 52));
        g.fillPolygon(new int[]{480, 700, 920}, new int[]{215, 120, 215}, 3);
        r(g, new Color(120, 80, 70), 790, 132, 26, 56);
        int[] wx = {535, 610, 760, 835};
        for (int x : wx) window(g, x, 250, 40, 50, new Color(255, 225, 130));
        r(g, new Color(110, 70, 44), 680, 310, 60, 80);
        g.setColor(new Color(110, 70, 44));
        g.fillOval(680, 296, 60, 30);
        r(g, GOLD, 726, 352, 6, 6);
        g.setColor(new Color(210, 190, 150));
        g.fillPolygon(new int[]{680, 740, 820, 600}, new int[]{392, 392, H, H}, 4);

        // дерево
        r(g, new Color(100, 70, 40), 56, 300, 20, 90);
        g.setColor(new Color(60, 130, 60));
        g.fillOval(10, 210, 120, 110);
        g.setColor(new Color(80, 155, 70));
        g.fillOval(30, 190, 80, 80);

        // семья: отец, маленькая Эльза, мать
        int foot = 410;
        person(g, 330, foot, 3, new Color(198, 46, 64), GOLD, ELSA_HAIR, SKIN, Color.BLACK, true, true);
        person(g, 220, foot, 5, new Color(50, 70, 120), GOLD, new Color(70, 50, 40), SKIN, Color.BLACK, false, false);
        person(g, 440, foot, 5, new Color(60, 130, 100), GOLD, new Color(120, 70, 40), SKIN, Color.BLACK, true, true);
        g.setColor(SKIN);                                                  // держатся за руки
        g.setStroke(new BasicStroke(4));
        g.drawLine(248, 376, 314, 389);
        g.drawLine(346, 389, 412, 376);
        g.setStroke(new BasicStroke(1));

        int hy = 300 - (t % 120) / 3;                                      // всплывающие сердечки
        heart(g, 318, hy, new Color(255, 120, 150, Math.max(0, 255 - (t % 120) * 2)));
    }

    // ================= КАДР 2: ПРЕДАТЕЛЬСТВО =================
    private void betrayal(Graphics2D g) {
        grad(g, new Color(26, 18, 28), new Color(48, 30, 34));
        for (int x = 0; x < W; x += 120) r(g, new Color(38, 26, 32), x + 4, 60, 112, 290);
        r(g, new Color(58, 38, 36), 0, 360, W, H - 360);

        int foot = 372;                                                    // три родственника за столом
        person(g, 330, foot, 5, new Color(70, 40, 80), new Color(30, 20, 40), new Color(120, 120, 130), SKIN, new Color(255, 230, 120), false, false);
        person(g, 500, foot, 5, new Color(40, 70, 60), new Color(20, 40, 30), new Color(30, 30, 30), SKIN, new Color(255, 230, 120), true, true);
        person(g, 670, foot, 5, new Color(80, 50, 40), new Color(40, 24, 20), new Color(150, 150, 150), SKIN, new Color(255, 230, 120), false, false);
        for (int cx : new int[]{330, 500, 670}) smirk(g, cx, foot, 5);

        r(g, new Color(100, 64, 40), 230, 330, 560, 20);                   // стол
        r(g, new Color(70, 44, 28), 230, 350, 560, 70);
        r(g, new Color(240, 232, 205), 380, 314, 80, 16);                  // поддельные бумаги
        r(g, new Color(150, 140, 120), 388, 318, 60, 2);
        r(g, new Color(150, 140, 120), 388, 323, 48, 2);
        g.setColor(new Color(190, 30, 40));
        g.fillOval(446, 316, 14, 14);                                      // печать
        g.setColor(new Color(60, 50, 40));                                 // перо в руке
        g.drawLine(470, 330, 520, 296);

        glow(g, 740, 296, 70, new Color(255, 180, 60), 70);                // свеча
        r(g, new Color(240, 230, 200), 736, 300, 8, 30);
        g.setColor(new Color(255, 170, 40));
        g.fillOval(737, 286 + (t / 6) % 2, 6, 13);

        // большой поддельный документ слева
        int pulse = (int) (40 + 30 * Math.sin(t * 0.08));
        glow(g, 120, 220, 130, new Color(255, 80, 80), pulse);
        r(g, new Color(240, 232, 205), 50, 110, 140, 200);
        r(g, new Color(90, 80, 70), 66, 128, 108, 6);
        for (int i = 0; i < 7; i++) r(g, new Color(150, 140, 120), 66, 148 + i * 16, 108, 3);
        g.setColor(new Color(60, 50, 40));                                 // подпись-каракуля
        for (int i = 0; i < 6; i++) g.drawLine(70 + i * 10, 272 + (i % 2) * 8, 80 + i * 10, 272 + ((i + 1) % 2) * 8);
        g.setColor(new Color(190, 30, 40));
        g.fillOval(130, 270, 34, 34);
        r(g, new Color(240, 232, 205), 140, 285, 14, 4);
    }

    // ================= КАДР 3: ПЛЕН =================
    private void captivity(Graphics2D g) {
        grad(g, new Color(18, 18, 26), new Color(34, 30, 40));
        for (int row = 0; row < 8; row++)
            for (int col = 0; col < 12; col++) {
                int bx = col * 90 - (row % 2) * 45;
                r(g, (row + col) % 3 == 0 ? new Color(40, 38, 50) : new Color(32, 30, 42), bx + 2, 40 + row * 44 + 2, 86, 40);
            }
        r(g, new Color(26, 22, 30), 0, 392, W, H - 392);
        r(g, new Color(46, 40, 52), 0, 392, W, 4);

        r(g, new Color(8, 8, 12), 700, 80, 90, 120);                       // окно с решёткой и лучом света
        for (int i = 0; i < 4; i++) r(g, new Color(90, 90, 100), 708 + i * 22, 80, 6, 120);
        r(g, new Color(90, 90, 100), 700, 130, 90, 6);
        int la = (int) (36 + 10 * Math.sin(t * 0.05));
        g.setColor(new Color(200, 210, 255, la));
        g.fillPolygon(new int[]{700, 790, 580, 470}, new int[]{200, 200, 410, 410}, 4);
        for (int i = 0; i < 12; i++) {                                     // пылинки
            int px = 560 + (i * 37 + t / 2) % 200 - (i * 11) % 60;
            int py = 230 + (i * 53 + t / 3) % 170;
            r(g, new Color(230, 235, 255, 120), px, py, 2, 2);
        }

        // Эльза лежит на полу
        Color hair = Theme.mix(ELSA_HAIR, Color.BLACK, 0.3);
        Color skin = Theme.mix(SKIN, new Color(120, 120, 130), 0.3);
        r(g, hair, 296, 398, 48, 14);
        r(g, skin, 340, 390, 28, 24);
        r(g, hair, 336, 384, 36, 10);
        r(g, RAGS, 368, 390, 80, 26);
        r(g, Theme.mix(RAGS, Color.BLACK, 0.3), 448, 396, 72, 18);
        r(g, new Color(150, 90, 40), 520, 398, 18, 14);
        r(g, skin, 380, 414, 34, 8);
        r(g, new Color(90, 50, 100), 388, 396, 10, 8);                     // синяки
        r(g, new Color(130, 40, 50), 420, 394, 8, 6);
        r(g, new Color(110, 110, 120), 408, 412, 14, 10);                  // кандалы и цепь
        g.setColor(new Color(110, 110, 120));
        g.drawLine(414, 416, 262, 340);
        g.drawOval(250, 326, 16, 16);

        r(g, new Color(90, 56, 34), 600, 402, 60, 8);                      // плеть
        g.setStroke(new BasicStroke(3));
        g.setColor(new Color(60, 36, 24));
        g.drawLine(660, 406, 700, 414);
        g.drawLine(700, 414, 740, 402);
        g.drawLine(740, 402, 780, 412);
        g.setColor(new Color(150, 40, 40));
        g.drawLine(780, 412, 800, 398);
        g.setStroke(new BasicStroke(1));

        g.setColor(new Color(120, 120, 130));                              // пустая тарелка
        g.fillOval(190, 402, 80, 18);
        g.setColor(new Color(58, 58, 68));
        g.fillOval(200, 405, 60, 11);
    }

    // ================= КАДР 4: АННА =================
    private void anna(Graphics2D g) {
        grad(g, new Color(70, 18, 30), new Color(40, 10, 20));
        for (int x = 0; x < W; x += 160) {
            r(g, new Color(90, 28, 40), x + 10, 60, 140, 280);
            r(g, GOLD, x + 10, 60, 3, 280);
        }
        for (int x = 0; x < W; x += 60)
            for (int y = 380; y < H; y += 30)
                r(g, ((x / 60 + y / 30) % 2 == 0) ? new Color(60, 40, 40) : new Color(34, 22, 26), x, y, 60, 30);

        r(g, GOLD, W / 2 - 2, 40, 4, 40);                                  // люстра
        r(g, GOLD, W / 2 - 60, 80, 120, 6);
        for (int i = 0; i < 5; i++) {
            int fx = W / 2 - 56 + i * 28;
            r(g, new Color(240, 230, 200), fx, 70, 6, 10);
            g.setColor(new Color(255, 190, 70));
            g.fillOval(fx, 60 + (t / 6 + i) % 2, 6, 10);
        }
        glow(g, W / 2, 90, 150, new Color(255, 200, 100), 60);

        // Эльза в стороне, в тени
        Color eye = t > 120 ? new Color(235, 40, 40) : DARK;
        person(g, 130, GROUND, 5, RAGS, Theme.mix(RAGS, Color.BLACK, 0.4), Theme.mix(ELSA_HAIR, Color.BLACK, 0.3),
                Theme.mix(SKIN, new Color(120, 120, 130), 0.3), eye, true, true);
        g.setColor(new Color(0, 0, 0, 70));
        g.fillRect(0, 40, 270, H);
        if (t > 120) glow(g, 130, 330, 100, new Color(220, 30, 30), (int) (50 + 30 * Math.sin(t * 0.1)));

        // Анна за богатым столом
        person(g, 580, 352, 5, new Color(240, 150, 190), GOLD, new Color(150, 60, 30), SKIN, Color.BLACK, true, true);
        r(g, GOLD, 568, 238, 24, 6);                                       // тиара
        r(g, GOLD, 568, 277, 24, 5);                                       // ожерелье
        r(g, new Color(220, 40, 70), 577, 282, 6, 6);
        r(g, new Color(236, 230, 220), 300, 336, 600, 24);                 // стол
        r(g, new Color(224, 216, 206), 300, 360, 600, 64);
        r(g, GOLD, 300, 412, 600, 4);
        g.setColor(GOLD);                                                  // блюда
        g.fillOval(400, 322, 110, 22);
        g.setColor(new Color(150, 80, 36));
        g.fillOval(420, 308, 70, 28);
        g.setColor(new Color(200, 40, 50));
        g.fillOval(650, 316, 16, 16);
        g.fillOval(668, 316, 16, 16);
        g.setColor(new Color(110, 180, 70));
        g.fillOval(659, 306, 16, 16);
        for (int cxx : new int[]{340, 800}) {                              // свечи
            r(g, GOLD, cxx, 296, 6, 40);
            r(g, new Color(240, 230, 200), cxx, 280, 6, 18);
            g.setColor(new Color(255, 190, 70));
            g.fillOval(cxx, 268 + (t / 6) % 2, 6, 12);
        }
        r(g, new Color(200, 200, 220), 560, 312, 8, 20);                   // кубок
        r(g, new Color(200, 200, 220), 556, 330, 16, 4);
    }

    // ================= КАДР 5: СЕБАСТЬЯН =================
    private void sebastian(Graphics2D g) {
        grad(g, new Color(14, 16, 30), new Color(28, 30, 50));
        for (int row = 0; row < 8; row++)
            for (int col = 0; col < 12; col++) {
                int bx = col * 90 - (row % 2) * 45;
                r(g, (row + col) % 3 == 0 ? new Color(28, 32, 52) : new Color(22, 26, 44), bx + 2, 40 + row * 44 + 2, 86, 40);
            }
        r(g, new Color(18, 20, 34), 0, 424, W, H - 424);

        r(g, new Color(110, 70, 44), 96, 210, 10, 50);                     // факел
        glow(g, 101, 200, 170, new Color(255, 150, 50), 90 + (t / 5) % 3 * 10);
        g.setColor(new Color(255, 140, 30));
        g.fillOval(92, 172 + (t / 6) % 3, 20, 38);
        g.setColor(new Color(255, 230, 120));
        g.fillOval(97, 188 + (t / 6) % 3, 10, 20);

        r(g, new Color(70, 48, 34), 736, 110, 160, 314);                   // дверь, приоткрытая на волю
        g.setPaint(new GradientPaint(0, 120, new Color(150, 175, 225), 0, 424, new Color(50, 70, 120)));
        g.fillRect(748, 122, 136, 302);
        g.setColor(new Color(70, 48, 34));
        g.fillPolygon(new int[]{748, 748, 800, 800}, new int[]{122, 424, 410, 140}, 4);

        person(g, 340, GROUND, 5, RAGS, Theme.mix(RAGS, Color.BLACK, 0.4), Theme.mix(ELSA_HAIR, Color.BLACK, 0.2),
                Theme.mix(SKIN, new Color(120, 120, 130), 0.2), Color.BLACK, true, true);
        person(g, 560, GROUND, 5, new Color(110, 80, 50), new Color(60, 40, 26), new Color(40, 30, 30), SKIN, Color.BLACK, false, false);

        // рука Себастьяна с ключом и рука Эльзы
        r(g, new Color(110, 80, 50), 470, 345, 70, 10);
        r(g, GOLD, 432, 347, 44, 5);                                       // ключ
        r(g, GOLD, 432, 352, 5, 8);
        r(g, GOLD, 442, 352, 5, 6);
        g.setColor(GOLD);
        g.fillOval(468, 338, 20, 20);
        g.setColor(new Color(40, 30, 20));
        g.fillOval(474, 344, 8, 8);
        r(g, SKIN, 484, 342, 14, 16);
        r(g, RAGS, 366, 345, 62, 10);
        r(g, SKIN, 424, 343, 12, 14);
        glow(g, 454, 350, 50, GOLD, (int) (90 + 60 * Math.sin(t * 0.12)));
        for (int i = 0; i < 4; i++) {                                      // искры у ключа
            int sx = 440 + (i * 17 + t / 3) % 40;
            int sy = 320 + (i * 23 + t / 2) % 50;
            r(g, new Color(255, 240, 160, 200), sx, sy, 3, 3);
        }
    }

    // ================= КАДР 6: ТРУЩОБЫ =================
    private void slums(Graphics2D g) {
        grad(g, new Color(22, 28, 44), new Color(54, 58, 72));
        glow(g, 815, 105, 120, new Color(200, 210, 240), 70);
        g.setColor(new Color(210, 215, 230));
        g.fillOval(780, 70, 70, 70);
        g.setColor(new Color(180, 186, 205));
        g.fillOval(800, 90, 14, 14);

        int[] bx = {0, 130, 260, 380, 520, 650, 780, 870};                 // заброшенные дома
        int[] bh = {160, 230, 130, 190, 150, 240, 170, 210};
        for (int i = 0; i < bx.length; i++) {
            int top = 384 - bh[i];
            r(g, new Color(30, 32, 42), bx[i], top, 120, bh[i]);
            g.setColor(new Color(30, 32, 42));
            g.fillPolygon(new int[]{bx[i], bx[i] + 60 + (i % 3) * 20, bx[i] + 120}, new int[]{top, top - 24 - (i % 2) * 14, top}, 3);
            for (int k = 0; k < 2; k++) r(g, new Color(8, 8, 14), bx[i] + 18 + k * 56, top + 26, 26, 34);
            r(g, new Color(60, 44, 34), bx[i] + 14, top + 60, 40, 5);      // доски на окнах
        }
        r(g, new Color(40, 38, 44), 0, 384, W, H - 384);
        for (int x = 0; x < W; x += 70) r(g, new Color(52, 50, 58), x, 392, 60, 4);

        r(g, new Color(70, 52, 40), 100, 372, 40, 26);                     // мусор и бочка
        r(g, new Color(90, 66, 50), 130, 380, 30, 18);
        r(g, new Color(80, 60, 44), 690, 346, 44, 52);
        r(g, new Color(50, 40, 34), 690, 360, 44, 4);
        r(g, new Color(50, 40, 34), 690, 380, 44, 4);

        r(g, new Color(60, 60, 70), 876, 260, 6, 140);                     // далёкий фонарь, мерцает
        glow(g, 879, 258, 90, new Color(255, 210, 120), (t % 90 < 4) ? 20 : 70);

        // Эльза сидит у стены, обхватив колени
        Color hair = Theme.mix(ELSA_HAIR, Color.BLACK, 0.35);
        Color skin = Theme.mix(SKIN, new Color(120, 120, 130), 0.4);
        r(g, new Color(80, 60, 40), 436, 400, 24, 8);
        r(g, RAGS, 454, 346, 36, 52);
        r(g, Theme.mix(RAGS, Color.BLACK, 0.3), 444, 376, 56, 28);
        r(g, skin, 450, 388, 52, 6);
        r(g, skin, 456, 326, 28, 24);
        r(g, hair, 452, 318, 36, 14);
        r(g, hair, 452, 330, 8, 54);
        r(g, hair, 484, 330, 8, 44);

        g.setStroke(new BasicStroke(1));                                   // дождь
        g.setColor(new Color(170, 190, 230, 110));
        for (int i = 0; i < 90; i++) {
            int x = (i * 97 + t * 2) % W;
            int y = 40 + (i * 53 + t * 14) % (H - 40);
            g.drawLine(x, y, x - 4, y + 14);
        }
    }

    // ================= КАДР 7: РАЗРУШЕНИЕ =================
    private void destruction(Graphics2D g) {
        if (buf == null) buf = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        Graphics2D b = buf.createGraphics();
        drawDestroyScene(b);
        b.dispose();

        int amp = 3 + (int) (10 * Math.min(1.0, t / 200.0));
        Shape oldClip = g.getClip();
        for (int y = 0; y < H; y += 8) {                                   // искажение полосами
            int dx = (int) (Math.sin(y * 0.05 + t * 0.15) * amp);
            if ((y + t) % 61 < 8) dx += (((y * 7 + t / 3) % 2) == 0 ? 18 : -18);
            g.setClip(0, y, W, 8);
            g.drawImage(buf, dx, 0, null);
        }
        g.setClip(oldClip);
        Composite oc = g.getComposite();
        g.setComposite(AlphaComposite.SrcOver.derive(0.22f));              // призрачное двоение
        g.drawImage(buf, 7, 0, null);
        g.setComposite(oc);
    }

    private void drawDestroyScene(Graphics2D b) {
        b.setPaint(new GradientPaint(0, 0, new Color(30, 6, 24), 0, H, new Color(80, 14, 40)));
        b.fillRect(0, 0, W, H);
        r(b, new Color(24, 10, 22), 0, 384, W, H - 384);

        b.setStroke(new BasicStroke(3));                                   // кольца «голосов» вокруг Эльзы
        for (int i = 0; i < 4; i++) {
            int rad = 40 + (t * 2 + i * 45) % 220;
            int al = Math.max(0, 150 - rad * 150 / 260);
            b.setColor(new Color(220, 60, 90, al));
            b.drawOval(480 - rad, 330 - rad, rad * 2, rad * 2);
        }
        b.setStroke(new BasicStroke(1));

        Color blk = new Color(10, 4, 14);                                  // силуэты с белыми глазами
        for (int i = 0; i < 7; i++) {
            if (i == 3) continue;
            int cx = 100 + i * 130 + (int) (6 * Math.sin(t * 0.04 + i));
            int foot = 410 + (int) (8 * Math.sin(t * 0.05 + i * 1.3));
            person(b, cx, foot, 5 + (i % 2), blk, blk, blk, blk, new Color(240, 240, 255), i % 2 == 0, i % 3 == 0);
        }

        int sh = (int) (Math.sin(t * 0.9) * 2);                            // Эльза дрожит
        AffineTransform old = b.getTransform();
        b.translate(sh, 0);
        r(b, BOOT, 452, 404, 22, 10);
        r(b, BOOT, 486, 404, 22, 10);
        r(b, DARK, 456, 384, 48, 22);
        r(b, RED, 462, 336, 36, 50);
        r(b, GOLD, 462, 380, 36, 4);
        r(b, RED_DARK, 450, 300, 12, 46);
        r(b, RED_DARK, 498, 300, 12, 46);
        r(b, SKIN, 450, 290, 14, 14);
        r(b, SKIN, 496, 290, 14, 14);
        r(b, SKIN, 464, 306, 32, 30);
        r(b, ELSA_HAIR, 460, 298, 40, 14);
        r(b, ELSA_HAIR, 458, 306, 8, 34);
        r(b, ELSA_HAIR, 494, 306, 8, 34);
        r(b, DARK, 470, 320, 8, 2);
        r(b, DARK, 484, 320, 8, 2);
        b.setTransform(old);
    }

    // ================= КАДР 8: КНИГА =================
    private void book(Graphics2D g) {
        g.setPaint(new RadialGradientPaint(new Point2D.Float(W / 2f, 250f), 420f,
                new float[]{0f, 1f}, new Color[]{new Color(70, 40, 20), new Color(4, 2, 8)}));
        g.fillRect(0, 0, W, H);

        double pulse = 0.5 + 0.5 * Math.sin(t * 0.08);
        float z = 1f + Math.min(t, 400) / 400f * 0.10f;                    // медленный наезд
        AffineTransform old = g.getTransform();
        g.translate(W / 2, 250);
        g.scale(z, z);
        g.translate(-W / 2, -250);
        g.translate(0, (int) (Math.sin(t * 0.05) * 4));

        int bx = 290, by = 64, bw = 380, bh = 360, cx = bx + 34 + (bw - 34) / 2;
        g.setColor(new Color(0, 0, 0, 120));
        g.fillOval(300, by + bh + 4, 360, 30);
        r(g, new Color(236, 226, 200), bx + 6, by + bh, bw - 2, 10);       // обрез страниц
        r(g, new Color(236, 226, 200), bx + bw, by + 8, 10, bh);
        for (int i = 0; i < 4; i++) r(g, new Color(200, 190, 160), bx + bw + 2 + i * 2, by + 10, 1, bh - 4);
        r(g, new Color(72, 30, 92), bx, by, bw, bh);                       // обложка
        r(g, new Color(46, 18, 64), bx, by, 34, bh);                       // корешок
        for (int i = 0; i < 4; i++) r(g, GOLD, bx + 4, by + 30 + i * 80, 26, 4);
        g.setStroke(new BasicStroke(4));
        g.setColor(GOLD);
        g.drawRect(bx + 50, by + 20, bw - 70, bh - 40);
        g.setStroke(new BasicStroke(1));
        for (int ox : new int[]{bx + 44, bx + bw - 28})
            for (int oy : new int[]{by + 14, by + bh - 28}) r(g, GOLD, ox, oy, 14, 14);

        g.setStroke(new BasicStroke(2));                                   // эмблема: нимб и крест
        g.drawOval(cx - 14, by + 30, 28, 10);
        g.setStroke(new BasicStroke(1));
        r(g, GOLD, cx - 3, by + 44, 6, 36);
        r(g, GOLD, cx - 16, by + 54, 32, 6);

        g.setFont(new Font("Serif", Font.BOLD, 40));
        g.setColor(new Color(40, 16, 10));
        centerText(g, "PATH TO", cx + 2, by + 124);
        centerText(g, "HEAVEN", cx + 2, by + 170);
        g.setColor(GOLD);
        centerText(g, "PATH TO", cx, by + 122);
        centerText(g, "HEAVEN", cx, by + 168);

        // главный акцент — надпись "novel"
        glow(g, cx, by + 265, 190, new Color(255, 200, 60), (int) (60 + 70 * pulse));
        r(g, new Color(18, 8, 26), bx + 70, by + 212, bw - 110, 104);
        g.setStroke(new BasicStroke(3));
        g.setColor(new Color(240, 200, 60, (int) (140 + 110 * pulse)));
        g.drawRect(bx + 70, by + 212, bw - 111, 103);
        g.setStroke(new BasicStroke(1));
        g.setFont(new Font("Serif", Font.BOLD | Font.ITALIC, 82));
        for (int i = 4; i >= 1; i--) {                                     // свечение вокруг букв
            g.setColor(new Color(255, 210, 80, 28));
            centerText(g, "novel", cx - i, by + 292);
            centerText(g, "novel", cx + i, by + 292);
            centerText(g, "novel", cx, by + 292 - i);
            centerText(g, "novel", cx, by + 292 + i);
        }
        g.setColor(new Color(255, 240, 175));
        centerText(g, "novel", cx, by + 292);

        g.setTransform(old);
        for (int i = 0; i < 16; i++) {                                     // золотые пылинки
            int x = (i * 131 + t / 2) % W;
            int y = H - (t + i * 57) % H;
            r(g, new Color(255, 220, 110, 160), x, y, 3, 3);
        }
    }

    // ================= РИСОВАЛКИ =================
    // Человечек из прямоугольников: s — размер «пикселя», foot — линия ног, cx — центр. Высота ~22*s.
    private static void person(Graphics2D g, int cx, int foot, int s, Color cloth, Color trim, Color hair,
                               Color skin, Color eye, boolean longHair, boolean skirt) {
        int x0 = cx - 4 * s;
        g.setColor(new Color(0, 0, 0, 70));
        g.fillOval(cx - 6 * s, foot - s, 12 * s, 2 * s);
        Color pants = Theme.mix(cloth, Color.BLACK, 0.55);
        r(g, pants, x0 + s, foot - 6 * s, 2 * s, 6 * s);
        r(g, pants, x0 + 5 * s, foot - 6 * s, 2 * s, 6 * s);
        Color boot = new Color(48, 34, 30);
        r(g, boot, x0 + s - s / 2, foot - s, 3 * s, s);
        r(g, boot, x0 + 5 * s - s / 2, foot - s, 3 * s, s);
        if (skirt) r(g, cloth, x0 - s, foot - 9 * s, 10 * s, 7 * s);
        r(g, cloth, x0, foot - 15 * s, 8 * s, 9 * s);
        r(g, trim, x0, foot - 8 * s, 8 * s, s);
        r(g, cloth, x0 - 2 * s, foot - 15 * s, 2 * s, 7 * s);
        r(g, cloth, x0 + 8 * s, foot - 15 * s, 2 * s, 7 * s);
        r(g, skin, x0 - 2 * s, foot - 8 * s, 2 * s, 2 * s);
        r(g, skin, x0 + 8 * s, foot - 8 * s, 2 * s, 2 * s);
        r(g, skin, x0 + s, foot - 21 * s, 6 * s, 6 * s);
        r(g, hair, x0, foot - 22 * s, 8 * s, 3 * s);
        r(g, hair, x0, foot - 20 * s, s, longHair ? 9 * s : 3 * s);
        r(g, hair, x0 + 7 * s, foot - 20 * s, s, longHair ? 9 * s : 3 * s);
        r(g, eye, x0 + 2 * s, foot - 18 * s, s, s);
        r(g, eye, x0 + 5 * s, foot - 18 * s, s, s);
    }

    private static void smirk(Graphics2D g, int cx, int foot, int s) {
        r(g, new Color(120, 30, 40), cx - 2 * s, foot - 16 * s, 4 * s, Math.max(2, s / 2));
        r(g, new Color(120, 30, 40), cx + 2 * s, foot - 17 * s, s, s);
    }

    private static void window(Graphics2D g, int x, int y, int w, int h, Color light) {
        r(g, new Color(110, 70, 44), x - 4, y - 4, w + 8, h + 8);
        r(g, light, x, y, w, h);
        r(g, new Color(110, 70, 44), x + w / 2 - 2, y, 4, h);
        r(g, new Color(110, 70, 44), x, y + h / 2 - 2, w, 4);
    }

    private static void heart(Graphics2D g, int x, int y, Color c) {
        g.setColor(c);
        g.fillRect(x + 2, y, 6, 4);
        g.fillRect(x + 12, y, 6, 4);
        g.fillRect(x, y + 4, 20, 6);
        g.fillRect(x + 2, y + 10, 16, 4);
        g.fillRect(x + 6, y + 14, 8, 3);
    }

    private static void r(Graphics2D g, Color c, int x, int y, int w, int h) {
        g.setColor(c);
        g.fillRect(x, y, w, h);
    }

    private static void grad(Graphics2D g, Color top, Color bottom) {
        g.setPaint(new GradientPaint(0, 0, top, 0, H, bottom));
        g.fillRect(0, 0, W, H);
    }

    private static void glow(Graphics2D g, int cx, int cy, int rad, Color c, int a) {
        int al = Math.max(0, Math.min(255, a));
        RadialGradientPaint p = new RadialGradientPaint(new Point2D.Float(cx, cy), rad,
                new float[]{0f, 1f},
                new Color[]{new Color(c.getRed(), c.getGreen(), c.getBlue(), al),
                        new Color(c.getRed(), c.getGreen(), c.getBlue(), 0)});
        Paint old = g.getPaint();
        g.setPaint(p);
        g.fillRect(cx - rad, cy - rad, rad * 2, rad * 2);
        g.setPaint(old);
    }

    private static void centerText(Graphics2D g, String s, int cx, int y) {
        g.drawString(s, cx - g.getFontMetrics().stringWidth(s) / 2, y);
    }
}