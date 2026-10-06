package game;

import javax.swing.SwingUtilities;
import javax.swing.*;
import java.awt.CardLayout;
import java.awt.*;
import java.awt.geom.Point2D;

public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(GameWindow::new);
    }
}

// Масштаб картинки под размер окна: игра рисуется в «логических» 960x576, а на экране растягивается с чёрными полями.
final class View {
    private View() {}

    static double scale(Component c) {
        return Math.min(c.getWidth() / (double) Room.VIEW_W, c.getHeight() / (double) Room.VIEW_H);
    }

    static double offX(Component c) { return (c.getWidth() - Room.VIEW_W * scale(c)) / 2.0; }
    static double offY(Component c) { return (c.getHeight() - Room.VIEW_H * scale(c)) / 2.0; }

    // сдвигает и масштабирует кисть; дальше можно рисовать как в обычном окне 960x576
    static void apply(Graphics2D g, Component c) {
        double k = scale(c);
        if (k <= 0) return;
        g.translate(offX(c), offY(c));
        g.scale(k, k);
        g.clipRect(0, 0, Room.VIEW_W, Room.VIEW_H);
    }

    // координаты мыши на экране -> координаты в игре (960x576)
    static Point toLogical(Component c, Point p) {
        double k = scale(c);
        if (k <= 0) return p;
        return new Point((int) Math.floor((p.x - offX(c)) / k), (int) Math.floor((p.y - offY(c)) / k));
    }
}

class GameWindow extends JFrame {
    private final CardLayout cards = new CardLayout();
    private final JPanel root = new JPanel(cards);
    private final GamePanel gamePanel = new GamePanel(this);
    private final MainMenuPanel mainMenu = new MainMenuPanel(this);   // главное меню (вход в игру)
    private final MenuPanel pauseMenu = new MenuPanel(this);          // меню паузы (ESC во время игры)

    public GameWindow() {
        setTitle("Моя игра");
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        root.add(mainMenu, "MAIN");
        root.add(pauseMenu, "PAUSE");
        root.add(gamePanel, "GAME");
        add(root);

        setResizable(true);                       // можно растягивать и разворачивать окно — картинка масштабируется
        pack();
        setMinimumSize(getSize());
        setLocationRelativeTo(null);
        setupFullscreenKey();
        setVisible(true);
        showMenu();
    }

    // ===== полный экран: F11 (в любом месте игры) или кнопка «развернуть» у окна =====
    private boolean fullscreen;
    private String currentCard = "MAIN";

    private void setupFullscreenKey() {
        InputMap im = getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        im.put(KeyStroke.getKeyStroke("F11"), "fullscreen");
        getRootPane().getActionMap().put("fullscreen", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { toggleFullscreen(); }
        });
    }

    public void toggleFullscreen() {
        fullscreen = !fullscreen;
        Rectangle screen = getGraphicsConfiguration().getBounds();
        dispose();                                // режим окна (с рамкой / без) меняется только у закрытого окна
        setUndecorated(fullscreen);
        if (fullscreen) {
            setExtendedState(JFrame.NORMAL);
            setBounds(screen);
        } else {
            pack();
            setLocationRelativeTo(null);
        }
        setVisible(true);
        SwingUtilities.invokeLater(this::focusCurrent);
    }

    private void focusCurrent() {
        switch (currentCard) {
            case "GAME" -> gamePanel.requestFocusInWindow();
            case "PAUSE" -> pauseMenu.requestFocusInWindow();
            default -> mainMenu.requestFocusInWindow();
        }
    }

    private void show(String card) {
        currentCard = card;
        cards.show(root, card);
    }

    // Главное меню без сохранения: при запуске и после финала игры (GamePanel вызывает именно этот метод).
    public void showMenu() {
        gamePanel.stop();
        mainMenu.setProgress(null);
        show("MAIN");
    }

    // ESC во время игры: пауза, мир замирает, ничего не сбрасывается
    public void showPause() {
        gamePanel.pause();
        show("PAUSE");
    }

    // в меню паузы нажали ИГРАТЬ (или ещё раз ESC) / в главном меню нажали ПРОДОЛЖИТЬ
    public void resumeGame() { continueGame(); }

    public void continueGame() {
        show("GAME");
        gamePanel.resume();
    }

    // в меню паузы нажали ВЫХОД: идём в главное меню, но игра запоминается — там появляется кнопка ПРОДОЛЖИТЬ
    public void exitToMainMenu() {
        gamePanel.pause();
        mainMenu.setProgress(gamePanel.progressText());
        show("MAIN");
    }

    // в главном меню нажали ИГРАТЬ: новая игра с нуля
    public void startNewGame() {
        show("GAME");
        gamePanel.start();
        gamePanel.requestFocusInWindow();
    }
}


