package com.cavetale.inventory;

import com.cavetale.core.menu.MenuItemEntry;
import com.cavetale.core.menu.MenuItemEvent;
import com.cavetale.mytems.Mytems;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import static com.cavetale.mytems.util.Items.tooltip;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.format.NamedTextColor.*;

public final class MenuListener implements Listener {
    protected void enable() {
        Bukkit.getPluginManager().registerEvents(this, InventoryPlugin.plugin());
    }

    @EventHandler
    private void onMenuItem(MenuItemEvent event) {
        if (event.getPlayer().hasPermission("inventory.stash")) {
            event.addItem(builder -> builder
                          .key("inventory:stash")
                          .command("stash")
                          .icon(tooltip(new ItemStack(Material.CHEST),
                                        List.of(text("Stash", GRAY)))));
        }
        if (event.getPlayer().hasPermission("inventory.mail") && InventoryPlugin.plugin().getItemMail().hasItemMail(event.getPlayer())) {
            event.addItem(builder -> builder
                          .priority(MenuItemEntry.Priority.NOTIFICATION)
                          .key("inventory:itemmail")
                          .command("imail")
                          .highlightColor(YELLOW)
                          .icon(Mytems.LOVE_LETTER.createIcon(List.of(text("Item Mail", YELLOW)))));
        }
    }
}
