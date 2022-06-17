package com.cavetale.inventory;

import com.cavetale.core.connect.Connect;
import com.cavetale.core.connect.ServerGroup;
import com.cavetale.core.event.connect.ConnectMessageEvent;
import com.cavetale.core.util.Json;
import com.cavetale.inventory.mail.SQLItemMail;
import com.cavetale.inventory.sql.SQLInventory;
import com.cavetale.inventory.sql.SQLTrack;
import com.cavetale.inventory.storage.InventoryStorage;
import com.cavetale.inventory.storage.ItemStorage;
import com.cavetale.inventory.storage.PlayerStatusStorage;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Level;
import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import static net.kyori.adventure.text.Component.text;

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
        Date then = Date.from(Instant.now().minus(Duration.ofDays(3)));
        plugin.database.find(SQLInventory.class)
            .isNotNull("claimed")
            .lt("claimed", then)
            .deleteAsync(count -> {
                    if (count <= 0) return;
                    plugin.getLogger().info("Deleted " + count + " inventories claimed before " + then);
                });
    }

    protected void storeToDatabase(Player player, Runner runner, Consumer<Integer> callback) {
        SQLInventory.Tag tag = new SQLInventory.Tag();
        tag.setInventory(InventoryStorage.of(player.getInventory()));
        tag.setEnderChest(InventoryStorage.of(player.getEnderChest()));
        tag.setStatus(PlayerStatusStorage.of(player));
        ItemStack cursor = player.getOpenInventory().getCursor();
        if (cursor != null && cursor.getType() != Material.AIR) {
            tag.setCursor(ItemStorage.of(cursor));
        }
        if (tag.isEmpty()) return;
        final UUID uuid = player.getUniqueId();
        InventoryStorage.clear(player.getInventory());
        InventoryStorage.clear(player.getEnderChest());
        PlayerStatusStorage.clear(player);
        player.getOpenInventory().setCursor(null);
        runner.sql(() -> {
                SQLTrack trackRow = plugin.database.find(SQLTrack.class)
                    .eq("player", uuid)
                    .findUnique();
                final int track = trackRow != null
                    ? trackRow.getTrack()
                    : 0;
                SQLInventory row = new SQLInventory(uuid, track, Json.serialize(tag), tag.getItemCount());
                int result = plugin.database.insert(row);
                plugin.getLogger().info("[Store] Stored " + player.getName()
                                        + " track=" + track
                                        + " items=" + tag.getItemCount()
                                        + " id=" + row.getId()
                                        + " result=" + result);
                runner.main(() -> {
                        Connect.get().broadcastMessage(ServerGroup.current(), MESSAGE_STORED, uuid.toString());
                        if (callback != null) callback.accept(result);
                    });
            });
    }

    protected void loadFromDatabase(Player player, Runner runner, Consumer<Integer> callback) {
        final UUID uuid = player.getUniqueId();
        runner.sql(() -> {
                SQLTrack trackRow = plugin.database.find(SQLTrack.class)
                    .eq("player", uuid)
                    .findUnique();
                final int track = trackRow != null
                    ? trackRow.getTrack()
                    : 0;
                List<SQLInventory> list = plugin.database.find(SQLInventory.class)
                    .eq("owner", uuid)
                    .isNull("claimed")
                    .eq("track", track)
                    .findList();
                if (list.isEmpty()) return;
                Date now = new Date();
                list.removeIf(it -> {
                        return 0 == plugin.database.update(SQLInventory.class)
                            .row(it)
                            .atomic("claimed", now).sync();
                    });
                if (list.isEmpty()) return;
                runner.main(() -> loadFromDatabaseCallback(player, list, callback));
            });
    }

    /** Always called in main thread. */
    private void loadFromDatabaseCallback(Player player, List<SQLInventory> list, Consumer<Integer> callback) {
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
            if (!drops.isEmpty()) {
                List<ItemStorage> drops2 = new ArrayList<>();
                for (ItemStack it : drops) {
                    drops2.add(ItemStorage.of(it));
                }
                SQLItemMail mail = new SQLItemMail(SQLItemMail.SERVER_UUID, player.getUniqueId(), drops2,
                                                   text("You dropped this earlier"));
                plugin.database.insertAsync(mail, null);
            }
            if (tag.getStatus() != null) {
                tag.getStatus().restore(player);
            }
            plugin.getLogger().info("[Store] Restored " + player.getName()
                                    + " track=" + row.getTrack()
                                    + " items=" + tag.getItemCount()
                                    + " drops=" + drops.size()
                                    + " id=" + row.getId());
        }
        if (callback != null) callback.accept(list.size());
    }

    @EventHandler(priority = EventPriority.HIGH)
    protected void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission(PERM_NOSTORE) && player.isPermissionSet(PERM_NOSTORE)) return;
        loadFromDatabase(player, Runner.ASYNC, null);
    }

    @EventHandler(priority = EventPriority.HIGH)
    protected void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission(PERM_NOSTORE) && player.isPermissionSet(PERM_NOSTORE)) return;
        storeToDatabase(player, Runner.ASYNC, null);
    }

    @EventHandler
    protected void onConnectMessage(ConnectMessageEvent event) {
        if (MESSAGE_STORED.equals(event.getChannel())) {
            UUID uuid = event.getPayload(UUID.class);
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) return;
            loadFromDatabase(player, Runner.ASYNC, r -> {
                    if (r != 0) plugin.getLogger().info("[Store] Update received: " + player.getName());
                });
        }
    }

    public void switchTrack(Player player, int track) {
        storeToDatabase(player, Runner.SYNC, null);
        if (track == 0) {
            plugin.database.find(SQLTrack.class).eq("player", player.getUniqueId()).delete();
        } else {
            plugin.database.save(new SQLTrack(player, track));
        }
        loadFromDatabase(player, Runner.SYNC, null);
    }
}
