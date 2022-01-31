package com.cavetale.inventory;

import com.cavetale.core.command.AbstractCommand;
import com.cavetale.core.command.CommandArgCompleter;
import com.cavetale.core.command.CommandNode;
import com.cavetale.core.command.CommandWarn;
import com.cavetale.inventory.gui.Gui;
import com.cavetale.inventory.sql.SQLBackup;
import com.cavetale.inventory.sql.SQLStash;
import com.cavetale.inventory.storage.InventoryStorage;
import com.cavetale.inventory.util.Items;
import com.cavetale.inventory.util.Json;
import com.winthier.playercache.PlayerCache;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import static net.kyori.adventure.text.Component.empty;
import static net.kyori.adventure.text.Component.join;
import static net.kyori.adventure.text.Component.newline;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.JoinConfiguration.noSeparators;
import static net.kyori.adventure.text.JoinConfiguration.separator;
import static net.kyori.adventure.text.format.NamedTextColor.*;
import static net.kyori.adventure.text.format.TextDecoration.*;

public final class InventoryCommand extends AbstractCommand<InventoryPlugin> {
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("YY/MM/DD hh:mm");

    protected InventoryCommand(final InventoryPlugin plugin) {
        super(plugin, "inventory");
    }

    @Override
    protected void onEnable() {
        rootNode.addChild("stash").arguments("<player>")
            .description("Peek in a player's stash (copy)")
            .playerCaller(this::stash);
        // Backup
        CommandNode backupNode = rootNode.addChild("backup")
            .description("Backup commands");
        backupNode.addChild("list").arguments("<player>")
            .completers(CommandArgCompleter.NULL)
            .description("List player inventory backups")
            .senderCaller(this::backupList);
        backupNode.addChild("restore").arguments("<id> <player>")
            .completers(CommandArgCompleter.integer(i -> i > 0),
                        CommandArgCompleter.NULL)
            .description("Restore player inventory")
            .senderCaller(this::backupRestore);
        backupNode.addChild("open").arguments("<id>")
            .completers(CommandArgCompleter.integer(i -> i > 0))
            .description("Open an inventory backup")
            .playerCaller(this::backupOpen);
        backupNode.addChild("create").arguments("<player> [type] [comment]")
            .completers(CommandArgCompleter.NULL,
                        CommandArgCompleter.enumLowerList(SQLBackup.Type.class),
                        CommandArgCompleter.EMPTY,
                        CommandArgCompleter.REPEAT)
            .description("Create an inventory backup")
            .senderCaller(this::backupCreate);
    }

    protected boolean backupList(CommandSender sender, String[] args) {
        if (args.length != 1) return false;
        String ownerName = args[0];
        UUID ownerUuid = PlayerCache.uuidForName(args[0]);
        if (ownerUuid == null) throw new CommandWarn("Player not found: " + ownerName);
        plugin.getDatabase().find(SQLBackup.class)
            .eq("owner", ownerUuid)
            .findListAsync(list -> {
                    sender.sendMessage(text("Found: " + list.size(), YELLOW));
                    List<Component> lines = new ArrayList<>();
                    for (SQLBackup row : list) {
                        String cmd = "/inventory backup restore " + row.getId() + " " + ownerName;
                        lines.add(join(noSeparators(),
                                       text("#" + row.getId(), YELLOW),
                                       text(" " + row.getTypeEnum().shorthand, WHITE),
                                       text(" items:" + row.getItemCount(), GRAY),
                                       text(" " + DATE_FORMAT.format(row.getCreated()), WHITE),
                                       (row.getComment() != null
                                        ? text(" " + row.getComment(), GRAY, ITALIC)
                                        : empty()))
                                  .clickEvent(ClickEvent.suggestCommand(cmd))
                                  .hoverEvent(HoverEvent.showText(text(cmd, YELLOW))));
                    }
                    sender.sendMessage(join(separator(newline()), lines));
                });
        return true;
    }

