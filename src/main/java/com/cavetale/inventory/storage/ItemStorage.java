package com.cavetale.inventory.storage;

import com.cavetale.inventory.util.Items;
import com.cavetale.mytems.Mytems;
import com.cavetale.mytems.item.farawaymap.FarawayMapTag;
import com.cavetale.mytems.util.Json;
import lombok.Data;
import lombok.NonNull;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

@Data
public final class ItemStorage {
    protected int slot;
    protected String base64;
    protected String bukkit;
    protected String mytems;
    protected Integer amount;

    public static ItemStorage of(final int slot, final ItemStack itemStack) {
        ItemStorage result = new ItemStorage();
        if (itemStack != null) result.store(itemStack);
        result.slot = slot;
        return result;
    }

    public ItemStack toItemStack() {
        ItemStack result;
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
        return result;
    }

    public void store(@NonNull ItemStack itemStack) {
        amount = itemStack.getAmount() != 1 ? itemStack.getAmount() : null;
        Mytems key = Mytems.forItem(itemStack);
        if (key != null) {
            mytems = key.serializeSingleItem(itemStack);
        } else if (itemStack.getType() == Material.FILLED_MAP) {
            FarawayMapTag tag = new FarawayMapTag();
            tag.loadMap(itemStack);
            mytems = Mytems.FARAWAY_MAP.serializeWithTag(tag);
        } else if (!itemStack.isSimilar(new ItemStack(itemStack.getType()))) {
            base64 = Items.serialize(itemStack);
            bukkit = itemStack.getType().name().toLowerCase();
        } else {
            bukkit = itemStack.getType().name().toLowerCase();
        }
    }

    public String toString() {
        return Json.serialize(this);
    }

    public int getAmount() {
        return amount != null ? amount : 1;
    }
}
