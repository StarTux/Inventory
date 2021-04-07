package com.cavetale.inventory;

import com.cavetale.inventory.gui.Gui;
import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;

@RequiredArgsConstructor
public final class EventListener implements Listener {
    private final InventoryPlugin plugin;

    public void enable() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    void onPluginDisable(PluginDisableEvent event) {
        if (event.getPlugin() == plugin) {
            Gui.disable(plugin);
        }
    }
}
