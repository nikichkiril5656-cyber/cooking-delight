package dev.cooking.delight;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.FurnaceStartSmeltEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;

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
        if (id.equals("pot")) plugin.manager().addPot(e.getBlockPlaced(), snapYaw(e.getPlayer().getLocation().getYaw()));
        else if (id.equals("board")) plugin.manager().addBoard(e.getBlockPlaced(), snapYaw(e.getPlayer().getLocation().getYaw()));
        else if (id.equals("skillet")) plugin.manager().addSkillet(e.getBlockPlaced(), snapYaw(e.getPlayer().getLocation().getYaw()));
        else if (id.equals("stove")) {
            // кухонная печь = обычная печь с меткой в данных блока
            if (e.getBlockPlaced().getState() instanceof TileState ts) {
                ts.getPersistentDataContainer().set(plugin.keyStove, PersistentDataType.BYTE, (byte) 1);
                ts.update();
            }
            plugin.manager().addStove(e.getBlockPlaced());
        }
    }

    @EventHandler
    public void onSmeltStart(FurnaceStartSmeltEvent e) {
        if (plugin.manager().isStove(e.getBlock())) {
            e.setTotalCookTime((int) (e.getTotalCookTime() / 1.5));
        }
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
        } else if (m.skillet(b) != null) {
            e.setDropItems(false);
            m.removeSkillet(b, true);
            if (!creative) b.getWorld().dropItemNaturally(at, plugin.items().create("skillet"));
        } else if (m.isStove(b)) {
            e.setDropItems(false); // содержимое печи выпадет само
            m.removeStove(b);
            if (!creative) b.getWorld().dropItemNaturally(at, plugin.items().create("stove"));
        }
    }

    // ---------- клик по блокам ----------

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        Block b = e.getClickedBlock();
        if (b == null) return;
        Player p = e.getPlayer();

        if (e.getAction() == Action.LEFT_CLICK_BLOCK) {
            // барьер нельзя сломать в выживании, поэтому забираем кастрюлю Shift + ЛКМ
            if (plugin.manager().skillet(b) != null && p.isSneaking()) {
                e.setCancelled(true);
                plugin.manager().removeSkillet(b, true);
                b.setType(Material.AIR);
                Location sat = b.getLocation().add(0.5, 0.5, 0.5);
                if (p.getGameMode() != GameMode.CREATIVE) b.getWorld().dropItemNaturally(sat, plugin.items().create("skillet"));
                b.getWorld().playSound(sat, Sound.BLOCK_METAL_BREAK, 1f, 1f);
                return;
            }
            if (plugin.manager().pot(b) != null && p.isSneaking()) {
                e.setCancelled(true);
                plugin.manager().removePot(b, true);
                b.setType(Material.AIR);
                Location at = b.getLocation().add(0.5, 0.5, 0.5);
                if (p.getGameMode() != GameMode.CREATIVE) b.getWorld().dropItemNaturally(at, plugin.items().create("pot"));
                b.getWorld().playSound(at, Sound.BLOCK_METAL_BREAK, 1f, 1f);
            }
            return;
        }
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
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

        SkilletState sk = m.skillet(b);
        if (sk != null) {
            if (p.isSneaking() && holdingSomething) return;
            e.setCancelled(true);
            handleSkillet(p, b, sk, held);
        }
    }

    private void handleSkillet(Player p, Block b, SkilletState sk, ItemStack held) {
        Location at = b.getLocation().add(0.5, 0.3, 0.5);
        if (sk.item == null) { // кладём сырую еду
            RecipeBook.SkilletRecipe r = held.getType().isAir() ? null : plugin.recipes().skillet(held);
            if (r == null) return;
            ItemStack one = held.clone();
            one.setAmount(1);
            sk.item = one;
            sk.progress = 0;
            plugin.manager().showFood(b, one.clone());
            if (p.getGameMode() != GameMode.CREATIVE) held.setAmount(held.getAmount() - 1);
            b.getWorld().playSound(at, Sound.BLOCK_METAL_PLACE, 0.8f, 1.2f);
            return;
        }
        if (held.getType().isAir()) { // пустая рука забирает еду
            HashMap<Integer, ItemStack> left = p.getInventory().addItem(sk.item);
            for (ItemStack rest : left.values()) b.getWorld().dropItemNaturally(at, rest);
            sk.item = null;
            sk.progress = 0;
            plugin.manager().clearFood(b);
        }
    }

    // ---------- доска: клики идут через Interaction-сущность ----------

    private boolean isBoardHitbox(Entity ent) {
        return ent instanceof Interaction
                && ent.getPersistentDataContainer().has(plugin.keyBoardPart, PersistentDataType.BYTE);
    }

    @EventHandler
    public void onBoardUse(PlayerInteractEntityEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || !isBoardHitbox(e.getRightClicked())) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        handleBoard(p, e.getRightClicked().getLocation().getBlock(), p.getInventory().getItemInMainHand());
    }

    @EventHandler
    public void onBoardPunch(PrePlayerAttackEntityEvent e) {
        if (!isBoardHitbox(e.getAttacked())) return;
        e.setCancelled(true);
        Block b = e.getAttacked().getLocation().getBlock();
        if (!plugin.manager().isBoard(b)) return;
        plugin.manager().removeBoard(b, true);
        if (e.getPlayer().getGameMode() != GameMode.CREATIVE) {
            b.getWorld().dropItemNaturally(b.getLocation().add(0.5, 0.2, 0.5), plugin.items().create("board"));
        }
        b.getWorld().playSound(b.getLocation().add(0.5, 0.2, 0.5), Sound.BLOCK_WOOD_BREAK, 1f, 1f);
    }

    private static float snapYaw(float yaw) {
        return Math.round(yaw / 90f) * 90f;
    }

    private void handleBoard(Player p, Block b, ItemStack held) {
        CookingManager m = plugin.manager();
        String heldId = plugin.items().idOf(held);
        boolean isKnife = heldId != null && heldId.endsWith("_knife");
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
            RecipeBook.CutRecipe r = plugin.recipes().cut(display.getItemStack());
            if (r == null) return;
            b.getWorld().dropItemNaturally(at, r.result().clone());
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
