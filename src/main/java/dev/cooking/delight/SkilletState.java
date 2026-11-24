package dev.cooking.delight;

import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

public final class SkilletState {
    public final Location loc;
    public ItemStack item;     // что лежит на сковороде (null = пусто)
    public double progress;    // секунды готовки с учётом ускорения

    public SkilletState(Location loc) {
        this.loc = loc;
    }
}
