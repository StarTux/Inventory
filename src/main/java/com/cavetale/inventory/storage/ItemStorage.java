package com.cavetale.inventory.storage;

import com.cavetale.inventory.InventoryPlugin;
import com.cavetale.inventory.util.Items;
import com.cavetale.mytems.Mytems;
import com.cavetale.mytems.item.farawaymap.FarawayMapTag;
import com.cavetale.mytems.util.Json;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.NonNull;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Container;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import static java.util.logging.Level.SEVERE;

/**
 * Store a simple item in a way which can be serialized easily.  The
 * ultimate goal is to store each item in a succinct and human
 * readable fashion.  When this is impossible, Base64 encoding is used
 * as a fallback option.
 */
@Data
public final class ItemStorage {
    private static final boolean DO_STORE_OPTIONAL_DATA = false;
    protected Integer slot;
    protected Integer amount;
    protected String mytems;
    protected String bukkit;
    protected String base64;
    // Vanilla Item Details
    protected InventoryStorage content;
    protected Integer damage;
    protected String name;
    protected List<String> lore;
    protected Map<String, Integer> enchants;
    protected List<Map<String, Object>> attributes;
    protected Integer color;
    protected transient boolean doStoreOptionalData = DO_STORE_OPTIONAL_DATA;

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
            final Material material = parseBukkitMaterial();
            result = new ItemStack(material);
        } else {
            return null;
        }
        if (amount != null) {
            result.setAmount(amount);
        }
        ItemMeta itemMeta = result.getItemMeta();
        if (content != null
            && itemMeta instanceof BlockStateMeta meta
            && meta.getBlockState() instanceof Container container) {
            content.restore(container.getInventory(), "ItemStorage::toItemStack");
            meta.setBlockState(container);
        }
        // Begin Optional Data
        if (damage != null && itemMeta instanceof Damageable meta) {
            meta.setDamage(damage);
        }
        if (name != null) {
            try {
                itemMeta.displayName(GsonComponentSerializer.gson().deserialize(name));
            } catch (Exception e) {
                InventoryPlugin.getInstance().getLogger().log(SEVERE, "Deserializing DisplayName " + name, e);
            }
        }
        if (enchants != null) {
            for (String key : enchants.keySet()) {
                Enchantment enchantment = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(key));
                if (enchantment == null) {
                    InventoryPlugin.getInstance().getLogger().warning("Unknown enchantment: " + key);
                    continue;
                }
                itemMeta.addEnchant(enchantment, enchants.get(key), true);
            }
        }
        if (lore != null) {
            List<Component> itemLore = new ArrayList<>();
            for (String line : lore) {
                try {
                    itemLore.add(GsonComponentSerializer.gson().deserialize(line));
                } catch (Exception e) {
                    InventoryPlugin.getInstance().getLogger().log(SEVERE, "Deserializing Lore " + line, e);
                }
            }
            itemMeta.lore(itemLore);
        }
        if (attributes != null) {
            for (Map<String, Object> serialized : attributes) {
                if (!(serialized.get("attribute") instanceof String key)) continue;
                Attribute attribute = null;
                for (Attribute it : Attribute.values()) {
                    if (it.getKey().getKey().equals(key)) {
                        attribute = it;
                        break;
                    }
                }
                if (attribute == null) {
                    InventoryPlugin.getInstance().getLogger().warning("Unknown attribute: " + key);
                    continue;
                }
                serialized.remove("attribute");
                AttributeModifier modifier;
                try {
                    modifier = AttributeModifier.deserialize(serialized);
                } catch (Exception e) {
                    InventoryPlugin.getInstance().getLogger().log(SEVERE, "Deserializing Attribute " + serialized, e);
                    continue;
                }
                itemMeta.addAttributeModifier(attribute, modifier);
            }
        }
        if (color != null && itemMeta instanceof LeatherArmorMeta meta) {
            meta.setColor(Color.fromRGB(color));
        }
        // End Optional Data
        result.setItemMeta(itemMeta);
        return result;
    }

    private Material parseBukkitMaterial() {
        if ("grass".equals(bukkit)) return Material.SHORT_GRASS;
        try {
            return Material.valueOf(bukkit.toUpperCase());
        } catch (IllegalArgumentException iae) {
            throw new IllegalStateException("Invalid bukkit material: " + this);
        }
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
        if (itemStack.isSimilar(prototype)) return;
        // Since the item will now be deconstructed to some extent,
        // make a copy!
        itemStack = itemStack.clone();
        itemStack.setAmount(1);
        ItemMeta itemMeta = itemStack.getItemMeta();
        ItemMeta protoMeta = prototype.getItemMeta();
        if (itemMeta instanceof BlockStateMeta meta && meta.getBlockState() instanceof Container container) {
            Inventory inventory = container.getInventory();
            if (!inventory.isEmpty()) {
                this.content = InventoryStorage.of(inventory);
            }
            inventory.clear();
            meta.setBlockState(container);
            if (protoMeta instanceof BlockStateMeta mm) mm.setBlockState(container);
        }
        if (doStoreOptionalData) {
            storeOptionalData(itemMeta, protoMeta);
        }
        itemStack.setItemMeta(itemMeta);
        prototype.setItemMeta(protoMeta);
        if (!itemStack.isSimilar(prototype)) {
            this.base64 = Items.serialize(itemStack);
        }
        return;
    }

    /**
     * Make more values human readable, at the risk of future
     * compatibility!  This is generally goverened by
     * doStoreOptionalData, which defaults to false.
     *
     * Comparing a generated itemStack (the prototype) with a live
     * version is extremely finnicky, so further modifications must be
     * done with care and sufficient testing!  Part of our concept is
     * that we gradually fill our own fields with data from the item.
     * Meanwhile we reset the meta of the copy to its defaults.  The
     * same happens to the prototype to enable the final isSimilar
     * check.  If the latter fails, the base64 of the modified item is
     * stored!  Thus, the deserialized result will later have to be
     * modified as well.  Use the "/inventory store" commands to test
     * this thoroughly!
     */
    private void storeOptionalData(ItemMeta itemMeta, ItemMeta protoMeta) {
        if (itemMeta instanceof Damageable damageable && damageable.hasDamage()) {
            this.damage = damageable.getDamage();
            damageable.setDamage(0);
            if (protoMeta instanceof Damageable dm) dm.setDamage(0);
        }
        if (itemMeta.hasDisplayName()) {
            try {
                name = GsonComponentSerializer.gson().serialize(itemMeta.displayName());
            } catch (Exception e) {
                InventoryPlugin.getInstance().getLogger().log(SEVERE, "Serializing DisplayName", e);
            }
            itemMeta.displayName(null);
            protoMeta.displayName(null);
        }
        if (itemMeta.hasEnchants()) {
            Map<Enchantment, Integer> itemEnchants = itemMeta.getEnchants();
            enchants = new HashMap<>();
            for (Enchantment enchantment : itemEnchants.keySet()) {
                enchants.put(enchantment.getKey().getKey(), itemEnchants.get(enchantment));
                itemMeta.removeEnchant(enchantment);
                protoMeta.removeEnchant(enchantment);
            }
        }
        if (itemMeta.hasLore()) {
            List<Component> itemLore = itemMeta.lore();
            lore = new ArrayList<>();
            for (Component line : itemLore) {
                try {
                    lore.add(GsonComponentSerializer.gson().serialize(line));
                } catch (Exception e) {
                    InventoryPlugin.getInstance().getLogger().log(SEVERE, "Serializing Lore", e);
                }
            }
            itemMeta.lore(null);
            protoMeta.lore(null);
        }
        if (itemMeta.hasAttributeModifiers()) {
            attributes = new ArrayList<>();
            for (Attribute attribute : Attribute.values()) {
                Collection<AttributeModifier> modifiers = itemMeta.getAttributeModifiers(attribute);
                if (modifiers == null || modifiers.isEmpty()) continue;
                for (AttributeModifier modifier : modifiers) {
                    Map<String, Object> serialized = modifier.serialize();
                    serialized.put("attribute", attribute.getKey().getKey());
                    attributes.add(serialized);
                }
                itemMeta.removeAttributeModifier(attribute);
                protoMeta.removeAttributeModifier(attribute);
            }
        }
        if (itemMeta instanceof LeatherArmorMeta meta) {
            Color itemColor = meta.getColor();
            if (itemColor != null && !itemColor.equals(Bukkit.getItemFactory().getDefaultLeatherColor())) {
                this.color = itemColor.asRGB();
                meta.setColor(null);
                if (protoMeta instanceof LeatherArmorMeta la) {
                    la.setColor(null);
                }
            }
        }
    }

    public String toString() {
        return Json.serialize(this);
    }

    public int getAmount() {
        return amount != null ? amount : 1;
    }
}
