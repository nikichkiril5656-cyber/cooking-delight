package dev.cooking.delight;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.plugin.java.JavaPlugin;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class CookingDelight extends JavaPlugin {
    private static final int RECIPES_VERSION = 3;

    public NamespacedKey keyItem;
    public NamespacedKey keyDish;
    public NamespacedKey keyBoard;
    public NamespacedKey keyPotDisplay;
    public NamespacedKey keyBoardPart;
    public NamespacedKey keySkilletDisplay;
    public NamespacedKey keySkilletFood;
    public NamespacedKey keyStove;

    private CustomItems items;
    private RecipeBook recipes;
    private CookingManager manager;

    public CustomItems items() { return items; }
    public RecipeBook recipes() { return recipes; }
    public CookingManager manager() { return manager; }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        // дописываем в существующий config.yml новые предметы (например, ножи), не трогая старые значения
        getConfig().options().copyDefaults(true);
        saveConfig();

        File recipesFile = new File(getDataFolder(), "recipes.yml");
        if (recipesFile.exists() && YamlConfiguration.loadConfiguration(recipesFile).getInt("version", 1) < RECIPES_VERSION) {
            File old = new File(getDataFolder(), "recipes.old.yml");
            if (old.exists()) old.delete();
            recipesFile.renameTo(old);
            getLogger().warning("recipes.yml устарел и заменён новым, старый сохранён как recipes.old.yml");
        }
        if (!recipesFile.exists()) saveResource("recipes.yml", false);

        keyItem = new NamespacedKey(this, "item");
        keyDish = new NamespacedKey(this, "dish");
        keyBoard = new NamespacedKey(this, "board_display");
        keyPotDisplay = new NamespacedKey(this, "pot_display");
        keyBoardPart = new NamespacedKey(this, "board_part");
        keySkilletDisplay = new NamespacedKey(this, "skillet_display");
        keySkilletFood = new NamespacedKey(this, "skillet_food");
        keyStove = new NamespacedKey(this, "stove");

        items = new CustomItems(this);
        recipes = new RecipeBook(this);
        recipes.load();
        manager = new CookingManager(this);
        manager.load();

        getServer().getPluginManager().registerEvents(new Listeners(this), this);
        getServer().getPluginManager().registerEvents(new RecipeGuiListener(this), this);
        registerCrafting();

        getServer().getScheduler().runTaskTimer(this, manager::tick, 20L, 20L);
        getServer().getScheduler().runTaskTimer(this, manager::save, 6000L, 6000L);
        getServer().getScheduler().runTaskTimer(this, () -> RecipeGui.animate(), 20L, 20L);
    }

    @Override
    public void onDisable() {
        if (manager != null) manager.save();
    }

    private final List<CraftDef> crafts = new ArrayList<>();

    public List<CraftDef> crafts() { return crafts; }

    private static RecipeChoice.MaterialChoice mc(Material... m) {
        return new RecipeChoice.MaterialChoice(m);
    }

    private void craft(String id, String[] shape, Map<Character, RecipeChoice.MaterialChoice> ingredients) {
        ItemStack result = items.create(id);
        if (result == null) return;
        ShapedRecipe r = new ShapedRecipe(new NamespacedKey(this, id), result);
        r.shape(shape);
        for (Map.Entry<Character, RecipeChoice.MaterialChoice> e : ingredients.entrySet()) {
            r.setIngredient(e.getKey(), e.getValue());
        }
        Bukkit.addRecipe(r);
        crafts.add(new CraftDef(id, shape, ingredients));
    }

    private void registerCrafting() {
        crafts.clear();
        craft("pot", new String[]{"I I", "IBI", "III"},
                Map.of('I', mc(Material.IRON_INGOT), 'B', mc(Material.BOWL)));
        craft("skillet", new String[]{"II ", "II ", "  S"},
                Map.of('I', mc(Material.IRON_INGOT), 'S', mc(Material.STICK)));
        craft("stove", new String[]{"III", "BCB", "BBB"},
                Map.of('I', mc(Material.IRON_INGOT), 'B', mc(Material.BRICK), 'C', mc(Material.CAMPFIRE)));
        craft("board", new String[]{" S ", "PPP"},
                Map.of('S', mc(Material.STICK), 'P', new RecipeChoice.MaterialChoice(Tag.PLANKS)));
        knifeRecipe("flint_knife", Material.FLINT);
        knifeRecipe("iron_knife", Material.IRON_INGOT);
        knifeRecipe("golden_knife", Material.GOLD_INGOT);
        knifeRecipe("diamond_knife", Material.DIAMOND);
        knifeRecipe("netherite_knife", Material.NETHERITE_INGOT);
    }

    private void knifeRecipe(String id, Material head) {
        craft(id, new String[]{"H", "S"}, Map.of('H', mc(head), 'S', mc(Material.STICK)));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("recipe")) {
            if (sender instanceof Player p) RecipeGui.openCatalog(this, p, 0);
            else sender.sendMessage("Команда только для игроков.");
            return true;
        }
        if (!sender.hasPermission("cookingdelight.admin")) {
            sender.sendMessage("/cooking recipe: книга рецептов");
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage("/cooking recipe | /cooking give <предмет> [игрок] | /cooking reload");
            return true;
        }
        if (args[0].equalsIgnoreCase("reload")) {
            reloadConfig();
            recipes.load();
            manager.load();
            sender.sendMessage("Конфиг и рецепты перезагружены.");
            return true;
        }
        if (args[0].equalsIgnoreCase("give") && args.length >= 2) {
            Player target = args.length > 2 ? Bukkit.getPlayer(args[2])
                    : (sender instanceof Player p ? p : null);
            ItemStack it = items.create(args[1].toLowerCase());
            if (target == null || it == null) {
                sender.sendMessage("Игрок или предмет не найден.");
                return true;
            }
            target.getInventory().addItem(it);
            sender.sendMessage("Выдано: " + args[1]);
            return true;
        }
        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String a, String[] args) {
        if (args.length == 1) return s.hasPermission("cookingdelight.admin") ? List.of("recipe", "give", "reload") : List.of("recipe");
        if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            var sec = getConfig().getConfigurationSection("items");
            return sec == null ? List.of() : List.copyOf(sec.getKeys(false));
        }
        return List.of();
    }
}
