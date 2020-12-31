package com.cavetale.inventory;

import com.winthier.sql.SQLDatabase;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class InventoryPlugin extends JavaPlugin {
    @Getter private static InventoryPlugin instance;
    InventoryCommand inventoryCommand = new InventoryCommand(this);
    EventListener eventListener = new EventListener(this);
    SQLDatabase database = new SQLDatabase(this);
    boolean doStore;
    boolean debug;

    @Override
    public void onEnable() {
        instance = this;
        reloadConfig();
        saveDefaultConfig();
        loadConf();
        inventoryCommand.enable();
        eventListener.enable();
        database.registerTables(SQLInventory.class);
        database.createAllTables();
        for (Player player : Bukkit.getOnlinePlayers()) {
            enter(player);
        }
    }

    @Override
    public void onDisable() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            exit(player);
        }
    }

    public void enter(Player player) {
    }

    public void exit(Player player) {
        if (doStore) {
            storeInventory(player);
        }
    }

    void loadConf() {
        doStore = getConfig().getBoolean("Store");
        debug = getConfig().getBoolean("DebugMode");
        getLogger().info("Store=" + doStore + " DebugMode=" + debug);
    }

    public void storeInventory(Player player) {
        Inventory inventory = new Inventory();
        inventory.store(player);
        SQLInventory row = new SQLInventory(player.getUniqueId(), Json.serialize(inventory));
        if (isEnabled()) {
            database.saveAsync(row, unused -> {
                    if (debug) getLogger().info("Inventory stored: " + player.getName());
                });
        } else {
            database.save(row);
        }
    }

    public void restoreInventory(Player player) {
        database.find(SQLInventory.class).findUniqueAsync(row -> {
                if (row == null) {
                    if (debug) getLogger().info("Inventory not found: " + player.getName());
                }
                Inventory inventory = Json.deserialize(row.json, Inventory.class, () -> null);
                if (inventory == null) {
                    if (debug) getLogger().warning("Inventory invalid: " + player.getName() + ": " + row.json);
                }
                inventory.restore(player);
                if (debug) getLogger().info("Inventory restored: " + player.getName());
            });
    }
}
