package com.cavetale.inventory.sql;

import com.winthier.sql.SQLDatabase;
import org.junit.Test;

public final class SQLTest {
    @Test
    public void main() {
        System.out.println(SQLDatabase.testTableCreation(SQLBackup.class));
        System.out.println(SQLDatabase.testTableCreation(SQLStash.class));
        System.out.println(SQLDatabase.testTableCreation(SQLInventory.class));
        System.out.println(SQLDatabase.testTableCreation(SQLTrack.class));
    }
}
