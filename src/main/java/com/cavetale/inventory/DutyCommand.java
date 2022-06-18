package com.cavetale.inventory;

import com.cavetale.core.command.AbstractCommand;
import com.cavetale.core.command.CommandArgCompleter;
import com.cavetale.core.command.CommandNode;
import com.cavetale.core.command.CommandWarn;
import com.cavetale.core.command.RemotePlayer;
import com.cavetale.core.connect.Connect;
import com.cavetale.core.perm.Perm;
import com.cavetale.inventory.sql.SQLTrack;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.format.NamedTextColor.*;

public final class DutyCommand extends AbstractCommand<InventoryPlugin> {
    protected DutyCommand(final InventoryPlugin plugin) {
        super(plugin, "duty");
    }

    @Override
    protected void onEnable() {
        rootNode.arguments("[on|off]")
            .description("Toggle duty mode")
            .completers(CommandArgCompleter.ignoreCaseList(List.of("on", "off")))
            .remotePlayerCaller(this::duty);
    }

    private boolean duty(RemotePlayer player, String[] args) {
        if (args.length == 1) {
            final boolean dutymode;
            switch (args[0]) {
            case "on": dutymode = true; break;
            case "off": dutymode = false; break;
            default: return false;
            }
            duty(player, dutymode);
            return true;
        } else if (args.length == 0) {
            plugin.database.find(SQLTrack.class).eq("player", player.getUniqueId()).findUniqueAsync(row -> {
                    int track = row != null
                        ? row.getTrack()
                        : 0;
                    boolean hadDuty = track != 0;
                    CommandNode.wrap(player, () -> duty(player, !hadDuty));
                });
            return true;
        } else {
            return false;
        }
    }

    private void duty(RemotePlayer player, boolean dutymode) {
        if (dutymode) {
            if (!player.isPlayer()) throw new CommandWarn("Something went wrong!");
            if (plugin.inventoryStore != null) {
                plugin.inventoryStore.switchTrack(player.getPlayer(), 1);
                player.getPlayer().setGameMode(GameMode.CREATIVE);
            } else {
                plugin.database.save(new SQLTrack(player.getPlayer(), 1));
            }
            Perm.get().addGroup(player.getUniqueId(), "dutymode");
            player.sendMessage(text("Dutymode enabled", AQUA));
            return;
        }
        plugin.database.find(SQLTrack.class).eq("player", player.getUniqueId()).findUniqueAsync(row -> {
                if (row == null) throw new CommandWarn("You are not in dutymode!");
                if (player.isPlayer() && !row.isThisServer() && Connect.get().getOnlineServerNames().contains(row.getServer())) {
                    Connect.get().dispatchRemoteCommand(player.getPlayer(), "duty", row.getServer());
                } else if (player.isPlayer()) {
                    // Online
                    Location location = row.getLocation();
                    if (location != null) player.getPlayer().teleport(location, TeleportCause.COMMAND);
                    if (plugin.inventoryStore != null) {
                        plugin.inventoryStore.switchTrack(player.getPlayer(), 0);
                    } else {
                        plugin.database.delete(row);
                    }
                    Perm.get().removeGroup(player.getUniqueId(), "dutymode");
                    player.sendMessage(text("Dutymode disabled", YELLOW));
                } else {
                    // Remote
                    Location location = row.getLocation();
                    if (location == null) location = Bukkit.getWorlds().get(0).getSpawnLocation();
                    player.bring(plugin, location, player2 -> {
                            if (player2 == null) {
                                player.sendMessage(text("Something went wrong!", RED));
                                return;
                            }
                            plugin.database.delete(row); // BEFORE onPlayerJoin() in Store
                            Perm.get().removeGroup(player.getUniqueId(), "dutymode");
                            player2.sendMessage(text("Dutymode disabled", YELLOW));
                        });
                }
            });
    }
}
