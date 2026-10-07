package game;

import java.awt.*;

// Ловушки для комнат с видом сбоку.
//  HIDDEN_SPIKES — шипы прячутся в полу и выскакивают, когда игрок подходит
//  CRUMBLE       — хрупкая плита: рушится, если на ней постоять, потом возвращается
//  SAW           — пила ездит туда-сюда по полу
//  FLAME         — струя огня из пола по таймеру (сначала искры — предупреждение)
//  ROCK          — камень в потолке: когда подходишь, дрожит, потом падает (успей отойти)
//  ARROW         — стрелострел: торчит из стены/блока, мигает (предупреждение) и пускает стрелу по прямой
public class Trap {
    public enum Type { HIDDEN_SPIKES, CRUMBLE, SAW, FLAME, ARROW, ROCK }

    private static final int T = Room.TILE;

    public final Type type;
    public final int col, row;
    public final int col2;            // для пилы: где заканчивается путь
    public final int offset;          // сдвиг фазы (для огня и стрел)
    public final int dx, dy, range;   // для стрелы: направление полёта и дальность в клетках
    private int state, timer;

    private Trap(Type type, int col, int row, int col2, int offset, int dx, int dy, int range) {
        this.type = type;
        this.col = col;
        this.row = row;
        this.col2 = col2;
        this.offset = offset;
        this.dx = dx;
        this.dy = dy;
        this.range = range;
    }

    private Trap(Type type, int col, int row, int col2, int offset) {
        this(type, col, row, col2, offset, 0, 0, 0);
    }

    public static Trap hidden(int col, int row)               { return new Trap(Type.HIDDEN_SPIKES, col, row, col, 0); }
    public static Trap crumble(int col, int row)              { return new Trap(Type.CRUMBLE, col, row, col, 0); }
    public static Trap saw(int colA, int colB, int row)       { return new Trap(Type.SAW, colA, row, colB, 0); }
    public static Trap flame(int col, int row, int offset)    { return new Trap(Type.FLAME, col, row, col, offset); }
    public static Trap rock(int col, int row)                 { return new Trap(Type.ROCK, col, row, col, 0); }   // row — ряд, где камень висит под потолком

    // стрелострел в клетке (col,row); (dx,dy) — куда летит стрела: (0,1) вниз, (0,-1) вверх, (-1,0) влево, (1,0) вправо
    public static Trap arrow(int col, int row, int dx, int dy, int range, int offset) {
        return new Trap(Type.ARROW, col, row, col, offset, dx, dy, range);
    }

    // какие колонки «заняты» ловушкой (для стрелы — вся трасса полёта)
    public int minCol() { return type == Type.ARROW && dx < 0 ? col - range : col; }
    public int maxCol() { return type == Type.ARROW && dx > 0 ? col + range : col2; }

    // хрупкая плита твёрдая, пока не рухнула
    public boolean solidAt(int c, int r) {
        return type == Type.CRUMBLE && c == col && r == row && state != 2;
    }

