package dev.cooking.delight;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

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
