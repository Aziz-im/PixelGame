package game;

import java.awt.*;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Random;
import javax.imageio.ImageIO;

/**
 * Пиксельные фоны "сырой подвал" для игры про 7 грехов.
 *
 * Фон рисуется в маленьком разрешении (320x180) и растягивается
 * без сглаживания, поэтому пиксели остаются чёткими при любом размере окна.
 *
 * Использование в игре:
 *   BufferedImage bg = PixelBackground.render(PixelBackground.Sin.WRATH, getWidth(), getHeight());
 *   ...в paintComponent:  g.drawImage(bg, 0, 0, null);
 *   (опционально поверх)  PixelBackground.drawDust(g2, Sin.WRATH, System.currentTimeMillis(), getWidth(), getHeight());
 *
 * Компиляция (важна кодировка из-за русских названий):
 *   javac -encoding UTF-8 PixelBackground.java
 */
public class PixelBackground {

    public static final int LW = 320, LH = 180;   // внутреннее разрешение (обычная комната)
    public static final int RH = LH / 2;          // внутренняя высота НИЗКОЙ комнаты (в 2 раза ниже)
    private static int H = LH;                    // высота, с которой рисуем сейчас

    /** Грехи: название, цвет кирпича, цвет раствора, цвет свечения, цвет сырости. */
    public enum Sin {
        WRATH   ("Гнев",          0x4a2420, 0x1c0d0c, 0xd2461e, 0x2a1210),
        GREED   ("Жадность",      0x4a4128, 0x1b170c, 0xe0b030, 0x2a2a14),
        ENVY    ("Зависть",       0x2a3b2e, 0x0e1710, 0x6fbf3f, 0x16301c),
        SLOTH   ("Лень",          0x3a4050, 0x14161e, 0x7a90c0, 0x1c2430),
        PRIDE   ("Гордыня",       0x42324e, 0x17101c, 0xb070e0, 0x241a30),
        GLUTTONY("Чревоугодие",   0x4e3a26, 0x1d140c, 0xd08a30, 0x2e2414),
        LUST    ("Похоть",        0x4e2440, 0x1c0d18, 0xe04890, 0x2e1428);

        public final String title;
        public final int brick, mortar, glow, damp;

        Sin(String title, int brick, int mortar, int glow, int damp) {
            this.title = title; this.brick = brick; this.mortar = mortar;
            this.glow = glow; this.damp = damp;
        }
    }

    // ------------------------------------------------------------------
    //  ПУБЛИЧНОЕ API
    // ------------------------------------------------------------------

    /** Фон под размер окна. Пол - на 76% высоты, пол рисуется самим фоном. */
    public static BufferedImage render(Sin sin, int outW, int outH) {
        return render(sin, outW, outH, 0.76, true);
    }

    /**
     * @param floorFrac где проходит линия пола (0..1 от высоты) - подгони под свою игру
     * @param drawFloor false, если пол ты рисуешь сам: тогда кирпичная стена идёт до низа
     */
    public static BufferedImage render(Sin sin, int outW, int outH, double floorFrac, boolean drawFloor) {
        return build(sin, 0, LH, outW, outH, floorFrac, drawFloor);
    }

    /**
     * НИЗКАЯ комната конкретного греха (высота в 2 раза меньше обычной).
     * Фон (стена, пол, трубы, свет) у всех комнат одного греха одинаковый,
     * отличается только наполнение. Номер комнаты с 1 до roomCount(sin).
     * Для грехов без комнат (Зависть, Гордыня) вернётся обычный фон.
     * Пропорции картинки 32:9 - например 1280x360.
     * Поверх можно рисовать пыль: drawDust(g, sin, time, w, h, true).
     */
    public static BufferedImage renderRoom(Sin sin, int room, int outW, int outH) {
        return renderRoom(sin, room, outW, outH, 0.76);
    }

    /** То же, но с заданной линией пола (0..1 от высоты картинки). */
    public static BufferedImage renderRoom(Sin sin, int room, int outW, int outH, double floorFrac) {
        int n = roomCount(sin);
        if (n == 0) return render(sin, outW, outH, floorFrac, true);
        if (room < 1 || room > n)
            throw new IllegalArgumentException(sin.title + ": комнаты 1.." + n + ", а запрошена " + room);
        return build(sin, room, RH, outW, outH, floorFrac, true);
    }

    /** Сколько комнат у греха (0 - комнаты ещё не придуманы). */
    public static int roomCount(Sin s) {
        switch (s) {
            case WRATH:    return 2;
            case GREED:    return 3;
            case LUST:     return 1;
            case GLUTTONY: return 4;
            case SLOTH:    return 4;
            default:       return 0;
        }
    }

    /** Короткое название комнаты - для отладки и подписей. */
    public static String roomTitle(Sin s, int room) {
        String[][] t = {
                /* WRATH    */ {"Комната картин (портреты Османов)", "Комната наказаний"},
                /* GREED    */ {"Игрушки (рваные, побитые)", "Игрушки в хаосе", "Кабинет сотрудников"},
                /* ENVY     */ {},
                /* SLOTH    */ {"Ванная", "Спальня (кровать)", "Комната с ловушками", "Пустая комната (её угол)"},
                /* PRIDE    */ {},
                /* GLUTTONY */ {"Фуршет Османов", "Столовая для слуг", "Пустая комната", "Комната отходов (рыба)"},
                /* LUST     */ {"Комната с одеждой"}
        };
        return t[s.ordinal()][room - 1];
    }

    /** Насколько приглушить комнату (0..255 чёрного сверху). Гнев и Жадность тусклее. */
    private static int dimAlpha(Sin s, int room) {
        return (room > 0 && (s == Sin.WRATH || s == Sin.GREED)) ? 90 : 0;
    }

