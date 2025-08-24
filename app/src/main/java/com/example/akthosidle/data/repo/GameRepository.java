package com.example.akthosidle.data.repo;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.lifecycle.MutableLiveData;

import com.example.akthosidle.data.dtos.InventoryItem;
import com.example.akthosidle.data.tracking.ExpTracker;
import com.example.akthosidle.domain.model.Action;
import com.example.akthosidle.domain.model.EquipmentSlot;
import com.example.akthosidle.domain.model.Item;
import com.example.akthosidle.domain.model.Monster;
import com.example.akthosidle.domain.model.PlayerCharacter;
import com.example.akthosidle.domain.model.ShopEntry;
import com.example.akthosidle.domain.model.SkillId;
import com.example.akthosidle.domain.model.Stats;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

public class GameRepository {

    private static final String SP_NAME = "akthos_idle_save";
    private static final String KEY_PLAYER = "player_json";
    private static final String KEY_LAST_SEEN = "last_seen_ms";
    // NEW: persisted “which combat skill should get kill XP”
    private static final String KEY_TRAIN_SKILL = "combat_training_skill";

    private static final String ASSET_ITEMS    = "game/items.v1.json";
    private static final String ASSET_ACTIONS  = "game/actions.v1.json";
    private static final String ASSET_MONSTERS = "game/monsters.v1.json";
    private static final String ASSET_SHOP     = "game/shop.v1.json";

    private final Context app;
    private final SharedPreferences sp;
    private final Gson gson = new Gson();

    // Definitions
    public final Map<String, Item> items = new HashMap<>();
    private final Map<String, Monster> monsters = new HashMap<>();
    private final Map<String, Action> actions = new HashMap<>();
    // Shop definitions
    private final List<ShopEntry> shop = new ArrayList<>();

    // Runtime save
    private PlayerCharacter player;

    // Live data
    public final MutableLiveData<Map<String, Long>> currencyLive =
            new MutableLiveData<>(new HashMap<>());
    public final MutableLiveData<Integer> playerHpLive = new MutableLiveData<>();

    // XP/hour tracker
    public final ExpTracker xpTracker = new ExpTracker();

    // Gathering state
    public final MutableLiveData<Boolean> gatheringLive = new MutableLiveData<>(false);
    @Nullable private SkillId gatheringSkill = null;

    // Battle state (authoritative flag for FAB visibility, etc.)
    public final MutableLiveData<Boolean> battleLive = new MutableLiveData<>(false);

    // --- Toast throttling + coalescing (prevents "queued 5 toasts" errors) ---
    private static final long TOAST_MIN_INTERVAL_MS = 1200L;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    @Nullable private Toast currentToast = null;
    private long nextAllowedToastAt = 0L;
    @Nullable private String deferredToastMsg = null;
    private int deferredToastCount = 0;
    private final Runnable toastDrain = new Runnable() {
        @Override public void run() {
            String msg = deferredToastMsg;
            int count = deferredToastCount;
            deferredToastMsg = null;
            deferredToastCount = 0;
            if (msg != null) showToastNow(msg + (count > 1 ? " ×" + count : ""));
        }
    };

    public GameRepository(Context appContext) {
        this.app = appContext.getApplicationContext();
        this.sp = app.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
    }

    /* =========================================================
     * Load static definitions (items / monsters / actions / shop).
     * ========================================================= */
    public void loadDefinitions() {
        loadItemsFromAssets();
        loadMonstersFromAssets();
        loadActionsFromAssets();
        loadShopFromAssets();
    }

    public void loadActionsFromAssets() {
        if (!actions.isEmpty()) return;
        try (InputStream is = app.getAssets().open(ASSET_ACTIONS)) {
            String json = readStream(is);
            Type t = new TypeToken<List<Action>>() {}.getType();
            List<Action> list = gson.fromJson(json, t);
            if (list != null) {
                for (Action a : list) if (a != null && a.id != null) actions.put(a.id, a);
            }
        } catch (Exception ignored) {}
    }

