package com.cavetale.inventory;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;

@RequiredArgsConstructor
public final class InventoryCommand implements TabExecutor {
    private final InventoryPlugin plugin;

    public void enable() {
        plugin.getCommand("inventory").setExecutor(this);
    }

    @Override
    public boolean onCommand(final CommandSender sender, final Command command, final String alias, final String[] args) {
        if (args.length == 0) return false;
        switch (args[0]) {
        case "reload":
            if (args.length != 1) return false;
            plugin.loadConf();
            sender.sendMessage("Inventory config reloaded");
            return true;
        default: return false;
        }
    }

    @Override
    public List<String> onTabComplete(final CommandSender sender, final Command command, final String alias, final String[] args) {
        return null;
    }
}
