package game;

import javax.sound.sampled.*;
import java.util.EnumMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

// Звуки взаимодействий в духе хоррора (музыки нет). Всё синтезируется кодом при первом запуске — файлы .wav не нужны.
// Звуки строятся из шума, низких ударов, ржавого металла и тёмного эха — без «писков» и чистых нот.
// Использование:  Sound.play(Sound.Sfx.DOOR);   или с громкостью 0..1:  Sound.play(Sound.Sfx.ARROW, 0.5);
public final class Sound {
    private Sound() {}

    public enum Sfx {
        //                  громкость, мягкость (меньше = глуше)
        DOOR(0.60, 0.35),         // тяжёлая дверь: скрип дерева, щеколда, глухой удар
        UNLOCK(0.55, 0.45),       // ржавый замок, цепь, скрежет каменных ворот
        SPIKES(0.50, 0.40),       // клинки со скрежетом выползают из пола, тяжёлый удар, гул расстроенного металла
        SPIKE_WARN(0.35, 0.40),   // ржавый скрежет перед выскакиванием шипов
        ARROW(0.25, 0.45),        // арбалетный выстрел и свист стрелы мимо
        FLAME(0.45, 0.35),        // глухой хлопок газа и рёв огня
        ROCK_WARN(0.45, 0.30),    // камень трещит и ворчит в потолке
        ROCK_HIT(0.60, 0.35),     // тяжёлый удар камня и осыпь
        CRUMBLE(0.50, 0.40),      // плита трескается и осыпается
        SWORD(0.40, 0.40),        // тяжёлый взмах клинка
        HURT(0.55, 0.35),         // удар, выдох, стон
        FAINT(0.55, 0.25),        // замедленное сердцебиение, падение в темноту
        VASE(0.40, 0.40),         // взяла вазу: скрежет глины, гулкий стук, жуткий выдох из горлышка
        PICKUP(0.35, 0.40),       // шорох ткани и глухой стук
        SMASH(0.50, 0.50),        // треск и осыпь осколков
        PLACE(0.45, 0.35),        // камень о камень
        INTERACT(0.25, 0.35),     // тихий деревянный щелчок
        MEOW(0.45, 0.40);         // жуткое протяжное мяуканье

        final double level;   // громкость
        final double soft;    // мягкость: чем меньше число, тем глуше звук (срез высоких частот)
        Sfx(double level, double soft) { this.level = level; this.soft = soft; }
    }

    // ======== НАСТРОЙКИ ========
    public static final double MASTER = 0.3;                    // общая громкость 0..1
    private static final float RATE = 22050f;
    private static final int MAX_AT_ONCE = 10;                  // сколько звуков одновременно
    private static final double[] PITCH = {1.00, 0.90, 1.10, 0.95};   // варианты высоты: у каждого звука 4 версии
    private static final Random RND = new Random();
    private static final Map<Sfx, Integer> LAST = new EnumMap<>(Sfx.class);
    private static final double REVERB = 0.85;                   // сколько тёмного «эха подвала» добавлять (0 — сухо)

