package com.cavetale.inventory.sql;

import com.cavetale.inventory.util.Json;
import java.util.Date;
import java.util.UUID;
import javax.persistence.Column;
import javax.persistence.Id;
import javax.persistence.Table;
import lombok.Data;

@Data @Table(name = "stash")
public final class SQLStash {
    @Id
    private Integer id;
    @Column(nullable = false, unique = true)
    private UUID owner;
    @Column(nullable = true, length = 16777215) // MEDIUMTEXT
    String json;
    @Column(nullable = false, columnDefinition = "INT(3)")
    private int itemCount;
    @Column(nullable = false, columnDefinition = "INT(11) DEFAULT 0")
    private int version;
    @Column(nullable = false, columnDefinition = "TIMESTAMP DEFAULT NOW()")
    private Date access;

    public SQLStash() { }

    public SQLStash(final UUID uuid) {
        this.owner = uuid;
    }

    public void setAccessNow() {
        access = new Date();
    }

    @Override
    public String toString() {
        return Json.serialize(this);
    }
}
