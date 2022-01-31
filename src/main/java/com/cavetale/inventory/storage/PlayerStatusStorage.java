package com.cavetale.inventory.storage;

import lombok.Data;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;

@Data
public final class PlayerStatusStorage {
    protected Integer level;
    protected Float exp;
    protected Double health;
    protected Integer foodLevel;
    protected Float saturation;
    // Hotbar slot?
    // Potion effects?
    // Fire, freeze

    public void store(Player player) {
        level = player.getLevel();
        exp = player.getExp();
        health = player.getHealth();
        foodLevel = player.getFoodLevel();
        saturation = player.getSaturation();
    }

    public void restore(Player player) {
        if (level != null) player.setLevel(level);
        if (exp != null) player.setExp(exp);
        if (health != null) player.setHealth(Math.max(0, Math.min(health, player.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue())));
        if (foodLevel != null) player.setFoodLevel(foodLevel);
        if (saturation != null) player.setSaturation(saturation);
    }

    public static int getXpNeededForNextLevel(int level) {
        if (level >= 30) return 112 + (level - 30) * 9;
        if (level >= 15) return 37 + (level - 15) * 5;
        return 7 + level * 2;
    }
}
