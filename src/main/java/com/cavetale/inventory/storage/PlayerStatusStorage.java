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
}
