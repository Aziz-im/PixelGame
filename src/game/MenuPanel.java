package game;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.util.Random;

// МЕНЮ ПАУЗЫ (открывается по ESC во время игры): «рай сверху, ад снизу».
// Весь фон и кнопки рисуются в маленькую картинку 320x192 и растягиваются в 3 раза без сглаживания —
// получается честный пиксель-арт. Текст рисуется поверх уже в полном разрешении.
public class MenuPanel extends JPanel {

    private static final int S = 3;                                   // увеличение пикселя
    private static final int W = Room.COLS * Room.TILE;               // 960
    private static final int H = Room.ROWS * Room.TILE;               // 576
    private static final int LW = W / S, LH = H / S;                  // 320 x 192 — «холст» пиксель-арта

    // ===== НАСТРОЙКИ: положение кнопок (в пикселях арта, умножай на 3 для экрана) =====
    private static final Rectangle PLAY_R = new Rectangle(105, 82, 110, 22);
    private static final Rectangle EXIT_R = new Rectangle(105, 114, 110, 22);

    // ===== НАСТРОЙКИ: надписи и шрифты =====
    private static final String TITLE = "Семь смертных грехов";
    private static final int TITLE_Y = 188;                           // базовая линия заголовка (экранные px)
    private static final Font TITLE_FONT = new Font("Courier New", Font.BOLD, 50);
    private static final Font BTN_FONT = new Font("Courier New", Font.BOLD, 26);

    private static final int EMBERS = 46;

    private final GameWindow window;
    private final BufferedImage art = new BufferedImage(LW, LH, BufferedImage.TYPE_INT_RGB);
    private final javax.swing.Timer timer;
    private final Random rnd = new Random(7);
    private final float[] ex = new float[EMBERS], ey = new float[EMBERS], es = new float[EMBERS];

    private int tick;
    private int sel = 0;            // 0 — ИГРАТЬ, 1 — ВЫХОД (подсвечена мышью или стрелками)
    private int pressedIdx = -1;
    private long shownAt;           // когда меню появилось (чтобы то же нажатие ESC не закрыло его сразу)

    public MenuPanel(GameWindow window) {
        this.window = window;
        setPreferredSize(new Dimension(W, H));
        setBackground(Color.BLACK);
        setFocusable(true);

        for (int i = 0; i < EMBERS; i++) {
            ex[i] = rnd.nextFloat() * LW;
            ey[i] = rnd.nextFloat() * LH;
            es[i] = 0.25f + rnd.nextFloat() * 0.6f;
        }

        timer = new javax.swing.Timer(33, e -> {
            tick++;
            moveEmbers();
            repaint();
        });
        // анимация крутится только пока меню на экране
        addHierarchyListener(e -> {
            if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0) {
                if (isShowing()) { shownAt = System.currentTimeMillis(); timer.start(); requestFocusInWindow(); } else timer.stop();
            }
        });

        MouseAdapter mouse = new MouseAdapter() {
            @Override public void mouseMoved(MouseEvent e) {
                int i = hit(e.getPoint());
                if (i >= 0) sel = i;
                setCursor(Cursor.getPredefinedCursor(i >= 0 ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
            }
            @Override public void mousePressed(MouseEvent e) { pressedIdx = hit(e.getPoint()); }
            @Override public void mouseReleased(MouseEvent e) {
                int i = hit(e.getPoint());
                if (i >= 0 && i == pressedIdx) activate(i);
                pressedIdx = -1;
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);

        bind("UP", () -> sel = 0);
        bind("W", () -> sel = 0);
        bind("DOWN", () -> sel = 1);
        bind("S", () -> sel = 1);
        bind("ENTER", () -> activate(sel));
        bind("SPACE", () -> activate(sel));
        bind("ESCAPE", () -> { if (System.currentTimeMillis() - shownAt > 300) activate(0); });        // ещё раз ESC — вернуться в игру
    }

    private void bind(String key, Runnable r) {
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(key), key);
        getActionMap().put(key, new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { if (isShowing()) r.run(); }
        });
    }

