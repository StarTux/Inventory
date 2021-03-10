package com.cavetale.inventory;

import java.util.Date;
import java.util.UUID;
import javax.persistence.Column;
import javax.persistence.Id;
import javax.persistence.Table;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

@Getter @Setter @Table(name = "inventories")
public final class SQLInventory {
    @Id
    Integer id;
    @Column(nullable = false)
    UUID uuid;
    @Column(nullable = true, length = 16777215) // MEDIUMTEXT
    String json;
    @Column(nullable = false)
    Date created;
    @Column(nullable = true)
    Date claimed;
    @Column(nullable = true)
    Date restored;

    public SQLInventory() { }

    SQLInventory(@NonNull final UUID uuid, @NonNull final String json, final Date created) {
        this.uuid = uuid;
        this.json = json;
        this.created = created;
    }
}
