package com.cavetale.inventory;

import com.cavetale.core.command.CommandNode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

@RequiredArgsConstructor
public final class InventoryCommand implements TabExecutor {
    private final InventoryPlugin plugin;
    private CommandNode rootNode;

    public void enable() {
        rootNode = new CommandNode("inventory");
        rootNode.addChild("reload").denyTabCompletion()
            .senderCaller(this::reload);
        rootNode.addChild("store").denyTabCompletion()
            .playerCaller(this::store);
        rootNode.addChild("restore").denyTabCompletion()
            .playerCaller(this::restore);
        plugin.getCommand("inventory").setExecutor(this);
    }

    @Override
    public boolean onCommand(final CommandSender sender, final Command command, final String alias, final String[] args) {
        return rootNode.call(sender, command, alias, args);
    }

    @Override
    public List<String> onTabComplete(final CommandSender sender, final Command command, final String alias, final String[] args) {
        return rootNode.complete(sender, command, alias, args);
    }

    boolean reload(CommandSender sender, String[] args) {
        if (args.length != 0) return false;
        plugin.loadSettings();
        sender.sendMessage("Inventory config reloaded");
        return true;
    }

    boolean store(Player player, String[] args) {
        // if (args.length != 0) return false;
        // plugin.storeInventory(player, true);
        // player.sendMessage("Inventory stored!");
        return true;
    }

    boolean restore(Player player, String[] args) {
        // if (args.length != 0) return false;
        // plugin.restoreInventory(player);
        // player.sendMessage("Inventory restored!");
        return true;
    }
}
