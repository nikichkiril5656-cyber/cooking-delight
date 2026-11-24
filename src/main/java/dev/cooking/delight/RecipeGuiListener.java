package dev.cooking.delight;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

public final class RecipeGuiListener implements Listener {
    private final CookingDelight plugin;

    public RecipeGuiListener(CookingDelight plugin) {
        this.plugin = plugin;
    }

    private void later(Runnable r) {
        Bukkit.getScheduler().runTask(plugin, r);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        Inventory top = e.getView().getTopInventory();
        if (!(top.getHolder() instanceof RecipeGui.Holder h)) return;
        e.setCancelled(true); // из книги ничего забрать нельзя
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (!top.equals(e.getClickedInventory())) return;
        int slot = e.getRawSlot();

        if (h.entry == null) { // каталог
            if (slot == 49) {
                later(p::closeInventory);
            } else if (slot == 45 && h.page > 0) {
                later(() -> RecipeGui.openCatalog(plugin, p, h.page - 1));
            } else if (slot == 53 && h.hasNext) {
                later(() -> RecipeGui.openCatalog(plugin, p, h.page + 1));
            } else {
                int pos = RecipeGui.indexOfListSlot(slot);
                if (pos >= 0) later(() -> RecipeGui.openEntry(plugin, p, h.page, pos));
            }
        } else if (slot == 49) { // рецепт: назад к списку
            later(() -> RecipeGui.openCatalog(plugin, p, h.page));
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof RecipeGui.Holder) e.setCancelled(true);
    }
}
