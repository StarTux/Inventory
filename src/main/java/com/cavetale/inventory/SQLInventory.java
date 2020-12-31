package com.cavetale.inventory;

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
    @Column(nullable = false, unique = true)
    UUID uuid;
    @Column(nullable = true, length = 4096)
    String json;

    public SQLInventory() { }

    SQLInventory(@NonNull final UUID uuid, @NonNull final String json) {
        this.uuid = uuid;
        this.json = json;
    }
}