    public void update(Player p, int tick) {
        switch (type) {
            case HIDDEN_SPIKES -> {
                switch (state) {
                    case 0 -> {
                        boolean near = Math.abs(p.centerX() - (col * T + T / 2)) < 88
                                && Math.abs(p.centerY() - (row * T + T / 2)) < 70;
                        if (near) { state = 1; timer = 14; sfx(Sound.Sfx.SPIKE_WARN, p, 0.8); }   // предупреждение: шипы дрожат
                    }
                    case 1 -> { if (--timer <= 0) { state = 2; timer = 90; sfx(Sound.Sfx.SPIKES, p, 1.0); } }   // выскочили
                    case 2 -> { if (--timer <= 0) { state = 3; timer = 40; } }   // прячутся
                    default -> { if (--timer <= 0) state = 0; }
                }
            }
            case CRUMBLE -> {
                if (state == 0) {
                    boolean standing = p.y + p.h == row * T && p.x + p.w > col * T && p.x < (col + 1) * T;
                    if (standing) { state = 1; timer = 34; sfx(Sound.Sfx.CRUMBLE, p, 1.0); }
                } else if (state == 1) {
                    if (--timer <= 0) { state = 2; timer = 170; sfx(Sound.Sfx.ROCK_HIT, p, 0.5); }
                } else if (--timer <= 0 && !new Rectangle(col * T, row * T, T, T).intersects(p.box())) {
                    state = 0;                                         // вернулась (если игрок не внутри неё)
                }
            }
            case ROCK -> {
                switch (state) {
                    case 0 -> { if (Math.abs(p.centerX() - (col * T + T / 2)) < ROCK_RADIUS) { state = 1; timer = ROCK_WARN; sfx(Sound.Sfx.ROCK_WARN, p, 1.0); } }   // дрожит
                    case 1 -> { if (--timer <= 0) { state = 2; timer = ROCK_FALL; } }                                             // падает
                    case 2 -> { if (--timer <= 0) { state = 3; timer = 110; sfx(Sound.Sfx.ROCK_HIT, p, 1.0); } }                  // обломки лежат
                    default -> { if (--timer <= 0) state = 0; }                                                                   // новый камень
                }
            }
            case ARROW -> {                                          // state хранит прошлую фазу: звук только в момент выстрела
                int ph = arrowPhase(tick);
                if (ph == 2 && state != 2) sfx(Sound.Sfx.ARROW, p, 0.4);
                state = ph;
            }
            case FLAME -> {
                int ph = flamePhase(tick);
                if (ph == 2 && state != 2) sfx(Sound.Sfx.FLAME, p, 1.0);
                state = ph;
            }
            default -> { }
        }
    }

    // звук ловушки: чем дальше от Эльзы, тем тише; совсем далеко — не играет
    private void sfx(Sound.Sfx s, Player p, double vol) {
        double d = Math.hypot(p.centerX() - (col * T + T / 2), p.centerY() - (row * T + T / 2));
        if (d > 560) return;
        double near = 1 - d / 560.0;
        double v = vol * (0.5 + 0.5 * near);                 // вблизи лишь чуть громче, чем вдали: звук не «вырастает» при приближении
        Sound.play(s, v);
    }

    // ----- камень -----
    private static final int ROCK_RADIUS = 92, ROCK_WARN = 32, ROCK_FALL = 14, ROCK_LAND_Y = 10 * T;   // 10 ряд — пол в комнатах сбоку

    private int rockBottom() {                      // нижний край камня в этот кадр
        int start = (row + 1) * T;
        if (state != 2) return start;
        double prog = (ROCK_FALL - timer + 1) / (double) ROCK_FALL;
        return start + (int) ((ROCK_LAND_Y - start) * Math.min(1.0, prog));
    }

    private int sawX(int tick) {
        int mid = (col + col2) * T / 2 + T / 2;
        int amp = (col2 - col) * T / 2;
        return mid + (int) (Math.sin(tick * 0.045) * amp);
    }

    private int flamePhase(int tick) {          // 0 — тихо, 1 — искры, 2 — огонь
        int t = (tick + offset) % 200;
        return t < 120 ? 0 : t < 140 ? 1 : 2;
    }

    private static final int ARROW_PERIOD = 150, ARROW_WARN = 90, ARROW_FLY = 112;   // 100..130 — мигает, 130..170 — летит

    private int arrowPhase(int tick) {              // 0 — тихо, 1 — предупреждение, 2 — стрела в полёте
        int t = (tick + offset) % ARROW_PERIOD;
        return t < ARROW_WARN ? 0 : t < ARROW_FLY ? 1 : 2;
    }

    // центр летящей стрелы в этот кадр
    private Point arrowPos(int tick) {
        int t = (tick + offset) % ARROW_PERIOD - ARROW_FLY;                 // 0..39
        double dist = Math.max(0, Math.min(1, t / (double) (ARROW_PERIOD - ARROW_FLY))) * range * T;
        int sx = dx != 0 ? (col + (dx > 0 ? 1 : 0)) * T : col * T + T / 2;
        int sy = dy != 0 ? (row + (dy > 0 ? 1 : 0)) * T : row * T + 34;
        return new Point(sx + (int) (dx * dist), sy + (int) (dy * dist));
    }

