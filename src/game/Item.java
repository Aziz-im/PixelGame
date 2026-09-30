package game;

import java.awt.Color;

public class Item {
    public enum Type { COIN, BOOK, CANDLE, SWORD }

    public final Type type;
    public final String name;
    public final String description;
    public String text = "";                          // для книги
    public int damage = 0;                            // для меча
    public Color blade = new Color(210, 220, 235);    // цвет клинка

    public Item(Type type, String name, String description) {
        this.type = type;
        this.name = name;
        this.description = description;
    }

    public static Item sword(String name, String description, int damage, Color blade) {
        Item it = new Item(Type.SWORD, name, description);
        it.damage = damage;
        it.blade = blade;
        return it;
    }
}