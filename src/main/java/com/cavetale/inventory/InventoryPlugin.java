package com.cavetale.inventory;

import com.cavetale.core.connect.ServerCategory;
import com.cavetale.inventory.gui.Gui;
import com.cavetale.inventory.mail.ItemMail;
import com.cavetale.inventory.mail.SQLItemMail;
import com.cavetale.inventory.ms.MassStorage;
import com.cavetale.inventory.ms.SQLMassStorage;
import com.cavetale.inventory.sql.SQLBackup;
import com.cavetale.inventory.sql.SQLInventory;
import com.cavetale.inventory.sql.SQLStash;
import com.winthier.sql.SQLDatabase;
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
    protected Backups backups = new Backups(this);
    protected InventoryStore inventoryStore;
    protected MassStorage massStorage;
    protected ItemMail itemMail;

    @Override
    public void onEnable() {
        if (!Bukkit.getPluginManager().isPluginEnabled("Mytems")) {
            throw new IllegalStateException("Mytems not enabled!");
        }
        instance = this;
        final boolean survival = ServerCategory.current().isSurvival();
        database.registerTables(SQLStash.class, SQLBackup.class);
        final boolean doInventoryStore = survival;
        if (doInventoryStore) {
            database.registerTable(SQLInventory.class);
            inventoryStore = new InventoryStore(this);
            inventoryStore.enable();
            getLogger().info("Inventory Store enabled");
        } else {
            getLogger().info("Inventory Store disabled");
        }
        final boolean doMassStorage = false && survival;
        if (doMassStorage) {
            database.registerTable(SQLMassStorage.class);
            massStorage = new MassStorage(this);
            massStorage.enable();
            getLogger().info("Mass Storage Store enabled");
        } else {
            getLogger().info("Mass Storage Store disabled");
        }
        final boolean doItemMail = survival;
        if (doItemMail) {
            database.registerTable(SQLItemMail.class);
            itemMail = new ItemMail(this);
            itemMail.enable();
            getLogger().info("Item Mail Store enabled");
        } else {
            getLogger().info("Item Mail Store disabled");
        }
        if (!database.createAllTables()) {
            throw new IllegalStateException("Database creation failed!");
        }
        backups.enable();
        inventoryCommand.enable();
        stashCommand.enable();
        openStashCommand.enable();
        Gui.enable(this);
    }

    @Override
    public void onDisable() {
        Gui.disable(this);
        database.waitForAsyncTask();
        database.close();
    }
}
