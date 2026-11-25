package dev.cooking.delight;

import org.bukkit.inventory.RecipeChoice;

import java.util.Map;

/** Верстак-рецепт предмета плагина: используется и для регистрации, и для книги рецептов. */
public record CraftDef(String id, String[] shape, Map<Character, RecipeChoice> ingredients) {
}
