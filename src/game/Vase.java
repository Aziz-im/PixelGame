package game;

import java.awt.*;

public class Vase {
    public enum State { INTACT, CHECKED, BROKEN }

    private static final Color[] COLORS = {
            new Color(176, 98, 62), new Color(72, 132, 142), new Color(120, 86, 160)
    };

    public final int col, row;
    public State state = State.INTACT;
    public boolean hasCandle;
    private final int style;

    public Vase(int col, int row) {
        this.col = col;
        this.row = row;
        this.style = (col * 7 + row * 3) % COLORS.length;
    }

    public Rectangle box()  { return new Rectangle(col * Room.TILE + 8, row * Room.TILE + 6, 32, 40); }
    public int centerX()    { return col * Room.TILE + Room.TILE / 2; }
    public int centerY()    { return row * Room.TILE + Room.TILE / 2; }

    private static Color darker(Color c) {
        return new Color((int) (c.getRed() * 0.7), (int) (c.getGreen() * 0.7), (int) (c.getBlue() * 0.7));
    }

    public void draw(Graphics2D g, int tick) {
        int x = col * Room.TILE, y = row * Room.TILE;
        Color body = COLORS[style];

        if (state == State.BROKEN) {                              // осколки
            g.setColor(body);
            g.fillPolygon(new int[]{x + 8, x + 18, x + 14}, new int[]{y + 46, y + 46, y + 40}, 3);
            g.fillPolygon(new int[]{x + 22, x + 34, x + 28}, new int[]{y + 46, y + 46, y + 38}, 3);
            g.setColor(darker(body));
            g.fillPolygon(new int[]{x + 34, x + 42, x + 40}, new int[]{y + 46, y + 46, y + 42}, 3);
            return;
        }

        g.setColor(new Color(0, 0, 0, 60));
        g.fillOval(x + 8, y + 42, 32, 6);
        g.setColor(darker(body));                                 // горлышко и ободок
        g.fillRect(x + 18, y + 8, 12, 10);
        g.fillRect(x + 15, y + 6, 18, 5);
        g.setColor(body);                                         // тело
        g.fillOval(x + 9, y + 16, 30, 30);
        g.setColor(new Color(255, 255, 255, 60));
        g.fillRect(x + 14, y + 22, 4, 10);

        if (state == State.CHECKED) {                             // проверена: тёмное горлышко и галочка
            g.setColor(new Color(20, 14, 26));
            g.fillOval(x + 17, y + 6, 14, 4);
            g.setColor(new Color(120, 230, 140));
            Stroke old = g.getStroke();
            g.setStroke(new BasicStroke(3));
            g.drawLine(x + 17, y + 30, x + 22, y + 36);
            g.drawLine(x + 22, y + 36, x + 32, y + 24);
            g.setStroke(old);
        }
    }
}