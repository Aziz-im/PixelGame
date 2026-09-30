package game;

import java.awt.*;

// Предметы, которые нужно собрать в испытании: корона, розы, угощение, сферы снов.
public class Pickup {
    public enum Kind { CROWN, ROSE, FOOD, ORB }

    private static final int T = Room.TILE;

    public final Kind kind;
    public final int col, row;
    public boolean taken;

    public Pickup(Kind kind, int col, int row) {
        this.kind = kind;
        this.col = col;
        this.row = row;
    }

    public Rectangle box() { return new Rectangle(col * T + 8, row * T + 8, 32, 36); }

    public void draw(Graphics2D g, int tick) {
        if (taken) return;
        int x = col * T, y = row * T + (int) (Math.sin(tick * 0.08 + col) * 4);   // парит вверх-вниз

        Color glow = switch (kind) {
            case CROWN -> new Color(255, 215, 90);
            case ROSE  -> new Color(255, 100, 160);
            case FOOD  -> new Color(255, 180, 90);
            case ORB   -> new Color(120, 180, 255);
        };
        g.setColor(Theme.alpha(glow, 46));
        g.fillOval(x + 2, y + 4, 44, 44);

        switch (kind) {
            case CROWN -> {
                g.setColor(new Color(240, 190, 40));
                g.fillPolygon(new int[]{x + 8, x + 8, x + 16, x + 24, x + 32, x + 40, x + 40},
                        new int[]{y + 34, y + 14, y + 23, y + 10, y + 23, y + 14, y + 34}, 7);
                g.setColor(new Color(190, 140, 20));
                g.fillRect(x + 8, y + 30, 32, 6);
                g.setColor(new Color(220, 50, 70));
                g.fillRect(x + 22, y + 24, 5, 5);
                g.setColor(new Color(80, 160, 255));
                g.fillRect(x + 12, y + 26, 4, 4);
                g.fillRect(x + 32, y + 26, 4, 4);
            }
            case ROSE -> {
                g.setColor(new Color(40, 140, 60));
                g.fillRect(x + 23, y + 26, 3, 18);
                g.fillOval(x + 26, y + 32, 10, 5);
                g.setColor(new Color(200, 30, 90));
                g.fillOval(x + 14, y + 8, 20, 20);
                g.setColor(new Color(240, 80, 140));
                g.fillOval(x + 18, y + 12, 12, 12);
                g.setColor(new Color(150, 20, 70));
                g.fillOval(x + 21, y + 15, 6, 6);
            }
            case FOOD -> {
                if (col % 2 == 0) {                                    // яблоко
                    g.setColor(new Color(200, 40, 40));
                    g.fillOval(x + 10, y + 14, 28, 28);
                    g.setColor(new Color(240, 100, 90));
                    g.fillRect(x + 16, y + 20, 4, 8);
                    g.setColor(new Color(90, 60, 30));
                    g.fillRect(x + 23, y + 8, 3, 8);
                    g.setColor(new Color(60, 170, 70));
                    g.fillOval(x + 26, y + 8, 10, 6);
                } else {                                               // пирог
                    g.setColor(new Color(170, 110, 50));
                    g.fillOval(x + 6, y + 20, 36, 22);
                    g.setColor(new Color(220, 160, 80));
                    g.fillOval(x + 8, y + 16, 32, 20);
                    g.setColor(new Color(250, 240, 220));
                    g.fillOval(x + 16, y + 18, 16, 8);
                    g.setColor(new Color(220, 50, 60));
                    g.fillOval(x + 21, y + 14, 6, 6);
                }
            }
            case ORB -> {
                int r = 14 + (int) (Math.sin(tick * 0.1 + col) * 2);
                g.setColor(new Color(90, 140, 230));
                g.fillOval(x + 24 - r, y + 26 - r, r * 2, r * 2);
                g.setColor(new Color(160, 210, 255));
                g.fillOval(x + 24 - r + 4, y + 26 - r + 4, r * 2 - 10, r * 2 - 10);
                g.setColor(Color.WHITE);
                g.fillOval(x + 24 - r + 7, y + 26 - r + 6, 7, 5);
            }
        }
    }
}