package dev.cooking.delight;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import java.util.List;
import java.util.Set;

/**
 * ingredients: по одному набору допустимых материалов на слот (группа #tag даёт несколько вариантов).
 * labels: подписи слотов для книги рецептов.
 */
public record PotRecipe(String id, List<Set<Material>> ingredients, List<Component> labels, Material container,
                        ItemStack result, int time, List<PotionEffect> effects) {
}