    private static synchronized BufferedImage build(Sin sin, int room, int lh, int outW, int outH,
                                                    double floorFrac, boolean drawFloor) {
        H = lh;
        BufferedImage low = new BufferedImage(LW, H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = low.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        long seed = 1000L + sin.ordinal() * 7919L;
        Random r = new Random(seed);                 // общий генератор: основа комнат одного греха совпадает
        Random rd = new Random(seed + room * 104729L); // свой генератор на декор комнаты
        int floorY = (int) (H * floorFrac);

        paintWall(g, sin, r, drawFloor ? floorY : H);
        paintDamp(g, sin, r, floorY);
        paintPipes(g, sin, r, floorY);
        if (room == 0) paintBackDecor(g, sin, r, floorY); else roomDecor(g, sin, room, rd, floorY, false);
        if (drawFloor) paintFloor(g, sin, r, floorY);
        if (room == 0) paintFrontDecor(g, sin, r, floorY); else roomDecor(g, sin, room, rd, floorY, true);
        paintLamps(g, sin, floorY);
        paintAtmosphere(g, sin, r, floorY);
        int dim = dimAlpha(sin, room);
        if (dim > 0) rect(g, 0, 0, LW, H, 0x000000, dim);
        g.dispose();

        BufferedImage out = new BufferedImage(outW, outH, BufferedImage.TYPE_INT_RGB);
        Graphics2D o = out.createGraphics();
        o.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        o.drawImage(low, 0, 0, outW, outH, null);
        o.dispose();
        H = LH;
        return out;
    }

    /** Лёгкая анимация: медленно плывущая пыль / искры. Рисуй поверх фона каждый кадр. */
    public static void drawDust(Graphics2D g, Sin sin, long timeMs, int w, int h) {
        drawDust(g, sin, timeMs, w, h, false);
    }

    /** То же для низкой комнаты (lowRoom = true), чтобы пыль не уплывала за 90 пикселей. */
    public static void drawDust(Graphics2D g, Sin sin, long timeMs, int w, int h, boolean lowRoom) {
        int lh = lowRoom ? RH : LH;
        int sx = Math.max(1, w / LW), sy = Math.max(1, h / lh);
        double t = timeMs / 1000.0;
        for (int i = 0; i < 45; i++) {
            double sp = 3 + (i * 37 % 7);
            double x = ((i * 83.0) % LW + t * sp * 0.5 + Math.sin(t * 0.7 + i) * 5) % LW;
            double y = ((i * 47.0) % lh + t * sp * 0.35) % lh;
            if (x < 0) x += LW;
            int a = (int) (30 + 60 * Math.abs(Math.sin(t * 1.3 + i)));
            g.setColor(c(i % 4 == 0 ? sin.glow : 0xb0b0b0, a));
            g.fillRect((int) x * sx, (int) y * sy, sx, sy);
        }
    }

    // ------------------------------------------------------------------
    //  ОБЩИЕ СЛОИ: стена, сырость, трубы, пол, свет, атмосфера
    // ------------------------------------------------------------------

    private static void paintWall(Graphics2D g, Sin s, Random r, int bottom) {
        int bh = 9, bw = 18;
        rect(g, 0, 0, LW, H, s.mortar, 255);
        for (int row = 0; row * bh < bottom; row++) {
            int y = row * bh;
            int off = (row % 2 == 0) ? 0 : -bw / 2;
            for (int x = off; x < LW; x += bw) {
                double v = 0.78 + r.nextDouble() * 0.44;
                rect(g, x + 1, y + 1, bw - 1, bh - 1, shade(s.brick, v), 255);
                rect(g, x + 1, y + 1, bw - 1, 1, 0xffffff, 12);        // свет сверху
                rect(g, x + 1, y + bh - 2, bw - 1, 1, 0x000000, 60);   // тень снизу
                for (int i = 0; i < 4; i++)
                    rect(g, x + 2 + r.nextInt(bw - 3), y + 2 + r.nextInt(bh - 3), 1, 1, 0x000000, 40 + r.nextInt(40));
                if (r.nextInt(14) == 0) rect(g, x + 1, y + 1, bw - 1, bh - 1, 0x000000, 70);
            }
        }
    }

    private static void paintDamp(Graphics2D g, Sin s, Random r, int floorY) {
        // тёмные потёки сверху вниз
        for (int i = 0; i < 30; i++) {
            int x = r.nextInt(LW), len = 15 + r.nextInt(Math.max(1, floorY - 30)), w = 1 + r.nextInt(2);
            for (int y = 0; y < len; y++)
                rect(g, x, y, w, 1, 0x000000, (int) (55 * (1 - (double) y / len)));
        }
        // светлые подтёки солей
        for (int i = 0; i < 9; i++) {
            int x = r.nextInt(LW), len = 10 + r.nextInt(40);
            for (int y = 0; y < len; y++)
                rect(g, x, 20 + y, 1, 1, 0xb0c0b8, (int) (22 * (1 - (double) y / len)));
        }
        // пятна влаги
        for (int i = 0; i < 14; i++)
            blot(g, r.nextInt(LW), 20 + r.nextInt(floorY - 20), 20 + r.nextInt(30), 10 + r.nextInt(20), 0x000000, 22);
        // сырость у пола
        g.setPaint(new GradientPaint(0, floorY - 55, c(s.damp, 0), 0, floorY, c(s.damp, 150)));
        g.fillRect(0, floorY - 55, LW, 55);
        // темнота под потолком
        int td = (H < LH) ? 40 : 70;
        g.setPaint(new GradientPaint(0, 0, c(0x000000, 160), 0, td, c(0x000000, 0)));
        g.fillRect(0, 0, LW, td);
    }

    private static void paintPipes(Graphics2D g, Sin s, Random r, int floorY) {
        int y = 16;
        rect(g, 0, y, LW, 6, 0x25262a, 255);
        rect(g, 0, y, LW, 1, 0x55575c, 255);
        rect(g, 0, y + 5, LW, 1, 0x0a0a0c, 255);
        rect(g, 0, y, LW, 6, s.damp, 60);
        for (int x = 30 + r.nextInt(30); x < LW; x += 60 + r.nextInt(40)) {
            rect(g, x, y - 2, 5, 10, 0x1a1a1d, 255);
            rect(g, x, y - 2, 5, 1, 0x4a4a50, 255);
            int len = 10 + r.nextInt(30);
            for (int k = 0; k < len; k++) rect(g, x + 2, y + 8 + k, 1, 1, 0x6a3a1a, Math.max(10, 110 - k * 4));
            rect(g, x + 2, y + 8 + len + 2, 1, 2, 0x9ab0c0, 120);   // капля
        }
        // вертикальная труба у правого края
        rect(g, 302, 0, 4, floorY, 0x222327, 255);
        rect(g, 302, 0, 1, floorY, 0x55575c, 255);
        for (int yy = 30; yy < floorY; yy += 38) rect(g, 300, yy, 8, 3, 0x141417, 255);
    }

    private static void paintFloor(Graphics2D g, Sin s, Random r, int floorY) {
        rect(g, 0, floorY, LW, H - floorY, shade(s.brick, 0.55), 255);
        int rows = 3, rh = (H - floorY) / rows + 1;
        for (int row = 0; row < rows; row++) {
            int y = floorY + row * rh, off = (row % 2) * 14;
            for (int x = -off; x < LW; x += 28) {
                double v = 0.7 + r.nextDouble() * 0.4;
                rect(g, x + 1, y + 1, 27, rh - 1, shade(s.brick, 0.5 * v), 255);
                rect(g, x + 1, y + 1, 27, 1, 0xffffff, 14);
                rect(g, x, y, 1, rh, s.mortar, 255);
            }
            rect(g, 0, y, LW, 1, s.mortar, 255);
        }
        rect(g, 0, floorY, LW, 1, mix(s.brick, 0xffffff, 0.22), 255);   // кромка пола
        // лужи с отражением
        for (int i = 0; i < 4; i++) {
            int x = r.nextInt(LW - 50), w = 24 + r.nextInt(30), y = floorY + 4 + r.nextInt(Math.max(1, H - floorY - 8));
            rect(g, x, y, w, 2, s.glow, 28);
            rect(g, x + 2, y + 1, w - 4, 1, 0xaac0d0, 30);
        }
        g.setPaint(new GradientPaint(0, floorY, c(0x000000, 0), 0, H, c(0x000000, 120)));
        g.fillRect(0, floorY, LW, H - floorY);
    }

    private static void paintLamps(Graphics2D g, Sin s, int floorY) {
        int[] xs = { 60 + (s.ordinal() * 13) % 30, 215 + (s.ordinal() * 17) % 40 };
        int cable = (H < LH) ? 5 : 12;          // в низкой комнате лампы висят ближе к потолку
        int sy = 22 + cable;
        for (int x : xs) {
            rect(g, x, 22, 1, cable, 0x111111, 255);
            rect(g, x - 4, sy, 9, 3, 0x1a1a1a, 255);
            rect(g, x - 2, sy + 3, 5, 2, mix(s.glow, 0xffffff, 0.3), 255);
            Polygon cone = new Polygon(new int[]{x - 3, x + 3, x + 50, x - 50}, new int[]{sy + 5, sy + 5, floorY, floorY}, 4);
            g.setPaint(new GradientPaint(0, sy + 5, c(s.glow, 44), 0, floorY, c(s.glow, 5)));
            g.fill(cone);
            g.setPaint(new RadialGradientPaint(new Point2D.Float(x, sy + 6), 34f,
                    new float[]{0f, 1f}, new Color[]{c(s.glow, 70), c(s.glow, 0)}));
            g.fillRect(x - 36, 4, 72, 72);
            rect(g, x - 34, floorY + 1, 68, 2, s.glow, 14);       // светлое пятно на полу
        }
    }

    private static void paintAtmosphere(Graphics2D g, Sin s, Random r, int floorY) {
        // туман у пола
        g.setPaint(new GradientPaint(0, floorY - 34, c(s.glow, 0), 0, floorY, c(s.glow, 30)));
        g.fillRect(0, floorY - 34, LW, 34);
        // виньетка
        g.setPaint(new RadialGradientPaint(new Point2D.Float(LW / 2f, H / 2f), LW * 0.55f,
                new float[]{0.35f, 1f}, new Color[]{c(0, 0), c(0, 200)}));
        g.fillRect(0, 0, LW, H);
        // общее приглушение - чтобы не было ярко
        rect(g, 0, 0, LW, H, 0x000000, 26);
        // зерно
        for (int i = 0; i < 2600 * H / LH; i++)
            rect(g, r.nextInt(LW), r.nextInt(H), 1, 1, r.nextBoolean() ? 0x000000 : 0xffffff, 12);
    }

    // ------------------------------------------------------------------
    //  ДЕКОР ПО ГРЕХАМ
    // ------------------------------------------------------------------

    private static void paintBackDecor(Graphics2D g, Sin s, Random r, int floorY) {
        switch (s) {
            case WRATH:    wrathBack(g, s, r, floorY); break;
            case GREED:    greedBack(g, s, r, floorY); break;
            case ENVY:     envyBack(g, s, r, floorY); break;
            case SLOTH:    slothBack(g, s, r, floorY); break;
            case PRIDE:    prideBack(g, s, r, floorY); break;
            case GLUTTONY: gluttonyBack(g, s, r, floorY); break;
            case LUST:     lustBack(g, s, r, floorY); break;
        }
    }

    private static void paintFrontDecor(Graphics2D g, Sin s, Random r, int floorY) {
        switch (s) {
            case WRATH:
                rubble(g, 60, floorY, r, s.brick); rubble(g, 200, floorY, r, s.brick); rubble(g, 275, floorY, r, s.brick);
                break;
            case GREED:
                coinPile(g, 40, floorY, 36, 14, r, s.glow);
                coinPile(g, 250, floorY, 46, 19, r, s.glow);
                break;
            case ENVY:
                for (int x = 10; x < LW; x += 38 + r.nextInt(20)) tuft(g, x, floorY, r, s.glow);
                rect(g, 120, floorY + 3, 40, 2, s.glow, 60);
                rect(g, 122, floorY + 4, 36, 1, 0xffffff, 18);
                break;
            case SLOTH:
                oldBed(g, 22, floorY, r);
                break;
            case PRIDE:
                crown(g, 205, floorY);
                for (int i = 0; i < 9; i++)
                    rect(g, 150 + r.nextInt(70), floorY + 1 + r.nextInt(5), 1 + r.nextInt(2), 1, 0xdde0ff, 70);
                break;
            case GLUTTONY:
                barrel(g, 14, floorY, 22, 28); barrel(g, 262, floorY, 22, 28); barrel(g, 285, floorY, 20, 22);
                plate(g, 110, floorY + 4); plate(g, 200, floorY + 6);
                bone(g, 135, floorY + 5); bone(g, 90, floorY + 8); bone(g, 230, floorY + 4);
                rect(g, 150, floorY + 3, 34, 2, s.glow, 40);
                break;
            case LUST:
                candle(g, 82, floorY, 8, s.glow); candle(g, 90, floorY, 5, s.glow);
                candle(g, 212, floorY, 9, s.glow); candle(g, 220, floorY, 6, s.glow); candle(g, 228, floorY, 10, s.glow);
                break;
        }
    }

    // ---------- ГНЕВ: тёмный кирпич, тлеющие трещины, цепи, следы когтей ----------
    private static void wrathBack(Graphics2D g, Sin s, Random r, int floorY) {
        for (int i = 0; i < 5; i++) blot(g, r.nextInt(LW), 30 + r.nextInt(60), 30 + r.nextInt(30), 16 + r.nextInt(14), 0x000000, 38);
        barredWindow(g, 172, 52, 26, 20, s.glow);
        claws(g, 86, 62, s.glow);
        claws(g, 246, 80, s.glow);
        for (int i = 0; i < 5; i++) glowCrack(g, 15 + r.nextInt(LW - 30), 48 + r.nextInt(55), 18 + r.nextInt(22), r, s.glow);
        chain(g, 34, 0, 58); chain(g, 134, 0, 40); chain(g, 290, 0, 66);
    }

    // ---------- ЖАДНОСТЬ: тусклое золото, дверь хранилища, ящики, монеты ----------
    private static void greedBack(Graphics2D g, Sin s, Random r, int floorY) {
        vaultDoor(g, 160, 88, 36, s.glow);
        crate(g, 12, floorY - 24, 24);  crate(g, 36, floorY - 18, 18);  crate(g, 20, floorY - 44, 18);
        crate(g, 262, floorY - 22, 22); crate(g, 284, floorY - 26, 26);
        for (int i = 0; i < 6; i++) sparkle(g, 20 + r.nextInt(280), 50 + r.nextInt(80), s.glow);
        chain(g, 100, 0, 45); chain(g, 225, 0, 52);
    }

    // ---------- ЗАВИСТЬ: мох, плесень, глаза в темноте ----------
    private static void envyBack(Graphics2D g, Sin s, Random r, int floorY) {
        for (int i = 0; i < 7; i++) moss(g, r, r.nextInt(LW - 40), 30 + r.nextInt(100), 30 + r.nextInt(30), 16 + r.nextInt(20), s.glow);
        barredWindow(g, 172, 54, 26, 20, s.glow);
        int[][] ey = {{30, 80}, {112, 100}, {246, 76}, {290, 108}, {70, 118}};
        for (int[] e : ey) eyes(g, e[0], e[1], s.glow);
        for (int i = 0; i < 9; i++) vine(g, 12 + i * 36 + r.nextInt(14), 20 + r.nextInt(40), r.nextDouble() * 6);
    }

    // ---------- ЛЕНЬ: паутина, остановившиеся часы, сырая кровать ----------
    private static void slothBack(Graphics2D g, Sin s, Random r, int floorY) {
        web(g, 0, 0, 1, 1, 46);
        web(g, LW - 1, 0, -1, 1, 40);
        web(g, 150, 22, 1, 1, 22);
        clock(g, 200, 76, 14);
        barredWindow(g, 92, 54, 26, 20, s.glow);
        chain(g, 270, 0, 30);
        for (int i = 0; i < 4; i++) blot(g, r.nextInt(LW), 60 + r.nextInt(60), 30, 14, 0x000000, 26);
    }

    // ---------- ГОРДЫНЯ: разбитое зеркало, сломанные колонны, корона на полу ----------
    private static void prideBack(Graphics2D g, Sin s, Random r, int floorY) {
        column(g, 38, floorY, 54, r);  column(g, 268, floorY, 64, r);  column(g, 100, floorY, 96, r);
        mirror(g, 148, 48, 36, 66, s.glow, r);
        sparkle(g, 160, 60, 0xdde0ff);
    }

    // ---------- ЧРЕВОУГОДИЕ: крюки с мясом, бочки, жир, кости ----------
    private static void gluttonyBack(Graphics2D g, Sin s, Random r, int floorY) {
        for (int i = 0; i < 6; i++) blot(g, r.nextInt(LW), 45 + r.nextInt(70), 26, 20, s.glow, 14);
        rect(g, 0, 32, LW, 5, 0x2a1e12, 255);
        rect(g, 0, 32, LW, 1, 0x5a4630, 255);
        int[] hx = {36, 92, 150, 205, 262, 305};
        for (int i = 0; i < hx.length; i++) hook(g, hx[i], 37, 10 + r.nextInt(6), i != 2 && i != 5);
    }

    // ---------- ПОХОТЬ: рваный бархат, свечи, треснувшее сердце ----------
    private static void lustBack(Graphics2D g, Sin s, Random r, int floorY) {
        heart(g, 160, 80, s.glow);
        curtain(g, 0, 46, floorY, r);
        curtain(g, LW - 52, LW, floorY, r);
        barredWindow(g, 132, 56, 22, 18, s.glow);
    }

    // ------------------------------------------------------------------
    //  ЭЛЕМЕНТЫ ДЕКОРА
    // ------------------------------------------------------------------

    private static void barredWindow(Graphics2D g, int x, int y, int w, int h, int glow) {
        rect(g, x - 2, y - 2, w + 4, h + 4, 0x0c0c0e, 255);
        rect(g, x, y, w, h, mix(glow, 0x000000, 0.78), 255);
        rect(g, x, y, w, h, glow, 34);
        for (int bx = x + 3; bx < x + w; bx += 5) rect(g, bx, y, 2, h, 0x0a0a0c, 255);
        rect(g, x - 2, y + h, w + 4, 2, 0x2a2a2e, 255);
        g.setPaint(new RadialGradientPaint(new Point2D.Float(x + w / 2f, y + h / 2f), 30f,
                new float[]{0f, 1f}, new Color[]{c(glow, 34), c(glow, 0)}));
        g.fillRect(x - 30, y - 30, w + 60, h + 60);
    }

    private static void chain(Graphics2D g, int x, int y0, int len) {
        for (int y = y0; y < y0 + len; y += 4) {
            if (((y - y0) / 4) % 2 == 0) {
                rect(g, x - 1, y, 3, 4, 0x4a4a50, 255);
                rect(g, x, y + 1, 1, 2, 0x0e0e10, 255);
            } else {
                rect(g, x, y, 1, 4, 0x34343a, 255);
            }
        }
        rect(g, x - 2, y0 + len, 5, 3, 0x2a2a2e, 255);    // кольцо на конце
        rect(g, x - 1, y0 + len + 1, 3, 1, 0x0e0e10, 255);
    }

    private static void claws(Graphics2D g, int x, int y, int glow) {
        for (int k = 0; k < 3; k++)
            for (int i = 0; i < 28; i++) {
                int px = x + k * 5 + i / 4, py = y + i;
                rect(g, px, py, 1, 1, 0x000000, 200);
                rect(g, px + 1, py, 1, 1, glow, 70);
            }
    }

    private static void glowCrack(Graphics2D g, int x, int y, int len, Random r, int glow) {
        int[] xs = new int[len], ys = new int[len];
        int cx = x, cy = y;
        for (int i = 0; i < len; i++) {
            xs[i] = cx; ys[i] = cy;
            cy++; cx += r.nextInt(3) - 1;
        }
        for (int i = 0; i < len; i++) rect(g, xs[i] - 1, ys[i], 3, 1, glow, 55);
        for (int i = 0; i < len; i++) rect(g, xs[i], ys[i], 1, 1, 0x000000, 230);
        for (int i = 0; i < len; i += 5) rect(g, xs[i], ys[i], 1, 1, glow, 200);
    }

    private static void rubble(Graphics2D g, int x, int floorY, Random r, int base) {
        for (int i = 0; i < 7; i++) {
            int w = 2 + r.nextInt(4), h = 1 + r.nextInt(3);
            int px = x + r.nextInt(16), py = floorY - h + 1;
            rect(g, px, py, w, h, shade(base, 0.6 + r.nextDouble() * 0.5), 255);
            rect(g, px, py, w, 1, 0xffffff, 18);
        }
    }

    private static void vaultDoor(Graphics2D g, int cx, int cy, int rad, int glow) {
        g.setColor(c(0x0a0a0c, 255)); g.fillOval(cx - rad - 3, cy - rad - 3, (rad + 3) * 2, (rad + 3) * 2);
        g.setColor(c(0x34363c, 255)); g.fillOval(cx - rad, cy - rad, rad * 2, rad * 2);
        g.setColor(c(0x24262b, 255)); g.fillOval(cx - rad + 5, cy - rad + 5, (rad - 5) * 2, (rad - 5) * 2);
        for (int i = 0; i < 12; i++) {
            double a = i * Math.PI / 6;
            rect(g, (int) (cx + Math.cos(a) * (rad - 2.5)), (int) (cy + Math.sin(a) * (rad - 2.5)), 2, 2, 0x5a5c62, 255);
        }
        g.setColor(c(0x15161a, 255));
        for (int i = 0; i < 3; i++) {
            double a = i * Math.PI / 3 + 0.3;
            for (int d = -1; d <= 1; d++)
                g.drawLine((int) (cx - Math.cos(a) * 15) + d, (int) (cy - Math.sin(a) * 15),
                        (int) (cx + Math.cos(a) * 15) + d, (int) (cy + Math.sin(a) * 15));
        }
        g.setColor(c(0x5a5c62, 255)); g.drawOval(cx - 15, cy - 15, 30, 30);
        g.setColor(c(glow, 150));     g.fillOval(cx - 4, cy - 4, 8, 8);
        rect(g, cx - 2, cy - 2, 2, 2, 0xffffff, 90);
    }

    private static void crate(Graphics2D g, int x, int y, int sz) {
        rect(g, x, y, sz, sz, 0x2c2214, 255);
        rect(g, x, y, sz, 1, 0x5a4a2a, 255);
        rect(g, x, y, 2, sz, 0x1a140a, 255);
        rect(g, x + sz - 2, y, 2, sz, 0x1a140a, 255);
        for (int i = 6; i < sz; i += 6) rect(g, x + 2, y + i, sz - 4, 1, 0x120d06, 255);
        g.setColor(c(0x120d06, 255));
        g.drawLine(x + 2, y + 2, x + sz - 3, y + sz - 3);
    }

    private static void coinPile(Graphics2D g, int cx, int floorY, int w, int h, Random r, int glow) {
        for (int row = 0; row < h; row++) {
            double f = 1.0 - (double) row / h;
            int rw = (int) (w * f * f + 4);
            for (int x = -rw / 2; x < rw / 2; x += 2) {
                int col = r.nextInt(5) == 0 ? 0x5a4a14 : mix(0x7a6420, glow, r.nextDouble() * 0.45);
                rect(g, cx + x, floorY - row, 2, 1, col, 255);
            }
        }
        for (int i = 0; i < 4; i++) sparkle(g, cx - w / 4 + r.nextInt(w / 2), floorY - h + r.nextInt(h), glow);
    }

    private static void sparkle(Graphics2D g, int x, int y, int col) {
        rect(g, x, y, 1, 1, 0xffffff, 200);
        rect(g, x - 1, y, 1, 1, col, 140); rect(g, x + 1, y, 1, 1, col, 140);
        rect(g, x, y - 1, 1, 1, col, 140); rect(g, x, y + 1, 1, 1, col, 140);
    }

    private static void moss(Graphics2D g, Random r, int x, int y, int w, int h, int glow) {
        for (int i = 0; i < w * h / 3; i++) {
            int px = x + r.nextInt(w), py = y + r.nextInt(h);
            double d = Math.hypot((px - (x + w / 2.0)) / (w / 2.0), (py - (y + h / 2.0)) / (h / 2.0));
            if (d > 1) continue;
            rect(g, px, py, 1 + r.nextInt(2), 1, mix(0x1c3a1c, glow, r.nextDouble() * 0.35), 170 + r.nextInt(80));
        }
    }

    private static void eyes(Graphics2D g, int x, int y, int glow) {
        g.setColor(c(0x000000, 150)); g.fillOval(x - 8, y - 4, 18, 9);
        rect(g, x - 5, y - 1, 3, 2, glow, 230); rect(g, x + 3, y - 1, 3, 2, glow, 230);
        rect(g, x - 4, y - 1, 1, 2, 0x000000, 255); rect(g, x + 4, y - 1, 1, 2, 0x000000, 255);
    }

    private static void vine(Graphics2D g, int x, int len, double phase) {
        for (int y = 0; y < len; y++) {
            int off = (int) (Math.sin(y * 0.15 + phase) * 2);
            rect(g, x + off, y, 1, 1, 0x2a5a2a, 230);
            if (y % 7 == 3) rect(g, x + off + 1, y, 2, 1, 0x3a7a34, 230);
        }
    }

    private static void tuft(Graphics2D g, int x, int floorY, Random r, int glow) {
        for (int i = 0; i < 8; i++)
            rect(g, x + i * 2, floorY - 1 - r.nextInt(3), 1, 2 + r.nextInt(2), mix(0x1c3a1c, glow, r.nextDouble() * 0.3), 230);
    }

    private static void web(Graphics2D g, int cx, int cy, int sx, int sy, int len) {
        int rays = 5;
        int[] ex = new int[rays], ey = new int[rays];
        g.setColor(c(0xdde4f0, 70));
        for (int k = 0; k < rays; k++) {
            double a = (Math.PI / 2) * k / (rays - 1);
            ex[k] = cx + (int) (sx * Math.cos(a) * len);
            ey[k] = cy + (int) (sy * Math.sin(a) * len);
            g.drawLine(cx, cy, ex[k], ey[k]);
        }
        for (int ring = 1; ring <= 4; ring++) {
            double f = ring / 4.0;
            for (int k = 0; k < rays - 1; k++) {
                int x1 = cx + (int) ((ex[k] - cx) * f), y1 = cy + (int) ((ey[k] - cy) * f);
                int x2 = cx + (int) ((ex[k + 1] - cx) * f), y2 = cy + (int) ((ey[k + 1] - cy) * f);
                int mx = (x1 + x2) / 2 - sx * 2, my = (y1 + y2) / 2 - sy * 2;
                g.drawLine(x1, y1, mx, my); g.drawLine(mx, my, x2, y2);
            }
        }
    }

    private static void clock(Graphics2D g, int cx, int cy, int rad) {
        g.setColor(c(0x0e0e10, 255)); g.fillOval(cx - rad - 2, cy - rad - 2, (rad + 2) * 2, (rad + 2) * 2);
        g.setColor(c(0x6a6e78, 255)); g.fillOval(cx - rad, cy - rad, rad * 2, rad * 2);
        g.setColor(c(0x000000, 70));  g.fillOval(cx - rad + 2, cy - rad + 2, (rad - 2) * 2, (rad - 2) * 2);
        for (int i = 0; i < 12; i++) {
            double a = i * Math.PI / 6;
            rect(g, (int) (cx + Math.cos(a) * (rad - 3)), (int) (cy + Math.sin(a) * (rad - 3)), 1, 1, 0x1a1a1c, 255);
        }
        g.setColor(c(0x1a1a1c, 255));
        g.drawLine(cx, cy, cx - 5, cy - 4);   // часы стоят
        g.drawLine(cx, cy, cx + 7, cy - 5);
        g.setColor(c(0x000000, 200));
        g.drawLine(cx + 3, cy + 2, cx + 7, cy + 9);   // трещина
    }

    private static void oldBed(Graphics2D g, int x, int floorY, Random r) {
        rect(g, x, floorY - 12, 3, 12, 0x1a1612, 255); rect(g, x + 46, floorY - 12, 3, 12, 0x1a1612, 255);
        rect(g, x, floorY - 28, 3, 18, 0x241d16, 255);
        rect(g, x, floorY - 12, 50, 3, 0x2c241a, 255);
        for (int i = 0; i < 46; i++) {
            int sag = (int) (3 * Math.sin(Math.PI * i / 46.0));
            rect(g, x + 2 + i, floorY - 17 + sag, 1, 5, 0x4a4e5a, 255);
            rect(g, x + 2 + i, floorY - 17 + sag, 1, 1, 0x7a7e90, 255);
            if (i > 20) rect(g, x + 2 + i, floorY - 16 + sag, 1, 3 + r.nextInt(5), 0x30384a, 255);
        }
        rect(g, x + 4, floorY - 21, 10, 4, 0x70748a, 255);
    }

    private static void column(Graphics2D g, int x, int floorY, int yTop, Random r) {
        for (int i = 0; i < 14; i++) {
            double f = i < 4 ? 1.15 : (i > 10 ? 0.6 : 0.9);
            int jag = r.nextInt(6);
            rect(g, x + i, yTop + jag, 1, floorY - yTop - jag, shade(0x4a4258, f), 255);
            if (i % 3 == 1) rect(g, x + i, yTop + jag + 2, 1, floorY - yTop - jag - 7, 0x000000, 50);
        }
        rect(g, x - 2, floorY - 5, 18, 5, 0x3a3348, 255);
        rect(g, x - 2, floorY - 5, 18, 1, 0x6a6280, 255);
        g.setColor(c(0x000000, 190));
        g.drawLine(x + 4, yTop + 8, x + 8, yTop + 20);
        g.drawLine(x + 8, yTop + 20, x + 6, yTop + 32);
    }

    private static void mirror(Graphics2D g, int x, int y, int w, int h, int glow, Random r) {
        rect(g, x - 4, y - 4, w + 8, h + 8, 0x3a2e1a, 255);
        rect(g, x - 4, y - 4, w + 8, 1, 0x7a6436, 255);
        rect(g, x - 4, y - 4, 1, h + 8, 0x6a5430, 255);
        rect(g, x - 6, y + h / 2 - 3, 2, 6, 0x6a5430, 255);
        rect(g, x + w + 4, y + h / 2 - 3, 2, 6, 0x6a5430, 255);
        rect(g, x + w / 2 - 3, y - 6, 6, 2, 0x6a5430, 255);
        g.setPaint(new GradientPaint(x, y, c(mix(glow, 0x000000, 0.45), 255), x, y + h, c(mix(glow, 0x000000, 0.8), 255)));
        g.fillRect(x, y, w, h);
        g.setColor(c(0xffffff, 22));
        for (int i = 0; i < 4; i++) g.drawLine(x + 4 + i * 8, y + h, x + 16 + i * 8, y);
        int cx = x + (int) (w * 0.6), cy = y + (int) (h * 0.4);
        g.setColor(c(0xe8eaff, 130));
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4 + r.nextDouble() * 0.4;
            int len = 12 + r.nextInt(26);
            g.drawLine(cx, cy, cx + (int) (Math.cos(a) * len), cy + (int) (Math.sin(a) * len));
        }
        g.setColor(c(0x0a0a0c, 255));                           // выпавший осколок
        g.fill(new Polygon(new int[]{x + w - 11, x + w, x + w}, new int[]{y, y, y + 15}, 3));
    }

