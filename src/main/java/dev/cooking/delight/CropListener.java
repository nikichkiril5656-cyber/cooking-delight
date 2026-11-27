package dev.cooking.delight;

import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Levelled;
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
import org.bukkit.util.RayTraceResult;

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

    /**
     * Семена сажаются ПКМ: наземные на вспаханную землю, рис на источник воды (по взгляду).
     * Ванильную посадку (свёкла) отменяем.
     */
    @EventHandler
    public void onPlant(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        Action act = e.getAction();
        if (act != Action.RIGHT_CLICK_BLOCK && act != Action.RIGHT_CLICK_AIR) return;
        ItemStack held = e.getItem();
        String type = plugin.crops().typeBySeeds(plugin.items().idOf(held));
        if (type == null) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        Block spot;
        if (plugin.crops().isWater(type)) {
            RayTraceResult ray = p.rayTraceBlocks(5.0, FluidCollisionMode.SOURCE_ONLY);
            spot = ray == null ? null : ray.getHitBlock();
            if (spot == null || spot.getType() != Material.WATER) return;
            if (!(spot.getBlockData() instanceof Levelled lv) || lv.getLevel() != 0) return;
            if (!spot.getRelative(BlockFace.DOWN).getType().isSolid()) return;
            if (!spot.getRelative(BlockFace.UP).getType().isAir()) return;
        } else {
            if (act != Action.RIGHT_CLICK_BLOCK) return;
            Block ground = e.getClickedBlock();
            if (ground == null || e.getBlockFace() != BlockFace.UP || ground.getType() != Material.FARMLAND) return;
            spot = ground.getRelative(BlockFace.UP);
            if (!spot.getType().isAir()) return;
        }
        if (plugin.crops().at(spot) != null) return;
        plugin.crops().plant(spot, type, 0);
        if (p.getGameMode() != GameMode.CREATIVE) held.setAmount(held.getAmount() - 1);
        spot.getWorld().playSound(spot.getLocation().add(0.5, 0.3, 0.5), Sound.ITEM_CROP_PLANT, 1f, 1f);
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
