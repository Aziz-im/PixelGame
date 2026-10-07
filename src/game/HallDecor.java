package game;

import java.awt.*;
import java.awt.geom.Point2D;

// Оформление коридоров между комнатами. Один и тот же коридор, но у каждого греха свои мрачные детали:
// ковровая дорожка, факелы, украшения на стенах (над дверями и между ними), мусор на полу и пылинки.
// Всё приглушённое и тёмное: цвета греха берутся из темы и сильно затемняются.
// loc: 1 Гнев, 2 Алчность, 3 Зависть, 4 Похоть, 5 Чревоугодие, 6 Уныние, 7 Гордыня.
final class HallDecor {
    private HallDecor() {}

    private static final int T = Room.TILE;
    private static final int TOP_WALL = 3, BOT_WALL = 8;       // ряды стен, примыкающих к полу
    private static final int FLOOR_TOP = 4, FLOOR_BOT = 7;     // ряды пола коридора
    private static final int[] TORCH_COLS = {5, 9, 17, 21};
    private static final int[] ORN_COLS = {2, 13, 25};

    static void draw(Graphics2D g0, int loc, Theme t, int c0, int c1, int cols, int tick) {
        Graphics2D g = (Graphics2D) g0.create();
        int a = Math.max(1, c0 - 1), b = Math.min(cols - 1, c1 + 1);

        shadows(g, a, b);
        carpet(g, t, cols);
        for (int col = a; col <= b; col++)
            for (int row = FLOOR_TOP; row <= FLOOR_BOT; row++) floorItem(g, loc, col, row, tick);

        for (int col : ORN_COLS) {
            if (col < a || col > b) continue;
            ornament(g, loc, col, TOP_WALL, false, tick);
            ornament(g, loc, col, BOT_WALL, true, tick);
        }
        for (int col : TORCH_COLS) {
            if (col < a || col > b) continue;
            torch(g, t, col, TOP_WALL, false, tick);
            torch(g, t, col, BOT_WALL, true, tick);
        }
        motes(g, t, a, b, tick);
        g.dispose();
    }

    // ---------- общее ----------
    private static void shadows(Graphics2D g, int a, int b) {
        int x = a * T, w = (b - a + 1) * T;
        g.setPaint(new GradientPaint(0, FLOOR_TOP * T, new Color(0, 0, 0, 120), 0, FLOOR_TOP * T + 22, new Color(0, 0, 0, 0)));
        g.fillRect(x, FLOOR_TOP * T, w, 22);
        g.setPaint(new GradientPaint(0, (FLOOR_BOT + 1) * T - 22, new Color(0, 0, 0, 0), 0, (FLOOR_BOT + 1) * T, new Color(0, 0, 0, 120)));
        g.fillRect(x, (FLOOR_BOT + 1) * T - 22, w, 22);
    }

    private static void carpet(Graphics2D g, Theme t, int cols) {
        Color base = Theme.mix(t.base, Color.BLACK, 0.80);
        Color trim = Theme.mix(t.accent, Color.BLACK, 0.68);
        Color pat = Theme.mix(t.base, Color.BLACK, 0.68);
        int x0 = 2 * T, x1 = (cols - 2) * T, y0 = 5 * T + 6, h = 2 * T - 12;
        g.setColor(base);
        g.fillRect(x0, y0, x1 - x0, h);
        g.setColor(trim);
        g.fillRect(x0, y0, x1 - x0, 3);
        g.fillRect(x0, y0 + h - 3, x1 - x0, 3);
        g.fillRect(x0 + 6, y0 + 8, x1 - x0 - 12, 1);
        g.fillRect(x0 + 6, y0 + h - 9, x1 - x0 - 12, 1);
        for (int x = x0 + 24; x < x1 - 24; x += T) {                // ромбы по дорожке
            g.setColor(pat);
            g.fillPolygon(new int[]{x, x + 12, x, x - 12}, new int[]{y0 + h / 2 - 12, y0 + h / 2, y0 + h / 2 + 12, y0 + h / 2}, 4);
            g.setColor(trim);
            g.fillRect(x - 1, y0 + h / 2 - 1, 3, 3);
        }
        for (int x = x0; x < x1; x += 7) {                          // бахрома по краям
            g.setColor(trim);
            if (x == x0 || x + 7 >= x1) for (int y = y0 + 2; y < y0 + h - 2; y += 4) g.fillRect(x == x0 ? x0 - 5 : x1, y, 5, 1);
        }
        g.setColor(new Color(0, 0, 0, 70));                         // потёртости
        for (int x = x0 + 70; x < x1 - 40; x += 211) g.fillOval(x, y0 + 14, 34, 12);
        for (int x = x0 + 150; x < x1 - 40; x += 257) g.fillOval(x, y0 + h - 28, 40, 12);
    }

