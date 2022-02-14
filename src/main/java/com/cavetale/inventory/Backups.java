package com.cavetale.inventory;

import com.cavetale.inventory.sql.SQLBackup;
import com.cavetale.inventory.storage.InventoryStorage;
import com.cavetale.inventory.util.Items;
import java.time.Duration;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Methods to create and retrieve backups.  Manages regular backup
 * creation of all online players.
 */
@RequiredArgsConstructor
public final class Backups implements Listener {
    private final InventoryPlugin plugin;
    private Map<UUID, Session> sessions = new HashMap<>();
    private final Duration backupInterval = Duration.ofMinutes(5);

    protected void enable() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            enable(player);
        }
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
        Bukkit.getPluginManager().registerEvents(this, plugin);
        deleteOld();
    }

    protected void enable(Player player) {
        Session session = new Session();
        sessions.put(player.getUniqueId(), session);
        findLatest(player.getUniqueId(), row -> {
                session.nextBackup = row != null
                    ? row.getCreated().getTime() + backupInterval.toMillis()
                    : 10000L + System.currentTimeMillis();
            });
    }

    protected void tick() {
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, Session> entry : sessions.entrySet()) {
            Session session = entry.getValue();
            if (session.nextBackup == 0L) continue;
            if (session.nextBackup > now) continue;
            session.nextBackup = now + backupInterval.toMillis();
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null) {
                plugin.getLogger().severe("[Backups] Player not found: " + entry.getKey());
                continue;
            }
            if (player.getGameMode() != GameMode.SURVIVAL && player.getGameMode() != GameMode.ADVENTURE) {
                return;
            }
            create(player, SQLBackup.Type.INVENTORY, "Scheduled", result1 -> {
                    create(player, SQLBackup.Type.ENDER_CHEST, "Scheduled", result2 -> {
                            plugin.getLogger().info("[Backups] Finished " + player.getName()
                                                    + ": " + result1 + ", " + result2);
                        });
                });
            // Wait for next loop after one player!
            break;
        }
    }

    protected void deleteOld() {
        Date then = new Date(System.currentTimeMillis() - Duration.ofDays(7).toMillis());
        plugin.database.find(SQLBackup.class)
            .lt("created", then)
            .deleteAsync(count -> {
                    if (count <= 0) return;
                    plugin.getLogger().info("Deleted " + count + " backups older than " + then);
                });
    }

    public void find(UUID uuid, Consumer<List<SQLBackup>> callback) {
        plugin.getDatabase().find(SQLBackup.class)
            .eq("owner", uuid)
            .findListAsync(callback);
    }

    public void findLatest(UUID uuid, Consumer<SQLBackup> callback) {
        plugin.getDatabase().find(SQLBackup.class)
            .eq("owner", uuid)
            .orderByDescending("created")
            .limit(1)
            .findUniqueAsync(callback);
    }

    public void find(int id, Consumer<SQLBackup> callback) {
        plugin.getDatabase().find(SQLBackup.class)
            .eq("id", id)
            .findUniqueAsync(callback);
    }

    public void create(Player player, SQLBackup.Type type, String comment, Consumer<Boolean> callback) {
        SQLBackup.Tag tag = new SQLBackup.Tag();
        switch (type) {
        case INVENTORY:
            tag.setInventory(InventoryStorage.of(player.getInventory()));
            break;
        case ENDER_CHEST:
            tag.setEnderChest(InventoryStorage.of(player.getEnderChest()));
            break;
        default:
            throw new IllegalArgumentException("Backup type not implemented: " + type);
        }
        SQLBackup backup = new SQLBackup(player, type, tag);
        backup.setComment(comment);
        plugin.getDatabase().insertAsync(backup, result -> callback.accept(result == 1));
    }

    public void restore(Player player, SQLBackup row, Consumer<Integer> callback) {
        SQLBackup.Tag tag = row.deserialize();
        List<ItemStack> drops;
        switch (row.getTypeEnum()) {
        case INVENTORY:
            drops = tag.getInventory().restore(player.getInventory(), player.getName());
            break;
        case ENDER_CHEST:
            drops = tag.getEnderChest().restore(player.getEnderChest(), player.getName());
            break;
        default:
            throw new IllegalStateException("Backup type not implemented: " + row.getTypeEnum());
        }
        Items.give(player, drops);
        callback.accept(drops.size());
    }

    @EventHandler
    protected void onPlayerJoin(PlayerJoinEvent event) {
        enable(event.getPlayer());
    }

    @EventHandler
    protected void onPlayerQuit(PlayerQuitEvent event) {
        sessions.remove(event.getPlayer().getUniqueId());
    }

    protected static final class Session {
        protected long nextBackup;
    }
}
