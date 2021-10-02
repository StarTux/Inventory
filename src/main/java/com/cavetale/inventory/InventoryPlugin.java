package com.cavetale.inventory;

import com.cavetale.inventory.gui.Gui;
import com.cavetale.inventory.sql.SQLBackup;
import com.cavetale.inventory.sql.SQLStash;
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
    protected final Settings settings = new Settings();

    @Override
    public void onEnable() {
        if (!Bukkit.getPluginManager().isPluginEnabled("Mytems")) {
            throw new IllegalStateException("Mytems not enabled!");
        }
        instance = this;
        loadSettings();
        inventoryCommand.enable();
        stashCommand.enable();
        openStashCommand.enable();
        database.registerTables(SQLStash.class, SQLBackup.class);
        if (!database.createAllTables()) {
            throw new IllegalStateException("Database creation failed!");
        }
        Date then = new Date(System.currentTimeMillis() - Duration.ofDays(90).toMillis());
        database.find(SQLBackup.class)
            .lt("created", then)
            .deleteAsync(count -> getLogger().info("" + count + " backups deleted older than " + then));
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
