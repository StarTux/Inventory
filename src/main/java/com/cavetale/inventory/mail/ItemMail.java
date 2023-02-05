package com.cavetale.inventory.mail;

import com.cavetale.core.command.AbstractCommand;
import com.cavetale.core.command.CommandWarn;
import com.cavetale.core.command.RemotePlayer;
import com.cavetale.core.connect.Connect;
import com.cavetale.core.connect.NetworkServer;
import com.cavetale.core.connect.ServerCategory;
import com.cavetale.core.event.hud.PlayerHudEvent;
import com.cavetale.core.event.hud.PlayerHudPriority;
import com.cavetale.core.event.item.PlayerReceiveItemsEvent;
import com.cavetale.core.font.GuiOverlay;
import com.cavetale.core.util.Json;
import com.cavetale.inventory.InventoryPlugin;
import com.cavetale.inventory.gui.Gui;
import com.cavetale.inventory.storage.ItemStorage;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import static com.cavetale.inventory.InventoryPlugin.plugin;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.Component.textOfChildren;
import static net.kyori.adventure.text.event.ClickEvent.runCommand;
import static net.kyori.adventure.text.event.HoverEvent.showText;
import static net.kyori.adventure.text.format.NamedTextColor.*;

/**
 * Item Mail manager.  Enabling this class allows players to open
 * their item mail.
 */
public final class ItemMail extends AbstractCommand<InventoryPlugin> implements Listener {
    private HashSet<UUID> userMailCache = new HashSet<>();
    public static final String MAIL_PERMISSION = "inventory.mail";
    private static final int SIZE = 6 * 9;
    private final List<Integer> slots = new ArrayList<>();

    public ItemMail(final InventoryPlugin plugin) {
        super(plugin, "itemmail");
    }

    @Override
    protected void onEnable() {
        rootNode.playerCaller(this::pickup);
        rootNode.addChild("pickup").denyTabCompletion()
            .description("Open your mail")
            .playerCaller(this::pickup);
        if (NetworkServer.current().isSurvival()) {
            Bukkit.getScheduler().runTaskTimer(plugin, this::check, 0L, 200L);
            Bukkit.getPluginManager().registerEvents(this, plugin);
        }
        // Build a list of slots closest to the "center".  We use a
        // 6x9 inventory so there is no dead center.
        for (int i = 0; i < SIZE; i += 1) slots.add(i);
        slots.sort((a, b) -> {
                final int ax = 4 - (a % 9);
                final int ay = 2 - (a / 9);
                final int bx = 4 - (b % 9);
                final int by = 2 - (b / 9);
                return Integer.compare(ax * ax + ay * ay,
                                       bx * bx + by * by);
            });
    }

    private void check() {
        plugin.getDatabase().find(SQLItemMail.class)
            .findValuesAsync("owner", UUID.class, uuids -> {
                    userMailCache.clear();
                    userMailCache.addAll(uuids);
                });
    }

    private void pickup(Player player) {
        if (!ServerCategory.current().isSurvival()) {
            throw new CommandWarn("Item mails are only available in survival mode!");
        }
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
            .size(SIZE)
            .title(GuiOverlay.HOLES.builder(SIZE, WHITE)
                   .layer(GuiOverlay.TITLE_BAR, GRAY)
                   .title(row.getMessageComponent())
                   .build());
        gui.setEditable(true);
        for (int i = 0; i < itemStackList.size(); i += 1) {
            ItemStack it = itemStackList.get(i);
            if (slots.size() > i) {
                int slot = slots.get(i);
                gui.getInventory().setItem(slot, it);
            } else {
                gui.getInventory().addItem(it);
            }
        }
        gui.onClose(cle -> {
                PlayerReceiveItemsEvent event = new PlayerReceiveItemsEvent(player, gui.getInventory());
                if (event.isEmpty()) return;
                event.giveItems();
                event.callEvent();
                event.dropItems();
                check();
                player.playSound(player.getLocation(), Sound.BLOCK_CHEST_CLOSE, 1.0f, 1.0f);
            });
        gui.open(player);
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 1.0f, 1.0f);
        if (row.getMessage() != null) {
            player.sendMessage(row.getMessageComponent());
        }
    }

    private static final Component NTFY = textOfChildren(text("You have ", AQUA), text("/imail", YELLOW));
    private static final List<Component> NLST = List.of(NTFY);

    @EventHandler
    private void onPlayerHud(PlayerHudEvent event) {
        if (!NetworkServer.current().isSurvival()) return;
        if (!userMailCache.contains(event.getPlayer().getUniqueId())) return;
        if (!event.getPlayer().hasPermission(MAIL_PERMISSION)) return;
        event.sidebar(PlayerHudPriority.HIGH, NLST);
        event.bossbar(PlayerHudPriority.HIGH, NTFY, BossBar.Color.YELLOW, BossBar.Overlay.PROGRESS, 1.0f);
    }

    public static boolean send(UUID target, Inventory inventory, Component message) {
        List<ItemStorage> items = new ArrayList<>();
        for (ItemStack item : inventory) {
            if (item == null || item.getType().isAir()) continue;
            items.add(ItemStorage.of(item));
        }
        if (items.isEmpty()) return false;
        SQLItemMail row = new SQLItemMail(SQLItemMail.SERVER_UUID, target, items, message);
        plugin().getDatabase().insertAsync(row, i -> sendCallback(i, target, message));
        return true;
    }

    public static boolean send(UUID target, List<ItemStack> list, Component message) {
        List<ItemStorage> items = new ArrayList<>();
        for (ItemStack item : list) {
            if (item == null || item.getType().isAir()) continue;
            items.add(ItemStorage.of(item));
        }
        if (items.isEmpty()) return false;
        SQLItemMail row = new SQLItemMail(SQLItemMail.SERVER_UUID, target, items, message);
        plugin().getDatabase().insertAsync(row, i -> sendCallback(i, target, message));
        return true;
    }

    private static void sendCallback(int returnValue, UUID target, Component message) {
        refreshUserCache();
        RemotePlayer remote = Connect.get().getRemotePlayer(target);
        if (remote != null) {
            remote.sendMessage(textOfChildren(text("You have "),
                                              text("/imail", YELLOW),
                                              text(": "),
                                              message)
                               .hoverEvent(showText(text("/imail", YELLOW)))
                               .clickEvent(runCommand("/imail")));
        }
    }

    public static void refreshUserCache() {
        plugin().getItemMail().check();
    }
}
