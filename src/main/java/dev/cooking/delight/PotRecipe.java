package dev.cooking.delight;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import java.util.List;

public record PotRecipe(String id, List<Material> ingredients, Material container,
                        ItemStack result, int time, List<PotionEffect> effects) {
}
