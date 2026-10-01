package game;

// Система грехов: в каждой из 7 комнат (ключи s1..s7) игрок делает один выбор — добрый или плохой.
// 4 и больше плохих поступков -> АД, 4 и больше добрых -> РАЙ.
// Так как комнат 7, одно из двух условий выполняется всегда (ничьей не бывает).
public class Sins {
    public enum Ending { HEAVEN, HELL }

    public static final int HELL_LIMIT   = 4;         // с какого числа плохих поступков начинается Ад
    public static final int HEAVEN_LIMIT = 4;         // с какого числа добрых поступков начинается Рай

    private final boolean[] bad = new boolean[7];
    private final boolean[] good = new boolean[7];

    public void reset() {
        java.util.Arrays.fill(bad, false);
        java.util.Arrays.fill(good, false);
    }

    private static int idx(String key) {
        if (key == null || !key.matches("s[1-7]")) return -1;
        return key.charAt(1) - '1';
    }

    // плохой поступок в комнате; true — если он засчитан впервые
    public boolean bad(String key) {
        int i = idx(key);
        if (i < 0 || bad[i]) return false;
        bad[i] = true;
        good[i] = false;
        return true;
    }

    // добрый поступок (плохой, засчитанный раньше, не отменяется)
    public void good(String key) {
        int i = idx(key);
        if (i >= 0 && !bad[i]) good[i] = true;
    }

    public int badCount() {
        int n = 0;
        for (boolean b : bad) if (b) n++;
        return n;
    }

    public int goodCount() {
        int n = 0;
        for (boolean b : good) if (b) n++;
        return n;
    }

    public Ending ending() {
        if (badCount() >= HELL_LIMIT) return Ending.HELL;
        return Ending.HEAVEN;                          // иначе добрых не меньше HEAVEN_LIMIT (если пройдены все 7 комнат)
    }
}