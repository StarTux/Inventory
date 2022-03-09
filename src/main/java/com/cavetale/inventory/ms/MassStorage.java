package com.cavetale.inventory.ms;

import com.cavetale.inventory.InventoryPlugin;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;

public final class MassStorage implements Listener {
    public static final String PERMISSION = "inventory.ms";
    protected final InventoryPlugin plugin;
    protected final StorableItemIndex itemIndex = new StorableItemIndex();
    protected final MassStorageSessions sessions = new MassStorageSessions(this);
    protected final MassStorageCommand command;

    public MassStorage(final InventoryPlugin plugin) {
        this.plugin = plugin;
        this.command = new MassStorageCommand(plugin, this);
    }

    public void enable() {
        itemIndex.populate();
        sessions.enable();
        command.enable();
    }

    public void setupPlayer(Player player) {
        List<SQLMassStorage> list = new ArrayList<>();
        UUID uuid = player.getUniqueId();
        for (StorableItem it : itemIndex.all) {
            list.add(new SQLMassStorage(uuid, it));
        }
    }

    public boolean insert(Player player, ItemStack itemStack, Consumer<Boolean> callback) {
        if (itemStack == null || itemStack.getType() == Material.AIR || itemStack.getAmount() < 1) return false;
        StorableItem storable = itemIndex.get(itemStack);
        if (!storable.isValid()) return false;
        insert(player.getUniqueId(), storable, itemStack.getAmount(), callback);
        return true;
    }

    public void insert(UUID uuid, StorableItem storable, int amount, Consumer<Boolean> callback) {
        final StorageType type = storable.getStorageType();
        final String name = storable.getSqlName();
        String sql = "INSERT INTO `" + plugin.getDatabase().getTable(SQLMassStorage.class).getTableName() + "`"
            + " (`owner`, `type`, `name`, `amount`)"
            + " VALUES"
            + " (\"" + uuid + "\", " + type.id + ", \"" + name + "\", " + amount + ")"
            + " ON DUPLICATE KEY UPDATE `amount` = `amount` + VALUES(`amount`)";
        plugin.getDatabase().executeUpdateAsync(sql, result -> {
                if (result == 0) {
                    plugin.getLogger().severe("[MassStorage] Insert failed!"
                                              + " result=" + result
                                              + " player=" + uuid
                                              + " type=" + type
                                              + " item=" + name + " x " + amount);
                }
                callback.accept(result > 0);
            });
    }

    public void remove(UUID uuid, StorableItem storable, int amount, Consumer<Boolean> callback) {
        final StorageType type = storable.getStorageType();
        final String name = storable.getSqlName();
        plugin.getDatabase().update(SQLMassStorage.class)
            .subtract("amount", amount)
            .where(c -> c
                   .eq("owner", uuid)
                   .eq("type", type.id)
                   .eq("name", name)
                   .gte("amount", amount))
            .async(result -> callback.accept(result > 0));
    }

    public void find(UUID uuid, Consumer<List<SQLMassStorage>> callback) {
        plugin.getDatabase().find(SQLMassStorage.class)
            .eq("owner", uuid)
            .findListAsync(callback);
    }
}
