package com.cavetale.inventory.mail;

import com.cavetale.core.command.AbstractCommand;
import com.cavetale.core.util.Json;
import com.cavetale.inventory.InventoryPlugin;
import com.cavetale.inventory.gui.Gui;
import com.cavetale.inventory.storage.ItemStorage;
import com.cavetale.inventory.util.Items;
import com.cavetale.sidebar.PlayerSidebarEvent;
import com.cavetale.sidebar.Priority;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import static net.kyori.adventure.text.Component.join;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.JoinConfiguration.noSeparators;
import static net.kyori.adventure.text.format.NamedTextColor.*;

/**
 * Item Mail manager.  Enabling this class allows players to open
 * their item mail.
 */
public final class ItemMail extends AbstractCommand<InventoryPlugin> implements Listener {
    private HashSet<UUID> userMailCache = new HashSet<>();
    public static final String MAIL_PERMISSION = "inventory.mail";

    public ItemMail(final InventoryPlugin plugin) {
        super(plugin, "itemmail");
    }

    @Override
    protected void onEnable() {
        rootNode.addChild("pickup").denyTabCompletion()
            .description("Open your mail")
            .playerCaller(this::pickup);
        Bukkit.getScheduler().runTaskTimer(plugin, this::check, 0L, 200L);
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    private void check() {
        plugin.getDatabase().find(SQLItemMail.class)
            .findValuesAsync("owner", UUID.class, uuids -> {
                    userMailCache.clear();
                    userMailCache.addAll(uuids);
                });
    }

    private boolean pickup(Player player, String[] args) {
        if (args.length != 0) return false;
        final UUID uuid = player.getUniqueId();
        plugin.getDatabase().scheduleAsyncTask(() -> {
                List<SQLItemMail> rows = plugin.getDatabase().find(SQLItemMail.class)
                    .eq("owner", uuid)
                    .orderByAscending("created")
                    .limit(1)
                    .findList();
                SQLItemMail row = !rows.isEmpty() ? rows.get(0) : null;
                if (row == null) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                            player.sendMessage(text("You don't have any item mail", RED));
                        });
                    return;
                }
                plugin.getDatabase().delete(row);
                Bukkit.getScheduler().runTask(plugin, () -> pickupCallback(player, row));
            });
        return true;
    }

    private void pickupCallback(Player player, SQLItemMail row) {
        if (!player.isOnline()) {
            plugin.getLogger().warning("[ItemMail] Player went offline: " + player.getName());
            row.setId(null);
            plugin.getDatabase().insertAsync(row, null);
            return;
        }
        List<ItemStorage> itemList = row.getItemList();
        List<ItemStack> itemStackList = new ArrayList<>(itemList.size());
        for (ItemStorage it : itemList) {
            try {
                itemStackList.add(it.toItemStack());
            } catch (IllegalStateException ise) {
                plugin.getLogger().log(Level.SEVERE, "[ItemMail] pickupCallback: " + Json.serialize(it), ise);
            }
        }
        Gui gui = new Gui(plugin, Gui.Type.MAIL)
            .title(text("Item Mail", DARK_BLUE))
            .size(6 * 9);
        gui.setEditable(true);
        for (ItemStack it : itemStackList) {
            gui.getInventory().addItem(it);
        }
        gui.onClose(cle -> {
                for (ItemStack itemStack : gui.getInventory()) {
                    if (itemStack == null || itemStack.getType() == Material.AIR) continue;
                    Items.give(player, itemStack);
                }
                check();
            });
        gui.open(player);
        if (row.getMessage() != null) {
            player.sendMessage(row.getMessageComponent());
        }
    }

    @EventHandler
    private void onPlayerSidebar(PlayerSidebarEvent event) {
        if (!userMailCache.contains(event.getPlayer().getUniqueId())) return;
        if (!event.getPlayer().hasPermission(MAIL_PERMISSION)) return;
        event.add(plugin, Priority.HIGH,
                  join(noSeparators(),
                       text("You have ", AQUA),
                       text("/imail", YELLOW)));
    }

    public static void send(UUID target, Inventory inventory, Component message) {
        List<ItemStorage> items = new ArrayList<>();
        for (ItemStack item : inventory) {
            if (item == null || item.getType().isAir()) continue;
            items.add(ItemStorage.of(item));
        }
        if (items.isEmpty()) return;
        SQLItemMail row = new SQLItemMail(SQLItemMail.SERVER_UUID, target, items, message);
        InventoryPlugin.getInstance().getDatabase().insertAsync(row, null);
    }
}
