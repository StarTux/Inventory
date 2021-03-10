package com.cavetale.inventory;

import com.cavetale.inventory.gui.Gui;
import com.cavetale.inventory.sql.SQLStash;
import com.winthier.sql.SQLDatabase;
import lombok.Getter;
import org.bukkit.plugin.java.JavaPlugin;

public final class InventoryPlugin extends JavaPlugin {
    @Getter private static InventoryPlugin instance;
    InventoryCommand inventoryCommand = new InventoryCommand(this);
    StashCommand stashCommand = new StashCommand(this);
    EventListener eventListener = new EventListener(this);
    SQLDatabase database = new SQLDatabase(this);
    final Settings settings = new Settings();

    @Override
    public void onEnable() {
        instance = this;
        loadSettings();
        inventoryCommand.enable();
        stashCommand.enable();
        eventListener.enable();
        database.registerTables(SQLStash.class);
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
    }

    void loadSettings() {
        saveDefaultConfig();
        reloadConfig();
        settings.load(getConfig());
    }
}
