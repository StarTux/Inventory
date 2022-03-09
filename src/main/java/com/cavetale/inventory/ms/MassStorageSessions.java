package com.cavetale.inventory.ms;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

@RequiredArgsConstructor
public final class MassStorageSessions implements Listener {
    private final MassStorage ms;
    private final Map<UUID, MassStorageSession> sessionsMap = new HashMap<>();

    protected void enable() {
        Bukkit.getPluginManager().registerEvents(this, ms.plugin);
        for (Player player : Bukkit.getOnlinePlayers()) {
            of(player);
        }
    }

    public MassStorageSession of(Player player) {
        return sessionsMap.computeIfAbsent(player.getUniqueId(), u -> new MassStorageSession(ms, u));
    }

    @EventHandler
    private void onPlayerJoin(PlayerJoinEvent event) {
        of(event.getPlayer()).fill();
    }

    @EventHandler
    private void onPlayerQuit(PlayerQuitEvent event) {
        sessionsMap.remove(event.getPlayer().getUniqueId());
    }
}
