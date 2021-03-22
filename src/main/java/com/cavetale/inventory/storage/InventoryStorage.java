package com.cavetale.inventory.storage;

import com.cavetale.inventory.InventoryPlugin;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * JSONable inventory.
 */
@Data
public final class InventoryStorage {
    List<ItemStorage> items;
    int size;
    int count;

    public static InventoryStorage of(Inventory inventory) {
        InventoryStorage result = new InventoryStorage();
        result.store(inventory);
        return result;
    }

    /**
     * Restore this inventory storage into an actual inventory.
     * @return List of items that didn't fit.
     */
    public List<ItemStack> restore(Inventory inventory, String name) {
        List<ItemStack> drops = new ArrayList<>();
        for (ItemStorage itemStorage : items) {
            ItemStack itemStack;
            try {
                itemStack = itemStorage.toItemStack();
            } catch (Exception e) {
                InventoryPlugin.getInstance().getLogger().warning(name + ": restore failed: " + itemStorage);
                e.printStackTrace();
                continue;
            }
            if (itemStack == null) continue;
            if (itemStorage.slot < 0 || itemStorage.slot >= inventory.getSize()) {
                drops.add(itemStack);
            } else {
                ItemStack old = inventory.getItem(itemStorage.slot);
                if (old != null && old.getAmount() != 0) {
                    drops.add(itemStack);
                } else {
                    inventory.setItem(itemStorage.slot, itemStack);
                }
            }
        }
        return drops;
    }

    public void store(Inventory inventory) {
        size = inventory.getSize();
        items = new ArrayList<>(size);
        for (int slot = 0; slot < size; slot += 1) {
            ItemStack itemStack = inventory.getItem(slot);
            if (itemStack == null || itemStack.getAmount() == 0) continue;
            items.add(ItemStorage.of(slot, itemStack));
            count += 1;
        }
    }

    public Inventory toInventory() {
        int invSize = ((size - 1) / 9 + 1) * 9;
        Inventory inventory = Bukkit.createInventory(null, invSize);
        restore(inventory, "toInventory");
        return inventory;
    }
}
