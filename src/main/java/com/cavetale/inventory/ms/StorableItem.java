package com.cavetale.inventory.ms;

import java.util.Set;
import org.bukkit.inventory.ItemStack;

public sealed interface StorableItem permits UnstorableItem, StorableBukkitItem, StorableMytemsItem {
    Set<String> getNames();
    StorageType getStorageType();
    String getSqlName();

    /**
     * This function is called with the exception that the itemStack
     * is valid (not air) and has the same type (material, mytems) as
     * the StorableItem.  It only needs to establish whether this item
     * can be boiled down to a SQLMassStorage object (true), or it
     * contains extraneous data.
     * @param itemStack the ItemStack
     * @return true or false
     */
    boolean canStore(ItemStack itemStack);

    boolean isValid();

    ItemStack createItemStack(int amount);
}
