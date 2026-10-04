package game;

import java.awt.Color;

public class Inventory {
    public static final int SLOTS = 7;            // сколько всего слотов
    private final Item[] items = new Item[SLOTS];

    public Item get(int slot) { return items[slot]; }

    // кладёт предмет в первый свободный слот; false, если места нет
    public boolean add(Item item) {
        for (int i = 0; i < SLOTS; i++) {
            if (items[i] == null) {
                items[i] = item;
                return true;
            }
        }
        return false;
    }

    public void remove(int slot) { items[slot] = null; }
}
// ---------- Item (раньше был в Item.java) ----------

class Item {
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