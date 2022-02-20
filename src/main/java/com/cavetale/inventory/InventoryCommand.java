package com.cavetale.inventory;

import com.cavetale.core.command.AbstractCommand;
import com.cavetale.core.command.CommandArgCompleter;
import com.cavetale.core.command.CommandNode;
import com.cavetale.core.command.CommandWarn;
import com.cavetale.core.util.Json;
import com.cavetale.inventory.gui.Gui;
import com.cavetale.inventory.sql.SQLBackup;
import com.cavetale.inventory.sql.SQLStash;
import com.cavetale.inventory.storage.InventoryStorage;
import com.cavetale.inventory.storage.ItemStorage;
import com.cavetale.inventory.util.Items;
import com.winthier.playercache.PlayerCache;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.List;
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
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.JoinConfiguration.noSeparators;
import static net.kyori.adventure.text.format.NamedTextColor.*;
import static net.kyori.adventure.text.format.TextDecoration.*;

public final class InventoryCommand extends AbstractCommand<InventoryPlugin> {
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yy/MM/dd hh:mm");

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
        // Store
        CommandNode storeNode = rootNode.addChild("store")
            .description("Player inventory storage commands");
        storeNode.addChild("testitem").denyTabCompletion()
            .description("Serialize item in hand")
            .playerCaller(this::storeTestItem);
        storeNode.addChild("dupeitem").denyTabCompletion()
            .description("Duplicate item in hand")
            .playerCaller(this::storeDupeItem);
        storeNode.addChild("dupeitem64").denyTabCompletion()
            .description("Duplicate base64 item in hand")
            .playerCaller(this::storeDupeItem64);
    }

    protected boolean backupList(CommandSender sender, String[] args) {
        if (args.length != 1) return false;
        String ownerName = args[0];
        PlayerCache owner = PlayerCache.forArg(args[0]);
        if (owner == null) throw new CommandWarn("Player not found: " + ownerName);
        plugin.backups.find(owner.uuid, list -> {
                sender.sendMessage(text("Found: " + list.size(), YELLOW));
                for (SQLBackup row : list) {
                    String restoreCmd = "/inventory backup restore " + row.getId() + " " + ownerName;
                    String openCmd = "/inventory backup open " + row.getId();
                    sender.sendMessage(join(noSeparators(),
                                            text("#" + row.getId(), YELLOW)
                                            .clickEvent(ClickEvent.suggestCommand(restoreCmd))
                                            .hoverEvent(HoverEvent.showText(text(restoreCmd, YELLOW))),
                                            text(" " + row.getTypeEnum().shorthand, WHITE),
                                            text(" items:" + row.getItemCount(), GRAY),
                                            text(" " + DATE_FORMAT.format(row.getCreated()), WHITE),
                                            Component.space(),
                                            (row.getComment() != null
                                             ? text(row.getComment(), GRAY, ITALIC)
                                             : empty()))
                                       .clickEvent(ClickEvent.suggestCommand(openCmd))
                                       .hoverEvent(HoverEvent.showText(text(openCmd, YELLOW))));
                }
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
        plugin.backups.find(id, row -> {
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
        String comment = args.length > 2
            ? String.join(" ", Arrays.copyOfRange(args, 2, args.length))
            : null;
        plugin.backups.create(target, backupType, comment, result -> {
                sender.sendMessage(result
                                   ? text("Inventory backed up: " + target.getName(), YELLOW)
                                   : text("Something went wrong. See console", RED));
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
        plugin.backups.find(id, row -> {
                if (row == null) {
                    sender.sendMessage(text("Backup not found: #" + id, RED));
                    return;
                }
                if (!target.isOnline()) {
                    sender.sendMessage(text("Player disconnected: " + target.getName(), RED));
                    return;
                }
                plugin.backups.restore(target, row, dropCount -> {
                        sender.sendMessage(text("Returned " + row.getTypeEnum().shorthand
                                                + " to " + target.getName() + ": "
                                                + row.getItemCount() + " items, "
                                                + dropCount + " drops", YELLOW));
                    });
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

    protected boolean storeTestItem(Player player, String[] args) {
        ItemStack itemStack = player.getInventory().getItemInMainHand();
        ItemStorage itemStorage = ItemStorage.of(itemStack);
        String json = Json.serialize(itemStorage);
        player.sendMessage(text(json, AQUA));
        if (itemStorage.isEmpty()) return true;
        plugin.getLogger().info(json);
        ItemStack copy = itemStorage.toItemStack();
        String json2 = Json.serialize(ItemStorage.of(copy));
        if (!itemStack.isSimilar(copy)) {
            // This is not always accurate, as the internals of
            // InventoryStorage show.  Many examples of item
            // comparisons yield false, even though they are in fact
            // identicals.  The culprit are meaningless differences in
            // the item's data tag, such as inventory arrays being
            // null, versus being empty.
            player.sendMessage(text(json2, RED));
            plugin.getLogger().info(org.bukkit.ChatColor.RED + json2);
        }
        return true;
    }

    protected boolean storeDupeItem(Player player, String[] args) {
        ItemStack itemStack = player.getInventory().getItemInMainHand();
        ItemStorage itemStorage = ItemStorage.of(itemStack);
        ItemStack copy = itemStorage.toItemStack();
        player.getInventory().addItem(copy);
        player.sendMessage(text("Item duplicated: " + Json.serialize(itemStorage), YELLOW));
        return true;
    }

    protected boolean storeDupeItem64(Player player, String[] args) {
        ItemStack itemStack = player.getInventory().getItemInMainHand();
        ItemStorage itemStorage = ItemStorage.of(itemStack);
        String base64 = itemStorage.getBase64();
        if (base64 == null) {
            player.sendMessage(text("No Base64 component!", AQUA));
            return true;
        }
        ItemStack copy = Items.deserialize(base64);
        player.getInventory().addItem(copy);
        player.sendMessage(text("Item duplicated: " + base64, YELLOW));
        return true;
    }
}
