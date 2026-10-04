package game;

import java.awt.*;
import java.awt.geom.AffineTransform;

// Декорации и персонажи сцен (комнаты с видом сбоку). Стоят на полу — ряд 9 (картины и табличка висят выше).
//  script  — сценка: реплики вида "Кто|Текст". Запускается сама, когда Эльза подходит.
//  mash    — после сценки включается мини-игра «вырвись» (жми E / ПРОБЕЛ).
//  prompt  — подсказка «E — ...» для предметов, с которыми можно взаимодействовать вручную (ванна).
public class Prop {
    public enum Kind {
        PAINTING, WHIPS, PILLORY, TOYS, PLAQUE, WORKER, DESK, CABINET, CLOTHES,
        CHRIS, SEBASTIAN, BUFFET, CANTEEN, TRASH, BATH, RAG, DOLL, DRESS, FAMILY, BED
    }

    private static final int T = Room.TILE;
    private static final Color SKIN = new Color(236, 196, 174);

    public final Kind kind;
    public final int col, row;
    public String text = "";               // для таблички: строки через \n
    public String prompt;                  // подсказка «E — ...» (ручное взаимодействие)
    public String[] script;                // сценка (диалог)
    public boolean mash;                   // после сценки — QTE
    public String endText, winText, loseText;
    public String effect;                  // эффект после сценки: "SICK", "SLEEPY"
    public boolean done;
    private Cat bedCat;                    // кошка на кровати (для Kind.BED)
    private int age;                       // кадров после завершения (для исчезновения)

    public Prop(Kind kind, int col, int row) {
        this.kind = kind;
        this.col = col;
        this.row = row;
    }

    // персонаж, с которым Эльза говорит (он отвечает молчанием или репликами)
    public static Prop talk(Kind kind, int col, int row, String endText, String... script) {
        Prop p = new Prop(kind, col, row);
        p.script = script;
        p.endText = endText;
        return p;
    }

    // персонаж, от которого нужно вырваться: сначала диалог, потом мини-игра
    public static Prop mash(Kind kind, int col, int row, String winText, String loseText, String... script) {
        Prop p = new Prop(kind, col, row);
        p.script = script;
        p.mash = true;
        p.winText = winText;
        p.loseText = loseText;
        return p;
    }

    public static Prop plaque(int col, int row, String text) {
        Prop p = new Prop(Kind.PLAQUE, col, row);
        p.text = text;
        return p;
    }

    public int centerX() {
        return col * T + T / 2;
    }

    public int centerY() {
        return row * T + T / 2;
    }

