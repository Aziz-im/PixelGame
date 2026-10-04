package game;

import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public class Room {
    public static final int TILE = 48;
    public static final int COLS = 20;              // ширина ЭКРАНА в клетках
    public static final int ROWS = 12;              // высота ЭКРАНА в клетках
    public static final int VIEW_W = COLS * TILE;
    public static final int VIEW_H = ROWS * TILE;

    // что нужно сделать, чтобы открылся выход (ворота)
    //  CANDLE  — найти свечу среди ваз
    //  CAT     — спасти или убить кота
    //  COLLECT — собрать все предметы (корона, розы, угощение, сферы)
    //  TOTEMS  — разбить все тотемы мечом
    //  CHOICE  — сделать выбор (E / L): добрый или плохой поступок
    //  ESCAPE  — поговорить с персонажем и вырваться из его рук (жать E / ПРОБЕЛ)
    public enum Goal { NONE, CANDLE, CAT, COLLECT, TOTEMS, CHOICE, ESCAPE }

    // табличка с подсказкой (для обучения)
    public record Sign(int col, int row, String text) {}

    private static final Random RND = new Random();

    public final String name;
    public String key;                               // ключ комнаты в словаре rooms
    private final String[] map;
    public final Map<Character, Door> doors = new HashMap<>();
    public final Map<Character, Color> doorTint = new HashMap<>();     // цвет двери
    public final Map<Character, String> doorLabel = new HashMap<>();   // надпись у двери
    public final List<Vase> vases = new ArrayList<>();
    public final List<Totem> totems = new ArrayList<>();
    public final List<Pickup> pickups = new ArrayList<>();
    public final List<Trap> traps = new ArrayList<>();
    public final List<Sign> signs = new ArrayList<>();
    public final List<Prop> props = new ArrayList<>();   // декорации и сценки с репликами Эльзы
    public final Set<Character> doneDoors = new HashSet<>();   // двери, за которыми испытание пройдено
    public Cat cat;                                  // кот этой комнаты (если он тут есть)
    public boolean sideView;                         // true = вид сбоку (платформер)
    public Decision choice;                            // выбор «добро / зло» в комнате греха
    public boolean basement;                         // подвал: кирпичные стены и железные двери
    public final Set<Character> lockedDoors = new HashSet<>();   // двери, которые пока заперты (коридор)

    public Theme theme = Theme.TUTORIAL;             // цвет и атмосфера локации
    // оформление: GALLERY — кирпичная галерея с лампами (комнаты), VOID — почти чёрное (спуски и коридоры), CLASSIC — старый вид
    public enum Look { CLASSIC, GALLERY, VOID }
    public Look look = Look.GALLERY;
    public String title, subtitle;                   // заставка при входе
    public int checkTime = 45;                       // сколько кадров длится безопасная проверка вазы (в каждой комнате своё)
    public int wind;                                 // >0 — течение тянет игрока влево (Лень)
    public int gateCol = -1;                         // колонка ворот 'G' (-1 = нет)

    public Goal goal = Goal.NONE;
    public String goalLabel = "";                    // текст цели для HUD
    public char exitChar;                            // символ двери-выхода
    public boolean exitLocked;                       // выход закрыт, пока не выполнена цель

    public Room(String name, String[] map) {
        int w = map[0].length();
        for (int i = 0; i < map.length; i++) {
            if (map[i].length() != w) {
                throw new IllegalArgumentException("Комната \"" + name + "\": строка " + i
                        + " имеет длину " + map[i].length() + ", а нужно " + w);
            }
        }
        this.name = name;
        this.map = map;

        // ищем на карте кота, вазы, тотемы, предметы и ворота
        for (int row = 0; row < map.length; row++) {
            for (int col = 0; col < w; col++) {
                switch (map[row].charAt(col)) {
                    case 'C' -> cat = new Cat(col, row);
                    case 'V' -> vases.add(new Vase(col, row));
                    case 'T' -> totems.add(new Totem(col, row));
                    case 'K' -> pickups.add(new Pickup(Pickup.Kind.CROWN, col, row));
                    case 'R' -> pickups.add(new Pickup(Pickup.Kind.ROSE, col, row));
                    case 'A' -> pickups.add(new Pickup(Pickup.Kind.FOOD, col, row));
                    case 'Z' -> pickups.add(new Pickup(Pickup.Kind.ORB, col, row));
                    case 'G' -> gateCol = col;
                    default -> { }
                }
            }
        }
        if (!vases.isEmpty()) vases.get(RND.nextInt(vases.size())).hasCandle = true;   // свеча в случайной вазе
    }

    public int cols()     { return map[0].length(); }
    public int rows()     { return map.length; }
    public int widthPx()  { return cols() * TILE; }
    public int heightPx() { return rows() * TILE; }

    public void addDoor(char symbol, String targetRoom, int spawnCol, int spawnRow) {
        doors.put(symbol, new Door(targetRoom, spawnCol, spawnRow));
    }

    public void addSign(int col, String text) {
        signs.add(new Sign(col, 9, text));
    }

    // ---------- цели ----------
    public int takenCount() {
        int n = 0;
        for (Pickup p : pickups) if (p.taken) n++;
        return n;
    }

    public boolean allTaken() { return takenCount() == pickups.size(); }

    public int totemsLeft() {
        int n = 0;
        for (Totem t : totems) if (!t.broken) n++;
        return n;
    }

    // есть ли рядом с колонкой опасная ловушка (нужно, чтобы не сохранять "безопасное место" на ловушке)
    public boolean trapNear(int col, int dist) {
        for (Trap t : traps) {
            if (t.type == Trap.Type.CRUMBLE) continue;
            if (col >= t.minCol() - dist && col <= t.maxCol() + dist) return true;
        }
        return false;
    }

    // ---------- карта ----------
    public char tileAt(int col, int row) {
        if (col < 0 || row < 0 || row >= map.length || col >= map[row].length()) return '#';
        char c = map[row].charAt(col);
        return (c == 'C' || c == 'V' || c == 'T' || c == 'K' || c == 'R' || c == 'A' || c == 'Z') ? '.' : c;   // места появления = обычный пол
    }

    // 'G' — ворота: твёрдые, пока не выполнена цель
    private boolean solidChar(char c) {
        return c == '#' || c == 'r' || c == 'b' || c == 'a' || c == 'F'
                || (exitLocked && (c == exitChar || c == 'G'))
                || lockedDoors.contains(c);
    }

    public boolean isSolid(int px, int py) {
        if (px < 0 || py < 0) return true;
        int col = px / TILE, row = py / TILE;
        if (solidChar(tileAt(col, row))) return true;
        for (Trap t : traps) if (t.solidAt(col, row)) return true;     // хрупкие плиты
        return false;
    }

    // упирается ли прямоугольник (хитбокс) в твёрдую клетку
    public boolean blocked(int x, int y, int w, int h) {
        return isSolid(x, y) || isSolid(x + w - 1, y) || isSolid(x, y + h - 1) || isSolid(x + w - 1, y + h - 1);
    }

    // ================= ОТРИСОВКА =================
    public void draw(Graphics2D g, int camX, int camY, int tick) {
        int c0 = Math.max(0, camX / TILE);
        int c1 = Math.min(cols(), (camX + VIEW_W) / TILE + 1);
        int r0 = Math.max(0, camY / TILE);
        int r1 = Math.min(rows(), (camY + VIEW_H) / TILE + 1);

        if (sideView) {
            drawSideBackground(g, camX, camY, c0, c1, tick);
            if (look != Look.VOID) drawParticles(g, camX, camY, tick);
        } else {
            g.setColor(new Color(20, 28, 44));
            g.fillRect(camX, camY, VIEW_W, VIEW_H);
        }

        for (int row = r0; row < r1; row++) {
            for (int col = c0; col < c1; col++) {
                int x = col * TILE, y = row * TILE;
                char c = tileAt(col, row);
                if (c == '#') {
                    if (sideView) drawSideWall(g, x, y, col, row);
                    else drawWall(g, x, y, col, row);
                } else if (c == 'g' || c == 'f') {
                    drawGrass(g, x, y, col, row, c == 'f');
                } else {
                    if (!sideView) drawFloor(g, x, y, col, row);
                    switch (c) {
                        case 'r' -> drawRock(g, x, y);
                        case 'b' -> drawCrate(g, x, y);
                        case 'a' -> drawAnvil(g, x, y);
                        case 'F' -> drawForge(g, x, y, tick);
                        case '^' -> drawSpikes(g, x, y);
                        case 'P' -> drawPit(g, x, y, col, row);
                        case 'G' -> { if (exitLocked) drawGate(g, x, y, tick); }
                        default -> {
                            if (Character.isDigit(c)) {
                                if (basement) drawIronDoor(g, x, y, doneDoors.contains(c), doorTint.get(c));
                                else drawDoor(g, x, y, doneDoors.contains(c), doorTint.get(c));
                                if ((c == exitChar && exitLocked) || (lockedDoors.contains(c) && !doneDoors.contains(c))) drawLock(g, x, y);
                                String label = doorLabel.get(c);
                                if (label != null) drawDoorLabel(g, label, x, y, col, row, doneDoors.contains(c));
                            }
                        }
                    }
                }
            }
        }

        if (sideView && look == Look.GALLERY) drawGalleryFront(g, camX);
        for (Trap t : traps) t.draw(g, tick, theme);
        for (Sign s : signs) drawSign(g, s);
        for (Prop p : props) p.draw(g, tick);
        for (Totem t : totems) t.draw(g, tick);
        for (Vase v : vases) v.draw(g, tick);
        if (choice != null) choice.draw(g, tick);
        for (Pickup p : pickups) p.draw(g, tick);
    }

    // ---------- атмосфера комнат сбоку ----------
    private void drawSideBackground(Graphics2D g, int camX, int camY, int c0, int c1, int tick) {
        if (look == Look.GALLERY) { drawGalleryBackground(g, camX, camY, tick); return; }
        if (look == Look.VOID) { drawVoidBackground(g, camX, camY); return; }
        g.setPaint(new GradientPaint(0, camY, theme.skyTop, 0, camY + VIEW_H, theme.skyBottom));
        g.fillRect(camX, camY, VIEW_W, VIEW_H);
        g.setColor(new Color(255, 255, 255, 10));                    // светлая полоса-панель на стене
        g.fillRect(camX, camY + 2 * TILE, VIEW_W, 5 * TILE);

        int floorY = (rows() - 2) * TILE;
        for (int col = Math.max(0, c0 - 1); col <= c1 + 1; col++) {
            int x = col * TILE;
            if (col % 9 == 4) {                                      // колонна
                g.setColor(theme.pillar);
                g.fillRect(x + 12, camY, 24, VIEW_H);
                g.setColor(theme.pillarHi);
                g.fillRect(x + 12, camY, 5, VIEW_H);
                g.setColor(Theme.alpha(theme.accent, 120));          // полоска цвета греха
                g.fillRect(x + 12, camY + 2 * TILE, 24, 3);
            } else if (col % 7 == 2) {                               // цепь
                int len = (2 + (col * 37) % 5) * TILE;
                g.setColor(Theme.mix(theme.pillarHi, Color.GRAY, 0.3));
                for (int yy = TILE; yy < TILE + len; yy += 8) {
                    g.fillRect(x + 22 + ((yy / 8) & 1) * 2, yy, 4, 6);
                }
            } else if (col % 13 == 6) {                              // лампа с конусом света
                int lampY = TILE + 2 * TILE;
                g.setColor(new Color(40, 40, 60));
                g.fillRect(x + 23, TILE, 2, 2 * TILE);
                g.fillPolygon(new int[]{x + 12, x + 36, x + 30, x + 18},
                        new int[]{lampY + 10, lampY + 10, lampY, lampY}, 4);
                g.setColor(Theme.alpha(theme.accent, 48));
                g.fillPolygon(new int[]{x + 16, x + 32, x + 24 + 100, x + 24 - 100},
                        new int[]{lampY + 10, lampY + 10, floorY, floorY}, 4);
            }
        }
    }

    // частицы на фоне: у каждого греха своя «погода»
    private void drawParticles(Graphics2D g, int camX, int camY, int tick) {
        if (theme.deco == Theme.Deco.NONE) return;
        Color ac = theme.accent;
        for (int i = 0; i < 28; i++) {
            double fx = ((i * 7919L) % 997) / 997.0;
            double fy = ((i * 104729L) % 991) / 991.0;
            double sp = 0.5 + (i % 4) * 0.3;
            switch (theme.deco) {
                case SPARKLE -> {                                    // мерцающие звёздочки
                    int x = camX + (int) (fx * VIEW_W), y = camY + (int) (fy * VIEW_H * 0.85);
                    double tw = (Math.sin(tick * 0.07 + i * 1.9) + 1) / 2;
                    int s = 1 + (int) (tw * 3);
                    g.setColor(Theme.alpha(ac, 60 + (int) (tw * 160)));
                    g.fillRect(x - s, y, 2 * s + 1, 1);
                    g.fillRect(x, y - s, 1, 2 * s + 1);
                }
                case COINS -> {                                      // падающие монетки
                    int x = camX + (int) (fx * VIEW_W), y = camY + (int) ((fy * VIEW_H + tick * sp) % VIEW_H);
                    int w = (int) Math.abs(Math.cos(tick * 0.08 + i) * 7) + 1;
                    g.setColor(Theme.alpha(new Color(255, 225, 90), 190));
                    g.fillOval(x, y, w, 8);
                }
                case EMBERS -> {                                     // поднимающиеся угли
                    int x = camX + (int) (fx * VIEW_W + Math.sin(tick * 0.03 + i) * 14);
                    int y = camY + (int) (VIEW_H - ((fy * VIEW_H + tick * sp * 1.4) % VIEW_H));
                    g.setColor(new Color(255, 120 + (i % 3) * 40, 40, 170));
                    g.fillRect(x, y, 3, 3);
                }
                case WISPS -> {                                      // плавающие огоньки
                    int x = camX + (int) (fx * VIEW_W + Math.sin(tick * 0.02 + i * 1.7) * 40);
                    int y = camY + (int) (fy * VIEW_H + Math.cos(tick * 0.017 + i) * 30);
                    int r = 6 + (i % 4) * 2;
                    g.setColor(Theme.alpha(ac, 45));
                    g.fillOval(x - r, y - r, r * 2, r * 2);
                    g.setColor(Theme.alpha(ac, 130));
                    g.fillOval(x - 2, y - 2, 4, 4);
                }
                case PETALS -> {                                     // лепестки
                    int x = camX + (int) ((fx * VIEW_W + Math.sin(tick * 0.04 + i) * 30 + tick * sp * 0.3) % VIEW_W);
                    int y = camY + (int) ((fy * VIEW_H + tick * sp * 0.9) % VIEW_H);
                    g.setColor(Theme.alpha(ac, 170));
                    g.fillOval(x, y, 8, 5);
                }
                case STEAM -> {                                      // пар от угощения
                    int x = camX + (int) (fx * VIEW_W + Math.sin(tick * 0.02 + i) * 16);
                    int y = camY + (int) (VIEW_H - ((fy * VIEW_H + tick * sp * 0.6) % VIEW_H));
                    int r = 10 + (i % 3) * 5;
                    g.setColor(Theme.alpha(ac, 26));
                    g.fillOval(x - r, y - r, r * 2, r * 2);
                }
                case DUST -> {                                       // сонные «z», дрейфуют по течению
                    int x = camX + (int) (VIEW_W - ((fx * VIEW_W + tick * sp * 0.8) % VIEW_W));
                    int y = camY + (int) (fy * VIEW_H * 0.9 + Math.sin(tick * 0.03 + i) * 10);
                    g.setFont(new Font("Monospaced", Font.BOLD, 12 + (i % 3) * 6));
                    g.setColor(Theme.alpha(ac, 90));
                    g.drawString("z", x, y);
                }
                default -> { }
            }
        }
    }

    private void drawSideWall(Graphics2D g, int x, int y, int col, int row) {
        if (look == Look.GALLERY) { drawGalleryWall(g, x, y, col, row); return; }
        if (look == Look.VOID) { drawVoidWall(g, x, y, col, row); return; }
        boolean surface = tileAt(col, row - 1) != '#';
        if (surface) {                                               // верхняя грань пола / платформы
            g.setColor(theme.wallTop);
            g.fillRect(x, y, TILE, TILE);
            g.setColor(theme.wallTopHi);
            g.fillRect(x, y, TILE, 5);
            g.setColor(theme.wallLine);
            g.fillRect(x, y + 20, TILE, 2);
            g.fillRect(x + ((col + row) % 2) * 20 + 10, y + 5, 2, 15);
            g.fillRect(x + ((col + row) % 2) * 20 + 30, y + 22, 2, 26);
        } else {
            g.setColor(theme.wallBody);
            g.fillRect(x, y, TILE, TILE);
            g.setColor(theme.wallLine);
            g.fillRect(x, y + 16, TILE, 2);
            g.fillRect(x, y + 34, TILE, 2);
            g.fillRect(x + (row % 2) * 16 + 12, y, 2, 16);
            g.fillRect(x + (row % 2) * 16 + 32, y + 18, 2, 16);
        }
    }

    // яма: тёмная пропасть с туманом у края и зубьями на дне
    private void drawPit(Graphics2D g, int x, int y, int col, int row) {
        g.setColor(new Color(6, 4, 12));
        g.fillRect(x, y, TILE, TILE);
        if (tileAt(col, row - 1) != 'P') {
            g.setColor(Theme.alpha(theme.accent, 55));
            g.fillRect(x, y, TILE, 10);
            g.setColor(Theme.alpha(theme.accent, 30));
            g.fillRect(x, y + 10, TILE, 10);
        }
        if (tileAt(col, row + 1) != 'P') {
            g.setColor(new Color(58, 44, 70));
            for (int i = 0; i < 4; i++) {
                int sx = x + i * 12;
                g.fillPolygon(new int[]{sx, sx + 12, sx + 6}, new int[]{y + TILE, y + TILE, y + TILE - 14}, 3);
            }
        }
    }

    // ворота испытания: светящийся барьер
    private void drawGate(Graphics2D g, int x, int y, int tick) {
        g.setColor(Theme.alpha(theme.accent, 70));
        g.fillRect(x + 14, y, 20, TILE);
        g.setColor(Theme.alpha(theme.accent, 200));
        g.fillRect(x + 21, y, 6, TILE);
        g.setColor(Color.WHITE);
        g.fillRect(x + 20, y + ((tick * 3 + y) % TILE), 8, 3);
    }

    private void drawSign(Graphics2D g, Sign s) {
        int x = s.col() * TILE, y = s.row() * TILE;
        g.setColor(new Color(0, 0, 0, 60));
        g.fillRect(x + 10, y + 42, 28, 5);
        g.setColor(new Color(96, 64, 34));
        g.fillRect(x + 22, y + 20, 5, 26);
        g.setColor(new Color(170, 124, 70));
        g.fillRect(x + 8, y + 8, 32, 16);
        g.setColor(new Color(110, 76, 40));
        g.fillRect(x + 8, y + 8, 32, 3);
        g.fillRect(x + 8, y + 21, 32, 3);
        g.setColor(new Color(60, 40, 20));
        g.fillRect(x + 13, y + 14, 22, 2);
        g.fillRect(x + 13, y + 18, 14, 2);
    }

    // ---------- вид сверху ----------
    private void drawFloor(Graphics2D g, int x, int y, int col, int row) {
        if (basement) { drawBasementFloor(g, x, y, col, row); return; }
        g.setColor((col + row) % 2 == 0 ? new Color(46, 64, 92) : new Color(42, 60, 86));
        g.fillRect(x, y, TILE, TILE);
        g.setColor(new Color(33, 48, 70));                        // швы кладки
        int off = (row % 2 == 0) ? 12 : 36;
        g.fillRect(x, y, TILE, 2);
        g.fillRect(x, y + 24, TILE, 2);
        g.fillRect(x + off, y + 2, 2, 22);
        g.fillRect(x + (off + 24) % TILE, y + 26, 2, 22);
        if ((col * 7 + row * 13) % 5 == 0) {
            g.setColor(new Color(60, 84, 116));
            g.fillRect(x + 20, y + 8, 6, 3);
        }
    }

    private void drawGrass(Graphics2D g, int x, int y, int col, int row, boolean flower) {
        g.setColor(new Color(38, 112, 64));
        g.fillRect(x, y, TILE, TILE);
        int h = col * 31 + row * 17;
        g.setColor(new Color(80, 176, 96));
        g.fillRect(x + (h * 7) % 36 + 4, y + (h * 3) % 30 + 4, 4, 6);
        g.fillRect(x + (h * 11) % 36 + 4, y + (h * 5) % 30 + 8, 4, 6);
        g.setColor(new Color(24, 86, 50));
        g.fillRect(x + (h * 13) % 36 + 4, y + (h * 9) % 30 + 6, 6, 4);
        if (flower) {
            int fx = x + 14 + (h % 3) * 6, fy = y + 14 + (h % 2) * 8;
            g.setColor(new Color(240, 130, 30));
            g.fillRect(fx + 2, fy, 4, 2);
            g.fillRect(fx + 2, fy + 6, 4, 2);
            g.fillRect(fx, fy + 2, 2, 4);
            g.fillRect(fx + 6, fy + 2, 2, 4);
            g.setColor(new Color(250, 200, 60));
            g.fillRect(fx + 2, fy + 2, 4, 4);
        }
    }

    private void drawWall(Graphics2D g, int x, int y, int col, int row) {
        if (basement) { drawBrickWall(g, x, y, col, row); return; }
        g.setColor(new Color(26, 38, 58));
        g.fillRect(x, y, TILE, TILE);
        g.setColor(new Color(36, 52, 78));
        g.fillRect(x, y + 12, TILE, 2);
        g.fillRect(x, y + 30, TILE, 2);
        int off = (row % 2) * 16;
        g.fillRect(x + off + 10, y + 14, 2, 16);
        g.fillRect(x + (off + 30) % TILE, y + 32, 2, 16);
        if (tileAt(col, row - 1) != '#') {                        // светлая верхняя грань
            g.setColor(new Color(70, 98, 134));
            g.fillRect(x, y, TILE, 12);
            g.setColor(new Color(90, 122, 160));
            g.fillRect(x, y, TILE, 3);
        }
    }

    private void drawRock(Graphics2D g, int x, int y) {
        g.setColor(new Color(0, 0, 0, 60));
        g.fillRect(x + 6, y + 38, 36, 6);
        g.setColor(new Color(96, 104, 122));
        g.fillRect(x + 6, y + 14, 36, 26);
        g.setColor(new Color(132, 140, 158));
        g.fillRect(x + 10, y + 14, 20, 8);
        g.setColor(new Color(70, 76, 92));
        g.fillRect(x + 6, y + 34, 36, 6);
    }

    private void drawCrate(Graphics2D g, int x, int y) {
        g.setColor(new Color(0, 0, 0, 60));
        g.fillRect(x + 4, y + 40, 40, 6);
        g.setColor(new Color(140, 96, 54));
        g.fillRect(x + 4, y + 10, 40, 32);
        g.setColor(new Color(176, 130, 74));
        g.fillRect(x + 4, y + 10, 40, 10);
        g.setColor(new Color(96, 62, 30));
        g.fillRect(x + 4, y + 10, 40, 3);
        g.fillRect(x + 4, y + 39, 40, 3);
        g.fillRect(x + 4, y + 10, 3, 32);
        g.fillRect(x + 41, y + 10, 3, 32);
        g.fillRect(x + 22, y + 20, 4, 22);
    }

    private void drawAnvil(Graphics2D g, int x, int y) {
        g.setColor(new Color(0, 0, 0, 60));
        g.fillRect(x + 8, y + 40, 32, 6);
        g.setColor(new Color(60, 64, 78));
        g.fillRect(x + 10, y + 32, 28, 10);
        g.fillRect(x + 16, y + 24, 16, 10);
        g.setColor(new Color(150, 158, 176));
        g.fillRect(x + 4, y + 12, 40, 12);
        g.setColor(new Color(196, 204, 220));
        g.fillRect(x + 6, y + 12, 36, 4);
    }

    private void drawForge(Graphics2D g, int x, int y, int tick) {
        g.setColor(new Color(0, 0, 0, 60));
        g.fillRect(x + 2, y + 40, 44, 6);
        g.setColor(new Color(52, 86, 110));
        g.fillRect(x + 2, y + 4, 44, 38);
        g.setColor(new Color(74, 112, 140));
        g.fillRect(x + 2, y + 4, 44, 8);
        g.setColor(new Color(34, 58, 78));
        g.fillRect(x + 8, y + 14, 32, 24);
        g.setColor((tick / 12) % 2 == 0 ? new Color(255, 180, 60) : new Color(235, 120, 30));
        g.fillRect(x + 14, y + 18, 5, 14);
        g.fillRect(x + 22, y + 18, 5, 14);
        g.fillRect(x + 30, y + 18, 5, 14);
    }

    private void drawSpikes(Graphics2D g, int x, int y) {
        if (!sideView) {
            g.setColor(new Color(24, 34, 52));
            g.fillRect(x + 4, y + 4, TILE - 8, TILE - 8);
        }
        // один большой шип на всю клетку: левая грань светлая, правая тёмная
        int left = x + 4, right = x + TILE - 4, mid = x + TILE / 2;
        int top = y + 3, base = y + TILE - 4;
        g.setColor(new Color(204, 212, 228));
        g.fillPolygon(new int[]{left, mid, mid}, new int[]{base, top, base}, 3);
        g.setColor(new Color(142, 151, 172));
        g.fillPolygon(new int[]{mid, right, mid}, new int[]{base, base, top}, 3);
        g.setColor(new Color(240, 244, 252));                  // блик вдоль левого ребра
        g.fillPolygon(new int[]{mid - 1, mid - 1, mid - 7}, new int[]{top + 4, top + 22, top + 22}, 3);
    }

    // дверь: если у неё есть цвет (tint) — рисуем рамку этого цвета со свечением
    private void drawDoor(Graphics2D g, int x, int y, boolean done, Color tint) {
        Color frame = tint != null ? Theme.mix(tint, Color.BLACK, 0.35) : new Color(70, 44, 24);
        Color panel = tint != null ? tint : new Color(120, 78, 44);
        Color line = tint != null ? Theme.mix(tint, Color.BLACK, 0.5) : new Color(90, 58, 32);
        if (tint != null) {
            g.setColor(Theme.alpha(tint, 60));
            g.fillRect(x - 4, y - 4, TILE + 8, TILE + 8);
        }
        g.setColor(frame);
        g.fillRect(x + 2, y + 2, TILE - 4, TILE - 2);
        g.setColor(panel);
        g.fillRect(x + 6, y + 6, TILE - 12, TILE - 8);
        g.setColor(line);
        g.fillRect(x + TILE / 2 - 1, y + 6, 2, TILE - 8);
        g.setColor(new Color(240, 200, 60));
        g.fillRect(x + TILE / 2 + 5, y + TILE / 2, 4, 4);
        if (done) {                                               // зелёная отметка: испытание пройдено
            g.setColor(new Color(90, 220, 120));
            g.fillOval(x + TILE / 2 - 5, y + 10, 10, 10);
        }
    }

    // название греха над дверью (если под дверью пол — надпись сверху, иначе снизу)
    private void drawDoorLabel(Graphics2D g, String text, int x, int y, int col, int row, boolean done) {
        g.setFont(new Font("Monospaced", Font.BOLD, 11));
        int w = g.getFontMetrics().stringWidth(text);
        int ty = tileAt(col, row + 1) == '.' ? y - 6 : y + TILE + 14;
        g.setColor(new Color(0, 0, 0, 150));
        g.drawString(text, x + TILE / 2 - w / 2 + 1, ty + 1);
        g.setColor(done ? new Color(120, 235, 140) : Color.WHITE);
        g.drawString(text, x + TILE / 2 - w / 2, ty);
    }

    // ================= ОФОРМЛЕНИЕ: «ГАЛЕРЕЯ» (комнаты) и «ПУСТОТА» (спуски, коридоры) =================
    private static final int LP = 480, LO = 200;      // период узора по X и сдвиг первой лампы

    // кладка: ряды кирпичей 36x17, привязаны к мировым координатам (при прокрутке не «плывут»)
    private static void bricks(Graphics2D g, int x0, int x1, int y0, int y1, Color brick, Color mortar, Color brick2) {
        g.setColor(mortar);
        g.fillRect(x0, y0, x1 - x0, y1 - y0);
        int bh = 17, bw = 36, rowN = 0;
        for (int y = y0; y < y1; y += bh, rowN++) {
            int off = (rowN % 2) * (bw / 2);
            int hh = Math.min(bh - 2, y1 - y - 1);
            int start = x0 - Math.floorMod(x0 - off, bw);
            for (int bx = start; bx < x1; bx += bw) {
                g.setColor(Math.floorMod(Math.floorDiv(bx, bw) * 7 + rowN * 5, 3) == 0 ? brick2 : brick);
                int a = Math.max(bx + 1, x0), b = Math.min(bx + bw - 1, x1);
                if (b > a && hh > 0) g.fillRect(a, y + 1, b - a, hh);
            }
        }
    }

    // пунктирная линия с «засечками»
    private static void dashedLine(Graphics2D g, int x0, int x1, int y, Color c, int th) {
        g.setColor(c);
        int seg = 60;
        for (int sx = x0 - Math.floorMod(x0, seg); sx < x1; sx += seg) {
            int len = 50 + Math.floorMod(Math.floorDiv(sx, seg) * 13, 6);
            g.fillRect(sx, y, len, th);
            g.fillRect(sx + len + 2, y - 2, 2, th + 4);
        }
    }

    // освещённость пола под лампами: 0 — тень, 1 — центр светового пятна
    private double lampLight(int worldX) {
        int m = Math.floorMod(worldX - LO, LP);
        return Math.max(0, 1 - Math.min(m, LP - m) / 170.0);
    }

    private void plaque(Graphics2D g, int x, int y, int w, int h) {
        g.setColor(Theme.mix(theme.base, Color.BLACK, 0.55));
        g.fillRect(x - 3, y - 3, w + 6, h + 6);
        g.setColor(Theme.mix(theme.base, Color.BLACK, 0.80));
        g.fillRect(x, y, w, h);
        g.setColor(Theme.alpha(theme.accent, 50));
        for (int i = 0; i < w - 6; i += 8) {
            g.fillRect(x + i + 3, y + 4, 3, 3);
            g.fillRect(x + i + 3, y + h - 7, 3, 3);
        }
    }

    private void drawGalleryBackground(Graphics2D g, int camX, int camY, int tick) {
        Color deep = Theme.mix(theme.base, Color.BLACK, 0.82);
        Color wall = Theme.mix(theme.base, Color.BLACK, 0.62);
        Color brick = Theme.mix(theme.base, Color.BLACK, 0.36);
        Color brick2 = Theme.mix(theme.base, Color.BLACK, 0.44);
        Color mortar = Theme.mix(theme.base, Color.BLACK, 0.72);
        int floorY = (rows() - 2) * TILE;
        int xa = camX, xb = camX + VIEW_W;

        g.setPaint(new GradientPaint(0, camY, deep, 0, camY + VIEW_H, wall));
        g.fillRect(xa, camY, VIEW_W, VIEW_H);

        // слабая кладка на гладкой стене между панелями
        Color fb = Theme.mix(wall, brick, 0.22), fm = Theme.mix(wall, Color.BLACK, 0.30);
        bricks(g, xa, xb, 126, 177, fb, fm, fb);
        bricks(g, xa, xb, 400, floorY, fb, fm, fb);

        // кирпичные панели с нишами
        for (int k = Math.floorDiv(xa - LO, LP) - 1; k <= Math.floorDiv(xb - LO, LP) + 1; k++) {
            int px = LO + k * LP + 91, pw = 158;
            bricks(g, px, px + pw, 75, 379, brick, mortar, brick2);
            g.setColor(Theme.mix(theme.base, Color.WHITE, 0.12));            // карниз
            g.fillRect(px - 4, 379, pw + 8, 5);
            bricks(g, px, px + pw, 384, floorY, Theme.mix(brick, Color.BLACK, 0.45),
                    Theme.mix(mortar, Color.BLACK, 0.4), Theme.mix(brick2, Color.BLACK, 0.45));
            g.setColor(new Color(0, 0, 0, 95));                               // тень сбоку панели
            g.fillRect(px + pw, 75, 22, 375);
            plaque(g, px + 54, 169, 54, 167);
            plaque(g, px + 44, 397, 70, 44);
        }

        // потолок: тёмная решётка с рельсом
        g.setColor(Theme.mix(theme.base, Color.BLACK, 0.92));
        g.fillRect(xa, camY, VIEW_W, 75);
        g.setColor(new Color(255, 255, 255, 14));
        for (int y = 3; y < 48; y += 8)
            for (int x = xa - Math.floorMod(xa, 8); x < xb; x += 8) g.fillRect(x, y, 2, 2);
        g.setColor(new Color(6, 2, 3));
        g.fillRect(xa, 50, VIEW_W, 5);
        g.setColor(Theme.mix(theme.base, Color.BLACK, 0.35));
        g.fillRect(xa, 71, VIEW_W, 3);

        // лампы с конусами света
        for (int k = Math.floorDiv(xa - LO, LP) - 1; k <= Math.floorDiv(xb - LO, LP) + 1; k++) {
            int cx = LO + k * LP;
            g.setColor(Theme.alpha(Theme.mix(theme.base, theme.accent, 0.35), 36));
            g.fillPolygon(new int[]{cx - 20, cx + 20, cx + 150, cx - 150}, new int[]{122, 122, floorY, floorY}, 4);
            g.setColor(new Color(10, 4, 6));
            g.fillRect(cx - 1, 54, 3, 28);
            g.setColor(new Color(34, 20, 44));
            g.fillPolygon(new int[]{cx - 8, cx + 8, cx + 24, cx - 24}, new int[]{80, 80, 104, 104}, 4);
            g.fillRect(cx - 10, 78, 20, 4);
            boolean on = (tick / 9 + k) % 13 != 0;                            // лампа изредка мигает
            for (int i = 0; i < 4; i++) {
                Color c = i % 2 == 0 ? theme.accent : Theme.mix(theme.accent, Color.WHITE, 0.4);
                g.setColor(on ? c : Theme.mix(c, theme.base, 0.6));
                g.fillRect(cx - 15 + i * 8, 106, 5, 14 + (i % 2) * 8);
            }
        }
    }

    // постаменты стоят перед фоном: низ уходит в плитку пола
    private void drawGalleryFront(Graphics2D g, int camX) {
        int c = pedestalCol();
        if (c >= 0) pedestal(g, c * TILE + TILE / 2, true);       // максимум один постамент на комнату
    }

    // колонка для постамента: ближе к середине комнаты, где рядом нет ловушек, ям, блоков, дверей и предметов
    private int pedestalCol() {
        int best = -1;
        for (int c = 2; c < cols() - 2; c++) {
            if (!pedestalFree(c)) continue;
            if (best < 0 || Math.abs(c - cols() / 2) < Math.abs(best - cols() / 2)) best = c;
        }
        return best;
    }

    private boolean pedestalFree(int c) {
        for (int k = c - 2; k <= c + 2; k++) {
            if (k < 0 || k >= cols()) return false;
            if (map[9].charAt(k) != '.' || map[10].charAt(k) != '#') return false;   // только чистый пол
        }
        for (Trap t : traps) if (c >= t.minCol() - 2 && c <= t.maxCol() + 2) return false;
        for (Sign s : signs) if (Math.abs(s.col() - c) <= 2) return false;
        for (Prop p : props) if (Math.abs(p.col - c) <= 3) return false;
        if (choice != null && Math.abs(choice.col - c) <= 4) return false;
        return true;
    }

    // подземелье: чёрный камень, чёткие пиксельные грани, никаких полупрозрачных пятен
    private void pedestal(Graphics2D g, int cx, boolean lit) {
        Color stone   = new Color(16, 15, 21);
        Color stoneHi = new Color(48, 46, 58);     // светлая грань слева и сверху
        Color stoneLo = new Color(7, 6, 9);        // тёмная грань справа и снизу
        Color seam    = new Color(8, 7, 11);       // швы кладки
        Color trim    = lit ? Theme.mix(theme.accent, Color.BLACK, 0.15) : new Color(70, 66, 80);

        // тень на полу
        g.setColor(new Color(0, 0, 0, 120));
        g.fillRect(cx - 30, 506, 60, 4);

        // основание
        g.setColor(stone);
        g.fillRect(cx - 28, 486, 56, 20);
        g.setColor(stoneHi);
        g.fillRect(cx - 28, 486, 56, 2);
        g.fillRect(cx - 28, 486, 2, 20);
        g.setColor(stoneLo);
        g.fillRect(cx + 26, 488, 2, 18);
        g.fillRect(cx - 28, 504, 56, 2);

        // ножка: кладка из чёрных блоков
        g.setColor(stone);
        g.fillRect(cx - 15, 430, 30, 56);
        g.setColor(seam);
        for (int i = 0; i < 4; i++) {
            int top = 430 + i * 14;
            if (i > 0) g.fillRect(cx - 15, top, 30, 2);
            g.fillRect(i % 2 == 0 ? cx - 4 : cx + 5, top + (i > 0 ? 2 : 0), 2, i > 0 ? 12 : 14);
        }
        g.setColor(stoneHi);
        g.fillRect(cx - 15, 430, 2, 56);
        g.setColor(stoneLo);
        g.fillRect(cx + 13, 430, 2, 56);

        // у зажжённых — тонкая светящаяся прорезь
        if (lit) {
            g.setColor(Theme.mix(theme.accent, Color.BLACK, 0.45));
            g.fillRect(cx - 3, 446, 6, 28);
            g.setColor(theme.accent);
            g.fillRect(cx - 1, 446, 2, 28);
        }

        // шапка
        g.setColor(new Color(4, 3, 6));
        g.fillRect(cx - 15, 430, 30, 4);            // тень под шапкой
        g.setColor(new Color(22, 20, 28));
        g.fillRect(cx - 28, 414, 56, 16);
        g.setColor(trim);
        g.fillRect(cx - 28, 414, 56, 3);
        g.setColor(stoneHi);
        g.fillRect(cx - 28, 417, 2, 13);
        g.setColor(stoneLo);
        g.fillRect(cx + 26, 417, 2, 13);
        g.fillRect(cx - 28, 428, 56, 2);
    }

    private void drawGalleryWall(Graphics2D g, int x, int y, int col, int row) {
        boolean surface = tileAt(col, row - 1) != '#';
        if (surface) {                                                        // плитка пола / верх платформы
            Color f = Theme.mix(theme.base, Color.BLACK, 0.52);
            double lit = lampLight(x + TILE / 2);
            Color c = Theme.mix(f, Theme.mix(theme.accent, theme.base, 0.25), 0.55 * lit);
            g.setColor(c);
            g.fillRect(x, y, TILE, TILE);
            g.setColor(Theme.mix(c, Color.BLACK, 0.38));                      // швы плит
            g.fillRect(x, y + 22, TILE, 2);
            g.fillRect(x + ((col + row) % 2) * 20 + 10, y, 2, 22);
            g.fillRect(x + ((col + row) % 2) * 20 + 30, y + 24, 2, TILE - 24);
            g.setColor(Theme.mix(c, Color.WHITE, 0.22));                      // верхняя грань — по ней идти
            g.fillRect(x, y, TILE, 3);
            g.setColor(Theme.mix(theme.accent, theme.base, 0.2));             // яркая кромка снизу
            g.fillRect(x, y + TILE - 3, TILE, 3);
        } else {                                                              // тёмная масса под полом / стена
            g.setColor(Theme.mix(theme.base, Color.BLACK, 0.88));
            g.fillRect(x, y, TILE, TILE);
            g.setColor(new Color(255, 255, 255, 13));
            for (int yy = 3; yy < TILE; yy += 8)
                for (int xx = 3; xx < TILE; xx += 8) g.fillRect(x + xx, y + yy, 2, 2);
        }
    }

    private void drawVoidBackground(Graphics2D g, int camX, int camY) {
        int xa = camX, xb = camX + VIEW_W, floorY = (rows() - 2) * TILE;
        g.setPaint(new GradientPaint(0, camY, new Color(4, 7, 10), 0, camY + VIEW_H, new Color(9, 13, 16)));
        g.fillRect(xa, camY, VIEW_W, VIEW_H);
        bricks(g, xa, xb, 156, 192, new Color(17, 22, 25), new Color(9, 13, 15), new Color(15, 20, 23));
        for (int k = Math.floorDiv(xa - 185, 540) - 1; k <= Math.floorDiv(xb - 185, 540) + 1; k++) {
            int px = 185 + k * 540;
            bricks(g, px, px + 50, 216, floorY, new Color(19, 25, 28), new Color(10, 14, 16), new Color(17, 22, 25));
        }
        dashedLine(g, xa, xb, 96, new Color(66, 76, 80, 170), 4);
    }

    private void drawVoidWall(Graphics2D g, int x, int y, int col, int row) {
        boolean surface = tileAt(col, row - 1) != '#';
        g.setColor(surface ? new Color(13, 17, 20) : new Color(5, 8, 10));
        g.fillRect(x, y, TILE, TILE);
        g.setColor(new Color(76, 86, 90));                                    // бледная пунктирная кромка у границы с воздухом
        if (surface) { g.fillRect(x + 1, y, 22, 4); g.fillRect(x + 26, y, 21, 4); }
        if (tileAt(col - 1, row) != '#') { g.fillRect(x, y + 2, 4, 20); g.fillRect(x, y + 26, 4, 20); }
        if (tileAt(col + 1, row) != '#') { g.fillRect(x + TILE - 4, y + 2, 4, 20); g.fillRect(x + TILE - 4, y + 26, 4, 20); }
    }

    // в «пустоте» подвал затемняется: стены и пол почти чёрные, двери читаются по цветной полосе
    private Color d(Color c)  { return look == Look.VOID ? Theme.mix(c, new Color(3, 6, 8), 0.80) : c; }
    private Color dd(Color c) { return look == Look.VOID ? Theme.mix(c, new Color(3, 6, 8), 0.50) : c; }

    // ---------- подвал: кирпичные стены, каменный пол, железные двери ----------
    private void drawBrickWall(Graphics2D g, int x, int y, int col, int row) {
        boolean vd = look == Look.VOID;
        g.setColor(vd ? new Color(6, 10, 12) : new Color(38, 28, 28));   // раствор между кирпичами
        g.fillRect(x, y, TILE, TILE);
        for (int i = 0; i < 4; i++) {                              // 4 ряда кирпичей по 12 px
            int off = ((row * 4 + i) % 2) * 12;
            for (int j = -1; j < 3; j++) {
                int bx = x + j * 24 + off;
                int tone = ((col * 7 + row * 13 + i * 5 + j * 3) % 3 + 3) % 3 * 8;
                g.setColor(vd ? new Color(13 + tone / 3, 19 + tone / 3, 21 + tone / 3)
                        : new Color(112 + tone, 52 + tone / 2, 42 + tone / 2));
                int x0 = Math.max(bx + 1, x), x1 = Math.min(bx + 23, x + TILE);
                if (x1 > x0) g.fillRect(x0, y + i * 12 + 1, x1 - x0, 10);
            }
        }
        if (look == Look.VOID) {                                   // бледная пунктирная кромка у границы с полом
            g.setColor(new Color(76, 86, 90, 160));
            if (tileAt(col, row - 1) != '#') { g.fillRect(x + 1, y, 22, 3); g.fillRect(x + 26, y, 21, 3); }
            if (tileAt(col, row + 1) != '#') { g.fillRect(x + 1, y + TILE - 3, 22, 3); g.fillRect(x + 26, y + TILE - 3, 21, 3); }
            if (tileAt(col - 1, row) != '#') { g.fillRect(x, y + 2, 3, 20); g.fillRect(x, y + 26, 3, 20); }
            if (tileAt(col + 1, row) != '#') { g.fillRect(x + TILE - 3, y + 2, 3, 20); g.fillRect(x + TILE - 3, y + 26, 3, 20); }
        } else if (tileAt(col, row - 1) != '#') {                  // светлая верхняя грань стены
            g.setColor(new Color(150, 92, 72));
            g.fillRect(x, y, TILE, 3);
        }
    }

    private void drawBasementFloor(Graphics2D g, int x, int y, int col, int row) {
        boolean vd = look == Look.VOID;
        g.setColor(vd ? ((col + row) % 2 == 0 ? new Color(12, 17, 20) : new Color(10, 14, 17))
                : ((col + row) % 2 == 0 ? new Color(60, 56, 58) : new Color(54, 50, 52)));
        g.fillRect(x, y, TILE, TILE);
        g.setColor(vd ? new Color(5, 8, 10) : new Color(36, 32, 34));   // швы плит
        g.fillRect(x, y, TILE, 2);
        g.fillRect(x, y, 2, TILE);
        if ((col * 7 + row * 13) % 5 == 0) {                       // сырое пятно
            g.setColor(vd ? new Color(16, 22, 26) : new Color(40, 46, 52));
            g.fillOval(x + 12, y + 14, 22, 12);
        }
    }

    // железная дверь: стальная плита, решётка-окошко, заклёпки; цветная лампа над ней показывает грех
    private void drawIronDoor(Graphics2D g, int x, int y, boolean done, Color tint) {
        if (tint != null) {
            g.setColor(Theme.alpha(tint, 70));
            g.fillRect(x - 4, y - 4, TILE + 8, TILE + 8);
        }
        g.setColor(dd(new Color(34, 38, 46)));
        g.fillRect(x + 2, y + 2, TILE - 4, TILE - 2);              // рама
        g.setColor(dd(new Color(92, 100, 112)));
        g.fillRect(x + 6, y + 6, TILE - 12, TILE - 8);             // плита
        g.setColor(tint != null ? tint : new Color(120, 130, 144));
        g.fillRect(x + 6, y + 6, TILE - 12, 3);                    // полоса цвета греха
        g.setColor(dd(new Color(60, 66, 78)));
        g.fillRect(x + TILE / 2 - 1, y + 10, 2, TILE - 12);        // шов створок
        g.setColor(new Color(14, 14, 20));
        g.fillRect(x + 14, y + 13, 20, 10);                        // окошко
        g.setColor(dd(new Color(150, 158, 170)));
        for (int i = 0; i < 3; i++) g.fillRect(x + 18 + i * 6, y + 13, 2, 10);   // прутья
        for (int i = 0; i < 2; i++) {                              // заклёпки
            g.fillRect(x + 9, y + 29 + i * 9, 3, 3);
            g.fillRect(x + TILE - 12, y + 29 + i * 9, 3, 3);
        }
        g.setColor(new Color(240, 200, 60));
        g.fillRect(x + TILE / 2 + 5, y + TILE / 2 + 2, 4, 6);      // ручка
        if (done) {                                                // зелёная отметка: испытание пройдено
            g.setColor(new Color(90, 220, 120));
            g.fillOval(x + TILE / 2 - 5, y + 26, 10, 10);
        }
    }

    private void drawLock(Graphics2D g, int x, int y) {
        g.setColor(new Color(150, 30, 50, 130));
        g.fillRect(x + 2, y + 2, TILE - 4, TILE - 2);
        g.setColor(new Color(240, 200, 60));                      // замок
        g.fillRect(x + TILE / 2 - 8, y + TILE / 2 - 2, 16, 14);
        g.setColor(new Color(30, 24, 40));
        g.fillRect(x + TILE / 2 - 2, y + TILE / 2 + 3, 4, 5);
        Stroke old = g.getStroke();
        g.setStroke(new BasicStroke(3));
        g.setColor(new Color(200, 200, 215));
        g.drawArc(x + TILE / 2 - 6, y + TILE / 2 - 14, 12, 16, 0, 180);
        g.setStroke(old);
    }
}

// Дверь: куда ведёт и где появится игрок
record Door(String targetRoom, int spawnCol, int spawnRow) {}