    private static void torch(Graphics2D g, Theme t, int col, int wallRow, boolean flip, int tick) {
        int cx = col * T + T / 2, y0 = wallRow * T;
        // мягкий свет на полу
        Graphics2D gg = (Graphics2D) g.create();
        gg.clipRect(0, FLOOR_TOP * T, 40 * T, (FLOOR_BOT - FLOOR_TOP + 1) * T);
        int gy = flip ? y0 - 8 : y0 + T + 8;
        int fl = 6 + ((tick / 5 + col) % 3) * 2;
        gg.setPaint(new RadialGradientPaint(new Point2D.Float(cx, gy), 96 + fl,
                new float[]{0f, 1f},
                new Color[]{Theme.alpha(Theme.mix(t.accent, new Color(255, 160, 60), 0.5), 44), Theme.alpha(t.accent, 0)}));
        gg.fillRect(cx - 120, gy - 120, 240, 240);
        gg.dispose();

        int fy = flip ? y0 + 12 : y0 + T - 26;                      // поближе к полу
        int f = (tick / 4 + col * 3) % 3;
        g.setColor(new Color(10, 10, 12));                          // кронштейн
        g.fillRect(cx - 3, fy + 4, 6, 14);
        g.setColor(new Color(58, 56, 60));
        g.fillRect(cx - 5, fy + 2, 10, 4);
        g.fillRect(cx - 2, fy + 6, 4, 10);
        g.setColor(new Color(230, 130, 40));                        // пламя
        g.fillPolygon(new int[]{cx - 4, cx, cx + 4}, new int[]{fy + 2, fy - 8 - f, fy + 2}, 3);
        g.setColor(new Color(255, 226, 130));
        g.fillPolygon(new int[]{cx - 2, cx, cx + 2}, new int[]{fy + 2, fy - 3 - f, fy + 2}, 3);
        g.setColor(new Color(255, 170, 60, 40));                    // ореол на стене
        g.fillOval(cx - 14, fy - 14, 28, 28);
    }

    private static void motes(Graphics2D g, Theme t, int a, int b, int tick) {
        for (int i = 0; i < 44; i++) {
            int x = (int) ((i * 97 + tick * (0.15 + (i % 4) * 0.05)) % (30 * T));
            if (x < a * T || x > (b + 1) * T) continue;
            int y = FLOOR_TOP * T + 10 + (i * 53) % (4 * T - 20) + (int) (Math.sin(tick * 0.02 + i) * 6);
            int al = 40 + (int) (30 * Math.sin(tick * 0.05 + i));
            g.setColor(Theme.alpha(Theme.mix(t.accent, Color.WHITE, 0.2), Math.max(8, al)));
            g.fillRect(x, y, 2, 2);
        }
    }

