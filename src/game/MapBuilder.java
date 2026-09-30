package game;

// Помощник для сборки карт комнат: не нужно вручную считать длину строк.
public class MapBuilder {
    public final int cols, rows;
    private final char[][] g;

    public MapBuilder(int cols, int rows, char fill) {
        this.cols = cols;
        this.rows = rows;
        g = new char[rows][cols];
        for (char[] line : g) java.util.Arrays.fill(line, fill);
    }

    // Комната с видом сбоку: 12 рядов, пол — ряд 9 (под ним стены),
    // дверь '1' слева (col 1), выход '2' справа (col cols-2).
    public static MapBuilder side(int cols) {
        MapBuilder b = new MapBuilder(cols, 12, '#');
        b.rect(1, 1, cols - 2, 9, '.');
        b.set(1, 9, '1');
        b.set(cols - 2, 9, '2');
        return b;
    }

    public MapBuilder set(int c, int r, char ch) {
        g[r][c] = ch;
        return this;
    }

    // прямоугольник, границы включительно
    public MapBuilder rect(int c0, int r0, int c1, int r1, char ch) {
        for (int r = r0; r <= r1; r++)
            for (int c = c0; c <= c1; c++)
                g[r][c] = ch;
        return this;
    }

    public String[] build() {
        String[] out = new String[rows];
        for (int r = 0; r < rows; r++) out[r] = new String(g[r]);
        return out;
    }
}