    private static void crown(Graphics2D g, int x, int floorY) {
        int gold = 0x8a7030;
        rect(g, x, floorY - 4, 11, 4, gold, 255);
        rect(g, x, floorY - 4, 11, 1, 0xc0a050, 255);
        rect(g, x, floorY - 7, 2, 3, gold, 255);
        rect(g, x + 4, floorY - 8, 3, 4, gold, 255);
        rect(g, x + 9, floorY - 7, 2, 3, gold, 255);
        rect(g, x + 2, floorY - 2, 1, 1, 0x3050c0, 255);
        rect(g, x + 5, floorY - 2, 1, 1, 0xc03030, 255);
        rect(g, x + 8, floorY - 2, 1, 1, 0x30a050, 255);
    }

    private static void hook(Graphics2D g, int x, int y0, int len, boolean meat) {
        rect(g, x, y0, 1, len, 0x4a4a50, 255);
        int hy = y0 + len;
        rect(g, x, hy, 1, 2, 0x5a5a60, 255);
        rect(g, x + 1, hy + 2, 2, 1, 0x5a5a60, 255);
        rect(g, x + 3, hy, 1, 2, 0x5a5a60, 255);
        if (meat) {
            int my = hy + 2;
            g.setColor(c(0x4a2018, 255)); g.fillOval(x - 6, my, 14, 22);
            g.setColor(c(0x6e3226, 255)); g.fillOval(x - 4, my + 2, 6, 10);
            g.setColor(c(0xc09a78, 140)); g.fillRect(x - 5, my + 7, 1, 8);
        }
    }

