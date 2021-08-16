package com.cavetale.inventory;

import com.cavetale.core.command.CommandNode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;

@RequiredArgsConstructor
public final class OpenStashCommand implements TabExecutor {
    private final InventoryPlugin plugin;
    private CommandNode rootNode;

    public void enable() {
        rootNode = new CommandNode("openstash").arguments("<player>")
            .playerCaller(plugin.inventoryCommand::stash);
        plugin.getCommand("openstash").setExecutor(this);
    }

    @Override
    public boolean onCommand(final CommandSender sender, final Command command, final String alias, final String[] args) {
        return rootNode.call(sender, command, alias, args);
    }

    @Override
    public List<String> onTabComplete(final CommandSender sender, final Command command, final String alias, final String[] args) {
        return rootNode.complete(sender, command, alias, args);
    }
}
