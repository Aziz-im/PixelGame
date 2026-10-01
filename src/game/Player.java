package game;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;

public class Player extends Entity {
    public enum Dir { DOWN, UP, LEFT, RIGHT }
    public static final int ATTACK_FRAMES = 14;

    // палитра героини: тёмно-фиолетовые волосы, красно-чёрная одежда, оранжевые ботинки
    private static final Color HAIR = new Color(34, 24, 58), HAIR_HI = new Color(78, 54, 120);
    private static final Color SKIN = new Color(236, 196, 174);
    private static final Color RED = new Color(198, 46, 64), RED_DARK = new Color(128, 26, 46);
    private static final Color DARK = new Color(30, 24, 40), GOLD = new Color(238, 184, 64);
    private static final Color BOOT = new Color(238, 134, 38), BOOT_DARK = new Color(168, 86, 22);

    public final int maxHp = 5;
    public Dir facing = Dir.DOWN;
    public boolean moving;
    public int invul;                 // кадры неуязвимости после удара
    public int attackTimer;           // идёт взмах, пока > 0
    public int attackCooldown;
    public Color blade = new Color(210, 220, 235);

    public double vy;                 // вертикальная скорость (вид сбоку)
    public boolean onGround;          // стоит на полу (вид сбоку)

    public Player() {
        super(26, 18, 5);
    }

    // прямоугольник в координатах спрайта; при взгляде влево зеркалится
    private void box(Graphics2D g, Color c, int sx, int sy, boolean r, int dx, int dy, int bw, int bh) {
        g.setColor(c);
        g.fillRect(r ? sx + dx : sx + 36 - dx - bw, sy + dy, bw, bh);
    }

    @Override
    public void draw(Graphics2D g, int tick) {
        if (invul > 0 && (invul / 4) % 2 == 0) return;        // мигание после удара

        int sx = x + w / 2 - 18, sy = y + h - 46;             // спрайт 36x46, ноги на хитбоксе
        int step = moving ? (tick / 8) % 2 : 0;

        g.setColor(new Color(0, 0, 0, 70));                   // тень
        g.fillOval(x - 2, y + h - 8, w + 4, 12);

        if (facing == Dir.LEFT || facing == Dir.RIGHT) drawSide(g, sx, sy, step, tick);
        else drawFront(g, sx, sy, step, tick);

        // взмах мечом
        if (attackTimer > 0) {
            double base = switch (facing) {
                case RIGHT -> 0.0;
                case DOWN -> Math.PI / 2;
                case LEFT -> Math.PI;
                default -> -Math.PI / 2;
            };
            double t = 1.0 - attackTimer / (double) ATTACK_FRAMES;   // 0..1
            double angle = base - 1.1 + 2.2 * t;
            AffineTransform old = g.getTransform();
            Stroke oldStroke = g.getStroke();
            g.translate(centerX(), y + h - 22);
            double swept = Math.toDegrees(2.2 * t);                   // сколько дуги уже прошёл клинок
            double startDeg = -Math.toDegrees(angle);
            g.setStroke(new BasicStroke(26f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL));
            g.setColor(new Color(225, 238, 255, 60));                 // широкий шлейф взмаха
            g.draw(new Arc2D.Double(-40, -40, 80, 80, startDeg, swept, Arc2D.OPEN));
            g.setStroke(new BasicStroke(4f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL));
            g.setColor(new Color(255, 255, 255, 150));                // яркая кромка у острия
            g.draw(new Arc2D.Double(-54, -54, 108, 108, startDeg, swept, Arc2D.OPEN));
            g.setStroke(oldStroke);
            g.rotate(angle);
            SwordArt.draw(g, blade);
            g.setTransform(old);
        }
    }

