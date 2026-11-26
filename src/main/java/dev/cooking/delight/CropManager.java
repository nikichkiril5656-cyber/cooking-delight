package dev.cooking.delight;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Farmland;
import org.bukkit.block.data.type.Light;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Свои культуры (томат и далее): невидимый блок света + модель (item_display) + Interaction-хитбокс.
 * Параметры каждой культуры лежат в config.yml, раздел crops.
 */
public final class CropManager {
    public static final class Crop {
        public final Location loc;
        public final String type;
        public int stage;      // -1 = дикий куст
        public double timer;

        Crop(Location loc, String type, int stage) {
            this.loc = loc;
            this.type = type;
            this.stage = stage;
        }
    }

    private final CookingDelight plugin;
    private final Map<String, Crop> crops = new HashMap<>();

    public CropManager(CookingDelight plugin) {
        this.plugin = plugin;
    }

    // ---------- настройки ----------

    private ConfigurationSection cfg(String type) {
        return plugin.getConfig().getConfigurationSection("crops." + type);
    }

    /** Какая культура растёт из этих семян (id предмета), либо null. */
    public String typeBySeeds(String seedsId) {
        ConfigurationSection all = plugin.getConfig().getConfigurationSection("crops");
        if (all == null || seedsId == null) return null;
        for (String t : all.getKeys(false)) {
            if (seedsId.equals(all.getString(t + ".seeds"))) return t;
        }
        return null;
    }

    private int ripeStage(String type) {
        ConfigurationSection s = cfg(type);
        return s == null ? 7 : s.getInt("stages", 8) - 1;
    }

    public Crop at(Block b) {
        return crops.get(CookingManager.key(b));
    }

    // ---------- модель и части ----------

    private ItemStack modelItem(String type, int stage) {
        ItemStack it = new ItemStack(Material.STICK);
        ItemMeta meta = it.getItemMeta();
        String name = stage < 0 ? type + "_wild" : type + "_stage_" + stage;
        meta.setItemModel(new NamespacedKey("cookingdelight", name));
        it.setItemMeta(meta);
        return it;
    }

    private List<Entity> parts(Block b) {
        List<Entity> out = new ArrayList<>();
        for (Entity e : b.getWorld().getNearbyEntities(b.getLocation().add(0.5, 0.5, 0.5), 0.5, 0.5, 0.5)) {
            if (e.getPersistentDataContainer().has(plugin.keyCrop, PersistentDataType.BYTE)) out.add(e);
        }
        return out;
    }

    private void removeParts(Block b) {
        for (Entity e : parts(b)) e.remove();
    }

    private void spawnParts(Block b, String type, int stage) {
        ItemStack model = modelItem(type, stage);
        b.getWorld().spawn(b.getLocation().add(0.5, 0.5, 0.5), ItemDisplay.class, d -> {
            d.setItemStack(model);
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            d.getPersistentDataContainer().set(plugin.keyCrop, PersistentDataType.BYTE, (byte) 1);
        });
        b.getWorld().spawn(b.getLocation().add(0.5, 0.0, 0.5), Interaction.class, i -> {
            i.setInteractionWidth(0.8f);
            i.setInteractionHeight(0.9f);
            i.getPersistentDataContainer().set(plugin.keyCrop, PersistentDataType.BYTE, (byte) 1);
        });
    }

    private void updateModel(Block b, Crop c) {
        for (Entity e : parts(b)) {
            if (e instanceof ItemDisplay d) d.setItemStack(modelItem(c.type, c.stage));
        }
    }

    // ---------- посадка, сбор, разрушение ----------

    /** stage = 0 для посаженного семени, -1 для дикого куста. */
    public void plant(Block b, String type, int stage) {
        Light data = (Light) Material.LIGHT.createBlockData();
        data.setLevel(0);
        b.setBlockData(data, false);
        crops.put(CookingManager.key(b), new Crop(b.getLocation(), type, stage));
        spawnParts(b, type, stage);
    }

    private void dropId(String id, int amount, Location at) {
        if (id == null || amount <= 0) return;
        ItemStack it = plugin.items().stackOf(id, amount); // свой предмет или ванильный (CARROT)
        if (it == null) return;
        at.getWorld().dropItemNaturally(at, it);
    }

    private int fruitCount(ConfigurationSection s) {
        int min = Math.max(1, s.getInt("fruit-min", 1));
        int max = Math.max(min, s.getInt("fruit-max", 3));
        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }

    private void dropOnBreak(Crop c, Block b) {
        ConfigurationSection s = cfg(c.type);
        if (s == null) return;
        Location at = b.getLocation().add(0.5, 0.4, 0.5);
        if (c.stage < 0) { // дикий куст отдаёт плод
            dropId(s.getString("fruit"), 1, at);
            return;
        }
        dropId(s.getString("seeds"), 1, at);
        if (c.stage >= ripeStage(c.type)) dropId(s.getString("fruit"), fruitCount(s), at);
    }

    private void removeCrop(Block b) {
        crops.remove(CookingManager.key(b));
        removeParts(b);
        if (b.getType() == Material.LIGHT) b.setType(Material.AIR, false);
    }

    /** Игрок сломал куст ударом. */
    public void breakByPlayer(Block b, Crop c, boolean drop) {
        if (drop) dropOnBreak(c, b);
        removeCrop(b);
        b.getWorld().playSound(b.getLocation().add(0.5, 0.3, 0.5), Sound.BLOCK_GRASS_BREAK, 1f, 1f);
    }

