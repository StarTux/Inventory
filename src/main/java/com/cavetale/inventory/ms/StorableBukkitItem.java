package com.cavetale.inventory.ms;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.Getter;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

@Getter
public final class StorableBukkitItem implements StorableItem {
    protected final Material material;
    protected final String sqlName;
    protected final List<ItemStack> prototypes = new ArrayList<>();
    protected final Set<String> names = new HashSet<>();

    protected StorableBukkitItem(final Material material) {
        this.material = material;
        this.sqlName = material.name().toLowerCase();
        ItemStack prototype = new ItemStack(material);
        prototypes.add(prototype);
        names.add(prototype.getI18NDisplayName());
    }

    @Override
    public StorageType getStorageType() {
        return StorageType.BUKKIT;
    }

    @Override
    public boolean canStore(ItemStack itemStack) {
        for (ItemStack prototype : prototypes) {
            if (prototype.isSimilar(itemStack)) return true;
        }
        return false;
    }

    @Override
    public boolean isValid() {
        return true;
    }

    @Override
    public ItemStack createItemStack(int amount) {
        return new ItemStack(material, amount);
    }
}
