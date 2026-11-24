package dev.cooking.delight;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Книга рецептов в виде GUI: каталог предметов плагина, по клику открывается рецепт.
 * Каталог: 28 предметов на страницу. Рецепт: сетка, стрелка и результат, как в верстаке.
 */
public final class RecipeGui {
    private RecipeGui() {}

    public static final int[] LIST_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43};
    private static final int[] POT_GRID = {10, 11, 12, 19, 20, 21};
    private static final int ARROW = 23, RESULT = 25, BACK = 49;

    private static int counter; // для смены вариантов ингредиентов раз в секунду

    /** Запись каталога: либо предмет с верстак-рецептом, либо блюдо кастрюли, либо блюдо сковороды. */
    public record Entry(String id, Component name, ItemStack icon, String kind,
                        PotRecipe pot, CraftDef craft, RecipeBook.SkilletRecipe skillet) {}

    public static final class Holder implements InventoryHolder {
        final Entry entry;   // null = каталог
        final int page;
        boolean hasNext;
        Inventory inv;
        int[] cycleSlots = new int[0];
        List<List<Material>> cycleMats = new ArrayList<>();
        List<List<Component>> cycleLore = new ArrayList<>();

        Holder(Entry entry, int page) {
            this.entry = entry;
            this.page = page;
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inv;
        }
    }

    // ---------- вспомогательное ----------

    private static Component line(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }

    private static ItemStack named(Material m, String text, NamedTextColor color, List<Component> lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(line(text, color));
        if (!lore.isEmpty()) meta.lore(lore);
        it.setItemMeta(meta);
        return it;
    }

    private static ItemStack pane() {
        ItemStack it = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Component.text(" "));
        it.setItemMeta(meta);
        return it;
    }

    private static void fillAll(Inventory inv) {
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, pane());
    }

    private static void clear(Inventory inv, int... slots) {
        for (int s : slots) inv.setItem(s, null);
    }

    private static Component nameOf(ItemStack it) {
        ItemMeta meta = it.getItemMeta();
        if (meta != null && meta.hasDisplayName()) return meta.displayName();
        return Component.translatable(it.getType().translationKey());
    }

    private static ItemStack withLore(ItemStack base, List<Component> lore) {
        ItemStack it = base.clone();
        it.setAmount(1);
        ItemMeta meta = it.getItemMeta();
        meta.lore(lore);
        it.setItemMeta(meta);
        return it;
    }

    private static ItemStack stack(Material m, List<Component> lore) {
        ItemStack it = new ItemStack(m);
        if (!lore.isEmpty()) {
            ItemMeta meta = it.getItemMeta();
            meta.lore(lore);
            it.setItemMeta(meta);
        }
        return it;
    }

    private static List<Component> optionsLore(Collection<Material> mats) {
        List<Component> l = new ArrayList<>();
        if (mats.size() <= 1) return l;
        l.add(line("Подходит любой из:", NamedTextColor.GRAY));
        int n = 0;
        for (Material m : mats) {
            if (n++ >= 8) {
                l.add(line("…", NamedTextColor.GRAY));
                break;
            }
            l.add(Component.text("• ", NamedTextColor.DARK_GRAY)
                    .append(Component.translatable(m.translationKey(), NamedTextColor.WHITE))
                    .decoration(TextDecoration.ITALIC, false));
        }
        return l;
    }

    // ---------- каталог ----------

    private static List<Entry> entries(CookingDelight plugin) {
        List<Entry> out = new ArrayList<>();
        for (CraftDef c : plugin.crafts()) {
            ItemStack icon = plugin.items().create(c.id());
            if (icon == null) continue;
            out.add(new Entry(c.id(), nameOf(icon), icon, "Предмет", null, c, null));
        }
        for (PotRecipe r : plugin.recipes().potRecipes()) {
            out.add(new Entry(r.id(), nameOf(r.result()), r.result().clone(), "Кастрюля", r, null, null));
        }
        for (RecipeBook.SkilletRecipe sr : plugin.recipes().skilletRecipes().values()) {
            ItemStack icon = new ItemStack(sr.result());
            out.add(new Entry("skillet_" + sr.input().name().toLowerCase(), nameOf(icon), icon, "Сковорода", null, null, sr));
        }
        return out;
    }

    public static int indexOfListSlot(int slot) {
        for (int i = 0; i < LIST_SLOTS.length; i++) if (LIST_SLOTS[i] == slot) return i;
        return -1;
    }

    public static void openCatalog(CookingDelight plugin, Player p, int page) {
        List<Entry> all = entries(plugin);
        int pages = Math.max(1, (all.size() + LIST_SLOTS.length - 1) / LIST_SLOTS.length);
        int pg = Math.max(0, Math.min(page, pages - 1));
        Holder h = new Holder(null, pg);
        h.hasNext = pg < pages - 1;
        Inventory inv = Bukkit.createInventory(h, 54,
                Component.text("Книга рецептов (" + (pg + 1) + "/" + pages + ")", NamedTextColor.DARK_GRAY));
        h.inv = inv;
        fillAll(inv);
        for (int i = 0; i < LIST_SLOTS.length; i++) {
            int idx = pg * LIST_SLOTS.length + i;
            if (idx >= all.size()) break;
            Entry e = all.get(idx);
            List<Component> lore = new ArrayList<>();
            lore.add(line(e.kind(), NamedTextColor.DARK_GRAY));
            lore.add(Component.empty());
            lore.add(line("Нажми, чтобы посмотреть рецепт", NamedTextColor.YELLOW));
            inv.setItem(LIST_SLOTS[i], withLore(e.icon(), lore));
        }
        if (pg > 0) inv.setItem(45, named(Material.ARROW, "Предыдущая страница", NamedTextColor.YELLOW, List.of()));
        inv.setItem(49, named(Material.BARRIER, "Закрыть", NamedTextColor.RED, List.of()));
        if (h.hasNext) inv.setItem(53, named(Material.ARROW, "Следующая страница", NamedTextColor.YELLOW, List.of()));
        p.openInventory(inv);
    }

    public static void openEntry(CookingDelight plugin, Player p, int page, int posOnPage) {
        List<Entry> all = entries(plugin);
        int idx = page * LIST_SLOTS.length + posOnPage;
        if (idx < 0 || idx >= all.size()) return;
        openDetail(plugin, p, all.get(idx), page);
    }

    // ---------- рецепт ----------

    private static void openDetail(CookingDelight plugin, Player p, Entry e, int fromPage) {
        Holder h = new Holder(e, fromPage);
        Inventory inv = Bukkit.createInventory(h, 54,
                Component.text("Рецепт: ", NamedTextColor.DARK_GRAY).append(e.name()));
        h.inv = inv;
        fillAll(inv);

        List<Integer> slots = new ArrayList<>();
        List<List<Material>> mats = new ArrayList<>();
        List<List<Component>> lores = new ArrayList<>();

        if (e.craft() != null) {
            String[] shape = e.craft().shape();
            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < 3; col++) {
                    int slot = 10 + row * 9 + col;
                    clear(inv, slot);
                    if (row >= shape.length || col >= shape[row].length()) continue;
                    RecipeChoice.MaterialChoice choice = e.craft().ingredients().get(shape[row].charAt(col));
                    if (choice == null) continue;
                    slots.add(slot);
                    mats.add(new ArrayList<>(choice.getChoices()));
                    lores.add(optionsLore(choice.getChoices()));
                }
            }
            inv.setItem(ARROW, named(Material.ARROW, "Верстак", NamedTextColor.GRAY, List.of()));
            inv.setItem(RESULT, e.icon().clone());
        } else if (e.pot() != null) {
            PotRecipe r = e.pot();
            for (int s : POT_GRID) clear(inv, s);
            for (int i = 0; i < r.ingredients().size() && i < POT_GRID.length; i++) {
                List<Material> list = new ArrayList<>(r.ingredients().get(i));
                slots.add(POT_GRID[i]);
                mats.add(list);
                lores.add(optionsLore(list));
            }
            clear(inv, 28, 29, 30);
            if (r.container() != null) {
                inv.setItem(28, named(r.container(), "Нужна посуда", NamedTextColor.GRAY, List.of()));
            }
            addHeatAndClock(inv, r.time());
            inv.setItem(ARROW, named(Material.ARROW, "Кастрюля", NamedTextColor.GRAY, List.of()));
            List<Component> lore = new ArrayList<>();
            ItemMeta meta = r.result().getItemMeta();
            if (meta != null && meta.hasFood()) {
                lore.add(line("Сытость: " + meta.getFood().getNutrition(), NamedTextColor.GREEN));
            }
            inv.setItem(RESULT, lore.isEmpty() ? r.result().clone() : withLore(r.result(), lore));
        } else if (e.skillet() != null) {
            RecipeBook.SkilletRecipe r = e.skillet();
            clear(inv, 20, 29, 30);
            slots.add(20);
            mats.add(new ArrayList<>(List.of(r.input())));
            lores.add(new ArrayList<>());
            addHeatAndClock(inv, r.time());
            inv.setItem(ARROW, named(Material.ARROW, "Сковорода", NamedTextColor.GRAY, List.of()));
            inv.setItem(RESULT, new ItemStack(r.result()));
        }

        h.cycleSlots = slots.stream().mapToInt(Integer::intValue).toArray();
        h.cycleMats = mats;
        h.cycleLore = lores;
        for (int i = 0; i < h.cycleSlots.length; i++) {
            inv.setItem(h.cycleSlots[i], stack(mats.get(i).get(0), lores.get(i)));
        }

        inv.setItem(BACK, named(Material.ARROW, "Назад к списку", NamedTextColor.YELLOW, List.of()));
        p.openInventory(inv);
    }

    private static void addHeatAndClock(Inventory inv, int seconds) {
        List<Component> heat = new ArrayList<>();
        heat.add(line("Поставь над источником тепла:", NamedTextColor.GRAY));
        heat.add(line("костёр, печь, лава, огонь или магма", NamedTextColor.WHITE));
        inv.setItem(29, named(Material.CAMPFIRE, "Нужен нагрев", NamedTextColor.GOLD, heat));

        List<Component> time = new ArrayList<>();
        time.add(line("На кухонной печи: " + (int) Math.ceil(seconds / 1.5) + " с", NamedTextColor.GREEN));
        inv.setItem(30, named(Material.CLOCK, "Время: " + seconds + " с", NamedTextColor.YELLOW, time));
    }

    /** Раз в секунду показывает следующий вариант ингредиента, если подходит несколько. */
    public static void animate() {
        counter++;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!(p.getOpenInventory().getTopInventory().getHolder() instanceof Holder h) || h.entry == null) continue;
            for (int i = 0; i < h.cycleSlots.length; i++) {
                List<Material> mats = h.cycleMats.get(i);
                if (mats.size() < 2) continue;
                h.inv.setItem(h.cycleSlots[i], stack(mats.get(counter % mats.size()), h.cycleLore.get(i)));
            }
        }
    }
}