    // ================= ОТРИСОВКА =================
    public void draw(Graphics2D g, int tick) {
        if (done) age++;
        int x = col * T, y = row * T;

        if (done && kind == Kind.WORKER) x += Math.min(age, 70);          // работница молча уходит прочь
        float a = 1f;
        if (done && (kind == Kind.CHRIS || kind == Kind.SEBASTIAN || kind == Kind.WORKER)) a = Math.max(0f, 1f - age / 60f);   // уходят
        if (a <= 0f) return;
        Composite old = g.getComposite();
        if (a < 1f) g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, a));

        switch (kind) {
            case PAINTING -> drawPainting(g, x, y);
            case WHIPS -> drawWhips(g, x, y);
            case PILLORY -> drawPillory(g, x, y);
            case TOYS -> drawToys(g, x, y);
            case PLAQUE -> drawPlaque(g, x, y);
            case WORKER -> {
                person(g, x + 24, y + 46, new Color(150, 150, 150), new Color(96, 100, 112), new Color(40, 40, 50), true, 1.0, true);
                g.setColor(new Color(224, 224, 218));                              // фартук
                g.fillRect(x + 19, y + 17, 10, 20);
            }
            case DESK -> drawDesk(g, x, y, tick);
            case CABINET -> drawCabinet(g, x, y);
            case CLOTHES -> drawClothes(g, x, y);
            case CHRIS -> {
                person(g, x + 24, y + 46, new Color(30, 24, 28), new Color(52, 52, 68), new Color(30, 30, 40), false, 1.0, false);
                g.setColor(new Color(190, 40, 60));                                // галстук
                g.fillRect(x + 23, y + 14, 3, 12);
            }
            case SEBASTIAN -> {
                person(g, x + 24, y + 46, new Color(112, 72, 40), new Color(52, 76, 120), new Color(40, 40, 52), false, 1.0, false);
                g.setColor(new Color(240, 200, 60));                               // пуговицы ливреи
                g.fillRect(x + 23, y + 16, 3, 3);
                g.fillRect(x + 23, y + 23, 3, 3);
            }
            case BUFFET -> drawBuffet(g, x, y, tick);
            case CANTEEN -> drawCanteen(g, x, y);
            case TRASH -> drawTrash(g, x, y, tick);
            case BATH -> drawBath(g, x, y, tick);
            case RAG -> drawRag(g, x, y);
            case DOLL -> drawDoll(g, x, y);
            case DRESS -> drawDress(g, x, y, tick);
            case FAMILY -> drawFamily(g, x, y, tick);
            case BED -> drawBedWithCat(g, x, y, tick);
        }
        g.setComposite(old);
    }

    // человечек спереди: cx — центр, fy — линия ног, s — масштаб
    private static void person(Graphics2D g, int cx, int fy, Color hair, Color top, Color bottom,
                               boolean dress, double s, boolean bun) {
        AffineTransform old = g.getTransform();
        g.translate(cx, fy);
        g.scale(s, s);
        g.setColor(new Color(0, 0, 0, 70));
        g.fillOval(-15, -4, 30, 8);
        g.setColor(bottom);                                        // ноги
        g.fillRect(-8, -14, 6, 14);
        g.fillRect(2, -14, 6, 14);
        g.setColor(top);                                           // туловище / платье
        if (dress) g.fillPolygon(new int[]{-9, 9, 15, -15}, new int[]{-32, -32, -8, -8}, 4);
        else g.fillRect(-10, -34, 20, 22);
        g.fillRect(-14, -32, 4, 16);                               // руки
        g.fillRect(10, -32, 4, 16);
        g.setColor(SKIN);
        g.fillRect(-14, -17, 4, 4);
        g.fillRect(10, -17, 4, 4);
        g.fillRect(-7, -48, 14, 15);                               // голова
        g.setColor(hair);
        g.fillRect(-8, -50, 16, 7);
        g.fillRect(-8, -50, 3, 15);
        g.fillRect(5, -50, 3, 15);
        if (bun) g.fillOval(-5, -57, 10, 8);
        g.setColor(Color.BLACK);
        g.fillRect(-4, -41, 2, 2);
        g.fillRect(2, -41, 2, 2);
        g.setTransform(old);
    }

    private static void shadow(Graphics2D g, int x, int y, int w) {
        g.setColor(new Color(0, 0, 0, 60));
        g.fillOval(x + (T - w) / 2, y + 42, w, 6);
    }

    // ---------- музей / наказания ----------
    private void drawPainting(Graphics2D g, int x, int y) {
        g.setColor(new Color(0, 0, 0, 50));
        g.fillRect(x + 6, y + 4, 40, 52);
        g.setColor(new Color(196, 154, 52));                       // золочёная рама
        g.fillRect(x + 2, y, 44, 52);
        g.setColor(new Color(120, 90, 30));
        g.fillRect(x + 6, y + 4, 36, 44);
        int v = col % 3;
        g.setColor(v == 0 ? new Color(110, 150, 200) : v == 1 ? new Color(206, 150, 120) : new Color(96, 112, 96));
        g.fillRect(x + 8, y + 6, 32, 40);
        g.setColor(new Color(58, 88, 60));
        g.fillPolygon(new int[]{x + 8, x + 24, x + 40}, new int[]{y + 46, y + 26, y + 46}, 3);
        g.setColor(new Color(250, 232, 150));
        g.fillOval(x + 28, y + 10, 8, 8);
    }

    private void drawWhips(Graphics2D g, int x, int y) {
        shadow(g, x, y, 44);
        g.setColor(new Color(90, 60, 36));                         // стойка
        g.fillRect(x + 4, y + 4, 5, 44);
        g.fillRect(x + 39, y + 4, 5, 44);
        g.fillRect(x + 2, y + 2, 44, 6);
        Stroke old = g.getStroke();
        g.setStroke(new BasicStroke(2f));
        for (int i = 0; i < 3; i++) {                              // плети
            int hx = x + 16 + i * 8;
            g.setColor(new Color(112, 72, 40));
            g.fillRect(hx - 1, y + 8, 4, 10);
            g.setColor(new Color(36, 26, 22));
            g.drawLine(hx + 1, y + 18, hx + 1 + (i % 2 == 0 ? -4 : 4), y + 42);
        }
        g.setStroke(old);
        g.setColor(new Color(150, 154, 168));                      // цепь
        for (int k = 0; k < 5; k++) g.fillRect(x + 11 + (k % 2) * 2, y + 8 + k * 7, 3, 5);
    }

    private void drawPillory(Graphics2D g, int x, int y) {
        shadow(g, x, y, 46);
        g.setColor(new Color(96, 64, 38));
        g.fillRect(x + 6, y + 8, 6, 40);                           // столбы
        g.fillRect(x + 36, y + 8, 6, 40);
        g.fillRect(x + 2, y + 44, 44, 4);
        g.setColor(new Color(130, 90, 52));
        g.fillRect(x + 4, y + 18, 40, 6);                          // доски с отверстиями
        g.fillRect(x + 4, y + 26, 40, 6);
        g.setColor(new Color(24, 16, 12));
        g.fillOval(x + 12, y + 22, 8, 6);
        g.fillOval(x + 28, y + 22, 8, 6);
    }

    // ---------- приют ----------
    private void drawToys(Graphics2D g, int x, int y) {
        shadow(g, x, y, 40);
        int v = col % 3;
        if (v == 0) {                                              // кубики
            g.setColor(new Color(210, 70, 70));
            g.fillRect(x + 6, y + 30, 16, 16);
            g.setColor(new Color(70, 120, 210));
            g.fillRect(x + 24, y + 30, 16, 16);
            g.setColor(new Color(240, 200, 60));
            g.fillRect(x + 15, y + 15, 16, 15);
            g.setColor(new Color(255, 255, 255, 160));
            g.fillRect(x + 10, y + 34, 6, 3);
            g.fillRect(x + 28, y + 34, 6, 3);
        } else if (v == 1) {                                       // мишка
            g.setColor(new Color(150, 100, 60));
            g.fillOval(x + 12, y + 24, 24, 22);
            g.fillOval(x + 14, y + 8, 20, 18);
            g.fillOval(x + 11, y + 6, 8, 8);
            g.fillOval(x + 29, y + 6, 8, 8);
            g.setColor(new Color(214, 170, 120));
            g.fillOval(x + 19, y + 16, 10, 8);
            g.setColor(Color.BLACK);
            g.fillRect(x + 19, y + 13, 2, 2);
            g.fillRect(x + 27, y + 13, 2, 2);
            g.fillRect(x + 23, y + 18, 3, 2);
        } else {                                                   // мячик и юла
            g.setColor(new Color(220, 60, 70));
            g.fillOval(x + 6, y + 26, 20, 20);
            g.setColor(Color.WHITE);
            g.fillRect(x + 8, y + 34, 16, 4);
            g.setColor(new Color(80, 160, 220));
            g.fillPolygon(new int[]{x + 28, x + 42, x + 35}, new int[]{y + 30, y + 30, y + 46}, 3);
            g.setColor(new Color(240, 200, 60));
            g.fillRect(x + 34, y + 24, 2, 7);
        }
    }

    private void drawPlaque(Graphics2D g, int x, int y) {
        if (text.isEmpty()) return;                                // пустая табличка = невидимый запуск сценки
        String[] lines = text.split("\n");
        Font old = g.getFont();
        g.setFont(new Font("Monospaced", Font.BOLD, 14));
        FontMetrics fm = g.getFontMetrics();
        int w = 0;
        for (String s : lines) w = Math.max(w, fm.stringWidth(s));
        w += 28;
        int h = lines.length * 20 + 16;
        int px = x + T / 2 - w / 2, py = y;
        g.setColor(new Color(90, 90, 100));                        // цепи
        g.fillRect(px + 10, py - 22, 3, 22);
        g.fillRect(px + w - 13, py - 22, 3, 22);
        g.setColor(new Color(0, 0, 0, 70));
        g.fillRect(px + 4, py + 4, w, h);
        g.setColor(new Color(110, 76, 40));
        g.fillRect(px, py, w, h);
        g.setColor(new Color(240, 200, 60));
        g.drawRect(px + 3, py + 3, w - 7, h - 7);
        g.setColor(new Color(250, 240, 215));
        for (int i = 0; i < lines.length; i++) g.drawString(lines[i], px + 14, py + 26 + i * 20);
        g.setFont(old);
    }

    // ---------- кабинет ----------
    private void drawDesk(Graphics2D g, int x, int y, int tick) {
        g.setColor(new Color(0, 0, 0, 60));
        g.fillOval(x - 6, y + 42, 60, 6);
        g.setColor(new Color(96, 62, 34));
        g.fillRect(x - 2, y + 30, 5, 18);
        g.fillRect(x + 45, y + 30, 5, 18);
        g.setColor(new Color(130, 86, 46));
        g.fillRect(x - 6, y + 26, 60, 6);
        g.setColor(new Color(170, 120, 70));
        g.fillRect(x - 6, y + 26, 60, 2);
        g.setColor(new Color(236, 232, 220));                      // бумаги
        g.fillRect(x + 2, y + 20, 14, 6);
        g.fillRect(x + 18, y + 22, 10, 4);
        g.setColor(new Color(60, 60, 70));                         // лампа
        g.fillRect(x + 38, y + 22, 10, 4);
        g.fillRect(x + 42, y + 12, 2, 10);
        g.setColor(new Color(250, 214, 110));
        g.fillPolygon(new int[]{x + 36, x + 50, x + 46, x + 40}, new int[]{y + 14, y + 14, y + 8, y + 8}, 4);
    }

    private void drawCabinet(Graphics2D g, int x, int y) {
        shadow(g, x, y, 40);
        g.setColor(new Color(100, 108, 120));
        g.fillRect(x + 6, y + 4, 36, 44);
        g.setColor(new Color(136, 146, 160));
        g.fillRect(x + 6, y + 4, 36, 4);
        g.setColor(new Color(70, 76, 88));
        for (int i = 0; i < 3; i++) {
            g.fillRect(x + 9, y + 12 + i * 12, 30, 2);
            g.fillRect(x + 20, y + 17 + i * 12, 8, 3);
        }
    }

    // ---------- похоть ----------
    private void drawClothes(Graphics2D g, int x, int y) {
        shadow(g, x, y, 44);
        g.setColor(new Color(110, 110, 124));
        g.fillRect(x + 4, y + 4, 3, 44);
        g.fillRect(x + 41, y + 4, 3, 44);
        g.fillRect(x + 2, y + 4, 44, 3);
        Color[] cs = {new Color(190, 40, 70), new Color(30, 24, 40), new Color(220, 120, 170), new Color(120, 40, 120)};
        for (int i = 0; i < 4; i++) {
            int gx = x + 9 + i * 8;
            g.setColor(cs[(i + col) % 4]);
            g.fillPolygon(new int[]{gx, gx + 8, gx + 10, gx - 2}, new int[]{y + 8, y + 8, y + 34, y + 34}, 4);
            g.setColor(new Color(250, 230, 236, 170));
            g.fillRect(gx - 1, y + 31, 10, 3);
        }
    }

    // ---------- чревоугодие ----------
    private void drawBuffet(Graphics2D g, int x, int y, int tick) {
        shadow(g, x, y, 56);
        g.setColor(new Color(70, 52, 40));
        g.fillRect(x + 4, y + 40, 4, 8);
        g.fillRect(x + 40, y + 40, 4, 8);
        g.setColor(new Color(240, 238, 230));                      // скатерть
        g.fillRect(x - 4, y + 30, 56, 12);
        g.setColor(new Color(200, 198, 190));
        for (int i = 0; i < 6; i++) g.fillRect(x - 4 + i * 10, y + 40, 6, 4);
        g.setColor(new Color(236, 214, 120));                      // подсвечник
        g.fillRect(x + 40, y + 16, 3, 14);
        g.setColor(new Color(255, 190, 60));
        g.fillOval(x + 39, y + 10 - (tick / 8) % 2, 5, 7);
    }

    private void drawCanteen(Graphics2D g, int x, int y) {
        shadow(g, x, y, 60);
        g.setColor(new Color(74, 52, 34));
        g.fillRect(x - 6, y + 30, 4, 18);
        g.fillRect(x + 50, y + 30, 4, 18);
        g.setColor(new Color(104, 74, 46));
        g.fillRect(x - 10, y + 28, 68, 6);
        g.setColor(new Color(84, 60, 38));                         // скамья
        g.fillRect(x - 2, y + 40, 52, 4);
        g.setColor(new Color(110, 108, 100));                      // миски
        g.fillOval(x + 2, y + 24, 12, 6);
        g.fillOval(x + 30, y + 24, 12, 6);
    }

    private void drawTrash(Graphics2D g, int x, int y, int tick) {
        shadow(g, x, y, 48);
        g.setColor(new Color(44, 48, 40));
        g.fillPolygon(new int[]{x, x + 10, x + 22, x + 34, x + 48}, new int[]{y + 47, y + 30, y + 20, y + 28, y + 47}, 5);
        g.setColor(new Color(76, 68, 48));
        g.fillRect(x + 8, y + 36, 12, 6);
        g.setColor(new Color(100, 102, 84));
        g.fillRect(x + 26, y + 30, 8, 10);
        g.setColor(new Color(214, 206, 180));                      // кости
        g.fillRect(x + 30, y + 40, 10, 2);
        g.fillRect(x + 14, y + 28, 6, 2);
        g.setColor(Color.BLACK);                                   // муха
        int fx = x + 24 + (int) (Math.cos(tick * 0.2) * 14), fy = y + 12 + (int) (Math.sin(tick * 0.31) * 6);
        g.fillRect(fx, fy, 2, 2);
    }

    // ---------- лень ----------
    private void drawBath(Graphics2D g, int x, int y, int tick) {
        g.setColor(new Color(0, 0, 0, 60));
        g.fillOval(x - 8, y + 42, 64, 6);
        g.setColor(new Color(176, 140, 60));
        g.fillRect(x + 2, y + 38, 5, 10);
        g.fillRect(x + 41, y + 38, 5, 10);
        g.setColor(new Color(226, 224, 214));
        g.fillRect(x - 4, y + 22, 56, 18);
        g.setColor(new Color(244, 242, 234));
        g.fillRect(x - 6, y + 18, 60, 6);
        g.setColor(new Color(130, 176, 196));                      // вода
        g.fillRect(x - 2, y + 22, 52, 5);
        g.setColor(new Color(255, 255, 255, 180));
        for (int i = 0; i < 3; i++) {
            int by = y + 16 - (tick / 4 + i * 7) % 12;
            g.fillOval(x + 8 + i * 14, by, 6, 6);
        }
    }

    private void drawRag(Graphics2D g, int x, int y) {
        g.setColor(new Color(50, 60, 70, 140));                    // сырость
        g.fillOval(x - 2, y + 41, 52, 7);
        g.setColor(new Color(100, 92, 84));
        g.fillPolygon(new int[]{x + 4, x + 12, x + 26, x + 40, x + 46, x + 30, x + 14},
                new int[]{y + 45, y + 36, y + 34, y + 38, y + 45, y + 47, y + 47}, 7);
        g.setColor(new Color(72, 66, 60));
        g.fillRect(x + 14, y + 38, 14, 2);
        g.fillRect(x + 28, y + 42, 10, 2);
        g.setColor(new Color(120, 106, 90));
        g.fillRect(x + 20, y + 36, 6, 3);
    }

    private void drawDoll(Graphics2D g, int x, int y) {
        shadow(g, x, y, 30);
        g.setColor(new Color(140, 96, 70));
        g.fillRect(x + 16, y + 30, 16, 14);
        g.fillRect(x + 10, y + 32, 6, 4);
        g.fillRect(x + 32, y + 32, 6, 4);
        g.setColor(new Color(214, 190, 150));
        g.fillOval(x + 14, y + 16, 20, 18);
        g.setColor(new Color(170, 80, 50));
        g.fillRect(x + 16, y + 14, 16, 4);
        g.setColor(Color.BLACK);
        g.fillRect(x + 19, y + 23, 3, 3);
        g.fillRect(x + 27, y + 23, 3, 3);
        g.fillRect(x + 21, y + 29, 6, 1);
    }

    // кровать, на которой свернулась кошка
    private void drawBedWithCat(Graphics2D g, int x, int y, int tick) {
        g.setColor(new Color(0, 0, 0, 70));
        g.fillOval(x + 6, y + 40, 36, 8);
        g.setColor(new Color(110, 70, 90));                        // рама и матрас
        g.fillRect(x + 2, y + 34, 44, 12);
        g.setColor(new Color(200, 170, 200));
        g.fillRect(x + 4, y + 30, 40, 6);
        g.setColor(new Color(236, 236, 242));                      // подушка
        g.fillRect(x + 4, y + 26, 12, 6);
        if (bedCat == null) bedCat = new Cat(col, row);
        bedCat.draw(g, tick);
    }

    // ---------- гордыня ----------
    private void drawDress(Graphics2D g, int x, int y, int tick) {
        Color[] pal = {new Color(130, 44, 76), new Color(44, 76, 140), new Color(34, 106, 86),
                new Color(156, 116, 44), new Color(116, 64, 146)};
        Color c = pal[(col / 2) % pal.length];
        g.setColor(new Color(0, 0, 0, 50));
        g.fillOval(x + 2, y + 42, 44, 6);
        g.setColor(new Color(96, 74, 52));                         // манекен
        g.fillRect(x + 23, y - 12, 3, 58);
        g.fillRect(x + 12, y + 44, 24, 4);
        g.setColor(new Color(200, 170, 130));
        g.fillOval(x + 19, y - 28, 10, 14);
        g.setColor(c);                                             // корсаж и юбка
        g.fillPolygon(new int[]{x + 17, x + 31, x + 29, x + 19}, new int[]{y - 12, y - 12, y + 8, y + 8}, 4);
        int sw = (int) (Math.sin(tick * 0.04 + col) * 2);
        g.fillPolygon(new int[]{x + 19, x + 29, x + 44 + sw, x + 4 + sw}, new int[]{y + 8, y + 8, y + 44, y + 44}, 4);
        g.setColor(Theme.mix(c, Color.WHITE, 0.35));
        g.fillRect(x + 22, y - 10, 4, 18);
        g.fillRect(x + 6 + sw, y + 40, 36, 4);
        g.setColor(new Color(240, 200, 60));
        g.fillRect(x + 18, y + 6, 12, 3);
    }

    // воспоминание: отец, мать и девочка — полупрозрачные, как призраки прошлого
    private void drawFamily(Graphics2D g, int x, int y, int tick) {
        float a = (float) (0.55 + 0.12 * Math.sin(tick * 0.05 + col));
        Composite old = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, a));
        g.setColor(new Color(255, 236, 170, 80));
        g.fillOval(x - 30, y - 14, 130, 70);
        person(g, x - 6, y + 46, new Color(70, 50, 36), new Color(70, 80, 110), new Color(40, 40, 52), false, 1.05, false);
        person(g, x + 30, y + 46, new Color(110, 70, 44), new Color(160, 80, 100), new Color(110, 60, 70), true, 1.0, false);
        person(g, x + 62, y + 46, new Color(245, 202, 3), new Color(240, 200, 220), new Color(200, 160, 180), true, 0.7, false);
        g.setComposite(old);
    }
}


// ==================== ВАЗА (испытание «найди свечу») ====================
class Vase {
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


// ==================== ТОТЕМ ЯРОСТИ ====================
// Тотем ярости: его нужно разбить мечом (L). Три удара.
class Totem {
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


// ==================== ПРЕДМЕТЫ ДЛЯ СБОРА ====================
// Предметы, которые нужно собрать в испытании: корона, розы, угощение, сферы снов.
class Pickup {
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