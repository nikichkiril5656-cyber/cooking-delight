package dev.cooking.delight;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.FoodComponent;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

public final class CustomItems {
    private final CookingDelight plugin;

    public CustomItems(CookingDelight plugin) {
        this.plugin = plugin;
    }

    /** Создаёт предмет по id (pot, board, knife) из config.yml, либо null. */
    public ItemStack create(String id) {
        ConfigurationSection s = plugin.getConfig().getConfigurationSection("items." + id);
        if (s == null) return null;
        Material m = Material.matchMaterial(s.getString("material", ""));
        if (m == null) return null;
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(MiniMessage.miniMessage().deserialize("<!italic>" + s.getString("name", id)));
        if (s.contains("lore")) {
            List<Component> lore = new ArrayList<>();
            for (String line : s.getStringList("lore")) lore.add(MiniMessage.miniMessage().deserialize("<!italic><gray>" + line));
            meta.lore(lore);
        }
        if (s.contains("nutrition") || s.contains("saturation")) {
            FoodComponent food = meta.getFood();
            food.setNutrition(s.getInt("nutrition", 1));
            food.setSaturation((float) s.getDouble("saturation", 0.5));
            meta.setFood(food);
        }
        String model = s.getString("model");
        if (model != null) {
            NamespacedKey k = NamespacedKey.fromString(model);
            if (k != null) meta.setItemModel(k);
        }
        meta.getPersistentDataContainer().set(plugin.keyItem, PersistentDataType.STRING, id);
        it.setItemMeta(meta);
        return it;
    }

    public String idOf(ItemStack it) {
        if (it == null || it.getType().isAir() || !it.hasItemMeta()) return null;
        return it.getItemMeta().getPersistentDataContainer().get(plugin.keyItem, PersistentDataType.STRING);
    }

    public Material blockMaterial(String id) {
        return Material.matchMaterial(plugin.getConfig().getString("items." + id + ".material", ""));
    }
}
