package com.cavetale.inventory;

import com.cavetale.core.command.AbstractCommand;
import com.cavetale.core.command.CommandArgCompleter;
import com.cavetale.core.command.CommandWarn;
import com.cavetale.core.command.RemotePlayer;
import java.util.List;

public final class DutyCommand extends AbstractCommand<InventoryPlugin> {
    protected DutyCommand(final InventoryPlugin plugin) {
        super(plugin, "duty");
    }

    @Override
    protected void onEnable() {
        rootNode.arguments("[on|off]")
            .description("Toggle duty mode")
            .completers(CommandArgCompleter.ignoreCaseList(List.of("on", "off")))
            .remotePlayerCaller(this::duty);
    }

    private boolean duty(RemotePlayer player, String[] args) {
        if (plugin.inventoryStore == null) throw new CommandWarn("This server does not have dutymode");
        final Boolean dutymode;
        if (args.length == 1) {
            dutymode = CommandArgCompleter.requireBoolean(args[0]);
        } else if (args.length == 0) {
            dutymode = null;
        } else {
            return false;
        }
        plugin.inventoryStore.duty(player, dutymode);
        return true;
    }
}
