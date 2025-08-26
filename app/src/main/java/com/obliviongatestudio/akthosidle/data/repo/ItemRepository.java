package com.obliviongatestudio.akthosidle.data.repo;

import android.content.Context;
import androidx.annotation.Nullable;
import android.util.Log; // For error logging

import com.obliviongatestudio.akthosidle.domain.model.Item;
import com.obliviongatestudio.akthosidle.domain.model.Stats; // Assuming Stats class is in this package
import com.google.gson.Gson; // Keep Gson if you were using it for specific Item parsing, but JSONObject is used here

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class ItemRepository {

    private static final String TAG = "ItemRepository";
    private static final String ASSET_ITEMS = "game/items.v1.json";
    private final Map<String, Item> itemsCache = new HashMap<>();
    private final Context appContext;
    // private final Gson gson = new Gson(); // Not strictly needed if using JSONObject directly for this part

    public ItemRepository(Context context) {
        this.appContext = context.getApplicationContext();
        loadItemsFromAssets(); // Load items on initialization
    }

    private void loadItemsFromAssets() {
        if (!itemsCache.isEmpty()) {
            return;
        }
        try (InputStream is = appContext.getAssets().open(ASSET_ITEMS)) {
            String json = readStream(is);
            JSONObject root = new JSONObject(json);
            JSONArray arr = root.optJSONArray("items");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.optJSONObject(i);
                    if (o == null) continue;
                    String id = o.optString("id", null);
                    if (id == null || id.isEmpty()) continue;

                    Item it = new Item();
                    it.id = id;
                    it.name = o.optString("name", id);
                    it.type = o.optString("type", "RESOURCE");
                    if (it.type != null) it.type = it.type.toUpperCase();
                    it.icon = o.optString("icon", null);
                    it.slot = o.optString("slot", null);
                    if (it.slot != null) it.slot = it.slot.toUpperCase();
                    it.rarity = o.optString("rarity", null);

                    // **** Crucial part for healing ****
                    if (o.has("heal")) { // Check if "heal" key exists
                        it.heal = o.optInt("heal"); // Assign to it.heal
                    } else {
                        it.heal = null; // Explicitly set to null if not present
                    }

                    JSONObject s = o.optJSONObject("stats");
                    if (s != null) {
                        it.stats = new Stats(
                                s.optInt("attack", 0),
                                s.optInt("defense", 0),
                                s.optDouble("speed", 0.0),
                                s.optInt("health", 0),
                                s.optDouble("critChance", 0.0),
                                s.optDouble("critMultiplier", 0.0)
                        );
                    }

                    JSONObject sb = o.optJSONObject("skillBuffs");
                    if (sb != null) {
                        it.skillBuffs = new HashMap<>();
                        JSONArray names = sb.names();
                        if (names != null) {
                            for (int k = 0; k < names.length(); k++) {
                                String key = names.optString(k, null);
                                if (key == null) continue;
                                try { // Ensure SkillId keys are valid if you use enum keys
                                    it.skillBuffs.put(key.toUpperCase(), sb.optInt(key, 0));
                                } catch (IllegalArgumentException e) {
                                    Log.w(TAG, "Invalid skill ID in item skillBuffs: " + key);
                                }
                            }
                        }
                    }
                    itemsCache.put(id, it);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading items from assets", e);
            // Consider how to handle this error. Maybe throw a custom exception or have a fallback.
        }
    }

    @Nullable
    public Item getItem(String itemId) {
        if (itemId == null) return null;
        return itemsCache.get(itemId);
    }

    public Map<String, Item> getAllItems() {
        return new HashMap<>(itemsCache); // Return a copy to prevent external modification
    }

    private String readStream(InputStream is) throws Exception {
        StringBuilder sb = new StringBuilder();
        byte[] buf = new byte[4096];
        int n;
        while ((n = is.read(buf)) != -1) {
            sb.append(new String(buf, 0, n, StandardCharsets.UTF_8));
        }
        return sb.toString();
    }
}
