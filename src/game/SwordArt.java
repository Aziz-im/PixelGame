package game;

import java.awt.*;

// Рисует меч, направленный вправо (+x): навершие у начала координат, остриё примерно в точке x = 63.
// Один и тот же меч используется и при взмахе в руках героини, и как иконка в инвентаре.
public final class SwordArt {
    private SwordArt() {}

    public static void draw(Graphics2D g, Color blade) {
        Color light = Theme.mix(blade, Color.WHITE, 0.65);
        Color shade = Theme.mix(blade, Color.BLACK, 0.30);
        Color gold = new Color(240, 200, 60), goldDark = new Color(172, 124, 28);

        // клинок: сужается к острию, нижняя половина в тени, сверху блик
        g.setColor(blade);
        g.fillPolygon(new int[]{16, 50, 63, 50, 16}, new int[]{-5, -5, 0, 5, 5}, 5);
        g.setColor(shade);
        g.fillPolygon(new int[]{16, 63, 50, 16}, new int[]{0, 0, 5, 5}, 4);
        g.setColor(light);
        g.fillPolygon(new int[]{16, 50, 58, 16}, new int[]{-5, -5, -2, -2}, 4);
        g.setColor(shade);                                         // долы (желобок) вдоль клинка
        g.fillRect(19, -1, 28, 2);

        // гарда с камнем
        g.setColor(gold);
        g.fillRect(11, -11, 5, 22);
        g.setColor(goldDark);
        g.fillRect(11, -11, 5, 3);
        g.fillRect(11, 8, 5, 3);
        g.setColor(new Color(220, 50, 70));
        g.fillRect(12, -2, 3, 4);

        // рукоять с обмоткой и навершие
        g.setColor(new Color(104, 66, 34));
        g.fillRect(3, -2, 8, 4);
        g.setColor(new Color(150, 106, 58));
        g.fillRect(5, -2, 1, 4);
        g.fillRect(8, -2, 1, 4);
        g.setColor(gold);
        g.fillOval(-3, -4, 7, 8);
        g.setColor(goldDark);
        g.fillOval(-1, -2, 3, 4);
    }
}