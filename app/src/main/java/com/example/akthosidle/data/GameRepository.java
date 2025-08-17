package com.example.akthosidle.data;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;

import com.example.akthosidle.model.Drop;
import com.example.akthosidle.model.EquipmentSlot;
import com.example.akthosidle.model.InventoryItem;
import com.example.akthosidle.model.Item;
import com.example.akthosidle.model.Monster;
import com.example.akthosidle.model.PlayerCharacter;
import com.example.akthosidle.model.SkillId;
import com.example.akthosidle.model.Stats;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

public class GameRepository {

    private static final String SP_NAME = "akthos_idle_save";
    private static final String KEY_PLAYER = "player_json";

    private final Context app;
    private final SharedPreferences sp;
    private final Gson gson = new Gson();

    // Definitions
    private final Map<String, Item> items = new HashMap<>();
    private final Map<String, Monster> monsters = new HashMap<>();

    // Runtime save
    private PlayerCharacter player;

    public GameRepository(Context appContext) {
        this.app = appContext.getApplicationContext();
        this.sp = app.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
    }

    /* =========================================================
     * Load static definitions (items / monsters).
     * ========================================================= */
    public void loadDefinitions() {
        if (!items.isEmpty() || !monsters.isEmpty()) return;

        // ---------- Items (using your Item.java shape) ----------
        // Equipment
        Item rustySword = new Item();
        rustySword.id = "wpn_rusty_sword";
        rustySword.name = "Rusty Sword";
        rustySword.icon = "ic_sword";
        rustySword.type = "EQUIPMENT";
        rustySword.slot = "WEAPON"; // must match EquipmentSlot enum name
        rustySword.rarity = "COMMON";
        rustySword.stats = new Stats(3, 0, 0.0, 0, 0.0, 0.0);
        items.put(rustySword.id, rustySword);

        Item leatherCap = new Item();
        leatherCap.id = "helm_leather_cap";
        leatherCap.name = "Leather Cap";
        leatherCap.icon = "ic_helmet";
        leatherCap.type = "EQUIPMENT";
        leatherCap.slot = "HELMET";
        leatherCap.rarity = "COMMON";
        leatherCap.stats = new Stats(0, 1, 0.0, 5, 0.0, 0.0);
        items.put(leatherCap.id, leatherCap);

        // Food (CONSUMABLE + heal)
        Item apple = new Item();
        apple.id = "food_apple";
        apple.name = "Apple";
        apple.icon = "ic_food_apple";
        apple.type = "CONSUMABLE";
        apple.rarity = "COMMON";
        apple.heal = 10;
        items.put(apple.id, apple);

        // Basic “combat” potion (consumable that affects combat via stats)
        Item warDraught = new Item();
        warDraught.id = "pot_basic_combat";
        warDraught.name = "War Draught";
        warDraught.icon = "ic_potion_red";
        warDraught.type = "CONSUMABLE";
        warDraught.rarity = "UNCOMMON";
        warDraught.stats = new Stats(2, 0, 0.0, 0, 0.05, 0.0); // +ATK, +CritChance
        items.put(warDraught.id, warDraught);

        // Basic “non-combat” potion (consumable that buffs gathering skill)
        Item focusTonic = new Item();
        focusTonic.id = "pot_basic_noncombat";
        focusTonic.name = "Focus Tonic";
        focusTonic.icon = "ic_potion_blue";
        focusTonic.type = "CONSUMABLE";
        focusTonic.rarity = "UNCOMMON";
        focusTonic.skillBuffs = new HashMap<>();
        focusTonic.skillBuffs.put("MINING", 5); // example
        items.put(focusTonic.id, focusTonic);

        // “Syrup” special consumable
        Item syrup = new Item();
        syrup.id = "syrup_basic";
        syrup.name = "Syrup";
        syrup.icon = "ic_flask";
        syrup.type = "CONSUMABLE";
        syrup.rarity = "UNCOMMON";
        syrup.heal = 20;
        items.put(syrup.id, syrup);

        // ---------- Monsters ----------
        Monster thief = new Monster();
        thief.id = "shadow_thief";
        thief.name = "Shadow Thief";
        thief.stats = new Stats(8, 4, 0.15, 40, 0.05, 1.5);
        thief.exp = 20;
        thief.drops = new ArrayList<>();
        thief.drops.add(new Drop("food_apple", 1, 3, 0.5));
        monsters.put(thief.id, thief);
    }

