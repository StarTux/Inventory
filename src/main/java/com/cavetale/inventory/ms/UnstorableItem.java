package com.cavetale.inventory.ms;

import java.util.Set;
import org.bukkit.inventory.ItemStack;

public final class UnstorableItem implements StorableItem {
    @Override
    public Set<String> getNames() {
        return Set.of();
    }

    @Override
    public StorageType getStorageType() {
        return StorageType.INVALID;
    }

    @Override
    public String getSqlName() {
        return "air";
    }

    @Override
    public boolean canStore(ItemStack itemStack) {
        return false;
    }

    @Override
    public boolean isValid() {
        return false;
    }

    @Override
    public ItemStack createItemStack(int amount) {
        throw new IllegalStateException("Cannot create unstorable item");
    }
}
