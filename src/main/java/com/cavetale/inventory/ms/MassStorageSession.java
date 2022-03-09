package com.cavetale.inventory.ms;

import java.util.Map;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public final class MassStorageSession {
    protected final MassStorage ms;
    protected final UUID uuid;
    private boolean filling;
    private List<SQLMassStorage> rows = List.of();
    private final Map<StorableItem, SQLMassStorage> cache = new IdentityHashMap<>();

    public void fill() {
        if (filling) return;
        filling = true;
        ms.find(uuid, this::onLoadedRows);
    }

    protected void onLoadedRows(List<SQLMassStorage> newRows) {
        filling = false;
        this.rows = newRows;
        cache.clear();
        for (SQLMassStorage it : rows) {
            StorableItem storable = ms.itemIndex.get(it);
            if (!storable.isValid()) continue;
            cache.put(storable, it);
        }
    }
}