    private static final AudioFormat FMT = new AudioFormat(RATE, 16, 1, true, false);
    private static final ExecutorService EX = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "sound");
        t.setDaemon(true);
        return t;
    });
    private static final Map<Sfx, byte[][]> CACHE = new EnumMap<>(Sfx.class);
    private static final AtomicInteger ACTIVE = new AtomicInteger();

    // ======== ПУБЛИЧНОЕ API ========
    public static void preload() {
        EX.submit(() -> { for (Sfx s : Sfx.values()) data(s); });
    }

    public static void play(Sfx s) { play(s, 1.0); }

    public static void play(Sfx s, double vol) {
        EX.submit(() -> {
            try {
                if (ACTIVE.get() >= MAX_AT_ONCE) return;
                byte[][] vs = data(s);
                int v = RND.nextInt(vs.length);                          // каждый раз берём другую версию звука
                Integer last = LAST.get(s);
                if (last != null && v == last) v = (v + 1 + RND.nextInt(vs.length - 1)) % vs.length;
                LAST.put(s, v);
                byte[] d = vs[v];
                double jitter = 0.8 + 0.2 * RND.nextDouble();            // и чуть разная громкость
                Clip c = AudioSystem.getClip();
                c.open(FMT, d, 0, d.length);
                setGain(c, MASTER * s.level * vol * jitter);
                c.addLineListener(e -> {
                    if (e.getType() == LineEvent.Type.STOP) { c.close(); ACTIVE.decrementAndGet(); }
                });
                ACTIVE.incrementAndGet();
                c.start();
            } catch (Throwable ignored) { }      // нет звуковой карты — игра просто идёт без звука
        });
    }

    private static void setGain(Clip c, double amp) {
        try {
            FloatControl g = (FloatControl) c.getControl(FloatControl.Type.MASTER_GAIN);
            float db = (float) (20 * Math.log10(Math.max(amp, 0.0001)));
            g.setValue(Math.max(g.getMinimum(), Math.min(g.getMaximum(), db)));
        } catch (Exception ignored) { }
    }

    private static synchronized byte[][] data(Sfx s) {
        byte[][] d = CACHE.get(s);
        if (d == null) {
            d = new byte[PITCH.length][];
            for (int v = 0; v < d.length; v++) d[v] = toPcm(render(s, v));
            CACHE.put(s, d);
        }
        return d;
    }

    // ======== СИНТЕЗ: общая обработка ========
    private static double[] render(Sfx s, int variant) {
        Random r = new Random(77L + s.ordinal() * 31L + variant * 1009L);   // у каждой версии свой «шум» и тайминги
        double[] raw = switch (s) {
            case DOOR -> door(r);
            case UNLOCK -> unlock(r);
            case SPIKES -> spikes(r);
            case SPIKE_WARN -> spikeWarn(r);
            case ARROW -> arrow(r);
            case FLAME -> flame(r);
            case ROCK_WARN -> rockWarn(r);
            case ROCK_HIT -> rockHit(r);
            case CRUMBLE -> crumble(r);
            case SWORD -> sword(r);
            case HURT -> hurt(r);
            case FAINT -> faint(r);
            case VASE -> vase(r);
            case PICKUP -> pickup(r);
            case SMASH -> smash(r);
            case PLACE -> place(r);
            case INTERACT -> interact(r);
            case MEOW -> meow(r);
        };
        raw = resample(raw, PITCH[variant]);                           // версия чуть выше или ниже по тону
        if (s != Sfx.INTERACT) dread(raw);                             // жуткая подложка
        soften(raw, s.soft);                                           // срез высоких частот (два прохода = глуше)
        soften(raw, s.soft);
        double[] b = new double[raw.length + (int) (1.6 * RATE)];      // запас под хвост эха
        System.arraycopy(raw, 0, b, 0, raw.length);
        reverb(b, REVERB);

        double peak = 0.0001;
        for (double v : b) peak = Math.max(peak, Math.abs(v));
        int attack = (int) (0.008 * RATE), fade = (int) (0.30 * RATE);
        for (int i = 0; i < b.length; i++) {
            b[i] = b[i] / peak * 0.5;
            if (i < attack) b[i] *= i / (double) attack;
            int left = b.length - 1 - i;
            if (left < fade) b[i] *= left / (double) fade;
        }
        return b;
    }

    // тёмное эхо пустого подвала: четыре гребёнки с затуханием высоких частот
    private static void reverb(double[] b, double wet) {
        double[] delays = {0.0297, 0.0371, 0.0411, 0.0437};
        double[] out = new double[b.length];
        for (double dl : delays) {
            int d = (int) (dl * RATE);
            double[] y = new double[b.length];
            double damp = 0;
            for (int i = 0; i < b.length; i++) {
                double fbv = i >= d ? y[i - d] : 0;
                damp += 0.35 * (fbv - damp);
                y[i] = b[i] + 0.82 * damp;
                out[i] += y[i] * 0.25;
            }
        }
        for (int i = 0; i < b.length; i++) b[i] = b[i] * 0.6 + out[i] * wet;
    }

    // жуткая подложка: еле слышный расстроенный низкий гул (малая секунда), нарастает и гаснет вместе со звуком
    private static void dread(double[] a) {
        double peak = 0.0001;
        for (double v : a) peak = Math.max(peak, Math.abs(v));
        for (int i = 0; i < a.length; i++) {
            double t = t(i), env = Math.pow(Math.sin(Math.PI * i / (double) a.length), 2);
            a[i] += (sin(55, t) + sin(58.3, t) + 0.5 * sin(110.6, t)) * env * peak * 0.10;
        }
    }

    private static double[] resample(double[] a, double f) {
        if (f == 1.0) return a;
        double[] o = new double[(int) (a.length / f)];
        for (int i = 0; i < o.length; i++) {
            double pos = i * f;
            int k = (int) pos;
            double fr = pos - k, x0 = a[k], x1 = k + 1 < a.length ? a[k + 1] : 0;
            o[i] = x0 + (x1 - x0) * fr;
        }
        return o;
    }

    private static void soften(double[] b, double a) {
        double y = 0;
        for (int i = 0; i < b.length; i++) { y += a * (b[i] - y); b[i] = y; }
    }

    // ======== СИНТЕЗ: кирпичики ========
    private static final class Lp {                                    // фильтр низких частот (чем меньше a, тем глуше)
        double y;
        double f(double x, double a) { y += a * (x - y); return y; }
    }

    private static double[] buf(double sec) { return new double[(int) (sec * RATE)]; }
    private static double t(int i) { return i / (double) RATE; }
    private static double sin(double hz, double t) { return Math.sin(2 * Math.PI * hz * t); }
    private static double saw(double ph) { return (ph / (2 * Math.PI) % 1.0) * 2 - 1; }
    private static double noise(Random r) { return r.nextDouble() * 2 - 1; }

    // ржавый металл: несколько негармоничных обертонов с разным затуханием
    private static double metal(double hz, double t, double decay) {
        double[] ratio = {1, 2.76, 5.40, 8.93}, amp = {1, 0.6, 0.35, 0.2};
        double v = 0;
        for (int k = 0; k < 4; k++) v += Math.sin(2 * Math.PI * hz * ratio[k] * t) * amp[k] * Math.exp(-t * decay * (1 + k * 0.6));
        return v;
    }

    // ======== СИНТЕЗ: сами звуки ========

    // дверь: стонущее дерево (пила через фильтр, «залипание» петель), щеколда, глухой удар
    private static double[] door(Random r) {
        double[] b = buf(1.5);
        Lp body = new Lp(), fr = new Lp(), tn = new Lp();
        double ph = 0;
        for (int i = 0; i < b.length; i++) {
            double t = t(i), g = r.nextGaussian();
            ph += 2 * Math.PI * (82 * (1 + 0.25 * Math.sin(Math.PI * Math.min(1, t))) + 6 * sin(5.3, t)) / RATE;
            double stick = Math.pow(0.5 + 0.5 * sin(11 + 5 * t, t), 3);
            double env = t < 1.05 ? Math.pow(Math.sin(Math.PI * t / 1.05), 1.2) : 0;
            double v = body.f(saw(ph), 0.12) * env * stick * 1.6 + fr.f(g, 0.10) * env * stick * 1.8;
            if (t < 0.15) v += metal(310, t, 25) * 0.35;
            double nz = tn.f(g, 0.12);
            if (t > 1.12) {
                double d = t - 1.12;
                v += Math.sin(2 * Math.PI * (62 - 20 * d) * d) * Math.exp(-d * 14) + nz * Math.exp(-d * 25) * 0.9;
            }
            b[i] = v;
        }
        return b;
    }

    // замок: тяжёлый лязг, дребезг цепи, долгий скрежет камня
    private static double[] unlock(Random r) {
        double[] b = buf(1.8);
        Lp tn = new Lp(), gr = new Lp();
        double[] rat = new double[8], rhz = new double[8];
        for (int k = 0; k < 8; k++) { rat[k] = 0.18 + r.nextDouble() * 0.45; rhz[k] = 900 + r.nextDouble() * 700; }
        for (int i = 0; i < b.length; i++) {
            double t = t(i), g = r.nextGaussian();
            double v = tn.f(g, 0.25) * Math.exp(-t * 30) * 0.9 + metal(180, t, 9) * 0.5;
            for (int k = 0; k < 8; k++) {
                double d = t - rat[k];
                if (d >= 0) v += metal(rhz[k], d, 45) * 0.18;
            }
            if (t > 0.4 && t < 1.7) {
                double u = (t - 0.4) / 1.3, env = Math.sin(Math.PI * u);
                double stutter = 0.6 + 0.4 * sin(7, t);
                v += gr.f(g, 0.04) * env * 3.0 * stutter + sin(48 + 6 * u, t) * env * 0.4;
            }
            b[i] = v;
        }
        return b;
    }

    // шипы: клинки с визгом металла выползают из пола, тяжёлый удар, затем расстроенный гул ржавой стали
    private static double[] spikes(Random r) {
        double[] b = buf(1.3);
        Lp scr = new Lp(), nz = new Lp(), gr = new Lp();
        double ph = 0;
        for (int i = 0; i < b.length; i++) {
            double t = t(i), g = r.nextGaussian(), v = 0;
            if (t < 0.18) {                                            // визг: тон скользит вверх, как смычок по металлу
                double u = t / 0.18;
                ph += 2 * Math.PI * (260 + 700 * u * u + 18 * sin(31, t)) / RATE;
                v += scr.f(saw(ph), 0.22) * Math.pow(Math.sin(Math.PI * u), 0.6) * 0.9 + gr.f(g, 0.3) * Math.sin(Math.PI * u) * 0.5;
            }
            if (t >= 0.15) {                                           // удар клинков и гулкий отзвук
                double d = t - 0.15;
                v += Math.sin(2 * Math.PI * (70 - 25 * Math.min(d, 1)) * d) * Math.exp(-d * 14) * 1.1
                        + nz.f(g, 0.3) * Math.exp(-d * 35) * 0.9
                        + (metal(233, d, 3.0) * 0.35 + metal(247, d, 3.5) * 0.3);   // две близкие частоты — неприятные «биения»
            }
            b[i] = v;
        }
        return b;
    }

    // предупреждение: ржавый, дрожащий скрежет
    private static double[] spikeWarn(Random r) {
        double[] b = buf(0.5);
        Lp b1 = new Lp(), b2 = new Lp();
        for (int i = 0; i < b.length; i++) {
            double t = t(i), g = r.nextGaussian();
            double band = b1.f(g, 0.35) - b2.f(g, 0.05);
            double rattle = Math.pow(0.5 + 0.5 * Math.sin(2 * Math.PI * 23 * t + 3 * Math.sin(2 * Math.PI * 3 * t)), 4);
            b[i] = band * rattle * Math.sin(Math.PI * t / 0.5) * 1.8;
        }
        return b;
    }

    // арбалет: щелчок спуска, удар тетивы, свист стрелы, уходящий вниз по тону
    private static double[] arrow(Random r) {
        double[] b = buf(0.9);
        Lp sn = new Lp(), l1 = new Lp(), l2 = new Lp();
        for (int i = 0; i < b.length; i++) {
            double t = t(i), g = r.nextGaussian();
            double snap = sn.f(g, 0.3) * Math.exp(-t * 60) * 0.8 + sin(140, t) * Math.exp(-t * 25) * 0.6;
            double ahi = 0.08 + 0.4 * Math.exp(-t * 3);
            double band = l1.f(g, ahi) - l2.f(g, ahi * 0.2);
            double whoosh = band * Math.pow(Math.sin(Math.PI * Math.min(1, t / 0.5)), 2) * Math.exp(-t * 2.5) * 2.4;
            b[i] = snap + whoosh;
        }
        return b;
    }

    // огонь: глухой «вух» газа, рёв и потрескивание
    private static double[] flame(Random r) {
        double[] b = buf(1.3);
        Lp roar = new Lp();
        double click = 0;
        for (int i = 0; i < b.length; i++) {
            double t = t(i), g = r.nextGaussian();
            double env = Math.min(1, t / 0.07) * Math.exp(-t * 2.2);
            if (r.nextDouble() < 0.003) click = 0.9;
            click *= 0.9;
            b[i] = roar.f(g, 0.06) * env * 2.6 + sin(55, t) * Math.exp(-t * 8) * 0.9 + click * noise(r) * env * 0.8;
        }
        return b;
    }

    // камень под потолком: низкий гул и треск
    private static double[] rockWarn(Random r) {
        double[] b = buf(1.0);
        Lp rum = new Lp();
        double click = 0;
        for (int i = 0; i < b.length; i++) {
            double t = t(i), g = r.nextGaussian(), env = Math.sin(Math.PI * t);
            if (r.nextDouble() < 0.0012) click = 1;
            click *= 0.88;
            b[i] = rum.f(g, 0.015) * env * 5 + sin(34, t) * env * 0.4 + click * noise(r) * env * 0.7;
        }
        return b;
    }

    // падение камня: гулкий бас-удар, пыль и осыпь обломков
    private static double[] rockHit(Random r) {
        double[] b = buf(1.7);
        Lp nz = new Lp(), tail = new Lp();
        double click = 0;
        for (int i = 0; i < b.length; i++) {
            double t = t(i), g = r.nextGaussian();
            if (t > 0.15 && r.nextDouble() < 0.006 * Math.exp(-t * 2.5)) click = 1;
            click *= 0.88;
            b[i] = Math.sin(2 * Math.PI * (48 - 22 * Math.min(t, 1)) * t) * Math.exp(-t * 3.2) * 1.2
                    + nz.f(g, 0.2) * Math.exp(-t * 9) * 1.4
                    + click * noise(r) * 0.5
                    + tail.f(g, 0.02) * Math.exp(-t * 2) * 2;
        }
        return b;
    }

    // рассыпающаяся плита: частые сухие трески и осыпь
    private static double[] crumble(Random r) {
        double[] b = buf(1.0);
        Lp rum = new Lp();
        double click = 0;
        for (int i = 0; i < b.length; i++) {
            double t = t(i), g = r.nextGaussian();
            if (r.nextDouble() < 0.01 * Math.exp(-t * 2)) click = 1;
            click *= 0.85;
            b[i] = rum.f(g, 0.03) * Math.exp(-t * 3) * 2.2 + click * noise(r) * 0.8;
        }
        return b;
    }

    // взмах: тяжёлый воздух, свист поднимается и обрывается
    private static double[] sword(Random r) {
        double[] b = buf(0.4);
        Lp l1 = new Lp(), l2 = new Lp();
        for (int i = 0; i < b.length; i++) {
            double t = t(i), g = r.nextGaussian(), u = t / 0.4;
            double a = 0.1 + 0.4 * u;
            double band = l1.f(g, a) - l2.f(g, a * 0.15);
            double env = Math.pow(Math.sin(Math.PI * u), 1.5);
            b[i] = band * env * 2.5 + sin(90, t) * env * 0.3;
        }
        return b;
    }

    // удар по Эльзе: глухой хруст, резкий выдох, короткий стон
    private static double[] hurt(Random r) {
        double[] b = buf(0.8);
        Lp wet = new Lp(), gr = new Lp(), b1 = new Lp(), b2 = new Lp();
        double ph = 0;
        for (int i = 0; i < b.length; i++) {
            double t = t(i), g = r.nextGaussian();
            ph += 2 * Math.PI * (280 - 150 * t) / RATE;
            double breath = (b1.f(g, 0.3) - b2.f(g, 0.05)) * Math.exp(-Math.pow((t - 0.25) / 0.12, 2)) * 1.5;
            b[i] = Math.sin(2 * Math.PI * (85 - 35 * t) * t) * Math.exp(-t * 12)
                    + wet.f(g, 0.15) * Math.exp(-t * 18)
                    + gr.f(saw(ph), 0.1) * Math.min(1, t / 0.05) * Math.exp(-t * 4.5) * 0.7
                    + breath;
        }
        return b;
    }

    // обморок: замедленное сердцебиение, гул уходит вниз, как под водой
    private static double[] faint(Random r) {
        double[] b = buf(2.0);
        Lp rum = new Lp();
        double[] at = {0, 0.28, 0.95, 1.25}, am = {1.0, 0.7, 0.6, 0.4};
        double ph = 0;
        for (int i = 0; i < b.length; i++) {
            double t = t(i), g = r.nextGaussian();
            ph += 2 * Math.PI * (95 * Math.exp(-t * 0.9) + 30) / RATE;
            double v = Math.sin(ph) * Math.exp(-t * 1.1) * 0.5 + rum.f(g, 0.02) * Math.exp(-t * 1.5) * 2;
            for (int k = 0; k < 4; k++) {
                double d = t - at[k];
                if (d >= 0) v += Math.sin(2 * Math.PI * (60 - 25 * d) * d) * Math.exp(-d * 12) * am[k];
            }
            b[i] = v;
        }
        return b;
    }

    // взяла вазу: захват, скрежет глины, гулкий стук, из горлышка будто выдыхает что-то холодное
    private static double[] vase(Random r) {
        double[] b = buf(1.1);
        Lp rus1 = new Lp(), rus2 = new Lp(), sc1 = new Lp(), sc2 = new Lp(), br1 = new Lp(), br2 = new Lp();
        for (int i = 0; i < b.length; i++) {
            double t = t(i), g = r.nextGaussian(), v = 0;
            if (t < 0.12) v += (rus1.f(g, 0.3) - rus2.f(g, 0.05)) * Math.sin(Math.PI * t / 0.12) * 1.4;      // рука хватает
            if (t > 0.08 && t < 0.45) {                                                                       // зернистый скрежет глины
                double u = (t - 0.08) / 0.37;
                double grain = Math.pow(0.5 + 0.5 * sin(40 + 20 * u, t), 2);
                v += (sc1.f(g, 0.2) - sc2.f(g, 0.04)) * Math.sin(Math.PI * u) * grain * 2.2;
            }
            if (t > 0.12) {                                                                                   // гулкий стук внутри вазы
                double d = t - 0.12;
                v += (sin(180, d) + 0.7 * sin(310, d) + 0.5 * sin(520, d)) * Math.exp(-d * 7) * 0.5;
            }
            if (t > 0.3 && t < 1.0) {                                                                         // холодный выдох из горлышка
                double u = (t - 0.3) / 0.7;
                v += (br1.f(g, 0.2) - br2.f(g, 0.03)) * Math.pow(Math.sin(Math.PI * u), 2) * 1.6;
            }
            b[i] = v;
        }
        return b;
    }

    // подняла предмет: шорох ткани и глухой стук
    private static double[] pickup(Random r) {
        double[] b = buf(0.5);
        Lp b1 = new Lp(), b2 = new Lp(), kn = new Lp();
        for (int i = 0; i < b.length; i++) {
            double t = t(i), g = r.nextGaussian();
            double env = t < 0.3 ? (0.6 + 0.4 * sin(17, t)) * Math.sin(Math.PI * t / 0.3) : 0;
            double v = (b1.f(g, 0.3) - b2.f(g, 0.05)) * env * 1.6;
            if (t > 0.22) {
                double d = t - 0.22;
                v += sin(140, d) * Math.exp(-d * 35) * 0.7 + kn.f(g, 0.2) * Math.exp(-d * 40) * 0.5;
            }
            b[i] = v;
        }
        return b;
    }

    // разбилась ваза: треск, глухой удар и осыпь осколков
    private static double[] smash(Random r) {
        double[] b = buf(1.4);
        Lp sn = new Lp();
        int n = 22;
        double[] at = new double[n], hz = new double[n], am = new double[n];
        for (int k = 0; k < n; k++) {
            at[k] = 0.02 + Math.pow(r.nextDouble(), 1.8) * 0.9;
            hz[k] = 1100 + r.nextDouble() * 2100;
            am[k] = 1 - at[k] / 1.2;
        }
        for (int i = 0; i < b.length; i++) {
            double t = t(i), g = r.nextGaussian();
            double v = sn.f(g, 0.45) * Math.exp(-t * 30) + Math.sin(2 * Math.PI * (95 - 40 * t) * t) * Math.exp(-t * 14) * 0.6;
            for (int k = 0; k < n; k++) {
                double d = t - at[k];
                if (d >= 0) v += sin(hz[k], d) * Math.exp(-d * 45) * 0.2 * am[k];
            }
            b[i] = v;
        }
        return b;
    }

    // камень о камень: глухой удар и короткий скрежет
    private static double[] place(Random r) {
        double[] b = buf(0.5);
        Lp nz = new Lp(), b1 = new Lp(), b2 = new Lp();
        for (int i = 0; i < b.length; i++) {
            double t = t(i), g = r.nextGaussian();
            double v = Math.sin(2 * Math.PI * (100 - 60 * t) * t) * Math.exp(-t * 14) * 0.9 + nz.f(g, 0.2) * Math.exp(-t * 30) * 0.8;
            if (t > 0.04 && t < 0.2) v += (b1.f(g, 0.4) - b2.f(g, 0.1)) * Math.sin(Math.PI * (t - 0.04) / 0.16) * 0.7;
            b[i] = v;
        }
        return b;
    }

    // тихий деревянный щелчок
    private static double[] interact(Random r) {
        double[] b = buf(0.22);
        Lp nz = new Lp();
        for (int i = 0; i < b.length; i++) {
            double t = t(i), g = r.nextGaussian();
            b[i] = nz.f(g, 0.25) * Math.exp(-t * 30) * 0.8 + sin(160, t) * Math.exp(-t * 40) * 0.6;
        }
        return b;
    }

    // жуткое мяуканье: низкое, протяжное, с придыханием
    private static double[] meow(Random r) {
        double[] b = buf(0.9);
        Lp tone = new Lp(), b1 = new Lp(), b2 = new Lp();
        double ph = 0;
        for (int i = 0; i < b.length; i++) {
            double t = t(i), g = r.nextGaussian(), u = t / 0.9;
            double f = 380 + 170 * Math.sin(Math.PI * Math.pow(u, 0.6)) - 120 * u + 14 * sin(7, t);
            ph += 2 * Math.PI * f / RATE;
            double harm = Math.sin(ph) + 0.7 * Math.sin(2 * ph) + 0.5 * Math.sin(3 * ph) + 0.35 * Math.sin(4 * ph);
            double env = Math.pow(Math.sin(Math.PI * u), 0.6);
            b[i] = tone.f(harm, 0.25) * env * 0.35 + (b1.f(g, 0.3) - b2.f(g, 0.05)) * env * 0.25;
        }
        return b;
    }

    private static byte[] toPcm(double[] s) {
        byte[] out = new byte[s.length * 2];
        for (int i = 0; i < s.length; i++) {
            int v = (int) Math.max(-32768, Math.min(32767, s[i] * 32767));
            out[2 * i] = (byte) (v & 0xFF);
            out[2 * i + 1] = (byte) ((v >> 8) & 0xFF);
        }
        return out;
    }
}