    // ---------- мусор и детали на полу (детерминированно, без Random) ----------
    private static void floorItem(Graphics2D g, int loc, int col, int row, int tick) {
        int h = Math.floorMod(col * 73 + row * 151 + loc * 31, 19);
        if (h > 2) return;
        int x = col * T + 6 + (col * 17 + row * 5) % 22, y = row * T + 8 + (col * 11 + row * 7) % 22;
        switch (loc) {
            case 1 -> {                                             // трещина с тлеющим краем
                g.setColor(new Color(8, 6, 6));
                g.fillPolygon(new int[]{x, x + 10, x + 16, x + 26, x + 24, x + 14, x + 8}, new int[]{y, y + 2, y + 8, y + 10, y + 13, y + 11, y + 5}, 7);
                g.setColor(new Color(150, 40, 28, 90 + (int) (30 * Math.sin(tick * 0.08 + col))));
                g.fillRect(x + 8, y + 5, 9, 2);
                g.fillRect(x + 16, y + 8, 6, 2);
            }
            case 2 -> {                                             // рассыпанные потускневшие монеты
                for (int i = 0; i < 3; i++) {
                    g.setColor(new Color(128, 100, 34));
                    g.fillOval(x + i * 9, y + (i % 2) * 6, 8, 5);
                    g.setColor(new Color(176, 146, 60));
                    g.fillRect(x + i * 9 + 2, y + (i % 2) * 6 + 1, 3, 1);
                }
            }
            case 3 -> {                                             // мох и бледные грибы
                g.setColor(new Color(24, 58, 34, 190));
                g.fillOval(x - 2, y, 26, 12);
                g.fillOval(x + 8, y + 6, 18, 9);
                g.setColor(new Color(90, 170, 110, 120));
                g.fillRect(x + 6, y + 2, 2, 3);
                g.fillRect(x + 15, y + 5, 2, 3);
            }
            case 4 -> {                                             // увядшие лепестки
                for (int i = 0; i < 4; i++) {
                    g.setColor(new Color(112, 30, 70, 190));
                    g.fillOval(x + i * 7, y + (i * 5) % 9, 6, 4);
                }
                g.setColor(new Color(60, 14, 38));
                g.fillRect(x + 12, y + 12, 5, 1);
            }
            case 5 -> {                                             // жирное пятно, кость, тарелка
                g.setColor(new Color(60, 36, 16, 150));
                g.fillOval(x - 2, y, 28, 13);
                g.setColor(new Color(164, 150, 124));
                g.fillRect(x + 2, y + 4, 14, 3);
                g.fillOval(x, y + 3, 5, 5);
                g.fillOval(x + 13, y + 3, 5, 5);
                if (h == 0) {
                    g.setColor(new Color(92, 88, 92));
                    g.fillOval(x + 14, y - 4, 16, 8);
                    g.setColor(new Color(60, 58, 62));
                    g.fillOval(x + 18, y - 3, 8, 4);
                }
            }
            case 6 -> {                                             // кучки пыли и обрывок одеяла
                g.setColor(new Color(70, 80, 92, 130));
                g.fillOval(x, y + 2, 26, 10);
                g.fillOval(x + 6, y - 2, 16, 8);
                if (h == 1) {
                    g.setColor(new Color(54, 62, 78));
                    g.fillRect(x - 4, y + 12, 24, 7);
                    g.setColor(new Color(40, 46, 60));
                    g.fillRect(x - 4, y + 16, 24, 2);
                }
            }
            default -> {                                            // осколки зеркала и обломок короны
                g.setColor(new Color(130, 160, 200, 120));
                g.fillPolygon(new int[]{x, x + 9, x + 5}, new int[]{y + 8, y + 6, y}, 3);
                g.fillPolygon(new int[]{x + 12, x + 22, x + 16}, new int[]{y + 10, y + 12, y + 3}, 3);
                if (h == 0) {
                    g.setColor(new Color(150, 120, 40));
                    g.fillRect(x + 4, y + 14, 12, 3);
                    g.fillPolygon(new int[]{x + 4, x + 7, x + 10}, new int[]{y + 14, y + 8, y + 14}, 3);
                    g.fillPolygon(new int[]{x + 10, x + 13, x + 16}, new int[]{y + 14, y + 8, y + 14}, 3);
                }
            }
        }
    }

