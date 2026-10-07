package game;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

// ГЛАВНОЕ МЕНЮ (вход в игру): собор, девушка у свечи, пентаграмма с чашами.
// Фон — картинка menu_bg.png (лежит рядом с этим файлом), поверх неё код рисует:
//   • каменные кнопки (ПРОДОЛЖИТЬ — только если есть сохранённое место, ИГРАТЬ, ВЫХОД)
//   • живые детали: дым над чашами, пылинки в луче света, свет свечи, пульсацию пентаграммы
public class MainMenuPanel extends JPanel {

    private static final int S = 3;                                   // увеличение пикселя
    private static final int W = Room.COLS * Room.TILE;               // 960
    private static final int H = Room.ROWS * Room.TILE;               // 576
    private static final int LW = W / S, LH = H / S;                  // 320 x 192 — «холст» пиксель-арта

    // ===== НАСТРОЙКИ: кнопки (в пикселях арта, на экране ×3) =====
    private static final int BTN_W = 92;                              // ширина кнопки
    private static final int BTN_H = 20;                              // высота обычной кнопки
    private static final int BTN_H_CONT = 24;                         // высота «ПРОДОЛЖИТЬ» (две строки)
    private static final int BTN_GAP = 4;                             // расстояние между кнопками
    private static final int BTN_CX = 237;                            // центр колонки кнопок по X
    private static final int BTN_CY2 = 78;                            // центр колонки по Y, когда кнопок две
    private static final int BTN_CY3 = 75;                            // ...и когда три

    // ===== НАСТРОЙКИ: цитата внизу справа (в полном разрешении, в пикселях экрана 960x576) =====
    private static final String VERSE = "Ибо возмездие за грех — смерть, а дар Божий — жизнь вечная во Христе Иисусе, Господе нашем.";
    private static final String VERSE_REF = "Рим. 6:23";
    private static final int VERSE_CX = BTN_CX * S;                   // центр по X — под колонкой кнопок
    private static final int VERSE_W = 440;                           // максимальная ширина строки
    private static final int VERSE_Y = 400;                           // верх текста по Y
    private static final int VERSE_SIZE = 20;                         // размер шрифта

    private enum Act { CONTINUE, PLAY, EXIT }

    private static final class Btn {
        final Act act;
        final String label;
        final Rectangle r;
        Btn(Act act, String label, Rectangle r) { this.act = act; this.label = label; this.r = r; }
    }

    // откуда идёт дым (в пикселях арта): над каждой чашей
    private static final int[][] SMOKE = {{179, 121}, {239, 110}, {300, 121}, {192, 142}, {290, 142}};
    private static final int SMOKE_N = 14, SMOKE_LIFE = 120;
    // луч света из окна (многоугольник, в пикселях арта) — здесь летают пылинки
    private static final int[] BEAM_X = {40, 67, 143, 100, 50};
    private static final int[] BEAM_Y = {23, 27, 113, 120, 83};
    private static final int MOTES = 30;

    private static final Map<String, BufferedImage> STONES = new HashMap<>();
    private static final Map<String, boolean[][]> GLYPHS = new HashMap<>();

    private final GameWindow window;
    private final BufferedImage bg = loadBg();
    private final BufferedImage fx = new BufferedImage(LW, LH, BufferedImage.TYPE_INT_ARGB);
    private final javax.swing.Timer timer;
    private final Random rnd = new Random(11);
    private final float[] mx = new float[MOTES], my = new float[MOTES];
    private final List<Btn> btns = new ArrayList<>();

    private String progress;        // где остановился игрок; null — сохранения нет
    private int tick;
    private int sel = 0;            // какая кнопка подсвечена (мышь или стрелки)
    private int pressed = -1;

    public MainMenuPanel(GameWindow window) {
        this.window = window;
        setPreferredSize(new Dimension(W, H));
        setBackground(Color.BLACK);
        setFocusable(true);
        layoutButtons();

        for (int i = 0; i < MOTES; i++) respawnMote(i);

        timer = new javax.swing.Timer(33, e -> {
            tick++;
            moveMotes();
            repaint();
        });
        addHierarchyListener(e -> {                                   // анимация крутится, только пока меню на экране
            if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0) {
                if (isShowing()) { timer.start(); requestFocusInWindow(); } else timer.stop();
            }
        });

