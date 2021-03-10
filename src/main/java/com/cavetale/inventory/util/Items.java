package com.cavetale.inventory.util;

import java.util.Base64;
import java.util.List;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class Items {
    private Items() { }

    public static String serialize(ItemStack item) {
        byte[] bytes = item.serializeAsBytes();
        String result = Base64.getEncoder().encodeToString(bytes);
        return result;
    }

    public static ItemStack deserialize(String serialized) {
        byte[] bytes = Base64.getDecoder().decode(serialized);
        ItemStack item = ItemStack.deserializeBytes(bytes);
        return item;
    }

    public static void give(Player player, ItemStack... items) {
        for (ItemStack drop : player.getInventory().addItem(items).values()) {
            player.getWorld().dropItem(player.getEyeLocation(), drop);
        }
    }

    public static void give(Player player, List<ItemStack> list) {
        ItemStack[] array = list.toArray(new ItemStack[0]);
        give(player, array);
    }
}
