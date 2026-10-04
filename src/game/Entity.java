package game;

import java.awt.*;
import java.util.Random;

public abstract class Entity {
    public int x, y;             // левый верхний угол "ног" (хитбокса)
    public final int w, h;       // размер хитбокса
    public int hp;
    public int flash;            // кадры красной вспышки после удара

    protected Entity(int w, int h, int hp) {
        this.w = w;
        this.h = h;
        this.hp = hp;
    }

    public void setTile(int col, int row) {
        x = col * Room.TILE + (Room.TILE - w) / 2;
        y = row * Room.TILE + (Room.TILE - h) / 2;
    }

    public int feetY()   { return y + h; }
    public int centerX() { return x + w / 2; }
    public int centerY() { return y + h / 2; }
    public Rectangle box() { return new Rectangle(x, y, w, h); }

    // Двигается по 1 пикселю, не заходя в стены. По осям отдельно, поэтому скользит вдоль стен.
    public void move(Room room, int dx, int dy) {
        int sx = Integer.signum(dx), sy = Integer.signum(dy);
        for (int i = 0; i < Math.abs(dx); i++) {
            if (!room.blocked(x + sx, y, w, h)) x += sx; else break;
        }
        for (int i = 0; i < Math.abs(dy); i++) {
            if (!room.blocked(x, y + sy, w, h)) y += sy; else break;
        }
    }

    public abstract void draw(Graphics2D g, int tick);
}

// Кошка — индикатор реальности. Она по-настоящему живая, поэтому контактирует только с тем, что существует:
// к настоящей еде (мусорная комната, рыба) тянется и мяукает, а на иллюзии (фуршет, столовая слуг) не реагирует.
// Сами реакции задаются в GamePanel через Prop.catHint (одноразовые подсказки, пока кошка сидит на плечах).
class Cat extends Entity {
    public enum State { SITTING, FOLLOWING, DEAD }
    public State state = State.SITTING;
    public boolean onShoulder;                    // true = сидит на плечах игрока

    public Cat(int col, int row) {
        super(22, 14, 3);
        setTile(col, row);
    }

    // на плечах кот рисуется поверх героя
    @Override
    public int feetY() { return onShoulder ? y + h + 30 : y + h; }

    public void follow(Room room, Player p) {
        if (onShoulder) {
            int off = p.facing == Player.Dir.RIGHT ? -12 : p.facing == Player.Dir.LEFT ? 12 : 10;
            x = p.centerX() - w / 2 + off;
            y = p.y + 2 - h - 6;
            return;
        }
        int dx = p.centerX() - centerX();
        int dy = p.centerY() - centerY();
        double dist = Math.hypot(dx, dy);
        if (dist > 400) {                        // отстал слишком сильно — телепорт к игроку
            x = p.x;
            y = p.y;
        } else if (room.sideView) {              // вид сбоку: ходит по земле, гравитация тянет вниз
            if (dist > 64 && Math.abs(dx) >= 4) move(room, Integer.signum(dx) * 3, 0);
            move(room, 0, 4);
        } else if (dist > 64) {
            int sx = Math.abs(dx) < 4 ? 0 : Integer.signum(dx);
            int sy = Math.abs(dy) < 4 ? 0 : Integer.signum(dy);
            move(room, sx * 3, sy * 3);
        }
    }

    @Override
    public void draw(Graphics2D g, int tick) {
        int sx = x + w / 2 - 13, sy = y + h - 24;
        Color fur = flash > 0 ? new Color(255, 200, 200) : new Color(214, 132, 58);
        Color stripe = new Color(160, 90, 36);

        if (!onShoulder) {
            g.setColor(new Color(0, 0, 0, 70));
            g.fillOval(x - 2, y + h - 6, w + 4, 10);
        }

        int wag = (tick / 12) % 2 == 0 ? 0 : 2;                     // хвост
        g.setColor(fur);
        g.fillRect(sx + 22, sy + 10 - wag, 4, 12);
        g.setColor(stripe);
        g.fillRect(sx + 22, sy + 10 - wag, 4, 3);

        g.setColor(fur);                                             // тело
        g.fillRect(sx + 4, sy + 10, 18, 14);
        g.setColor(stripe);
        g.fillRect(sx + 8, sy + 12, 2, 6);
        g.fillRect(sx + 14, sy + 12, 2, 6);
        g.setColor(new Color(240, 220, 190));                        // лапки
        g.fillRect(sx + 6, sy + 21, 5, 3);
        g.fillRect(sx + 15, sy + 21, 5, 3);

        g.setColor(fur);                                             // голова
        g.fillRect(sx + 2, sy + 2, 22, 14);
        g.fillPolygon(new int[]{sx + 2, sx + 2, sx + 9}, new int[]{sy + 4, sy - 4, sy + 4}, 3);
        g.fillPolygon(new int[]{sx + 24, sx + 24, sx + 17}, new int[]{sy + 4, sy - 4, sy + 4}, 3);
        g.setColor(stripe);
        g.fillRect(sx + 11, sy + 2, 4, 4);
        g.setColor(new Color(60, 200, 90));                          // глаза
        g.fillRect(sx + 7, sy + 7, 3, 4);
        g.fillRect(sx + 16, sy + 7, 3, 4);
        g.setColor(new Color(240, 150, 160));                        // нос
        g.fillRect(sx + 12, sy + 11, 2, 2);
    }
}
// ---------- Mob (раньше был в Mob.java) ----------

