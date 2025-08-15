package com.example.akthosidle.data;

import android.content.Context;

import androidx.annotation.Nullable;

import com.example.akthosidle.model.EquipmentSlot;
import com.example.akthosidle.model.Item;
import com.example.akthosidle.model.Monster;
import com.example.akthosidle.model.PlayerCharacter;
import com.example.akthosidle.model.Skill;
import com.example.akthosidle.model.SkillId;
import com.example.akthosidle.model.Stats;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Central game data repo: loads static definitions, manages the save, and provides helpers.
 */
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
        this.prefs = new Prefs(this.ctx);
    }

    /** Load items/monsters from assets (call once from ViewModel ctor). */
    public void loadDefinitions() {
        try {
            // Items
            String itemJson = JsonUtils.readAsset(ctx, "items.json");
            Type itemsType = new TypeToken<Map<String, List<Item>>>() {}.getType();
            Map<String, List<Item>> iw = gson.fromJson(itemJson, itemsType);
            items.clear();
            for (Item it : iw.getOrDefault("items", List.of())) {
                items.put(it.id, it);
            }

            // Monsters
            String monsJson = JsonUtils.readAsset(ctx, "monsters.json");
            Type monsType = new TypeToken<Map<String, List<Monster>>>() {}.getType();
            Map<String, List<Monster>> mw = gson.fromJson(monsJson, monsType);
            monsters.clear();
            for (Monster m : mw.getOrDefault("monsters", List.of())) {
                monsters.put(m.id, m);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed loading game definitions", e);
        }
    }

    /** Load player from disk or create a fresh one; also runs migrations/initializers. */
    public PlayerCharacter loadOrCreatePlayer() {
        if (player != null) return player;

        String json = prefs.getJson(KEY_PLAYER);
        if (json != null) {
            player = gson.fromJson(json, PlayerCharacter.class);
        } else {
            player = new PlayerCharacter();
            // starter kit example
            addItemToBag(player, "steel_sword", 1);
            addItemToBag(player, "apple", 5);
        }

        // ---- One-time initializations / migrations ----

        // Ensure skills exist (all Lv1)
        if (player.skills == null || player.skills.isEmpty()) {
            player.skills = new EnumMap<>(SkillId.class);
            for (SkillId id : SkillId.values()) {
                player.skills.put(id, new Skill(1, 0));
            }
        }

        // Migrate legacy equipment slot names to new schema
        migrateEquipmentSlots(player);

        // Initialize current HP to max if missing
        if (player.currentHp == null) {
            Stats gear = gearStats(player);
            int maxHp = Math.max(1, player.totalStats(gear).health);
            player.currentHp = maxHp;
        }

        // Ensure maps not null
        if (player.bag == null) player.bag = new HashMap<>();
        if (player.equipment == null) player.equipment = new EnumMap<>(EquipmentSlot.class);

        return player;
    }

    /** Persist player. Call this after any mutation. */
    public void save() {
        if (player != null) {
            prefs.putJson(KEY_PLAYER, gson.toJson(player));
        }
    }

    public Item getItem(String id) { return items.get(id); }
    public Monster getMonster(String id) { return monsters.get(id); }

    /**
     * Sum gear stats from all equipped items (ignores nulls).
     * NOTE: Stats.speed is treated as an additive bonus (e.g., 0.20 = +20% attack speed).
     */
    public Stats gearStats(PlayerCharacter pc) {
        Stats sum = new Stats(0, 0, 0.0, 0, 0.0, 1.5); // critMultiplier: keep max later
        if (pc.equipment == null) return sum;

        for (Map.Entry<EquipmentSlot, String> e : pc.equipment.entrySet()) {
            Item it = items.get(e.getValue());
            if (it != null && it.stats != null) {
                sum = Stats.add(sum, it.stats);
            }
        }
        return sum;
    }

    /**
     * Map an item's slot string (handles legacy values) to the new EquipmentSlot enum.
     * Legacy: CHEST->ARMOR, LEGS->PANTS, OFFHAND->SHIELD.
     */
    public @Nullable EquipmentSlot slotOf(Item item) {
        if (item == null || item.slot == null) return null;
        String s = item.slot.trim().toUpperCase();

        // Legacy aliases
        if (s.equals("CHEST")) s = "ARMOR";
        if (s.equals("LEGS")) s = "PANTS";
        if (s.equals("OFFHAND")) s = "SHIELD";

        try {
            return EquipmentSlot.valueOf(s);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /**
     * Aggregate skill buffs from equipped items.
     * Items may define: item.skillBuffs : Map<String skillName, Integer bonus>
     * e.g., {"ATTACK": 3, "MINING": 5}
     */
    public Map<String, Integer> aggregatedSkillBuffs(PlayerCharacter pc) {
        Map<String, Integer> out = new HashMap<>();
        if (pc.equipment == null) return out;

        for (Map.Entry<EquipmentSlot, String> e : pc.equipment.entrySet()) {
            Item it = items.get(e.getValue());
            if (it != null && it.skillBuffs != null) {
                for (Map.Entry<String, Integer> b : it.skillBuffs.entrySet()) {
                    String key = b.getKey();
                    Integer val = b.getValue();
                    if (key == null || val == null) continue;
                    out.put(key, out.getOrDefault(key, 0) + val);
                }
            }
        }
        return out;
    }

    // ---------- Helpers ----------

    private void addItemToBag(PlayerCharacter pc, String itemId, int qty) {
        if (qty == 0) return;
        if (pc.bag == null) pc.bag = new HashMap<>();
        pc.bag.put(itemId, pc.bag.getOrDefault(itemId, 0) + qty);
        if (pc.bag.get(itemId) != null && pc.bag.get(itemId) <= 0) {
            pc.bag.remove(itemId);
        }
    }

    /** Convert any legacy equipment slot keys in the save to the new enum names. */
    private void migrateEquipmentSlots(PlayerCharacter pc) {
        if (pc.equipment == null) {
            pc.equipment = new EnumMap<>(EquipmentSlot.class);
            return;
        }

        // If equipment map already uses the new enum keys properly, nothing to do.
        // Older saves might have persisted strings or used different enum members.
        Map<EquipmentSlot, String> migrated = new EnumMap<>(EquipmentSlot.class);

        for (Map.Entry<EquipmentSlot, String> e : pc.equipment.entrySet()) {
            EquipmentSlot slot = e.getKey();
            String itemId = e.getValue();
            if (itemId == null) continue;

            // Slots CHEST/LEGS/OFFHAND no longer exist in enum; map them by item.slot if needed.
            // (This path is defensive; typical legacy saves encoded the enum, so keys are valid.)
            if (slot == null) {
                Item it = items.get(itemId);
                EquipmentSlot resolved = slotOf(it);
                if (resolved != null) migrated.put(resolved, itemId);
                continue;
            }

            migrated.put(slot, itemId);
        }

        // Additionally: if some items were in now-legacy slots, move by reading the item’s declared slot
        for (Map.Entry<EquipmentSlot, String> e : new HashMap<>(migrated).entrySet()) {
            String itemId = e.getValue();
            Item it = items.get(itemId);
            EquipmentSlot shouldBe = slotOf(it);
            if (shouldBe != null && shouldBe != e.getKey()) {
                migrated.remove(e.getKey());
                // If collision, keep the first-equipped; new one goes to bag.
                if (!migrated.containsKey(shouldBe)) {
                    migrated.put(shouldBe, itemId);
                } else {
                    addItemToBag(pc, itemId, 1);
                }
            }
        }

        pc.equipment = migrated;
    }
}
