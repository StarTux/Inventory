package com.cavetale.inventory;

import org.bukkit.configuration.ConfigurationSection;

public final class Settings {
    boolean store;
    boolean remove;
    boolean restore;
    boolean debug;

    void load(ConfigurationSection config) {
        store = config.getBoolean("Store");
        remove = config.getBoolean("Remove");
        restore = config.getBoolean("Restore");
        debug = config.getBoolean("Debug");
    }
}
