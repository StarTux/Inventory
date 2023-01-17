package com.cavetale.inventory;

import com.cavetale.core.command.CommandWarn;
import com.cavetale.core.command.RemotePlayer;
import com.cavetale.core.connect.Connect;
import com.cavetale.core.connect.ServerGroup;
import com.cavetale.core.event.connect.ConnectMessageEvent;
import com.cavetale.core.event.hud.PlayerHudEvent;
import com.cavetale.core.event.hud.PlayerHudPriority;
import com.cavetale.core.perm.Perm;
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
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.inventory.ItemStack;
import static com.cavetale.inventory.mail.ItemMail.refreshUserCache;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.event.ClickEvent.runCommand;
import static net.kyori.adventure.text.event.HoverEvent.showText;
import static net.kyori.adventure.text.format.NamedTextColor.*;
import static net.kyori.adventure.text.format.TextDecoration.*;

/**
 * This class, when activated, will take care of player inventories so
 * that they are stored in the database when the user logs out, and
 * loaded from database when they join.
 */
@RequiredArgsConstructor
public final class InventoryStore implements Listener {
    private static final String MESSAGE_STORED = "inventory:stored";
    private static final String GROUP = "dutymode";
    private final InventoryPlugin plugin;
    private final Map<UUID, StoreSession> sessions = new HashMap<>();
    private boolean disabled;

