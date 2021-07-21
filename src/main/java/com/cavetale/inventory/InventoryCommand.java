package com.cavetale.inventory;

import com.cavetale.core.command.CommandNode;
import com.cavetale.core.command.CommandWarn;
import com.cavetale.inventory.gui.Gui;
import com.cavetale.inventory.sql.SQLBackup;
import com.cavetale.inventory.sql.SQLStash;
import com.cavetale.inventory.storage.InventoryStorage;
import com.cavetale.inventory.util.Items;
import com.cavetale.inventory.util.Json;
import com.winthier.playercache.PlayerCache;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

@RequiredArgsConstructor
public final class InventoryCommand implements TabExecutor {
    private final InventoryPlugin plugin;
    private CommandNode rootNode;
    private final TextColor red = TextColor.fromHexString("#ff0000");
    private final TextColor yellow = TextColor.fromHexString("#ffff00");
    private final TextColor gray = TextColor.fromHexString("#a0a0a0");
    private final TextColor white = TextColor.fromHexString("#ffffff");

    public void enable() {
        rootNode = new CommandNode("inventory");
        rootNode.addChild("reload").denyTabCompletion()
            .senderCaller(this::reload);
        rootNode.addChild("stash").arguments("<player>")
            .description("Peek in a player's stash (copy)")
            .playerCaller(this::stash);
        // Backup
        CommandNode backupNode = rootNode.addChild("backup")
            .description("Backup commands");
        backupNode.addChild("list").arguments("<player>")
            .description("List player inventory backups")
            .senderCaller(this::backupList);
        backupNode.addChild("restore").arguments("<id> <player> [ender]")
            .description("Restore player inventory")
            .senderCaller(this::backupRestore);
        backupNode.addChild("open").arguments("<id> [ender]")
            .description("Open an inventory backup")
            .playerCaller(this::backupOpen);
        backupNode.addChild("create").arguments("<player>")
            .description("Create an inventory backup")
            .senderCaller(this::backupCreate);
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

    boolean backupList(CommandSender sender, String[] args) {
        if (args.length != 1) return false;
        String ownerName = args[0];
        UUID ownerUuid = PlayerCache.uuidForName(args[0]);
        if (ownerUuid == null) throw new CommandWarn("Player not found: " + ownerName);
        plugin.getDatabase().find(SQLBackup.class)
            .eq("owner", ownerUuid)
            .findListAsync(list -> {
                    sender.sendMessage(Component.text("Found: " + list.size()).color(yellow));
                    for (SQLBackup row : list) {
                        sender.sendMessage(Component.text(" #" + row.getId()).color(yellow)
                                           .append(Component.text(" items:" + row.getItemCount()).color(gray))
                                           .append(Component.text(" " + row.getCreated()).color(white)));
                    }
                });
        return true;
    }

    boolean backupOpen(Player player, String[] args) {
        if (args.length != 1 && args.length != 2) return false;
        String idString = args[0];
        final String enderString = args.length >= 2 ? args[1] : null;
        final int id;
        try {
            id = Integer.parseInt(idString);
        } catch (NumberFormatException nfe) {
            throw new CommandWarn("Invalid id: " + idString);
        }
        if (enderString != null && !enderString.equals("ender")) {
            throw new CommandWarn("Invalid ender arg: " + enderString);
        }
        plugin.getDatabase().find(SQLBackup.class)
            .eq("id", id)
            .findUniqueAsync(row -> {
                    if (row == null) {
                        player.sendMessage(Component.text("Backup not found: #" + id).color(red));
                        return;
                    }
                    player.sendMessage(Component.text(" #" + row.getId()).color(yellow)
                                       .append(Component.text(" " + row.getItemCount()).color(gray))
                                       .append(Component.text(" " + row.getCreated()).color(white)));
                    SQLBackup.Tag tag = row.deserialize();
                    Inventory inventory = enderString != null
                        ? tag.getEnderChest().toInventory()
                        : tag.getInventory().toInventory();
                    player.sendMessage(Component.text("Opening...").color(yellow));
                    player.openInventory(inventory);
                });
        return true;
    }

    boolean backupCreate(CommandSender sender, String[] args) {
        if (args.length != 1) return false;
        String playerName = args[0];
        Player target = Bukkit.getPlayerExact(playerName);
        if (target == null) {
            throw new CommandWarn("Player not found: " + playerName);
        }
        SQLBackup.Tag tag = new SQLBackup.Tag();
        tag.setInventory(InventoryStorage.of(target.getInventory()));
        tag.setEnderChest(InventoryStorage.of(target.getEnderChest()));
        SQLBackup backup = new SQLBackup(target, tag);
        plugin.getDatabase().insertAsync(backup, count -> {
                if (count == 1) {
                    sender.sendMessage(Component.text("Inventory backed up: " + target.getName()).color(yellow));
                } else {
                    sender.sendMessage(Component.text("Something went wrong. See console").color(red));
                }
            });
        return true;
    }

    boolean backupRestore(CommandSender sender, String[] args) {
        if (args.length != 2 && args.length != 3) return false;
        String idString = args[0];
        String playerName = args[1];
        final String enderString = args.length >= 3 ? args[2] : null;
        final int id;
        try {
            id = Integer.parseInt(idString);
        } catch (NumberFormatException nfe) {
            throw new CommandWarn("Invalid id: " + idString);
        }
        Player target = Bukkit.getPlayerExact(playerName);
        if (target == null) {
            throw new CommandWarn("Player not found: " + playerName);
        }
        if (enderString != null && !enderString.equals("ender")) {
            throw new CommandWarn("Invalid ender arg: " + enderString);
        }
        plugin.getDatabase().find(SQLBackup.class)
            .eq("id", id)
            .findUniqueAsync(row -> {
                    if (row == null) {
                        sender.sendMessage(Component.text("Backup not found: #" + id).color(red));
                        return;
                    }
                    if (!target.isOnline()) {
                        sender.sendMessage(Component.text("Player disconnected: " + target.getName()).color(red));
                        return;
                    }
                    sender.sendMessage(Component.text(" #" + row.getId()).color(yellow)
                                       .append(Component.text(" " + row.getItemCount()).color(gray))
                                       .append(Component.text(" " + row.getCreated()).color(white)));
                    SQLBackup.Tag tag = row.deserialize();
                    List<ItemStack> drops = new ArrayList<>();
                    if (enderString == null) {
                        drops.addAll(tag.getInventory().restore(target.getInventory(), target.getName()));
                        Items.give(target, drops);
                        sender.sendMessage(Component.text("Returned inventory to " + target.getName() + ": "
                                                          + tag.getInventory().getCount() + " items, "
                                                          + drops.size() + " drops").color(yellow));
                    } else {
                        drops.addAll(tag.getEnderChest().restore(target.getEnderChest(), target.getName()));
                        Items.give(target, drops);
                        sender.sendMessage(Component.text("Returned ender chest to " + target.getName() + ": "
                                                          + tag.getEnderChest().getCount() + " items, "
                                                          + drops.size() + " drops").color(yellow));
                    }
                });
        return true;
    }

    boolean stash(Player sender, String[] args) {
        if (args.length != 1) return false;
        PlayerCache player = PlayerCache.forName(args[0]);
        if (player == null) {
            throw new CommandWarn("Player not found: " + args[0]);
        }
        SQLStash stash = plugin.database.find(SQLStash.class).eq("owner", player.uuid).findUnique();
        if (stash == null || stash.getJson() == null) {
            throw new CommandWarn(player.name + " has no stash!");
        }
        InventoryStorage inventoryStorage = Json.deserialize(stash.getJson(), InventoryStorage.class, () -> null);
        if (inventoryStorage == null) {
            throw new CommandWarn("Something went wrong! See console.");
        }
        Gui gui = new Gui(plugin, Gui.Type.STASH)
            .title(Component.text("Stash of " + player.name + " (copy)", red))
            .size(inventoryStorage.getSize());
        gui.setEditable(true);
        List<ItemStack> drops = inventoryStorage.restore(gui.getInventory(), player.getName());
        // ignoring drops here...
        gui.open(sender);
        sender.sendMessage(Component.text("Opening copy of stash of " + player.name, yellow));
        return true;
    }
}