// Катсцена №1 (до начала игры): переулок -> странник отдаёт книгу -> «Путь в рай» -> книга крупным планом -> уход в темноту.
// Управление: ПРОБЕЛ / ENTER / E — дальше (первое нажатие дописывает реплику), ESC — пропустить всё.
class Cutscene {
    private static final int W = Room.VIEW_W, H = Room.VIEW_H;
    private static final int GROUND = 440;                // линия "ног" в сценах-переулках
    private static final int TYPE = 2;                    // кадров на одну букву реплики
    private static final Color GOLD = new Color(240, 200, 60);

    private enum Scene { WALK, HAND, SLIDE, COVER, PAGE1, PAGE2, LEAVE }

    // line — реплика Эльзы (null = без реплики), start — через сколько кадров начать печатать, dur — длительность
    private record Beat(Scene scene, String line, int start, int dur, boolean fadeIn) {}

    private static final Beat[] BEATS = {
            // КАДР 1
            new Beat(Scene.WALK,  "Я так голодна, я не ела две-три недели.", 15, 230, true),
            new Beat(Scene.HAND,  null, 0, 150, false),
            new Beat(Scene.SLIDE, null, 0, 260, true),
            // КАДР 2
            new Beat(Scene.COVER, null, 0, 200, true),
            new Beat(Scene.PAGE1, null, 0, 220, false),
            new Beat(Scene.PAGE2, "Неужели это правда? Есть способ попасть в рай? Я хочу этого.", 40, 340, false),
            // КАДР 3
            new Beat(Scene.LEAVE, "Я сделаю это, несмотря ни на что. Я через столько всего прошла, я столько страдала.", 20, 360, true),
    };

    private final Player elsa = new Player();             // готовый спрайт героини из игры
    private int beat, t, tick;

    public boolean finished() { return beat >= BEATS.length; }
    public void skip()        { beat = BEATS.length; }

    public void update() {
        if (finished()) return;
        tick++;
        if (++t >= BEATS[beat].dur()) next();
    }

    // кнопка «дальше»: сначала дописывает реплику, потом переключает кадр
    public void advance() {
        if (finished()) return;
        Beat b = BEATS[beat];
        if (b.line() != null && t < b.start() + b.line().length() * TYPE) {
            t = b.start() + b.line().length() * TYPE;
        } else {
            next();
        }
    }

    private void next() { beat++; t = 0; }

    // ================= ОТРИСОВКА =================
    public void draw(Graphics2D g0) {
        if (finished()) return;
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        Beat b = BEATS[beat];

        switch (b.scene()) {
            case WALK  -> drawWalk(g);
            case HAND  -> drawHand(g);
            case SLIDE -> drawSlide(g);
            case COVER -> drawCover(g);
            case PAGE1 -> drawPages(g, false);
            case PAGE2 -> drawPages(g, true);
            case LEAVE -> drawLeave(g, b);
        }

        // кинематографичные полосы, подпись, подсказка
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, W, 40);
        g.setFont(new Font("Monospaced", Font.PLAIN, 14));
        g.setColor(new Color(150, 150, 180));
        String hint = "ПРОБЕЛ — дальше    ESC — пропустить";
        g.drawString(hint, W - g.getFontMetrics().stringWidth(hint) - 14, 26);
        drawSubtitle(g, b);