    /** ПКМ по кусту. Возвращает true, если что-то собрали. */
    public boolean harvest(Block b, Crop c) {
        ConfigurationSection s = cfg(c.type);
        if (s == null) return false;
        Location at = b.getLocation().add(0.5, 0.4, 0.5);
        if (c.stage < 0) { // дикий куст срываем целиком
            dropId(s.getString("fruit"), 1 + ThreadLocalRandom.current().nextInt(2), at);
            if (ThreadLocalRandom.current().nextInt(100) < 30) dropId(s.getString("seeds"), 1, at);
            removeCrop(b);
            b.getWorld().playSound(at, Sound.BLOCK_SWEET_BERRY_BUSH_PICK_BERRIES, 1f, 1f);
            return true;
        }
        if (c.stage < ripeStage(c.type)) return false;
        dropId(s.getString("fruit"), fruitCount(s), at);
        int regrow = s.getInt("regrow-stage", 4);
        if (regrow < 0) { // куст исчезает, остаются семена
            dropId(s.getString("seeds"), s.getInt("harvest-seeds", 1), at);
            removeCrop(b);
            b.getWorld().playSound(at, Sound.BLOCK_SWEET_BERRY_BUSH_PICK_BERRIES, 1f, 1f);
            return true;
        }
        c.stage = Math.min(ripeStage(c.type), regrow);
        c.timer = 0;
        updateModel(b, c);
        b.getWorld().playSound(at, Sound.BLOCK_SWEET_BERRY_BUSH_PICK_BERRIES, 1f, 1f);
        return true;
    }

    /** Костная мука и подобное: перескакиваем стадии. */
    public boolean grow(Block b, Crop c, int stages) {
        int ripe = ripeStage(c.type);
        if (c.stage < 0 || c.stage >= ripe) return false;
        c.stage = Math.min(ripe, c.stage + stages);
        c.timer = 0;
        updateModel(b, c);
        return true;
    }

    // ---------- рост ----------

    public void tick() {
        Iterator<Map.Entry<String, Crop>> it = crops.entrySet().iterator();
        while (it.hasNext()) {
            Crop c = it.next().getValue();
            World w = c.loc.getWorld();
            if (w == null || !w.isChunkLoaded(c.loc.getBlockX() >> 4, c.loc.getBlockZ() >> 4)) continue;
            Block b = c.loc.getBlock();
            Block below = b.getRelative(BlockFace.DOWN);
            boolean ok = b.getType() == Material.LIGHT
                    && (c.stage < 0 ? below.getType().isSolid() : below.getType() == Material.FARMLAND);
            if (!ok) { // блок убрали или грядку вытоптали
                it.remove();
                dropOnBreak(c, b);
                removeParts(b);
                if (b.getType() == Material.LIGHT) b.setType(Material.AIR, false);
                continue;
            }
            if (parts(b).size() < 2) { // модель или хитбокс пропали, восстанавливаем
                removeParts(b);
                spawnParts(b, c.type, c.stage);
            }
            ConfigurationSection s = cfg(c.type);
            if (s == null || c.stage < 0 || c.stage >= ripeStage(c.type)) continue;
            if (b.getLightLevel() < 9) continue;
            double speed = 1.0;
            if (!(below.getBlockData() instanceof Farmland f) || f.getMoisture() <= 0) speed = 0.5;
            c.timer += speed;
            if (c.timer >= Math.max(1, s.getInt("stage-seconds", 90))) {
                c.timer = 0;
                c.stage++;
                updateModel(b, c);
            }
        }
    }

    // ---------- дикие кусты ----------

    public void generateWild(Chunk chunk) {
        ConfigurationSection all = plugin.getConfig().getConfigurationSection("crops");
        if (all == null) return;
        Random r = ThreadLocalRandom.current();
        World w = chunk.getWorld();
        for (String type : all.getKeys(false)) {
            ConfigurationSection s = all.getConfigurationSection(type);
            if (s == null || r.nextDouble() >= s.getDouble("wild-chance", 0)) continue;
            int x = chunk.getX() * 16 + r.nextInt(16);
            int z = chunk.getZ() * 16 + r.nextInt(16);
            Block ground = w.getBlockAt(x, w.getHighestBlockYAt(x, z), z);
            Block above = ground.getRelative(BlockFace.UP);
            if (!ground.getType().isSolid() || !above.getType().isAir()) continue;
            String biome = above.getBiome().getKey().getKey();
            if (!s.getStringList("wild-biomes").contains(biome)) continue;
            plant(above, type, -1);
        }
    }

    // ---------- сохранение ----------

    public void load() {
        crops.clear();
        YamlConfiguration y = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "crops.yml"));
        ConfigurationSection cs = y.getConfigurationSection("crops");
        if (cs == null) return;
        for (String id : cs.getKeys(false)) {
            ConfigurationSection s = cs.getConfigurationSection(id);
            if (s == null) continue;
            World w = Bukkit.getWorld(s.getString("world", ""));
            if (w == null) continue;
            Location l = new Location(w, s.getInt("x"), s.getInt("y"), s.getInt("z"));
            Crop c = new Crop(l, s.getString("type", "tomato"), s.getInt("stage", 0));
            c.timer = s.getDouble("timer");
            crops.put(CookingManager.key(l.getBlock()), c);
        }
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        int i = 0;
        for (Crop c : crops.values()) {
            String base = "crops." + (i++);
            y.set(base + ".world", c.loc.getWorld().getName());
            y.set(base + ".x", c.loc.getBlockX());
            y.set(base + ".y", c.loc.getBlockY());
            y.set(base + ".z", c.loc.getBlockZ());
            y.set(base + ".type", c.type);
            y.set(base + ".stage", c.stage);
            y.set(base + ".timer", c.timer);
        }
        try {
            y.save(new File(plugin.getDataFolder(), "crops.yml"));
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save crops.yml: " + e.getMessage());
        }
    }
}
