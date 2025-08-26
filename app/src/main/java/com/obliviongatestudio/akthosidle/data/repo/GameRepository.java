package com.obliviongatestudio.akthosidle.data.repo;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.MutableLiveData;

import com.obliviongatestudio.akthosidle.data.dtos.InventoryItem;
import com.obliviongatestudio.akthosidle.data.tracking.ExpTracker;
import com.obliviongatestudio.akthosidle.domain.model.Action;
import com.obliviongatestudio.akthosidle.domain.model.EquipmentSlot;
import com.obliviongatestudio.akthosidle.domain.model.Item;
import com.obliviongatestudio.akthosidle.domain.model.Monster;
import com.obliviongatestudio.akthosidle.domain.model.PlayerCharacter;
import com.obliviongatestudio.akthosidle.domain.model.Recipe;
import com.obliviongatestudio.akthosidle.domain.model.RecipeIO;
import com.obliviongatestudio.akthosidle.domain.model.ShopEntry;
import com.obliviongatestudio.akthosidle.domain.model.SkillId;
import com.obliviongatestudio.akthosidle.domain.model.Stats;
import com.obliviongatestudio.akthosidle.domain.model.SlayerAssignment;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.SetOptions;
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
import java.util.Random;

/** Central game repository (definitions + save + live state + Firestore sync). */
public class GameRepository {

    private static final String SP_NAME = "akthos_idle_save";
    private static final String KEY_PLAYER = "player_json";
    private static final String KEY_LAST_SEEN = "last_seen_ms";
    private static final String KEY_TRAIN_SKILL = "combat_training_skill";
    private static final String KEY_SLAYER_JSON = "slayer_assignment_json";
    private static final String KEY_LOCAL_UPDATED_AT = "player_updated_at_ms";

    private static final String ASSET_ITEMS    = "game/items.v1.json";
    private static final String ASSET_ACTIONS  = "game/actions.v1.json";
    private static final String ASSET_MONSTERS = "game/monsters.v1.json";
    private static final String ASSET_SHOP     = "game/shop.v1.json";
    private static final String ASSET_SLAYER   = "game/slayer.v1.json";
    private static final String ASSET_RECIPES  = "game/recipes.v1.json";

    private final Map<String, Recipe> recipes = new HashMap<>();

    // Item definitions in-memory
    public final Map<String, Item> items = new HashMap<>();

    // Slayer fallback values if JSON is missing
    private static final int    SLAYER_ROLL_COST_FALLBACK      = 5;
    private static final int    SLAYER_ABANDON_COST_FALLBACK   = 2;
    private static final double SLAYER_BONUS_PER_KILL_FALLBACK = 0.25; // required/4
    private static final int    SLAYER_MIN_BONUS_FALLBACK      = 10;

    private final Context app;
    private final SharedPreferences sp;
    private final Gson gson = new Gson();

    // Definitions
    private final Map<String, Monster> monsters = new HashMap<>();
    private final Map<String, Action> actions = new HashMap<>();
    private final List<ShopEntry> shop = new ArrayList<>();

    private final Random rng = new Random();

    // Runtime save
    private PlayerCharacter player;

    // Live data
    public final MutableLiveData<Map<String, Long>> currencyLive = new MutableLiveData<>(new HashMap<>());
    public final MutableLiveData<Integer> playerHpLive = new MutableLiveData<>();

    // XP/hour tracker
    public final ExpTracker xpTracker = new ExpTracker();

    // Gathering state
    public final MutableLiveData<Boolean> gatheringLive = new MutableLiveData<>(false);
    @Nullable private SkillId gatheringSkill = null;

    // Battle state
    public final MutableLiveData<Boolean> battleLive = new MutableLiveData<>(false);

    // Slayer state
    public final MutableLiveData<SlayerAssignment> slayerLive = new MutableLiveData<>(null);

    // --- Toast throttling + coalescing ---
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

    // --- Cloud (Firestore) ---
    private static final String TAG = "GameRepository";
    private final FirebaseAuth auth = FirebaseAuth.getInstance();
    private final FirebaseFirestore fdb = FirebaseFirestore.getInstance();
    @Nullable private ListenerRegistration cloudListener;
    private static final long CLOUD_DEBOUNCE_MS = 500L;
    @Nullable private Runnable cloudSaveRunnable = null;

    public GameRepository(Context appContext) {
        this.app = appContext.getApplicationContext();
        this.sp = app.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
    }

    /* ============================
     * HP helpers (single source of truth)
     * ============================ */
    private int maxHp(@NonNull PlayerCharacter pc) {
        return Math.max(1, pc.totalStats(gearStats(pc)).health);
    }

    private int clampHp(@NonNull PlayerCharacter pc, @Nullable Integer hp) {
        int m = maxHp(pc);
        if (hp == null) return m;
        return Math.max(0, Math.min(m, hp));
    }

    private int curHp(@NonNull PlayerCharacter pc) {
        return clampHp(pc, pc.currentHp);
    }

    private void setHpAndPublish(@NonNull PlayerCharacter pc, int newHp) {
        pc.currentHp = clampHp(pc, newHp);
        save();
        publishHp();
    }

    /* =========================================================
     * Load static definitions
     * ========================================================= */
    public void loadDefinitions() {
        loadItemsFromAssets();
        loadMonstersFromAssets();
        loadActionsFromAssets();
        loadSlayerFromAssets();
        loadRecipesFromAssets();
        loadShopFromAssets();
    }