        MouseAdapter mouse = new MouseAdapter() {
            @Override public void mouseMoved(MouseEvent e) {
                int i = hit(e.getPoint());
                if (i >= 0) sel = i;
                setCursor(Cursor.getPredefinedCursor(i >= 0 ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
            }
            @Override public void mousePressed(MouseEvent e) { pressed = hit(e.getPoint()); }
            @Override public void mouseReleased(MouseEvent e) {
                int i = hit(e.getPoint());
                if (i >= 0 && i == pressed) activate(i);
                pressed = -1;
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);

        bind("UP", () -> sel = Math.max(0, sel - 1));
        bind("W", () -> sel = Math.max(0, sel - 1));
        bind("DOWN", () -> sel = Math.min(btns.size() - 1, sel + 1));
        bind("S", () -> sel = Math.min(btns.size() - 1, sel + 1));
        bind("ENTER", () -> activate(sel));
        bind("SPACE", () -> activate(sel));
    }

    // Вызывает GameWindow: text — где остановился игрок (кнопка ПРОДОЛЖИТЬ появится), null — сохранения нет.
    public void setProgress(String text) {
        progress = (text == null || text.isBlank()) ? null : text;
        layoutButtons();
        sel = 0;
        pressed = -1;
    }

    private void layoutButtons() {
        btns.clear();
        boolean cont = progress != null;
        int total = (cont ? BTN_H_CONT + BTN_GAP : 0) + BTN_H + BTN_GAP + BTN_H;
        int top = (cont ? BTN_CY3 : BTN_CY2) - total / 2;
        int x = BTN_CX - BTN_W / 2;
        if (cont) {
            btns.add(new Btn(Act.CONTINUE, "ПРОДОЛЖИТЬ", new Rectangle(x, top, BTN_W, BTN_H_CONT)));
            top += BTN_H_CONT + BTN_GAP;
        }
        btns.add(new Btn(Act.PLAY, "ИГРАТЬ", new Rectangle(x, top, BTN_W, BTN_H)));
        top += BTN_H + BTN_GAP;
        btns.add(new Btn(Act.EXIT, "ВЫХОД", new Rectangle(x, top, BTN_W, BTN_H)));
    }

    private void bind(String key, Runnable r) {
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(key), key);
        getActionMap().put(key, new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { if (isShowing()) r.run(); }
        });
    }

    private void activate(int i) {
        if (i < 0 || i >= btns.size()) return;
        switch (btns.get(i).act) {
            case CONTINUE -> window.continueGame();
            case PLAY -> window.startNewGame();
            case EXIT -> System.exit(0);
        }
    }

    private int hit(Point screen) {
        Point p = View.toLogical(this, screen);
        for (int i = 0; i < btns.size(); i++) {
            Rectangle r = btns.get(i).r;
            if (new Rectangle(r.x * S, r.y * S, r.width * S, r.height * S).contains(p)) return i;
        }
        return -1;
    }

    private static BufferedImage loadBg() {
        try {
            java.net.URL u = MainMenuPanel.class.getResource("mune_bg.png");
            if (u != null) return ImageIO.read(u);
            File f = new File("src/game/mune_bg.png");               // запасной путь, если запускаешь не из IDE
            if (f.exists()) return ImageIO.read(f);
        } catch (Exception ignored) { }
        return null;
    }

    // ---------- пылинки ----------
    private static boolean inBeam(float x, float y) {
        return new Polygon(BEAM_X, BEAM_Y, BEAM_X.length).contains(x, y);
    }

    private void respawnMote(int i) {
        do {
            mx[i] = 40 + rnd.nextFloat() * 105;
            my[i] = 23 + rnd.nextFloat() * 97;
        } while (!inBeam(mx[i], my[i]));
    }

    private void moveMotes() {
        for (int i = 0; i < MOTES; i++) {
            mx[i] += 0.05f + (float) Math.sin(tick * 0.03 + i) * 0.03f;
            my[i] += 0.035f + (float) Math.cos(tick * 0.025 + i * 2) * 0.02f;
            if (!inBeam(mx[i], my[i])) respawnMote(i);
        }
    }

    // ======================================================================
    //                              ОТРИСОВКА
    // ======================================================================
    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0.create();
        View.apply(g, this);                                          // масштаб под размер окна

