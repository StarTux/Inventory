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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Level;
import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
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
    /**
     * The tracks map scores player tracks while the player is online.
     *
     * Entries are only added by enable() and
     * loadFromDatabaseCallback().
     *
     * Entries are only removed by onPlayerQuit() and switchTrack().
     */
    private final Map<UUID, SQLTrack> tracks = new HashMap<>();

    protected void enable() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        deleteOld();
        for (SQLTrack track : plugin.database.find(SQLTrack.class).findList()) {
            if (Bukkit.getPlayer(track.getPlayer()) != null) {
                tracks.put(track.getPlayer(), track);
            }
        }
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

    /**
     * Called during logout or player switch.
     */
    private void storeToDatabase(Player player, Runner runner, SQLTrack trackRow, boolean withGameMode, Consumer<Integer> callback) {
        final UUID uuid = player.getUniqueId();
        final String name = player.getName();
        final int track = trackRow != null
            ? trackRow.getTrack()
            : 0;
        SQLInventory.Tag tag = new SQLInventory.Tag();
        tag.setInventory(InventoryStorage.of(player.getInventory()));
        tag.setEnderChest(InventoryStorage.of(player.getEnderChest()));
        tag.setStatus(PlayerStatusStorage.of(player));
        ItemStack cursor = player.getOpenInventory().getCursor();
        if (cursor != null && cursor.getType() != Material.AIR) {
            tag.setCursor(ItemStorage.of(cursor));
        }
        if (tag.isEmpty()) {
            if (callback != null) callback.accept(0);
            return;
        }
        InventoryStorage.clear(player.getInventory());
        InventoryStorage.clear(player.getEnderChest());
        PlayerStatusStorage.clear(player);
        player.getOpenInventory().setCursor(null);
        GameMode gameMode = withGameMode || track == 1
            ? player.getGameMode()
            : null;
        runner.sql(() -> {
                storeToDatabase(uuid, name, runner, trackRow, tag, gameMode, callback);
            });
    }

    /**
     * Called only by the method above.
     */
    private void storeToDatabase(UUID uuid, String name, Runner runner, SQLTrack trackRow,
                                 SQLInventory.Tag tag, GameMode gameMode, Consumer<Integer> callback) {
        final int track = trackRow != null
            ? trackRow.getTrack()
            : 0;
        SQLInventory row = new SQLInventory(uuid, track, Json.serialize(tag), tag.getItemCount());
        row.setGameMode(gameMode);
        int result = plugin.database.insert(row);
        plugin.getLogger().info("[Store] Stored " + name
                                + " track=" + track
                                + " items=" + tag.getItemCount()
                                + " id=" + row.getId()
                                + " result=" + result);
        runner.main(() -> {
                Connect.get().broadcastMessage(ServerGroup.current(), MESSAGE_STORED, uuid.toString());
                if (callback != null) callback.accept(result);
            });
    }

    /**
     * Called during login or refresh.
     */
    private void loadFromDatabase(Player player, Runner runner, Consumer<Integer> callback) {
        final UUID uuid = player.getUniqueId();
        final String name = player.getName();
        runner.sql(() -> {
                SQLTrack trackRow = plugin.database.find(SQLTrack.class)
                    .eq("player", uuid)
                    .findUnique();
                loadFromDatabase(uuid, name, runner, trackRow, callback);
            });
    }

    /**
     * Called in sql thread during login by method above.  Called in
     * main thread during switch.
     */
    private void loadFromDatabase(UUID uuid, String name, Runner runner, SQLTrack trackRow, Consumer<Integer> callback) {
        int track = trackRow != null
            ? trackRow.getTrack()
            : 0;
        List<SQLInventory> list = plugin.database.find(SQLInventory.class)
            .eq("owner", uuid)
            .isNull("claimed")
            .eq("track", track)
            .findList();
        if (list.isEmpty()) {
            if (callback != null) {
                runner.main(() -> callback.accept(0));
            }
            return;
        }
        Date now = new Date();
        list.removeIf(it -> {
                return 0 == plugin.database.update(SQLInventory.class)
                    .row(it)
                    .atomic("claimed", now).sync();
            });
        if (list.isEmpty()) {
            if (callback != null) {
                runner.main(() -> callback.accept(0));
            }
            return;
        }
        runner.main(() -> loadFromDatabaseCallback(uuid, name, list, trackRow, callback));
    }

    /** Always called in main thread. */
    private void loadFromDatabaseCallback(UUID uuid, String name, List<SQLInventory> list, SQLTrack trackRow, Consumer<Integer> callback) {
        final Player player = Bukkit.getPlayer(uuid);
        if (player == null || !player.isOnline()) {
            plugin.getLogger().warning("[Store] Player left: " + name + ": " + list);
            for (SQLInventory row : list) {
                row.setClaimed(null);
                plugin.database.updateAsync(row, null, "claimed");
            }
            return;
        }
        if (tracks.containsKey(uuid) && !Objects.equals(trackRow, tracks.get(uuid))) {
            plugin.getLogger().warning("[Store] Conflicting track exists: " + name + ": " + trackRow + "/" + tracks.get(uuid) + ", " + list);
            for (SQLInventory row : list) {
                row.setClaimed(null);
                plugin.database.updateAsync(row, null, "claimed");
            }
            return;
        }
        if (trackRow != null) tracks.put(uuid, trackRow);
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
                SQLItemMail mail = new SQLItemMail(SQLItemMail.SERVER_UUID, uuid, drops2,
                                                   text("You dropped this earlier"));
                plugin.database.insertAsync(mail, null);
            }
            if (tag.getStatus() != null) {
                tag.getStatus().restore(player);
            }
            if (row.getGameMode() != null) player.setGameMode(row.getGameMode());
            plugin.getLogger().info("[Store] Restored " + player.getName()
                                    + " track=" + row.getTrack()
                                    + " items=" + tag.getItemCount()
                                    + " drops=" + drops.size()
                                    + " gamemode=" + row.getGameMode()
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
        storeToDatabase(player, Runner.ASYNC, tracks.remove(player.getUniqueId()), false, null);
    }

    /**
     * Refresh a logged in player.
     */
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

    public void switchTrack(Player player, int newTrack) {
        final UUID uuid = player.getUniqueId();
        final String name = player.getName();
        SQLTrack oldTrackRow = tracks.remove(uuid);
        int oldTrack = oldTrackRow != null
            ? oldTrackRow.getTrack()
            : 0;
        storeToDatabase(player, Runner.SYNC, oldTrackRow, true, null);
        final SQLTrack newTrackRow;
        if (newTrack == 0) {
            if (oldTrackRow != null) plugin.database.delete(oldTrackRow);
            newTrackRow = null;
        } else {
            newTrackRow = new SQLTrack(player, newTrack);
            plugin.database.save(newTrackRow);
        }
        loadFromDatabase(uuid, name, Runner.SYNC, newTrackRow, null);
    }
}
