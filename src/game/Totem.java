package game;

import java.awt.*;

// Тотем ярости: его нужно разбить мечом (L). Три удара.
public class Totem {
    private static final int T = Room.TILE;

    public final int col, row;
    public int hp = 3;
    public int flash;                 // вспышка после удара
    public boolean broken;

    public Totem(int col, int row) {
        this.col = col;
        this.row = row;
    }

    public Rectangle box() { return new Rectangle(col * T + 6, row * T + 2, 36, 44); }

    public void hit() {
        if (broken) return;
        hp--;
        flash = 10;
        if (hp <= 0) broken = true;
    }

    public void draw(Graphics2D g, int tick) {
        int x = col * T, y = row * T;
        if (broken) {                                                  // обломки
            g.setColor(new Color(70, 30, 34));
            g.fillPolygon(new int[]{x + 8, x + 22, x + 16}, new int[]{y + 46, y + 46, y + 36}, 3);
            g.fillPolygon(new int[]{x + 24, x + 40, x + 32}, new int[]{y + 46, y + 46, y + 32}, 3);
            g.setColor(new Color(110, 48, 50));
            g.fillRect(x + 16, y + 42, 10, 4);
            return;
        }
        g.setColor(new Color(0, 0, 0, 70));
        g.fillOval(x + 6, y + 40, 36, 8);
        Color stone = flash > 0 ? new Color(255, 190, 170) : new Color(104, 46, 50);
        Color dark = flash > 0 ? new Color(230, 140, 120) : new Color(62, 26, 32);
        g.setColor(dark);
        g.fillRect(x + 8, y + 8, 32, 38);
        g.setColor(stone);
        g.fillRect(x + 10, y + 6, 28, 36);
        g.fillPolygon(new int[]{x + 10, x + 5, x + 17}, new int[]{y + 10, y - 3, y + 6}, 3);      // рога
        g.fillPolygon(new int[]{x + 38, x + 43, x + 31}, new int[]{y + 10, y - 3, y + 6}, 3);
        g.setColor((tick / 10) % 2 == 0 ? new Color(255, 170, 40) : new Color(230, 90, 30));      // глаза
        g.fillRect(x + 15, y + 16, 6, 5);
        g.fillRect(x + 27, y + 16, 6, 5);
        g.setColor(new Color(20, 8, 10));                                                          // пасть
        g.fillRect(x + 16, y + 30, 16, 4);
        g.setColor(new Color(30, 12, 16));                                                         // трещины
        if (hp <= 2) g.fillRect(x + 22, y + 8, 2, 10);
        if (hp <= 1) {
            g.fillRect(x + 14, y + 22, 10, 2);
            g.fillRect(x + 30, y + 24, 2, 12);
        }
    }
}