    private static void barrel(Graphics2D g, int x, int floorY, int w, int h) {
        int y = floorY - h;
        rect(g, x, y, w, h, 0x3a2814, 255);
        rect(g, x, y, 2, h, 0x20160a, 255);
        rect(g, x + w - 2, y, 2, h, 0x20160a, 255);
        rect(g, x + 3, y, 2, h, 0x5a4226, 255);
        rect(g, x, y + 4, w, 2, 0x1a1a1c, 255);
        rect(g, x, y + h - 6, w, 2, 0x1a1a1c, 255);
        rect(g, x, y, w, 1, 0x6a5030, 255);
    }

    private static void plate(Graphics2D g, int x, int y) {
        g.setColor(c(0x60626a, 255)); g.fillOval(x, y, 14, 4);
        g.setColor(c(0x8a8c94, 255)); g.fillOval(x + 2, y + 1, 10, 2);
    }

    private static void bone(Graphics2D g, int x, int y) {
        rect(g, x, y, 9, 1, 0xb8b09a, 255);
        rect(g, x - 1, y - 1, 2, 3, 0xc8c0aa, 255);
        rect(g, x + 8, y - 1, 2, 3, 0xc8c0aa, 255);
    }

    private static void curtain(Graphics2D g, int x0, int x1, int floorY, Random r) {
        for (int x = x0; x < x1; x++) {
            double f = 0.55 + 0.45 * Math.sin(x * 0.55);
            int col = shade(0x70204a, f);
            int bottom = floorY - 4 - r.nextInt(16);
            rect(g, x, 6, 1, bottom - 6, col, 255);
            rect(g, x, bottom - 3, 1, 3, 0x000000, 70);       // рваный край
        }
        rect(g, x0 - 2, 4, x1 - x0 + 4, 3, 0x2a2218, 255);
        rect(g, x0 - 2, 4, x1 - x0 + 4, 1, 0x6a5a3a, 255);
        for (int x = x0; x < x1; x += 7) rect(g, x, 3, 2, 5, 0x5a4a2a, 255);   // кольца
    }

    private static void heart(Graphics2D g, int cx, int cy, int glow) {
        String[] m = { ".XX.XX.", "XXXXXXX", "XXXXXXX", ".XXXXX.", "..XXX..", "...X..." };
        int sc = 4, x0 = cx - m[0].length() * sc / 2, y0 = cy - m.length * sc / 2;
        g.setPaint(new RadialGradientPaint(new Point2D.Float(cx, cy), 36f,
                new float[]{0f, 1f}, new Color[]{c(glow, 60), c(glow, 0)}));
        g.fillRect(cx - 36, cy - 36, 72, 72);
        for (int j = 0; j < m.length; j++)
            for (int i = 0; i < m[j].length(); i++)
                if (m[j].charAt(i) == 'X') rect(g, x0 + i * sc, y0 + j * sc, sc, sc, mix(glow, 0x000000, 0.45), 200);
        g.setColor(c(0x000000, 220));                       // трещина
        g.drawLine(cx, y0 - 1, cx - 2, y0 + 8);
        g.drawLine(cx - 2, y0 + 8, cx + 2, y0 + 14);
        g.drawLine(cx + 2, y0 + 14, cx, y0 + 22);
    }

    private static void candle(Graphics2D g, int x, int floorY, int h, int glow) {
        g.setPaint(new RadialGradientPaint(new Point2D.Float(x + 1, floorY - h - 2), 26f,
                new float[]{0f, 1f}, new Color[]{c(glow, 85), c(glow, 0)}));
        g.fillRect(x - 26, floorY - h - 28, 54, 54);
        rect(g, x, floorY - h, 3, h, 0xcdbfa6, 255);
        rect(g, x, floorY - h, 1, h, 0xffffff, 40);
        rect(g, x + 1, floorY - h - 3, 1, 3, 0xffe08a, 255);
        rect(g, x + 1, floorY - h - 2, 1, 1, 0xffffff, 220);
        rect(g, x + 3, floorY - h + 1, 1, 3, 0xcdbfa6, 200);   // потёк воска
    }

    private static void blot(Graphics2D g, int x, int y, int w, int h, int rgb, int a) {
        g.setColor(c(rgb, a));
        g.fillOval(x - w / 2, y - h / 2, w, h);
    }

    // ------------------------------------------------------------------
    //  КОМНАТЫ (низкие, 320x90): декор по комнатам
    // ------------------------------------------------------------------

    private static void roomDecor(Graphics2D g, Sin s, int room, Random r, int fy, boolean front) {
        switch (s) {
            case WRATH:
                if (room == 1) wrathPortraits(g, s, r, fy, front); else wrathPunish(g, s, r, fy, front);
                break;
            case GREED:
                if (room == 1) greedToys(g, s, r, fy, front, false);
                else if (room == 2) greedToys(g, s, r, fy, front, true);
                else greedOffice(g, s, r, fy, front);
                break;
            case LUST:
                lustClothes(g, s, r, fy, front);
                break;
            case GLUTTONY:
                if (room == 1) buffet(g, s, r, fy, front);
                else if (room == 2) servants(g, s, r, fy, front);
                else if (room == 3) { if (front) blot(g, 160, fy + 10, 40, 8, 0x000000, 30); }
                else wasteRoom(g, s, r, fy, front);
                break;
            case SLOTH:
                if (room == 1) bathRoom(g, s, r, fy, front);
                else if (room == 2) bedRoom(g, s, r, fy, front);
                else if (room == 3) trapRoom(g, s, r, fy, front);
                else ragRoom(g, s, r, fy, front);
                break;
            default: break;
        }
    }

    // ---------- ГНЕВ 1: большие портреты Османов ----------
    private static void wrathPortraits(Graphics2D g, Sin s, Random r, int fy, boolean front) {
        if (!front) {
            for (int i = 0; i < 4; i++) blot(g, r.nextInt(LW), 30 + r.nextInt(30), 30 + r.nextInt(30), 12 + r.nextInt(10), 0x000000, 38);
            portrait(g, 38, 24, 36, 38, r, s.glow, false);
            portrait(g, 137, 22, 46, 40, r, s.glow, false);
            portrait(g, 246, 24, 36, 38, r, s.glow, true);
            claws(g, 214, 34, s.glow);
            int[] cx = {18, 112, 196};
            for (int x : cx) glowCrack(g, x, 28 + r.nextInt(8), 18 + r.nextInt(8), r, s.glow);
            chain(g, 100, 22, 28); chain(g, 292, 22, 36);
        } else {
            rubble(g, 60, fy, r, s.brick); rubble(g, 200, fy, r, s.brick); rubble(g, 275, fy, r, s.brick);
        }
    }

    // ---------- ГНЕВ 2: комната наказаний ----------
    private static void wrathPunish(Graphics2D g, Sin s, Random r, int fy, boolean front) {
        if (!front) {
            for (int i = 0; i < 4; i++) blot(g, r.nextInt(LW), 30 + r.nextInt(30), 24 + r.nextInt(20), 10 + r.nextInt(8), 0x2a0808, 60);
            plank(g, 14, 30, 74);
            int[] wl = {20, 31, 42, 53, 64};
            for (int i = 0; i < wl.length; i++) whip(g, wl[i], 33, 10 + r.nextInt(8), 1 + i % 3);
            for (int i = 0; i < 3; i++) cane(g, 76 + i * 4, 33, 22 + r.nextInt(4));
            plank(g, 232, 34, 66);
            paddle(g, 240, 37); paddle(g, 254, 37);
            chain(g, 270, 37, 12); cuff(g, 270, 49);
            chain(g, 282, 37, 18); cuff(g, 282, 55);
            whip(g, 292, 37, 12, 2);
            barredWindow(g, 140, 30, 22, 16, s.glow);
            chain(g, 112, 22, 22); cuff(g, 112, 44);
            chain(g, 206, 22, 16); cuff(g, 206, 38);
        } else {
            stocks(g, 168, fy);
            bench(g, 100, fy);
            bucket(g, 225, fy);
            blot(g, 150, fy + 8, 26, 6, 0x3a0c0c, 90);
            blot(g, 60, fy + 12, 20, 5, 0x3a0c0c, 90);
            rubble(g, 275, fy, r, s.brick);
        }
    }

