package com.cavetale.inventory.sql;

import com.cavetale.inventory.storage.InventoryStorage;
import com.cavetale.inventory.storage.ItemStorage;
import com.cavetale.inventory.storage.PlayerStatusStorage;
import com.winthier.sql.SQLRow.Key;
import com.winthier.sql.SQLRow.Name;
import com.winthier.sql.SQLRow.NotNull;
import com.winthier.sql.SQLRow;
import java.util.Date;
import java.util.UUID;
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
@Data @NotNull @Name("inventories")
@Key({"owner", "claimed", "track"})
@Key("claimed")
public final class SQLInventory implements SQLRow {
    @Id private Integer id;
    private UUID owner;
    private int track;
    @MediumText private String json;
    private int itemCount;
    private Date created;
    @Nullable private Date claimed;

    @Data
    public static final class Tag {
        protected InventoryStorage inventory;
        protected InventoryStorage enderChest;
        protected ItemStorage cursor;
        protected PlayerStatusStorage status;

        public boolean isEmpty() {
            return (inventory == null || inventory.isEmpty())
                && (enderChest == null || enderChest.isEmpty())
                && cursor == null
                && status == null;
        }

        public int getItemCount() {
            return (inventory != null ? inventory.getCount() : 0)
                + (enderChest != null ? enderChest.getCount() : 0)
                + (cursor != null ? cursor.getAmount() : 0);
        }
    }

    public SQLInventory() { }

    public SQLInventory(final UUID owner, final int track, final String json, final int itemCount) {
        this.owner = owner;
        this.track = track;
        this.json = json;
        this.itemCount = itemCount;
        this.created = new Date();
        this.claimed = null;
    }
}
