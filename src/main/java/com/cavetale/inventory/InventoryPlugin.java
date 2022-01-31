package com.cavetale.inventory;

import com.cavetale.inventory.gui.Gui;
import com.cavetale.inventory.sql.SQLBackup;
import com.cavetale.inventory.sql.SQLInventory;
import com.cavetale.inventory.sql.SQLStash;
import com.winthier.connect.Connect;
import com.winthier.sql.SQLDatabase;
import java.time.Duration;
import java.util.Date;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

@Getter
public final class InventoryPlugin extends JavaPlugin {
    @Getter private static InventoryPlugin instance;
    protected InventoryCommand inventoryCommand = new InventoryCommand(this);
    protected StashCommand stashCommand = new StashCommand(this);
    protected OpenStashCommand openStashCommand = new OpenStashCommand(this);
    protected SQLDatabase database = new SQLDatabase(this);
    protected InventoryStore inventoryStore;

    @Override
    public void onEnable() {
        if (!Bukkit.getPluginManager().isPluginEnabled("Mytems")) {
            throw new IllegalStateException("Mytems not enabled!");
        }
        instance = this;
        final boolean doInventoryStore = Connect.getInstance().getServerName().equals("beta")
            || Connect.getInstance().getServerName().equals("alpha");
        if (doInventoryStore) {
            database.registerTables(SQLStash.class, SQLBackup.class, SQLInventory.class);
            inventoryStore = new InventoryStore(this);
            inventoryStore.enable();
            getLogger().info("Inventory Store enabled");
        } else {
            database.registerTables(SQLStash.class, SQLBackup.class);
            getLogger().info("Inventory Store disabled");
        }
        if (!database.createAllTables()) {
            throw new IllegalStateException("Database creation failed!");
        }
        inventoryCommand.enable();
        stashCommand.enable();
        openStashCommand.enable();
        Gui.enable(this);
        deleteOldDatabaseRows();
    }

    @Override
    public void onDisable() {
        Gui.disable(this);
        database.waitForAsyncTask();
        database.close();
    }

    protected void deleteOldDatabaseRows() {
        Date then = new Date(System.currentTimeMillis() - Duration.ofDays(90).toMillis());
        database.find(SQLBackup.class)
            .lt("created", then)
            .deleteAsync(count -> {
                    if (count <= 0) return;
                    getLogger().info("Deleted " + count + " deleted older than " + then);
                });
        if (inventoryStore != null) {
            database.find(SQLInventory.class)
                .isNotNull("claimed")
                .lt("claimed", then)
                .deleteAsync(count -> {
                        if (count <= 0) return;
                        getLogger().info("Deleted " + count + " inventories claimed before " + then);
                    });
        }
    }
}
