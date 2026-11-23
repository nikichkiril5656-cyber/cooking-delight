package dev.cooking.delight;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Light;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.block.data.Lightable;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.io.File;
import java.io.IOException;
import java.util.*;

public final class CookingManager {
    private final CookingDelight plugin;
    private final Map<String, PotState> pots = new HashMap<>();
    private final Set<String> boards = new HashSet<>();
    private final Set<Material> heat = EnumSet.noneOf(Material.class);

    public CookingManager(CookingDelight plugin) {
        this.plugin = plugin;
    }

    // ---------- реестр блоков ----------

    public static String key(Block b) {
        return b.getWorld().getName() + ";" + b.getX() + ";" + b.getY() + ";" + b.getZ();
    }

    public PotState pot(Block b) { return pots.get(key(b)); }
    public boolean isBoard(Block b) { return boards.contains(key(b)); }

    /** Ставит невидимый барьер и модель кастрюли (item_display) вместо бочки. */
    public void addPot(Block b, float yaw) {
        pots.put(key(b), new PotState(b.getLocation()));
        b.setType(Material.BARRIER, false);
        spawnPotDisplay(b, yaw);
    }

    public ItemDisplay findPotDisplay(Block b) {
        Location c = b.getLocation().add(0.5, 0.5, 0.5);
        for (ItemDisplay d : b.getWorld().getNearbyEntitiesByType(ItemDisplay.class, c, 0.3)) {
            if (d.getPersistentDataContainer().has(plugin.keyPotDisplay, PersistentDataType.BYTE)) return d;
        }
        return null;
    }

