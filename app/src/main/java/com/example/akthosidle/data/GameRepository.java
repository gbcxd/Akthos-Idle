package com.example.akthosidle.data;

import android.content.Context;
import androidx.annotation.Nullable;
import com.example.akthosidle.model.*;
import com.google.firebase.crashlytics.buildtools.reloc.com.google.common.reflect.TypeToken;
import com.google.gson.Gson;

import java.lang.reflect.Type;
import java.util.*;

public class GameRepository {
    private static final String KEY_PLAYER = "player_v1";

    private final Context ctx;
    private final Prefs prefs;
    private final Gson gson = new Gson();

    public final Map<String, Item> items = new HashMap<>();
    public final Map<String, Monster> monsters = new HashMap<>();

    private PlayerCharacter player;

    public GameRepository(Context ctx) {
        this.ctx = ctx.getApplicationContext();
        this.prefs = new Prefs(ctx);
    }

    public void loadDefinitions() {
        try {
            // Items
            String itemJson = JsonUtils.readAsset(ctx, "items.json");
            Type itemsType = new TypeToken<Map<String, List<Item>>>(){}.getType();
            Map<String, List<Item>> wrapper = gson.fromJson(itemJson, itemsType);
            for (Item i : wrapper.get("items")) items.put(i.id, i);

            // Monsters
            String monJson = JsonUtils.readAsset(ctx, "monsters.json");
            Type monsType = new TypeToken<Map<String, List<Monster>>>(){}.getType();
            Map<String, List<Monster>> w2 = gson.fromJson(monJson, monsType);
            for (Monster m : w2.get("monsters")) monsters.put(m.id, m);

        } catch (Exception e) { throw new RuntimeException(e); }
    }

    public PlayerCharacter loadOrCreatePlayer() {
        if (player != null) return player;
        String json = prefs.getJson(KEY_PLAYER);
        if (json != null) {
            player = gson.fromJson(json, PlayerCharacter.class);
        } else {
            player = new PlayerCharacter();
            // starter kit
            player.addItem("steel_sword", 1);
            player.addItem("apple", 5);
        }
        return player;
    }

    public void save() {
        if (player != null) prefs.putJson(KEY_PLAYER, gson.toJson(player));
    }

    public Item getItem(String id) { return items.get(id); }
    public Monster getMonster(String id) { return monsters.get(id); }

    public Stats gearStats(PlayerCharacter pc) {
        Stats sum = new Stats(0,0,0,0,0,1.5);
        for (Map.Entry<EquipmentSlot,String> e : pc.equipment.entrySet()) {
            Item it = items.get(e.getValue());
            if (it != null && it.stats != null) sum = Stats.add(sum, it.stats);
        }
        return sum;
    }

    public @Nullable EquipmentSlot slotOf(Item item) {
        if (item.slot == null) return null;
        try { return EquipmentSlot.valueOf(item.slot); } catch (Exception e) { return null; }
    }
}
