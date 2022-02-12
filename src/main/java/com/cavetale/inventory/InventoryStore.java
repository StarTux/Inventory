package com.cavetale.inventory;

import com.cavetale.core.util.Json;
import com.cavetale.inventory.sql.SQLInventory;
import com.cavetale.inventory.storage.InventoryStorage;
import com.cavetale.inventory.storage.ItemStorage;
import com.cavetale.inventory.util.Items;
import com.winthier.connect.Connect;
import com.winthier.connect.event.ConnectMessageEvent;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Level;
import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

/**
 * This class, when activated, will take care of player inventories so
 * that they are stored in the database when the user logs out, and
 * loaded from database when they join.
 */
@RequiredArgsConstructor
public final class InventoryStore implements Listener {
    private static final String PERM_NOSTORE = "inventory.nostore";
    private static final String MESSAGE_STORED = "inventory:stored";
    private final InventoryPlugin plugin;

    protected void enable() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        deleteOld();
    }

    protected void deleteOld() {
        Date then = new Date(System.currentTimeMillis() - Duration.ofDays(1).toMillis());
        plugin.database.find(SQLInventory.class)
            .isNotNull("claimed")
            .lt("claimed", then)
            .deleteAsync(count -> {
                    if (count <= 0) return;
                    plugin.getLogger().info("Deleted " + count + " inventories claimed before " + then);
                });
    }

    protected void storeToDatabase(Player player) {
        SQLInventory.Tag tag = new SQLInventory.Tag();
        tag.setInventory(InventoryStorage.of(player.getInventory()));
        tag.setEnderChest(InventoryStorage.of(player.getEnderChest()));
        ItemStack cursor = player.getOpenInventory().getCursor();
        if (cursor != null && cursor.getType() != Material.AIR) {
            tag.setCursor(ItemStorage.of(cursor));
        }
        if (tag.isEmpty()) return;
        SQLInventory row = new SQLInventory(player.getUniqueId(),
                                            SQLInventory.Track.SURVIVAL,
                                            Json.serialize(tag),
                                            tag.getItemCount());
        plugin.database.insertAsync(row, result -> {
                plugin.getLogger().info("[Store] Stored " + player.getName()
                                        + " items=" + tag.getItemCount()
                                        + " id=" + row.getId()
                                        + " result=" + result);
                Connect.getInstance().broadcast(MESSAGE_STORED, player.getUniqueId().toString());
            });
        InventoryStorage.clear(player.getInventory());
        InventoryStorage.clear(player.getEnderChest());
        player.getOpenInventory().setCursor(null);
    }

    protected void loadFromDatabase(Player player) {
        plugin.database.scheduleAsyncTask(() -> {
                List<SQLInventory> list = plugin.database.find(SQLInventory.class)
                    .eq("owner", player.getUniqueId())
                    .isNull("claimed")
                    .eq("track", SQLInventory.Track.SURVIVAL.ordinal())
                    .findList();
                if (list.isEmpty()) return;
                Date now = new Date();
                list.removeIf(it -> {
                        return 0 == plugin.database.update(SQLInventory.class)
                            .row(it)
                            .atomic("claimed", now).sync();
                    });
                if (list.isEmpty()) return;
                Bukkit.getScheduler().runTask(plugin, () -> loadFromDatabaseCallback(player, list));
            });
    }

    private void loadFromDatabaseCallback(Player player, List<SQLInventory> list) {
        if (!player.isOnline()) {
            plugin.getLogger().warning("[Store] Player left: "
                                       + player.getName() + ": " + list);
            for (SQLInventory row : list) {
                row.setClaimed(null);
                plugin.database.updateAsync(row, null, "claimed");
            }
            return;
        }
        for (SQLInventory row : list) {
            final SQLInventory.Tag tag;
            List<ItemStack> drops = new ArrayList<>();
            try {
                tag = Json.deserialize(row.getJson(), SQLInventory.Tag.class);
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "[Store] deserialize failed: " + row, e);
                continue;
            }
            if (tag.getInventory() != null) {
                drops.addAll(tag.getInventory().restore(player.getInventory(), "InventoryStore:inventory:" + player.getName()));
            }
            if (tag.getEnderChest() != null) {
                drops.addAll(tag.getEnderChest().restore(player.getEnderChest(), "InventoryStore:enderChest:" + player.getName()));
            }
            if (tag.getCursor() != null) {
                drops.add(tag.getCursor().toItemStack());
            }
            drops.removeIf(Objects::isNull);
            if (!drops.isEmpty()) Items.give(player, drops);
            plugin.getLogger().info("[Store] Restored " + player.getName()
                                    + " items=" + tag.getItemCount()
                                    + " drops=" + drops.size()
                                    + " id=" + row.getId());
        }
    }

    @EventHandler
    protected void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission(PERM_NOSTORE) && player.isPermissionSet(PERM_NOSTORE)) return;
        loadFromDatabase(player);
    }

    @EventHandler
    protected void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission(PERM_NOSTORE) && player.isPermissionSet(PERM_NOSTORE)) return;
        storeToDatabase(player);
    }

    @EventHandler
    protected void onConnectMessage(ConnectMessageEvent event) {
        if (MESSAGE_STORED.equals(event.getMessage().getChannel())) {
            UUID uuid = UUID.fromString(event.getMessage().getPayload());
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) return;
            loadFromDatabase(player);
        }
    }
}