        if (b.fadeIn() && t < 18) {                       // появление из темноты
            g.setColor(new Color(0, 0, 0, (int) (255 * (1 - t / 18f))));
            g.fillRect(0, 0, W, H);
        }
        g.dispose();
    }

    // ---------- КАДР 1: переулок ----------
    private void drawWalk(Graphics2D g) {
        drawBackdrop(g, false);
        drawStranger(g, 620, 0f);
        float p = ease(Math.min(1f, t / 200f));
        drawElsa(g, (int) lerp(-40, 540, p), t < 200);
    }

    private void drawHand(Graphics2D g) {
        drawBackdrop(g, false);
        float out = t < 100 ? ease(Math.min(1f, t / 40f)) : 1f - ease((t - 100) / 50f);
        Point tip = drawStranger(g, 620, out);
        drawElsa(g, 540, false);
        int bx, by;
        if (t < 40) {                                      // книга в руке странника
            bx = tip.x; by = tip.y;
        } else if (t < 100) {                              // переходит к Эльзе
            float u = ease((t - 40) / 60f);
            bx = (int) lerp(560, 556, u);
            by = (int) lerp(GROUND - 78, GROUND - 40, u);
        } else {                                           // книга у Эльзы
            bx = 556; by = GROUND - 40;
        }
        drawBook(g, bx, by, 22, 28);
    }

    // ---------- слайд 1: «Что это за книга? Путь в рай» ----------
    private void drawSlide(Graphics2D g) {
        g.setPaint(new GradientPaint(0, 0, new Color(10, 8, 22), 0, H, new Color(30, 22, 50)));
        g.fillRect(0, 0, W, H);
        g.setColor(new Color(240, 200, 60, 90));
        g.setStroke(new BasicStroke(3));
        g.drawRect(60, 70, W - 120, H - 140);
        g.setStroke(new BasicStroke(1));

        float qa = Math.min(1f, t / 40f);
        float dim = t > 110 ? 0.35f : 1f;
        g.setFont(new Font("Monospaced", Font.BOLD, 40));
        g.setColor(alpha(Color.WHITE, qa * dim));
        centered(g, "Что это за книга?", H / 2 - 20);

        if (t > 110) {
            float ta = Math.min(1f, (t - 110) / 50f);
            g.setPaint(new RadialGradientPaint(new Point2D.Float(W / 2f, H / 2f + 60), 300,
                    new float[]{0f, 1f}, new Color[]{alpha(GOLD, ta * 0.25f), alpha(GOLD, 0f)}));
            g.fillOval(W / 2 - 300, H / 2 - 240, 600, 600);
            g.setFont(new Font("Monospaced", Font.BOLD, 72));
            g.setColor(alpha(GOLD, ta));
            centered(g, "ПУТЬ В РАЙ", H / 2 + 80);
        }
    }

    // ---------- КАДР 2: книга крупным планом ----------
    private void bookBackdrop(Graphics2D g) {
        g.setColor(new Color(12, 9, 20));
        g.fillRect(0, 0, W, H);
        g.setPaint(new RadialGradientPaint(new Point2D.Float(W / 2f, 250), 400,
                new float[]{0f, 1f}, new Color[]{new Color(255, 215, 120, 55), new Color(255, 215, 120, 0)}));
        g.fillOval(W / 2 - 400, 250 - 400, 800, 800);
    }

    private void drawCover(Graphics2D g) {
        bookBackdrop(g);
        float s = ease(Math.min(1f, t / 70f));              // книга подносится вплотную
        int cx = W / 2, cy = 255;
        int bw = (int) lerp(46, 330, s), bh = (int) lerp(60, 410, s);
        int x = cx - bw / 2, y = cy - bh / 2;

        g.setColor(new Color(0, 0, 0, 120));
        g.fillRect(x + 8, y + 10, bw, bh);
        g.setColor(new Color(122, 38, 44));
        g.fillRect(x, y, bw, bh);
        g.setColor(new Color(84, 24, 32));
        g.fillRect(x, y, Math.max(2, bw / 12), bh);
        g.setColor(GOLD);
        g.setStroke(new BasicStroke(Math.max(1f, bw / 90f)));
        int inset = Math.max(2, bw / 18);
        g.drawRect(x + bw / 7, y + inset, bw - bw / 7 - inset, bh - 2 * inset);

        if (s > 0.5f) {
            int ex = x + (int) (bw * 0.575), ey = y + (int) (bh * 0.30), r = (int) (bw * 0.10);
            g.drawOval(ex - r, ey - r, r * 2, r * 2);                       // нимб
            for (int i = 0; i < 12; i++) {
                double a = i * Math.PI / 6;
                g.drawLine(ex + (int) (Math.cos(a) * r * 1.4), ey + (int) (Math.sin(a) * r * 1.4),
                        ex + (int) (Math.cos(a) * r * 1.9), ey + (int) (Math.sin(a) * r * 1.9));
            }
            g.setFont(new Font(Font.SERIF, Font.BOLD | Font.ITALIC, (int) (bw * 0.085)));
            centeredAt(g, "path to heaven", ex, y + (int) (bh * 0.62));
            g.setFont(new Font(Font.SERIF, Font.ITALIC, (int) (bw * 0.06)));
            g.setColor(new Color(230, 200, 120));
            centeredAt(g, "novel", ex, y + (int) (bh * 0.70));
        }
        g.setStroke(new BasicStroke(1));

        if (s > 0.85f) {                                                    // руки Эльзы держат книгу
            g.setColor(new Color(128, 26, 46));
            g.fillRect(x - 10, y + (int) (bh * 0.55) + 50, 38, H);
            g.fillRect(x + bw - 28, y + (int) (bh * 0.55) + 50, 38, H);
            g.setColor(new Color(226, 186, 164));
            g.fillRoundRect(x - 14, y + (int) (bh * 0.55), 46, 70, 22, 22);
            g.fillRoundRect(x + bw - 32, y + (int) (bh * 0.55), 46, 70, 22, 22);
        }
    }

    private void drawPages(Graphics2D g, boolean second) {
        bookBackdrop(g);
        int pw = 330, ph = 390, lx = W / 2 - pw, rx = W / 2, y = 52;
        g.setColor(new Color(0, 0, 0, 120));
        g.fillRect(lx + 8, y + 10, pw * 2, ph);
        g.setColor(new Color(92, 30, 36));                                   // обложка по краям
        g.fillRect(lx - 10, y - 8, pw * 2 + 20, ph + 16);
        g.setColor(new Color(226, 214, 184));
        g.fillRect(lx, y, pw, ph);
        g.setColor(new Color(238, 228, 200));
        g.fillRect(rx, y, pw, ph);
        g.setPaint(new GradientPaint(W / 2 - 26, 0, new Color(0, 0, 0, 0), W / 2, 0, new Color(0, 0, 0, 90)));
        g.fillRect(W / 2 - 26, y, 26, ph);
        g.setPaint(new GradientPaint(W / 2, 0, new Color(0, 0, 0, 90), W / 2 + 26, 0, new Color(0, 0, 0, 0)));
        g.fillRect(W / 2, y, 26, ph);

        Color ink = new Color(70, 46, 34);
        if (!second) {                                                       // стр.1 справа, слева пусто
            float a = Math.min(1f, t / 50f);
            withAlpha(g, a, () -> {
                g.setColor(ink);
                g.setFont(new Font(Font.SERIF, Font.BOLD | Font.ITALIC, 34));
                wrap(g, "Вечный рай, заточение небес.", Integer.MAX_VALUE, rx + 35, y + 160, pw - 70, 44, true);
            });
            return;
        }

        float p = t / 26f;                                                   // перелистывание страницы
        if (p < 1f) {
            double ang = p * Math.PI;
            int edge = W / 2 + (int) (pw * Math.cos(ang));
            g.setColor(new Color(214, 202, 172));
            g.fillRect(Math.min(edge, W / 2), y, Math.abs(edge - W / 2), ph);
            g.setColor(new Color(0, 0, 0, 40));
            g.drawRect(Math.min(edge, W / 2), y, Math.abs(edge - W / 2), ph);
            if (p < 0.5f) {                                                  // пока лист стоит — виден его текст
                g.setColor(ink);
                g.setFont(new Font(Font.SERIF, Font.BOLD | Font.ITALIC, 34));
                wrap(g, "Вечный рай, заточение небес.", Integer.MAX_VALUE, rx + 35, y + 160, pw - 70, 44, true);
            }
            return;
        }
        g.setColor(new Color(ink.getRed(), ink.getGreen(), ink.getBlue(), 150));
        g.setFont(new Font(Font.SERIF, Font.BOLD | Font.ITALIC, 24));
        wrap(g, "Вечный рай, заточение небес.", Integer.MAX_VALUE, lx + 35, y + 170, pw - 70, 32, true);

        float a = Math.min(1f, (t - 26) / 40f);
        withAlpha(g, a, () -> {
            g.setColor(ink);
            g.setFont(new Font(Font.SERIF, Font.BOLD | Font.ITALIC, 28));
            wrap(g, "Ты должен совершить 7 смертных грехов, чтобы попасть в рай — пройти испытание.",
                    Integer.MAX_VALUE, rx + 35, y + 120, pw - 70, 40, true);
        });
    }

    // ---------- КАДР 3: уход в темноту ----------
    private void drawLeave(Graphics2D g, Beat b) {
        drawBackdrop(g, true);
        float p = ease(Math.min(1f, t / 330f));
        drawElsa(g, (int) lerp(110, 900, p), true);
        g.setPaint(new GradientPaint(220, 0, new Color(0, 0, 0, 0), W, 0, new Color(0, 0, 0, 245)));
        g.fillRect(0, 0, W, H);                                              // тьма справа глотает героиню
        if (t > b.dur() - 60) {
            g.setColor(new Color(0, 0, 0, (int) (255 * (t - (b.dur() - 60)) / 60f)));
            g.fillRect(0, 0, W, H);
        }
    }

    // ================= ДЕКОРАЦИИ =================
    private void drawBackdrop(Graphics2D g, boolean slum) {
        g.setPaint(new GradientPaint(0, 0, new Color(8, 10, 22), 0, GROUND, new Color(34, 34, 58)));
        g.fillRect(0, 0, W, GROUND + 20);
        g.setColor(new Color(220, 225, 255, 150));
        for (int i = 0; i < 40; i++) {
            if ((tick / 20 + i) % 7 == 0) continue;
            g.fillRect((i * 137) % W, (i * 61) % 150 + 10, 2, 2);
        }
        if (slum) drawShacks(g); else drawAlley(g);

        g.setColor(new Color(30, 26, 34));                                   // земля
        g.fillRect(0, GROUND - 20, W, H - GROUND + 20);
        g.setColor(new Color(46, 40, 50));
        g.fillRect(0, GROUND - 20, W, 4);
        g.setColor(new Color(22, 19, 26));
        for (int i = 0; i < W; i += 48) {
            g.fillRect(i, GROUND + (i / 48 % 2) * 14, 30, 3);
            g.fillRect(i + 18, GROUND + 44, 24, 3);
        }
        g.setColor(new Color(120, 140, 190, 40));                            // лужи
        g.fillOval(300, GROUND + 22, 90, 14);
        g.fillOval(760, GROUND + 50, 70, 10);
        g.setColor(new Color(160, 170, 210, 14));                            // туман
        for (int i = 0; i < 4; i++) {
            int fx = (int) ((i * 260 + tick * 0.3) % (W + 300)) - 150;
            g.fillOval(fx, GROUND - 40 + (i % 2) * 20, 300, 50);
        }
    }

    private void drawAlley(Graphics2D g) {
        int floor = GROUND - 20;
        g.setColor(new Color(36, 32, 46));                                   // задняя стена
        g.fillRect(190, 120, 590, floor - 120);
        g.setColor(new Color(52, 38, 44));                                   // дома слева и справа
        g.fillRect(0, 40, 190, floor - 40);
        g.fillRect(780, 40, 180, floor - 40);
        g.setColor(new Color(32, 25, 31));
        for (int y = 60; y < floor; y += 22) { g.fillRect(0, y, 190, 2); g.fillRect(780, y, 180, 2); }
        g.setColor(new Color(30, 27, 40));
        for (int y = 140; y < floor; y += 22) g.fillRect(190, y, 590, 2);

        boolean flick = (tick / 14) % 11 != 0;                               // окна
        g.setColor(flick ? new Color(250, 200, 100, 190) : new Color(120, 90, 50, 150));
        g.fillRect(60, 120, 40, 56);
        g.setColor(new Color(14, 12, 20));
        g.fillRect(60, 220, 40, 56);
        g.fillRect(840, 130, 40, 56);
        g.fillRect(840, 240, 40, 56);
        g.fillRect(300, 170, 36, 50);
        g.fillRect(470, 170, 36, 50);

        g.setColor(new Color(30, 50, 44));                                   // мусорный бак
        g.fillRect(230, floor - 54, 110, 54);
        g.setColor(new Color(22, 38, 34));
        g.fillRect(224, floor - 62, 122, 10);
        g.setColor(new Color(74, 56, 38));                                   // ящики
        g.fillRect(700, floor - 44, 46, 44);
        g.fillRect(740, floor - 30, 40, 30);
        g.setColor(new Color(52, 38, 26));
        g.fillRect(700, floor - 30, 46, 3);

        g.setColor(new Color(26, 24, 32));                                   // фонарь
        g.fillRect(398, 200, 6, floor - 200);
        g.fillRect(388, 192, 26, 10);
        int glow = 80 + (flick ? 14 : -30);
        g.setPaint(new RadialGradientPaint(new Point2D.Float(401, 205), 170,
                new float[]{0f, 1f}, new Color[]{new Color(255, 210, 120, glow), new Color(255, 210, 120, 0)}));
        g.fillOval(231, 35, 340, 340);
    }

    // бедный район: покосившиеся лачуги
    private void drawShacks(Graphics2D g) {
        int floor = GROUND - 20;
        for (int i = 0; i < 10; i++) {
            int x = i * 100 - 20, w = 90 + (i * 37) % 30, top = floor - (90 + (i * 53) % 80);
            g.setColor(new Color(28 + i % 3 * 3, 24, 32));
            g.fillRect(x, top, w, floor - top);
            g.setColor(new Color(20, 17, 24));
            g.fillPolygon(new int[]{x - 8, x + w / 2 + (i % 3 - 1) * 12, x + w + 8},
                    new int[]{top, top - 38, top}, 3);
            g.fillRect(x + w / 2 - 9, floor - 38, 18, 38);                   // дверь
            if (i % 3 == 1) {
                g.setColor(new Color(230, 180, 90, 120 - (tick / 9 + i) % 3 * 30));
                g.fillRect(x + 14, top + 22, 18, 20);
            } else {
                g.setColor(new Color(10, 9, 14));
                g.fillRect(x + 14, top + 22, 18, 20);
            }
        }
        g.setColor(new Color(14, 12, 18));                                   // тёмные стены переднего плана
        g.fillRect(0, 60, 56, floor - 60);
        g.fillRect(W - 56, 80, 56, floor - 80);
    }

    // ================= ПЕРСОНАЖИ / ПРЕДМЕТЫ =================
    private void drawElsa(Graphics2D g, int cx, boolean moving) {
        elsa.x = cx - elsa.w / 2;
        elsa.y = GROUND - elsa.h;
        elsa.facing = Player.Dir.RIGHT;
        elsa.moving = moving;
        elsa.draw(g, tick);
    }

    // странник в капюшоне и лохмотьях; смотрит влево. Возвращает кончик протянутой руки.
    private Point drawStranger(Graphics2D g, int cx, float out) {
        int feet = GROUND;
        Color cloak = new Color(40, 38, 46), dark = new Color(26, 24, 32);
        g.setColor(new Color(0, 0, 0, 90));
        g.fillOval(cx - 34, feet - 6, 68, 14);

        g.setColor(cloak);
        g.fillPolygon(new int[]{cx - 16, cx + 16, cx + 32, cx - 32}, new int[]{feet - 92, feet - 92, feet, feet}, 4);
        g.setColor(dark);
        g.fillPolygon(new int[]{cx + 2, cx + 16, cx + 32, cx + 2}, new int[]{feet - 92, feet - 92, feet, feet}, 4);
        g.setColor(cloak);                                                   // рваный подол
        for (int i = 0; i < 6; i++) {
            int bx = cx - 32 + i * 11, d = 6 + (i * 5) % 9;
            g.fillPolygon(new int[]{bx, bx + 11, bx + 5}, new int[]{feet - 2, feet - 2, feet + d}, 3);
        }
        g.setColor(new Color(74, 66, 56));                                   // заплатки
        g.fillRect(cx - 14, feet - 50, 12, 10);
        g.fillRect(cx + 8, feet - 28, 10, 8);
        g.setColor(new Color(58, 52, 46));
        g.fillRect(cx - 22, feet - 14, 8, 6);

        g.setColor(cloak);                                                   // капюшон
        g.fillOval(cx - 19, feet - 130, 38, 44);
        g.setColor(new Color(6, 6, 10));
        g.fillOval(cx - 17, feet - 120, 20, 26);

        int tipX = cx - 14 - (int) (46 * out), tipY = feet - 78;             // рука
        g.setStroke(new BasicStroke(9f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(dark);
        g.drawLine(cx - 10, feet - 84, tipX, tipY);
        g.setStroke(new BasicStroke(1));
        g.setColor(new Color(140, 124, 112));
        g.fillRect(tipX - 5, tipY - 4, 8, 8);
        return new Point(tipX - 4, tipY);
    }

    private void drawBook(Graphics2D g, int cx, int cy, int w, int h) {
        g.setColor(new Color(0, 0, 0, 70));
        g.fillRect(cx - w / 2 + 2, cy - h / 2 + 3, w, h);
        g.setColor(new Color(140, 50, 50));
        g.fillRect(cx - w / 2, cy - h / 2, w, h);
        g.setColor(new Color(100, 30, 30));
        g.fillRect(cx - w / 2, cy - h / 2, w / 5, h);
        g.setColor(new Color(240, 235, 210));
        g.fillRect(cx - w / 2 + w / 5, cy + h / 2 - 3, w - w / 5, 3);
        g.setColor(GOLD);
        g.fillRect(cx - w / 10, cy - h / 4, w / 2, 3);
        g.fillRect(cx - w / 10, cy - h / 4 + 7, w / 2, 3);
    }

    // ================= ТЕКСТ =================
    private void drawSubtitle(Graphics2D g, Beat b) {
        if (b.line() == null || t < b.start()) return;
        int n = Math.min(b.line().length(), (t - b.start()) / TYPE);
        g.setColor(new Color(0, 0, 0, 205));
        g.fillRect(0, H - 120, W, 120);
        g.setFont(new Font("Monospaced", Font.BOLD, 20));
        g.setColor(GOLD);
        g.drawString("Эльза", 40, H - 90);
        g.setFont(new Font("Monospaced", Font.BOLD, 22));
        g.setColor(Color.WHITE);
        wrap(g, b.line(), n, 40, H - 58, W - 80, 30, false);
    }

    // перенос по словам; печатает только первые n символов полного текста (строки не «прыгают» при наборе)
    private static void wrap(Graphics2D g, String full, int n, int x, int y, int maxW, int lh, boolean center) {
        FontMetrics fm = g.getFontMetrics();
        int pos = 0;
        while (pos < full.length()) {
            int end = pos, lastSpace = -1;
            while (end < full.length() && fm.stringWidth(full.substring(pos, end + 1)) <= maxW) {
                if (full.charAt(end) == ' ') lastSpace = end;
                end++;
            }
            int lineEnd = end >= full.length() ? full.length() : (lastSpace > pos ? lastSpace : end);
            if (n > pos) {
                int lx = center ? x + (maxW - fm.stringWidth(full.substring(pos, lineEnd))) / 2 : x;
                g.drawString(full.substring(pos, Math.min(lineEnd, n)), lx, y);
            }
            y += lh;
            pos = lineEnd;
            if (pos < full.length() && full.charAt(pos) == ' ') pos++;
        }
    }

    private static void centered(Graphics2D g, String s, int y) {
        g.drawString(s, (W - g.getFontMetrics().stringWidth(s)) / 2, y);
    }

    private static void centeredAt(Graphics2D g, String s, int cx, int y) {
        g.drawString(s, cx - g.getFontMetrics().stringWidth(s) / 2, y);
    }

    // ================= УТИЛИТЫ =================
    private static float ease(float x) { x = Math.max(0f, Math.min(1f, x)); return x * x * (3 - 2 * x); }
    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }
    private static Color alpha(Color c, float a) { return Theme.alpha(c, (int) (255 * Math.max(0f, Math.min(1f, a)))); }

    private static void withAlpha(Graphics2D g, float a, Runnable r) {
        Composite old = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0f, Math.min(1f, a))));
        r.run();
        g.setComposite(old);
    }
}