class Mob extends Entity {
    public enum Kind { SLIME, BEETLE }

    private static final Random RND = new Random();
    public final Kind kind;
    private int dirX, dirY, think;

    public Mob(Kind kind, int col, int row) {
        super(kind == Kind.SLIME ? 28 : 22,      // ширина хитбокса
                kind == Kind.SLIME ? 20 : 16,      // высота
                kind == Kind.SLIME ? 3 : 2);       // здоровье
        this.kind = kind;
        setTile(col, row);
    }

    public void update(Room room, Player p, int tick) {
        if (flash > 0) flash--;
        int speed = kind == Kind.SLIME ? 1 : 2;
        int ddx = p.centerX() - centerX();
        int ddy = p.centerY() - centerY();

        // вид сбоку: ходит только по горизонтали, а вниз его тянет гравитация
        if (room.sideView) {
            if (Math.hypot(ddx, ddy) < 220 && Math.abs(ddx) >= 4) {
                move(room, Integer.signum(ddx) * speed, 0);
            } else if (Math.hypot(ddx, ddy) >= 220) {
                if (think-- <= 0) {
                    dirX = RND.nextInt(3) - 1;
                    think = 40 + RND.nextInt(80);
                }
                if (tick % 2 == 0) move(room, dirX * speed, 0);
            }
            move(room, 0, 4);
            return;
        }

        if (Math.hypot(ddx, ddy) < 220) {                    // заметил игрока — идёт к нему
            int sx = Math.abs(ddx) < 4 ? 0 : Integer.signum(ddx);
            int sy = Math.abs(ddy) < 4 ? 0 : Integer.signum(ddy);
            move(room, sx * speed, sy * speed);
        } else {                                             // бродит сам по себе
            if (think-- <= 0) {
                dirX = RND.nextInt(3) - 1;
                dirY = RND.nextInt(3) - 1;
                think = 40 + RND.nextInt(80);
            }
            if (tick % 2 == 0) move(room, dirX * speed, dirY * speed);
        }
    }

    // удар по мобу: урон + отбрасывание
    public void hit(Room room, int dmg, int fromX, int fromY) {
        hp -= dmg;
        flash = 10;
        move(room, Integer.signum(centerX() - fromX) * 18, Integer.signum(centerY() - fromY) * 18);
    }

    @Override
    public void draw(Graphics2D g, int tick) {
        g.setColor(new Color(0, 0, 0, 70));
        g.fillOval(x - 2, y + h - 6, w + 4, 10);

        if (kind == Kind.SLIME) {
            int bob = (tick / 12) % 2;
            Color top = flash > 0 ? new Color(255, 200, 200) : new Color(58, 170, 150);
            Color bottom = flash > 0 ? new Color(255, 150, 150) : new Color(32, 110, 100);
            g.setColor(bottom);
            g.fillRect(x, y + 6 + bob, w, h - 6 - bob);
            g.setColor(top);
            g.fillRect(x + 2, y + 2 + bob, w - 4, h - 8 - bob);
            g.setColor(new Color(150, 240, 215));
            g.fillRect(x + 5, y + 4 + bob, 6, 3);
            g.setColor(Color.BLACK);
            g.fillRect(x + 7, y + 9 + bob, 3, 4);
            g.fillRect(x + w - 10, y + 9 + bob, 3, 4);
        } else {
            int l = (tick / 6) % 2;
            g.setColor(new Color(40, 20, 40));                       // лапки
            g.fillRect(x - 2, y + 2 + l, 4, 3);
            g.fillRect(x - 2, y + 9 - l, 4, 3);
            g.fillRect(x + w - 2, y + 2 + (1 - l), 4, 3);
            g.fillRect(x + w - 2, y + 9 - (1 - l), 4, 3);
            g.setColor(flash > 0 ? new Color(255, 170, 170) : new Color(110, 40, 90));
            g.fillRect(x + 2, y, w - 4, h);                          // панцирь
            g.setColor(new Color(70, 24, 60));
            g.fillRect(x + w / 2 - 1, y, 2, h);
            g.setColor(new Color(240, 140, 30));                     // глаза
            g.fillRect(x + 4, y + 2, 3, 3);
            g.fillRect(x + w - 7, y + 2, 3, 3);
        }
    }
}