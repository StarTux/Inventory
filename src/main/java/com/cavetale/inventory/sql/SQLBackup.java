package com.cavetale.inventory.sql;

import com.cavetale.inventory.storage.InventoryStorage;
import com.cavetale.inventory.util.Json;
import java.util.Date;
import java.util.UUID;
import javax.persistence.Column;
import javax.persistence.Id;
import javax.persistence.Table;
import lombok.Data;
import org.bukkit.entity.Player;

@Data @Table(name = "backup")
public final class SQLBackup {
    @Id
    private Integer id;
    @Column(nullable = false)
    private UUID owner;
    @Column(nullable = false, length = 16)
    private String ownerName;
    @Column(nullable = false, length = 16777215) // MEDIUMTEXT
    String json;
    @Column(nullable = false)
    private int itemCount;
    @Column(nullable = false, columnDefinition = "TIMESTAMP DEFAULT NOW()")
    private Date created;

    public SQLBackup() { }

    @Data
    public static final class Tag {
        InventoryStorage inventory;
        InventoryStorage enderChest;
    }

    public SQLBackup(final Player player, final Tag tag) {
        this(player.getUniqueId(), player.getName(), tag);
    }

    public SQLBackup(final UUID uuid, final String ownerName, final Tag tag) {
        this.owner = uuid;
        this.ownerName = ownerName;
        this.created = new Date();
        this.json = Json.serialize(tag);
        this.itemCount = tag.inventory.getCount() + tag.enderChest.getCount();
    }

    public Tag deserialize() {
        return Json.deserialize(json, Tag.class, Tag::new);
    }

    @Override
    public String toString() {
        return Json.serialize(this);
    }
}