    // ---------- ЖАДНОСТЬ 1-2: побитые рваные игрушки (порядок / хаос) ----------
    private static void greedToys(Graphics2D g, Sin s, Random r, int fy, boolean front, boolean chaos) {
        if (!front) {
            for (int i = 0; i < 4; i++) sparkle(g, 20 + r.nextInt(280), 30 + r.nextInt(30), s.glow);
            if (!chaos) {
                plank(g, 10, 42, 98); plank(g, 214, 48, 84);
                toy(g, 0, 26, 42, 0.8, r); toy(g, 1, 50, 42, 0.8, r); toy(g, 2, 72, 42, 1, r); toy(g, 3, 90, 42, 1, r);
                toy(g, 1, 230, 48, 0.8, r); toy(g, 0, 254, 48, 0.8, r); toy(g, 3, 278, 48, 1, r);
                barredWindow(g, 150, 28, 22, 16, s.glow);
            } else {
                java.awt.geom.AffineTransform old = g.getTransform();
                g.rotate(0.5, 60, 40);
                plank(g, 30, 40, 70);
                g.setTransform(old);
                chain(g, 150, 22, 10);
                rotToy(g, 0.25, 0, 150, 49, 0.8, r);
                chain(g, 230, 22, 6);
                rotToy(g, -0.3, 1, 230, 43, 0.8, r);
            }
        } else {
            if (!chaos) {
                crate(g, 12, fy - 20, 20); crate(g, 34, fy - 14, 14);
                toy(g, 0, 150, fy + 8, 1.8, r);
                rotToy(g, -1.3, 1, 100, fy + 12, 1.0, r);
                toy(g, 2, 205, fy + 10, 1.0, r);
                toy(g, 3, 235, fy + 14, 1.0, r);
                toy(g, 4, 70, fy + 14, 1.0, r);
                coinPile(g, 285, fy, 26, 8, r, s.glow);
                for (int i = 0; i < 12; i++) rect(g, 90 + r.nextInt(120), fy + 3 + r.nextInt(16), 1, 1, 0xc8c0aa, 150);
            } else {
                for (int i = 0; i < 18; i++) {
                    int kind = r.nextInt(5), x = 10 + r.nextInt(300), by = fy + 3 + r.nextInt(18);
                    rotToy(g, (r.nextDouble() - 0.5) * 5, kind, x, by, 0.8 + r.nextDouble() * 0.7, r);
                }
                for (int i = 0; i < 6; i++)
                    rotToy(g, (r.nextDouble() - 0.5) * 4, r.nextInt(5), 130 + r.nextInt(60), fy + 1 + r.nextInt(8), 1.0, r);
                for (int i = 0; i < 30; i++) rect(g, 10 + r.nextInt(300), fy + 2 + r.nextInt(20), 1, 1, 0xc8c0aa, 150);
            }
        }
    }

    // ---------- ЖАДНОСТЬ 3: кабинет сотрудников, на столе чемодан с деньгами ----------
    private static void greedOffice(Graphics2D g, Sin s, Random r, int fy, boolean front) {
        if (!front) {
            barredWindow(g, 56, 30, 22, 16, s.glow);
            clock(g, 160, 38, 9);
            rect(g, 100, 28, 28, 18, 0x4a3a22, 255);
            for (int i = 0; i < 5; i++) rect(g, 103 + i * 5, 30 + (i % 2) * 6, 4, 6, 0xb8b4a4, 200);
            vaultDoor(g, 268, 44, 14, s.glow);
            chair(g, 26, fy); chair(g, 112, fy); chair(g, 198, fy);
        } else {
            desk(g, 14, fy, 58);
            desk(g, 100, fy, 62);
            suitcase(g, 124, fy - 14, s.glow);
            desk(g, 186, fy, 58);
        }
    }

    // ---------- ПОХОТЬ: комната с пошлой одеждой ----------
    private static void lustClothes(Graphics2D g, Sin s, Random r, int fy, boolean front) {
        if (!front) {
            curtain(g, 0, 36, fy, r);
            curtain(g, LW - 40, LW, fy, r);
            for (int x = 40; x < 280; x++) rect(g, x, 28 + (int) (6 * Math.sin(Math.PI * (x - 40) / 240.0)), 1, 1, 0x8a7a5a, 255);
            int[] gx = {58, 84, 110, 136, 162, 188, 214, 240};
            for (int i = 0; i < gx.length; i++) {
                int y = 29 + (int) (6 * Math.sin(Math.PI * (gx[i] - 40) / 240.0));
                switch (i % 4) {
                    case 0: dress(g, gx[i], y, i % 8 == 0 ? 0x8a1a3a : 0x6a1a5a); break;
                    case 1: corset(g, gx[i], y); break;
                    case 2: stockings(g, gx[i], y); break;
                    default: boa(g, gx[i], y, r); break;
                }
            }
        } else {
            rect(g, 90, fy + 6, 140, 10, 0x5a1a30, 120);
            candle(g, 60, fy, 7, s.glow); candle(g, 68, fy, 5, s.glow);
            candle(g, 252, fy, 8, s.glow); candle(g, 260, fy, 6, s.glow);
            heel(g, 120, fy + 10); heel(g, 134, fy + 12); heel(g, 200, fy + 9);
        }
    }

    // ---------- ЧРЕВОУГОДИЕ 1: фуршет (галлюцинация - съесть нельзя) ----------
    private static void buffet(Graphics2D g, Sin s, Random r, int fy, boolean front) {
        if (!front) {
            banner(g, 44, 24, 22, 30, 0x5a2a20); banner(g, 244, 24, 22, 30, 0x5a2a20);
            barredWindow(g, 148, 28, 24, 14, s.glow);
        } else {
            int ty = fy - 18;
            rect(g, 10, ty, 280, 2, 0xd8d0c0, 255);
            rect(g, 10, ty + 2, 280, 18, 0xb4ac9c, 255);
            for (int x = 14; x < 288; x += 6) rect(g, x, ty + 2, 1, 18, 0x000000, x % 12 == 0 ? 42 : 28);
            rect(g, 10, ty + 19, 280, 1, 0x000000, 70);
            for (int x : new int[]{36, 78, 118, 184, 244, 270}) plate(g, x - 7, ty - 3);
            g.setColor(c(0x8a5a2a, 255)); g.fillOval(26, ty - 9, 20, 9);                       // жаркое
            g.setColor(c(0xc08a4a, 160)); g.fillOval(30, ty - 8, 10, 4);
            rect(g, 44, ty - 9, 5, 2, 0xc8c0aa, 255);
            rect(g, 70, ty - 6, 16, 6, 0xe0d0c0, 255); rect(g, 72, ty - 11, 12, 5, 0xd88aa0, 255);   // торт
            rect(g, 75, ty - 14, 6, 3, 0xe0d0c0, 255); rect(g, 77, ty - 16, 2, 2, 0xc02030, 255);
            int[] fc = {0xb02030, 0x4a8a30, 0xd08a20, 0x6a2a6a};                                 // фрукты
            for (int i = 0; i < 4; i++) { g.setColor(c(fc[i], 255)); g.fillOval(110 + i * 4, ty - 6 - (i % 2) * 2, 5, 5); }
            rect(g, 152, ty - 14, 2, 14, 0xa08a40, 255); rect(g, 146, ty - 14, 14, 1, 0xa08a40, 255);  // канделябр
            candle(g, 145, ty - 13, 5, s.glow); candle(g, 151, ty - 13, 6, s.glow); candle(g, 157, ty - 13, 5, s.glow);
            g.setColor(c(0xa87a3a, 255)); g.fillOval(178, ty - 6, 12, 5); g.fillOval(182, ty - 9, 12, 5);  // хлеб
            rect(g, 208, ty - 14, 3, 14, 0x1a3a1c, 255); rect(g, 209, ty - 18, 1, 4, 0x1a3a1c, 255);     // вино
            for (int x : new int[]{218, 226}) { rect(g, x, ty - 4, 1, 4, 0xc8c8d0, 255); rect(g, x - 1, ty - 7, 3, 3, 0xc8c8d0, 120); }
            g.setColor(c(0xd8b040, 255)); g.fillOval(237, ty - 6, 14, 6);                         // сыр
            rect(g, 242, ty - 4, 2, 2, 0x6a5010, 255);
            g.setColor(c(0x9a6a30, 255)); g.fillOval(263, ty - 6, 14, 6);                         // пирог
            rect(g, 267, ty - 5, 5, 1, 0x4a2a10, 255);
            for (int yy = ty - 22; yy < ty + 20; yy += 3) rect(g, 10, yy, 280, 1, 0xffe8c0, 9);   // мираж
            for (int i = 0; i < 10; i++) sparkle(g, 14 + r.nextInt(270), ty - 22 + r.nextInt(22), 0xffe0a0);
        }
    }

    // ---------- ЧРЕВОУГОДИЕ 2: столовая для слуг ----------
    private static void servants(Graphics2D g, Sin s, Random r, int fy, boolean front) {
        if (!front) {
            plank(g, 36, 32, 240);
            pot(g, 52, 35, 9); pot(g, 96, 35, 7); pot(g, 140, 35, 10); pot(g, 230, 35, 8);
            rect(g, 118, 35, 1, 10, 0x6a6a70, 255); g.setColor(c(0x6a6a70, 255)); g.fillOval(116, 44, 5, 3);
            for (int k = 0; k < 4; k++) rect(g, 100 + k * 3, 48, 1, 7, 0xd8d8d0, 70);
            g.setColor(c(0xd8d8d0, 70)); g.drawLine(98, 52, 111, 50);
            g.drawLine(226, 46, 236, 54); g.drawLine(236, 46, 226, 54);
            barredWindow(g, 176, 38, 22, 12, s.glow);
        } else {
            int x0 = 60, w = 190;
            rect(g, x0, fy - 14, w, 3, 0x4a3620, 255); rect(g, x0, fy - 14, w, 1, 0x7a5a38, 255);
            rect(g, x0 + 4, fy - 11, 3, 13, 0x2a1c0e, 255); rect(g, x0 + w - 7, fy - 11, 3, 13, 0x2a1c0e, 255);
            rect(g, x0 + 8, fy - 6, w - 16, 2, 0x3a2a18, 255);
            rect(g, x0 + 10, fy - 4, 2, 6, 0x2a1c0e, 255); rect(g, x0 + w - 12, fy - 4, 2, 6, 0x2a1c0e, 255);
            for (int i = 0; i < 6; i++) {
                int bx = x0 + 20 + i * 30;
                bowl(g, bx, fy - 14);
                if (i == 1 || i == 4)
                    for (int yy = 0; yy < 9; yy++) rect(g, bx + (int) (2 * Math.sin(yy)), fy - 20 - yy, 1, 1, 0xffe0b0, 70);
            }
            stool(g, 18, fy);
        }
    }

    // ---------- ЧРЕВОУГОДИЕ 4: комната отходов (здесь настоящая рыба) ----------
    private static void wasteRoom(Graphics2D g, Sin s, Random r, int fy, boolean front) {
        if (!front) {
            barredWindow(g, 56, 32, 20, 14, s.glow);
            for (int i = 0; i < 3; i++) blot(g, r.nextInt(LW), 30 + r.nextInt(30), 26, 10, 0x1a2a10, 50);
        } else {
            garbageHeap(g, 40, fy + 2, 50, 20, r);
            garbageHeap(g, 250, fy + 3, 60, 24, r);
            garbageHeap(g, 100, fy + 1, 26, 10, r);
            crate(g, 142, fy - 12, 16);
            fish(g, 143, fy - 18);
            sparkle(g, 141, fy - 21, 0xdde8f0); sparkle(g, 159, fy - 19, 0xdde8f0);
            bowl(g, 182, fy + 6);
            bone(g, 70, fy + 8); bone(g, 205, fy + 12); bone(g, 128, fy + 14); bone(g, 300, fy + 10);
            blot(g, 175, fy + 12, 30, 5, 0x3a4a1a, 70);
            for (int i = 0; i < 14; i++) rect(g, 20 + r.nextInt(280), fy - 14 + r.nextInt(14), 1, 1, 0x000000, 160);
        }
    }

