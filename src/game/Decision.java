package game;

import java.awt.*;

// Выбор в комнате греха. У каждого выбора два варианта: E и L.
// Один вариант «добрый» (идёт в копилку Рая), другой «плохой» (идёт в копилку Ада).
public class Decision {
    public enum Kind { TOTEM, CHEST, MONEY, GIRL, SHADOW, FISH, BED, ALTAR, RAG, GENIE }

    // label — подпись у кнопки, good — добрый ли поступок, result — что написать после выбора
    public record Option(String label, boolean good, String result) {}

    private static final int T = Room.TILE;
    private static final Color SKIN = new Color(236, 196, 174);

    public final Kind kind;
    public final int col, row;
    public final Option optE, optL;      // E — первый вариант, L — второй (удар мечом)
    public boolean decided;
    public Option picked;
    public boolean showCat = true;       // для рыбы: рисовать ли кошку рядом (false, если кошка уже сидит у Эльзы на плечах)

    private final Totem totem;           // для тотема используем готовый класс
    private final Cat cat;               // для кровати — кот, который лежит на ней
    private int age;                     // сколько кадров прошло после выбора

    public Decision(Kind kind, int col, int row, Option optE, Option optL) {
        this.kind = kind;
        this.col = col;
        this.row = row;
        this.optE = optE;
        this.optL = optL;
        totem = kind == Kind.TOTEM ? new Totem(col, row) : null;
        cat = kind == Kind.BED ? new Cat(col, row)
                : kind == Kind.FISH ? new Cat(col + 1, row) : null;
    }

    public int centerX() { return col * T + T / 2; }
    public int centerY() { return row * T + T / 2; }

    public void pick(Option o) {
        decided = true;
        picked = o;
        age = 0;
        if (totem != null && !o.good()) {        // плохой выбор: тотем разбит
            totem.hp = 0;
            totem.broken = true;
        }
    }

    // ================= ОТРИСОВКА =================
    public void draw(Graphics2D g, int tick) {
        if (decided) age++;
        int x = col * T, y = row * T;
        boolean good = decided && picked.good();
        boolean bad = decided && !picked.good();

        // мягкое свечение, пока выбор не сделан
        if (!decided) {
            int pulse = 30 + (int) (Math.sin(tick * 0.08) * 12);
            g.setColor(new Color(255, 255, 255, pulse));
            g.fillOval(x - 8, y - 4, T + 16, T + 8);
        }

        switch (kind) {
            case TOTEM -> {
                totem.draw(g, tick);
                if (good) {
                    g.setColor(new Color(120, 230, 150, 60));
                    g.fillOval(x - 4, y - 8, T + 8, T + 8);
                }
            }
            case CHEST -> drawChest(g, x, y, tick, good, bad);
            case MONEY -> drawMoney(g, x, y, tick, good, bad);
            case GIRL -> {
                float a = bad ? Math.max(0f, 1f - age / 90f) : 1f;      // плохой выбор: силуэт исчезает
                withAlpha(g, a, () -> drawGirl(g, x, y));
                if (good) drawHeart(g, x + 18, y - 8 - (int) (Math.sin(tick * 0.1) * 3));
            }
            case SHADOW -> {
                float a = decided ? Math.max(0f, 1f - age / 80f) : 1f;  // тень уходит
                int shift = decided ? Math.min(age, 80) / 2 : 0;
                withAlpha(g, a, () -> drawShadow(g, x + shift, y, tick));
            }
            case FISH -> {
                drawFish(g, x, y, tick);
                if (good) drawHeart(g, x + 30, y - 8 - (int) (Math.sin(tick * 0.1) * 3));
            }
            case RAG -> {
                drawRagToy(g, x, y, tick, good, bad);
                if (good) drawHeart(g, x + 18, y - 6 - (int) (Math.sin(tick * 0.1) * 3));
            }
            case GENIE -> {
                float a = decided ? Math.max(0f, 1f - age / 80f) : 1f;  // мираж рассеивается
                withAlpha(g, a, () -> drawGenie(g, x, y, tick));
            }
            case BED -> drawBed(g, x, y, tick, good, bad);
            case ALTAR -> drawAltar(g, x, y, tick, good, bad);
        }
    }

