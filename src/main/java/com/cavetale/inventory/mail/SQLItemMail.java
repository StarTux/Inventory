package com.cavetale.inventory.mail;

import com.cavetale.core.util.Json;
import com.cavetale.inventory.storage.ItemStorage;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import javax.persistence.Column;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import static net.kyori.adventure.text.Component.empty;

@Table(name = "mails",
       indexes = @Index(unique = false, name = "owner", columnList = "owner"))
@Data
public final class SQLItemMail {
    public static final UUID SERVER_UUID = new UUID(0L, 0L);

    @Id
    private Integer id;

    @Column(nullable = false)
    private UUID owner;

    @Column(nullable = true)
    private UUID sender;

    @Column(nullable = false)
    private int itemCount;

    @Column(nullable = false, length = 16777215) // MEDIUMTEXT
    private String json;

    @Column(nullable = true, length = 255)
    private String message;

    @Column(nullable = false)
    private Date created;

    public SQLItemMail() { }

    @AllArgsConstructor @NoArgsConstructor
    private static final class Tag {
        protected List<ItemStorage> items = List.of();
    }

    public SQLItemMail(final UUID from, final UUID to, final List<ItemStorage> items, final Component message) {
        this.sender = from;
        this.owner = to;
        for (ItemStorage it : items) {
            this.itemCount += it.getAmount();
        }
        this.json = Json.serialize(new Tag(items));
        this.message = (message != null && !empty().equals(message))
            ? GsonComponentSerializer.gson().serialize(message)
            : null;
        this.created = new Date();
    }

    public List<ItemStorage> getItemList() {
        Tag tag = Json.deserialize(json, Tag.class, Tag::new);
        return tag.items;
    }

    public Component getMessageComponent() {
        return message != null
            ? GsonComponentSerializer.gson().deserialize(message)
            : empty();
    }
}