    // ---------- украшения на стенах ----------
    private static void ornament(Graphics2D g0, int loc, int col, int wallRow, boolean flip, int tick) {
        Graphics2D g = (Graphics2D) g0.create();
        int x = col * T;
        if (flip) { g.translate(x, wallRow * T + T); g.scale(1, -1); }
        else g.translate(x, wallRow * T);
        switch (loc) {
            case 1 -> wrath(g, col, tick);
            case 2 -> greed(g);
            case 3 -> envy(g, col, tick);
            case 4 -> lust(g);
            case 5 -> gluttony(g, col, tick);
            case 6 -> sloth(g);
            default -> pride(g);
        }
        g.dispose();
    }

    // Гнев: щит со скрещёнными мечами и тёмные потёки
    private static void wrath(Graphics2D g, int col, int tick) {
        g.setColor(new Color(60, 14, 14, 150));
        g.fillRect(10, 0, 3, 18);
        g.fillRect(34, 0, 2, 12);
        Stroke os = g.getStroke();
        g.setStroke(new BasicStroke(3));
        g.setColor(new Color(104, 108, 118));
        g.drawLine(8, 10, 40, 42);
        g.drawLine(40, 10, 8, 42);
        g.setStroke(os);
        g.setColor(new Color(80, 20, 20));
        g.fillRect(5, 7, 6, 4);
        g.fillRect(37, 7, 6, 4);
        g.setColor(new Color(58, 20, 22));
        g.fillOval(14, 12, 20, 24);
        g.setColor(new Color(120, 36, 30));
        g.fillOval(20, 20, 8, 8);
        g.setColor(new Color(190, 70, 40, 60 + (int) (25 * Math.sin(tick * 0.07 + col))));
        g.fillOval(22, 22, 4, 4);
    }

