package com.cavetale.inventory;

import com.cavetale.core.command.AbstractCommand;
import com.cavetale.core.command.CommandArgCompleter;
import com.cavetale.core.command.CommandNode;
import com.cavetale.core.command.CommandWarn;
import com.cavetale.core.event.item.PlayerReceiveItemsEvent;
import com.cavetale.core.util.Json;
import com.cavetale.inventory.mail.ItemMail;
import com.cavetale.inventory.mail.SQLItemMail;
import com.cavetale.inventory.sql.SQLBackup;
import com.cavetale.inventory.sql.SQLInventory;
import com.cavetale.inventory.sql.SQLStash;
import com.cavetale.inventory.sql.SQLTrack;
import com.cavetale.inventory.storage.InventoryStorage;
import com.cavetale.inventory.storage.ItemStorage;
import com.cavetale.inventory.util.Items;
import com.cavetale.mytems.util.Gui;
import com.winthier.playercache.PlayerCache;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import static net.kyori.adventure.text.Component.empty;
import static net.kyori.adventure.text.Component.join;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.JoinConfiguration.noSeparators;
import static net.kyori.adventure.text.event.HoverEvent.showText;
import static net.kyori.adventure.text.format.NamedTextColor.*;
import static net.kyori.adventure.text.format.TextDecoration.*;

public final class InventoryCommand extends AbstractCommand<InventoryPlugin> {
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yy/MM/dd HH:mm");

    protected InventoryCommand(final InventoryPlugin plugin) {
        super(plugin, "inventory");
    }

    @Override
    protected void onEnable() {
        // Stash
        CommandNode stashNode = rootNode.addChild("stash")
            .description("Stash commands");
        stashNode.addChild("open").arguments("<player>")
            .description("Peek in a player's stash (copy)")
            .playerCaller(this::stashOpen);
        stashNode.addChild("transfer").arguments("<from> <to>")
            .description("Transfer stash")
            .completers(PlayerCache.NAME_COMPLETER, PlayerCache.NAME_COMPLETER)
            .senderCaller(this::stashTransfer);
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
        backupNode.addChild("mail").arguments("<id> <player> [message...]")
            .completers(CommandArgCompleter.integer(i -> i > 0),
                        CommandArgCompleter.PLAYER_CACHE)
            .description("Send backup via mail")
            .senderCaller(this::backupMail);
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
        storeNode.addChild("transfer").arguments("<from> <to>")
            .description("Transfer inventories")
            .completers(PlayerCache.NAME_COMPLETER, PlayerCache.NAME_COMPLETER)
            .senderCaller(this::storeTransfer);
        storeNode.addChild("list").arguments("<player>")
            .description("List inventory stores")
            .senderCaller(this::storeList);
        storeNode.addChild("open").arguments("<id>")
            .description("Open inventory store")
            .playerCaller(this::storeOpen);
        storeNode.addChild("openender").arguments("<id>")
            .description("Open ender store")
            .playerCaller(this::storeOpenEnder);
        storeNode.addChild("deliver").arguments("<id>")
            .description("(Re)deliver an inventory")
            .playerCaller(this::storeDeliver);
        storeNode.addChild("unclaim").arguments("<id>")
            .description("Mark a claimed inventory as unclaimed")
            .senderCaller(this::storeUnclaim);
        // Mail
        CommandNode mailNode = rootNode.addChild("mail")
            .description("Item mail commands");
        mailNode.addChild("send").arguments("<player> <message>")
            .description("Send item mail to player")
            .completers(CommandArgCompleter.PLAYER_CACHE)
            .playerCaller(this::mailSend);
        //
        rootNode.addChild("duties").denyTabCompletion()
            .description("List players in dutymode")
            .senderCaller(this::duties);
        rootNode.addChild("itemsfromfile").arguments("<path>")
            .description("Load items from file, one item per line")
            .playerCaller(this::itemsFromFile);
        rootNode.addChild("inventoryfromfile").arguments("<path>")
            .description("Load whole inventory from file")
            .playerCaller(this::inventoryFromFile);
    }