    // ---------- ЛЕНЬ 1: ванная ----------
    private static void bathRoom(Graphics2D g, Sin s, Random r, int fy, boolean front) {
        if (!front) {
            barredWindow(g, 60, 30, 22, 14, s.glow);
            smallMirror(g, 236, 28, 16, 22, s.glow);
            rect(g, 150, 22, 3, 16, 0x3a3c42, 255); rect(g, 150, 36, 10, 3, 0x3a3c42, 255);
            rect(g, 158, 36, 3, 5, 0x3a3c42, 255); rect(g, 159, 43, 1, 2, 0x9ab0c0, 200);
            rect(g, 190, 36, 24, 2, 0x7a7c84, 255);
            rect(g, 194, 38, 16, 14, 0x5a5648, 255);
            for (int i = 0; i < 16; i += 3) rect(g, 194 + i, 52, 2, 2 + r.nextInt(3), 0x5a5648, 255);
            for (int i = 0; i < 16; i += 4) rect(g, 194 + i, 38, 1, 14, 0x000000, 40);
        } else {
            int x = 100, w = 96;
            rect(g, x + 6, fy - 4, 6, 6, 0x2a2218, 255); rect(g, x + w - 12, fy - 4, 6, 6, 0x2a2218, 255);
            g.setColor(c(0xa8acb2, 255)); g.fillOval(x, fy - 30, w, 9);
            g.setColor(c(0x2f3a24, 255)); g.fillOval(x + 5, fy - 29, w - 10, 6);
            rect(g, x + 20, fy - 27, 12, 1, 0x6a7a50, 120);
            rect(g, x + 30, fy - 27, 3, 1, 0x1a1a10, 200);
            g.setPaint(new GradientPaint(x, fy - 24, c(0x8a8e94, 255), x, fy - 4, c(0x4a4e54, 255)));
            g.fillRect(x, fy - 25, w, 22);
            rect(g, x - 2, fy - 25, w + 4, 2, 0xb8bcc2, 255);
            rect(g, x - 2, fy - 25, w + 4, 1, 0xe0e4ea, 160);
            for (int i = 0; i < 6; i++) rect(g, x + 8 + r.nextInt(w - 16), fy - 22, 1, 6 + r.nextInt(10), 0x4a2a14, 120);
            rect(g, x + w + 8, fy - 2, 6, 3, 0x9a9a80, 255);
            bucket(g, x - 18, fy);
        }
    }

    // ---------- ЛЕНЬ 2: кровать, куда залезла кошка ----------
    private static void bedRoom(Graphics2D g, Sin s, Random r, int fy, boolean front) {
        if (!front) {
            web(g, 0, 22, 1, 1, 34);
            web(g, LW - 1, 22, -1, 1, 30);
            clock(g, 236, 40, 9);
            barredWindow(g, 64, 32, 22, 14, s.glow);
            for (int i = 0; i < 3; i++) blot(g, r.nextInt(LW), 30 + r.nextInt(30), 30, 12, 0x000000, 26);
        } else {
            int x = 110;
            oldBed(g, x, fy, r);
            g.setColor(c(0x3a4258, 255)); g.fillOval(x + 24, fy - 27, 22, 9);
            g.setColor(c(0x5a627a, 120)); g.fillOval(x + 26, fy - 27, 12, 4);
            g.setColor(c(0x242a3a, 255)); g.fillOval(x + 34, fy - 26, 8, 4);        // вмятина от кошки
            rect(g, x + 30, fy - 20, 1, 1, 0x1a1e2a, 255); rect(g, x + 33, fy - 19, 1, 1, 0x1a1e2a, 255);   // лапки
            crate(g, x + 58, fy - 12, 12);
            candle(g, x + 62, fy - 12, 5, s.glow);
            rect(g, x + 76, fy + 4, 6, 2, 0x4a3a2a, 255); rect(g, x + 84, fy + 6, 6, 2, 0x4a3a2a, 255);
        }
    }

    // ---------- ЛЕНЬ 3: ловушки ----------
    private static void trapRoom(Graphics2D g, Sin s, Random r, int fy, boolean front) {
        if (!front) {
            for (int i = 0; i < 6; i++) rect(g, 30 + i * 10, 38, 2, 2, 0x050505, 255);
            rect(g, 25, 38, 6, 1, 0x7a6a4a, 255); rect(g, 48, 38, 6, 1, 0x7a6a4a, 255);
            chain(g, 250, 22, 20);
            barredWindow(g, 150, 30, 22, 14, s.glow);
        } else {
            rect(g, 58, fy - 10, 2, 10, 0x3a2814, 255); rect(g, 260, fy - 10, 2, 10, 0x3a2814, 255);
            g.setColor(c(0xc8c8d0, 150)); g.drawLine(60, fy - 6, 260, fy - 4);
            rect(g, 110, fy - 5, 4, 4, 0x7a7c84, 255); rect(g, 170, fy - 4, 4, 4, 0x7a7c84, 255);
            rect(g, 244, fy + 3, 34, 12, 0x040404, 255);
            rect(g, 244, fy + 2, 34, 1, 0x4a3a2a, 255);
            for (int i = 0; i < 7; i++) {
                int sx = 246 + i * 5;
                g.setColor(c(0x8a8c94, 255));
                g.fill(new Polygon(new int[]{sx, sx + 4, sx + 2}, new int[]{fy + 14, fy + 14, fy + 5}, 3));
            }
            rect(g, 168, fy + 5, 16, 3, 0x4a4a52, 255); rect(g, 168, fy + 5, 16, 1, 0x8a8a92, 255);
            rect(g, 174, fy + 6, 4, 1, 0xb02020, 200);
            bearTrap(g, 80, fy + 8); bearTrap(g, 130, fy + 13); bearTrap(g, 215, fy + 9);
        }
    }

    // ---------- ЛЕНЬ 4: пустая комната - её угол: тряпка и одна игрушка ----------
    private static void ragRoom(Graphics2D g, Sin s, Random r, int fy, boolean front) {
        if (!front) {
            barredWindow(g, 150, 30, 22, 14, s.glow);
            g.setColor(c(s.glow, 16));
            g.fill(new Polygon(new int[]{148, 174, 200, 130}, new int[]{44, 44, fy + 20, fy + 20}, 4));
        } else {
            for (int i = 0; i < 3; i++) {
                int w = 40 + r.nextInt(30), x = r.nextInt(LW - w), y = fy + 4 + r.nextInt(14);
                rect(g, x, y, w, 2, s.glow, 40);
                rect(g, x + 2, y + 1, w - 4, 1, 0xaac0d0, 40);
            }
            g.setColor(c(0x4a463e, 255));
            g.fill(new Polygon(new int[]{110, 165, 172, 160, 108, 104}, new int[]{fy + 7, fy + 5, fy + 12, fy + 19, fy + 17, fy + 11}, 6));
            g.setColor(c(0x5a564c, 255));
            g.fill(new Polygon(new int[]{110, 165, 163, 112}, new int[]{fy + 7, fy + 5, fy + 8, fy + 10}, 4));
            for (int i = 0; i < 8; i++) rect(g, 110 + r.nextInt(55), fy + 8 + r.nextInt(10), 1, 1, 0x000000, 90);
            for (int i = 0; i < 10; i += 2) rect(g, 106 + i * 6, fy + 18 + (i % 3), 2, 1 + (i % 2), 0x4a463e, 255);
            teddy(g, 176, fy + 16, 0.8, r);
        }
    }

    // ------------------------------------------------------------------
    //  ПРЕДМЕТЫ ДЛЯ КОМНАТ
    // ------------------------------------------------------------------

    private static void portrait(Graphics2D g, int x, int y, int w, int h, Random r, int glow, boolean slashed) {
        rect(g, x - 3, y - 3, w + 6, h + 6, 0x2a200e, 255);
        rect(g, x - 3, y - 3, w + 6, 1, 0x8a7236, 255);
        rect(g, x - 3, y - 3, 1, h + 6, 0x6a5428, 255);
        rect(g, x - 3, y + h + 2, w + 6, 1, 0x120c04, 255);
        rect(g, x - 1, y - 1, w + 2, h + 2, 0x5a4622, 255);
        g.setPaint(new GradientPaint(x, y, c(0x3a2c1c, 255), x, y + h, c(0x120c08, 255)));
        g.fillRect(x, y, w, h);
        int cx = x + w / 2;
        g.setColor(c(0x1a1214, 255));
        g.fill(new Polygon(new int[]{x + 1, cx - w / 5, cx + w / 5, x + w - 1}, new int[]{y + h, y + h * 6 / 10, y + h * 6 / 10, y + h}, 4));
        rect(g, cx - w / 6, y + h * 58 / 100, w / 3, 3, 0xb8b4a8, 255);
        rect(g, cx - w / 6, y + h * 58 / 100 + 2, w / 3, 1, 0x6a665c, 255);
        int hw = w * 28 / 100, hh = h * 34 / 100, hy = y + h * 17 / 100;
        g.setColor(c(0x8a6a4a, 255)); g.fillOval(cx - hw, hy, hw * 2, hh);
        g.setColor(c(0x1a1210, 255));
        g.fillArc(cx - hw - 1, hy - 2, hw * 2 + 2, hh / 2 + 2, 0, 180);
        rect(g, cx - hw - 1, hy + 2, 2, hh / 2, 0x1a1210, 255); rect(g, cx + hw - 1, hy + 2, 2, hh / 2, 0x1a1210, 255);
        int ey = hy + hh / 2 - 1;
        rect(g, cx - hw / 2 - 1, ey, 2, 1, glow, 230); rect(g, cx + hw / 2, ey, 2, 1, glow, 230);
        rect(g, cx - hw / 2 - 2, ey - 2, 4, 1, 0x1a1210, 255); rect(g, cx + hw / 2 - 1, ey - 2, 4, 1, 0x1a1210, 255);
        rect(g, cx - 2, ey + hh / 3, 4, 1, 0x2a1010, 255);
        rect(g, cx + w / 6, y + h * 68 / 100, 2, 2, 0xc0a040, 255);
        rect(g, x, y, w, h, 0x000000, 30);
        for (int i = 0; i < 5; i++) rect(g, x + r.nextInt(w), y + r.nextInt(h), 2, 1, 0x000000, 60);
        rect(g, cx - 6, y + h + 4, 12, 2, 0xa08a40, 255);
        if (slashed) {
            g.setColor(c(0x000000, 235));
            for (int k = 0; k < 3; k++) g.drawLine(x + 4 + k * 6, y + 3, x + w - 12 + k * 4, y + h - 5);
            g.setColor(c(glow, 90));
            for (int k = 0; k < 3; k++) g.drawLine(x + 5 + k * 6, y + 3, x + w - 11 + k * 4, y + h - 5);
        }
    }

    private static void plank(Graphics2D g, int x, int y, int w) {
        rect(g, x, y, w, 3, 0x3a2814, 255);
        rect(g, x, y, w, 1, 0x6a5030, 255);
        rect(g, x, y + 3, w, 1, 0x000000, 90);
        rect(g, x + 2, y + 3, 2, 4, 0x2a1c0e, 255); rect(g, x + w - 4, y + 3, 2, 4, 0x2a1c0e, 255);
    }