    // Алчность: ниша с тусклыми слитками и мешок
    private static void greed(Graphics2D g) {
        g.setColor(new Color(6, 5, 3));
        g.fillRect(6, 4, 36, 38);
        g.setColor(new Color(60, 48, 20));
        g.drawRect(6, 4, 36, 38);
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c <= r; c++) {
                int bx = 24 - r * 7 + c * 14 - 7, by = 12 + r * 7;
                g.setColor(new Color(120, 94, 30));
                g.fillRect(bx, by, 14, 6);
                g.setColor(new Color(176, 146, 58));
                g.fillRect(bx + 1, by, 12, 1);
            }
        }
        g.setColor(new Color(70, 52, 30));                          // мешок
        g.fillOval(10, 30, 14, 12);
        g.fillRect(14, 28, 6, 4);
        g.setColor(new Color(150, 120, 40));
        g.fillRect(15, 33, 4, 3);
    }

    // Зависть: плющ и два глаза в темноте
    private static void envy(Graphics2D g, int col, int tick) {
        g.setColor(new Color(4, 8, 6));
        g.fillRect(8, 10, 32, 24);
        g.setColor(new Color(30, 70, 40));
        for (int vx : new int[]{6, 16, 30, 41}) {
            int len = 14 + (vx * 5) % 22;
            g.fillRect(vx, 0, 2, len);
            for (int y = 6; y < len; y += 8) g.fillOval(vx + ((y / 8) % 2 == 0 ? -4 : 1), y, 6, 4);
        }
        boolean closed = (tick / 6 + col) % 40 < 3;
        g.setColor(new Color(110, 230, 140, 200));
        if (closed) {
            g.fillRect(14, 22, 7, 1);
            g.fillRect(27, 22, 7, 1);
        } else {
            g.fillOval(14, 19, 7, 5);
            g.fillOval(27, 19, 7, 5);
            g.setColor(Color.BLACK);
            g.fillRect(17, 20, 2, 3);
            g.fillRect(30, 20, 2, 3);
        }
    }

    // Похоть: тяжёлая бархатная драпировка с кистью
    private static void lust(Graphics2D g) {
        Color c = new Color(84, 22, 54), dk = new Color(52, 12, 34);
        g.setColor(c);
        g.fillPolygon(new int[]{4, 44, 40, 24, 8}, new int[]{4, 4, 30, 38, 30}, 5);
        g.setColor(dk);
        for (int x = 10; x < 42; x += 7) g.fillRect(x, 6, 2, 26);
        g.setColor(new Color(140, 100, 40));
        g.fillRect(2, 2, 44, 4);
        g.setColor(new Color(150, 110, 44));
        g.fillRect(22, 36, 4, 3);
        g.fillRect(21, 39, 6, 5);
        g.setColor(dk);
        g.fillRect(2, 24, 6, 18);
        g.fillRect(40, 24, 6, 18);
    }

    // Чревоугодие: крюк с подвешенным окороком
    private static void gluttony(Graphics2D g, int col, int tick) {
        int sw = (int) (Math.sin(tick * 0.04 + col) * 2);
        g.setColor(new Color(80, 82, 88));
        g.fillRect(23 + sw / 2, 0, 2, 10);
        g.fillRect(21 + sw, 10, 6, 2);
        g.setColor(new Color(120, 58, 48));
        g.fillOval(12 + sw, 12, 24, 26);
        g.setColor(new Color(84, 36, 32));
        g.fillOval(18 + sw, 22, 14, 14);
        g.setColor(new Color(150, 84, 70));
        g.fillRect(18 + sw, 16, 6, 2);
        g.setColor(new Color(180, 168, 140));
        g.fillRect(22 + sw, 36, 4, 6);
        g.setColor(new Color(60, 30, 14, 120));
        g.fillRect(24, 42, 2, 4);
    }

    // Уныние: остановившиеся часы и паутина
    private static void sloth(Graphics2D g) {
        g.setColor(new Color(40, 46, 58));
        g.fillOval(8, 6, 32, 32);
        g.setColor(new Color(112, 122, 136));
        g.fillOval(11, 9, 26, 26);
        g.setColor(new Color(30, 34, 44));
        g.fillRect(23, 14, 2, 10);
        g.fillRect(23, 22, 8, 2);
        g.fillRect(23, 9, 2, 2);
        g.fillRect(23, 33, 2, 2);
        g.fillRect(11, 21, 2, 2);
        g.fillRect(35, 21, 2, 2);
        g.setColor(new Color(160, 170, 182, 90));                   // паутина в углу
        g.drawLine(0, 0, 18, 6);
        g.drawLine(0, 0, 12, 16);
        g.drawLine(0, 0, 4, 20);
        g.drawArc(-8, -8, 24, 24, 270, 90);
        g.drawLine(48, 0, 36, 4);
        g.drawLine(48, 0, 40, 14);
    }

    // Гордыня: тёмно-синее знамя с золотой короной и трещина в раме
    private static void pride(Graphics2D g) {
        g.setColor(new Color(22, 30, 70));
        g.fillRect(8, 2, 32, 34);
        g.fillPolygon(new int[]{8, 24, 40}, new int[]{36, 46, 36}, 3);
        g.setColor(new Color(130, 104, 36));
        g.fillRect(8, 2, 32, 3);
        g.fillRect(8, 2, 2, 34);
        g.fillRect(38, 2, 2, 34);
        g.setColor(new Color(170, 138, 44));                        // корона
        g.fillRect(15, 22, 18, 5);
        g.fillPolygon(new int[]{15, 18, 21}, new int[]{22, 12, 22}, 3);
        g.fillPolygon(new int[]{21, 24, 27}, new int[]{22, 9, 22}, 3);
        g.fillPolygon(new int[]{27, 30, 33}, new int[]{22, 12, 22}, 3);
        g.setColor(new Color(10, 12, 28));                          // трещина
        g.drawLine(32, 4, 26, 16);
        g.drawLine(26, 16, 30, 26);
    }
}
