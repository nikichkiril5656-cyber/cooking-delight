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
            if (s.getBoolean("always-eat", false)) food.setCanAlwaysEat(true);
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

    /** Ключ предмета для рецептов: id предмета плагина либо название материала (CARROT). */
    public String keyOf(ItemStack it) {
        if (it == null || it.getType().isAir()) return null;
        String id = idOf(it);
        return id != null ? id : it.getType().name();
    }

    /** Предмет по ключу из рецепта: свой (id из config.yml) либо ванильный материал. */
    public ItemStack stackOf(String key, int amount) {
        ItemStack it = create(key);
        if (it == null) {
            Material m = Material.matchMaterial(key);
            if (m == null) return null;
            it = new ItemStack(m);
        }
        it.setAmount(Math.max(1, amount));
        return it;
    }

    public boolean isCustomId(String token) {
        return plugin.getConfig().isConfigurationSection("items." + token);
    }

    public Material blockMaterial(String id) {
        return Material.matchMaterial(plugin.getConfig().getString("items." + id + ".material", ""));
    }
}