    // опасная зона в этот кадр (или null, если сейчас безопасно)
    public Rectangle hazard(int tick) {
        return switch (type) {
            case ARROW -> {
                if (arrowPhase(tick) != 2) yield null;
                Point a = arrowPos(tick);
                yield dx != 0 ? new Rectangle(a.x - 16, a.y - 6, 32, 12) : new Rectangle(a.x - 10, a.y - 16, 20, 32);
            }
            case ROCK -> state == 2 ? new Rectangle(col * T + 8, rockBottom() - 44, 32, 44) : null;
            case HIDDEN_SPIKES -> state == 2 ? new Rectangle(col * T + 6, row * T + 12, 36, 36) : null;
            case SAW -> { int cx = sawX(tick), cy = row * T + 32; yield new Rectangle(cx - 15, cy - 15, 30, 30); }
            case FLAME -> flamePhase(tick) == 2 ? new Rectangle(col * T + 10, (row - 2) * T, 28, 3 * T) : null;
            default -> null;
        };
    }

    // ================= ОТРИСОВКА =================
    public void draw(Graphics2D g, int tick, Theme th) {
        switch (type) {
            case HIDDEN_SPIKES -> drawHidden(g, tick, th);
            case CRUMBLE -> drawCrumble(g, tick, th);
            case SAW -> drawSaw(g, tick, th);
            case FLAME -> drawFlame(g, tick);
            case ARROW -> drawArrow(g, tick, th);
            case ROCK -> drawRock(g, tick, th);
        }
    }

    // стрелострел: каменная плита с бойницей в сторону полёта; перед выстрелом бойница светится
    private void drawArrow(Graphics2D g, int tick, Theme th) {
        int x = col * T, y = row * T;
        int ph = arrowPhase(tick);
        g.setColor(new Color(34, 30, 38));
        g.fillRect(x + 5, y + 5, T - 10, T - 10);
        g.setColor(new Color(70, 64, 78));
        g.fillRect(x + 5, y + 5, T - 10, 3);
        g.setColor(new Color(150, 154, 168));                          // заклёпки
        g.fillRect(x + 8, y + 8, 3, 3);
        g.fillRect(x + T - 11, y + T - 11, 3, 3);
        Color slit = ph == 1 ? (tick / 3 % 2 == 0 ? th.accent : new Color(255, 80, 60)) : new Color(8, 6, 10);
        if (dx != 0) g.fillRect(dx < 0 ? x + 3 : x + T - 13, y + 30, 10, 8);
        else g.fillRect(x + 18, dy > 0 ? y + T - 12 : y + 4, 12, 8);
        g.setColor(slit);
        if (dx != 0) g.fillRect(dx < 0 ? x + 4 : x + T - 12, y + 31, 8, 6);
        else g.fillRect(x + 19, dy > 0 ? y + T - 11 : y + 5, 10, 6);
        if (ph == 2) {
            Point a = arrowPos(tick);
            g.setColor(new Color(120, 84, 48));                        // древко
            if (dx != 0) g.fillRect(a.x - 16, a.y - 1, 28, 3); else g.fillRect(a.x - 1, a.y - 16, 3, 28);
            g.setColor(new Color(220, 226, 238));                      // наконечник
            if (dx != 0) {
                int hx = a.x + dx * 16;
                g.fillPolygon(new int[]{hx, hx - dx * 8, hx - dx * 8}, new int[]{a.y, a.y - 5, a.y + 5}, 3);
            } else {
                int hy = a.y + dy * 16;
                g.fillPolygon(new int[]{a.x, a.x - 5, a.x + 5}, new int[]{hy, hy - dy * 8, hy - dy * 8}, 3);
            }
            g.setColor(new Color(200, 70, 60));                        // оперение
            if (dx != 0) g.fillRect(a.x - dx * 16 - (dx > 0 ? 0 : 6), a.y - 4, 6, 8);
            else g.fillRect(a.x - 4, a.y - dy * 16 - (dy > 0 ? 0 : 6), 8, 6);
        }
    }

