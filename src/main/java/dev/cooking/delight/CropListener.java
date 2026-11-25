package dev.cooking.delight;

import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.concurrent.ThreadLocalRandom;

public final class CropListener implements Listener {
    private final CookingDelight plugin;

    public CropListener(CookingDelight plugin) {
        this.plugin = plugin;
    }

    private boolean isHitbox(Entity ent) {
        return ent instanceof Interaction
                && ent.getPersistentDataContainer().has(plugin.keyCrop, PersistentDataType.BYTE);
    }

    /** Семена сажаются на вспаханную землю ПКМ. Ванильную посадку (свёкла) отменяем. */
    @EventHandler
    public void onPlant(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getHand() != EquipmentSlot.HAND) return;
        ItemStack held = e.getItem();
        String type = plugin.crops().typeBySeeds(plugin.items().idOf(held));
        if (type == null) return;
        e.setCancelled(true);
        Block ground = e.getClickedBlock();
        if (ground == null || e.getBlockFace() != BlockFace.UP || ground.getType() != Material.FARMLAND) return;
        Block above = ground.getRelative(BlockFace.UP);
        if (!above.getType().isAir()) return;
        plugin.crops().plant(above, type, 0);
        if (e.getPlayer().getGameMode() != GameMode.CREATIVE) held.setAmount(held.getAmount() - 1);
        above.getWorld().playSound(above.getLocation().add(0.5, 0.3, 0.5), Sound.ITEM_CROP_PLANT, 1f, 1f);
    }

    @EventHandler
    public void onUse(PlayerInteractEntityEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || !isHitbox(e.getRightClicked())) return;
        e.setCancelled(true);
        Block b = e.getRightClicked().getLocation().getBlock();
        CropManager.Crop c = plugin.crops().at(b);
        if (c == null) return;
        Player p = e.getPlayer();
        ItemStack held = p.getInventory().getItemInMainHand();
        if (held.getType() == Material.BONE_MEAL
                && plugin.crops().grow(b, c, 1 + ThreadLocalRandom.current().nextInt(3))) {
            if (p.getGameMode() != GameMode.CREATIVE) held.setAmount(held.getAmount() - 1);
            b.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, b.getLocation().add(0.5, 0.5, 0.5), 8, 0.3, 0.3, 0.3);
            b.getWorld().playSound(b.getLocation().add(0.5, 0.3, 0.5), Sound.ITEM_BONE_MEAL_USE, 1f, 1f);
            return;
        }
        plugin.crops().harvest(b, c);
    }

    @EventHandler
    public void onPunch(PrePlayerAttackEntityEvent e) {
        if (!isHitbox(e.getAttacked())) return;
        e.setCancelled(true);
        Block b = e.getAttacked().getLocation().getBlock();
        CropManager.Crop c = plugin.crops().at(b);
        if (c == null) return;
        plugin.crops().breakByPlayer(b, c, e.getPlayer().getGameMode() != GameMode.CREATIVE);
    }

    /** Дикие кусты появляются в новых чанках подходящих биомов. */
    @EventHandler
    public void onChunkLoad(ChunkLoadEvent e) {
        if (!e.isNewChunk()) return;
        Chunk chunk = e.getChunk();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (chunk.isLoaded()) plugin.crops().generateWild(chunk);
        });
    }
}