        if (bg != null) {
            g.drawImage(bg, 0, 0, W, H, null);
        } else {
            g.setPaint(new GradientPaint(0, 0, new Color(8, 8, 14), 0, H, new Color(30, 8, 8)));
            g.fillRect(0, 0, W, H);
            g.setColor(new Color(200, 80, 80));
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
            g.drawString("Не найден фон menu_bg.png — положи его в папку src/game рядом с MainMenuPanel.java", 20, H - 20);
        }

        Graphics2D a = fx.createGraphics();
        a.setComposite(AlphaComposite.Clear);
        a.fillRect(0, 0, LW, LH);
        a.setComposite(AlphaComposite.SrcOver);
        a.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        drawAmbient(a);
        drawButtons(a);
        a.dispose();

        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(fx, 0, 0, W, H, null);

        drawProgressText(g);
        drawVerse(g);
        g.dispose();
    }

    // ---------- живые детали поверх фона ----------
    private void drawAmbient(Graphics2D a) {
        // свет свечи (пламя на фоне рисованное, поэтому только тёплое мерцающее свечение)
        double f = Math.sin(tick * 0.31) * 0.5 + Math.sin(tick * 0.13 + 1.7) * 0.5;
        glow(a, 53, 137, 30 + (float) f * 2, new Color(255, 170, 70), 0.20f + 0.06f * (float) f);

        // пентаграмма слегка пульсирует кровавым светом
        AffineTransformHelper.squashed(a, 240, 152, 0.38, () ->
                glow(a, 0, 0, 74, new Color(220, 20, 20), 0.12f + 0.07f * (float) Math.sin(tick * 0.05)));

        // пылинки в луче света
        for (int i = 0; i < MOTES; i++) {
            float tw = 0.5f + 0.5f * (float) Math.sin(tick * 0.07 + i * 1.9);
            a.setColor(new Color(220, 232, 255, (int) (60 + 130 * tw)));
            a.fillRect((int) mx[i], (int) my[i], 1, 1);
        }

        // красный дым над чашами
        for (int k = 0; k < SMOKE.length; k++) {
            for (int j = 0; j < SMOKE_N; j++) {
                int age = (tick + j * SMOKE_LIFE / SMOKE_N + k * 19) % SMOKE_LIFE;
                float t = age / (float) SMOKE_LIFE;
                double wob = Math.sin(age * 0.07 + j * 1.3 + k) * (1.2 + 2.4 * t);
                int x = (int) Math.round(SMOKE[k][0] + wob + j % 3 - 1);
                int y = (int) Math.round(SMOKE[k][1] - age * 0.30);
                int size = 1 + (int) (t * 1.8);
                int al = (int) (84 * Math.sin(Math.PI * t) * (1 - 0.3 * t));
                a.setColor(new Color(206, 60, 70, Math.max(0, Math.min(255, al))));
                a.fillRect(x, y, size, size);
            }
        }
    }

    // ---------- каменные кнопки ----------
    private void drawButtons(Graphics2D a) {
        for (int i = 0; i < btns.size(); i++) {
            Btn b = btns.get(i);
            boolean hot = sel == i, down = pressed == i;
            Rectangle r = b.r;
            int y = r.y + (down ? 1 : 0);

            if (hot) glow(a, r.x + r.width / 2f, r.y + r.height / 2f, 60, new Color(200, 36, 36),
                    0.26f + 0.06f * (float) Math.sin(tick * 0.15));
            a.setColor(new Color(0, 0, 0, 140));
            a.fillRect(r.x + 1, y + 2, r.width, r.height);
            a.drawImage(stone(r.width, r.height, hot), r.x, y, null);

            int size = 12;
            while (size > 7 && trimmedWidth(b.label, size) > BTN_W - 14) size--;
            int cy = b.act == Act.CONTINUE ? y + 8 : y + r.height / 2;
            pixelText(a, b.label, r.x + r.width / 2, cy, size,
                    hot ? new Color(255, 238, 208) : new Color(224, 226, 234), new Color(20, 20, 28));
        }
    }

    // подпись «где ты остановился» под словом ПРОДОЛЖИТЬ (в полном разрешении)
    private void drawProgressText(Graphics2D g) {
        if (progress == null) return;
        for (int i = 0; i < btns.size(); i++) {
            Btn b = btns.get(i);
            if (b.act != Act.CONTINUE) continue;
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int size = 14;
            Font f = new Font(Font.SANS_SERIF, Font.PLAIN, size);
            FontMetrics fm = g.getFontMetrics(f);
            while (size > 9 && fm.stringWidth(progress) > BTN_W * S - 28) {
                f = new Font(Font.SANS_SERIF, Font.PLAIN, --size);
                fm = g.getFontMetrics(f);
            }
            g.setFont(f);
            int x = (b.r.x + b.r.width / 2) * S - fm.stringWidth(progress) / 2;
            int y = (b.r.y + b.r.height) * S - 12 + (pressed == i ? S : 0);
            g.setColor(new Color(0, 0, 0, 200));
            g.drawString(progress, x + 1, y + 1);
            g.setColor(sel == i ? new Color(255, 206, 150) : new Color(178, 184, 198));
            g.drawString(progress, x, y);
        }
    }

    // цитата из Писания: чёткий текст по центру правой колонки, с тенью, чтобы читалось на любом фоне
    private void drawVerse(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        Font f = new Font(Font.SERIF, Font.ITALIC, VERSE_SIZE);
        FontMetrics fm = g.getFontMetrics(f);
        g.setFont(f);

        // перенос по словам
        List<String> lines = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String word : VERSE.split(" ")) {
            String test = cur.length() == 0 ? word : cur + " " + word;
            if (fm.stringWidth(test) > VERSE_W && cur.length() > 0) {
                lines.add(cur.toString());
                cur = new StringBuilder(word);
            } else {
                cur = new StringBuilder(test);
            }
        }
        if (cur.length() > 0) lines.add(cur.toString());

        int lineH = fm.getHeight() + 2;
        int y = VERSE_Y + fm.getAscent();

        // тонкая линия над цитатой
        g.setColor(new Color(150, 40, 40, 170));
        g.fillRect(VERSE_CX - 40, VERSE_Y - 14, 80, 2);

        for (String line : lines) {
            int x = VERSE_CX - fm.stringWidth(line) / 2;
            g.setColor(new Color(0, 0, 0, 220));
            g.drawString(line, x + 2, y + 2);
            g.setColor(new Color(232, 226, 214));
            g.drawString(line, x, y);
            y += lineH;
        }

        // ссылка на стих
        Font rf = new Font(Font.SERIF, Font.PLAIN, VERSE_SIZE - 4);
        FontMetrics rfm = g.getFontMetrics(rf);
        g.setFont(rf);
        int rx = VERSE_CX - rfm.stringWidth(VERSE_REF) / 2;
        y += 4;
        g.setColor(new Color(0, 0, 0, 220));
        g.drawString(VERSE_REF, rx + 1, y + 1);
        g.setColor(new Color(200, 70, 70));
        g.drawString(VERSE_REF, rx, y);
    }

    private BufferedImage stone(int w, int h, boolean hot) {
        return STONES.computeIfAbsent(w + "x" + h + (hot ? "h" : "n"), k -> {
            BufferedImage im = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Random rr = new Random(w * 31L + h);
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    if ((x == 0 || x == w - 1) && (y == 0 || y == h - 1)) continue;       // срезанные углы
                    Color c;
                    if (x == 0 || y == 0 || x == w - 1 || y == h - 1) c = new Color(16, 16, 22);
                    else if (y == 1) c = new Color(146, 152, 164);
                    else if (x == 1) c = new Color(116, 122, 134);
                    else if (y == h - 2) c = hot ? new Color(176, 40, 40) : new Color(46, 48, 56);
                    else if (x == w - 2) c = new Color(58, 60, 68);
                    else {
                        int n = rr.nextInt(17) - 8;
                        int base = 92 - (y * 14 / h) + (hot ? 16 : 0);
                        c = new Color(clamp(base + n), clamp(base + 4 + n), clamp(base + 12 + n));
                    }
                    im.setRGB(x, y, c.getRGB());
                }
            }
            int crack = new Color(44, 46, 54).getRGB();                                   // трещины
            for (int c = 0; c < 3; c++) {
                int x = 4 + rr.nextInt(w - 10), y = 3 + rr.nextInt(h - 7), dx = rr.nextBoolean() ? 1 : -1;
                for (int s = 0, len = 6 + rr.nextInt(6); s < len; s++) {
                    if (x < 3 || x >= w - 3 || y < 3 || y >= h - 3) break;
                    im.setRGB(x, y, crack);
                    if (rr.nextInt(3) == 0) x += dx; else y += rr.nextBoolean() ? 1 : 0;
                }
            }
            int[][] rv = {{3, 3}, {w - 4, 3}, {3, h - 4}, {w - 4, h - 4}};                // заклёпки
            for (int[] p : rv) {
                im.setRGB(p[0], p[1], new Color(150, 156, 168).getRGB());
                im.setRGB(p[0], p[1] + 1, new Color(40, 42, 50).getRGB());
            }
            return im;
        });
    }

    // ---------- пиксельный текст: рисуем шрифтом в маленькую картинку и превращаем в чёткие «пиксели» ----------
    private static boolean[][] glyphs(String s, int size) {
        return GLYPHS.computeIfAbsent(size + "|" + s, k -> {
            Font f = new Font(Font.SANS_SERIF, Font.BOLD, size);
            BufferedImage tmp = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = tmp.createGraphics();
            FontMetrics fm = g.getFontMetrics(f);
            g.dispose();
            int w = fm.stringWidth(s) + 4, h = fm.getHeight() + 4;
            BufferedImage im = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            g = im.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setFont(f);
            g.setColor(Color.WHITE);
            g.drawString(s, 2, fm.getAscent() + 2);
            g.dispose();
            int x0 = w, x1 = -1, y0 = h, y1 = -1;
            boolean[][] on = new boolean[h][w];
            for (int y = 0; y < h; y++)
                for (int x = 0; x < w; x++)
                    if (((im.getRGB(x, y) >>> 24) & 0xFF) > 120) {
                        on[y][x] = true;
                        x0 = Math.min(x0, x); x1 = Math.max(x1, x);
                        y0 = Math.min(y0, y); y1 = Math.max(y1, y);
                    }
            if (x1 < 0) return new boolean[1][1];
            boolean[][] out = new boolean[y1 - y0 + 1][x1 - x0 + 1];
            for (int y = y0; y <= y1; y++)
                for (int x = x0; x <= x1; x++) out[y - y0][x - x0] = on[y][x];
            return out;
        });
    }

    private static int trimmedWidth(String s, int size) { return glyphs(s, size)[0].length; }

    private static void pixelText(Graphics2D a, String s, int cx, int cy, int size, Color fill, Color outline) {
        boolean[][] b = glyphs(s, size);
        int x0 = cx - b[0].length / 2, y0 = cy - b.length / 2;
        int[][] offs = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}, {1, 1}};
        a.setColor(outline);
        for (int y = 0; y < b.length; y++)
            for (int x = 0; x < b[y].length; x++)
                if (b[y][x]) for (int[] o : offs) a.fillRect(x0 + x + o[0], y0 + y + o[1], 1, 1);
        a.setColor(fill);
        for (int y = 0; y < b.length; y++)
            for (int x = 0; x < b[y].length; x++)
                if (b[y][x]) a.fillRect(x0 + x, y0 + y, 1, 1);
    }

    // ---------- инструменты ----------
    private static void glow(Graphics2D a, float cx, float cy, float r, Color c, float alpha) {
        if (alpha <= 0) return;
        int al = Math.min(255, (int) (255 * alpha));
        Paint old = a.getPaint();
        a.setPaint(new RadialGradientPaint(new Point2D.Float(cx, cy), r, new float[]{0f, 1f},
                new Color[]{new Color(c.getRed(), c.getGreen(), c.getBlue(), al), new Color(c.getRed(), c.getGreen(), c.getBlue(), 0)}));
        a.fill(new Ellipse2D.Float(cx - r, cy - r, 2 * r, 2 * r));
        a.setPaint(old);
    }

    private static int clamp(int v) { return Math.max(0, Math.min(255, v)); }

    // рисует что-то вокруг точки (0,0), сплющив по вертикали — для света на полу
    private static final class AffineTransformHelper {
        static void squashed(Graphics2D a, double cx, double cy, double sy, Runnable r) {
            java.awt.geom.AffineTransform t0 = a.getTransform();
            a.translate(cx, cy);
            a.scale(1, sy);
            r.run();
            a.setTransform(t0);
        }
    }
}