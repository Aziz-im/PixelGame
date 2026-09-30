package game;

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