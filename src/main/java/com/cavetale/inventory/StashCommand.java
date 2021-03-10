package com.cavetale.inventory;

import com.cavetale.inventory.gui.Gui;
import com.cavetale.inventory.sql.SQLStash;
import com.cavetale.inventory.storage.InventoryStorage;
import com.cavetale.inventory.util.Items;
import com.cavetale.inventory.util.Json;
import com.cavetale.mytems.Mytems;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.Tag;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

// TODO: Improve cross-server synchronization. An extremely unlikely
// turn of events could open the stash on one server while it's still
// open and not saved on another.
@RequiredArgsConstructor
public final class StashCommand implements CommandExecutor {
    private final InventoryPlugin plugin;

    public void enable() {
        plugin.getCommand("stash").setExecutor(this);
    }

    @Override
    public boolean onCommand(final CommandSender sender, final Command command, final String alias, final String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("[inventory:stash] Player expected");
            return true;
        }
        if (args.length != 0) return false;
        Player player = (Player) sender;
        if (stashOf(player) != null) return true;
        openStash(player);
        return true;
    }

    public Gui stashOf(Player player) {
        Gui gui = Gui.of(player);
        if (gui == null || gui.getType() != Gui.Type.STASH) return null;
        return gui;
    }

    private void openStash(Player player) {
        SQLStash found = plugin.database.find(SQLStash.class).eq("owner", player.getUniqueId()).findUnique();
        SQLStash row = found != null ? found : new SQLStash(player.getUniqueId());
        Gui gui = new Gui(plugin, Gui.Type.STASH)
            .title("Stash")
            .size(6 * 9);
        gui.setEditable(true);
        if (row.getJson() != null) {
            InventoryStorage inventoryStorage = Json.deserialize(row.getJson(), InventoryStorage.class, () -> null);
            if (inventoryStorage != null) {
                List<ItemStack> drops = inventoryStorage.restore(gui.getInventory(), player.getName());
                Items.give(player, drops);
            }
        }
        gui.onClose(event -> onClose(player, row, gui));
        gui.open(player);
        player.playSound(player.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, SoundCategory.MASTER, 0.75f, 2.0f);
    }

    private void onClose(Player player, SQLStash row, Gui gui) {
        for (int i = 0; i < gui.getInventory().getSize(); i += 1) {
            ItemStack itemStack = gui.getInventory().getItem(i);
            if (itemStack == null || itemStack.getAmount() == 0) continue;
            if (Mytems.forItem(itemStack) != null) continue;
            if (Tag.SHULKER_BOXES.isTagged(itemStack.getType()) || itemStack.getType() == Material.FILLED_MAP) {
                gui.getInventory().setItem(i, null);
                Items.give(player, itemStack);
                player.sendMessage(ChatColor.RED + "You cannot stash " + itemStack.getI18NDisplayName() + "!");
            }
        }
        InventoryStorage inventoryStorage = InventoryStorage.of(gui.getInventory());
        row.setJson(Json.serialize(inventoryStorage));
        row.setAccessNow();
        row.setItemCount(inventoryStorage.getCount());
        try {
            plugin.database.save(row);
        } catch (Exception e) {
            plugin.getLogger().warning(player.getName() + ": Could not save stash: " + inventoryStorage);
            e.printStackTrace();
        }
        gui.getInventory().clear();
        player.playSound(player.getLocation(), Sound.BLOCK_ENDER_CHEST_CLOSE, SoundCategory.MASTER, 0.75f, 2.0f);
    }
}
