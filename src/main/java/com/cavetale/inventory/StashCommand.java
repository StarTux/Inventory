package com.cavetale.inventory;

import com.cavetale.core.connect.ServerCategory;
import com.cavetale.core.event.player.PluginPlayerEvent;
import com.cavetale.core.menu.MenuItemEvent;
import com.cavetale.core.util.Json;
import com.cavetale.inventory.sql.SQLStash;
import com.cavetale.inventory.storage.InventoryStorage;
import com.cavetale.inventory.util.Items;
import com.cavetale.mytems.util.Gui;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.format.NamedTextColor.*;

@RequiredArgsConstructor
public final class StashCommand implements CommandExecutor {
    private final InventoryPlugin plugin;
    private final Set<UUID> openStashes = new HashSet<>();

    public void enable() {
        plugin.getCommand("stash").setExecutor(this);
    }

    @Override
    public boolean onCommand(final CommandSender sender, final Command command, final String alias, final String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("[inventory:stash] Player expected");
            return true;
        }
        if (args.length != 0) return false;
        if (!ServerCategory.current().isSurvival()) {
            player.sendMessage(text("Stash is only available in survival mode!", RED));
            return true;
        }
        if (openStashes.contains(player.getUniqueId())) {
            player.sendMessage(text("Stash is already open!", RED));
            return true;
        }
        PluginPlayerEvent.Name.OPEN_STASH.call(plugin, player);
        openStashes.add(player.getUniqueId());
        plugin.database.scheduleAsyncTask(() -> openStashAsync(player));
        return true;
    }

    private void openStashAsync(Player player) {
        SQLStash found = plugin.database.find(SQLStash.class).eq("owner", player.getUniqueId()).findUnique();
        SQLStash row;
        if (found != null) {
            row = found;
        } else {
            row = new SQLStash(player.getUniqueId());
            try {
                plugin.database.insert(row);
            } catch (Exception e) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                        openStashes.remove(player.getUniqueId());
                        player.sendMessage(text("Your stash is unavailable. Please try again later.", RED));
                    });
                throw new IllegalStateException(row.toString(), e);
            }
            if (row.getId() == null) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                        openStashes.remove(player.getUniqueId());
                        player.sendMessage(text("Your stash is unavailable. Please try again later.", RED));
                    });
                throw new IllegalStateException("failed to save: " + row);
            }
        }
        Bukkit.getScheduler().runTask(plugin, () -> openStash(player, row));
    }

    /**
     * Called by openStash(Player).
     */
    private void openStash(Player player, SQLStash row) {
        InventoryStorage inventoryStorage;
        if (row.getJson() != null) {
            // If the row contains an inventory, we need to clear the
            // row and "lock" it.
            inventoryStorage = Json.deserialize(row.getJson(), InventoryStorage.class, () -> null);
            row.setAccessNow();
            row.setJson(null);
            row.setItemCount(0);
            int success = plugin.database.update(SQLStash.class)
                .row(row)
                .update("access", "item_count", "json")
                .atomic("version", row.getVersion() + 1)
                .sync();
            if (success <= 0) {
                player.sendMessage(text("Your stash is unavailable. Please try again later.", RED));
                throw new IllegalStateException("atomic update failed: " + row);
            }
        } else {
            inventoryStorage = null;
        }
        Gui gui = new Gui(plugin)
            .title(text("Stash"))
            .size(6 * 9);
        gui.setEditable(true);
        if (inventoryStorage != null) {
            List<ItemStack> drops = inventoryStorage.restore(gui.getInventory(), player.getName());
            Items.give(player, drops);
        }
        gui.onClose(event -> onClose(player, row, gui));
        gui.setItem(Gui.OUTSIDE, null, click -> {
                if (!click.isLeftClick()) return;
                MenuItemEvent.openMenu(player);
            });
        gui.open(player);
        player.playSound(player.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, SoundCategory.MASTER, 0.75f, 2.0f);
    }

    private void onClose(Player player, SQLStash row, Gui gui) {
        InventoryStorage inventoryStorage = InventoryStorage.of(gui.getInventory());
        row.setJson(Json.serialize(inventoryStorage));
        row.setAccessNow();
        row.setItemCount(inventoryStorage.getCount());
        int success = 0;
        try {
            success = plugin.database.update(SQLStash.class)
                .row(row)
                .update("access", "item_count", "json")
                .atomic("version", row.getVersion() + 1)
                .sync();
        } catch (Exception e) {
            success = 0;
            plugin.getLogger().warning(player.getName() + ": Saving failed. Returning: " + inventoryStorage);
            e.printStackTrace();
        }
        if (success <= 0) {
            player.sendMessage(text("Your stash is unavailable. Please try again later.", RED));
            List<ItemStack> list = new ArrayList<>();
            for (int i = 0; i < gui.getInventory().getSize(); i += 1) {
                ItemStack itemStack = gui.getInventory().getItem(i);
                if (itemStack == null || itemStack.getType() == Material.AIR) continue;
                list.add(itemStack);
                gui.getInventory().setItem(i, null);
            }
            Items.give(player, list);
        }
        gui.getInventory().clear();
        player.playSound(player.getLocation(), Sound.BLOCK_ENDER_CHEST_CLOSE, SoundCategory.MASTER, 0.75f, 2.0f);
        openStashes.remove(player.getUniqueId());
    }
}