    private static void whip(Graphics2D g, int x, int y, int len, int curl) {
        rect(g, x, y, 2, 2, 0x7a7a80, 255);
        rect(g, x, y + 2, 2, 7, 0x7a4a24, 255);
        rect(g, x, y + 4, 2, 1, 0x1a0e06, 255); rect(g, x, y + 7, 2, 1, 0x1a0e06, 255);
        int px = x;
        for (int i = 0; i < len; i++) {
            px = x + (int) (Math.sin(i * 0.28) * curl);
            rect(g, px, y + 9 + i, 1, 1, i < len / 2 ? 0x6a4430 : 0x553624, 255);
            if (i % 3 == 0) rect(g, px, y + 9 + i, 1, 1, 0x9a6a44, 160);
        }
        rect(g, px - 1, y + 9 + len, 3, 1, 0x120a04, 255);
    }

    private static void cane(Graphics2D g, int x, int y, int len) {
        rect(g, x - 2, y, 3, 1, 0x8a6a3a, 255);
        rect(g, x, y + 1, 1, len, 0x8a6a3a, 255);
        rect(g, x, y + 1, 1, len, 0xffffff, 18);
    }

    private static void paddle(Graphics2D g, int x, int y) {
        rect(g, x, y, 2, 7, 0x4a3018, 255);
        rect(g, x - 2, y + 7, 6, 10, 0x5a3c20, 255);
        rect(g, x - 2, y + 7, 1, 10, 0x8a6a40, 120);
        rect(g, x, y + 11, 2, 2, 0x1a1008, 255);
    }

    private static void cuff(Graphics2D g, int x, int y) {
        rect(g, x - 4, y + 1, 9, 2, 0x5a5a62, 255);
        rect(g, x - 4, y + 1, 9, 1, 0x8a8a92, 150);
        g.setColor(c(0x4a4a50, 255)); g.drawOval(x - 4, y + 3, 8, 6);
    }

    private static void stocks(Graphics2D g, int x, int fy) {
        rect(g, x, fy - 28, 4, 28, 0x2a1c0e, 255); rect(g, x + 36, fy - 28, 4, 28, 0x2a1c0e, 255);
        rect(g, x - 2, fy - 14, 44, 4, 0x3a2814, 255);
        rect(g, x - 2, fy - 19, 44, 4, 0x3a2814, 255);
        rect(g, x - 2, fy - 19, 44, 1, 0x6a5030, 255);
        rect(g, x + 10, fy - 16, 6, 3, 0x0c0804, 255); rect(g, x + 24, fy - 16, 6, 3, 0x0c0804, 255);
        rect(g, x + 39, fy - 19, 3, 9, 0x4a4a50, 255);
    }

    private static void bench(Graphics2D g, int x, int fy) {
        rect(g, x, fy - 10, 3, 10, 0x2a1c0e, 255); rect(g, x + 30, fy - 10, 3, 10, 0x2a1c0e, 255);
        rect(g, x - 2, fy - 13, 38, 4, 0x3a2814, 255);
        rect(g, x - 2, fy - 13, 38, 1, 0x6a5030, 255);
        rect(g, x + 6, fy - 14, 2, 6, 0x1a0e06, 255); rect(g, x + 24, fy - 14, 2, 6, 0x1a0e06, 255);
        rect(g, x + 6, fy - 11, 2, 1, 0x8a8a92, 255); rect(g, x + 24, fy - 11, 2, 1, 0x8a8a92, 255);
    }

    private static void bucket(Graphics2D g, int x, int fy) {
        rect(g, x, fy - 8, 8, 8, 0x3a3c42, 255);
        rect(g, x - 1, fy - 8, 10, 1, 0x7a7c84, 255);
        rect(g, x + 1, fy - 7, 6, 1, 0x0c0e10, 255);
        rect(g, x, fy - 8, 1, 8, 0xffffff, 20);
    }

    private static void chair(Graphics2D g, int x, int fy) {
        rect(g, x, fy - 26, 2, 16, 0x1a1610, 255);
        rect(g, x - 2, fy - 12, 10, 2, 0x1a1610, 255);
        rect(g, x, fy - 10, 2, 10, 0x120f0a, 255); rect(g, x + 6, fy - 10, 2, 10, 0x120f0a, 255);
    }

    private static void desk(Graphics2D g, int x, int fy, int w) {
        rect(g, x, fy - 14, w, 3, 0x4a3820, 255);
        rect(g, x, fy - 14, w, 1, 0x7a6038, 255);
        rect(g, x + 2, fy - 11, w - 4, 11, 0x2c2012, 255);
        rect(g, x + 2, fy - 11, w - 4, 1, 0x000000, 80);
        rect(g, x + 4, fy - 9, 14, 7, 0x24190e, 255); rect(g, x + 10, fy - 6, 3, 1, 0x8a7a4a, 255);
        rect(g, x + w - 18, fy - 9, 14, 7, 0x24190e, 255); rect(g, x + w - 12, fy - 6, 3, 1, 0x8a7a4a, 255);
        rect(g, x + 6, fy - 17, 10, 3, 0xb8b4a4, 255); rect(g, x + 6, fy - 17, 10, 1, 0xe0dccc, 255);
        rect(g, x + w - 22, fy - 17, 12, 3, 0x5a1a1a, 255);
        rect(g, x + w - 8, fy - 24, 1, 10, 0x1a1a1a, 255);
        rect(g, x + w - 11, fy - 26, 7, 3, 0x3a5a3a, 255);
    }

    private static void suitcase(Graphics2D g, int x, int y, int glow) {
        g.setPaint(new RadialGradientPaint(new Point2D.Float(x + 11, y - 8), 24f,
                new float[]{0f, 1f}, new Color[]{c(glow, 60), c(glow, 0)}));
        g.fillRect(x - 14, y - 32, 50, 50);
        rect(g, x + 1, y - 17, 20, 11, 0x3a2210, 255);
        rect(g, x + 3, y - 15, 16, 8, 0x1a0a08, 255);
        rect(g, x, y - 6, 22, 6, 0x5a3a1c, 255);
        rect(g, x, y - 6, 22, 1, 0x8a6a3c, 255);
        for (int i = 0; i < 4; i++) {
            rect(g, x + 2 + i * 5, y - 10, 4, 5, 0x4a8a4a, 255);
            rect(g, x + 2 + i * 5, y - 8, 4, 1, 0xd8d0a0, 255);
        }
        rect(g, x + 9, y - 4, 4, 2, 0xc0a050, 255);
        sparkle(g, x + 4, y - 12, glow); sparkle(g, x + 17, y - 11, glow);
    }

    private static void dress(Graphics2D g, int x, int y, int rgb) {
        rect(g, x - 3, y, 7, 1, 0x6a6a70, 255);
        rect(g, x - 3, y + 1, 1, 4, rgb, 255); rect(g, x + 3, y + 1, 1, 4, rgb, 255);
        rect(g, x - 4, y + 4, 9, 7, rgb, 255);
        rect(g, x - 4, y + 4, 9, 1, mix(rgb, 0xffffff, 0.25), 255);
        g.setColor(c(rgb, 255));
        g.fill(new Polygon(new int[]{x - 4, x + 5, x + 9, x - 8}, new int[]{y + 11, y + 11, y + 23, y + 23}, 4));
        rect(g, x - 1, y + 11, 1, 12, 0x000000, 40);
        for (int i = 0; i < 17; i += 2) rect(g, x - 8 + i, y + 23, 1, 1, rgb, 255);
    }

    private static void corset(Graphics2D g, int x, int y) {
        rect(g, x - 3, y, 7, 1, 0x6a6a70, 255);
        rect(g, x - 5, y + 1, 11, 18, 0x1a1014, 255);
        rect(g, x - 5, y + 1, 1, 18, 0x3a2030, 255);
        rect(g, x - 5, y + 1, 3, 2, 0xd04890, 255); rect(g, x + 3, y + 1, 3, 2, 0xd04890, 255);
        for (int i = 0; i < 5; i++) rect(g, x - 1, y + 3 + i * 3, 2, 1, 0xd04890, 255);
    }

    private static void stockings(Graphics2D g, int x, int y) {
        rect(g, x - 5, y, 11, 1, 0x6a6a70, 255);
        for (int leg = 0; leg < 2; leg++)
            for (int yy = 0; yy < 22; yy++)
                for (int xx = 0; xx < 4; xx++) {
                    int px = x - 5 + leg * 6 + xx;
                    if ((xx + yy) % 2 == 0) rect(g, px, y + 1 + yy, 1, 1, 0x1a1014, 255);
                    else rect(g, px, y + 1 + yy, 1, 1, 0x6a4a5a, 150);
                }
    }

    private static void boa(Graphics2D g, int x, int y, Random r) {
        rect(g, x - 3, y, 7, 1, 0x6a6a70, 255);
        for (int i = 0; i < 26; i++) rect(g, x - 3 + r.nextInt(7), y + 1 + i, 2 + r.nextInt(2), 1, i % 3 == 0 ? 0xe070a0 : 0xc05a8a, 255);
    }

    private static void heel(Graphics2D g, int x, int by) {
        rect(g, x, by - 3, 3, 2, 0x8a1030, 255);
        rect(g, x + 2, by - 4, 5, 1, 0x8a1030, 255);
        rect(g, x, by - 1, 7, 1, 0x1a0a10, 255);
        rect(g, x + 6, by - 6, 2, 6, 0x8a1030, 255);
        rect(g, x, by - 3, 3, 1, 0xc04060, 255);
    }

    private static void banner(Graphics2D g, int x, int y, int w, int h, int rgb) {
        rect(g, x - 2, y - 2, w + 4, 2, 0x6a5a3a, 255);
        rect(g, x, y, w, h, rgb, 255);
        rect(g, x + 2, y + 2, w - 4, h - 8, mix(rgb, 0xd8b040, 0.25), 90);
        for (int i = 0; i < w; i += 4) rect(g, x + i, y + h, 2, 3, rgb, 255);
        rect(g, x + w / 2 - 1, y + h / 2 - 3, 3, 6, 0xc0a050, 200);
    }

    private static void pot(Graphics2D g, int x, int y, int w) {
        rect(g, x, y, 1, 2, 0x6a6a70, 255);
        rect(g, x - w / 2, y + 2, w, w * 4 / 5, 0x2a2c30, 255);
        rect(g, x - w / 2, y + 2, w, 1, 0x6a6c74, 255);
        rect(g, x - w / 2 - 2, y + 4, 2, 1, 0x2a2c30, 255);
    }

    private static void bowl(Graphics2D g, int x, int y) {
        g.setColor(c(0x6a6a70, 255)); g.fillOval(x - 5, y - 4, 10, 5);
        g.setColor(c(0x4a3a2a, 255)); g.fillOval(x - 4, y - 4, 8, 2);
        rect(g, x + 4, y - 5, 4, 1, 0x8a8a92, 255);
    }

    private static void stool(Graphics2D g, int x, int fy) {
        rect(g, x - 2, fy - 9, 9, 2, 0x3a2a18, 255);
        rect(g, x - 1, fy - 7, 2, 7, 0x2a1c0e, 255); rect(g, x + 4, fy - 7, 2, 7, 0x2a1c0e, 255);
    }

    private static void fish(Graphics2D g, int x, int y) {
        g.setColor(c(0x8a9aa8, 255)); g.fillOval(x, y, 14, 6);
        g.setColor(c(0xc0ccd8, 255)); g.fillOval(x + 2, y + 3, 10, 2);
        g.fill(new Polygon(new int[]{x + 13, x + 18, x + 18}, new int[]{y + 3, y, y + 6}, 3));
        rect(g, x + 4, y - 1, 4, 1, 0x6a7a88, 255);
        rect(g, x + 3, y + 2, 1, 1, 0x000000, 255);
        rect(g, x + 6, y + 1, 1, 3, 0x6a7a88, 255);
    }

