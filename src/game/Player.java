package game;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;

public class Player extends Entity {
    public enum Dir { DOWN, UP, LEFT, RIGHT }
    public static final int ATTACK_FRAMES = 14;

    // палитра Эльзы (по пиксель-арту): светлые русые волосы, голубые глаза, серо-синее платье, босые ноги, бинт на ноге
    private static final Color HAIR = new Color(236, 200, 130), HAIR_HI = new Color(248, 224, 160), HAIR_SH = new Color(208, 168, 100);
    private static final Color SKIN = new Color(240, 214, 208), SKIN_SH = new Color(220, 186, 182);
    private static final Color DRESS = new Color(124, 124, 160), DRESS_DARK = new Color(96, 96, 134), DRESS_HI = new Color(146, 146, 178);
    private static final Color EYE = new Color(86, 176, 202), LASH = new Color(70, 52, 60);
    private static final Color BANDAGE = new Color(204, 180, 142), BANDAGE_LINE = new Color(150, 124, 96);

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

        // длинные волосы сзади
        box(g, HAIR_SH, sx, sy, r, 8, 4, 10, 22);
        box(g, HAIR, sx, sy, r, 9 + sway, 22, 8, 5);

        // босые ноги (дальняя и ближняя); на ближней — бинт
        int l1 = air ? 2 : (step == 0 ? 0 : 2);
        int l2 = air ? 2 : (step == 1 ? 0 : 2);
        int o1 = air ? -2 : 0, o2 = air ? 2 : 0;
        box(g, SKIN_SH, sx, sy, r, 12 + o1, 36, 4, 8 - l1);
        box(g, SKIN_SH, sx, sy, r, 11 + o1, 44 - l1, 6, 2);
        box(g, SKIN, sx, sy, r, 19 + o2, 36, 4, 8 - l2);
        box(g, SKIN, sx, sy, r, 19 + o2, 44 - l2, 7, 2);
        box(g, BANDAGE, sx, sy, r, 19 + o2, 38, 4, 5);
        box(g, BANDAGE_LINE, sx, sy, r, 19 + o2, 39, 4, 1);
        box(g, BANDAGE_LINE, sx, sy, r, 19 + o2, 41, 4, 1);

        // просторное платье
        box(g, DRESS, sx, sy, r, 9, 20, 18, 17);
        box(g, DRESS_DARK, sx, sy, r, 9, 20, 4, 17);
        box(g, DRESS_HI, sx, sy, r, 17, 21, 3, 8);
        box(g, DRESS_DARK, sx, sy, r, 9, 34, 18, 3);

        // рука в широком рукаве
        box(g, DRESS_DARK, sx, sy, r, 14 + swing * 2, 21, 8, 9);
        box(g, DRESS, sx, sy, r, 15 + swing * 2, 21, 6, 3);
        box(g, SKIN, sx, sy, r, 15 + swing * 2, 30, 5, 4);

        // голова
        box(g, HAIR, sx, sy, r, 10, 1, 17, 12);
        box(g, SKIN, sx, sy, r, 18, 8, 9, 12);
        box(g, SKIN_SH, sx, sy, r, 17, 19, 5, 2);
        box(g, HAIR, sx, sy, r, 18, 5, 9, 4);                      // чёлка
        box(g, HAIR_HI, sx, sy, r, 13, 2, 9, 2);
        box(g, HAIR, sx, sy, r, 10, 8, 7, 15);                     // прядь у щеки
        box(g, EYE, sx, sy, r, 22, 12, 3, 3);
        box(g, LASH, sx, sy, r, 22, 11, 4, 1);
    }

    // ---------- вид спереди / сзади (для комнат сверху) ----------
    private void drawFront(Graphics2D g, int sx, int sy, int step, int tick) {
        boolean down = facing == Dir.DOWN;
        int sway = (tick / 10) % 2;
        int l1 = step == 0 ? 0 : 2, l2 = step == 1 ? 0 : 2;

        g.setColor(SKIN);                                          // босые ноги
        g.fillRect(sx + 12, sy + 36, 4, 8 - l1);
        g.fillRect(sx + 20, sy + 36, 4, 8 - l2);
        g.fillRect(sx + 11, sy + 44 - l1, 6, 2);
        g.fillRect(sx + 19, sy + 44 - l2, 6, 2);
        g.setColor(SKIN_SH);
        g.fillRect(sx + 12, sy + 36, 1, 8 - l1);
        g.setColor(BANDAGE);                                       // бинт на правой ноге (слева на экране — со спины)
        int bx = down ? sx + 20 : sx + 12;
        g.fillRect(bx, sy + 38, 4, 5);
        g.setColor(BANDAGE_LINE);
        g.fillRect(bx, sy + 39, 4, 1);
        g.fillRect(bx, sy + 41, 4, 1);

        g.setColor(DRESS);                                         // платье
        g.fillRect(sx + 8, sy + 20, 20, 17);
        g.setColor(DRESS_DARK);
        g.fillRect(sx + 8, sy + 34, 20, 3);
        g.fillRect(sx + 8, sy + 20, 4, 14);
        g.setColor(DRESS_HI);
        g.fillRect(sx + 18, sy + 22, 3, 9);
        g.setColor(DRESS_DARK);                                    // широкие рукава
        g.fillRect(sx + 3, sy + 21, 6, 10);
        g.fillRect(sx + 27, sy + 21, 6, 10);
        g.setColor(DRESS);
        g.fillRect(sx + 4, sy + 21, 4, 3);
        g.fillRect(sx + 28, sy + 21, 4, 3);
        g.setColor(SKIN);                                          // ладони
        g.fillRect(sx + 3, sy + 31, 5, 4);
        g.fillRect(sx + 28, sy + 31, 5, 4);

        if (down) {
            g.setColor(HAIR_SH);                                   // волосы за плечами
            g.fillRect(sx + 6, sy + 8, 24, 12);
            g.setColor(HAIR);
            g.fillRect(sx + 7, sy + 1, 22, 14);
            g.fillRect(sx + 5, sy + 10 + sway, 5, 16);
            g.fillRect(sx + 26, sy + 10 + sway, 5, 16);
            g.setColor(SKIN);                                      // лицо и шея
            g.fillRect(sx + 11, sy + 8, 14, 12);
            g.fillRect(sx + 16, sy + 19, 4, 3);
            g.setColor(HAIR);                                      // чёлка
            g.fillRect(sx + 9, sy + 4, 18, 5);
            g.fillRect(sx + 9, sy + 8, 4, 4);
            g.fillRect(sx + 23, sy + 8, 4, 4);
            g.setColor(HAIR_HI);
            g.fillRect(sx + 12, sy + 2, 9, 2);
            g.setColor(EYE);                                       // голубые глаза
            g.fillRect(sx + 13, sy + 12, 3, 3);
            g.fillRect(sx + 21, sy + 12, 3, 3);
            g.setColor(LASH);
            g.fillRect(sx + 13, sy + 11, 3, 1);
            g.fillRect(sx + 21, sy + 11, 3, 1);
        } else {
            g.setColor(HAIR);                                      // голова сзади: волосы закрывают всё
            g.fillRect(sx + 7, sy + 1, 22, 22);
            g.fillRect(sx + 5, sy + 8, 4, 18);
            g.fillRect(sx + 27, sy + 8, 4, 18);
            g.setColor(HAIR_SH);
            g.fillRect(sx + 9, sy + 14, 18, 10);
            g.fillRect(sx + 14 + sway, sy + 22, 8, 5);
            g.setColor(HAIR_HI);
            g.fillRect(sx + 12, sy + 2, 9, 2);
        }
    }
}