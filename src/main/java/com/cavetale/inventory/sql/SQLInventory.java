package com.cavetale.inventory.sql;

import com.cavetale.inventory.storage.InventoryStorage;
import com.cavetale.inventory.storage.ItemStorage;
import com.cavetale.inventory.storage.PlayerStatusStorage;
import com.winthier.sql.SQLRow;
import com.winthier.sql.SQLRow.Key;
import com.winthier.sql.SQLRow.Name;
import com.winthier.sql.SQLRow.NotNull;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import lombok.Data;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

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
    @Nullable private GameMode gameMode;
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

        /**
         * Give all items to player and put drops in list.
         */
        public void restore(Player player, List<ItemStack> drops) {
            if (inventory != null) {
                drops.addAll(inventory.restore(player.getInventory(), "InventoryStore:inventory:" + player.getName()));
            }
            if (enderChest != null) {
                drops.addAll(enderChest.restore(player.getEnderChest(), "InventoryStore:enderChest:" + player.getName()));
            }
            if (cursor != null) {
                drops.add(cursor.toItemStack());
            }
            if (status != null) {
                status.restore(player);
            }
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

    public boolean isClaimed() {
        return claimed != null;
    }
}