    protected boolean stashTransfer(CommandSender sender, String[] args) {
        if (args.length != 2) return false;
        PlayerCache from = PlayerCache.forArg(args[0]);
        if (from == null) throw new CommandWarn("Player not found: " + args[0]);
        PlayerCache to = PlayerCache.forArg(args[1]);
        if (to == null) throw new CommandWarn("Player not found: " + args[1]);
        if (from.equals(to)) throw new CommandWarn("Players are identical!");
        List<SQLStash> rows = plugin.database.find(SQLStash.class).eq("owner", from.uuid).findList();
        if (rows.isEmpty()) throw new CommandWarn(from.name + " does not have a stash");
        int count = 0;
        for (SQLStash row : rows) {
            InventoryStorage inventoryStorage = row.getInventoryStorage();
            if (inventoryStorage.isEmpty()) continue;
            SQLItemMail itemMail = new SQLItemMail(SQLItemMail.SERVER_UUID, to.uuid, inventoryStorage.getItems(), text("Transfer"));
            plugin.database.insert(itemMail);
            count += 1;
        }
        plugin.database.delete(rows);
        sender.sendMessage("Transferred stash from " + from.name + " to " + to.name + ":"
                           + " rows=" + rows.size()
                           + " imails=" + count);
        return true;
    }

