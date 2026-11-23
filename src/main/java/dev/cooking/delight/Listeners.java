package dev.cooking.delight;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;

import java.util.HashMap;

public final class Listeners implements Listener {
    private final CookingDelight plugin;

    public Listeners(CookingDelight plugin) {
        this.plugin = plugin;
    }

    // ---------- установка / ломание ----------

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        String id = plugin.items().idOf(e.getItemInHand());
        if (id == null) return;
        if (id.equals("pot")) plugin.manager().addPot(e.getBlockPlaced());
        else if (id.equals("board")) plugin.manager().addBoard(e.getBlockPlaced());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Block b = e.getBlock();
        CookingManager m = plugin.manager();
        boolean creative = e.getPlayer().getGameMode() == GameMode.CREATIVE;
        Location at = b.getLocation().add(0.5, 0.5, 0.5);
        if (m.pot(b) != null) {
            e.setDropItems(false);
            m.removePot(b, true);
            if (!creative) b.getWorld().dropItemNaturally(at, plugin.items().create("pot"));
        } else if (m.isBoard(b)) {
            e.setDropItems(false);
            m.removeBoard(b, true);
            if (!creative) b.getWorld().dropItemNaturally(at, plugin.items().create("board"));
        }
    }

    // ---------- клик по блокам ----------

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getHand() != EquipmentSlot.HAND) return;
        Block b = e.getClickedBlock();
        if (b == null) return;
        Player p = e.getPlayer();
        ItemStack held = p.getInventory().getItemInMainHand();
        boolean holdingSomething = !held.getType().isAir();
        CookingManager m = plugin.manager();

        PotState pot = m.pot(b);
        if (pot != null) {
            if (p.isSneaking() && holdingSomething) return; // позволяем ставить блоки
            e.setCancelled(true);
            p.openInventory(pot.getInventory());
            return;
        }

        if (m.isBoard(b)) {
            if (p.isSneaking() && holdingSomething) return;
            e.setCancelled(true);
            handleBoard(p, b, held);
        }
    }

    private void handleBoard(Player p, Block b, ItemStack held) {
        CookingManager m = plugin.manager();
        boolean isKnife = "knife".equals(plugin.items().idOf(held));
        ItemDisplay display = m.findDisplay(b);
        Location at = b.getLocation().add(0.5, 0.2, 0.5);

        if (display == null) {
            if (held.getType().isAir() || isKnife) return;
            ItemStack one = held.clone();
            one.setAmount(1);
            m.placeOnBoard(b, one);
            if (p.getGameMode() != GameMode.CREATIVE) held.setAmount(held.getAmount() - 1);
            b.getWorld().playSound(at, Sound.BLOCK_WOOD_PLACE, 0.8f, 1.2f);
            return;
        }

        if (isKnife) {
            RecipeBook.CutRecipe r = plugin.recipes().cut(display.getItemStack().getType());
            if (r == null) return;
            b.getWorld().dropItemNaturally(at, new ItemStack(r.result(), r.amount()));
            display.remove();
            b.getWorld().playSound(at, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.8f, 1.5f);
            damage(p, held);
            return;
        }

        if (held.getType().isAir()) { // пустая рука забирает предмет
            HashMap<Integer, ItemStack> left = p.getInventory().addItem(display.getItemStack());
            for (ItemStack rest : left.values()) b.getWorld().dropItemNaturally(at, rest);
            display.remove();
        }
    }

    private void damage(Player p, ItemStack knife) {
        if (p.getGameMode() == GameMode.CREATIVE) return;
        ItemMeta meta = knife.getItemMeta();
        if (meta instanceof Damageable d) {
            d.setDamage(d.getDamage() + 1);
            knife.setItemMeta(d);
            if (d.getDamage() >= knife.getType().getMaxDurability()) {
                knife.setAmount(0);
                p.playSound(p.getLocation(), Sound.ENTITY_ITEM_BREAK, 1f, 1f);
            }
        }
        p.getInventory().setItemInMainHand(knife.getAmount() <= 0 ? null : knife);
    }

    // ---------- GUI кастрюли ----------

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        Inventory top = e.getView().getTopInventory();
        if (!(top.getHolder() instanceof PotState)) return;

        if (e.getAction() == InventoryAction.COLLECT_TO_CURSOR) { // иначе можно вытащить "стекло"
            e.setCancelled(true);
            return;
        }
        Inventory clicked = e.getClickedInventory();
        if (clicked == null) return;

        if (clicked.equals(top)) {
            int raw = e.getRawSlot();
            if (!PotState.isUsable(raw)) {
                e.setCancelled(true);
                return;
            }
            if (raw == PotState.OUT) { // из выходного слота только забираем
                boolean cursorFull = e.getCursor() != null && !e.getCursor().getType().isAir();
                if (cursorFull || e.getClick() == ClickType.NUMBER_KEY || e.getClick() == ClickType.SWAP_OFFHAND) {
                    e.setCancelled(true);
                }
            }
            return;
        }

        if (e.isShiftClick()) { // шифт-клик из инвентаря игрока: кладём в нужные слоты вручную
            e.setCancelled(true);
            ItemStack cur = e.getCurrentItem();
            if (cur == null || cur.getType().isAir()) return;
            ItemStack rest = PotState.addTo(top, PotState.ING, cur.clone());
            if (rest != null && plugin.recipes().containers().contains(rest.getType())) {
                rest = PotState.addTo(top, new int[]{PotState.CONT}, rest);
            }
            e.setCurrentItem(rest);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof PotState)) return;
        for (int raw : e.getRawSlots()) {
            if (raw < 27 && (!PotState.isUsable(raw) || raw == PotState.OUT)) {
                e.setCancelled(true);
                return;
            }
        }
    }

    // ---------- еда ----------

    @EventHandler
    public void onEat(PlayerItemConsumeEvent e) {
        ItemStack it = e.getItem();
        if (!it.hasItemMeta()) return;
        String id = it.getItemMeta().getPersistentDataContainer().get(plugin.keyDish, PersistentDataType.STRING);
        if (id == null) return;
        PotRecipe r = plugin.recipes().byId(id);
        if (r == null) return;
        for (PotionEffect eff : r.effects()) e.getPlayer().addPotionEffect(eff);
    }
}
