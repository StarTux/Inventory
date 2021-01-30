package com.cavetale.inventory;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * JSONable inventory.
 */
public final class Inventory {
    List<String> inventoryItems = new ArrayList<>();

    public void store(Player player) {
        inventoryItems.clear();
        PlayerInventory playerInventory = player.getInventory();
        for (ItemStack itemStack : playerInventory) {
            inventoryItems.add(Items.serialize(itemStack));
        }
    }

    public void restore(Player player) {
        PlayerInventory playerInventory = player.getInventory();
        List<ItemStack> oldItems = new ArrayList<>();
        for (int i = 0; i < inventoryItems.size(); i += 1) {
            ItemStack itemStack = Items.deserialize(inventoryItems.get(i));
            if (itemStack != null) {
                switch (itemStack.getType()) {
                case FILLED_MAP:
                    itemStack = null;
                default: break;
                }
            }
            ItemStack oldItem = playerInventory.getItem(i);
            if (oldItem != null && oldItem.getAmount() > 0) oldItems.add(oldItem);
            playerInventory.setItem(i, itemStack);
        }
        for (ItemStack oldItem : oldItems) {
            for (ItemStack drop : player.getInventory().addItem(oldItem).values()) {
                player.getWorld().dropItem(player.getEyeLocation(), oldItem).setPickupDelay(0);
            }
        }
    }
}
