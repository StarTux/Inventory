package com.cavetale.inventory.sql;

import com.cavetale.core.connect.NetworkServer;
import com.winthier.sql.SQLRow.Name;
import com.winthier.sql.SQLRow.NotNull;
import com.winthier.sql.SQLRow;
import java.util.Date;
import java.util.UUID;
import lombok.Data;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * Store tracks for each player.  Absence of a row implies track 0.
 * Effectively only players in duty mode will have track 1.
 */
@Data @NotNull @Name("tracks")
public final class SQLTrack implements SQLRow {
    @Id private Integer id;
    @Unique private UUID player;
    @Keyed private int track;
    @VarChar(40) private String server;
    @VarChar(40) private String world;
    private double x;
    private double y;
    private double z;
    @Default("0") private float pitch;
    @Default("0") private float yaw;
    @Default("NOW()") Date updated;

    public SQLTrack() { }

    public SQLTrack(final Player player, final int track) {
        this.player = player.getUniqueId();
        this.track = track;
        this.updated = new Date();
        setLocation(player.getLocation());
    }

    public void setLocation(Location location) {
        this.server = NetworkServer.current().registeredName;
        this.world = location.getWorld().getName();
        this.x = location.getX();
        this.y = location.getY();
        this.z = location.getZ();
        this.pitch = location.getPitch();
        this.yaw = location.getYaw();
    }

    public Location getLocation() {
        World w = Bukkit.getWorld(world);
        if (w == null) return null;
        return new Location(w, x, y, z, yaw, pitch);
    }

    public boolean isOnThisServer() {
        return server.equals(NetworkServer.current().registeredName);
    }
}
