package game;

import java.awt.*;
import java.util.Random;

public class Mob extends Entity {
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