    /* ============================
     * Player Save / Load
     * ============================ */
    public PlayerCharacter loadOrCreatePlayer() {
        if (player != null) return player;

        String json = sp.getString(KEY_PLAYER, null);
        if (json != null) {
            Type t = new TypeToken<PlayerCharacter>() {}.getType();
            player = gson.fromJson(json, t);
            // Defensive: ensure non-null fields after older saves
            if (player.bag == null) player.bag = new HashMap<>();
            if (player.equipment == null) player.equipment = new EnumMap<>(EquipmentSlot.class);
            if (player.skills == null) player.skills = new EnumMap<>(SkillId.class);
            // Initialize currentHp if missing
            if (player.currentHp == null) {
                int maxHp = totalStats().health; // base + gear
                player.currentHp = maxHp;
                save();
            }
            return player;
        }

        // Create new player
        player = new PlayerCharacter();

        // Starter inventory
        addToBag("wpn_rusty_sword", 1);
        addToBag("helm_leather_cap", 1);
        addToBag("food_apple", 5);
        addToBag("pot_basic_combat", 2);
        addToBag("pot_basic_noncombat", 2);
        addToBag("syrup_basic", 1);

        // First-time HP init to max
        if (player.currentHp == null) {
            int maxHp = totalStats().health; // base only for brand new char
            player.currentHp = maxHp;
        }

        save();
        return player;
    }

    public void save() {
        if (player == null) return;
        sp.edit().putString(KEY_PLAYER, gson.toJson(player)).apply();
    }

    /* ============================
     * Lookups & helpers
     * ============================ */
    public @Nullable Item getItem(String id) { return items.get(id); }
    public @Nullable Monster getMonster(String id) { return monsters.get(id); }

    public @Nullable EquipmentSlot slotOf(Item it) {
        if (it == null || it.slot == null) return null;
        try {
            return EquipmentSlot.valueOf(it.slot);
        } catch (IllegalArgumentException e) {
            try {
                return EquipmentSlot.valueOf(it.slot.toUpperCase());
            } catch (Exception ignored) {}
            return null;
        }
    }

    /** Sum of equipped gear Stats (uses Item.stats). */
    public Stats gearStats(PlayerCharacter pc) {
        Stats sum = new Stats(0, 0, 0.0, 0, 0.0, 0.0);
        if (pc == null || pc.equipment == null) return sum;
        for (Map.Entry<EquipmentSlot, String> e : pc.equipment.entrySet()) {
            Item it = getItem(e.getValue());
            if (it != null && it.stats != null) {
                sum.attack     += it.stats.attack;
                sum.defense    += it.stats.defense;
                sum.speed      += it.stats.speed;
                sum.health     += it.stats.health;
                sum.critChance += it.stats.critChance;
                sum.critMultiplier = Math.max(sum.critMultiplier, it.stats.critMultiplier);
            }
        }
        return sum;
    }

    /** Current total stats = base + gear (matches PlayerCharacter.totalStats usage). */
    public Stats totalStats() {
        PlayerCharacter pc = loadOrCreatePlayer();
        return pc.totalStats(gearStats(pc));
    }

    private void addToBag(String id, int qty) {
        loadOrCreatePlayer(); // ensure player exists
        player.bag.put(id, player.bag.getOrDefault(id, 0) + qty);
    }

    /* ============================
     * Food & Potions API (matches UI)
     * ============================ */

    /** Food = CONSUMABLE with heal > 0. */
    public List<InventoryItem> getFoodItems() {
        List<InventoryItem> list = new ArrayList<>();
        PlayerCharacter pc = loadOrCreatePlayer();

        for (Map.Entry<String, Integer> e : pc.bag.entrySet()) {
            String id = e.getKey();
            int qty = e.getValue();
            Item it = getItem(id);
            if (isFood(it)) list.add(new InventoryItem(id, it.name, qty));
        }
        return list;
    }

    /**
     * Potions = CONSUMABLE that are NOT food.
     * - combatOnly: affects combat (stats != null OR skillBuffs contains combat skills)
     * - nonCombatOnly: affects only non-combat skills
     * If both flags false -> return all potions.
     */
    public List<InventoryItem> getPotions(boolean combatOnly, boolean nonCombatOnly) {
        List<InventoryItem> list = new ArrayList<>();
        PlayerCharacter pc = loadOrCreatePlayer();

        for (Map.Entry<String, Integer> e : pc.bag.entrySet()) {
            String id = e.getKey();
            int qty = e.getValue();
            Item it = getItem(id);
            if (!isPotion(it)) continue;

            boolean isCombat = isCombatPotion(it);
            boolean isNonCombat = isNonCombatPotion(it);

            if (combatOnly && !isCombat) continue;
            if (nonCombatOnly && !isNonCombat) continue;

            list.add(new InventoryItem(id, it.name, qty));
        }
        return list;
    }