    protected boolean backupOpen(Player player, String[] args) {
        if (args.length != 1) return false;
        String idString = args[0];
        final int id;
        try {
            id = Integer.parseInt(idString);
        } catch (NumberFormatException nfe) {
            throw new CommandWarn("Invalid id: " + idString);
        }
        plugin.getDatabase().find(SQLBackup.class)
            .eq("id", id)
            .findUniqueAsync(row -> {
                    if (row == null) {
                        player.sendMessage(text("Backup not found: #" + id, RED));
                        return;
                    }
                    player.sendMessage(text(" #" + row.getId(), YELLOW)
                                       .append(text(" " + row.getItemCount(), GRAY))
                                       .append(text(" " + row.getCreated(), WHITE)));
                    SQLBackup.Tag tag = row.deserialize();
                    Inventory inventory = tag.getInventory(row.getTypeEnum()).toInventory();
                    player.sendMessage(text("Opening...", YELLOW));
                    player.openInventory(inventory);
                });
        return true;
    }

    protected boolean backupCreate(CommandSender sender, String[] args) {
        if (args.length < 1) return false;
        String playerName = args[0];
        Player target = Bukkit.getPlayerExact(playerName);
        if (target == null) {
            throw new CommandWarn("Player not found: " + playerName);
        }
        SQLBackup.Type backupType = args.length >= 2
            ? SQLBackup.Type.of(args[1])
            : SQLBackup.Type.INVENTORY;
        if (backupType == null) {
            throw new CommandWarn("Unknown backup type: " + args[1]);
        }
        SQLBackup.Tag tag = new SQLBackup.Tag();
        switch (backupType) {
        case INVENTORY:
            tag.setInventory(InventoryStorage.of(target.getInventory()));
            break;
        case ENDER_CHEST:
            tag.setEnderChest(InventoryStorage.of(target.getEnderChest()));
            break;
        default:
            throw new CommandWarn("Backup type not implemented: " + backupType);
        }
        SQLBackup backup = new SQLBackup(target, backupType, tag);
        if (args.length > 2) {
            backup.setComment(String.join(" ", Arrays.copyOfRange(args, 2, args.length)));
        }
        plugin.getDatabase().insertAsync(backup, count -> {
                if (count == 1) {
                    sender.sendMessage(text("Inventory backed up: " + target.getName(), YELLOW));
                } else {
                    sender.sendMessage(text("Something went wrong. See console", RED));
                }
            });
        return true;
    }

    protected boolean backupRestore(CommandSender sender, String[] args) {
        if (args.length != 2) return false;
        String idString = args[0];
        String playerName = args[1];
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
        plugin.getDatabase().find(SQLBackup.class)
            .eq("id", id)
            .findUniqueAsync(row -> {
                    if (row == null) {
                        sender.sendMessage(text("Backup not found: #" + id, RED));
                        return;
                    }
                    if (!target.isOnline()) {
                        sender.sendMessage(text("Player disconnected: " + target.getName(), RED));
                        return;
                    }
                    sender.sendMessage(text(" #" + row.getId(), YELLOW)
                                       .append(text(" " + row.getItemCount(), GRAY))
                                       .append(text(" " + row.getCreated(), WHITE)));
                    SQLBackup.Tag tag = row.deserialize();
                    List<ItemStack> drops = new ArrayList<>();
                    switch (row.getTypeEnum()) {
                    case INVENTORY:
                        drops.addAll(tag.getInventory().restore(target.getInventory(), target.getName()));
                        Items.give(target, drops);
                        sender.sendMessage(text("Returned inventory to " + target.getName() + ": "
                                                + tag.getInventory().getCount() + " items, "
                                                + drops.size() + " drops", YELLOW));
                        break;
                    case ENDER_CHEST:
                        drops.addAll(tag.getEnderChest().restore(target.getEnderChest(), target.getName()));
                        Items.give(target, drops);
                        sender.sendMessage(text("Returned ender chest to " + target.getName() + ": "
                                                + tag.getEnderChest().getCount() + " items, "
                                                + drops.size() + " drops", YELLOW));
                        break;
                    default:
                        throw new CommandWarn("Backup type not implemented: " + row.getTypeEnum());
                    }
                });
        return true;
    }

    protected boolean stash(Player sender, String[] args) {
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
            .title(text("Stash of " + player.name + " (copy)", RED))
            .size(inventoryStorage.getSize());
        gui.setEditable(true);
        List<ItemStack> drops = inventoryStorage.restore(gui.getInventory(), player.getName());
        // ignoring drops here...
        gui.open(sender);
        sender.sendMessage(text("Opening copy of stash of " + player.name, YELLOW));
        return true;
    }
}