    private static void garbageHeap(Graphics2D g, int cx, int by, int w, int h, Random r) {
        int[] pal = {0x16161a, 0x2a2a22, 0x3a3326, 0x26331e, 0x4a4234, 0x1e1a14};
        for (int row = 0; row < h; row++) {
            double f = 1.0 - (double) row / h;
            int rw = (int) (w * Math.sqrt(f)) + 4;
            for (int x = -rw / 2; x < rw / 2; x += 2)
                rect(g, cx + x, by - row, 2, 1, shade(pal[r.nextInt(pal.length)], 0.8 + r.nextDouble() * 0.4), 255);
        }
        for (int i = 0; i < 4; i++) {
            int bx = cx - w / 3 + r.nextInt(Math.max(1, w * 2 / 3)), byy = by - h / 2 - r.nextInt(Math.max(1, h / 3));
            g.setColor(c(0x101014, 255)); g.fillOval(bx, byy, 8, 6);
            rect(g, bx + 2, byy + 1, 2, 1, 0x5a5a62, 90);
            rect(g, bx + 3, byy - 1, 2, 1, 0x101014, 255);
        }
        for (int i = 0; i < 6; i++) rect(g, cx - w / 2 + r.nextInt(w), by - r.nextInt(h), 2, 1, 0xb8b09a, 255);
    }

    private static void smallMirror(Graphics2D g, int x, int y, int w, int h, int glow) {
        rect(g, x - 2, y - 2, w + 4, h + 4, 0x3a2e1a, 255);
        g.setPaint(new GradientPaint(x, y, c(mix(glow, 0x000000, 0.45), 255), x, y + h, c(mix(glow, 0x000000, 0.8), 255)));
        g.fillRect(x, y, w, h);
        g.setColor(c(0xe8eaff, 130));
        g.drawLine(x + w / 2, y + h / 3, x + 2, y); g.drawLine(x + w / 2, y + h / 3, x + w, y + 6);
        g.drawLine(x + w / 2, y + h / 3, x + w / 2 - 3, y + h);
    }

    private static void bearTrap(Graphics2D g, int x, int y) {
        rect(g, x, y + 3, 12, 2, 0x4a4a50, 255);
        for (int i = 0; i < 5; i++) {
            rect(g, x + 1 + i * 2, y, 1, 3, 0x9a9aa2, 255);
            rect(g, x + 1 + i * 2, y + 5, 1, 2, 0x9a9aa2, 255);
        }
        rect(g, x + 5, y + 3, 3, 2, 0x6a2a2a, 255);
        rect(g, x + 12, y + 4, 6, 1, 0x4a4a50, 255);
    }

    // ---------- игрушки ----------
    private static void toy(Graphics2D g, int kind, int x, int by, double k, Random r) {
        switch (kind % 5) {
            case 0: teddy(g, x, by, k, r); break;
            case 1: doll(g, x, by, k, r); break;
            case 2: soldier(g, x, by, k); break;
            case 3: block(g, x, by, (int) (7 * k), 0x7a3a2a); break;
            default: ball(g, x, by, (int) (4 * k) + 2); break;
        }
    }

    private static void rotToy(Graphics2D g, double ang, int kind, int x, int by, double k, Random r) {
        java.awt.geom.AffineTransform old = g.getTransform();
        g.rotate(ang, x, by - 6);
        toy(g, kind, x, by, k, r);
        g.setTransform(old);
    }

    /** Побитый плюшевый мишка: рваный бок, торчит набивка, один глаз-пуговица, заплатка. */
    private static void teddy(Graphics2D g, int x, int by, double k, Random r) {
        int bw = (int) (12 * k), bh = (int) (11 * k), hr = (int) (5 * k);
        int bx = x - bw / 2, top = by - bh;
        int fur = 0x6a4a2a, dark = 0x3e2a16, light = 0x8a6a42;
        g.setColor(c(dark, 255));
        g.fillOval(bx - 1, by - (int) (4 * k), (int) (5 * k), (int) (4 * k));
        g.fillOval(bx + bw - (int) (4 * k), by - (int) (4 * k), (int) (5 * k), (int) (4 * k));
        g.setColor(c(fur, 255)); g.fillOval(bx, top, bw, bh);
        g.setColor(c(light, 120)); g.fillOval(bx + 1, top + 1, bw / 2, bh / 3);
        g.setColor(c(fur, 255));
        g.fillOval(bx - (int) (3 * k), top + (int) (2 * k), (int) (4 * k), (int) (6 * k));
        g.fillOval(bx + bw - 1, top + (int) (3 * k), (int) (3 * k), (int) (3 * k));
        for (int i = 0; i < 4; i++) rect(g, bx + bw + (int) k + r.nextInt(3), top + (int) (5 * k) + r.nextInt(3), 1, 1, 0xc8c0aa, 255);
        rect(g, x - 2, top + bh / 2, (int) (5 * k), 1, 0x1a0e06, 255);
        for (int i = 0; i < 3; i++) rect(g, x - 2 + i * 2, top + bh / 2 - 1, 1, 3, 0x1a1a1a, 255);
        for (int j = 0; j < 5; j++) rect(g, x - 1 + r.nextInt(4), top + bh / 2 + 1 + r.nextInt(3), 1, 1, 0xc8c0aa, 255);
        rect(g, bx + (int) (2 * k), top + (int) (7 * k), (int) (3 * k), (int) (3 * k), 0x4a5a7a, 255);
        int cy = top - (int) k;
        g.setColor(c(fur, 255));
        g.fillOval(x - hr, cy - hr, hr * 2, hr * 2);
        g.fillOval(x - hr, cy - hr - (int) k, (int) (3 * k), (int) (3 * k));
        g.fillOval(x + hr - (int) (3 * k), cy - hr - (int) k, (int) (3 * k), (int) (3 * k));
        g.setColor(c(0x9a7a52, 255)); g.fillOval(x - (int) (2 * k), cy + (int) k, (int) (4 * k), (int) (3 * k));
        rect(g, x - 1, cy + (int) k, 2, 1, 0x120a06, 255);
        rect(g, x - (int) (3 * k), cy - (int) k, 2, 2, 0x20202a, 255);
        rect(g, x + (int) (2 * k), cy - (int) k, 1, 1, 0x000000, 255);
        rect(g, x + (int) (3 * k), cy, 1, (int) (3 * k), 0x2a2a2a, 255);
        g.setColor(c(0x000000, 45)); g.fillOval(bx + bw / 3, top + bh / 3, bw / 2, bh / 2);
    }

    /** Побитая кукла: треснувшая голова, одного глаза нет, рваное платье, отлетевшая рука. */
    private static void doll(Graphics2D g, int x, int by, double k, Random r) {
        int hr = (int) (4 * k), dw = (int) (12 * k), dh = (int) (11 * k), top = by - dh;
        for (int yy = 0; yy < dh; yy++) {
            int half = (int) (2 * k) + yy * (dw / 2 - (int) (2 * k)) / dh;
            rect(g, x - half, top + yy, half * 2, 1, shade(0x7a5a6a, 0.8 + 0.3 * Math.sin(yy * 0.7)), 255);
        }
        for (int i = 0; i < dw; i += 3) rect(g, x - dw / 2 + i, by - 2, 1, 2, 0x2a1a22, 255);
        rect(g, x - 1, top - 2, 3, 3, 0xb8a890, 255);
        int hy = top - 2 - 2 * hr;
        g.setColor(c(0xb8a890, 255)); g.fillOval(x - hr, hy, hr * 2, hr * 2);
        g.setColor(c(0xffffff, 40)); g.fillOval(x - hr + 1, hy + 1, hr, hr);
        g.setColor(c(0x2a1a10, 255)); g.fillArc(x - hr - 1, hy - 1, hr * 2 + 2, hr + 2, 0, 180);
        for (int i = 0; i < 5; i++) rect(g, x - hr - 1 + r.nextInt(hr * 2 + 2), hy + hr / 2 + r.nextInt(hr + 2), 1, 2 + r.nextInt(3), 0x2a1a10, 255);
        g.setColor(c(0x20181a, 230)); g.drawLine(x + 1, hy, x - 1, hy + hr + 1);
        rect(g, x - hr / 2 - 1, hy + hr, 1, 1, 0x000000, 255);
        rect(g, x + hr / 2, hy + hr, 2, 2, 0x000000, 255);
        rect(g, x - hr + 1, hy + hr + 2, 2, 1, 0x9a4a4a, 150);
        rect(g, x + dw / 2 + 2, by - 2, (int) (5 * k), 2, 0xb8a890, 255);
    }

    private static void soldier(Graphics2D g, int x, int by, double k) {
        int s = Math.max(1, (int) k);
        rect(g, x - 2 * s, by - 5 * s, 2 * s, 5 * s, 0x1a1a24, 255); rect(g, x + s, by - 5 * s, 2 * s, 5 * s, 0x1a1a24, 255);
        rect(g, x - 2 * s, by - 11 * s, 5 * s, 6 * s, 0x2a3a7a, 255);
        rect(g, x - s, by - 14 * s, 3 * s, 3 * s, 0xb89a7a, 255);
        rect(g, x - s, by - 16 * s, 3 * s, 2 * s, 0x14141a, 255);
        rect(g, x + 3 * s, by - 14 * s, 1, 9 * s, 0x4a4a50, 255);
        rect(g, x - 2 * s, by - 11 * s, s, 2 * s, 0x1a1a1a, 150);
    }

    private static void block(Graphics2D g, int x, int by, int sz, int rgb) {
        rect(g, x - sz / 2, by - sz, sz, sz, rgb, 255);
        rect(g, x - sz / 2, by - sz, sz, 1, mix(rgb, 0xffffff, 0.3), 255);
        rect(g, x - sz / 2, by - 1, sz, 1, 0x000000, 80);
        rect(g, x - 1, by - sz / 2 - 1, 2, 3, 0xd8d0b0, 200);
    }

    private static void ball(Graphics2D g, int x, int by, int d) {
        g.setColor(c(0x7a2a2a, 255)); g.fillOval(x - d / 2, by - d, d, d);
        rect(g, x - d / 2, by - d / 2 - 1, d, 1, 0xc8c0aa, 255);
        rect(g, x - d / 4, by - d + 1, 1, 1, 0xffffff, 120);
    }

    // ------------------------------------------------------------------
    //  УТИЛИТЫ
    // ------------------------------------------------------------------

    private static Color c(int rgb, int a) {
        return new Color((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, Math.max(0, Math.min(255, a)));
    }

    private static int clamp(int v) { return Math.max(0, Math.min(255, v)); }

    private static int shade(int rgb, double f) {
        return (clamp((int) (((rgb >> 16) & 255) * f)) << 16)
                | (clamp((int) (((rgb >> 8) & 255) * f)) << 8)
                |  clamp((int) ((rgb & 255) * f));
    }

    private static int mix(int a, int b, double t) {
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return (clamp(r) << 16) | (clamp(g) << 8) | clamp(bl);
    }

    private static void rect(Graphics2D g, int x, int y, int w, int h, int rgb, int a) {
        if (w <= 0 || h <= 0) return;
        g.setColor(c(rgb, a));
        g.fillRect(x, y, w, h);
    }

    // ------------------------------------------------------------------
    //  Сохранить превью всех фонов:  java PixelBackground [папка]
    // ------------------------------------------------------------------
    public static void main(String[] args) throws Exception {
        File dir = new File(args.length > 0 ? args[0] : "preview");
        dir.mkdirs();
        for (Sin s : Sin.values()) {
            ImageIO.write(render(s, 1280, 720), "png", new File(dir, "bg_" + s.name().toLowerCase() + ".png"));
            System.out.println("saved " + s.title);
            for (int room = 1; room <= roomCount(s); room++) {
                ImageIO.write(renderRoom(s, room, 1280, 360), "png",
                        new File(dir, "room_" + s.name().toLowerCase() + "_" + room + ".png"));
                System.out.println("  комната " + room + ": " + roomTitle(s, room));
            }
        }
    }
}