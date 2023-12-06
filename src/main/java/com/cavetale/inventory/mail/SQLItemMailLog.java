package com.cavetale.inventory.mail;

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

@Name("mail_logs")
@Key({"owner"})
@Data @ToString
public final class SQLItemMailLog implements SQLRow {
    public static final UUID SERVER_UUID = new UUID(0L, 0L);
    @Id private Integer id;
    @NotNull private int mailId;
    @NotNull private UUID owner;
    @Nullable private UUID sender;
    @NotNull private int itemCount;
    @MediumText @NotNull private String json;
    @Text private String message;
    @NotNull private Date created;
    @NotNull @Keyed private Date delivered;

    @AllArgsConstructor @NoArgsConstructor
    private static final class Tag {
        protected List<ItemStorage> items = List.of();
    }

    public SQLItemMailLog() { }

    public SQLItemMailLog(final SQLItemMail mail) {
        this.mailId = mail.getId();
        this.owner = mail.getOwner();
        this.sender = mail.getSender();
        this.itemCount = mail.getItemCount();
        this.json = mail.getJson();
        this.created = mail.getCreated();
        this.delivered = new Date();
    }
}
