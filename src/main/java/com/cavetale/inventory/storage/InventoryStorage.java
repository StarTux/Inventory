package com.cavetale.inventory.storage;

import com.cavetale.inventory.InventoryPlugin;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import lombok.Data;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * JSONable inventory.
 */
@Data
public final class InventoryStorage {
    private static final boolean DO_STORE_OPTIONAL_DATA = false;
    protected List<ItemStorage> items;
    protected int size;
    protected int count;
    protected transient boolean doStoreOptionalData = DO_STORE_OPTIONAL_DATA;

    public static InventoryStorage of(Inventory inventory) {
        InventoryStorage result = new InventoryStorage();
        result.store(inventory);
        return result;
    }

    /**
     * Restore this inventory storage into an actual inventory.
     * @param inventory the inventory to store the items in
     * @param name the name of this operation for debug logging
     * @return List of items that didn't fit.
     */
    public List<ItemStack> restore(Inventory inventory, String name) {
        // Remain stores the items that didn't fit at first try
        List<ItemStack> remain = new ArrayList<>();
        for (ItemStorage itemStorage : items) {
            ItemStack itemStack;
            try {
                itemStack = itemStorage.toItemStack();
            } catch (Exception e) {
                InventoryPlugin.getInstance().getLogger().log(Level.SEVERE, name + ": restore failed: " + itemStorage, e);
                continue;
            }
            if (itemStack == null) continue;
            if (itemStorage.slot < 0 || itemStorage.slot >= inventory.getSize()) {
                remain.add(itemStack);
            } else {
                ItemStack old = inventory.getItem(itemStorage.slot);
                if (old != null && old.getType() != Material.AIR) {
                    remain.add(itemStack);
                } else {
                    inventory.setItem(itemStorage.slot, itemStack);
                }
            }
        }
        ItemStack[] remainArray = remain.toArray(new ItemStack[0]);
        return List.copyOf(inventory.addItem(remainArray).values());
    }

    /**
     * Copy items from an inventory into this data structure.
     * @param inventory the inventory to copy
     */
    public void store(Inventory inventory) {
        size = inventory.getSize();
        items = new ArrayList<>(size);
        for (int slot = 0; slot < size; slot += 1) {
            ItemStack itemStack = inventory.getItem(slot);
            if (itemStack == null || itemStack.getType() == Material.AIR) continue;
            if (doStoreOptionalData) {
                ItemStorage itemStorage = new ItemStorage();
                itemStorage.slot = slot;
                itemStorage.doStoreOptionalData = true;
                itemStorage.store(itemStack);
                items.add(itemStorage);
            } else {
                items.add(ItemStorage.of(slot, itemStack));
            }
            count += itemStack.getAmount();
        }
    }

    public static void clear(Inventory inventory) {
        for (int i = 0; i < inventory.getSize(); i += 1) {
            inventory.setItem(i, null);
        }
    }

    /**
     * Create an inventory with the contents of this storage.
     * @return the inventory
     */
    public Inventory toInventory() {
        int invSize = ((size - 1) / 9 + 1) * 9;
        Inventory inventory = Bukkit.createInventory(null, invSize);
        restore(inventory, "toInventory");
        return inventory;
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }
}
