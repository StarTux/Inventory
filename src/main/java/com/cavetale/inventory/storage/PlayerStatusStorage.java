package com.cavetale.inventory.storage;

import com.cavetale.inventory.InventoryPlugin;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

@Data
public final class PlayerStatusStorage {
    protected int exp;
    protected double health;
    protected int foodLevel;
    protected float saturation;
    protected int fireTicks;
    protected int freezeTicks;
    protected int hotbarSlot;
    protected List<Effect> potionEffects;

    @AllArgsConstructor @NoArgsConstructor
    private static final class Effect {
        private String type;
        private int duration;
        private int amplifier;
        private boolean ambient;
        private boolean particles;
        private boolean icon;

        public static Effect of(PotionEffect potionEffect) {
            return new Effect(potionEffect.getType().getKey().getKey(),
                              potionEffect.getDuration(),
                              potionEffect.getAmplifier(),
                              potionEffect.isAmbient(),
                              potionEffect.hasParticles(),
                              potionEffect.hasIcon());
        }

        public PotionEffect toPotionEffect() {
            final NamespacedKey namespacedKey = NamespacedKey.minecraft(type);
            final PotionEffectType potionEffectType = Registry.EFFECT.get(namespacedKey);
            if (potionEffectType == null) return null;
            return new PotionEffect(potionEffectType, duration, amplifier, ambient, particles, icon);
        }

        public boolean giveTo(Player player) {
            PotionEffect potionEffect = toPotionEffect();
            if (potionEffect == null) return false;
            player.addPotionEffect(potionEffect);
            return true;
        }
    }

    public static PlayerStatusStorage of(Player player) {
        PlayerStatusStorage result = new PlayerStatusStorage();
        result.store(player);
        return result;
    }

    public void store(Player player) {
        exp = expOf(player);
        health = player.getHealth();
        foodLevel = player.getFoodLevel();
        saturation = player.getSaturation();
        fireTicks = player.getFireTicks();
        freezeTicks = player.getFreezeTicks();
        hotbarSlot = player.getInventory().getHeldItemSlot();
        for (PotionEffect potionEffect : player.getActivePotionEffects()) {
            if (potionEffects == null) potionEffects = new ArrayList<>();
            potionEffects.add(Effect.of(potionEffect));
        }
    }

    public void restore(Player player) {
        addExp(player, exp);
        player.setFoodLevel(foodLevel);
        player.setSaturation(saturation);
        Bukkit.getScheduler().runTaskLater(InventoryPlugin.getInstance(), () -> {
                double max = player.getAttribute(Attribute.MAX_HEALTH).getValue();
                player.setHealth(Math.max(0, Math.min(health, max)));
            }, 1L);
        if (potionEffects != null) {
            for (Effect effect : potionEffects) {
                effect.giveTo(player);
            }
        }
        player.setFireTicks(fireTicks);
        player.setFreezeTicks(freezeTicks);
        player.getInventory().setHeldItemSlot(hotbarSlot);
    }

    public static void clear(Player player) {
        player.setLevel(0);
        player.setExp(0.0f);
        player.setFireTicks(0);
        player.setFreezeTicks(0);
        for (PotionEffect potionEffect : List.copyOf(player.getActivePotionEffects())) {
            player.removePotionEffect(potionEffect.getType());
        }
    }

    /**
     * Calculate the exp cost for gaining one level.
     * Source: https://minecraft.fandom.com/wiki/Experience#Leveling_up
     *
     * @param level the current level
     * @return the amount of exp
     */
    public static int getExpRequired(int level) {
        if (level >= 30) return 112 + (level - 30) * 9;
        if (level >= 15) return 37 + (level - 15) * 5;
        return 7 + level * 2;
    }

    public static int expOf(Player player) {
        int result = 0;
        int level = player.getLevel();
        for (int i = 0; i < level; i += 1) {
            result += getExpRequired(i);
        }
        result += (int) Math.round(player.getExp() * (float) getExpRequired(level));
        return result;
    }

    public static void addExp(Player player, int xp) {
        xp += expOf(player);
        int level = 0;
        while (true) {
            int req = getExpRequired(level);
            if (req > xp) break;
            xp -= req;
            level += 1;
        }
        player.setLevel(level);
        player.setExp((float) xp / (float) getExpRequired(level));
    }
}
