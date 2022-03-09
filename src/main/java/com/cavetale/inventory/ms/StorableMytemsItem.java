package com.cavetale.inventory.ms;

import com.cavetale.mytems.MytemTag;
import com.cavetale.mytems.Mytems;
import java.util.HashSet;
import java.util.Set;
import lombok.Getter;
import org.bukkit.inventory.ItemStack;
import static net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText;

@Getter
public final class StorableMytemsItem implements StorableItem {
    protected final Mytems mytems;
    protected final String sqlName;
    protected final Set<String> names = new HashSet<>();

    protected StorableMytemsItem(final Mytems mytems) {
        this.mytems = mytems;
        this.sqlName = mytems.id;
        names.add(plainText().serialize(mytems.getMytem().getDisplayName()));
    }

    @Override
    public StorageType getStorageType() {
        return StorageType.MYTEMS;
    }

    @Override
    public boolean canStore(ItemStack itemStack) {
        MytemTag tag = mytems.getMytem().serializeTag(itemStack);
        if (tag == null) return true;
        tag.setAmount(null);
        return tag.isDismissable();
    }

    @Override
    public boolean isValid() {
        return true;
    }

    @Override
    public ItemStack createItemStack(int amount) {
        return mytems.createItemStack(amount);
    }
}
