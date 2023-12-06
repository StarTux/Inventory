package com.cavetale.inventory.mail;

import com.cavetale.core.util.Json;
import com.cavetale.inventory.storage.ItemStorage;
import com.winthier.sql.SQLRow.Key;
import com.winthier.sql.SQLRow.Name;
import com.winthier.sql.SQLRow;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import static com.cavetale.inventory.InventoryPlugin.plugin;
import static java.util.logging.Level.SEVERE;
import static net.kyori.adventure.text.Component.empty;

@Name("mails")
@Key({"owner"})
@Data @ToString
public final class SQLItemMail implements SQLRow {
    public static final UUID SERVER_UUID = new UUID(0L, 0L);
    @Id private Integer id;
    @NotNull private UUID owner;
    @Nullable private UUID sender;
    @NotNull private int itemCount;
    @MediumText @NotNull private String json;
    @Text private String message;
    @NotNull private Date created;

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
        this.message = GsonComponentSerializer.gson().serialize(message);
        this.created = new Date();
    }

    public List<ItemStorage> getItemList() {
        Tag tag = Json.deserialize(json, Tag.class, Tag::new);
        return tag.items;
    }

    public Component getMessageComponent() {
        try {
            return message != null
                ? GsonComponentSerializer.gson().deserialize(message)
                : empty();
        } catch (Exception e) {
            plugin().getLogger().log(SEVERE, "SQLItemMail.id=" + id, e);
            return empty();
        }
    }
}
