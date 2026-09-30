package game;

import java.awt.Color;

// Цветовая тема локации. Из двух цветов (base + accent) остальные оттенки считаются автоматически.
public final class Theme {
    // чем «сыплется» на фоне: искры, монеты, угли, огоньки, лепестки, пар, буква "z"
    public enum Deco { NONE, SPARKLE, COINS, EMBERS, WISPS, PETALS, STEAM, DUST }

    public static final Theme TUTORIAL = new Theme(new Color(112, 90, 150), new Color(240, 200, 60), Deco.NONE);
    public static final Theme HALL     = new Theme(new Color(46, 64, 92),   new Color(240, 200, 60), Deco.NONE);
    public static final Theme PRIDE    = new Theme(new Color(70, 100, 210), new Color(255, 215, 90), Deco.SPARKLE);   // синий + золото
    public static final Theme GREED    = new Theme(new Color(205, 165, 45), new Color(255, 235, 120), Deco.COINS);    // золотой
    public static final Theme WRATH    = new Theme(new Color(200, 45, 40),  new Color(255, 150, 50), Deco.EMBERS);    // красный
    public static final Theme ENVY     = new Theme(new Color(50, 165, 85),  new Color(170, 255, 170), Deco.WISPS);    // зелёный
    public static final Theme LUST     = new Theme(new Color(205, 75, 155), new Color(255, 160, 210), Deco.PETALS);   // розовый
    public static final Theme GLUTTONY = new Theme(new Color(215, 125, 45), new Color(255, 215, 140), Deco.STEAM);    // оранжевый
    public static final Theme SLOTH    = new Theme(new Color(95, 125, 160), new Color(200, 220, 240), Deco.DUST);     // серо-голубой

    public final Color base, accent;
    public final Color skyTop, skyBottom, pillar, pillarHi;
    public final Color wallTop, wallTopHi, wallBody, wallLine;
    public final Deco deco;

    private Theme(Color base, Color accent, Deco deco) {
        this.base = base;
        this.accent = accent;
        this.deco = deco;
        skyTop    = mix(base, Color.BLACK, 0.62);
        skyBottom = mix(base, Color.BLACK, 0.12);
        pillar    = mix(base, Color.BLACK, 0.50);
        pillarHi  = mix(base, Color.WHITE, 0.20);
        wallTop   = mix(base, Color.BLACK, 0.40);
        wallTopHi = mix(accent, base, 0.35);
        wallBody  = mix(base, Color.BLACK, 0.68);
        wallLine  = mix(base, Color.BLACK, 0.50);
    }

    public static Color mix(Color a, Color b, double t) {
        return new Color(
                (int) (a.getRed() + (b.getRed() - a.getRed()) * t),
                (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                (int) (a.getBlue() + (b.getBlue() - a.getBlue()) * t));
    }

    public static Color alpha(Color c, int a) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.max(0, Math.min(255, a)));
    }
}