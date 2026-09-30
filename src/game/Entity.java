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