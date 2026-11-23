package dev.cooking.delight;

import net.kyori.adventure.inventory.Book;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/** Книга рецептов: открывается командой /cooking recipe. */
public final class RecipeBookView {
    private RecipeBookView() {}

    public static void open(CookingDelight plugin, Player player) {
        List<Component> pages = new ArrayList<>();
        pages.add(Component.text("Книга рецептов\n\n", NamedTextColor.DARK_GREEN, TextDecoration.BOLD)
                .append(Component.text("1. Поставь кастрюлю над огнём: костёр, печь, лава.\n"
                        + "2. Открой её (ПКМ) и разложи ингредиенты.\n"
                        + "3. Положи посуду и подожди.\n\n"
                        + "Нож: ПКМ по доске.", NamedTextColor.BLACK)));
        for (PotRecipe r : plugin.recipes().potRecipes()) pages.add(page(r));
        player.openBook(Book.book(Component.text("Рецепты"), Component.text("CookingDelight"), pages));
    }

    private static Component page(PotRecipe r) {
        ItemStack result = r.result();
        ItemMeta meta = result.getItemMeta();
        String name = meta.hasDisplayName()
                ? PlainTextComponentSerializer.plainText().serialize(meta.displayName())
                : null;
        Component title = name != null ? Component.text(name) : Component.translatable(result.getType().translationKey());

        Component out = title.color(NamedTextColor.DARK_GREEN).decorate(TextDecoration.BOLD)
                .append(Component.newline()).append(Component.newline())
                .append(Component.text("Ингредиенты:", NamedTextColor.BLACK, TextDecoration.UNDERLINED))
                .append(Component.newline());
        for (Component label : r.labels()) {
            out = out.append(Component.text("• ", NamedTextColor.BLACK))
                    .append(label.color(NamedTextColor.DARK_GRAY)).append(Component.newline());
        }
        if (r.container() != null) {
            out = out.append(Component.text("Посуда: ", NamedTextColor.BLACK))
                    .append(Component.translatable(r.container().translationKey(), NamedTextColor.DARK_GRAY))
                    .append(Component.newline());
        }
        out = out.append(Component.text("Время: " + r.time() + " с", NamedTextColor.BLACK));
        if (meta.hasFood()) {
            out = out.append(Component.newline())
                    .append(Component.text("Сытость: " + meta.getFood().getNutrition(), NamedTextColor.BLACK));
        }
        return out;
    }
}
