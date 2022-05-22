package com.cavetale.inventory.sql;

import com.cavetale.inventory.storage.InventoryStorage;
import com.cavetale.inventory.storage.ItemStorage;
import com.winthier.sql.SQLRow;
import java.util.Date;
import java.util.UUID;
import javax.persistence.Column;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.Table;
import lombok.Data;

/**
 * Stores entire player inventories in the database.  Contents
 * contains the inventory and ender chest.  Rows are created once and
 * after that, only the claimed field is updated, the rest remains
 * untouched.  Thus, there maybe be multiple rows for each player
 * available at any time.
 *
 * When the inventory of a player is restored, it is expected that all
 * rows belonging to them are fetched from the databased, their
 * claimed field (which is optimistically locked) set, and the items
 * put in the player's inventory.
 */
@Table(name = "inventories",
       indexes = {
           @Index(unique = false, name = "owner", columnList = "owner"),
           @Index(unique = false, name = "claimed", columnList = "claimed"),
       })
@Data
public final class SQLInventory implements SQLRow {
    @Id
    private Integer id;
    @Column(nullable = false)
    private UUID owner;
    @Column(nullable = false)
    private int track;
    @Column(nullable = false, length = 16777215) // MEDIUMTEXT
    private String json;
    @Column(nullable = false)
    private int itemCount;
    @Column(nullable = false)
    private Date created;
    @Column(nullable = true)
    private Date claimed;

    public enum Track {
        SURVIVAL,
        DUTYMODE;
    }

    @Data
    public static final class Tag {
        protected InventoryStorage inventory;
        protected InventoryStorage enderChest;
        protected ItemStorage cursor;

        public boolean isEmpty() {
            return (inventory == null || inventory.isEmpty())
                && (enderChest == null || enderChest.isEmpty())
                && cursor == null;
        }

        public int getItemCount() {
            return (inventory != null ? inventory.getCount() : 0)
                + (enderChest != null ? enderChest.getCount() : 0)
                + (cursor != null ? cursor.getAmount() : 0);
        }
    }

    public SQLInventory() { }

    public SQLInventory(final UUID owner, final Track track, final String json, final int itemCount) {
        this.owner = owner;
        this.track = track.ordinal();
        this.json = json;
        this.itemCount = itemCount;
        this.created = new Date();
        this.claimed = null;
    }

    public Track getTrackEnum() {
        return Track.values()[track];
    }
}