    private void activate(int i) {
        if (i == 0) window.resumeGame();          // ИГРАТЬ — продолжить с того же места
        else window.exitToMainMenu();             // ВЫХОД — в главное меню (игра запоминается)
    }

    private int hit(Point screen) {
        Point p = View.toLogical(this, screen);
        if (scaled(PLAY_R).contains(p)) return 0;
        if (scaled(EXIT_R).contains(p)) return 1;
        return -1;
    }

    private static Rectangle scaled(Rectangle r) {
        return new Rectangle(r.x * S, r.y * S, r.width * S, r.height * S);
    }

    private void moveEmbers() {
        for (int i = 0; i < EMBERS; i++) {
            ey[i] -= es[i];
            ex[i] += (float) Math.sin(tick * 0.05 + i) * 0.25f;
            if (ey[i] < -2) { ey[i] = LH + 2; ex[i] = rnd.nextFloat() * LW; }
        }
    }

    // ======================================================================
    //                              ОТРИСОВКА
    // ======================================================================
    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);

        Graphics2D a = art.createGraphics();
        a.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        drawSky(a);
        drawRays(a);
        drawGate(a);          // ВРАТА В РАЙ (сверху)
        drawFloor(a);         // ПЕНТАГРАММА + КРЕСТ + СВЕЧИ (снизу)
        drawEmbers(a);
        drawExit(a, sel == 1, pressedIdx == 1);   // кнопка-рай с крыльями
        drawPlay(a, sel == 0, pressedIdx == 0);   // кнопка-ад с рогами
        drawVignette(a);
        drawForeground(a);    // облака в углах и адское пламя по краям
        a.dispose();

        Graphics2D g = (Graphics2D) g0.create();
        View.apply(g, this);                                          // масштаб под размер окна
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(art, 0, 0, W, H, null);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        drawTitle(g);
        drawLabels(g);
        g.dispose();
    }

    // ---------- небо: сверху райские сумерки, к низу — чёрный и кроваво-красный ----------
    private void drawSky(Graphics2D a) {
        for (int y = 0; y < LH; y += 4) {
            float t = y / (float) LH;
            Color c = t < 0.45f
                    ? Theme.mix(new Color(54, 42, 100), new Color(14, 8, 18), t / 0.45f)
                    : Theme.mix(new Color(14, 8, 18), new Color(62, 4, 6), (t - 0.45f) / 0.55f);
            rect(a, c, 0, y, LW, 4);
        }
        for (int i = 0; i < 26; i++) {                                // звёзды наверху
            if ((tick / 14 + i) % 6 == 0) continue;
            rect(a, new Color(235, 230, 255, 170), (i * 53) % LW, (i * 29) % 38 + 2, 1, 1);
        }
        glow(a, 160, 28, 95, new Color(255, 225, 150), 0.30f);       // сияние рая
    }

    // ---------- лучи света от врат ----------
    private void drawRays(Graphics2D a) {
        int al = 54 + (int) (22 * (0.5 + 0.5 * Math.sin(tick * 0.04)));
        Color top = new Color(244, 224, 150, al), none = new Color(244, 224, 150, 0);
        a.setPaint(new GradientPaint(0, 34, top, 0, 118, none));
        a.fillPolygon(new int[]{152, 168, 252, 208}, new int[]{34, 34, 125, 125}, 4);
        a.fillPolygon(new int[]{152, 168, 112, 68}, new int[]{34, 34, 125, 125}, 4);
        a.setPaint(new GradientPaint(0, 40, new Color(244, 224, 150, al - 14), 0, 132, none));
        a.fillPolygon(new int[]{156, 164, 182, 138}, new int[]{40, 40, 132, 132}, 4);
    }

    // ---------- ВРАТА В РАЙ ----------
    private void drawGate(Graphics2D a) {
        int cx = 160;
        glow(a, cx, 26, 46, new Color(255, 240, 180), 0.55f + 0.1f * (float) Math.sin(tick * 0.08));

        // золотая арка и проём
        Area outer = new Area(new Ellipse2D.Float(cx - 27, 1, 54, 54));
        outer.add(new Area(new Rectangle(cx - 27, 28, 54, 14)));
        Area door = new Area(new Ellipse2D.Float(cx - 22, 6, 44, 44));
        door.add(new Area(new Rectangle(cx - 22, 28, 44, 14)));

        a.setColor(new Color(150, 98, 18));
        a.fill(new Area(new Ellipse2D.Float(cx - 28, 0, 56, 56)));    // тёмный контур
        a.fill(new Rectangle(cx - 28, 28, 56, 14));
        a.setColor(new Color(226, 172, 52));
        a.fill(outer);
        a.setColor(new Color(255, 232, 140));
        a.fill(new Rectangle(cx - 27, 28, 2, 14));                    // блики на колоннах
        a.fill(new Rectangle(cx + 25, 28, 2, 14));

        a.setPaint(new GradientPaint(0, 6, new Color(255, 255, 248), 0, 42, new Color(255, 214, 110)));
        a.fill(door);

        Shape old = a.getClip();
        a.setClip(door);
        for (int side = -1; side <= 1; side += 2) {                   // створки с золотыми прутьями
            int x0 = side < 0 ? cx - 22 : cx + 10;
            rect(a, new Color(120, 78, 14), x0, 14, 12, 28);
            for (int bx = x0 + 1; bx < x0 + 12; bx += 3) rect(a, new Color(244, 196, 70), bx, 14, 2, 28);
            rect(a, new Color(255, 232, 140), x0, 26, 12, 1);
        }
        a.setClip(old);
        // искра света в проёме
        int sp = 2 + (int) Math.round(1.5 * Math.sin(tick * 0.15));
        rect(a, new Color(255, 255, 255), cx - sp, 24, sp * 2 + 1, 1);
        rect(a, new Color(255, 255, 255), cx, 24 - sp, 1, sp * 2 + 1);

        // ступени из облаков
        rect(a, new Color(238, 232, 255), cx - 34, 42, 68, 2);
        rect(a, new Color(214, 206, 244), cx - 40, 44, 80, 2);
        int d = (int) Math.round(2 * Math.sin(tick * 0.02));
        cloud(a, 118 + d, 41, 34);
        cloud(a, 202 - d, 41, 34);
    }

    // ---------- ПЕНТАГРАММА на полу, красный крест, свечи ----------
    private void drawFloor(Graphics2D a) {
        final int cx = 160, cy = 173, rx = 105, ry = 17;

        AffineTransform t0 = a.getTransform();                        // красное свечение пола
        a.translate(cx, cy);
        a.scale(1, 0.32);
        glow(a, 0, 0, 125, new Color(255, 40, 10), 0.55f);
        a.setTransform(t0);

        a.setStroke(new BasicStroke(1f));
        a.setColor(new Color(110, 8, 8));
        a.drawOval(cx - rx - 7, cy - ry - 3, 2 * (rx + 7), 2 * (ry + 3));
        a.setColor(new Color(215, 30, 22));
        a.drawOval(cx - rx, cy - ry, 2 * rx, 2 * ry);
        for (int i = 0; i < 48; i++) {                                // руны между кольцами
            double ang = i * Math.PI / 24;
            int rxm = (int) (cx + (rx + 3.5) * Math.cos(ang));
            int rym = (int) (cy + (ry + 1.5) * Math.sin(ang));
            rect(a, i % 2 == 0 ? new Color(190, 25, 20) : new Color(120, 10, 10), rxm, rym, 1, 1);
        }

        int[] px = new int[5], py = new int[5];
        for (int k = 0; k < 5; k++) {
            double ang = Math.toRadians(-90 + 72 * k);
            px[k] = (int) Math.round(cx + rx * Math.cos(ang));
            py[k] = (int) Math.round(cy + ry * Math.sin(ang));
        }
        for (int k = 0; k < 5; k++) {                                 // звезда
            int j = (k + 2) % 5;
            a.setColor(new Color(120, 6, 6));
            a.drawLine(px[k], py[k] + 1, px[j], py[j] + 1);
            a.setColor(new Color(235, 40, 28));
            a.drawLine(px[k], py[k], px[j], py[j]);
        }

        drawCross(a, cx, 159);                                        // перевёрнутый красный крест в центре

        for (int k : new int[]{0, 1, 4, 2, 3}) candle(a, px[k], py[k], k * 1.7);   // свеча на каждом конце звезды
    }

    private void drawCross(Graphics2D a, int cx, int top) {
        float f = 0.5f + 0.5f * (float) Math.sin(tick * 0.12);
        glow(a, cx, top + 12, 24, new Color(255, 30, 20), 0.45f + 0.15f * f);
        Color dark = new Color(110, 4, 8), mid = new Color(200, 18, 26), hi = new Color(255, 90, 70);
        rect(a, dark, cx - 3, top - 1, 6, 28);
        rect(a, dark, cx - 9, top + 14, 18, 6);
        rect(a, mid, cx - 2, top, 4, 26);
        rect(a, mid, cx - 8, top + 15, 16, 4);
        rect(a, hi, cx - 2, top, 1, 26);
        rect(a, hi, cx - 8, top + 15, 16, 1);
    }

    private void candle(Graphics2D a, int x, int y, double ph) {
        double f = Math.sin(tick * 0.33 + ph) * 0.5 + Math.sin(tick * 0.17 + ph * 2) * 0.5;
        glow(a, x, y - 17, 17 + (float) f * 2, new Color(255, 170, 60), 0.55f);
        rect(a, new Color(60, 6, 6), x - 4, y - 1, 8, 2);
        rect(a, new Color(236, 226, 196), x - 2, y - 12, 4, 12);      // воск
        rect(a, new Color(255, 250, 225), x - 2, y - 12, 1, 12);
        rect(a, new Color(190, 172, 132), x + 1, y - 12, 1, 12);
        rect(a, new Color(255, 245, 215), x - 2, y - 12, 4, 1);
        rect(a, new Color(170, 20, 20), x - 1, y - 11, 1, 4);         // красный подтёк
        rect(a, new Color(40, 30, 30), x, y - 14, 1, 2);              // фитиль
        int fh = 5 + (int) Math.round(f), fx = (int) Math.round(f * 0.8);
        int top = y - 14 - fh;
        a.setColor(new Color(255, 130, 25));
        a.fillOval(x - 2 + fx, top, 4, fh + 2);
        a.setColor(new Color(255, 236, 140));
        a.fillOval(x - 1 + fx, top + fh / 2 + 1, 2, fh / 2 + 1);
    }

    // ---------- искры ----------
    private void drawEmbers(Graphics2D a) {
        for (int i = 0; i < EMBERS; i++) {
            if ((i + tick / 3) % 4 == 0) continue;
            a.setColor(i % 3 == 0 ? new Color(255, 230, 120) : new Color(255, 120, 30));
            a.fillRect((int) ex[i], (int) ey[i], 1, 1);
            if (i % 5 == 0) a.fillRect((int) ex[i], (int) ey[i] + 1, 1, 1);
        }
    }

    // ======================================================================
    //                              КНОПКИ
    // ======================================================================

    // ИГРАТЬ — «адская»: кирпичи, рожки демона сверху
    private void drawPlay(Graphics2D a, boolean hot, boolean down) {
        Rectangle r = PLAY_R;
        int x = r.x, y = r.y + (down ? 1 : 0), w = r.width, h = r.height;

        horn(a, x + 15, y, -1, hot);
        horn(a, x + w - 15, y, 1, hot);
        if (hot) glow(a, x + w / 2f, y + h / 2f, 90, new Color(255, 60, 20), 0.35f + 0.1f * (float) Math.sin(tick * 0.2));

        rect(a, new Color(0, 0, 0, 150), x + 1, y + 3, w, h);
        rect(a, new Color(60, 10, 6), x - 1, y - 1, w + 2, h + 2);
        rect(a, hot ? new Color(255, 160, 60) : new Color(236, 120, 40), x, y, w, h);
        rect(a, new Color(70, 10, 8), x + 1, y + 1, w - 2, h - 2);

        Shape old = a.getClip();
        a.setClip(x + 2, y + 2, w - 4, h - 4);
        int row = 0;
        for (int by = y + 2; by < y + h - 2; by += 5, row++) {
            int off = (row % 2) * 7;
            for (int bx = x + 2 - off; bx < x + w - 2; bx += 14) {
                int v = ((bx * 31 + row * 17) & 15) - 8;
                int up = hot ? 26 : 0;
                Color c = new Color(clamp(172 + v + up), clamp(40 + v / 2 + up / 2), clamp(28 + v / 2));
                rect(a, c, bx, by, 13, 4);
                rect(a, new Color(clamp(c.getRed() + 45), clamp(c.getGreen() + 45), clamp(c.getBlue() + 30)), bx, by, 13, 1);
                rect(a, new Color(clamp(c.getRed() - 60), clamp(c.getGreen() - 20), clamp(c.getBlue() - 10)), bx, by + 3, 13, 1);
            }
        }
        a.setClip(old);
    }

    // рог демона; dir = -1 левый, +1 правый
    private void horn(Graphics2D a, int bx, int by, int dir, boolean hot) {
        final int n = 16;
        if (hot) glow(a, bx + dir * 5, by - n / 2f, 14, new Color(255, 80, 30), 0.55f);
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < n; i++) {
                int y = by - 1 - i;
                int w = Math.max(2, 8 - i / 2);
                int x = bx + (int) Math.round(dir * 9 * Math.sin(i * 0.19)) - w / 2;
                if (pass == 0) {
                    rect(a, new Color(18, 2, 3), x - 1, y - 1, w + 2, 3);
                } else {
                    Color base = i >= 12 ? new Color(244, 214, 160)
                            : Theme.mix(new Color(112, 22, 22), new Color(188, 44, 36), i / 11.0);
                    if (hot) base = Theme.mix(base, new Color(255, 110, 50), 0.35);
                    rect(a, base, x, y, w, 1);
                    if (i < 12) rect(a, new Color(232, 96, 70), dir < 0 ? x : x + w - 1, y, 1, 1);   // блик
                    if (i % 4 == 1 && i < 12) rect(a, new Color(80, 12, 14), dir < 0 ? x + w - 1 : x, y, 1, 1);   // кольца на роге
                }
            }
        }
    }

    // ВЫХОД — «райская»: золото, белая рамка, крылья по бокам
    private void drawExit(Graphics2D a, boolean hot, boolean down) {
        Rectangle r = EXIT_R;
        int x = r.x, y = r.y + (down ? 1 : 0), w = r.width, h = r.height;
        double flap = hot ? 12 * Math.sin(tick * 0.22) : 3 * Math.sin(tick * 0.07);

        wing(a, x + 3, y + h / 2 + 1, -1, flap);
        wing(a, x + w - 3, y + h / 2 + 1, 1, flap);
        if (hot) glow(a, x + w / 2f, y + h / 2f, 95, new Color(255, 235, 150), 0.45f);

        rect(a, new Color(0, 0, 0, 110), x + 1, y + 3, w, h);
        rect(a, Color.WHITE, x + 1, y, w - 2, h);                     // белая рамка со срезанными углами
        rect(a, Color.WHITE, x, y + 1, w, h - 2);
        rect(a, new Color(232, 176, 62), x + 1, y + 1, w - 2, h - 2);
        for (int yy = 0; yy < h - 4; yy++) {
            Color top = hot ? new Color(255, 250, 205) : new Color(255, 242, 184);
            rect(a, Theme.mix(top, new Color(240, 184, 72), yy / (float) (h - 5)), x + 2, y + 2 + yy, w - 4, 1);
        }
        rect(a, new Color(255, 255, 240), x + 3, y + 2, w - 6, 1);
    }

    // крыло из двух слоёв перьев; dir = -1 левое, +1 правое
    private void wing(Graphics2D a, int ax, int ay, int dir, double flapDeg) {
        final int n = 6;
        for (int layer = 0; layer < 2; layer++) {
            for (int i = n - 1; i >= 0; i--) {
                double t = i / (double) (n - 1);
                double ang = Math.toRadians(-64 + 84 * t + flapDeg * (1 - t));
                double vx = dir * Math.cos(ang), vy = Math.sin(ang);
                double len = (31 - i * 3.3) * (layer == 0 ? 1.0 : 0.6);
                double wd = layer == 0 ? 6 : 5;
                AffineTransform at = new AffineTransform();
                at.translate(ax, ay);
                at.rotate(Math.atan2(vy, vx));
                a.setColor(new Color(140, 152, 205));
                a.fill(at.createTransformedShape(new Ellipse2D.Double(-1, -wd / 2 - 1, len + 2, wd + 2)));
                a.setColor(layer == 0 ? new Color(226, 232, 255) : new Color(252, 252, 255));
                a.fill(at.createTransformedShape(new Ellipse2D.Double(0, -wd / 2, len, wd)));
            }
        }
    }

    // ======================================================================
    //                    ПЕРЕДНИЙ ПЛАН: углы, пламя, виньетка
    // ======================================================================
    private void drawVignette(Graphics2D a) {
        a.setPaint(new RadialGradientPaint(new Point2D.Float(LW / 2f, LH / 2f), LW * 0.62f,
                new float[]{0.5f, 1f}, new Color[]{new Color(0, 0, 0, 0), new Color(0, 0, 0, 150)}));
        a.fillRect(0, 0, LW, LH);
    }

    private void drawForeground(Graphics2D a) {
        cloud(a, 14, 3, 64);
        cloud(a, 52, 0, 48);
        cloud(a, 306, 4, 56);
        cloud(a, 272, 0, 44);

        for (int x = 0; x < LW; x++) {                                // пламя: высокое в углах, лава по низу
            int c = x / 2;
            double edge = Math.min(x, LW - 1 - x) / 62.0;
            double prof = Math.max(0.0, 1 - edge);
            double n = 0.5 + 0.5 * Math.sin(c * 1.7 + tick * 0.35) * Math.sin(c * 0.63 - tick * 0.21 + 2);
            int h = 3 + (int) (prof * prof * 28 * (0.5 + 0.5 * n));
            int h1 = Math.max(1, h * 35 / 100), h2 = Math.max(1, h * 65 / 100);
            rect(a, new Color(190, 36, 14), x, LH - h, 1, h);
            rect(a, new Color(255, 146, 30), x, LH - h2, 1, h2);
            rect(a, new Color(255, 236, 140), x, LH - h1, 1, h1);
        }
    }

    // ======================================================================
    //                    ТЕКСТ (в полном разрешении)
    // ======================================================================

    // «Семь смертных грехов»: левая половина — райская (золото и свет), правая — адская (огонь и кровь)
    private void drawTitle(Graphics2D g) {
        g.setFont(TITLE_FONT);
        FontMetrics fm = g.getFontMetrics();
        int x = (W - fm.stringWidth(TITLE)) / 2, y = TITLE_Y;
        Shape old = g.getClip();

        g.setClip(0, 0, W / 2, H);
        titleFx(g, x, y, new Color(255, 236, 150), 15, new Color(120, 84, 14),
                new GradientPaint(0, y - 36, new Color(255, 255, 246), 0, y, new Color(255, 204, 70)));

        float pulse = 0.5f + 0.5f * (float) Math.sin(tick * 0.2);
        g.setClip(W / 2, 0, W / 2, H);
        titleFx(g, x, y, new Color(255, 70, 20), 14 + (int) (10 * pulse), new Color(46, 0, 0),
                new GradientPaint(0, y - 36, new Color(255, 160 + (int) (40 * pulse), 40), 0, y, new Color(160, 12, 12)));

        g.setClip(old);
        int sx = W / 2;                                               // трещина между раем и адом
        int[] xs = {sx, sx - 3, sx + 3, sx - 2, sx + 2, sx};
        int[] ys = {y - 46, y - 33, y - 20, y - 8, y + 4, y + 14};
        g.setStroke(new BasicStroke(5f));
        g.setColor(new Color(0, 0, 0, 170));
        g.drawPolyline(xs, ys, xs.length);
        g.setStroke(new BasicStroke(1.5f));
        g.setColor(new Color(255, 205, 130, 235));
        g.drawPolyline(xs, ys, xs.length);
    }

    private void titleFx(Graphics2D g, int x, int y, Color glow, int glowAlpha, Color outline, Paint fill) {
        g.setColor(Theme.alpha(glow, glowAlpha));
        for (int dx = -4; dx <= 4; dx += 2)
            for (int dy = -4; dy <= 4; dy += 2) g.drawString(TITLE, x + dx, y + dy);
        g.setColor(outline);
        for (int dx = -2; dx <= 2; dx += 2)
            for (int dy = -2; dy <= 3; dy += 2) g.drawString(TITLE, x + dx, y + dy);
        g.setPaint(fill);
        g.drawString(TITLE, x, y);
    }

    private void drawLabels(Graphics2D g) {
        label(g, PLAY_R, "ИГРАТЬ", sel == 0 ? new Color(255, 246, 180) : new Color(255, 232, 190),
                new Color(60, 8, 4), pressedIdx == 0, false);
        label(g, EXIT_R, "ВЫХОД", new Color(28, 44, 110), new Color(255, 255, 255, 170), pressedIdx == 1, true);
    }

    private void label(Graphics2D g, Rectangle r, String s, Color fill, Color edge, boolean down, boolean lightEdge) {
        g.setFont(BTN_FONT);
        FontMetrics fm = g.getFontMetrics();
        int x = r.x * S + (r.width * S - fm.stringWidth(s)) / 2;
        int y = r.y * S + (r.height * S + fm.getAscent() - fm.getDescent()) / 2 + (down ? S : 0) + 1;
        g.setColor(edge);
        if (lightEdge) {
            g.drawString(s, x, y + 2);
        } else {
            for (int dx = -2; dx <= 2; dx += 2)
                for (int dy = -2; dy <= 2; dy += 2) g.drawString(s, x + dx, y + dy);
        }
        g.setColor(fill);
        g.drawString(s, x, y);
    }

    // ======================================================================
    //                           ИНСТРУМЕНТЫ
    // ======================================================================
    private void cloud(Graphics2D a, int cx, int cy, int w) {
        int h = Math.max(6, w / 3);
        a.setColor(new Color(156, 146, 212));
        a.fillOval(cx - w / 2, cy - h / 3 + 2, w, h);
        a.fillOval(cx - w / 3, cy - h + 3, w * 2 / 3, h);
        a.setColor(new Color(206, 198, 246));
        a.fillOval(cx - w / 2 + 1, cy - h / 3, w - 2, h - 2);
        a.fillOval(cx - w / 3 + 2, cy - h + 1, w * 2 / 3 - 3, h - 2);
        a.setColor(new Color(244, 240, 255));
        a.fillOval(cx - w / 4, cy - h + 2, w / 3, h / 3);
    }

    private static void glow(Graphics2D a, float cx, float cy, float r, Color c, float alpha) {
        if (alpha <= 0) return;
        int al = Math.min(255, (int) (255 * alpha));
        Paint old = a.getPaint();
        a.setPaint(new RadialGradientPaint(new Point2D.Float(cx, cy), r, new float[]{0f, 1f},
                new Color[]{Theme.alpha(c, al), Theme.alpha(c, 0)}));
        a.fill(new Ellipse2D.Float(cx - r, cy - r, 2 * r, 2 * r));
        a.setPaint(old);
    }

    private static void rect(Graphics2D a, Color c, int x, int y, int w, int h) {
        a.setColor(c);
        a.fillRect(x, y, w, h);
    }

    private static int clamp(int v) { return Math.max(0, Math.min(255, v)); }
}