// Админ-панель (Ctrl+Shift+5): пропуск испытаний и телепорт. Окно не забирает фокус у игры,
// поэтому с открытой панелью можно продолжать играть. Закрыть — той же комбинацией или крестиком.
class AdminPanel extends JDialog {
    private final GamePanel game;
    private final JLabel status = new JLabel(" ");
    private final JComboBox<String> rooms = new JComboBox<>();
    private final JCheckBox god = new JCheckBox("Бессмертие");
    private final javax.swing.Timer refresher;

    AdminPanel(GamePanel game, Window owner) {
        super(owner);
        this.game = game;
        setTitle("Админ-панель");
        setAlwaysOnTop(true);
        setFocusableWindowState(false);
        setResizable(false);
        setDefaultCloseOperation(HIDE_ON_CLOSE);
        refresher = new javax.swing.Timer(300, e -> status.setText(game.adminStatus()));

        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBorder(BorderFactory.createEmptyBorder(10, 12, 12, 12));
        p.setBackground(new Color(28, 28, 46));

        status.setFont(new Font("Monospaced", Font.BOLD, 12));
        status.setForeground(new Color(240, 200, 60));
        status.setAlignmentX(Component.LEFT_ALIGNMENT);
        status.setPreferredSize(new Dimension(320, 34));
        status.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        p.add(status);

        section(p, "ПРОПУСК ИСПЫТАНИЙ");
        button(p, "Пропустить эту комнату", game::adminSkipRoom);
        button(p, "Пройти всю локацию", game::adminSkipLocation);
        button(p, "Засчитать ВСЕ испытания", game::adminSkipAll);

        section(p, "ТЕЛЕПОРТ");
        rooms.setFocusable(false);
        rooms.setFont(new Font("Monospaced", Font.PLAIN, 12));
        rooms.setPrototypeDisplayValue("road7 — Спуск · Чревоугодие");
        rooms.setAlignmentX(Component.LEFT_ALIGNMENT);
        rooms.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        p.add(rooms);
        p.add(Box.createVerticalStrut(4));
        button(p, "Телепорт в выбранную комнату", () -> {
            Object it = rooms.getSelectedItem();
            if (it != null) game.adminGoto(it.toString().split(" ")[0]);
        });

        section(p, "К КОНЦУ ИГРЫ");
        button(p, "Последний спуск (локация 7)", () -> game.adminGoto("road7"));
        button(p, "Финал: по текущим выборам", () -> game.adminFinish(null));
        button(p, "Финал: РАЙ", () -> game.adminFinish(Sins.Ending.HEAVEN));
        button(p, "Финал: АД", () -> game.adminFinish(Sins.Ending.HELL));

        section(p, "ПРОЧЕЕ");
        god.setFocusable(false);
        god.setOpaque(false);
        god.setForeground(Color.WHITE);
        god.setFont(new Font("Monospaced", Font.BOLD, 13));
        god.setAlignmentX(Component.LEFT_ALIGNMENT);
        god.addActionListener(e -> game.adminSetGod(god.isSelected()));
        p.add(god);
        p.add(Box.createVerticalStrut(4));
        button(p, "Вылечить и снять эффекты", game::adminHeal);

        setContentPane(p);
    }

