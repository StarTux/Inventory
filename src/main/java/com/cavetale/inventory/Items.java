package com.cavetale.inventory;

import java.util.Base64;
import org.bukkit.inventory.ItemStack;

public final class Items {
    private Items() { }

    public static String serialize(ItemStack item) {
        if (item == null || item.getAmount() == 0) return null;
        byte[] bytes = item.serializeAsBytes();
        String result = Base64.getEncoder().encodeToString(bytes);
        return result;
    }

    public static ItemStack deserialize(String serialized) {
        if (serialized == null) return null;
        byte[] bytes = Base64.getDecoder().decode(serialized);
        ItemStack item = ItemStack.deserializeBytes(bytes);
        return item;
    }
}