    // камень: висит под потолком, перед падением трясётся, сыплется пыль, а на полу мигает метка, куда он упадёт
    private void drawRock(Graphics2D g, int tick, Theme th) {
        int x = col * T;
        if (state == 3) {                                              // обломки на полу
            g.setColor(new Color(30, 28, 36));
            g.fillRect(x + 6, ROCK_LAND_Y - 8, 14, 8);
            g.fillRect(x + 24, ROCK_LAND_Y - 12, 16, 12);
            g.fillRect(x + 14, ROCK_LAND_Y - 16, 12, 8);
            g.setColor(new Color(70, 66, 80));
            g.fillRect(x + 6, ROCK_LAND_Y - 8, 14, 2);
            g.fillRect(x + 24, ROCK_LAND_Y - 12, 16, 2);
            return;
        }
        int shake = state == 1 ? (tick % 2 == 0 ? -2 : 2) : 0;
        int bottom = rockBottom();
        int top = bottom - 44;
        if (state == 1) {
            if ((tick / 4) % 2 == 0) {                                 // метка падения на полу
                g.setColor(th.accent);
                g.fillRect(x + 6, ROCK_LAND_Y - 4, T - 12, 4);
            }
            g.setColor(new Color(150, 146, 160));                      // пыль из потолка
            for (int i = 0; i < 3; i++) g.fillRect(x + 12 + i * 11 + shake, top + 44 + (tick * 3 + i * 17) % 40, 2, 3);
        } else if (state == 2) {
            g.setColor(new Color(0, 0, 0, 60));                        // тень на полу
            g.fillRect(x + 8, ROCK_LAND_Y - 3, 32, 3);
        }
        if (state == 0 || state == 1) {                                // крепление к потолку
            g.setColor(new Color(24, 22, 30));
            g.fillRect(x + 18 + shake, top - 6, 12, 8);
        }
        g.setColor(new Color(34, 32, 42));                             // глыба
        g.fillPolygon(new int[]{x + 8 + shake, x + 40 + shake, x + 36 + shake, x + 24 + shake, x + 12 + shake},
                new int[]{top, top, bottom - 8, bottom, bottom - 6}, 5);
        g.setColor(new Color(76, 72, 88));                             // светлая грань
        g.fillRect(x + 8 + shake, top, 32, 3);
        g.fillRect(x + 8 + shake, top, 3, 22);
        g.setColor(new Color(12, 10, 16));                             // трещина
        g.fillRect(x + 22 + shake, top + 6, 2, 16);
        g.fillRect(x + 22 + shake, top + 20, 10, 2);
    }

    private void drawHidden(Graphics2D g, int tick, Theme th) {
        int x = col * T, y = row * T;
        if (state == 0) {                                              // еле заметные трещины
            g.setColor(Theme.alpha(th.wallLine, 170));
            g.fillRect(x + 10, y + T - 4, 12, 2);
            g.fillRect(x + 26, y + T - 4, 10, 2);
            g.fillRect(x + 20, y + T - 7, 2, 3);
            return;
        }
        int h = state == 1 ? 6 + (tick / 2 % 2) * 3 : state == 2 ? 36 : 14;
        int shake = state == 1 ? (tick % 2 == 0 ? -1 : 1) : 0;
        g.setColor(state == 1 ? th.accent : new Color(206, 212, 226));
        for (int i = 0; i < 3; i++) {
            int sx = x + 6 + i * 12 + shake;
            g.fillPolygon(new int[]{sx, sx + 12, sx + 6}, new int[]{y + T, y + T, y + T - h}, 3);
        }
    }