    private static void section(JPanel p, String text) {
        p.add(Box.createVerticalStrut(8));
        JLabel l = new JLabel(text);
        l.setFont(new Font("Monospaced", Font.BOLD, 12));
        l.setForeground(new Color(150, 150, 190));
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(l);
        p.add(Box.createVerticalStrut(4));
    }

    private static void button(JPanel p, String text, Runnable action) {
        JButton b = new JButton(text);
        b.setFont(new Font("Monospaced", Font.BOLD, 13));
        b.setForeground(Color.WHITE);
        b.setBackground(new Color(60, 60, 110));
        b.setOpaque(true);
        b.setBorderPainted(false);
        b.setFocusPainted(false);
        b.setFocusable(false);
        b.setAlignmentX(Component.LEFT_ALIGNMENT);
        b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        b.addActionListener(e -> action.run());
        p.add(b);
        p.add(Box.createVerticalStrut(4));
    }

    void open() {
        rooms.removeAllItems();
        for (String k : game.adminRoomKeys()) rooms.addItem(k + " — " + game.adminRoomName(k));
        god.setSelected(game.adminGod());
        status.setText(game.adminStatus());
        pack();
        place();
        refresher.start();
        setVisible(true);
        toFront();
    }

    void close() {
        refresher.stop();
        setVisible(false);
    }

    // справа от окна игры; если не помещается на экран — внахлёст на его правый край
    private void place() {
        Window o = getOwner();
        Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        int x = o.getX() + o.getWidth() + 8;
        if (x + getWidth() > screen.x + screen.width) x = o.getX() + o.getWidth() - getWidth() - 20;
        int y = Math.max(screen.y, Math.min(o.getY(), screen.y + screen.height - getHeight()));
        setLocation(Math.max(screen.x, x), y);
    }
}