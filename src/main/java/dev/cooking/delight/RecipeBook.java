package dev.cooking.delight;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.FoodComponent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.io.File;
import java.util.*;

public final class RecipeBook {
    public record CutRecipe(ItemStack result) {}
    public record SkilletRecipe(String input, ItemStack result, int time) {}

    private final CookingDelight plugin;
    private final List<PotRecipe> potRecipes = new ArrayList<>();
    private final Map<String, PotRecipe> byId = new HashMap<>();
    private final Map<String, CutRecipe> cuts = new LinkedHashMap<>();
    private final Set<Material> containers = EnumSet.noneOf(Material.class);
    private final Map<String, SkilletRecipe> skillet = new LinkedHashMap<>();
    private final Map<String, Set<String>> tags = new HashMap<>();
    private final Map<String, String> tagNames = new HashMap<>();

    public RecipeBook(CookingDelight plugin) {
        this.plugin = plugin;
    }

    public List<PotRecipe> potRecipes() { return potRecipes; }
    public PotRecipe byId(String id) { return byId.get(id); }
    public CutRecipe cut(ItemStack it) { return cuts.get(plugin.items().keyOf(it)); }
    public Map<String, CutRecipe> cuts() { return cuts; }
    public Set<Material> containers() { return containers; }
    public SkilletRecipe skillet(ItemStack it) { return skillet.get(plugin.items().keyOf(it)); }
    public Map<String, SkilletRecipe> skilletRecipes() { return skillet; }

    /** Ключ из рецепта: свой предмет (id из config.yml) либо материал. null, если не найден. */
    private String resolveKey(String raw) {
        String n = raw.trim();
        if (plugin.items().isCustomId(n)) return n;
        Material m = Material.matchMaterial(n);
        return m == null ? null : m.name();
    }

    private Component keyName(String key) {
        ItemStack it = plugin.items().stackOf(key, 1);
        if (it == null) return Component.text(key);
        ItemMeta meta = it.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            return Component.text(PlainTextComponentSerializer.plainText().serialize(meta.displayName()));
        }
        return Component.translatable(it.getType().translationKey());
    }

    private Component tagLabel(String key, Set<String> items) {
        String custom = tagNames.get(key);
        if (custom != null) return Component.text(custom);
        if (items.size() > 3) return Component.text(key);
        List<Component> names = new ArrayList<>();
        for (String k : items) names.add(keyName(k));
        return Component.join(JoinConfiguration.separator(Component.text(" / ")), names);
    }

    public void load() {
        potRecipes.clear();
        byId.clear();
        cuts.clear();
        containers.clear();
        skillet.clear();
        tags.clear();
        tagNames.clear();
        YamlConfiguration y = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "recipes.yml"));
        MiniMessage mm = MiniMessage.miniMessage();

        ConfigurationSection ts = y.getConfigurationSection("tags");
        if (ts != null) {
            for (String key : ts.getKeys(false)) {
                Set<String> set = new LinkedHashSet<>();
                for (String n : ts.getStringList(key)) {
                    String k = resolveKey(n);
                    if (k == null) plugin.getLogger().warning("Tag '" + key + "': unknown item " + n);
                    else set.add(k);
                }
                tags.put(key, set);
            }
        }
        ConfigurationSection tn = y.getConfigurationSection("tag-names");
        if (tn != null) for (String key : tn.getKeys(false)) tagNames.put(key, tn.getString(key, key));

        ConfigurationSection pot = y.getConfigurationSection("pot");
        if (pot != null) {
            for (String id : pot.getKeys(false)) {
                ConfigurationSection s = pot.getConfigurationSection(id);
                if (s == null) continue;
                try {
                    List<Set<String>> slots = new ArrayList<>();
                    List<Component> labels = new ArrayList<>();
                    for (String raw : s.getStringList("ingredients")) {
                        String n = raw.trim();
                        if (n.startsWith("#")) {
                            String key = n.substring(1);
                            Set<String> set = tags.get(key);
                            if (set == null || set.isEmpty()) throw new IllegalArgumentException("unknown or empty tag " + n);
                            slots.add(set);
                            labels.add(tagLabel(key, set));
                        } else {
                            String k = resolveKey(n);
                            if (k == null) throw new IllegalArgumentException("unknown item " + n);
                            slots.add(new LinkedHashSet<>(List.of(k)));
                            labels.add(keyName(k));
                        }
                    }
                    if (slots.isEmpty() || slots.size() > PotState.ING.length)
                        throw new IllegalArgumentException("ingredients must be 1.." + PotState.ING.length);

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
                    if (rs.contains("nutrition") || rs.contains("saturation")) {
                        FoodComponent food = meta.getFood();
                        food.setNutrition(rs.getInt("nutrition", food.getNutrition()));
                        food.setSaturation((float) rs.getDouble("saturation", food.getSaturation()));
                        meta.setFood(food);
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

                    PotRecipe r = new PotRecipe(id, slots, labels, container, result, Math.max(1, s.getInt("time", 10)), effects);
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
                String ik = resolveKey(in);
                String ok = resolveKey(cut.getString(in + ".result", ""));
                ItemStack out = ok == null ? null : plugin.items().stackOf(ok, cut.getInt(in + ".amount", 1));
                if (ik == null || out == null) {
                    plugin.getLogger().warning("Cutting recipe '" + in + "' skipped");
                    continue;
                }
                cuts.put(ik, new CutRecipe(out));
            }
        }
        ConfigurationSection sk = y.getConfigurationSection("skillet");
        if (sk != null) {
            for (String in : sk.getKeys(false)) {
                String ik = resolveKey(in);
                String ok = resolveKey(sk.getString(in + ".result", ""));
                ItemStack out = ok == null ? null : plugin.items().stackOf(ok, 1);
                if (ik == null || out == null) {
                    plugin.getLogger().warning("Skillet recipe '" + in + "' skipped");
                    continue;
                }
                skillet.put(ik, new SkilletRecipe(ik, out, Math.max(1, sk.getInt(in + ".time", 10))));
            }
        }
        plugin.getLogger().info("Loaded " + potRecipes.size() + " pot recipes, " + skillet.size() + " skillet recipes, "
                + cuts.size() + " cutting recipes");
    }
}
