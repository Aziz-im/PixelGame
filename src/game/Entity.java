package game;

import java.awt.*;

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