    protected boolean backupList(CommandSender sender, String[] args) {
        if (args.length != 1) return false;
        String ownerName = args[0];
        PlayerCache owner = PlayerCache.forArg(args[0]);
        if (owner == null) throw new CommandWarn("Player not found: " + ownerName);
        plugin.backups.find(owner.uuid, list -> {
                sender.sendMessage(text("Found: " + list.size(), YELLOW));
                for (SQLBackup row : list) {
                    if (row.getTypeEnum() == null) continue;
                    String openCmd = "/inventory backup open " + row.getId();
                    sender.sendMessage(join(noSeparators(),
                                            text("#" + row.getId(), YELLOW),
                                            text(" " + row.getTypeEnum().shorthand, WHITE),
                                            text(" items:" + row.getItemCount(), GRAY),
                                            text(" " + DATE_FORMAT.format(row.getCreated()), WHITE),
                                            Component.space(),
                                            (row.getComment() != null
                                             ? text(row.getComment(), GRAY, ITALIC)
                                             : empty()))
                                       .clickEvent(ClickEvent.suggestCommand(openCmd))
                                       .hoverEvent(showText(text(openCmd, YELLOW))));
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

    protected boolean backupMail(CommandSender sender, String[] args) {
        if (args.length < 2) return false;
        final String idString = args[0];
        final String playerName = args[1];
        final String message = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
        final int id = CommandArgCompleter.requireInt(idString, i -> i > 0);
        final PlayerCache target = PlayerCache.require(playerName);
        plugin.backups.find(id, row -> {
                if (row == null) {
                    sender.sendMessage(text("Backup not found: #" + id, RED));
                    return;
                }
                final SQLBackup.Tag tag = row.deserialize();
                final InventoryStorage storage = tag.getInventory(row.getTypeEnum());
                final Inventory inv = storage.toInventory();
                if (ItemMail.send(target.uuid, inv, text(message))) {
                    sender.sendMessage(text("Backup of " + row.getTypeEnum().getDisplayName()
                                            + " mailed to " + target.name + ": " + row.getId()
                                            + ", " + storage.getCount() + " items", YELLOW)
                                       .hoverEvent(showText(text(row.getId(), GRAY)))
                                       .insertion("" + row.getId()));
                } else {
                    sender.sendMessage(text("Inventory empty, nothing was sent: " + row.getId(), RED)
                                       .hoverEvent(showText(text(row.getId(), GRAY)))
                                       .insertion("" + row.getId()));
                }
            });
        return true;
    }

    protected boolean stashOpen(Player sender, String[] args) {
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
        Gui gui = new Gui(plugin)
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
            plugin.getLogger().warning(json2);
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

    protected boolean storeTransfer(CommandSender sender, String[] args) {
        if (args.length != 2) return false;
        PlayerCache from = PlayerCache.forArg(args[0]);
        if (from == null) throw new CommandWarn("Player not found: " + args[0]);
        PlayerCache to = PlayerCache.forArg(args[1]);
        if (to == null) throw new CommandWarn("Player not found: " + args[1]);
        if (from.equals(to)) throw new CommandWarn("Players are identical!");
        int count = plugin.database.update(SQLInventory.class)
            .set("owner", to.uuid)
            .where(c -> c.eq("owner", from.uuid).isNull("claimed"))
            .sync();
        if (count == 0) throw new CommandWarn(from.name + " does not have an inventory stored");
        sender.sendMessage(text("Inventories transferred from " + from.name + " to " + to.name + ":"
                                + " rows=" + count, YELLOW));
        return true;
    }

    protected boolean storeList(CommandSender sender, String[] args) {
        if (args.length != 1) return false;
        String ownerName = args[0];
        PlayerCache owner = PlayerCache.forArg(args[0]);
        if (owner == null) throw new CommandWarn("Player not found: " + ownerName);
        plugin.database.find(SQLInventory.class)
            .eq("owner", owner.uuid)
            .orderByAscending("claimed")
            .select("id", "track", "itemCount", "created", "claimed")
            .findListAsync(list -> {
                    sender.sendMessage(text("Found: " + list.size(), YELLOW));
                    for (SQLInventory row : list) {
                        String openCmd = "/inventory store open " + row.getId();
                        String deliverCmd = "/inventory store deliver " + row.getId();
                        sender.sendMessage(join(noSeparators(),
                                                (text("#" + row.getId(), YELLOW)
                                                 .clickEvent(ClickEvent.suggestCommand(deliverCmd))
                                                 .hoverEvent(showText(text(deliverCmd, YELLOW)))),
                                                text(" tr:", GRAY),
                                                text(row.getTrack(), WHITE),
                                                text(" i:", GRAY),
                                                text(row.getItemCount(), WHITE),
                                                text(" cr:", GRAY),
                                                text(DATE_FORMAT.format(row.getCreated()), WHITE),
                                                text(" cl:", GRAY),
                                                (row.isClaimed()
                                                 ? text(DATE_FORMAT.format(row.getCreated()), WHITE)
                                                 : text("NO", DARK_RED)))
                                           .clickEvent(ClickEvent.suggestCommand(openCmd))
                                           .hoverEvent(showText(text(openCmd, YELLOW))));
                    }
                });
        return true;
    }

    protected boolean storeOpen(Player player, String[] args) {
        if (args.length != 1) return false;
        String idString = args[0];
        final int id = CommandArgCompleter.requireInt(args[0], i -> i > 0);
        storeOpen(player, id, false);
        return true;
    }

    protected boolean storeOpenEnder(Player player, String[] args) {
        if (args.length != 1) return false;
        String idString = args[0];
        final int id = CommandArgCompleter.requireInt(args[0], i -> i > 0);
        storeOpen(player, id, true);
        return true;
    }

    private void storeOpen(Player player, int id, boolean ender) {
        plugin.database.find(SQLInventory.class)
            .idEq(id)
            .findUniqueAsync(row -> {
                    if (row == null) {
                        player.sendMessage(text("Inventory not found: #" + id, RED));
                        return;
                    }
                    player.sendMessage(text(" #" + row.getId(), YELLOW)
                                       .append(text(" " + row.getItemCount(), GRAY))
                                       .append(text(" " + DATE_FORMAT.format(row.getCreated()), WHITE)));
                    final SQLInventory.Tag tag = Json.deserialize(row.getJson(), SQLInventory.Tag.class);
                    Inventory inventory = !ender
                        ? tag.getInventory().toInventory()
                        : tag.getEnderChest().toInventory();
                    player.sendMessage(text("Opening...", YELLOW));
                    player.openInventory(inventory);
                });
    }

    protected boolean storeDeliver(CommandSender sender, String[] args) {
        if (args.length != 1) return false;
        String idString = args[0];
        final int id = CommandArgCompleter.requireInt(args[0], i -> i > 0);
        plugin.database.find(SQLInventory.class)
            .idEq(id)
            .findUniqueAsync(row -> {
                    if (row == null) {
                        sender.sendMessage(text("Inventory not found: #" + id, RED));
                        return;
                    }
                    Player target = Bukkit.getPlayer(row.getOwner());
                    if (target == null) {
                        sender.sendMessage(text("Player not online: " + PlayerCache.nameForUuid(row.getOwner()), RED));
                        return;
                    }
                    final SQLInventory.Tag tag = Json.deserialize(row.getJson(), SQLInventory.Tag.class);
                    List<ItemStack> drops = new ArrayList<>();
                    tag.restore(target, drops);
                    PlayerReceiveItemsEvent.receiveItems(target, drops);
                    if (!row.isClaimed()) {
                        row.setClaimed(new Date());
                        plugin.database.update(row, "claimed");
                    }
                    sender.sendMessage(text("Delivered inventory #" + row.getId() + " to " + target.getName(), YELLOW));
                });
        return true;
    }

    protected boolean storeUnclaim(CommandSender sender, String[] args) {
        if (args.length != 1) return false;
        String idString = args[0];
        final int id = CommandArgCompleter.requireInt(args[0], i -> i > 0);
        plugin.database.update(SQLInventory.class)
            .where(q -> q.eq("id", id))
            .set("claimed", null)
            .async(i -> {
                    if (i == 0) {
                        sender.sendMessage(text("Not claimed: #" + id, RED));
                        return;
                    }
                    sender.sendMessage(text("Set as unclaimed: #" + id, YELLOW));
                });
        return true;
    }

    private boolean mailSend(Player player, String[] args) {
        if (args.length < 2) return false;
        PlayerCache target = PlayerCache.require(args[0]);
        Component message = text(String.join(" ", Arrays.copyOfRange(args, 1, args.length)));
        Gui gui = new Gui(plugin)
            .title(text("Item Mail to " + target.name))
            .size(6 * 9);
        gui.setEditable(true);
        gui.onClose(evt -> {
                if (ItemMail.send(target.uuid, gui.getInventory(), message)) {
                    player.sendMessage(text("Item mail sent to " + target.name, YELLOW));
                } else {
                    player.sendMessage(text("Inventory empty, nothing was sent", RED));
                }
            });
        gui.open(player);
        return true;
    }

    private void duties(CommandSender sender) {
        plugin.database.find(SQLTrack.class).findListAsync(list -> CommandNode.wrap(sender, () -> {
                    if (list.isEmpty()) {
                        throw new CommandWarn("Nobody is in dutymode");
                    }
                    sender.sendMessage(text(list.size() + " player(s) in dutymode", GRAY));
                    for (SQLTrack row : list) {
                        sender.sendMessage(join(noSeparators(),
                                                text(PlayerCache.nameForUuid(row.getPlayer()), AQUA),
                                                text(" " + row.getTrack(), YELLOW),
                                                text(" " + row.getServer(), AQUA),
                                                text(" " + row.getWorld()
                                                     + " " + (int) Math.floor(row.getX())
                                                     + " " + (int) Math.floor(row.getY())
                                                     + " " + (int) Math.floor(row.getZ()), GRAY),
                                                text(" " + DATE_FORMAT.format(row.getUpdated()), GRAY, ITALIC)));
                    }
                }));
    }

    private boolean itemsFromFile(Player player, String[] args) {
        if (args.length != 1) return false;
        File file = new File(args[0]);
        if (!file.exists()) throw new IllegalArgumentException("File not found: " + file);
        int count = 0;
        try (BufferedReader in = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = in.readLine()) != null) {
                ItemStorage itemStorage = Json.deserialize(line, ItemStorage.class);
                if (itemStorage == null) throw new CommandWarn("Bad line: " + line);
                player.getInventory().addItem(itemStorage.toItemStack());
                count += 1;
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new CommandWarn(e.getMessage());
        }
        player.sendMessage(text(count + " items restored from " + file, YELLOW));
        return true;
    }

    private boolean inventoryFromFile(Player player, String[] args) {
        if (args.length != 1) return false;
        File file = new File(args[0]);
        if (!file.exists()) throw new IllegalArgumentException("File not found: " + file);
        InventoryStorage storage = Json.load(file, InventoryStorage.class, null);
        if (storage == null) throw new CommandWarn("Not an inventory storage: " + file);
        player.openInventory(storage.toInventory());
        player.sendMessage(text("Openinv inventory from " + file, YELLOW));
        return true;
    }
}
