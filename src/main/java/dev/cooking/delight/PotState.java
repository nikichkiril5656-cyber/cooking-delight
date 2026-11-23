package dev.cooking.delight;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

public final class PotState implements InventoryHolder {
    // Слоты совпадают с рамками в текстуре pot_gui.png (сетка сундука 9x3)
    public static final int[] ING = {1, 2, 3, 10, 11, 12};
    public static final int CONT = 23;
    public static final int OUT = 25;
    public static final int INFO = 20; // индикатор нагрева + полоска прогресса

    /** \uF808 сдвигает влево на 8px (позиция заголовка), \uE000 рисует фон GUI из ресурспака. */
    private static final Component TITLE = Component.text("\uF808\uE000")
            .font(Key.key("cookingdelight", "gui"))
            .color(NamedTextColor.WHITE);

    public final Location loc;
    public int progress;
    public PotRecipe active;
    private final Inventory inv;

    public PotState(Location loc) {
        this.loc = loc;
        this.inv = Bukkit.createInventory(this, 27, TITLE);
        setInfo(false, "Нет источника тепла", NamedTextColor.RED, 0);
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inv;
    }

    public static boolean isUsable(int slot) {
        if (slot == CONT || slot == OUT) return true;
        for (int i : ING) if (i == slot) return true;
        return false;
    }

    /**
     * Индикатор под ингредиентами: значок нагрева (heat_on/heat_off из ресурспака),
     * а прогресс готовки показывается полоской прочности (0..100).
     */
    public void setInfo(boolean heatOn, String text, NamedTextColor color, int percent) {
        ItemStack it = new ItemStack(Material.FISHING_ROD);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Component.text(text, color).decoration(TextDecoration.ITALIC, false));
        meta.setItemModel(new NamespacedKey("cookingdelight", heatOn ? "heat_on" : "heat_off"));
        if (meta instanceof Damageable d) {
            d.setMaxDamage(100);
            int p = Math.max(0, Math.min(100, percent));
            d.setDamage(p <= 0 ? 0 : 100 - p); // 0 = полоска скрыта
        }
        it.setItemMeta(meta);
        inv.setItem(INFO, it);
    }

    /** Кладёт предмет в слоты (сначала докладывает в стопки). Возвращает остаток или null. */
    public static ItemStack addTo(Inventory inv, int[] slots, ItemStack item) {
        for (int s : slots) {
            ItemStack cur = inv.getItem(s);
            if (cur != null && cur.isSimilar(item)) {
                int mv = Math.min(cur.getMaxStackSize() - cur.getAmount(), item.getAmount());
                if (mv > 0) {
                    cur.setAmount(cur.getAmount() + mv);
                    item.setAmount(item.getAmount() - mv);
                    if (item.getAmount() <= 0) return null;
                }
            }
        }
        for (int s : slots) {
            ItemStack cur = inv.getItem(s);
            if (cur == null || cur.getType().isAir()) {
                inv.setItem(s, item.clone());
                return null;
            }
        }
        return item;
    }
}
