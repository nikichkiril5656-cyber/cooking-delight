package dev.cooking.delight;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.io.File;
import java.util.*;

public final class RecipeBook {
    public record CutRecipe(Material result, int amount) {}

    private final CookingDelight plugin;
    private final List<PotRecipe> potRecipes = new ArrayList<>();
    private final Map<String, PotRecipe> byId = new HashMap<>();
    private final Map<Material, CutRecipe> cuts = new EnumMap<>(Material.class);
    private final Set<Material> containers = EnumSet.noneOf(Material.class);

    public RecipeBook(CookingDelight plugin) {
        this.plugin = plugin;
    }

    public List<PotRecipe> potRecipes() { return potRecipes; }
    public PotRecipe byId(String id) { return byId.get(id); }
    public CutRecipe cut(Material m) { return cuts.get(m); }
    public Set<Material> containers() { return containers; }

    public void load() {
        potRecipes.clear();
        byId.clear();
        cuts.clear();
        containers.clear();
        YamlConfiguration y = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "recipes.yml"));
        MiniMessage mm = MiniMessage.miniMessage();

        ConfigurationSection pot = y.getConfigurationSection("pot");
        if (pot != null) {
            for (String id : pot.getKeys(false)) {
                ConfigurationSection s = pot.getConfigurationSection(id);
                if (s == null) continue;
                try {
                    List<Material> ing = new ArrayList<>();
                    for (String n : s.getStringList("ingredients")) {
                        Material m = Material.matchMaterial(n);
                        if (m == null) throw new IllegalArgumentException("unknown material " + n);
                        ing.add(m);
                    }
                    if (ing.isEmpty() || ing.size() > PotState.ING.length)
                        throw new IllegalArgumentException("ingredients must be 1.." + PotState.ING.length);
                    Collections.sort(ing);

                    Material container = null;
                    if (s.contains("container")) {
                        container = Material.matchMaterial(s.getString("container", ""));
                        if (container == null) throw new IllegalArgumentException("unknown container");
                        containers.add(container);
                    }

                    ConfigurationSection rs = s.getConfigurationSection("result");
                    if (rs == null) throw new IllegalArgumentException("no result");
                    Material rm = Material.matchMaterial(rs.getString("material", ""));
                    if (rm == null) throw new IllegalArgumentException("unknown result material");
                    ItemStack result = new ItemStack(rm, Math.max(1, rs.getInt("amount", 1)));
                    ItemMeta meta = result.getItemMeta();
                    if (rs.contains("name")) meta.displayName(mm.deserialize("<!italic>" + rs.getString("name")));
                    if (rs.contains("model")) {
                        NamespacedKey k = NamespacedKey.fromString(rs.getString("model", ""));
                        if (k != null) meta.setItemModel(k);
                    }
                    meta.getPersistentDataContainer().set(plugin.keyDish, PersistentDataType.STRING, id);
                    result.setItemMeta(meta);

                    List<PotionEffect> effects = new ArrayList<>();
                    for (String e : s.getStringList("effects")) {
                        String[] a = e.split(":");
                        PotionEffectType t = PotionEffectType.getByKey(NamespacedKey.minecraft(a[0].toLowerCase(Locale.ROOT)));
                        if (t == null) throw new IllegalArgumentException("unknown effect " + a[0]);
                        int sec = a.length > 1 ? Integer.parseInt(a[1]) : 30;
                        int amp = a.length > 2 ? Integer.parseInt(a[2]) : 0;
                        effects.add(new PotionEffect(t, sec * 20, amp));
                    }

                    PotRecipe r = new PotRecipe(id, ing, container, result, Math.max(1, s.getInt("time", 10)), effects);
                    potRecipes.add(r);
                    byId.put(id, r);
                } catch (Exception ex) {
                    plugin.getLogger().warning("Recipe '" + id + "' skipped: " + ex.getMessage());
                }
            }
        }

        ConfigurationSection cut = y.getConfigurationSection("cutting");
        if (cut != null) {
            for (String in : cut.getKeys(false)) {
                Material im = Material.matchMaterial(in);
                Material om = Material.matchMaterial(cut.getString(in + ".result", ""));
                if (im == null || om == null) {
                    plugin.getLogger().warning("Cutting recipe '" + in + "' skipped");
                    continue;
                }
                cuts.put(im, new CutRecipe(om, Math.max(1, cut.getInt(in + ".amount", 1))));
            }
        }
        plugin.getLogger().info("Loaded " + potRecipes.size() + " pot recipes, " + cuts.size() + " cutting recipes");
    }
}
