package com.cavetale.inventory.ms;

import com.cavetale.core.command.AbstractCommand;
import com.cavetale.core.command.CommandWarn;
import com.cavetale.inventory.InventoryPlugin;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.format.NamedTextColor.*;

public final class MassStorageCommand extends AbstractCommand<InventoryPlugin> {
    protected final MassStorage ms;

    protected MassStorageCommand(final InventoryPlugin plugin, final MassStorage massStorage) {
        super(plugin, "ms2");
        this.ms = massStorage;
    }

    @Override
    protected void onEnable() {
        rootNode.addChild("dump").denyTabCompletion()
            .description("Dump your inventory")
            .playerCaller(this::dump);
    }

    protected boolean dump(Player player, String[] args) {
        if (args.length != 0) return false;
        int total = 0;
        for (int i = 9; i < 36; i += 1) {
            ItemStack itemStack = player.getInventory().getItem(i);
            if (ms.insert(player, itemStack, unused -> { })) {
                player.getInventory().setItem(i, null);
                total += itemStack.getAmount();
            }
        }
        if (total == 0) throw new CommandWarn("No items could be stored!");
        player.sendMessage(text("Stored " + total + " items", GREEN));
        return true;
    }
}