    public void spawnPotDisplay(Block b, float yaw) {
        ItemStack item = plugin.items().create("pot");
        if (item == null) return;
        Location l = b.getLocation().add(0.5, 0.5, 0.5);
        l.setYaw(yaw);
        l.setPitch(0);
        b.getWorld().spawn(l, ItemDisplay.class, d -> {
            d.setItemStack(item);
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            // у block/block в режиме fixed масштаб 0.5, компенсируем, чтобы модель была 1:1 с блоком
            d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(),
                    new Vector3f(2f, 2f, 2f), new AxisAngle4f()));
            d.getPersistentDataContainer().set(plugin.keyPotDisplay, PersistentDataType.BYTE, (byte) 1);
        });
    }
    /**
     * Доска: невидимый блок света (уровень 0, без коллизии), модель (item_display)
     * и Interaction-хитбокс для кликов. Ставится на твёрдый блок.
     */
    public void addBoard(Block b, float yaw) {
        boards.add(key(b));
        Light data = (Light) Material.LIGHT.createBlockData();
        data.setLevel(0);
        b.setBlockData(data, false);

        ItemStack model = plugin.items().create("board");
        Location c = b.getLocation().add(0.5, 0.5, 0.5);
        c.setYaw(yaw);
        c.setPitch(0);
        if (model != null) {
            b.getWorld().spawn(c, ItemDisplay.class, d -> {
                d.setItemStack(model);
                d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
                d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(),
                        new Vector3f(2f, 2f, 2f), new AxisAngle4f()));
                d.getPersistentDataContainer().set(plugin.keyBoardPart, PersistentDataType.BYTE, (byte) 1);
            });
        }
        b.getWorld().spawn(b.getLocation().add(0.5, 0.0, 0.5), Interaction.class, i -> {
            i.setInteractionWidth(0.9f);
            i.setInteractionHeight(0.15f);
            i.getPersistentDataContainer().set(plugin.keyBoardPart, PersistentDataType.BYTE, (byte) 1);
        });
    }

    private List<Entity> boardParts(Block b) {
        List<Entity> out = new ArrayList<>();
        for (Entity e : b.getWorld().getNearbyEntities(b.getLocation().add(0.5, 0.5, 0.5), 0.5, 0.5, 0.5)) {
            var pdc = e.getPersistentDataContainer();
            if (pdc.has(plugin.keyBoardPart, PersistentDataType.BYTE) || pdc.has(plugin.keyBoard, PersistentDataType.BYTE)) out.add(e);
        }
        return out;
    }

    private void cleanupBoard(Block b, boolean dropContents, boolean dropBoardItem) {
        Location at = b.getLocation().add(0.5, 0.2, 0.5);
        for (Entity e : boardParts(b)) {
            if (dropContents && e instanceof ItemDisplay d
                    && d.getPersistentDataContainer().has(plugin.keyBoard, PersistentDataType.BYTE)) {
                b.getWorld().dropItemNaturally(at, d.getItemStack());
            }
            e.remove();
        }
        Material t = b.getType();
        if (t == Material.LIGHT || t == plugin.items().blockMaterial("board")) b.setType(Material.AIR, false);
        if (dropBoardItem) {
            ItemStack item = plugin.items().create("board");
            if (item != null) b.getWorld().dropItemNaturally(at, item);
        }
    }

    public void removePot(Block b, boolean dropContents) {
        PotState p = pots.remove(key(b));
        if (p == null) return;
        ItemDisplay pd = findPotDisplay(b);
        if (pd != null) pd.remove();
        for (HumanEntity h : new ArrayList<>(p.getInventory().getViewers())) h.closeInventory();
        if (!dropContents) return;
        Location at = b.getLocation().add(0.5, 0.5, 0.5);
        List<Integer> slots = new ArrayList<>();
        for (int s : PotState.ING) slots.add(s);
        slots.add(PotState.CONT);
        slots.add(PotState.OUT);
        for (int s : slots) {
            ItemStack it = p.getInventory().getItem(s);
            if (it != null && !it.getType().isAir()) b.getWorld().dropItemNaturally(at, it);
        }
    }

    public void removeBoard(Block b, boolean dropContents) {
        boards.remove(key(b));
        cleanupBoard(b, dropContents, false);
    }

    // ---------- разделочная доска ----------

    public ItemDisplay findDisplay(Block b) {
        Location c = b.getLocation().add(0.5, 0.1, 0.5);
        for (ItemDisplay d : b.getWorld().getNearbyEntitiesByType(ItemDisplay.class, c, 0.3)) {
            if (d.getPersistentDataContainer().has(plugin.keyBoard, PersistentDataType.BYTE)) return d;
        }
        return null;
    }

    public void placeOnBoard(Block b, ItemStack one) {
        Location l = b.getLocation().add(0.5, 0.1, 0.5);
        b.getWorld().spawn(l, ItemDisplay.class, d -> {
            d.setItemStack(one);
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            d.setTransformation(new Transformation(
                    new Vector3f(0, 0, 0),
                    new AxisAngle4f((float) (-Math.PI / 2), 1, 0, 0), // лежит плашмя
                    new Vector3f(0.5f, 0.5f, 0.5f),
                    new AxisAngle4f()));
            d.getPersistentDataContainer().set(plugin.keyBoard, PersistentDataType.BYTE, (byte) 1);
        });
    }

    // ---------- кастрюля: логика ----------

    private boolean isHeated(Block below) {
        Material m = below.getType();
        if (!heat.contains(m)) return false;
        if (below.getBlockData() instanceof Lightable l) return l.isLit();
        return true;
    }

    private PotRecipe match(PotState p) {
        List<Material> have = new ArrayList<>();
        for (int s : PotState.ING) {
            ItemStack it = p.getInventory().getItem(s);
            if (it != null && !it.getType().isAir()) have.add(it.getType());
        }
        if (have.isEmpty()) return null;
        Collections.sort(have);
        ItemStack cont = p.getInventory().getItem(PotState.CONT);
        for (PotRecipe r : plugin.recipes().potRecipes()) {
            if (r.container() != null && (cont == null || cont.getType() != r.container())) continue;
            if (have.equals(r.ingredients())) return r;
        }
        return null;
    }

    public void tick() {
        Iterator<Map.Entry<String, PotState>> it = pots.entrySet().iterator();
        while (it.hasNext()) {
            PotState p = it.next().getValue();
            World w = p.loc.getWorld();
            if (w == null || !w.isChunkLoaded(p.loc.getBlockX() >> 4, p.loc.getBlockZ() >> 4)) continue;
            Block b = p.loc.getBlock();
            Material t = b.getType();
            if (t == Material.BARREL) { // старая кастрюля из прошлой версии: переносим на барьер
                b.setType(Material.BARRIER, false);
                t = Material.BARRIER;
            }
            if (t != Material.BARRIER) { // блок убрали иным способом
                it.remove();
                removePotContents(p, b);
                continue;
            }
            if (findPotDisplay(b) == null) spawnPotDisplay(b, 0f); // модель пропала, восстанавливаем
            tickPot(p, b);
        }

        Iterator<String> bi = boards.iterator();
        while (bi.hasNext()) {
            String k = bi.next();
            Block b = parse(k);
            if (b == null || !b.getWorld().isChunkLoaded(b.getX() >> 4, b.getZ() >> 4)) continue;
            // блок пропал или убрали опору: возвращаем доску и положенный на неё предмет
            if (b.getType() != Material.LIGHT || !b.getRelative(BlockFace.DOWN).getType().isSolid()) {
                bi.remove();
                cleanupBoard(b, true, true);
            }
        }
    }

    private void removePotContents(PotState p, Block b) {
        ItemDisplay pd = findPotDisplay(b);
        if (pd != null) pd.remove();
        for (HumanEntity h : new ArrayList<>(p.getInventory().getViewers())) h.closeInventory();
        int[] extra = {PotState.CONT, PotState.OUT};
        List<Integer> slots = new ArrayList<>();
        for (int s : PotState.ING) slots.add(s);
        for (int s : extra) slots.add(s);
        for (int s : slots) {
            ItemStack i = p.getInventory().getItem(s);
            if (i != null && !i.getType().isAir()) b.getWorld().dropItemNaturally(b.getLocation().add(0.5, 0.5, 0.5), i);
        }
    }

    private void tickPot(PotState p, Block b) {
        boolean heated = isHeated(b.getRelative(BlockFace.DOWN));
        PotRecipe r = match(p);
        ItemStack out = p.getInventory().getItem(PotState.OUT);
        boolean blocked = out != null && !out.getType().isAir();

        if (!heated) {
            p.progress = 0;
            p.active = null;
            p.setInfo(false, "Нет источника тепла", NamedTextColor.RED, 0);
            return;
        }
        if (r == null) {
            p.progress = 0;
            p.active = null;
            p.setInfo(true, "Нет подходящего рецепта", NamedTextColor.YELLOW, 0);
            return;
        }
        if (blocked) {
            p.setInfo(true, "Заберите готовое блюдо", NamedTextColor.GOLD, 0);
            return;
        }
        if (p.active != r) {
            p.active = r;
            p.progress = 0;
        }
        p.progress++;
        Location top = b.getLocation().add(0.5, 1.0, 0.5);
        b.getWorld().spawnParticle(Particle.SMOKE, top, 3, 0.2, 0.05, 0.2, 0.01);
        b.getWorld().playSound(top, Sound.BLOCK_BREWING_STAND_BREW, 0.3f, 1.4f);
        if (p.progress >= r.time()) {
            finish(p, r, top);
        } else {
            p.setInfo(true, "Готовка: " + p.progress + "/" + r.time() + " с", NamedTextColor.GREEN, 100 * p.progress / r.time());
        }
    }

    private void finish(PotState p, PotRecipe r, Location top) {
        for (int s : PotState.ING) shrink(p, s);
        if (r.container() != null) shrink(p, PotState.CONT);
        p.getInventory().setItem(PotState.OUT, r.result().clone());
        p.progress = 0;
        p.active = null;
        p.setInfo(true, "Заберите готовое блюдо", NamedTextColor.GOLD, 0);
        top.getWorld().playSound(top, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
    }

    private void shrink(PotState p, int slot) {
        ItemStack it = p.getInventory().getItem(slot);
        if (it == null) return;
        if (it.getAmount() <= 1) p.getInventory().setItem(slot, null);
        else it.setAmount(it.getAmount() - 1);
    }

    // ---------- сохранение ----------

    private Block parse(String k) {
        String[] a = k.split(";");
        World w = Bukkit.getWorld(a[0]);
        if (w == null) return null;
        return w.getBlockAt(Integer.parseInt(a[1]), Integer.parseInt(a[2]), Integer.parseInt(a[3]));
    }

    public void load() {
        heat.clear();
        for (String n : plugin.getConfig().getStringList("heat-sources")) {
            Material m = Material.matchMaterial(n);
            if (m != null) heat.add(m);
        }
        pots.clear();
        boards.clear();
        File f = new File(plugin.getDataFolder(), "data.yml");
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        ConfigurationSection ps = y.getConfigurationSection("pots");
        if (ps != null) {
            for (String id : ps.getKeys(false)) {
                ConfigurationSection s = ps.getConfigurationSection(id);
                if (s == null) continue;
                World w = Bukkit.getWorld(s.getString("world", ""));
                if (w == null) continue;
                Location l = new Location(w, s.getInt("x"), s.getInt("y"), s.getInt("z"));
                PotState st = new PotState(l);
                ConfigurationSection sl = s.getConfigurationSection("slots");
                if (sl != null) {
                    for (String sk : sl.getKeys(false)) st.getInventory().setItem(Integer.parseInt(sk), sl.getItemStack(sk));
                }
                pots.put(key(l.getBlock()), st);
            }
        }
        boards.addAll(y.getStringList("boards"));
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        int i = 0;
        for (PotState p : pots.values()) {
            String base = "pots." + (i++);
            y.set(base + ".world", p.loc.getWorld().getName());
            y.set(base + ".x", p.loc.getBlockX());
            y.set(base + ".y", p.loc.getBlockY());
            y.set(base + ".z", p.loc.getBlockZ());
            List<Integer> slots = new ArrayList<>();
            for (int s : PotState.ING) slots.add(s);
            slots.add(PotState.CONT);
            slots.add(PotState.OUT);
            for (int s : slots) {
                ItemStack it = p.getInventory().getItem(s);
                if (it != null && !it.getType().isAir()) y.set(base + ".slots." + s, it);
            }
        }
        y.set("boards", new ArrayList<>(boards));
        try {
            y.save(new File(plugin.getDataFolder(), "data.yml"));
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save data.yml: " + e.getMessage());
        }
    }
}
