package com.cavetale.inventory;

import org.bukkit.Bukkit;
import static com.cavetale.inventory.InventoryPlugin.database;
import static com.cavetale.inventory.InventoryPlugin.instance;

/**
 * Provides modes of operation for worker threads.
 *
 * The ASYNC method will schedule SQL tasks in the async sql thread,
 * and schedule main tasks with BukkitScheduler.
 *
 * The SYNC method will execute all tasks immediately, in the current
 * thread.
 */
public interface Runner {
    /**
     * Execute a task involving SQL queries.
     */
    void sql(Runnable task);

    /**
     * Execute a task accessing main thread data.
     */
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
