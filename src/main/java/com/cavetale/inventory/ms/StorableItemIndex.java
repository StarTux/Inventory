package com.cavetale.inventory.ms;

import com.cavetale.mytems.Mytems;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.NonNull;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public final class StorableItemIndex {
    protected final UnstorableItem unstorableItem = new UnstorableItem();
    protected final List<StorableItem> all = new ArrayList<>();

    protected final Map<Material, StorableBukkitItem> bukkitIndex = new EnumMap<>(Material.class);
    protected final Map<String, StorableBukkitItem> bukkitSqlNameMap = new HashMap<>();

    protected final Map<Mytems, StorableMytemsItem> mytemsIndex = new EnumMap<>(Mytems.class);
    protected final Map<String, StorableMytemsItem> mytemsSqlNameMap = new HashMap<>();

    protected void populate() {
        for (Material material : Material.values()) {
            if (!material.isItem()) continue;
            if (material.isLegacy()) continue;
            StorableBukkitItem value = new StorableBukkitItem(material);
            bukkitIndex.put(material, value);
            bukkitSqlNameMap.put(value.getSqlName(), value);
            all.add(value);
        }
        for (Mytems mytems : Mytems.values()) {
            StorableMytemsItem value = new StorableMytemsItem(mytems);
            mytemsIndex.put(mytems, value);
            mytemsSqlNameMap.put(value.getSqlName(), value);
            all.add(value);
        }
    }

    /**
     * Find a StorableItem which is either capable of storing the
     * given ItemStack, or not valid.
     */
    public @NonNull StorableItem get(ItemStack itemStack) {
        Mytems mytems = Mytems.forItem(itemStack);
        if (mytems != null) {
            StorableMytemsItem smi = mytemsIndex.get(mytems);
            if (smi != null && smi.canStore(itemStack)) return smi;
        }
        StorableBukkitItem sbi = bukkitIndex.get(itemStack.getType());
        if (sbi != null && sbi.canStore(itemStack)) return sbi;
        return unstorableItem;
    }

    public @NonNull StorableItem get(SQLMassStorage row) {
        switch (row.getStorageType()) {
        case BUKKIT:
            StorableBukkitItem bukkit = bukkitSqlNameMap.get(row.getName());
            return bukkit != null ? bukkit : unstorableItem;
        case MYTEMS:
            StorableMytemsItem mytems = mytemsSqlNameMap.get(row.getName());
            return mytems != null ? mytems : unstorableItem;
        case INVALID:
        default:
            return unstorableItem;
        }
    }
}