    public void loadItemsFromAssets() {
        if (!items.isEmpty()) return;
        try (InputStream is = app.getAssets().open(ASSET_ITEMS)) {
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
                    it.id   = id;
                    it.name = o.optString("name", id);
                    it.type = o.optString("type", "RESOURCE");
                    if (it.type != null) it.type = it.type.toUpperCase();
                    it.icon = o.optString("icon", null);
                    it.slot = o.optString("slot", null);
                    if (it.slot != null) it.slot = it.slot.toUpperCase();
                    it.rarity = o.optString("rarity", null);
                    if (o.has("heal")) it.heal = o.optInt("heal");

                    // stats
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

                    // skill buffs
                    JSONObject sb = o.optJSONObject("skillBuffs");
                    if (sb != null) {
                        it.skillBuffs = new HashMap<>();
                        JSONArray names = sb.names();
                        if (names != null) {
                            for (int k = 0; k < names.length(); k++) {
                                String key = names.optString(k, null);
                                if (key == null) continue;
                                it.skillBuffs.put(key.toUpperCase(), sb.optInt(key, 0));
                            }
                        }
                    }

                    items.put(id, it);
                }
            }
        } catch (Exception ignored) {}
    }

    private void loadMonstersFromAssets() {
        if (!monsters.isEmpty()) return;
        try (InputStream is = app.getAssets().open(ASSET_MONSTERS)) {
            String json = readStream(is);
            Type t = new TypeToken<List<Monster>>() {}.getType();
            List<Monster> list = gson.fromJson(json, t);
            if (list != null) {
                for (Monster m : list) {
                    if (m != null && m.id != null) monsters.put(m.id, ensureMonsterDefaults(m));
                }
            }
        } catch (Exception ignored) {}
    }

    // Shop
    public void loadShopFromAssets() {
        if (!shop.isEmpty()) return;
        try (InputStream is = app.getAssets().open(ASSET_SHOP)) {
            String json = readStream(is);
            Type t = new TypeToken<List<ShopEntry>>() {}.getType();
            List<ShopEntry> list = gson.fromJson(json, t);
            if (list != null) shop.addAll(list);
        } catch (Exception ignored) {}
    }

    /* ============================
     * Seeding
     * ============================ */
    public void seedMonsters(List<Monster> list) {
        if (list == null || list.isEmpty()) return;
        for (Monster m : list) {
            if (m == null || m.id == null) continue;
            monsters.put(m.id, ensureMonsterDefaults(m));
        }
    }

    private static Monster ensureMonsterDefaults(Monster m) {
        if (m.name == null) m.name = m.id;
        if (m.stats == null) m.stats = new Stats();
        return m;
    }

    /* ============================
     * Player Save / Load
     * ============================ */

    private static int xpAtStartOfLevel(int lvl) {
        // Minimal XP to *be at* level `lvl`. Level 1 => 0 XP.
        int x = 0;
        int L = Math.max(1, lvl);
        for (int i = 1; i < L; i++) {
            x += PlayerCharacter.xpForNextLevel(i);
        }
        return x;
    }

    public PlayerCharacter loadOrCreatePlayer() {
        if (player != null) return player;

        String json = sp.getString(KEY_PLAYER, null);
        if (json != null) {
            // First try normal parse (new format where skills map to ints)
            try {
                Type t = new TypeToken<PlayerCharacter>() {}.getType();
                player = gson.fromJson(json, t);
            } catch (Exception parseFail) {
                // Fallback: legacy migration – skills were objects not ints
                try {
                    JSONObject root = new JSONObject(json);
                    JSONObject skills = root.optJSONObject("skills");
                    if (skills != null) {
                        JSONArray names = skills.names();
                        if (names != null) {
                            for (int i = 0; i < names.length(); i++) {
                                String key = names.optString(i, null);
                                if (key == null) continue;

                                Object val = skills.opt(key);
                                if (val instanceof JSONObject) {
                                    JSONObject sobj = (JSONObject) val;
                                    // Prefer explicit xp/exp if present; otherwise derive from level
                                    int xp = sobj.has("xp") ? sobj.optInt("xp",
                                            sobj.optInt("exp", 0))
                                            : sobj.optInt("exp", 0);
                                    if (xp <= 0) {
                                        int lvl = Math.max(1,
                                                sobj.optInt("level",
                                                        sobj.optInt("lvl", 1)));
                                        xp = xpAtStartOfLevel(lvl);
                                    }
                                    skills.put(key, xp);
                                } else if (val == JSONObject.NULL) {
                                    skills.put(key, 0);
                                } else if (val instanceof String) {
                                    try {
                                        skills.put(key, Integer.parseInt((String) val));
                                    } catch (NumberFormatException ignored) {
                                        skills.put(key, 0);
                                    }
                                }
                                // if already a number, leave it
                            }
                        }
                    }
                    // Re-parse using migrated JSON
                    Type t = new TypeToken<PlayerCharacter>() {}.getType();
                    player = gson.fromJson(root.toString(), t);
                } catch (Exception migrateFail) {
                    // Couldn’t migrate; fall through to create a fresh player
                    player = null;
                }
            }

            if (player != null) {
                if (player.bag == null) player.bag = new HashMap<>();
                if (player.equipment == null) player.equipment = new EnumMap<>(EquipmentSlot.class);
                if (player.skills == null) player.skills = new EnumMap<>(SkillId.class);
                if (player.currencies == null) player.currencies = new HashMap<>();
                if (player.base == null) player.base = new Stats(12, 6, 0.0, 100, 0.05, 1.5);

                // migrate any other legacy bits now that we can access the object
                player.migrateSkillsToXpIfNeeded();

                if (player.base.health <= 0) player.base.health = 100;
                if (player.base.critMultiplier < 1.0) player.base.critMultiplier = 1.5;
                if (player.base.critChance < 0) player.base.critChance = 0;
                if (player.base.critChance > 1) player.base.critChance = 1;

                player.normalizeCurrencies();

                int maxHp = totalStats().health;
                if (maxHp <= 0) maxHp = 100;
                if (player.currentHp == null || player.currentHp <= 0 || player.currentHp > maxHp) {
                    player.currentHp = maxHp;
                    save();
                }

                publishCurrencies();
                publishHp();
                return player;
            }
            // If we get here, parsing/migration failed and we'll create a fresh save below.
        }

        // ---- new player seed path (unchanged) ----
        player = new PlayerCharacter();
        player.normalizeCurrencies();

        addToBag("wpn_rusty_sword", 1);
        addToBag("helm_leather_cap", 1);
        addToBag("food_apple", 5);
        addToBag("pot_basic_combat", 2);
        addToBag("pot_basic_noncombat", 2);
        addToBag("syrup_basic", 1);

        if (player.currentHp == null) {
            int maxHp = totalStats().health;
            player.currentHp = maxHp;
        }

        save();
        publishCurrencies();
        publishHp();
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
    public @Nullable Action getAction(String id) { return actions.get(id); }

    public List<Action> getActionsBySkill(SkillId skill) {
        List<Action> out = new ArrayList<>();
        for (Action a : actions.values()) if (a != null && a.skill == skill) out.add(a);
        return out;
    }

    public String itemName(String id) {
        Item it = getItem(id);
        return it != null && it.name != null ? it.name : id;
    }

    public @Nullable EquipmentSlot slotOf(Item it) {
        if (it == null || it.slot == null) return null;
        try {
            return EquipmentSlot.valueOf(it.slot);
        } catch (IllegalArgumentException e) {
            try { return EquipmentSlot.valueOf(it.slot.toUpperCase()); }
            catch (Exception ignored) { return null; }
        }
    }

    public Stats gearStats(PlayerCharacter pc) {
        Stats sum = new Stats(0, 0, 0.0, 0, 0.0, 0.0);
        if (pc == null || pc.equipment == null) return sum;
        for (Map.Entry<EquipmentSlot, String> e : pc.equipment.entrySet()) {
            Item it = getItem(e.getValue());
            if (it != null && it.stats != null) {
                sum.attack        += it.stats.attack;
                sum.defense       += it.stats.defense;
                sum.speed         += it.stats.speed;
                sum.health        += it.stats.health;
                sum.critChance    += it.stats.critChance;
                sum.critMultiplier = Math.max(sum.critMultiplier, it.stats.critMultiplier);
            }
        }
        return sum;
    }

    public Stats totalStats() {
        PlayerCharacter pc = loadOrCreatePlayer();
        return pc.totalStats(gearStats(pc));
    }

    private void addToBag(String id, int qty) {
        loadOrCreatePlayer();
        player.bag.put(id, player.bag.getOrDefault(id, 0) + qty);
    }

    /* ============================
     * Skill helpers (for UI)
     * ============================ */
    public int skillLevel(SkillId id) { return loadOrCreatePlayer().getSkillLevel(id); }
    public int skillExp(SkillId id)    { return loadOrCreatePlayer().getSkillExp(id); }

    public int skillXpIntoLevel(SkillId id) {
        int xp = skillExp(id);
        int lvl = PlayerCharacter.levelForExp(xp);
        return PlayerCharacter.xpIntoLevel(xp, lvl);
    }

    public int skillXpForNextLevel(SkillId id) {
        int lvl = skillLevel(id);
        return PlayerCharacter.xpForNextLevel(lvl);
    }

    /* ============================
     * Combat training skill (persisted choice)
     * ============================ */
    public @Nullable SkillId getCombatTrainingSkill() {
        String s = sp.getString(KEY_TRAIN_SKILL, null);
        if (s != null) {
            try {
                SkillId id = SkillId.valueOf(s);
                if (isCombatSkillEnum(id)) return id;
            } catch (Exception ignored) {}
        }
        // Default choice if not set
        return SkillId.ATTACK;
    }

    public void setCombatTrainingSkill(@Nullable SkillId id) {
        if (id == null) {
            sp.edit().remove(KEY_TRAIN_SKILL).apply();
            return;
        }
        if (!isCombatSkillEnum(id)) return;
        sp.edit().putString(KEY_TRAIN_SKILL, id.name()).apply();
    }

    private boolean isCombatSkillEnum(@Nullable SkillId id) {
        if (id == null) return false;
        switch (id) {
            case ATTACK:
            case STRENGTH:
            case DEFENSE:
            case ARCHERY:
            case MAGIC:
            case HP:
                return true;
            default:
                return false;
        }
    }

    /* ============================
     * XP helpers
     * ============================ */
    public void addPlayerExp(int amount) {
        if (amount <= 0) return;
        // No persistent "player.exp" field—just track & toast.
        save();
        toast("+" + amount + " XP");
        xpTracker.note("combat", amount);
    }

    public boolean addSkillExp(SkillId id, int amount) {
        if (id == null || amount <= 0) return false;
        PlayerCharacter pc = loadOrCreatePlayer();
        boolean leveled = pc.addSkillExp(id, amount);
        save();
        xpTracker.note("skill:" + id.name().toLowerCase(), amount);
        if (leveled) {
            if (id == SkillId.HP) publishHp(); // Max HP changed via skill → update observers
            toast(id.name().charAt(0) + id.name().substring(1).toLowerCase() +
                    " Lv " + pc.getSkillLevel(id) + "!");
        }
        return leveled;
    }

    /* ============================
     * Gathering state API
     * ============================ */
    public boolean isGatheringActive() {
        Boolean b = gatheringLive.getValue();
        return b != null && b;
    }
    public void startGathering(@Nullable SkillId skill) {
        gatheringSkill = skill;
        if (!Boolean.TRUE.equals(gatheringLive.getValue())) gatheringLive.setValue(true);
    }
    public void stopGathering() {
        gatheringSkill = null;
        if (!Boolean.FALSE.equals(gatheringLive.getValue())) gatheringLive.setValue(false);
    }
    @Nullable public SkillId getGatheringSkill() { return gatheringSkill; }

    /* ============================
     * Battle state API
     * ============================ */
    public boolean isBattleActive() {
        Boolean b = battleLive.getValue();
        return b != null && b;
    }
    public void startBattle() {
        if (!Boolean.TRUE.equals(battleLive.getValue())) battleLive.setValue(true);
    }
    public void stopBattle() {
        if (!Boolean.FALSE.equals(battleLive.getValue())) battleLive.setValue(false);
    }

    /* ============================
     * Food & Potions API
     * ============================ */
    public List<InventoryItem> getFoodItems() {
        List<InventoryItem> list = new ArrayList<>();
        PlayerCharacter pc = loadOrCreatePlayer();
        for (Map.Entry<String, Integer> e : pc.bag.entrySet()) {
            String id = e.getKey();
            int qty = e.getValue();
            Item it = getItem(id);
            if (isFood(it)) list.add(new InventoryItem(id, it != null ? it.name : id, qty));
        }
        return list;
    }

    /** Consume a food item (CONSUMABLE with heal > 0) and decrement quantity. */
    public void consumeFood(String foodId) {
        PlayerCharacter pc = loadOrCreatePlayer();
        Integer have = pc.bag.get(foodId);
        if (have == null || have <= 0) return;
        Item it = getItem(foodId);
        if (it == null || !isFood(it)) return;

        if (it.heal != null && it.heal > 0) {
            int maxHp = totalStats().health;
            int cur = pc.currentHp == null ? maxHp : pc.currentHp;
            int newHp = Math.min(maxHp, Math.max(0, cur) + it.heal);
            int healed = newHp - cur;
            pc.currentHp = newHp;
            toast(healed > 0 ? ("+" + healed + " HP") : "HP already full");
        }
        pc.bag.put(foodId, have - 1);
        if (pc.bag.get(foodId) != null && pc.bag.get(foodId) <= 0) pc.bag.remove(foodId);
        save();
        publishHp();
    }

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
            list.add(new InventoryItem(id, it != null ? it.name : id, qty));
        }
        return list;
    }

    public void consumePotion(String potionId) {
        PlayerCharacter pc = loadOrCreatePlayer();
        Integer have = pc.bag.get(potionId);
        if (have == null || have <= 0) return;
        Item it = getItem(potionId);
        if (it == null || !isPotion(it)) return;

        if (it.heal != null && it.heal > 0) {
            int maxHp = totalStats().health;
            int cur = pc.currentHp == null ? maxHp : pc.currentHp;
            int newHp = Math.min(maxHp, Math.max(0, cur) + it.heal);
            int healed = newHp - cur;
            pc.currentHp = newHp;
            toast(healed > 0 ? ("+" + healed + " HP") : "HP already full");
        }
        pc.bag.put(potionId, have - 1);
        if (pc.bag.get(potionId) != null && pc.bag.get(potionId) <= 0) pc.bag.remove(potionId);
        save();
        publishHp();
    }

    /** Consume one “syrup”: if it has a heal value, heal; otherwise give a small speed buff. */
    public void consumeSyrup() {
        PlayerCharacter pc = loadOrCreatePlayer();

        // Find a syrup-like consumable in the bag (by id or name)
        String syrupId = null;
        for (String id : new HashSet<>(pc.bag.keySet())) {
            Item it = getItem(id);
            if (it != null && "CONSUMABLE".equals(it.type)) {
                String nid = it.id != null ? it.id.toLowerCase() : "";
                String nname = it.name != null ? it.name.toLowerCase() : "";
                if (nid.contains("syrup") || nname.contains("syrup")) {
                    syrupId = id;
                    break;
                }
            }
        }
        if (syrupId == null) {
            toast("No syrup available");
            return;
        }

        Item syrup = getItem(syrupId);
        boolean didSomething = false;

        if (syrup != null && syrup.heal != null && syrup.heal > 0) {
            int maxHp = totalStats().health;
            int cur = pc.currentHp == null ? maxHp : pc.currentHp;
            int newHp = Math.min(maxHp, Math.max(0, cur) + syrup.heal);
            int healed = newHp - cur;
            pc.currentHp = newHp;
            didSomething = true;
            toast(healed > 0 ? ("+" + healed + " HP") : "HP already full");
        } else {
            // Fallback effect until timed buffs are implemented
            pc.base.speed += 0.05;
            didSomething = true;
            toast("Speed +0.05");
        }

        // Decrement inventory only if we applied an effect
        if (didSomething) {
            Integer have = pc.bag.get(syrupId);
            if (have != null) {
                pc.bag.put(syrupId, have - 1);
                if (pc.bag.get(syrupId) != null && pc.bag.get(syrupId) <= 0) {
                    pc.bag.remove(syrupId);
                }
            }
            save();
            publishHp();
        }
    }

    /* ============================
     * Equipment API (Inventory)
     * ============================ */
    /** Equip an item from the bag into its slot. Returns true if it equipped. */
    public boolean equip(String itemId) {
        PlayerCharacter pc = loadOrCreatePlayer();
        Item it = getItem(itemId);
        if (it == null) { toast("Unknown item"); return false; }
        EquipmentSlot slot = slotOf(it);
        if (slot == null) { toast("Can't equip: no slot"); return false; }

        Integer have = pc.bag.get(itemId);
        if (have == null || have <= 0) { toast("You don't have that item"); return false; }

        // Remove from bag
        pc.bag.put(itemId, have - 1);
        if (pc.bag.get(itemId) != null && pc.bag.get(itemId) <= 0) pc.bag.remove(itemId);

        // Swap with currently equipped
        String prev = pc.equipment.put(slot, itemId);
        if (prev != null) pc.addItem(prev, 1);

        // Clamp HP if max reduced
        int maxHp = totalStats().health;
        if (pc.currentHp == null) pc.currentHp = maxHp;
        if (pc.currentHp > maxHp) pc.currentHp = maxHp;

        save();
        publishHp();
        toast("Equipped " + itemName(itemId));
        return true;
    }

    /** Unequip whatever is in the slot back to the bag. */
    public boolean unequip(EquipmentSlot slot) {
        PlayerCharacter pc = loadOrCreatePlayer();
        if (slot == null) return false;
        String prev = pc.equipment.remove(slot);
        if (prev == null) return false;

        pc.addItem(prev, 1);

        int maxHp = totalStats().health;
        if (pc.currentHp != null && pc.currentHp > maxHp) pc.currentHp = maxHp;

        save();
        publishHp();
        toast("Unequipped " + itemName(prev));
        return true;
    }

    /* ============================
     * Bag listing
     * ============================ */
    public List<InventoryItem> getBagAsList() {
        List<InventoryItem> list = new ArrayList<>();
        PlayerCharacter pc = loadOrCreatePlayer();
        for (Map.Entry<String, Integer> e : pc.bag.entrySet()) {
            String id = e.getKey();
            int qty = e.getValue();
            Item it = getItem(id);
            String name = (it != null && it.name != null) ? it.name : id;
            list.add(new InventoryItem(id, name, qty));
        }
        return list;
    }

    /* ============================
     * Classification helpers
     * ============================ */
    private boolean isFood(@Nullable Item it) {
        return it != null && "CONSUMABLE".equals(it.type) && it.heal != null && it.heal > 0;
    }
    private boolean isPotion(@Nullable Item it) {
        return it != null && "CONSUMABLE".equals(it.type) && !isFood(it);
    }
    private boolean isCombatPotion(Item it) {
        if (it == null) return false;
        if (it.stats != null) return true;
        if (it.skillBuffs != null) {
            for (String key : it.skillBuffs.keySet()) if (isCombatSkill(key)) return true;
        }
        return false;
    }
    private boolean isNonCombatPotion(Item it) {
        if (it == null) return false;
        boolean hasAny = false;
        if (it.skillBuffs != null) {
            for (String key : it.skillBuffs.keySet()) {
                hasAny = true;
                if (isCombatSkill(key)) return false;
            }
        }
        return hasAny;
    }
    private boolean isCombatSkill(String skill) {
        if (skill == null) return false;
        String k = skill.toUpperCase();
        return k.equals("ATTACK") || k.equals("STRENGTH") || k.equals("DEFENSE") || k.equals("HP");
    }

    /* ============================
     * Pending loot (combat)
     * ============================ */
    private final List<PendingLoot> pendingLoot = new ArrayList<>();
    public final MutableLiveData<List<InventoryItem>> pendingLootLive =
            new MutableLiveData<>(new ArrayList<>());

    public void addPendingCurrency(String code, String name, int qty) {
        if (qty <= 0) return;
        String id = "currency:" + code;
        for (PendingLoot pl : pendingLoot) {
            if (pl.isCurrency && id.equals(pl.id)) {
                pl.quantity += qty;
                updatePendingLootLive();
                save();
                publishCurrencies();
                return;
            }
        }
        PendingLoot pl = new PendingLoot();
        pl.id = id; pl.name = name != null ? name : code; pl.quantity = qty; pl.isCurrency = true;
        pendingLoot.add(pl);
        updatePendingLootLive();
        save();
        publishCurrencies();
    }

    public void addPendingLoot(String itemId, String name, int qty) {
        if (itemId == null || qty <= 0) return;
        for (PendingLoot pl : pendingLoot) {
            if (!pl.isCurrency && itemId.equals(pl.id)) {
                pl.quantity += qty;
                updatePendingLootLive();
                save();
                return;
            }
        }
        PendingLoot pl = new PendingLoot();
        pl.id = itemId; pl.name = (name != null ? name : itemId); pl.quantity = qty; pl.isCurrency = false;
        pendingLoot.add(pl);
        updatePendingLootLive();
        save();
    }

    private void updatePendingLootLive() {
        List<InventoryItem> view = new ArrayList<>();
        for (PendingLoot pl : pendingLoot) if (!pl.isCurrency) view.add(new InventoryItem(pl.id, pl.name, pl.quantity));
        pendingLootLive.postValue(view);
    }

    public List<PendingLoot> getPendingLoot() { return new ArrayList<>(pendingLoot); }
    public void clearPendingLoot() {
        pendingLoot.clear();
        updatePendingLootLive();
        save();
        publishCurrencies();
    }

    public void collectPendingLoot() {
        PlayerCharacter pc = loadOrCreatePlayer();
        for (PendingLoot pl : pendingLoot) {
            if (pl.isCurrency) {
                String code = (pl.id != null && pl.id.startsWith("currency:"))
                        ? pl.id.substring("currency:".length()) : pl.id;
                pc.addCurrency(code, pl.quantity);
            } else {
                pc.addItem(pl.id, pl.quantity);
            }
        }
        pendingLoot.clear();
        updatePendingLootLive();
        save();
        publishCurrencies();
    }

    public synchronized void collectPendingLoot(PlayerCharacter pc) {
        List<InventoryItem> cur = pendingLootLive.getValue();
        if (cur == null) cur = new ArrayList<>();
        for (InventoryItem it : cur) pc.addItem(it.id, it.quantity);
        pendingLoot.removeIf(pl -> !pl.isCurrency);
        updatePendingLootLive();
        save();
        publishCurrencies();
    }

    /* ============================
     * Gathering: direct rewards (no pending buffer)
     * ============================ */

    /** Give a gathered ITEM straight to the bag and toast it. */
    public void grantGatheredItem(String itemId, int qty) {
        if (itemId == null || qty <= 0) return;
        PlayerCharacter pc = loadOrCreatePlayer();
        pc.addItem(itemId, qty);
        save();

        Item def = getItem(itemId);
        String name = def != null && def.name != null ? def.name : itemId;
        // (intentionally no per-item toast here; ActionEngine aggregates a toast)
    }

    /** Give a gathered CURRENCY straight to balances and toast it. */
    public void grantGatheredCurrency(String code, long amount) {
        if (code == null || amount <= 0) return;
        addCurrency(code, amount); // already saves + publishes
        toast("+" + amount + " " + capitalize(code));
    }

    /** Convenience: decide by id format; supports "currency:xxx" or plain item id. */
    public void grantGathered(String idOrCurrency, int qty, @Nullable String displayNameHint) {
        if (idOrCurrency == null || qty <= 0) return;
        if (idOrCurrency.startsWith("currency:")) {
            String code = idOrCurrency.substring("currency:".length());
            grantGatheredCurrency(code, qty);
        } else {
            grantGatheredItem(idOrCurrency, qty);
        }
    }

    /* ============================
     * Shop API
     * ============================ */

    @Nullable
    private ShopEntry findShopByItem(String itemId) {
        for (ShopEntry e : shop) {
            if (e != null && itemId.equals(e.itemId)) return e;
        }
        return null;
    }

    /** UI row for shop. */
    public static class ShopRow {
        public final String itemId;
        public final String name;
        public final int priceGold;
        public final int priceSilver;
        public final int ownedQty;

        public ShopRow(String itemId, String name, int priceGold, int priceSilver, int ownedQty) {
            this.itemId = itemId;
            this.name = name;
            this.priceGold = priceGold;
            this.priceSilver = priceSilver;
            this.ownedQty = ownedQty;
        }
    }

    /** Snapshot with current owned counts. */
    public List<ShopRow> getShopRows() {
        PlayerCharacter pc = loadOrCreatePlayer();
        List<ShopRow> out = new ArrayList<>();
        for (ShopEntry se : shop) {
            if (se == null || se.itemId == null) continue;
            Item def = getItem(se.itemId);
            String label = (se.name != null && !se.name.isEmpty()) ? se.name
                    : (def != null && def.name != null ? def.name : se.itemId);
            int owned = pc.bag.getOrDefault(se.itemId, 0);
            int g = se.priceGold == null ? 0 : Math.max(0, se.priceGold);
            int s = se.priceSilver == null ? 0 : Math.max(0, se.priceSilver);
            out.add(new ShopRow(se.itemId, label, g, s, owned));
        }
        return out;
    }

    /** Try to buy qty; returns true if purchased. */
    public boolean buyItem(String itemId, int qty) {
        if (qty <= 0) return false;
        ShopEntry se = findShopByItem(itemId);
        if (se == null) { toast("Not sold here"); return false; }

        long needGold = (long)(se.priceGold == null ? 0 : se.priceGold) * qty;
        long needSilver = (long)(se.priceSilver == null ? 0 : se.priceSilver) * qty;

        long haveGold = getCurrency("gold");
        long haveSilver = getCurrency("silver");
        if (haveGold < needGold || haveSilver < needSilver) {
            toast("Not enough funds");
            return false;
        }

        // deduct both
        if (needGold > 0) spendCurrency("gold", needGold);
        if (needSilver > 0) spendCurrency("silver", needSilver);

        // add to bag
        PlayerCharacter pc = loadOrCreatePlayer();
        pc.addItem(itemId, qty);
        save();

        Item def = getItem(itemId);
        String name = def != null && def.name != null ? def.name : itemId;
        toast("Bought +" + qty + "× " + name);
        return true;
    }

    /** Sell qty back at 25% of list price if shop carries the item. */
    public boolean sellItem(String itemId, int qty) {
        if (qty <= 0) return false;
        ShopEntry se = findShopByItem(itemId);
        if (se == null) { toast("Can't sell here"); return false; }

        PlayerCharacter pc = loadOrCreatePlayer();
        int have = pc.bag.getOrDefault(itemId, 0);
        if (have < qty) { toast("Not enough in bag"); return false; }

        // remove from bag
        pc.bag.put(itemId, have - qty);
        if (pc.bag.get(itemId) != null && pc.bag.get(itemId) <= 0) pc.bag.remove(itemId);

        // pay back
        long goldBack   = Math.max(0, (long)Math.floor(0.25 * (se.priceGold   == null ? 0 : se.priceGold)   * qty));
        long silverBack = Math.max(0, (long)Math.floor(0.25 * (se.priceSilver == null ? 0 : se.priceSilver) * qty));
        if (goldBack   > 0) addCurrency("gold", goldBack);
        if (silverBack > 0) addCurrency("silver", silverBack);
        save();

        Item def = getItem(itemId);
        String name = def != null && def.name != null ? def.name : itemId;
        toast("Sold " + qty + "× " + name);
        return true;
    }

    /* ============================
     * Currency convenience
     * ============================ */
    public long getCurrency(String id) { return loadOrCreatePlayer().getCurrency(id); }
    public void addCurrency(String id, long amount) {
        PlayerCharacter pc = loadOrCreatePlayer();
        pc.addCurrency(id, amount);
        save();
        publishCurrencies();
    }
    public boolean spendCurrency(String id, long amount) {
        PlayerCharacter pc = loadOrCreatePlayer();
        boolean ok = pc.spendCurrency(id, amount);
        if (ok) { save(); publishCurrencies(); }
        return ok;
    }
    public List<InventoryItem> listCurrencies() {
        PlayerCharacter pc = loadOrCreatePlayer();
        List<InventoryItem> out = new ArrayList<>();
        for (Map.Entry<String, Long> e : pc.currencies.entrySet()) {
            out.add(new InventoryItem(e.getKey(), capitalize(e.getKey()),
                    (int) Math.min(Integer.MAX_VALUE, e.getValue())));
        }
        return out;
    }
    public long getGold() { return getCurrency("gold"); }
    public void addGold(long amount) { addCurrency("gold", amount); }

    // Publish helpers
    private void publishCurrencies() {
        PlayerCharacter pc = loadOrCreatePlayer();
        currencyLive.postValue(new HashMap<>(pc.currencies));
    }
    private void publishHp() {
        PlayerCharacter pc = loadOrCreatePlayer();
        playerHpLive.postValue(pc.currentHp);
    }
    public MutableLiveData<Map<String, Long>> currenciesLive() { return currencyLive; }

    /* ============================
     * Offline timestamps
     * ============================ */
    public void setLastSeen(long ms) { sp.edit().putLong(KEY_LAST_SEEN, ms).apply(); }
    public long getLastSeen() { return sp.getLong(KEY_LAST_SEEN, System.currentTimeMillis()); }

    /* ============================
     * Per-skill “last picked action” (UX)
     * ============================ */
    private static String keyLastActionFor(SkillId skill) {
        return "last_action_" + (skill != null ? skill.name() : "unknown");
    }

    public void saveLastActionForSkill(@Nullable SkillId skill, @Nullable String actionId) {
        if (skill == null || actionId == null) return;
        sp.edit().putString(keyLastActionFor(skill), actionId).apply();
    }

    @Nullable
    public String getLastActionForSkill(@Nullable SkillId skill) {
        if (skill == null) return null;
        return sp.getString(keyLastActionFor(skill), null);
    }

    /** ---------- Helpers expected by SkillDetailFragment ---------- */

    /** Compat: check unlock using a provided level. */
    public boolean isUnlocked(@Nullable Action a, int currentLevel) {
        if (a == null) return false;
        int req = Math.max(1, a.reqLevel);
        int lvl = Math.max(1, currentLevel);
        return lvl >= req;
    }

    /** True if the player meets this action’s level requirement. */
    public boolean isUnlocked(@Nullable Action a) {
        if (a == null || a.skill == null) return false;
        int playerLevel = skillLevel(a.skill);
        return isUnlocked(a, playerLevel);
    }

    /**
     * Returns the best unlocked (highest reqLevel) action for this skill
     * given an explicit current level, or null if none are unlocked yet.
     */
    @Nullable
    public Action bestUnlockedFor(@Nullable SkillId skill, int currentLevel) {
        if (skill == null) return null;
        Action best = null;
        int bestReq = -1;
        for (Action a : actions.values()) {
            if (a == null || a.skill != skill) continue;
            int req = Math.max(1, a.reqLevel);
            if (currentLevel >= req && req > bestReq) {
                best = a;
                bestReq = req;
            }
        }
        return best;
    }

    /**
     * Returns the best unlocked (highest reqLevel) action for this skill using the player's current level,
     * or null if none are unlocked yet.
     */
    @Nullable
    public Action bestUnlockedFor(@Nullable SkillId skill) {
        if (skill == null) return null;
        return bestUnlockedFor(skill, skillLevel(skill));
    }

    /** Persist the last-picked action for UX. */
    public void setLastPickedAction(@Nullable SkillId skill, @Nullable String actionId) {
        saveLastActionForSkill(skill, actionId);
    }

    /** Convenience overload. */
    public void setLastPickedAction(@Nullable Action a) {
        if (a != null) saveLastActionForSkill(a.skill, a.id);
    }

    /**
     * Returns the last-picked Action for this skill if present and still unlocked.
     * Otherwise falls back to the best currently unlocked action. May return null.
     */
    @Nullable
    public Action getLastPickedAction(@Nullable SkillId skill) {
        if (skill == null) return null;
        String savedId = getLastActionForSkill(skill);
        if (savedId != null) {
            Action saved = getAction(savedId);
            if (isUnlocked(saved)) return saved;
        }
        return bestUnlockedFor(skill);
    }

    /* ============================
     * Utils
     * ============================ */
    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
    private static String readStream(InputStream is) throws Exception {
        StringBuilder sb = new StringBuilder();
        byte[] buf = new byte[4096];
        int n;
        while ((n = is.read(buf)) != -1) {
            sb.append(new String(buf, 0, n, StandardCharsets.UTF_8));
        }
        return sb.toString();
    }

    /* ============================
     * Toast Helper (throttled, main-thread safe)
     * ============================ */
    public void toast(String msg) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            throttleToast(msg);
        } else {
            mainHandler.post(() -> throttleToast(msg));
        }
    }

    private void throttleToast(String msg) {
        long now = SystemClock.uptimeMillis();
        if (now >= nextAllowedToastAt) {
            showToastNow(msg);
            return;
        }
        // coalesce duplicates during cooldown
        if (deferredToastMsg != null && deferredToastMsg.equals(msg)) {
            deferredToastCount++;
        } else {
            deferredToastMsg = msg;
            deferredToastCount = 1;
        }
        mainHandler.removeCallbacks(toastDrain);
        mainHandler.postAtTime(toastDrain, nextAllowedToastAt);
    }

    private void showToastNow(String msg) {
        try {
            if (currentToast != null) currentToast.cancel();
            currentToast = Toast.makeText(app, msg, Toast.LENGTH_SHORT);
            currentToast.show();
            nextAllowedToastAt = SystemClock.uptimeMillis() + TOAST_MIN_INTERVAL_MS;
        } catch (Throwable ignored) { }
    }

    /* ============================
     * Types
     * ============================ */
    public static class PendingLoot {
        public String id;        // e.g. "iron_ore" OR "currency:silver"
        public String name;      // display label
        public int quantity;
        public boolean isCurrency;
    }

    /** Dev helper to grant items. */
    public void giveItem(String itemId, int qty) {
        if (itemId == null || qty == 0) return;
        PlayerCharacter pc = loadOrCreatePlayer();
        pc.addItem(itemId, qty);
        save();
        toast("Granted " + qty + "× " + itemName(itemId));
    }
}