    private static void withAlpha(Graphics2D g, float a, Runnable r) {
        if (a <= 0f) return;
        Composite old = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.min(1f, a)));
        r.run();
        g.setComposite(old);
    }

    private void drawShadowOval(Graphics2D g, int x, int y) {
        g.setColor(new Color(0, 0, 0, 70));
        g.fillOval(x + 6, y + 40, 36, 8);
    }

    // сундук: выбор «пожертвовать» открывает его светом, «украсть» оставляет пустым
    private void drawChest(Graphics2D g, int x, int y, int tick, boolean good, boolean bad) {
        drawShadowOval(g, x, y);
        g.setColor(new Color(112, 70, 36));
        g.fillRect(x + 6, y + 24, 36, 20);
        g.setColor(new Color(150, 98, 52));
        g.fillRect(x + 6, y + 24, 36, 5);
        g.setColor(new Color(240, 200, 60));
        g.fillRect(x + 6, y + 32, 36, 3);
        g.fillRect(x + 21, y + 28, 6, 8);
        if (!decided) {                                            // крышка закрыта, сверху блестит золото
            g.setColor(new Color(130, 84, 44));
            g.fillRect(x + 6, y + 14, 36, 11);
            g.setColor(new Color(255, 225, 90));
            g.fillOval(x + 10, y + 8, 10, 6);
            g.fillOval(x + 24, y + 9, 12, 6);
            if ((tick / 10) % 2 == 0) g.fillRect(x + 31, y + 4, 2, 2);
        } else {                                                   // крышка откинута
            g.setColor(new Color(130, 84, 44));
            g.fillPolygon(new int[]{x + 6, x + 42, x + 38, x + 10}, new int[]{y + 24, y + 24, y + 8, y + 8}, 4);
            if (good) {
                g.setColor(new Color(255, 235, 140, 90));
                g.fillOval(x - 4, y - 6, T + 8, 40);
            } else {
                g.setColor(new Color(20, 14, 10));
                g.fillRect(x + 8, y + 26, 32, 4);
            }
        }
    }

    // деньги на столе в кабинете (сам стол рисует Prop.DESK): «не брать» — купюры остаются и светятся, «взять» — стол пуст
    private void drawMoney(Graphics2D g, int x, int y, int tick, boolean good, boolean bad) {
        if (bad) return;
        g.setColor(new Color(60, 130, 70));                        // пачка купюр
        g.fillRect(x + 2, y + 16, 18, 10);
        g.setColor(new Color(110, 190, 120));
        g.fillRect(x + 2, y + 16, 18, 3);
        g.setColor(new Color(236, 228, 190));
        g.fillRect(x + 9, y + 16, 4, 10);
        g.setColor(new Color(70, 150, 80));                        // вторая пачка сверху
        g.fillRect(x + 6, y + 9, 18, 8);
        g.setColor(new Color(120, 200, 130));
        g.fillRect(x + 6, y + 9, 18, 3);
        g.setColor(new Color(236, 228, 190));
        g.fillRect(x + 13, y + 9, 4, 8);
        g.setColor(new Color(250, 215, 80));                       // монеты
        g.fillOval(x + 26, y + 20, 10, 6);
        g.fillOval(x + 30, y + 17, 10, 6);
        g.setColor(new Color(150, 110, 60));                       // кошелёк
        g.fillOval(x + 36, y + 14, 12, 12);
        g.setColor(new Color(110, 78, 40));
        g.fillRect(x + 39, y + 12, 6, 3);
        if (good) {
            g.setColor(new Color(255, 235, 140, 70));
            g.fillOval(x - 4, y - 6, T + 8, 36);
        } else if ((tick / 14) % 2 == 0) {
            g.setColor(Color.WHITE);
            g.fillRect(x + 31, y + 14, 2, 2);
        }
    }

    private void drawGirl(Graphics2D g, int x, int y) {
        drawShadowOval(g, x, y);
        g.setColor(new Color(240, 130, 180));                      // платье
        g.fillPolygon(new int[]{x + 17, x + 31, x + 38, x + 10}, new int[]{y + 24, y + 24, y + 42, y + 42}, 4);
        g.setColor(SKIN);                                          // ножки
        g.fillRect(x + 17, y + 42, 4, 5);
        g.fillRect(x + 27, y + 42, 4, 5);
        g.setColor(new Color(60, 36, 24));                         // волосы
        g.fillRect(x + 13, y + 5, 22, 14);
        g.fillRect(x + 11, y + 10, 4, 18);
        g.fillRect(x + 33, y + 10, 4, 18);
        g.setColor(SKIN);                                          // лицо
        g.fillRect(x + 16, y + 10, 16, 14);
        g.setColor(Color.BLACK);
        g.fillRect(x + 19, y + 15, 3, 3);
        g.fillRect(x + 27, y + 15, 3, 3);
        g.setColor(new Color(200, 90, 100));
        g.fillRect(x + 22, y + 20, 4, 1);
        g.setColor(SKIN);                                          // ручки
        g.fillRect(x + 12, y + 28, 4, 8);
        g.fillRect(x + 32, y + 28, 4, 8);
    }

    private void drawShadow(Graphics2D g, int x, int y, int tick) {
        drawShadowOval(g, x, y);
        g.setColor(new Color(16, 10, 22));
        g.fillPolygon(new int[]{x + 14, x + 34, x + 40, x + 8}, new int[]{y + 18, y + 18, y + 46, y + 46}, 4);
        g.fillOval(x + 14, y + 2, 20, 22);
        g.setColor(new Color(235, 40, 50));
        int glint = (tick / 20) % 2;
        g.fillRect(x + 19, y + 11 + glint, 3, 3);
        g.fillRect(x + 27, y + 11 + glint, 3, 3);
    }

    // рыба на тарелке посреди мусора: после выбора остаются кости; кошка рядом, пока она не сидит у Эльзы на плечах
    private void drawFish(Graphics2D g, int x, int y, int tick) {
        drawShadowOval(g, x, y);
        g.setColor(new Color(74, 52, 34));                         // ящик-стол
        g.fillRect(x + 4, y + 34, 40, 12);
        g.setColor(new Color(104, 74, 46));
        g.fillRect(x + 2, y + 32, 44, 4);
        g.setColor(new Color(220, 222, 226));                      // тарелка
        g.fillOval(x + 6, y + 28, 36, 8);
        if (!decided) {                                            // целая рыба
            g.setColor(new Color(110, 170, 210));
            g.fillOval(x + 11, y + 18, 26, 12);
            g.fillPolygon(new int[]{x + 36, x + 45, x + 45}, new int[]{y + 24, y + 17, y + 31}, 3);
            g.setColor(new Color(190, 225, 245));
            g.fillRect(x + 18, y + 21, 12, 2);
            g.setColor(Color.BLACK);
            g.fillRect(x + 15, y + 22, 2, 2);
            if ((tick / 12) % 2 == 0) {
                g.setColor(Color.WHITE);
                g.fillRect(x + 28, y + 16, 2, 2);
            }
        } else {                                                   // остались кости
            g.setColor(new Color(236, 232, 214));
            g.fillRect(x + 12, y + 27, 26, 2);
            for (int i = 0; i < 4; i++) g.fillRect(x + 16 + i * 5, y + 23, 1, 9);
            g.fillRect(x + 9, y + 25, 4, 5);
            g.fillPolygon(new int[]{x + 38, x + 44, x + 44}, new int[]{y + 28, y + 24, y + 32}, 3);
        }
        if (showCat && cat != null) cat.draw(g, tick);             // голодная кошка рядом
    }

    // комната Эльзы в том доме: тряпка и одна игрушка. «Взять игрушку» — игрушки нет, «лечь» — Эльза уснула на тряпке
    private void drawRagToy(Graphics2D g, int x, int y, int tick, boolean good, boolean bad) {
        drawShadowOval(g, x, y);
        g.setColor(new Color(50, 60, 70, 140));                    // сырость на полу
        g.fillOval(x - 2, y + 41, 52, 7);
        g.setColor(new Color(100, 92, 84));                        // тряпка
        g.fillPolygon(new int[]{x + 2, x + 8, x + 20, x + 32, x + 38, x + 24, x + 10},
                new int[]{y + 45, y + 38, y + 36, y + 39, y + 45, y + 47, y + 47}, 7);
        g.setColor(new Color(72, 66, 60));
        g.fillRect(x + 10, y + 40, 12, 2);
        if (!decided) {                                            // мягкое свечение у игрушки
            g.setColor(new Color(255, 235, 140, 50));
            g.fillOval(x + 22, y + 16, 28, 34);
        }
        if (!good) {                                               // игрушка: потрёпанный зайчик
            g.setColor(bad ? new Color(150, 130, 120) : new Color(214, 190, 170));
            g.fillOval(x + 28, y + 34, 14, 12);
            g.fillOval(x + 30, y + 24, 12, 12);
            g.fillOval(x + 29, y + 17, 5, 9);
            g.fillOval(x + 38, y + 17, 5, 9);
            g.setColor(Color.BLACK);
            g.fillRect(x + 33, y + 29, 2, 2);
            g.fillRect(x + 37, y + 29, 2, 2);
        }
        if (bad) {                                                 // уснула: плывут «z»
            g.setFont(new Font("Monospaced", Font.BOLD, 16));
            g.setColor(new Color(200, 220, 255, 200));
            for (int i = 0; i < 3; i++) g.drawString("z", x + 8 + i * 6, y - 4 - ((tick / 3 + i * 14) % 40));
        }
    }

    // псевдо-джинн: синий дымчатый призрак с лампой; глаза иногда вспыхивают красным (он лжёт)
    private void drawGenie(Graphics2D g, int x, int y, int tick) {
        int sway = (int) (Math.sin(tick * 0.1) * 3);
        g.setColor(new Color(0, 0, 0, 50));
        g.fillOval(x + 6, y + 42, 36, 6);
        g.setColor(new Color(240, 190, 40));                       // лампа у ног
        g.fillOval(x + 30, y + 41, 14, 6);
        g.fillRect(x + 42, y + 40, 4, 3);
        g.setColor(new Color(70, 140, 230, 200));                  // хвост-дым
        g.fillPolygon(new int[]{x + 14, x + 34, x + 28 + sway, x + 20 + sway}, new int[]{y + 34, y + 34, y + 48, y + 48}, 4);
        g.setColor(new Color(90, 160, 240, 215));                  // торс
        g.fillOval(x + 11, y + 14, 26, 26);
        g.setColor(new Color(60, 110, 200, 230));                  // скрещённые руки
        g.fillRect(x + 8, y + 24, 32, 5);
        g.setColor(new Color(130, 190, 250, 230));                 // голова
        g.fillOval(x + 16, y + 2, 16, 16);
        g.setColor(new Color(150, 60, 170));                       // тюрбан
        g.fillRect(x + 14, y, 20, 7);
        g.setColor(new Color(250, 215, 80));
        g.fillRect(x + 22, y + 1, 4, 4);
        g.setColor((tick / 25) % 7 == 0 ? new Color(235, 40, 50) : new Color(255, 240, 120));
        g.fillRect(x + 19, y + 10, 3, 3);
        g.fillRect(x + 27, y + 10, 3, 3);
    }

    // кровать с котом: «лечь спать» — над ней плывут «z», «растрепать» — подушка и одеяло на полу
    private void drawBed(Graphics2D g, int x, int y, int tick, boolean good, boolean bad) {
        drawShadowOval(g, x, y);
        if (good) {                                                // растрёпана: кот убежал
            g.setColor(new Color(110, 70, 90));
            g.fillRect(x + 2, y + 36, 44, 10);
            g.setColor(new Color(236, 236, 242));
            g.fillPolygon(new int[]{x + 4, x + 18, x + 16, x + 2}, new int[]{y + 36, y + 30, y + 24, y + 30}, 4);
            g.setColor(new Color(90, 120, 190));
            g.fillPolygon(new int[]{x + 22, x + 46, x + 40, x + 26}, new int[]{y + 36, y + 36, y + 42, y + 44}, 4);
            return;
        }
        g.setColor(new Color(110, 70, 90));                        // рама и матрас
        g.fillRect(x + 2, y + 34, 44, 12);
        g.setColor(new Color(200, 170, 200));
        g.fillRect(x + 4, y + 30, 40, 6);
        g.setColor(new Color(236, 236, 242));                      // подушка
        g.fillRect(x + 4, y + 26, 12, 6);
        if (showCat) cat.draw(g, tick);                            // кот на кровати (если кошка уже на плечах у Эльзы — второй не рисуем)
        if (bad) {                                                 // сон
            g.setFont(new Font("Monospaced", Font.BOLD, 16));
            g.setColor(new Color(200, 220, 255, 200));
            for (int i = 0; i < 3; i++) {
                int zy = y - 4 - ((tick / 3 + i * 14) % 40);
                g.drawString("z", x + 26 + i * 6, zy);
            }
        }
    }

    // постамент с добычей: «вернуть всё» — свет и пустой постамент, «оставить в прошлом» — пыль и серые вещи
    private void drawAltar(Graphics2D g, int x, int y, int tick, boolean good, boolean bad) {
        drawShadowOval(g, x, y);
        g.setColor(new Color(110, 112, 128));                      // постамент
        g.fillRect(x + 8, y + 30, 32, 16);
        g.setColor(new Color(150, 152, 170));
        g.fillRect(x + 4, y + 26, 40, 6);
        if (good) {                                                // вещи возвращены: тёплый свет
            g.setColor(new Color(255, 235, 140, 70));
            g.fillOval(x - 2, y - 8, T + 4, 40);
            return;
        }
        boolean dim = bad;
        g.setColor(dim ? new Color(120, 118, 100) : new Color(240, 190, 40));          // корона
        g.fillPolygon(new int[]{x + 10, x + 10, x + 16, x + 22, x + 28, x + 34, x + 34},
                new int[]{y + 26, y + 12, y + 18, y + 8, y + 18, y + 12, y + 26}, 7);
        g.setColor(dim ? new Color(100, 98, 90) : new Color(250, 215, 80));            // монеты
        g.fillOval(x + 30, y + 20, 10, 6);
        g.fillOval(x + 33, y + 17, 10, 6);
        g.setColor(dim ? new Color(110, 110, 120) : new Color(214, 222, 238));         // кинжал
        g.fillRect(x + 36, y + 6, 3, 14);
        if (!dim && (tick / 12) % 2 == 0) {
            g.setColor(Color.WHITE);
            g.fillRect(x + 20, y + 6, 2, 2);
        }
    }

    private void drawHeart(Graphics2D g, int x, int y) {
        g.setColor(new Color(240, 80, 110));
        g.fillRect(x + 1, y, 4, 3);
        g.fillRect(x + 8, y, 4, 3);
        g.fillRect(x, y + 3, 13, 4);
        g.fillRect(x + 2, y + 7, 9, 3);
        g.fillRect(x + 5, y + 10, 3, 2);
    }
}