    private void drawCrumble(Graphics2D g, int tick, Theme th) {
        int x = col * T, y = row * T;
        if (state == 2) {                                              // контур: здесь плита вернётся
            g.setColor(Theme.alpha(th.accent, 45));
            g.drawRect(x + 2, y + 2, T - 5, T - 5);
            return;
        }
        int dx = state == 1 ? (tick % 4 < 2 ? -2 : 2) : 0;
        g.setColor(th.wallTop);
        g.fillRect(x + dx, y, T, T);
        g.setColor(th.wallTopHi);
        g.fillRect(x + dx, y, T, 5);
        g.setColor(th.wallLine);                                       // трещины
        g.fillRect(x + dx + 10, y + 8, 2, 20);
        g.fillRect(x + dx + 10, y + 26, 14, 2);
        g.fillRect(x + dx + 30, y + 14, 2, 22);
        g.setColor(th.accent);                                         // заклёпки — признак хрупкой плиты
        g.fillRect(x + dx + 4, y + T - 9, 4, 4);
        g.fillRect(x + dx + T - 8, y + T - 9, 4, 4);
    }

    private void drawSaw(Graphics2D g, int tick, Theme th) {
        int cx = sawX(tick), cy = row * T + 32;
        g.setColor(Theme.alpha(th.wallLine, 220));                     // направляющая
        g.fillRect(col * T + 6, row * T + T - 3, (col2 - col) * T + 36, 3);
        g.setColor(new Color(214, 218, 230));
        double ang = tick * 0.25;
        for (int i = 0; i < 8; i++) {
            double a = ang + i * Math.PI / 4;
            g.fillPolygon(
                    new int[]{(int) (cx + Math.cos(a) * 20), (int) (cx + Math.cos(a - 0.3) * 12), (int) (cx + Math.cos(a + 0.3) * 12)},
                    new int[]{(int) (cy + Math.sin(a) * 20), (int) (cy + Math.sin(a - 0.3) * 12), (int) (cy + Math.sin(a + 0.3) * 12)}, 3);
        }
        g.fillOval(cx - 13, cy - 13, 26, 26);
        g.setColor(new Color(90, 96, 116));
        g.fillOval(cx - 8, cy - 8, 16, 16);
        g.setColor(new Color(50, 54, 70));
        g.fillOval(cx - 3, cy - 3, 6, 6);
    }

    private void drawFlame(Graphics2D g, int tick) {
        int x = col * T, base = row * T + T, ph = flamePhase(tick);
        g.setColor(new Color(46, 46, 58));                             // решётка-жерло
        g.fillRect(x + 8, base - 5, 32, 5);
        g.setColor(new Color(20, 20, 28));
        for (int i = 0; i < 4; i++) g.fillRect(x + 12 + i * 8, base - 4, 3, 3);
        if (ph == 1) {                                                 // искры-предупреждение
            g.setColor(new Color(255, 180, 60));
            for (int i = 0; i < 3; i++) g.fillRect(x + 12 + i * 10, base - 9 - ((tick * 2 + i * 7) % 16), 3, 3);
        } else if (ph == 2) {
            int top = base - 3 * T + 8;
            int wob = tick % 6 < 3 ? 8 : 0, wob2 = tick % 4 < 2 ? 10 : 0;
            g.setColor(new Color(225, 70, 20, 210));
            g.fillPolygon(new int[]{x + 8, x + 40, x + 34, x + 24, x + 14},
                    new int[]{base - 5, base - 5, top + wob, top - 6, top + wob2}, 5);
            g.setColor(new Color(255, 165, 40, 230));
            g.fillPolygon(new int[]{x + 14, x + 34, x + 30, x + 24, x + 18},
                    new int[]{base - 5, base - 5, top + 40 + wob, top + 26, top + 44 + wob2}, 5);
            g.setColor(new Color(255, 240, 150, 230));
            g.fillPolygon(new int[]{x + 19, x + 29, x + 24}, new int[]{base - 5, base - 5, top + 80}, 3);
        }
    }
}