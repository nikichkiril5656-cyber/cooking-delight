package dev.cooking.delight;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
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
import java.util.List;

/**
 * Книга рецептов в виде GUI. Фон каждого экрана рисует ресурспак (глиф в заголовке окна),
 * поэтому слоты ниже расставлены по рамкам из текстур book_*.png.
 */
public final class RecipeGui {
    private RecipeGui() {}

    public static final int[] LIST_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43};
    private static final int BACK = 49;
    private static final int[] CRAFT_GRID = {10, 11, 12, 19, 20, 21, 28, 29, 30};
    private static final int CRAFT_OUT = 25;
    private static final int[] POT_GRID = {10, 11, 12, 19, 20, 21};
    private static final int POT_HEAT = 28, POT_TIME = 29, POT_CONT = 32, POT_OUT = 34;
    private static final int SIMPLE_IN = 20, SIMPLE_OUT = 24, SIMPLE_HEAT = 37, SIMPLE_TIME = 38;
    private static final int CUT_IN = 20, CUT_OUT = 24, CUT_TOOL = 33;

    private static final Key FONT = Key.key("cookingdelight", "gui");
    private static final TextColor TITLE_COLOR = TextColor.color(0x404040);

    private static int counter; // для смены вариантов ингредиентов раз в секунду

    private enum Mode {
        CATALOG("\ue001"), CRAFT("\ue002"), POT("\ue003"), SIMPLE("\ue004"), CUTTING("\ue005");
        final String glyph;
        Mode(String glyph) { this.glyph = glyph; }
    }

    /** Запись каталога: предмет с верстак-рецептом, блюдо кастрюли, блюдо сковороды или нарезка. */
    public record Entry(String id, Component name, ItemStack icon, String kind,
                        PotRecipe pot, CraftDef craft, RecipeBook.SkilletRecipe skillet,
                        Material cutInput, RecipeBook.CutRecipe cut) {}

    public static final class Holder implements InventoryHolder {
        final Entry entry;   // null = каталог
        final int page;
        boolean hasNext;
        Inventory inv;
        int[] cycleSlots = new int[0];
        List<List<ItemStack>> cycleOpts = new ArrayList<>();
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

    /** Заголовок: сдвиг влево, фон-глиф, возврат назад и обычный текст поверх. */
    private static Component title(Mode m, Component text) {
        return Component.empty()
                .append(Component.text("\uf808" + m.glyph).font(FONT).color(NamedTextColor.WHITE))
                .append(Component.text("\uf809").font(FONT))
                .append(text.color(TITLE_COLOR));
    }

    private static Component line(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }

    private static ItemStack named(Material m, String text, NamedTextColor color, List<Component> lore) {
        return relabel(new ItemStack(m), text, color, lore);
    }

    private static ItemStack relabel(ItemStack it, String text, NamedTextColor color, List<Component> lore) {
        ItemMeta meta = it.getItemMeta();
        meta.displayName(line(text, color));
        if (!lore.isEmpty()) meta.lore(lore);
        it.setItemMeta(meta);
        return it;
    }

    /** Название предмета: своё имя простым текстом либо перевод названия материала. */
    private static Component nameOf(ItemStack it) {
        ItemMeta meta = it.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            return Component.text(PlainTextComponentSerializer.plainText().serialize(meta.displayName()));
        }
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

    private static ItemStack stack(ItemStack base, List<Component> lore) {
        ItemStack it = base.clone();
        it.setAmount(1);
        if (!lore.isEmpty()) {
            ItemMeta meta = it.getItemMeta();
            meta.lore(lore);
            it.setItemMeta(meta);
        }
        return it;
    }

    private static List<ItemStack> optionsOf(RecipeChoice choice) {
        List<ItemStack> out = new ArrayList<>();
        if (choice instanceof RecipeChoice.MaterialChoice mc) {
            for (Material m : mc.getChoices()) out.add(new ItemStack(m));
        } else if (choice instanceof RecipeChoice.ExactChoice ec) {
            for (ItemStack s : ec.getChoices()) out.add(s.clone());
        }
        return out;
    }

    private static List<ItemStack> stacksOf(Iterable<Material> mats) {
        List<ItemStack> out = new ArrayList<>();
        for (Material m : mats) out.add(new ItemStack(m));
        return out;
    }

    private static List<Component> optionsLore(List<ItemStack> opts) {
        List<Component> l = new ArrayList<>();
        if (opts.size() <= 1) return l;
        l.add(line("Подходит любой из:", NamedTextColor.GRAY));
        int n = 0;
        for (ItemStack s : opts) {
            if (n++ >= 8) {
                l.add(line("…", NamedTextColor.GRAY));
                break;
            }
            l.add(Component.text("• ", NamedTextColor.DARK_GRAY)
                    .append(nameOf(s).color(NamedTextColor.WHITE))
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
            out.add(new Entry(c.id(), nameOf(icon), icon, "Предмет", null, c, null, null, null));
        }
        for (PotRecipe r : plugin.recipes().potRecipes()) {
            out.add(new Entry(r.id(), nameOf(r.result()), r.result().clone(), "Кастрюля", r, null, null, null, null));
        }
        for (RecipeBook.SkilletRecipe sr : plugin.recipes().skilletRecipes().values()) {
            ItemStack icon = new ItemStack(sr.result());
            out.add(new Entry("skillet_" + sr.input().name().toLowerCase(), nameOf(icon), icon, "Сковорода",
                    null, null, sr, null, null));
        }
        for (var e : plugin.recipes().cuts().entrySet()) {
            RecipeBook.CutRecipe cr = e.getValue();
            ItemStack icon = new ItemStack(cr.result(), cr.amount());
            out.add(new Entry("cut_" + e.getKey().name().toLowerCase(), nameOf(icon), icon, "Разделочная доска",
                    null, null, null, e.getKey(), cr));
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
                title(Mode.CATALOG, Component.text("Книга рецептов (" + (pg + 1) + "/" + pages + ")")));
        h.inv = inv;
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
        Mode mode = e.craft() != null ? Mode.CRAFT
                : e.pot() != null ? Mode.POT
                : e.skillet() != null ? Mode.SIMPLE : Mode.CUTTING;
        Holder h = new Holder(e, fromPage);
        Inventory inv = Bukkit.createInventory(h, 54, title(mode, e.name()));
        h.inv = inv;

        List<Integer> slots = new ArrayList<>();
        List<List<ItemStack>> opts = new ArrayList<>();
        List<List<Component>> lores = new ArrayList<>();

        if (mode == Mode.CRAFT) {
            String[] shape = e.craft().shape();
            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < 3; col++) {
                    if (row >= shape.length || col >= shape[row].length()) continue;
                    RecipeChoice choice = e.craft().ingredients().get(shape[row].charAt(col));
                    if (choice == null) continue;
                    List<ItemStack> o = optionsOf(choice);
                    if (o.isEmpty()) continue;
                    slots.add(CRAFT_GRID[row * 3 + col]);
                    opts.add(o);
                    lores.add(optionsLore(o));
                }
            }
            inv.setItem(CRAFT_OUT, e.icon().clone());
        } else if (mode == Mode.POT) {
            PotRecipe r = e.pot();
            for (int i = 0; i < r.ingredients().size() && i < POT_GRID.length; i++) {
                List<ItemStack> o = stacksOf(r.ingredients().get(i));
                slots.add(POT_GRID[i]);
                opts.add(o);
                lores.add(optionsLore(o));
            }
            if (r.container() != null) {
                inv.setItem(POT_CONT, named(r.container(), "Нужна посуда", NamedTextColor.GRAY, List.of()));
            }
            addHeatAndClock(inv, POT_HEAT, POT_TIME, r.time());
            List<Component> lore = new ArrayList<>();
            ItemMeta meta = r.result().getItemMeta();
            if (meta != null && meta.hasFood()) {
                lore.add(line("Сытость: " + meta.getFood().getNutrition(), NamedTextColor.GREEN));
            }
            inv.setItem(POT_OUT, lore.isEmpty() ? r.result().clone() : withLore(r.result(), lore));
        } else if (mode == Mode.SIMPLE) {
            RecipeBook.SkilletRecipe r = e.skillet();
            slots.add(SIMPLE_IN);
            opts.add(new ArrayList<>(List.of(new ItemStack(r.input()))));
            lores.add(new ArrayList<>());
            addHeatAndClock(inv, SIMPLE_HEAT, SIMPLE_TIME, r.time());
            inv.setItem(SIMPLE_OUT, new ItemStack(r.result()));
        } else {
            slots.add(CUT_IN);
            opts.add(new ArrayList<>(List.of(new ItemStack(e.cutInput()))));
            lores.add(new ArrayList<>());
            inv.setItem(CUT_OUT, new ItemStack(e.cut().result(), e.cut().amount()));
            ItemStack knife = plugin.items().create("iron_knife");
            if (knife != null) {
                inv.setItem(CUT_TOOL, relabel(knife, "Нужен нож", NamedTextColor.GRAY,
                        List.of(line("Положи предмет на доску", NamedTextColor.WHITE),
                                line("и режь любым ножом (ПКМ)", NamedTextColor.WHITE))));
            }
        }

        h.cycleSlots = slots.stream().mapToInt(Integer::intValue).toArray();
        h.cycleOpts = opts;
        h.cycleLore = lores;
        for (int i = 0; i < h.cycleSlots.length; i++) {
            inv.setItem(h.cycleSlots[i], stack(opts.get(i).get(0), lores.get(i)));
        }

        inv.setItem(BACK, named(Material.ARROW, "Назад к списку", NamedTextColor.YELLOW, List.of()));
        p.openInventory(inv);
    }

    private static void addHeatAndClock(Inventory inv, int heatSlot, int timeSlot, int seconds) {
        List<Component> heat = new ArrayList<>();
        heat.add(line("Поставь над источником тепла:", NamedTextColor.GRAY));
        heat.add(line("костёр, печь, лава, огонь или магма", NamedTextColor.WHITE));
        inv.setItem(heatSlot, named(Material.CAMPFIRE, "Нужен нагрев", NamedTextColor.GOLD, heat));

        List<Component> time = new ArrayList<>();
        time.add(line("На кухонной печи: " + (int) Math.ceil(seconds / 1.5) + " с", NamedTextColor.GREEN));
        inv.setItem(timeSlot, named(Material.CLOCK, "Время: " + seconds + " с", NamedTextColor.YELLOW, time));
    }

    /** Раз в секунду показывает следующий вариант ингредиента, если подходит несколько. */
    public static void animate() {
        counter++;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!(p.getOpenInventory().getTopInventory().getHolder() instanceof Holder h) || h.entry == null) continue;
            for (int i = 0; i < h.cycleSlots.length; i++) {
                List<ItemStack> o = h.cycleOpts.get(i);
                if (o.size() < 2) continue;
                h.inv.setItem(h.cycleSlots[i], stack(o.get(counter % o.size()), h.cycleLore.get(i)));
            }
        }
    }
}
