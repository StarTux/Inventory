package com.cavetale.inventory;

import org.bukkit.Bukkit;
import static com.cavetale.inventory.InventoryPlugin.database;
import static com.cavetale.inventory.InventoryPlugin.instance;

public interface Runner {
    void sql(Runnable task);

    void main(Runnable task);

    Runner ASYNC = new Runner() {
            @Override public void sql(Runnable task) {
                database().scheduleAsyncTask(task);
            }
            @Override public void main(Runnable task) {
                Bukkit.getScheduler().runTask(instance(), task);
            }
        };
    Runner SYNC = new Runner() {
            @Override public void sql(Runnable task) {
                task.run();
            }
            @Override public void main(Runnable task) {
                task.run();
            }
        };
}