    public void loadActionsFromAssets() {
        if (!actions.isEmpty()) return;
        try (InputStream is = app.getAssets().open(ASSET_ACTIONS)) {
            String json = readStream(is);
            Type t = new TypeToken<List<Action>>() {}.getType();
            List<Action> list = gson.fromJson(json, t);
            if (list != null) for (Action a : list) if (a != null && a.id != null) actions.put(a.id, a);
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
                for (Monster m : list) if (m != null && m.id != null) monsters.put(m.id, ensureMonsterDefaults(m));
            }
            if (slayerRegions.isEmpty() && !monsters.isEmpty()) {
                slayerRegions.put("basecamp", new SlayerRegion("basecamp", "Basecamp", new ArrayList<>(monsters.keySet())));
            }
        } catch (Exception ignored) {}
    }

    public void loadShopFromAssets() {
        if (!shop.isEmpty()) return;
        try (InputStream is = app.getAssets().open(ASSET_SHOP)) {
            String json = readStream(is);
            Type t = new TypeToken<List<ShopEntry>>() {}.getType();
            List<ShopEntry> list = gson.fromJson(json, t);
            if (list != null) shop.addAll(list);
        } catch (Exception ignored) {}
    }

    private void loadRecipesFromAssets() {
        if (!recipes.isEmpty()) return;
        try (InputStream is = app.getAssets().open(ASSET_RECIPES)) {
            String json = readStream(is);
            JSONObject root = new JSONObject(json);
            JSONArray arr = root.optJSONArray("recipes");
            if (arr != null) {
                Type tt = new com.google.gson.reflect.TypeToken<List<Recipe>>() {}.getType();
                List<Recipe> list = new Gson().fromJson(arr.toString(), tt);
                if (list != null) for (Recipe r : list) if (r != null && r.id != null) recipes.put(r.id, r);
            }
        } catch (Exception ignored) {}
    }

    @Nullable public Recipe getRecipe(String id) { return recipes.get(id); }
    public List<Recipe> getRecipesBySkill(SkillId skill) {
        List<Recipe> out = new ArrayList<>();
        for (Recipe r : recipes.values()) if (r != null && r.skill == skill) out.add(r);
        return out;
    }

    private int countInBag(String itemId) {
        PlayerCharacter pc = loadOrCreatePlayer();
        String iid = canonicalItemId(itemId);
        return pc.bag.getOrDefault(iid, 0);
    }

    private String resolveItemAlias(String id) {
        if ("raw_shrimp".equalsIgnoreCase(id)) return "fish_raw_shrimp";
        if ("raw_crayfish".equalsIgnoreCase(id)) return "fish_raw_crayfish";
        return id;
    }

    public boolean canCraft(String recipeId, int times) {
        Recipe r = getRecipe(recipeId);
        if (r == null || times <= 0) return false;
        int lvl = skillLevel(r.skill);
        if (lvl < Math.max(1, r.reqLevel)) return false;

        if (r.inputs != null) {
            for (RecipeIO in : r.inputs) {
                if (in == null || in.id == null || in.qty <= 0) continue;
                String needId = resolveItemAlias(in.id);
                if (countInBag(needId) < in.qty * times) return false;
            }
        }
        return true;
    }

    public boolean craft(String recipeId, int times) {
        Recipe r = getRecipe(recipeId);
        if (r == null || times <= 0) { toast("Invalid recipe"); return false; }
        if (!canCraft(recipeId, times)) { toast("Missing materials or level"); return false; }

        PlayerCharacter pc = loadOrCreatePlayer();

        if (r.inputs != null) {
            for (RecipeIO in : r.inputs) {
                if (in == null || in.id == null || in.qty <= 0) continue;
                String needId = resolveItemAlias(in.id);
                int need = in.qty * times;
                int have = pc.bag.getOrDefault(needId, 0);
                if (have < need) { toast("Missing materials"); return false; }
            }
            for (RecipeIO in : r.inputs) {
                if (in == null || in.id == null || in.qty <= 0) continue;
                String needId = resolveItemAlias(in.id);
                int need = in.qty * times;
                int have = pc.bag.getOrDefault(needId, 0);
                pc.bag.put(needId, have - need);
                if (pc.bag.get(needId) != null && pc.bag.get(needId) <= 0) pc.bag.remove(needId);
            }
        }

        if (r.outputs != null) {
            for (RecipeIO out : r.outputs) {
                if (out == null || out.id == null || out.qty <= 0) continue;
                pc.addItem(out.id, out.qty * times);
            }
        }

        if (r.xp > 0 && r.skill != null) addSkillExp(r.skill, r.xp * times);

        save();
        toast("Crafted ×" + times + " " + (r.name != null ? r.name : r.id));
        return true;
    }

    public boolean craftOnce(String id) {
        if (id == null || id.isEmpty()) return false;
        Recipe r = getRecipe(id);
        if (r == null) { toast("Unknown recipe"); return false; }

        int level = skillLevel(r.skill);
        if (level < Math.max(1, r.reqLevel)) {
            toast((r.name != null ? r.name : r.id) + " requires Lv " + r.reqLevel);
            return false;
        }

        PlayerCharacter pc = loadOrCreatePlayer();

        if (r.inputs == null || r.inputs.isEmpty()) { toast("Recipe has no inputs"); return false; }
        for (RecipeIO in : r.inputs) {
            if (in == null || in.id == null || in.qty <= 0) continue;
            String needId = resolveItemAlias(in.id);
            int need = Math.max(1, in.qty);
            int have = pc.bag.getOrDefault(needId, 0);
            if (have < need) { toast("Need " + itemName(needId) + " ×" + need); return false; }
        }

        for (RecipeIO in : r.inputs) {
            if (in == null || in.id == null || in.qty <= 0) continue;
            String needId = resolveItemAlias(in.id);
            int need = Math.max(1, in.qty);
            int have = pc.bag.getOrDefault(needId, 0);
            pc.bag.put(needId, Math.max(0, have - need));
            if (pc.bag.get(needId) != null && pc.bag.get(needId) <= 0) pc.bag.remove(needId);
        }

        int totalOut = 0;
        String toastLabel = (r.name != null ? r.name : r.id);
        if (r.outputs != null) {
            for (RecipeIO out : r.outputs) {
                if (out == null || out.id == null || out.qty <= 0) continue;
                pc.addItem(out.id, out.qty);
                totalOut += out.qty;
                toastLabel = itemName(out.id);
            }
        }

        if (r.xp > 0 && r.skill != null) addSkillExp(r.skill, r.xp);

        save();
        if (totalOut > 0) toast("Cooked " + toastLabel + " ×" + totalOut);
        else toast("Crafted " + (r.name != null ? r.name : r.id));
        return true;
    }

    /* ============================
     * Slayer regions + config
     * ============================ */
    public static class SlayerRegion {
        public final String id;
        public final String label;
        public final List<String> monsterIds;
        public SlayerRegion(String id, String label, List<String> monsterIds) {
            this.id = id; this.label = label; this.monsterIds = monsterIds;
        }
    }
    private final Map<String, SlayerRegion> slayerRegions = new HashMap<>();

    public List<SlayerRegion> listSlayerRegions() { return new ArrayList<>(slayerRegions.values()); }

    public void registerSlayerRegion(String id, String label, List<String> monsterIds) {
        if (id == null || label == null || monsterIds == null || monsterIds.isEmpty()) return;
        List<String> filtered = new ArrayList<>();
        for (String mid : monsterIds) if (monsters.containsKey(mid)) filtered.add(mid);
        if (filtered.isEmpty()) return;
        slayerRegions.put(id, new SlayerRegion(id, label, filtered));
    }

    @Nullable private RegionCfg getRegionCfg(@Nullable String regionId) {
        if (regionId == null || slayerCfg == null || slayerCfg.regions == null) return null;
        for (RegionCfg r : slayerCfg.regions) if (r != null && regionId.equals(r.id)) return r;
        return null;
    }

    @Nullable private SlayerCfg slayerCfg;
    private static class SlayerCfg {
        @Nullable Costs costs;
        @Nullable KillCountCfg killCount;
        @Nullable List<RegionCfg> regions;
    }
    private static class Costs {
        @Nullable Integer roll;
        @Nullable Integer abandon;
        @Nullable Double  completionBonusPerKill;
        @Nullable Integer minCompletionBonus;
    }
    private static class KillCountCfg {
        int minBase = 100;
        int maxBase = 150;
        double bumpPerCombatLevel = 0.5;
        int maxBump = 50;
    }
    private static class RegionCfg {
        String id;
        String label;
        List<String> monsters;
        @Nullable Costs costs;
        @Nullable KillCountCfg killCount;
    }

    private void loadSlayerFromAssets() {
        try (InputStream is = app.getAssets().open(ASSET_SLAYER)) {
            String json = readStream(is);
            slayerCfg = gson.fromJson(json, SlayerCfg.class);
            if (slayerCfg != null && slayerCfg.regions != null) {
                for (RegionCfg r : slayerCfg.regions) {
                    if (r == null || r.id == null || r.label == null || r.monsters == null) continue;
                    List<String> filtered = new ArrayList<>();
                    for (String mid : r.monsters) if (monsters.containsKey(mid)) filtered.add(mid);
                    if (!filtered.isEmpty()) slayerRegions.put(r.id, new SlayerRegion(r.id, r.label, filtered));
                }
            }
        } catch (Exception ignored) {}

        if (slayerRegions.isEmpty() && !monsters.isEmpty()) {
            slayerRegions.put("basecamp", new SlayerRegion("basecamp", "Basecamp", new ArrayList<>(monsters.keySet())));
        }
    }

    private int effectiveRollCost(@Nullable String regionId) {
        RegionCfg r = getRegionCfg(regionId);
        if (r != null && r.costs != null && r.costs.roll != null) return Math.max(0, r.costs.roll);
        if (slayerCfg != null && slayerCfg.costs != null && slayerCfg.costs.roll != null)
            return Math.max(0, slayerCfg.costs.roll);
        return SLAYER_ROLL_COST_FALLBACK;
    }

    private int effectiveAbandonCost(@Nullable String regionId) {
        RegionCfg r = getRegionCfg(regionId);
        if (r != null && r.costs != null && r.costs.abandon != null) return Math.max(0, r.costs.abandon);
        if (slayerCfg != null && slayerCfg.costs != null && slayerCfg.costs.abandon != null)
            return Math.max(0, slayerCfg.costs.abandon);
        return SLAYER_ABANDON_COST_FALLBACK;
    }

    private int computeCompletionBonus(int required, @Nullable String regionId) {
        double perKill = SLAYER_BONUS_PER_KILL_FALLBACK;
        int minBonus = SLAYER_MIN_BONUS_FALLBACK;

        RegionCfg r = getRegionCfg(regionId);
        if (r != null && r.costs != null) {
            if (r.costs.completionBonusPerKill != null) perKill = r.costs.completionBonusPerKill;
            if (r.costs.minCompletionBonus != null)    minBonus = r.costs.minCompletionBonus;
        } else if (slayerCfg != null && slayerCfg.costs != null) {
            if (slayerCfg.costs.completionBonusPerKill != null) perKill = slayerCfg.costs.completionBonusPerKill;
            if (slayerCfg.costs.minCompletionBonus != null)     minBonus = slayerCfg.costs.minCompletionBonus;
        }
        int byRate = (int) Math.floor(required * Math.max(0.0, perKill));
        return Math.max(minBonus, byRate);
    }

    private int rollKillCountForLevel(int combatLevel, @Nullable String regionId) {
        int minBase = 100, maxBase = 150, maxBump = 50; double bumpPerCL = 0.5;

        KillCountCfg kc = null;
        RegionCfg r = getRegionCfg(regionId);
        if (r != null && r.killCount != null) kc = r.killCount;
        else if (slayerCfg != null && slayerCfg.killCount != null) kc = slayerCfg.killCount;

        if (kc != null) {
            minBase = kc.minBase;
            maxBase = kc.maxBase;
            bumpPerCL = kc.bumpPerCombatLevel;
            maxBump = kc.maxBump;
        }

        int bump = Math.min(maxBump, (int) Math.floor(combatLevel * bumpPerCL));
        int min = minBase + bump;
        int max = maxBase + bump;
        if (min > max) { int t = min; min = max; max = t; }
        return min + rng.nextInt((max - min) + 1);
    }

    /* ============================
     * Seeding
     * ============================ */
    public void seedMonsters(List<Monster> list) {
        if (list == null || list.isEmpty()) return;
        for (Monster m : list) if (m != null && m.id != null) monsters.put(m.id, ensureMonsterDefaults(m));
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
        int x = 0;
        int L = Math.max(1, lvl);
        for (int i = 1; i < L; i++) x += PlayerCharacter.xpForNextLevel(i);
        return x;
    }

    /** Wipe local JSON + transient Livedata and stop cloud sync (used when logging out or account deleted). */
    public void wipeLocalSave() {
        stopCloudSync();
        player = null;
        sp.edit()
                .remove(KEY_PLAYER)
                .remove(KEY_SLAYER_JSON)
                .remove(KEY_LOCAL_UPDATED_AT)
                .apply();
        currencyLive.postValue(new HashMap<>());
        playerHpLive.postValue(null);
        pendingLoot.clear();
        updatePendingLootLive();
    }

    public PlayerCharacter loadOrCreatePlayer() {
        if (player != null) return player;

        String json = sp.getString(KEY_PLAYER, null);
        if (json != null) {
            try {
                Type t = new TypeToken<PlayerCharacter>() {}.getType();
                player = gson.fromJson(json, t);
            } catch (Exception parseFail) {
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
                                    int xp = sobj.has("xp") ? sobj.optInt("xp", sobj.optInt("exp", 0)) : sobj.optInt("exp", 0);
                                    if (xp <= 0) {
                                        int lvl = Math.max(1, sobj.optInt("level", sobj.optInt("lvl", 1)));
                                        xp = xpAtStartOfLevel(lvl);
                                    }
                                    skills.put(key, xp);
                                } else if (val == JSONObject.NULL) {
                                    skills.put(key, 0);
                                } else if (val instanceof String) {
                                    try { skills.put(key, Integer.parseInt((String) val)); }
                                    catch (NumberFormatException ignored) { skills.put(key, 0); }
                                }
                            }
                        }
                    }
                    Type t = new TypeToken<PlayerCharacter>() {}.getType();
                    player = gson.fromJson(root.toString(), t);
                } catch (Exception migrateFail) {
                    player = null;
                }
            }

            if (player != null) {
                if (player.bag == null) player.bag = new HashMap<>();
                if (player.equipment == null) player.equipment = new EnumMap<>(EquipmentSlot.class);
                if (player.skills == null) player.skills = new EnumMap<>(SkillId.class);
                if (player.currencies == null) player.currencies = new HashMap<>();
                if (player.base == null) player.base = new Stats(12, 6, 0.0, 100, 0.05, 1.5);

                player.migrateSkillsToXpIfNeeded();

                if (player.base.health <= 0) player.base.health = 100;
                if (player.base.critMultiplier < 1.0) player.base.critMultiplier = 1.5;
                if (player.base.critChance < 0) player.base.critChance = 0;
                if (player.base.critChance > 1) player.base.critChance = 1;

                player.normalizeCurrencies();

                // Fix HP only if invalid; otherwise keep saved HP
                int maxHpNow = maxHp(player);
                Integer savedHp = player.currentHp;
                if (savedHp == null || savedHp <= 0 || savedHp > maxHpNow) {
                    player.currentHp = maxHpNow;
                }

                save();
                publishCurrencies();
                publishHp();
                loadSlayerFromSpIfPresent();
                return player;
            }
        }

        // New player seed
        player = new PlayerCharacter();
        player.normalizeCurrencies();

        // Starter items
        addToBag("food_apple", 1000);
        addToBag("pot_basic_combat", 1000);
        addToBag("pot_basic_noncombat", 1000);
        addToBag("syrup_basic", 1000);

        if (player.currentHp == null) {
            player.currentHp = maxHp(player);
        }

        save();
        publishCurrencies();
        publishHp();
        loadSlayerFromSpIfPresent();
        return player;
    }

    private void loadSlayerFromSpIfPresent() {
        if (slayerLive.getValue() != null) return;
        String s = sp.getString(KEY_SLAYER_JSON, null);
        if (s != null) {
            try { SlayerAssignment a = gson.fromJson(s, SlayerAssignment.class); publishSlayer(a); }
            catch (Exception ignored) {}
        }
    }

    public void save() {
        if (player == null) return;
        sp.edit().putString(KEY_PLAYER, gson.toJson(player)).apply();
        touchLocalUpdatedAt();
        cloudSavePlayerDebounced();
    }

    /* ============================
     * Lookups & helpers
     * ============================ */
    @Nullable public Item getItem(String id) { return items.get(id); }
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
        try { return EquipmentSlot.valueOf(it.slot); }
        catch (IllegalArgumentException e) {
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

    public void updatePlayerHp(int newHp) {
        PlayerCharacter pc = loadOrCreatePlayer();
        setHpAndPublish(pc, newHp); // uses the existing clamp + save + publishHp()
    }

    public void updatePlayerInventory(@NonNull Map<String, Integer> updates) {
        if (updates == null || updates.isEmpty()) return;
        PlayerCharacter pc = loadOrCreatePlayer();

        // Track deltas so we can mirror to cloud incrementally
        Map<String, Integer> deltas = new HashMap<>();

        for (Map.Entry<String, Integer> e : updates.entrySet()) {
            String rawId = e.getKey();
            if (rawId == null) continue;

            String id = String.valueOf(canonicalItemId(rawId));
            int prev = pc.bag.getOrDefault(id, 0);
            int q = Math.max(0, e.getValue() == null ? 0 : e.getValue());

            if (q == prev) continue; // no change

            if (q == 0) pc.bag.remove(id);
            else pc.bag.put(id, q);

            deltas.put(id, q - prev);
        }

        if (deltas.isEmpty()) return; // nothing changed

        save(); // persist all changes at once

        // Mirror each change to Firestore incrementally
        for (Map.Entry<String, Integer> d : deltas.entrySet()) {
            int delta = d.getValue();
            if (delta != 0) cloudIncrementBag(d.getKey(), delta);
        }
    }

    /* ============================
     * Skill helpers (for UI)
     * ============================ */
    public int skillLevel(SkillId id) { return loadOrCreatePlayer().getSkillLevel(id); }
    public int skillExp(SkillId id)   { return loadOrCreatePlayer().getSkillExp(id); }

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
     * Combat training skill
     * ============================ */
    public @Nullable SkillId getCombatTrainingSkill() {
        String s = sp.getString(KEY_TRAIN_SKILL, null);
        if (s != null) {
            try {
                SkillId id = SkillId.valueOf(s);
                if (isCombatSkillEnum(id)) return id;
            } catch (Exception ignored) {}
        }
        return SkillId.ATTACK;
    }

    public void setCombatTrainingSkill(@Nullable SkillId id) {
        if (id == null) { sp.edit().remove(KEY_TRAIN_SKILL).apply(); return; }
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
            if (id == SkillId.HP) publishHp();
            toast(id.name().charAt(0) + id.name().substring(1).toLowerCase() +
                    " Lv " + pc.getSkillLevel(id) + "!");
        }
        return leveled;
    }

    /* ============================
     * Gathering state
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
     * Battle state
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
     * Slayer API
     * ============================ */
    private void publishSlayer(@Nullable SlayerAssignment a) { slayerLive.postValue(a); }
    private void persistSlayerToSp(@Nullable SlayerAssignment a) {
        SharedPreferences.Editor ed = sp.edit();
        if (a == null) ed.remove(KEY_SLAYER_JSON);
        else ed.putString(KEY_SLAYER_JSON, gson.toJson(a));
        ed.apply();
    }
    public @Nullable SlayerAssignment getSlayerAssignment() { loadSlayerFromSpIfPresent(); return slayerLive.getValue(); }

    public void assignSlayerTask(String regionId, String monsterId, int required, int completionBonus, @Nullable String label) {
        Monster m = getMonster(monsterId);
        String fallback = (m != null && m.name != null) ? m.name : monsterId;
        SlayerAssignment a = new SlayerAssignment(
                regionId, monsterId, (label != null ? label : fallback),
                Math.max(1, required), Math.max(0, completionBonus)
        );
        persistSlayerToSp(a);
        publishSlayer(a);
        toast("Slayer task: " + a.label + " ×" + a.required);
    }

    public void assignSlayerTask(String monsterId, int required, int completionBonus) {
        assignSlayerTask("basecamp", monsterId, required, completionBonus, null);
    }

    public void clearSlayerTask() { persistSlayerToSp(null); publishSlayer(null); }

    public void onMonsterKilled(@Nullable String monsterId) {
        if (monsterId == null) return;
        SlayerAssignment a = getSlayerAssignment();
        if (a == null || a.isComplete()) return;
        if (!monsterId.equalsIgnoreCase(a.monsterId)) return;

        Monster m = getMonster(a.monsterId);
        if (m != null && m.slayerReward > 0) {
            addCurrency("slayer", m.slayerReward);
            toast("+" + m.slayerReward + " Slayer");
        }

        a.done = Math.max(0, a.done) + 1;
        persistSlayerToSp(a);
        publishSlayer(a);
        if (a.isComplete()) toast("Task complete — claim your reward!");
    }

    public void onMonsterKilled(@Nullable Monster m) { onMonsterKilled(m != null ? m.id : null); }

    public int getCombatLevel() {
        int atk = skillLevel(SkillId.ATTACK);
        int str = skillLevel(SkillId.STRENGTH);
        int def = skillLevel(SkillId.DEFENSE);
        int arch= skillLevel(SkillId.ARCHERY);
        int mag = skillLevel(SkillId.MAGIC);
        int hp  = skillLevel(SkillId.HP);
        int offensive = Math.max(Math.max(atk, str), Math.max(arch, mag));
        double cl = (offensive + def + (hp * 0.5)) / 2.0;
        int out = (int)Math.round(cl);
        if (out < 1) out = 1;
        if (out > 99) out = 99;
        return out;
    }

    @Nullable public SlayerAssignment rollNewSlayerTask() { return rollNewSlayerTask("basecamp", false); }
    @Nullable public SlayerAssignment rollNewSlayerTask(boolean forceReplace) { return rollNewSlayerTask("basecamp", forceReplace); }

    @Nullable
    public SlayerAssignment rollNewSlayerTask(String regionId, boolean forceReplace) {
        SlayerAssignment cur = getSlayerAssignment();
        boolean rerolling = (cur != null && !cur.isComplete());
        if (rerolling && !forceReplace) { toast("Finish or abandon your current task first."); return cur; }

        SlayerRegion reg = slayerRegions.get(regionId);
        if (reg == null || reg.monsterIds == null || reg.monsterIds.isEmpty()) {
            toast("No Slayer monsters in this region yet.");
            return cur;
        }

        if (rerolling) {
            int rollCost = effectiveRollCost(regionId);
            if (rollCost > 0 && !spendCurrency("slayer", rollCost)) {
                toast("Need " + rollCost + " Slayer coins to reroll.");
                return cur;
            }
        }

        String monsterId = reg.monsterIds.get(rng.nextInt(reg.monsterIds.size()));
        Monster m = getMonster(monsterId);

        int required = rollKillCountForLevel(getCombatLevel(), regionId);
        int completionBonus = computeCompletionBonus(required, regionId);
        String label = reg.label + " — " + (m != null && m.name != null ? m.name : monsterId);

        assignSlayerTask(regionId, monsterId, required, completionBonus, label);
        toast(rerolling ? "Task rerolled." : "Task assigned.");
        return slayerLive.getValue();
    }

    @Nullable
    public SlayerAssignment rollNewSlayerTask(String regionId, String monsterId) {
        SlayerAssignment cur = getSlayerAssignment();
        boolean rerolling = (cur != null && !cur.isComplete());

        SlayerRegion reg = slayerRegions.get(regionId);
        if (reg == null || reg.monsterIds == null || reg.monsterIds.isEmpty()) {
            toast("No Slayer monsters in this region yet.");
            return cur;
        }
        if (!reg.monsterIds.contains(monsterId)) {
            toast("That monster isn’t in this region.");
            return cur;
        }

        if (rerolling) {
            int rollCost = effectiveRollCost(regionId);
            if (rollCost > 0 && !spendCurrency("slayer", rollCost)) {
                toast("Need " + rollCost + " Slayer coins to reroll.");
                return cur;
            }
        }

        Monster m = getMonster(monsterId);
        int required = rollKillCountForLevel(getCombatLevel(), regionId);
        int completionBonus = computeCompletionBonus(required, regionId);
        String label = reg.label + " — " + (m != null && m.name != null ? m.name : monsterId);

        assignSlayerTask(regionId, monsterId, required, completionBonus, label);
        toast(rerolling ? "Task rerolled." : "Task assigned.");
        return slayerLive.getValue();
    }

    public boolean abandonSlayerTask() {
        SlayerAssignment a = getSlayerAssignment();
        if (a == null) { toast("No Slayer task to abandon."); return false; }
        int cost = effectiveAbandonCost(a.regionId);
        if (!spendCurrency("slayer", cost)) { toast("Need " + cost + " Slayer coins to abandon."); return false; }
        clearSlayerTask();
        toast("Task abandoned (−" + cost + " Slayer).");
        return true;
    }

    public boolean claimSlayerTaskIfComplete() {
        SlayerAssignment a = getSlayerAssignment();
        if (a == null) { toast("No Slayer task active."); return false; }
        if (!a.isComplete()) { toast("Task not complete yet."); return false; }

        int reward = a.completionBonus > 0 ? a.completionBonus : computeCompletionBonus(a.required, a.regionId);
        addCurrency("slayer", reward);
        toast("Task reward: +" + reward + " Slayer!");
        clearSlayerTask();
        return true;
    }

    /* ============================
     * Food & Potions
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

    public void consumeFood(String foodId) {
        PlayerCharacter pc = loadOrCreatePlayer();
        Integer have = pc.bag.get(foodId);
        if (have == null || have <= 0) return;

        Item it = getItem(foodId);
        if (it == null || !isFood(it) || it.heal == null || it.heal <= 0) return;

        int before = curHp(pc);
        int after  = Math.min(maxHp(pc), before + it.heal);
        int healed = after - before;

        if (healed > 0) {
            pc.currentHp = after;
            toast("+" + healed + " HP");
        } else {
            toast("HP already full");
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
            int before = curHp(pc);
            int after  = Math.min(maxHp(pc), Math.max(0, before) + it.heal);
            int healed = after - before;
            pc.currentHp = after;
            toast(healed > 0 ? ("+" + healed + " HP") : "HP already full");
        }
        pc.bag.put(potionId, have - 1);
        if (pc.bag.get(potionId) != null && pc.bag.get(potionId) <= 0) pc.bag.remove(potionId);
        save();
        publishHp();
    }

    public void consumeSyrup() {
        PlayerCharacter pc = loadOrCreatePlayer();

        String syrupId = null;
        for (String id : new HashSet<>(pc.bag.keySet())) {
            Item it = getItem(id);
            if (it != null && "CONSUMABLE".equals(it.type)) {
                String nid = it.id != null ? it.id.toLowerCase() : "";
                String nname = it.name != null ? it.name.toLowerCase() : "";
                if (nid.contains("syrup") || nname.contains("syrup")) { syrupId = id; break; }
            }
        }
        if (syrupId == null) { toast("No syrup available"); return; }

        Item syrup = getItem(syrupId);
        boolean didSomething = false;

        if (syrup != null && syrup.heal != null && syrup.heal > 0) {
            int before = curHp(pc);
            int after  = Math.min(maxHp(pc), Math.max(0, before) + syrup.heal);
            int healed = after - before;
            pc.currentHp = after;
            didSomething = true;
            toast(healed > 0 ? ("+" + healed + " HP") : "HP already full");
        } else {
            pc.base.speed += 0.05;
            didSomething = true;
            toast("Speed +0.05");
        }

        if (didSomething) {
            Integer have = pc.bag.get(syrupId);
            if (have != null) {
                pc.bag.put(syrupId, have - 1);
                if (pc.bag.get(syrupId) != null && pc.bag.get(syrupId) <= 0) pc.bag.remove(syrupId);
            }
            save();
            publishHp();
        }
    }

    /* ============================
     * Equipment API
     * ============================ */
    public boolean equip(String itemId) {
        PlayerCharacter pc = loadOrCreatePlayer();
        Item it = getItem(itemId);
        if (it == null) { toast("Unknown item"); return false; }
        EquipmentSlot slot = slotOf(it);
        if (slot == null) { toast("Can't equip: no slot"); return false; }

        Integer have = pc.bag.get(itemId);
        if (have == null || have <= 0) { toast("You don't have that item"); return false; }

        pc.bag.put(itemId, have - 1);
        if (pc.bag.get(itemId) != null && pc.bag.get(itemId) <= 0) pc.bag.remove(itemId);

        String prev = pc.equipment.put(slot, itemId);
        if (prev != null) pc.addItem(prev, 1);

        int m = maxHp(pc);
        pc.currentHp = (pc.currentHp == null) ? m : Math.min(pc.currentHp, m);

        save();
        publishHp();
        toast("Equipped " + itemName(itemId));
        return true;
    }

    public boolean unequip(EquipmentSlot slot) {
        PlayerCharacter pc = loadOrCreatePlayer();
        if (slot == null) return false;
        String prev = pc.equipment.remove(slot);
        if (prev == null) return false;

        pc.addItem(prev, 1);

        int m = maxHp(pc);
        pc.currentHp = (pc.currentHp == null) ? m : Math.min(pc.currentHp, m);

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
        return k.equals("ATTACK") || k.equals("STRENGTH") || k.equals("DEFENSE") || k.equals("HP")
                || k.equals("ARCHERY") || k.equals("MAGIC");
    }

    private static @Nullable String canonicalItemId(@Nullable String id) {
        if (id == null) return null;
        String s = id.toLowerCase();
        if (s.equals("raw_shrimp") || s.equals("shore_shrimp") || s.equals("shrimp_raw")) return "fish_raw_shrimp";
        if (s.equals("fish_raw_shrimp")) return "fish_raw_shrimp";
        return s;
    }

    /* ============================
     * Pending loot (combat)
     * ============================ */
    private final List<PendingLoot> pendingLoot = new ArrayList<>();
    public final MutableLiveData<List<InventoryItem>> pendingLootLive = new MutableLiveData<>(new ArrayList<>());

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
                cloudIncrementCurrency(code, pl.quantity);
            } else {
                pc.addItem(pl.id, pl.quantity);
                cloudIncrementBag(pl.id, pl.quantity);
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
     * Gathering: direct rewards
     * ============================ */
    public void grantGatheredItem(String itemId, int qty) {
        if (itemId == null || qty <= 0) return;
        PlayerCharacter pc = loadOrCreatePlayer();
        pc.addItem(itemId, qty);
        save();
        cloudIncrementBag(itemId, qty);
    }

    public void grantGatheredCurrency(String code, long amount) {
        if (code == null || amount <= 0) return;
        addCurrency(code, amount);
        toast("+" + amount + " " + capitalize(code));
    }

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
    @Nullable private ShopEntry findShopByItem(String itemId) {
        for (ShopEntry e : shop) if (e != null && itemId.equals(e.itemId)) return e;
        return null;
    }

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

        if (needGold > 0) spendCurrency("gold", needGold);
        if (needSilver > 0) spendCurrency("silver", needSilver);

        PlayerCharacter pc = loadOrCreatePlayer();
        pc.addItem(itemId, qty);
        save();

        Item def = getItem(itemId);
        String name = def != null && def.name != null ? def.name : itemId;
        toast("Bought +" + qty + "× " + name);
        return true;
    }

    public boolean sellItem(String itemId, int qty) {
        if (qty <= 0) return false;
        ShopEntry se = findShopByItem(itemId);
        if (se == null) { toast("Can't sell here"); return false; }

        PlayerCharacter pc = loadOrCreatePlayer();
        int have = pc.bag.getOrDefault(itemId, 0);
        if (have < qty) { toast("Not enough in bag"); return false; }

        pc.bag.put(itemId, have - qty);
        if (pc.bag.get(itemId) != null && pc.bag.get(itemId) <= 0) pc.bag.remove(itemId);

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
     * Currency helpers
     * ============================ */
    public long getCurrency(String id) { return loadOrCreatePlayer().getCurrency(id); }

    public void addCurrency(String id, long amount) {
        PlayerCharacter pc = loadOrCreatePlayer();
        pc.addCurrency(id, amount);
        save();
        publishCurrencies();
        cloudIncrementCurrency(id, amount);
    }

    public boolean spendCurrency(String id, long amount) {
        PlayerCharacter pc = loadOrCreatePlayer();
        boolean ok = pc.spendCurrency(id, amount);
        if (ok) {
            save();
            publishCurrencies();
            cloudIncrementCurrency(id, -amount);
        }
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
     * Per-skill “last picked action”
     * ============================ */
    private static String keyLastActionFor(SkillId skill) { return "last_action_" + (skill != null ? skill.name() : "unknown"); }

    public void saveLastActionForSkill(@Nullable SkillId skill, @Nullable String actionId) {
        if (skill == null || actionId == null) return;
        sp.edit().putString(keyLastActionFor(skill), actionId).apply();
    }

    @Nullable
    public String getLastActionForSkill(@Nullable SkillId skill) {
        if (skill == null) return null;
        return sp.getString(keyLastActionFor(skill), null);
    }

    public boolean isUnlocked(@Nullable Action a, int currentLevel) {
        if (a == null) return false;
        int req = Math.max(1, a.reqLevel);
        int lvl = Math.max(1, currentLevel);
        return lvl >= req;
    }

    public boolean isUnlocked(@Nullable Action a) {
        if (a == null || a.skill == null) return false;
        int playerLevel = skillLevel(a.skill);
        return isUnlocked(a, playerLevel);
    }

    @Nullable
    public Action bestUnlockedFor(@Nullable SkillId skill, int currentLevel) {
        if (skill == null) return null;
        Action best = null;
        int bestReq = -1;
        for (Action a : actions.values()) {
            if (a == null || a.skill != skill) continue;
            int req = Math.max(1, a.reqLevel);
            if (currentLevel >= req && req > bestReq) { best = a; bestReq = req; }
        }
        return best;
    }

    @Nullable
    public Action bestUnlockedFor(@Nullable SkillId skill) {
        if (skill == null) return null;
        return bestUnlockedFor(skill, skillLevel(skill));
    }

    public void setLastPickedAction(@Nullable SkillId skill, @Nullable String actionId) {
        saveLastActionForSkill(skill, actionId);
    }
    public void setLastPickedAction(@Nullable Action a) { if (a != null) saveLastActionForSkill(a.skill, a.id); }

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

    public String normalizeItemIdForBag(String id) {
        if (id == null) return null;
        PlayerCharacter pc = loadOrCreatePlayer();
        if ("raw_shrimp".equalsIgnoreCase(id)
                && !pc.bag.containsKey(id)
                && pc.bag.containsKey("fish_raw_shrimp")) {
            return "fish_raw_shrimp";
        }
        return id;
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
        while ((n = is.read(buf)) != -1) sb.append(new String(buf, 0, n, StandardCharsets.UTF_8));
        return sb.toString();
    }

    /* ============================
     * Toast Helper
     * ============================ */
    public void toast(String msg) {
        if (Looper.myLooper() == Looper.getMainLooper()) throttleToast(msg);
        else mainHandler.post(() -> throttleToast(msg));
    }

    private void throttleToast(String msg) {
        long now = SystemClock.uptimeMillis();
        if (now >= nextAllowedToastAt) { showToastNow(msg); return; }
        if (deferredToastMsg != null && deferredToastMsg.equals(msg)) deferredToastCount++;
        else { deferredToastMsg = msg; deferredToastCount = 1; }
        mainHandler.removeCallbacks(toastDrain);
        mainHandler.postAtTime(toastDrain, nextAllowedToastAt);
    }

    private void showToastNow(String msg) {
        try {
            if (currentToast != null) currentToast.cancel();
            currentToast = Toast.makeText(app, msg, Toast.LENGTH_SHORT);
            currentToast.show();
            nextAllowedToastAt = SystemClock.uptimeMillis() + TOAST_MIN_INTERVAL_MS;
        } catch (Throwable ignored) {}
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

    public void giveItem(String itemId, int qty) {
        if (itemId == null || qty == 0) return;
        PlayerCharacter pc = loadOrCreatePlayer();
        pc.addItem(itemId, qty);
        save();
        cloudIncrementBag(itemId, qty);
        toast("Granted " + qty + "× " + itemName(itemId));
    }

    /* ============================
     * Firestore helpers
     * ============================ */
    @Nullable
    private DocumentReference charDoc() {
        FirebaseUser u = auth.getCurrentUser();
        if (u == null) return null;
        return fdb.collection("playerCharacters").document(u.getUid());
    }

    private Map<String, Object> toMap(PlayerCharacter pc) {
        java.lang.reflect.Type t = new com.google.gson.reflect.TypeToken<Map<String, Object>>(){}.getType();
        return gson.fromJson(gson.toJson(pc), t);
    }

    private void cloudSavePlayerDebounced() {
        if (auth.getCurrentUser() == null || player == null) return;
        if (cloudSaveRunnable != null) mainHandler.removeCallbacks(cloudSaveRunnable);
        cloudSaveRunnable = () -> {
            try {
                DocumentReference doc = charDoc();
                if (doc == null || player == null) return;
                Map<String, Object> data = toMap(player);
                data.put("updatedAt", FieldValue.serverTimestamp());
                doc.set(data, SetOptions.merge());
            } catch (Throwable t) {
                Log.w(TAG, "cloudSavePlayerDebounced failed", t);
            }
        };
        mainHandler.postDelayed(cloudSaveRunnable, CLOUD_DEBOUNCE_MS);
    }

    private void cloudIncrementCurrency(String code, long delta) {
        DocumentReference doc = charDoc();
        if (doc == null || code == null) return;
        Map<String, Object> patch = new HashMap<>();
        patch.put("currencies." + code, FieldValue.increment(delta));
        patch.put("updatedAt", FieldValue.serverTimestamp());
        doc.set(patch, SetOptions.merge());
    }

    private void cloudIncrementBag(String itemId, int delta) {
        if (itemId == null || delta == 0) return;
        DocumentReference doc = charDoc();
        if (doc == null) return;
        String key = "bag." + String.valueOf(canonicalItemId(itemId));
        Map<String, Object> patch = new HashMap<>();
        patch.put(key, FieldValue.increment(delta));
        patch.put("updatedAt", FieldValue.serverTimestamp());
        doc.set(patch, SetOptions.merge());
    }

    public void startCloudSync() {
        DocumentReference doc = charDoc();
        if (doc == null) return;
        if (cloudListener != null) cloudListener.remove();
        cloudListener = doc.addSnapshotListener((snap, e) -> {
            if (e != null) { Log.w(TAG, "cloud listen error", e); return; }
            // Hook for live cloud->local merges if desired later.
        });
    }

    public void stopCloudSync() { if (cloudListener != null) { cloudListener.remove(); cloudListener = null; } }

    /* ============================
     * Cloud pull-if-newer
     * ============================ */
    public interface BoolCallback { void onResult(boolean updated); }

    private long getLocalUpdatedAt() { return sp.getLong(KEY_LOCAL_UPDATED_AT, 0L); }
    private void touchLocalUpdatedAt() { sp.edit().putLong(KEY_LOCAL_UPDATED_AT, System.currentTimeMillis()).apply(); }

    public void loadFromCloudIfNewer(@NonNull BoolCallback cb) {
        DocumentReference doc = charDoc();
        if (doc == null) { cb.onResult(false); return; }

        final long localMs = getLocalUpdatedAt();

        doc.get().addOnSuccessListener(snap -> {
            if (snap == null || !snap.exists()) {
                try {
                    loadOrCreatePlayer();
                    cloudSavePlayerDebounced();
                } catch (Throwable ignored) {}
                cb.onResult(false);
                return;
            }

            Timestamp ts = snap.getTimestamp("updatedAt");
            long remoteMs = (ts != null) ? ts.toDate().getTime() : 0L;

            if (localMs >= remoteMs) {
                if (remoteMs == 0L) cloudSavePlayerDebounced();
                cb.onResult(false);
                return;
            }

            try {
                Map<String, Object> data = snap.getData();
                if (data == null) { cb.onResult(false); return; }

                String json = gson.toJson(data);
                PlayerCharacter cloudPc = gson.fromJson(json, PlayerCharacter.class);

                if (cloudPc != null) {
                    if (cloudPc.bag == null) cloudPc.bag = new HashMap<>();
                    if (cloudPc.equipment == null) cloudPc.equipment = new EnumMap<>(EquipmentSlot.class);
                    if (cloudPc.skills == null) cloudPc.skills = new EnumMap<>(SkillId.class);
                    if (cloudPc.currencies == null) cloudPc.currencies = new HashMap<>();
                    if (cloudPc.base == null) cloudPc.base = new Stats(12, 6, 0.0, 100, 0.05, 1.5);

                    cloudPc.migrateSkillsToXpIfNeeded();

                    if (cloudPc.base.health <= 0) cloudPc.base.health = 100;
                    if (cloudPc.base.critMultiplier < 1.0) cloudPc.base.critMultiplier = 1.5;
                    if (cloudPc.base.critChance < 0) cloudPc.base.critChance = 0;
                    if (cloudPc.base.critChance > 1) cloudPc.base.critChance = 1;

                    cloudPc.normalizeCurrencies();
                    player = cloudPc;

                    sp.edit().putString(KEY_PLAYER, gson.toJson(player)).apply();
                    sp.edit().putLong(KEY_LOCAL_UPDATED_AT, remoteMs).apply();

                    publishCurrencies();
                    publishHp();
                    loadSlayerFromSpIfPresent();
                    cb.onResult(true);
                } else {
                    cb.onResult(false);
                }
            } catch (Throwable t) {
                Log.w(TAG, "Failed to merge cloud -> local", t);
                cb.onResult(false);
            }
        }).addOnFailureListener(e -> {
            Log.w(TAG, "loadFromCloudIfNewer failed", e);
            cb.onResult(false);
        });
    }

    public void forcePullFromCloud(@NonNull BoolCallback cb) {
        DocumentReference doc = charDoc();
        if (doc == null) { cb.onResult(false); return; }
        doc.get().addOnSuccessListener(snap -> {
            if (snap == null || !snap.exists()) { cb.onResult(false); return; }
            try {
                Map<String,Object> data = snap.getData();
                if (data == null) { cb.onResult(false); return; }
                String json = gson.toJson(data);
                PlayerCharacter cloudPc = gson.fromJson(json, PlayerCharacter.class);

                if (cloudPc != null) {
                    if (cloudPc.bag == null) cloudPc.bag = new HashMap<>();
                    if (cloudPc.equipment == null) cloudPc.equipment = new EnumMap<>(EquipmentSlot.class);
                    if (cloudPc.skills == null) cloudPc.skills = new EnumMap<>(SkillId.class);
                    if (cloudPc.currencies == null) cloudPc.currencies = new HashMap<>();
                    if (cloudPc.base == null) cloudPc.base = new Stats(12, 6, 0.0, 100, 0.05, 1.5);

                    cloudPc.migrateSkillsToXpIfNeeded();
                    cloudPc.normalizeCurrencies();

                    player = cloudPc;
                    sp.edit().putString(KEY_PLAYER, gson.toJson(player)).apply();

                    Timestamp ts = snap.getTimestamp("updatedAt");
                    long remoteMs = (ts != null) ? ts.toDate().getTime() : System.currentTimeMillis();
                    sp.edit().putLong(KEY_LOCAL_UPDATED_AT, remoteMs).apply();

                    publishCurrencies();
                    publishHp();
                    loadSlayerFromSpIfPresent();
                    cb.onResult(true);
                } else {
                    cb.onResult(false);
                }
            } catch (Throwable t) {
                Log.w(TAG, "forcePullFromCloud failed", t);
                cb.onResult(false);
            }
        }).addOnFailureListener(e -> {
            Log.w(TAG, "forcePullFromCloud get failed", e);
            cb.onResult(false);
        });
    }
}
