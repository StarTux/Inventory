package com.cavetale.inventory;

import com.cavetale.core.connect.ServerCategory;
import com.cavetale.inventory.gui.Gui;
import com.cavetale.inventory.mail.ItemMail;
import com.cavetale.inventory.mail.SQLItemMail;
import com.cavetale.inventory.sql.SQLBackup;
import com.cavetale.inventory.sql.SQLInventory;
import com.cavetale.inventory.sql.SQLStash;
import com.cavetale.inventory.sql.SQLTrack;
import com.winthier.sql.SQLDatabase;
import java.util.List;
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
    protected Backups backups;
    protected InventoryStore inventoryStore;
    protected final ItemMail itemMail = new ItemMail(this);
    protected final DutyCommand dutyCommand = new DutyCommand(this);

    @Override
    public void onEnable() {
        if (!Bukkit.getPluginManager().isPluginEnabled("Mytems")) {
            throw new IllegalStateException("Mytems not enabled!");
        }
        instance = this;
        database.registerTables(List.of(SQLStash.class,
                                        SQLBackup.class,
                                        SQLInventory.class,
                                        SQLItemMail.class,
                                        SQLTrack.class));
        if (!database.createAllTables()) {
            throw new IllegalStateException("Database creation failed!");
        }
        if (ServerCategory.current().isSurvival()) {
            inventoryStore = new InventoryStore(this);
            inventoryStore.enable();
            backups = new Backups(this);
            backups.enable();
        }
        itemMail.enable();
        inventoryCommand.enable();
        stashCommand.enable();
        openStashCommand.enable();
        dutyCommand.enable();
        Gui.enable(this);
    }

    @Override
    public void onDisable() {
        if (inventoryStore != null) {
            inventoryStore.disable();
        }
        Gui.disable(this);
        database.waitForAsyncTask();
        database.close();
    }

    protected static SQLDatabase database() {
        return instance.database;
    }

    protected static InventoryPlugin instance() {
        return instance;
    }
}
