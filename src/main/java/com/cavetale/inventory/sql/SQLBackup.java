package com.cavetale.inventory.sql;

import com.cavetale.core.util.Json;
import com.cavetale.inventory.storage.InventoryStorage;
import com.winthier.sql.SQLRow;
import java.util.Date;
import java.util.UUID;
import javax.persistence.Column;
import javax.persistence.Id;
import javax.persistence.Table;
import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.bukkit.entity.Player;
import static com.cavetale.core.util.CamelCase.toCamelCase;

@Data @Table(name = "backup")
public final class SQLBackup implements SQLRow {
    @Id
    private Integer id;
    @Column(nullable = false)
    private UUID owner;
    @Column(nullable = false, length = 16)
    private String ownerName;
    @Column
    private int type;
    @Column(nullable = false, length = 16777215) // MEDIUMTEXT
    String json;
    @Column(nullable = false)
    private int itemCount;
    @Column(nullable = false, columnDefinition = "TIMESTAMP DEFAULT NOW()")
    private Date created;
    @Column(nullable = true, length = 255)
    private String comment;

    public SQLBackup() { }

    @Getter
    @RequiredArgsConstructor
    public enum Type {
        INVENTORY("inv"),
        ENDER_CHEST("end"),
        STASH("sta"),
        ;

        public final String shorthand;

        public String getDisplayName() {
            return toCamelCase(" ", this);
        }

        public static Type of(String name) {
            try {
                return valueOf(name.toUpperCase());
            } catch (IllegalArgumentException iae) {
                return null;
            }
        }
    }

    @Data
    public static final class Tag {
        InventoryStorage inventory;
        InventoryStorage enderChest;
        InventoryStorage stash;

        public int getItemCount() {
            return 0
                + (inventory != null ? inventory.getCount() : 0)
                + (enderChest != null ? enderChest.getCount() : 0)
                + (stash != null ? stash.getCount() : 0);
        }

        public InventoryStorage getInventory(Type type) {
            switch (type) {
            case INVENTORY: return inventory;
            case ENDER_CHEST: return enderChest;
            case STASH: return stash;
            default: throw new IllegalArgumentException("type=" + type);
            }
        }
    }

    public SQLBackup(final Player player, final Type type, final Tag tag) {
        this(player.getUniqueId(), player.getName(), type, tag);
    }

    public SQLBackup(final UUID uuid, final String ownerName, final Type type, final Tag tag) {
        this.owner = uuid;
        this.ownerName = ownerName;
        this.type = type.ordinal();
        this.created = new Date();
        this.json = Json.serialize(tag);
        this.itemCount = tag.getItemCount();
    }

    public Tag deserialize() {
        return Json.deserialize(json, Tag.class, Tag::new);
    }

    public Type getTypeEnum() {
        final var values = Type.values();
        return type < values.length
            ? values[type]
            : null;
    }

    @Override
    public String toString() {
        return Json.serialize(this);
    }
}