    // ---------- вид сбоку (профиль) ----------
    private void drawSide(Graphics2D g, int sx, int sy, int step, int tick) {
        boolean r = facing == Dir.RIGHT;
        boolean air = !onGround && vy != 0;
        int sway = (tick / 10) % 2;
        int swing = moving && !air ? (step == 0 ? -1 : 1) : 0;

        // хвост волос (сзади)
        box(g, HAIR, sx, sy, r, 3, 8, 6, 16);
        box(g, HAIR, sx, sy, r, 2 + sway, 22, 5, 6);
        box(g, HAIR_HI, sx, sy, r, 4, 10, 2, 8);

        // ноги и ботинки
        int l1 = air ? 2 : (step == 0 ? 0 : 2);
        int l2 = air ? 2 : (step == 1 ? 0 : 2);
        int o1 = air ? -2 : 0, o2 = air ? 2 : 0;
        box(g, DARK, sx, sy, r, 12 + o1, 34, 6, 6 - l1);
        box(g, BOOT, sx, sy, r, 11 + o1, 40 - l1, 8, 6);
        box(g, BOOT_DARK, sx, sy, r, 11 + o1, 44 - l1, 8, 2);
        box(g, DARK, sx, sy, r, 19 + o2, 34, 6, 6 - l2);
        box(g, BOOT, sx, sy, r, 19 + o2, 40 - l2, 9, 6);
        box(g, BOOT_DARK, sx, sy, r, 19 + o2, 44 - l2, 9, 2);

        // куртка
        box(g, RED, sx, sy, r, 11, 21, 15, 13);
        box(g, RED_DARK, sx, sy, r, 11, 30, 15, 4);
        box(g, GOLD, sx, sy, r, 11, 31, 15, 2);
        box(g, GOLD, sx, sy, r, 17, 20, 6, 2);

        // рука
        box(g, RED_DARK, sx, sy, r, 14 + swing * 2, 22, 6, 11);
        box(g, SKIN, sx, sy, r, 14 + swing * 2, 32, 5, 3);

        // голова
        box(g, HAIR, sx, sy, r, 9, 1, 19, 15);
        box(g, SKIN, sx, sy, r, 19, 9, 9, 11);
        box(g, HAIR, sx, sy, r, 9, 6, 19, 4);
        box(g, HAIR_HI, sx, sy, r, 12, 2, 8, 2);
        box(g, SKIN, sx, sy, r, 17, 19, 5, 2);
        box(g, Color.BLACK, sx, sy, r, 23, 13, 3, 3);
        box(g, GOLD, sx, sy, r, 8, 8, 3, 3);                       // резинка хвоста
    }

    // ---------- вид спереди / сзади (для комнат сверху) ----------
    private void drawFront(Graphics2D g, int sx, int sy, int step, int tick) {
        boolean down = facing == Dir.DOWN;
        int sway = (tick / 10) % 2;
        int l1 = step == 0 ? 0 : 2, l2 = step == 1 ? 0 : 2;

        g.setColor(DARK);                                          // ноги
        g.fillRect(sx + 10, sy + 34, 6, 6 - l1);
        g.fillRect(sx + 20, sy + 34, 6, 6 - l2);
        g.setColor(BOOT);
        g.fillRect(sx + 9, sy + 40 - l1, 8, 6);
        g.fillRect(sx + 19, sy + 40 - l2, 8, 6);
        g.setColor(BOOT_DARK);
        g.fillRect(sx + 9, sy + 44 - l1, 8, 2);
        g.fillRect(sx + 19, sy + 44 - l2, 8, 2);

        g.setColor(RED);                                           // куртка
        g.fillRect(sx + 8, sy + 20, 20, 14);
        g.setColor(RED_DARK);
        g.fillRect(sx + 8, sy + 30, 20, 4);
        g.fillRect(sx + 5, sy + 21, 4, 11);
        g.fillRect(sx + 27, sy + 21, 4, 11);
        g.setColor(GOLD);
        g.fillRect(sx + 8, sy + 31, 20, 2);
        g.setColor(SKIN);
        g.fillRect(sx + 5, sy + 32, 4, 3);
        g.fillRect(sx + 27, sy + 32, 4, 3);

        if (down) {
            g.setColor(DARK);
            g.fillRect(sx + 16, sy + 21, 4, 10);
            g.setColor(GOLD);
            g.fillRect(sx + 15, sy + 20, 6, 2);

            g.setColor(HAIR);                                      // голова спереди
            g.fillRect(sx + 29, sy + 8 + sway, 4, 14);             // хвост сбоку
            g.fillRect(sx + 7, sy + 1, 22, 16);
            g.fillRect(sx + 5, sy + 6, 4, 12);
            g.fillRect(sx + 27, sy + 6, 4, 12);
            g.setColor(SKIN);
            g.fillRect(sx + 11, sy + 9, 14, 11);
            g.setColor(HAIR);
            g.fillRect(sx + 9, sy + 6, 18, 4);
            g.setColor(HAIR_HI);
            g.fillRect(sx + 12, sy + 2, 8, 2);
            g.setColor(Color.BLACK);
            g.fillRect(sx + 14, sy + 13, 3, 3);
            g.fillRect(sx + 21, sy + 13, 3, 3);
        } else {
            g.setColor(HAIR);                                      // голова сзади
            g.fillRect(sx + 7, sy + 1, 22, 19);
            g.fillRect(sx + 5, sy + 6, 4, 12);
            g.fillRect(sx + 27, sy + 6, 4, 12);
            g.setColor(HAIR_HI);
            g.fillRect(sx + 12, sy + 2, 8, 2);
            g.setColor(HAIR);                                      // хвост по спине
            g.fillRect(sx + 15, sy + 14, 6, 18);
            g.fillRect(sx + 14 + sway, sy + 30, 6, 5);
            g.setColor(GOLD);
            g.fillRect(sx + 15, sy + 13, 6, 3);
        }
    }
}