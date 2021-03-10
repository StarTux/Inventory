package com.cavetale.inventory.util;

import org.bukkit.entity.Player;

public final class Players {
    private Players() { }

    public static int getTotalExp(Player player) {
        final int level = player.getLevel();
        final float exp = player.getExp();
        int sum = (int) ((float) player.getExp() * player.getExpToLevel());
        player.setExp(0f);
        while (player.getLevel() > 0) {
            player.setLevel(player.getLevel() - 1);
            sum += player.getExpToLevel();
        }
        player.setLevel(level);
        player.setExp(exp);
        return sum;
    }
}
