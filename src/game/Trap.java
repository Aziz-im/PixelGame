package game;

import java.awt.*;

// Ловушки для комнат с видом сбоку.
//  HIDDEN_SPIKES — шипы прячутся в полу и выскакивают, когда игрок подходит
//  CRUMBLE       — хрупкая плита: рушится, если на ней постоять, потом возвращается
//  SAW           — пила ездит туда-сюда по полу
//  FLAME         — струя огня из пола по таймеру (сначала искры — предупреждение)
public class Trap {
    public enum Type { HIDDEN_SPIKES, CRUMBLE, SAW, FLAME }

    private static final int T = Room.TILE;

    public final Type type;
    public final int col, row;
    public final int col2;            // для пилы: где заканчивается путь
    public final int offset;          // сдвиг фазы (для огня)
    private int state, timer;

    private Trap(Type type, int col, int row, int col2, int offset) {
        this.type = type;
        this.col = col;
        this.row = row;
        this.col2 = col2;
        this.offset = offset;
    }

    public static Trap hidden(int col, int row)               { return new Trap(Type.HIDDEN_SPIKES, col, row, col, 0); }
    public static Trap crumble(int col, int row)              { return new Trap(Type.CRUMBLE, col, row, col, 0); }
    public static Trap saw(int colA, int colB, int row)       { return new Trap(Type.SAW, colA, row, colB, 0); }
    public static Trap flame(int col, int row, int offset)    { return new Trap(Type.FLAME, col, row, col, offset); }

    // хрупкая плита твёрдая, пока не рухнула
    public boolean solidAt(int c, int r) {
        return type == Type.CRUMBLE && c == col && r == row && state != 2;
    }

    public void update(Player p, int tick) {
        switch (type) {
            case HIDDEN_SPIKES -> {
                switch (state) {
                    case 0 -> {
                        boolean near = Math.abs(p.centerX() - (col * T + T / 2)) < 72
                                && Math.abs(p.centerY() - (row * T + T / 2)) < 70;
                        if (near) { state = 1; timer = 22; }          // предупреждение: шипы дрожат
                    }
                    case 1 -> { if (--timer <= 0) { state = 2; timer = 75; } }   // выскочили
                    case 2 -> { if (--timer <= 0) { state = 3; timer = 50; } }   // прячутся
                    default -> { if (--timer <= 0) state = 0; }
                }
            }
            case CRUMBLE -> {
                if (state == 0) {
                    boolean standing = p.y + p.h == row * T && p.x + p.w > col * T && p.x < (col + 1) * T;
                    if (standing) { state = 1; timer = 48; }
                } else if (state == 1) {
                    if (--timer <= 0) { state = 2; timer = 170; }
                } else if (--timer <= 0 && !new Rectangle(col * T, row * T, T, T).intersects(p.box())) {
                    state = 0;                                         // вернулась (если игрок не внутри неё)
                }
            }
            default -> { }
        }
    }

    private int sawX(int tick) {
        int mid = (col + col2) * T / 2 + T / 2;
        int amp = (col2 - col) * T / 2;
        return mid + (int) (Math.sin(tick * 0.03) * amp);
    }

    private int flamePhase(int tick) {          // 0 — тихо, 1 — искры, 2 — огонь
        int t = (tick + offset) % 200;
        return t < 110 ? 0 : t < 140 ? 1 : 2;
    }

    // опасная зона в этот кадр (или null, если сейчас безопасно)
    public Rectangle hazard(int tick) {
        return switch (type) {
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
        }
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