    /** Consume a potion (not food). Currently applies simple immediate effects and decrements qty. */
    public void consumePotion(String potionId) {
        PlayerCharacter pc = loadOrCreatePlayer();
        Integer have = pc.bag.get(potionId);
        if (have == null || have <= 0) return;

        Item it = getItem(potionId);
        if (it == null || !isPotion(it)) return;

        // Minimal immediate effects: if potion has heal, treat as instant heal.
        if (it.heal != null && it.heal > 0) {
            int maxHp = totalStats().health;
            int cur = pc.currentHp == null ? maxHp : pc.currentHp;
            pc.currentHp = Math.min(maxHp, Math.max(0, cur) + it.heal);
        }

        // Decrement inventory
        pc.bag.put(potionId, have - 1);
        if (pc.bag.get(potionId) != null && pc.bag.get(potionId) <= 0) pc.bag.remove(potionId);

        save();
    }

    /** “Syrup” action: find a consumable with 'syrup' in id/name and heal or apply a tiny speed bonus. */
    public void consumeSyrup() {
        PlayerCharacter pc = loadOrCreatePlayer();
        String syrupId = null;
        for (String id : new HashSet<>(pc.bag.keySet())) {
            Item it = getItem(id);
            if (it != null && "CONSUMABLE".equals(it.type)) {
                if ((it.id != null && it.id.toLowerCase().contains("syrup")) ||
                        (it.name != null && it.name.toLowerCase().contains("syrup"))) {
                    syrupId = id;
                    break;
                }
            }
        }
        if (syrupId == null) return;

        Item syrup = getItem(syrupId);
        if (syrup != null && syrup.heal != null && syrup.heal > 0) {
            int maxHp = totalStats().health;
            int cur = pc.currentHp == null ? maxHp : pc.currentHp;
            pc.currentHp = Math.min(maxHp, Math.max(0, cur) + syrup.heal);
        } else {
            // tiny permanent speed nudge as placeholder (until you add timed buffs)
            pc.base.speed += 0.05;
        }

        Integer have = pc.bag.get(syrupId);
        if (have != null) {
            pc.bag.put(syrupId, have - 1);
            if (pc.bag.get(syrupId) != null && pc.bag.get(syrupId) <= 0) pc.bag.remove(syrupId);
        }
        save();
    }

    /* ============================
     * Generic bag listing (optional helper)
     * ============================ */
    public List<InventoryItem> getBagAsList() {
        List<InventoryItem> list = new ArrayList<>();
        PlayerCharacter pc = loadOrCreatePlayer();
        for (Map.Entry<String, Integer> e : pc.bag.entrySet()) {
            Item it = getItem(e.getKey());
            if (it != null) list.add(new InventoryItem(it.id, it.name, e.getValue()));
        }
        return list;
    }

    /* ============================
     * Classification helpers
     * ============================ */
    private boolean isFood(@Nullable Item it) {
        return it != null
                && "CONSUMABLE".equals(it.type)
                && it.heal != null
                && it.heal > 0;
    }

    private boolean isPotion(@Nullable Item it) {
        return it != null
                && "CONSUMABLE".equals(it.type)
                && !isFood(it); // i.e., any consumable that isn't food
    }

    /** Detects if a potion has combat impact. */
    private boolean isCombatPotion(Item it) {
        if (it == null) return false;
        if (it.stats != null) return true; // direct combat stats
        if (it.skillBuffs != null) {
            for (String key : it.skillBuffs.keySet()) {
                if (isCombatSkill(key)) return true;
            }
        }
        return false;
    }

    /** Detects if a potion buffs non-combat skills only. */
    private boolean isNonCombatPotion(Item it) {
        if (it == null) return false;
        boolean hasAny = false;
        if (it.skillBuffs != null) {
            for (String key : it.skillBuffs.keySet()) {
                hasAny = true;
                if (isCombatSkill(key)) return false; // contains combat skill -> not purely non-combat
            }
        }
        // If it has skill buffs and none are combat -> non-combat
        return hasAny;
    }

    private boolean isCombatSkill(String skill) {
        if (skill == null) return false;
        String k = skill.toUpperCase();
        return k.equals("ATTACK") || k.equals("STRENGTH") || k.equals("DEFENSE") || k.equals("HP");
    }
}
