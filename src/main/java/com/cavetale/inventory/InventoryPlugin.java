package com.cavetale.inventory;

import com.cavetale.inventory.gui.Gui;
import com.cavetale.inventory.sql.SQLBackup;
import com.cavetale.inventory.sql.SQLStash;
import com.winthier.sql.SQLDatabase;
import lombok.Getter;
import org.bukkit.plugin.java.JavaPlugin;

@Getter
public final class InventoryPlugin extends JavaPlugin {
    @Getter private static InventoryPlugin instance;
    protected InventoryCommand inventoryCommand = new InventoryCommand(this);
    protected StashCommand stashCommand = new StashCommand(this);
    protected EventListener eventListener = new EventListener(this);
    protected SQLDatabase database = new SQLDatabase(this);
    protected final Settings settings = new Settings();

    @Override
    public void onEnable() {
        instance = this;
        loadSettings();
        inventoryCommand.enable();
        stashCommand.enable();
        eventListener.enable();
        database.registerTables(SQLStash.class, SQLBackup.class);
        if (!database.createAllTables()) {
            getLogger().warning("Database creation failed!");
            setEnabled(false);
            return;
        }
        Gui.enable(this);
    }

    @Override
    public void onDisable() {
        Gui.disable(this);
        database.waitForAsyncTask();
        database.close();
    }

    void loadSettings() {
        saveDefaultConfig();
        reloadConfig();
        settings.load(getConfig());
    }
}
