package com.cavetale.inventory;

import com.cavetale.inventory.sql.SQLTrack;
import java.util.UUID;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
final class StoreSession {
    protected final UUID uuid;
    protected final String name;
    protected boolean loading;
    protected boolean loaded;
    protected boolean scheduled;
    protected SQLTrack trackRow;
    protected boolean disabled;

    int getTrack() {
        return trackRow != null ? trackRow.getTrack() : 0;
    }

    boolean isInDutymode() {
        return getTrack() == 1;
    }

    @Override
    public String toString() {
        return name
            + (loading ? " (loading)" : "")
            + (scheduled ? " (scheduled)" : "")
            + " track=" + getTrack();
    }
}