    protected void enable() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        deleteOld();
        for (Player player : Bukkit.getOnlinePlayers()) {
            sessions.put(player.getUniqueId(), new StoreSession(player.getUniqueId(), player.getName()));
        }
        for (SQLTrack track : plugin.database.find(SQLTrack.class).findList()) {
            StoreSession session = sessions.get(track.getPlayer());
            if (session != null) session.trackRow = track;
        }
    }

    protected void disable() {
        sessions.clear();
        disabled = true;
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
     * Store and clear inventory.
     * Called during logout or player switch.
     */
    private void storeToDatabase(Player player, StoreSession session, Runner runner, boolean withGameMode, Consumer<Integer> callback) {
        final UUID uuid = player.getUniqueId();
        final String name = player.getName();
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
        GameMode gameMode = withGameMode || session.getTrack() == 1
            ? player.getGameMode()
            : null;
        runner.sql(() -> {
                storeToDatabaseSQL(session, runner, tag, gameMode, callback);
            });
    }

    /**
     * Called only by the method above, in the sql thread.
     */
    private void storeToDatabaseSQL(StoreSession session, Runner runner, SQLInventory.Tag tag, GameMode gameMode, Consumer<Integer> callback) {
        SQLInventory row = new SQLInventory(session.uuid, session.getTrack(), Json.serialize(tag), tag.getItemCount());
        row.setGameMode(gameMode);
        int result = plugin.database.insert(row);
        plugin.getLogger().info("[Store] Stored " + session
                                + " items=" + tag.getItemCount()
                                + (gameMode != null ? " gamemode=" + gameMode : "")
                                + " id=" + row.getId()
                                + " result=" + result);
        runner.main(() -> {
                Connect.get().broadcastMessage(ServerGroup.current(), MESSAGE_STORED, session.uuid.toString());
                if (callback != null) callback.accept(result);
            });
    }

    /**
     * Called during login or refresh.
     */
    private void loadFromDatabase(StoreSession session) {
        if (session.loading) {
            session.scheduled = true;
            return;
        }
        session.loading = true;
        Runner runner = Runner.ASYNC;
        runner.sql(() -> {
                if (!session.loaded) {
                    session.loaded = true;
                    session.trackRow = plugin.database.find(SQLTrack.class)
                        .eq("player", session.uuid)
                        .findUnique();
                }
                loadFromDatabaseWithTrack(session, runner, r -> {
                        session.loading = false;
                        if (session.scheduled) {
                            session.scheduled = false;
                            loadFromDatabase(session);
                        }
                    });
            });
    }

    /**
     * Called in sql thread during when the track is already known.
     */
    private void loadFromDatabaseWithTrack(StoreSession session, Runner runner, Consumer<Integer> callback) {
        List<SQLInventory> list = plugin.database.find(SQLInventory.class)
            .eq("owner", session.uuid)
            .isNull("claimed")
            .eq("track", session.getTrack())
            .findList();
        if (list.isEmpty()) {
            if (callback != null) {
                runner.main(() -> callback.accept(0));
            }
            return;
        }
        final Date now = new Date();
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
        runner.main(() -> loadFromDatabaseCallback(session, list, callback));
    }

    /** Always called in main thread. */
    private void loadFromDatabaseCallback(StoreSession session, List<SQLInventory> list, Consumer<Integer> callback) {
        if (disabled || session.disabled) {
            for (SQLInventory row : list) {
                plugin.getLogger().warning("[Store] Disabled: " + session.name + " id=" + row.getId() + " claimed=" + row.getClaimed());
                row.setClaimed(null);
                plugin.database.update(row, "claimed");
            }
            callback.accept(0);
            return;
        }
        final Player player = Bukkit.getPlayer(session.uuid);
        if (player == null || !player.isOnline()) {
            plugin.getLogger().warning("[Store] Player left: " + session.name + ": " + list);
            for (SQLInventory row : list) {
                row.setClaimed(null);
                plugin.database.updateAsync(row, null, "claimed");
            }
            callback.accept(0);
            return;
        }
        // Restore
        for (SQLInventory row : list) {
            final SQLInventory.Tag tag;
            try {
                tag = Json.deserialize(row.getJson(), SQLInventory.Tag.class);
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "[Store] deserialize failed: " + row, e);
                continue;
            }
            List<ItemStack> drops = new ArrayList<>();
            tag.restore(player, drops);
            drops.removeIf(Objects::isNull);
            if (!drops.isEmpty()) {
                List<ItemStorage> drops2 = new ArrayList<>();
                for (ItemStack it : drops) {
                    drops2.add(ItemStorage.of(it));
                }
                SQLItemMail mail = new SQLItemMail(SQLItemMail.SERVER_UUID, session.uuid, drops2,
                                                   text("You dropped this earlier"));
                plugin.database.insertAsync(mail, i -> {
                        player.sendMessage(text("You dropped something. Click here to pick it up.", YELLOW)
                                           .hoverEvent(showText(text("/imail", YELLOW)))
                                           .clickEvent(runCommand("/imail")));
                        refreshUserCache();
                    });
            }
            if (row.getGameMode() != null) player.setGameMode(row.getGameMode());
            plugin.getLogger().info("[Store] Restored " + player.getName()
                                    + " track=" + row.getTrack()
                                    + " items=" + tag.getItemCount()
                                    + " drops=" + drops.size()
                                    + " gamemode=" + row.getGameMode()
                                    + " id=" + row.getId());
        }
        if (session.isInDutymode() && player.hasPermission("inventory.duty.op")) {
            player.setOp(true);
        } else {
            player.setOp(false);
        }
        callback.accept(list.size());
    }

    @EventHandler(priority = EventPriority.HIGH)
    private void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        StoreSession session = new StoreSession(player.getUniqueId(), player.getName());
        sessions.put(session.uuid, session);
        loadFromDatabase(session);
    }

    @EventHandler(priority = EventPriority.HIGH)
    private void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        StoreSession session = sessions.remove(player.getUniqueId());
        if (session == null) return;
        session.disabled = true;
        storeToDatabase(player, session, Runner.ASYNC, false, null);
    }

    @EventHandler
    private void onPlayerHud(PlayerHudEvent event) {
        StoreSession session = sessions.get(event.getPlayer().getUniqueId());
        if (session == null || !session.isInDutymode()) return;
        Component message = event.getPlayer().isOp()
            ? text("Dutymode! (OP)", DARK_RED, BOLD)
            : text("Dutymode!", DARK_RED, BOLD);
        event.bossbar(PlayerHudPriority.HIGHEST, message, BossBar.Color.RED, BossBar.Overlay.PROGRESS, 1.0f);
        event.sidebar(PlayerHudPriority.HIGHEST, List.of(message));
        event.footer(PlayerHudPriority.HIGHEST, List.of(message));
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
            StoreSession session = sessions.get(uuid);
            if (session == null) return;
            plugin.getLogger().info("[Store] Update received: " + player.getName());
            loadFromDatabase(session);
        }
    }

    protected void duty(RemotePlayer player, Boolean dutymode) {
        if (player.isPlayer()) {
            dutyPlayer(player.getPlayer(), dutymode);
        } else {
            if (dutymode == Boolean.TRUE) throw new CommandWarn("Cannot enter dutymode remotely!");
            dutyRemote(player);
        }
    }

    private void dutyPlayer(Player player, Boolean dutymode) {
        StoreSession session = sessions.get(player.getUniqueId());
        if (session == null || session.loading) {
            throw new CommandWarn("Session not ready! Please try again later");
        }
        if (dutymode == null) dutymode = !session.isInDutymode();
        if (dutymode == session.isInDutymode()) {
            if (session.isInDutymode()) {
                throw new CommandWarn("You are already in dutymode");
            } else {
                throw new CommandWarn("You are not in dutymode");
            }
        }
        if (dutymode) {
            switchTrack(player, session, true);
            player.sendMessage(text("Dutymode enabled", DARK_RED, BOLD));
            return;
        }
        // dutymode == false
        if (session.trackRow.isOnThisServer()) {
            Location location = session.trackRow.getLocation();
            if (location != null) player.teleport(location, TeleportCause.COMMAND);
            switchTrack(player, session, false);
            player.sendMessage(text("Dutymode disabled", YELLOW, BOLD));
            return;
        }
        if (Connect.get().serverIsOnline(session.trackRow.getServer())) {
            Connect.get().dispatchRemoteCommand(player, "duty off", session.trackRow.getServer());
            return;
        }
        String originServer = session.trackRow.getServer();
        switchTrack(player, session, false);
        player.sendMessage(text("Dutymode disabled. Origin server down: " + originServer, YELLOW, BOLD));
    }

    private void dutyRemote(RemotePlayer player) {
        SQLTrack track = plugin.database.find(SQLTrack.class).eq("player", player.getUniqueId()).findUnique();
        if (track == null) throw new CommandWarn("Not in dutymode!");
        Location location = track.getLocation();
        if (location == null) location = Bukkit.getWorlds().get(0).getSpawnLocation();
        player.bring(plugin, location, entity -> {
                plugin.database.delete(track);
                Perm.get().removeGroup(player.getUniqueId(), GROUP);
                entity.sendMessage(text("Dutymode disabled", YELLOW, BOLD));
            });
    }

    private void switchTrack(Player player, StoreSession session, boolean dutymode) {
        storeToDatabase(player, session, Runner.SYNC, true, null);
        if (dutymode) {
            session.trackRow = new SQLTrack(player, 1);
            plugin.database.insert(session.trackRow);
        } else {
            plugin.database.delete(session.trackRow);
            session.trackRow = null;
        }
        loadFromDatabaseWithTrack(session, Runner.SYNC, r -> { });
        if (dutymode) {
            player.setGameMode(GameMode.CREATIVE);
            player.setFlying(true);
            Perm.get().addGroup(player.getUniqueId(), GROUP);
        } else {
            Perm.get().removeGroup(player.getUniqueId(), GROUP);
        }
        if (dutymode && player.hasPermission("inventory.duty.op")) {
            player.setOp(true);
        } else {
            player.setOp(false);
        }
        plugin.getLogger().info("[Store] Switch " + session.name + " " + dutymode);
    }

    public boolean isInDutymode(Player player) {
        StoreSession session = sessions.get(player.getUniqueId());
        return session != null && session.isInDutymode();
    }

    public boolean isLoaded(Player player) {
        StoreSession session = sessions.get(player.getUniqueId());
        return session != null && session.loaded;
    }
}
