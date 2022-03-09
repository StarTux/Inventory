package com.cavetale.inventory.ms;

import com.cavetale.mytems.Mytems;
import java.util.UUID;
import javax.persistence.Column;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import lombok.Data;
import org.bukkit.Material;

@Data
@Table(name = "mass_storage",
       uniqueConstraints = {
           @UniqueConstraint(columnNames = {
                   "owner",
                   "type",
                   "name",
               }),
       })
public final class SQLMassStorage {
    @Id
    private Integer id;

    @Column(nullable = false)
    private UUID owner;

    @Column(nullable = false)
    private int type; // StorageType

    @Column(nullable = false, length = 40)
    private String name;

    @Column(nullable = false)
    private int amount;

    public SQLMassStorage() { }

    public SQLMassStorage(final UUID uuid, final Material material) {
        this.owner = uuid;
        this.type = StorageType.BUKKIT.id;
        this.name = material.name().toLowerCase();
    }

    public SQLMassStorage(final UUID uuid, final Mytems mytems) {
        this.owner = uuid;
        this.type = StorageType.MYTEMS.id;
        this.name = mytems.name().toLowerCase();
    }

    public SQLMassStorage(final UUID uuid, final StorableItem storable) {
        this.owner = uuid;
        this.type = storable.getStorageType().id;
        this.name = storable.getSqlName();
    }

    public StorageType getStorageType() {
        return StorageType.of(type);
    }
}
