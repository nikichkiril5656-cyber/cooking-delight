package dev.cooking.delight;

import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Блюда целиком на столе: блок-барьер и модель (item_display). Порции берутся ПКМ посудой,
 * модель меняется по числу оставшихся порций. Параметры: config.yml, раздел feasts.
 */
public final class FeastManager {
    public static final class Feast {
        public final Location loc;
        public final String type;
        public int servings;

        Feast(Location loc, String type, int servings) {
            this.loc = loc;
            this.type = type;
            this.servings = servings;
        }
    }

    private final CookingDelight plugin;
    private final Map<String, Feast> feasts = new HashMap<>();

    public FeastManager(CookingDelight plugin) {
        this.plugin = plugin;
    }

    private ConfigurationSection cfg(String type) {
        return plugin.getConfig().getConfigurationSection("feasts." + type);
    }

    /** Какое блюдо ставится из этого предмета (id предмета-блока), либо null. */
    public String typeByItem(String itemId) {
        ConfigurationSection all = plugin.getConfig().getConfigurationSection("feasts");
        if (all == null || itemId == null) return null;
        for (String t : all.getKeys(false)) {
            if (itemId.equals(all.getString(t + ".block-item"))) return t;
        }
        return null;
    }

    public String blockItem(String type) {
        ConfigurationSection s = cfg(type);
        return s == null ? null : s.getString("block-item");
    }

    public int max(String type) {
        ConfigurationSection s = cfg(type);
        return s == null ? 4 : Math.max(1, s.getInt("servings", 4));
    }

    public Feast at(Block b) {
        return feasts.get(CookingManager.key(b));
    }

    // ---------- модель ----------

    private ItemStack modelItem(Feast f) {
        String name = f.servings <= 0
                ? "feast/" + f.type + "_leftovers"
                : "feast/" + f.type + "_stage_" + (max(f.type) - f.servings);
        ItemStack it = new ItemStack(Material.STICK);
        ItemMeta meta = it.getItemMeta();
        meta.setItemModel(new NamespacedKey("cookingdelight", name));
        it.setItemMeta(meta);
        return it;
    }

    private ItemDisplay findDisplay(Block b) {
        Location c = b.getLocation().add(0.5, 0.5, 0.5);
        for (ItemDisplay d : b.getWorld().getNearbyEntitiesByType(ItemDisplay.class, c, 0.3)) {
            if (d.getPersistentDataContainer().has(plugin.keyFeast, PersistentDataType.BYTE)) return d;
        }
        return null;
    }

    private void spawnDisplay(Block b, Feast f, float yaw) {
        ItemStack model = modelItem(f);
        Location l = b.getLocation().add(0.5, 0.5, 0.5);
        l.setYaw(yaw);
        l.setPitch(0);
        b.getWorld().spawn(l, ItemDisplay.class, d -> {
            d.setItemStack(model);
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            // у block/block в режиме fixed масштаб 0.5, компенсируем, чтобы модель была 1:1 с блоком
            d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(),
                    new Vector3f(2f, 2f, 2f), new AxisAngle4f()));
            d.getPersistentDataContainer().set(plugin.keyFeast, PersistentDataType.BYTE, (byte) 1);
        });
    }

    private void updateModel(Block b, Feast f) {
        ItemDisplay d = findDisplay(b);
        if (d != null) d.setItemStack(modelItem(f));
    }

    // ---------- действия ----------

    public void place(Block b, String type, float yaw) {
        Feast f = new Feast(b.getLocation(), type, max(type));
        feasts.put(CookingManager.key(b), f);
        b.setType(Material.BARRIER, false);
        spawnDisplay(b, f, yaw);
    }

    public void remove(Block b) {
        feasts.remove(CookingManager.key(b));
        ItemDisplay d = findDisplay(b);
        if (d != null) d.remove();
        if (b.getType() == Material.BARRIER) b.setType(Material.AIR, false);
    }

    /** ПКМ: берём порцию посудой. */
    public void serve(Player p, Block b, Feast f, ItemStack held) {
        ConfigurationSection s = cfg(f.type);
        if (s == null || f.servings <= 0) return;
        Material container = Material.matchMaterial(s.getString("container", ""));
        if (container != null && held.getType() != container) {
            p.sendActionBar(Component.text("Нужна посуда: ").append(Component.translatable(container.translationKey())));
            return;
        }
        ItemStack serving = plugin.items().stackOf(s.getString("serving", ""), 1);
        if (serving == null) return;
        if (container != null && p.getGameMode() != GameMode.CREATIVE) held.setAmount(held.getAmount() - 1);
        Location at = b.getLocation().add(0.5, 0.5, 0.5);
        for (ItemStack rest : p.getInventory().addItem(serving).values()) b.getWorld().dropItemNaturally(at, rest);
        f.servings--;
        b.getWorld().playSound(at, Sound.ITEM_BUNDLE_INSERT, 1f, 1.1f);
        if (f.servings <= 0 && !s.getBoolean("leftovers", true)) {
            remove(b); // у блюда без остатков (тыква) блок исчезает с последней порцией
            return;
        }
        updateModel(b, f);
    }

    // ---------- обслуживание ----------

    public void tick() {
        Iterator<Map.Entry<String, Feast>> it = feasts.entrySet().iterator();
        while (it.hasNext()) {
            Feast f = it.next().getValue();
            World w = f.loc.getWorld();
            if (w == null || !w.isChunkLoaded(f.loc.getBlockX() >> 4, f.loc.getBlockZ() >> 4)) continue;
            Block b = f.loc.getBlock();
            if (b.getType() != Material.BARRIER) { // блок убрали иным способом
                it.remove();
                ItemDisplay d = findDisplay(b);
                if (d != null) d.remove();
                continue;
            }
            if (findDisplay(b) == null) spawnDisplay(b, f, 0f);
        }
    }

    public void load() {
        feasts.clear();
        YamlConfiguration y = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "feasts.yml"));
        ConfigurationSection fs = y.getConfigurationSection("feasts");
        if (fs == null) return;
        for (String id : fs.getKeys(false)) {
            ConfigurationSection s = fs.getConfigurationSection(id);
            if (s == null) continue;
            World w = Bukkit.getWorld(s.getString("world", ""));
            if (w == null) continue;
            Location l = new Location(w, s.getInt("x"), s.getInt("y"), s.getInt("z"));
            feasts.put(CookingManager.key(l.getBlock()), new Feast(l, s.getString("type", ""), s.getInt("servings", 1)));
        }
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        int i = 0;
        for (Feast f : feasts.values()) {
            String base = "feasts." + (i++);
            y.set(base + ".world", f.loc.getWorld().getName());
            y.set(base + ".x", f.loc.getBlockX());
            y.set(base + ".y", f.loc.getBlockY());
            y.set(base + ".z", f.loc.getBlockZ());
            y.set(base + ".type", f.type);
            y.set(base + ".servings", f.servings);
        }
        try {
            y.save(new File(plugin.getDataFolder(), "feasts.yml"));
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save feasts.yml: " + e.getMessage());
        }
    }
}
