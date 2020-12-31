package com.cavetale.inventory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.bukkit.plugin.java.JavaPlugin;

@RequiredArgsConstructor
public final class Json {
    public static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    public static String serialize(Object obj) {
        return GSON.toJson(obj);
    }

    public static <T> T deserialize(String json, Class<T> type, Supplier<T> dfl) {
        if (json == null) return dfl.get();
        T result = GSON.fromJson(json, type);
        return result != null ? result : dfl.get();
    }
}
