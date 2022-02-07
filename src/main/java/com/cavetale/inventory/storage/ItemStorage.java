package com.cavetale.inventory.storage;

import com.cavetale.inventory.util.Items;
import com.cavetale.mytems.Mytems;
import com.cavetale.mytems.item.farawaymap.FarawayMapTag;
import com.cavetale.mytems.util.Json;
import lombok.Data;
import lombok.NonNull;
import org.bukkit.Material;
import org.bukkit.block.Container;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Store a simple item in a way which can be serialized easily.  The
 * ultimate goal is to store each item in a succinct and human
 * readable fashion.  When this is impossible, Base64 encoding is used
 * as a fallback option.
 */
@Data
public final class ItemStorage {
    protected Integer slot;
    protected Integer amount;
    protected String mytems;
    protected String bukkit;
    protected String base64;
    protected InventoryStorage content;

    public static ItemStorage of(final int slot, final ItemStack itemStack) {
        ItemStorage result = of(itemStack);
        result.slot = slot;
        return result;
    }

    public static ItemStorage of(final ItemStack itemStack) {
        ItemStorage result = new ItemStorage();
        if (itemStack != null && itemStack.getType() != Material.AIR) result.store(itemStack);
        return result;
    }

    public int getSlot() {
        return slot != null ? slot : 0;
    }

    public boolean isEmpty() {
        return mytems == null && bukkit == null && base64 == null;
    }

    public ItemStack toItemStack() {
        final ItemStack result;
        if (mytems != null) {
            result = Mytems.deserializeItem(mytems);
            if (result == null) {
                throw new IllegalStateException("Invalid mytem: " + this);
            }
        } else if (base64 != null) {
            result = Items.deserialize(base64);
        } else if (bukkit != null) {
            Material material;
            try {
                material = Material.valueOf(bukkit.toUpperCase());
            } catch (IllegalArgumentException iae) {
                throw new IllegalStateException("Invalid bukkit material: " + this);
            }
            result = new ItemStack(material);
        } else {
            return null;
        }
        if (amount != null) {
            result.setAmount(amount);
        }
        if (content != null
            && result.getItemMeta() instanceof BlockStateMeta meta
            && meta.getBlockState() instanceof Container container) {
            content.restore(container.getInventory(), "ItemStorage::toItemStack");
            meta.setBlockState(container);
            result.setItemMeta(meta);
        }
        return result;
    }

    public void store(@NonNull ItemStack itemStack) {
        this.amount = itemStack.getAmount() != 1 ? itemStack.getAmount() : null;
        Mytems key = Mytems.forItem(itemStack);
        if (key != null) {
            this.mytems = key.serializeSingleItem(itemStack);
            return;
        }
        if (itemStack.getType() == Material.FILLED_MAP) {
            FarawayMapTag tag = new FarawayMapTag();
            tag.loadMap(itemStack);
            this.mytems = Mytems.FARAWAY_MAP.serializeWithTag(tag);
            return;
        }
        this.bukkit = itemStack.getType().name().toLowerCase();
        ItemStack prototype = new ItemStack(itemStack.getType());
        // Comparing a generated itemStack (the prototype) with a live
        // version is extremely finnicky, so further modifications
        // must be done with care and sufficient testing!
        if (itemStack.isSimilar(prototype)) return;
        // We will take the item apart, so make a copy!
        itemStack = itemStack.clone();
        itemStack.setAmount(1);
        ItemMeta itemMeta = itemStack.getItemMeta();
        if (itemMeta instanceof BlockStateMeta meta && meta.getBlockState() instanceof Container container) {
            Inventory inventory = container.getInventory();
            if (!inventory.isEmpty()) {
                this.content = InventoryStorage.of(inventory);
            }
            inventory.clear();
            meta.setBlockState(container);
            prototype.editMeta(m -> {
                    if (m instanceof BlockStateMeta mm) mm.setBlockState(container);
                });
        }
        itemStack.setItemMeta(itemMeta);
        if (!itemStack.isSimilar(prototype)) {
            this.base64 = Items.serialize(itemStack);
        }
        return;
    }

    public String toString() {
        return Json.serialize(this);
    }

    public int getAmount() {
        return amount != null ? amount : 1;
    }
}
