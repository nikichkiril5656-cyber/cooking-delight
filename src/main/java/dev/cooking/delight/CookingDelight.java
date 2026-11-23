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

import java.io.File;
import java.util.List;

public final class CookingDelight extends JavaPlugin {
    public NamespacedKey keyItem;
    public NamespacedKey keyDish;
    public NamespacedKey keyBoard;

    private CustomItems items;
    private RecipeBook recipes;
    private CookingManager manager;

    public CustomItems items() { return items; }
    public RecipeBook recipes() { return recipes; }
    public CookingManager manager() { return manager; }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        if (!new File(getDataFolder(), "recipes.yml").exists()) saveResource("recipes.yml", false);

        keyItem = new NamespacedKey(this, "item");
        keyDish = new NamespacedKey(this, "dish");
        keyBoard = new NamespacedKey(this, "board_display");

        items = new CustomItems(this);
        recipes = new RecipeBook(this);
        recipes.load();
        manager = new CookingManager(this);
        manager.load();

        getServer().getPluginManager().registerEvents(new Listeners(this), this);
        registerCrafting();

        getServer().getScheduler().runTaskTimer(this, manager::tick, 20L, 20L);
        getServer().getScheduler().runTaskTimer(this, manager::save, 6000L, 6000L);
    }

    @Override
    public void onDisable() {
        if (manager != null) manager.save();
    }

    private void registerCrafting() {
        ItemStack pot = items.create("pot");
        if (pot != null) {
            ShapedRecipe r = new ShapedRecipe(new NamespacedKey(this, "pot"), pot);
            r.shape("I I", "IBI", "III");
            r.setIngredient('I', Material.IRON_INGOT);
            r.setIngredient('B', Material.BOWL);
            Bukkit.addRecipe(r);
        }
        ItemStack board = items.create("board");
        if (board != null) {
            ShapedRecipe r = new ShapedRecipe(new NamespacedKey(this, "board"), board);
            r.shape(" S ", "PPP");
            r.setIngredient('S', Material.STICK);
            r.setIngredient('P', new RecipeChoice.MaterialChoice(Tag.PLANKS));
            Bukkit.addRecipe(r);
        }
        ItemStack knife = items.create("knife");
        if (knife != null) {
            ShapedRecipe r = new ShapedRecipe(new NamespacedKey(this, "knife"), knife);
            r.shape("I", "S");
            r.setIngredient('I', Material.IRON_INGOT);
            r.setIngredient('S', Material.STICK);
            Bukkit.addRecipe(r);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("cookingdelight.admin")) {
            sender.sendMessage("Нет прав.");
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage("/cooking give <pot|board|knife> [игрок] | /cooking reload");
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
        if (args.length == 1) return List.of("give", "reload");
        if (args.length == 2 && args[0].equalsIgnoreCase("give")) return List.of("pot", "board", "knife");
        return List.of();
    }
}
