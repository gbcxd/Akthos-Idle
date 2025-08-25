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
import com.example.akthosidle.domain.model.Recipe;
import com.example.akthosidle.domain.model.RecipeIO;
import com.example.akthosidle.domain.model.ShopEntry;
import com.example.akthosidle.domain.model.SkillId;
import com.example.akthosidle.domain.model.Stats;
import com.example.akthosidle.domain.model.SlayerAssignment;
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

/** Central game repository (definitions + save + live state). */
public class GameRepository {

    private static final String SP_NAME = "akthos_idle_save";
    private static final String KEY_PLAYER = "player_json";
    private static final String KEY_LAST_SEEN = "last_seen_ms";
    // persisted “which combat skill should get kill XP”
    private static final String KEY_TRAIN_SKILL = "combat_training_skill";
    // persisted Slayer assignment (json)
    private static final String KEY_SLAYER_JSON = "slayer_assignment_json";

    private static final String ASSET_ITEMS    = "game/items.v1.json";
    private static final String ASSET_ACTIONS  = "game/actions.v1.json";
    private static final String ASSET_MONSTERS = "game/monsters.v1.json";
    private static final String ASSET_SHOP     = "game/shop.v1.json";
    private static final String ASSET_SLAYER   = "game/slayer.v1.json";

    private static final String ASSET_RECIPES = "game/recipes.v1.json";

    private final java.util.Map<String, Recipe> recipes = new java.util.HashMap<>();

    // Slayer fallback values if JSON is missing
    private static final int    SLAYER_ROLL_COST_FALLBACK        = 5;
    private static final int    SLAYER_ABANDON_COST_FALLBACK     = 2;
    private static final double SLAYER_BONUS_PER_KILL_FALLBACK   = 0.25; // required/4
    private static final int    SLAYER_MIN_BONUS_FALLBACK        = 10;

    private final Context app;
    private final SharedPreferences sp;
    private final Gson gson = new Gson();

    // Definitions
    public final Map<String, Item> items = new HashMap<>();
    private final Map<String, Monster> monsters = new HashMap<>();
    private final Map<String, Action> actions = new HashMap<>();
    // Shop definitions
    private final List<ShopEntry> shop = new ArrayList<>();

    // RNG
    private final Random rng = new Random();

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

    public GameRepository(Context appContext) {
        this.app = appContext.getApplicationContext();
        this.sp = app.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
    }

    /* =========================================================
     * Load static definitions (items / monsters / actions / slayer / shop).
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
            // create a default region if no JSON will be present (safety)
            if (slayerRegions.isEmpty() && !monsters.isEmpty()) {
                slayerRegions.put("basecamp",
                        new SlayerRegion("basecamp", "Basecamp", new ArrayList<>(monsters.keySet())));
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
     * Recipes registry + JSON config
     * ============================ */
    private void loadRecipesFromAssets() {
        if (!recipes.isEmpty()) return;
        try (InputStream is = app.getAssets().open(ASSET_RECIPES)) {
            String json = readStream(is);
            org.json.JSONObject root = new org.json.JSONObject(json);
            org.json.JSONArray arr = root.optJSONArray("recipes");
            if (arr != null) {
                com.google.gson.reflect.TypeToken<java.util.List<Recipe>> tt =
                        new com.google.gson.reflect.TypeToken<java.util.List<Recipe>>() {};
                java.util.List<Recipe> list = new Gson().fromJson(arr.toString(), tt.getType());
                if (list != null) {
                    for (Recipe r : list) if (r != null && r.id != null) recipes.put(r.id, r);
                }
            }
        } catch (Exception ignored) {}
    }

    @Nullable public Recipe getRecipe(String id) { return recipes.get(id); }

    public java.util.List<Recipe> getRecipesBySkill(SkillId skill) {
        java.util.List<Recipe> out = new java.util.ArrayList<>();
        for (Recipe r : recipes.values()) if (r != null && r.skill == skill) out.add(r);
        return out;
    }

    private int countInBag(String itemId) {
        PlayerCharacter pc = loadOrCreatePlayer();
        String iid = canonicalItemId(itemId);
        return pc.bag.getOrDefault(iid, 0);
    }

    // Map recipe ingredient ids to actual bag ids (handles legacy/raw names)
    /** Map recipe ingredient ids to actual item ids in items.json. */
    private String resolveItemAlias(@Nullable String id) {
        if (id == null) return null;
        // Recipes say "raw_shrimp", items.json has "fish_raw_shrimp"
        if ("raw_shrimp".equalsIgnoreCase(id)) return "fish_raw_shrimp";
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

        // pre-check
        if (r.inputs != null) {
            for (RecipeIO in : r.inputs) {
                if (in == null || in.id == null || in.qty <= 0) continue;
                String needId = resolveItemAlias(in.id);
                int need = in.qty * times;
                int have = pc.bag.getOrDefault(needId, 0);
                if (have < need) { toast("Missing materials"); return false; }
            }
            // consume
            for (RecipeIO in : r.inputs) {
                if (in == null || in.id == null || in.qty <= 0) continue;
                String needId = resolveItemAlias(in.id);
                int need = in.qty * times;
                int have = pc.bag.getOrDefault(needId, 0);
                pc.bag.put(needId, have - need);
                if (pc.bag.get(needId) != null && pc.bag.get(needId) <= 0) pc.bag.remove(needId);
            }
        }

        // outputs
        if (r.outputs != null) {
            for (RecipeIO out : r.outputs) {
                if (out == null || out.id == null || out.qty <= 0) continue;
                pc.addItem(out.id, out.qty * times);
            }
        }

        // XP
        if (r.xp > 0 && r.skill != null) addSkillExp(r.skill, r.xp * times);

        save();
        toast("Crafted ×" + times + " " + (r.name != null ? r.name : r.id));
        return true;
    }

    public boolean craftOnce(String id) {
        if (id == null || id.isEmpty()) return false;
        Recipe r = getRecipe(id);
        if (r == null) { toast("Unknown recipe"); return false; }

        // level gate
        int level = skillLevel(r.skill);
        if (level < Math.max(1, r.reqLevel)) {
            toast((r.name != null ? r.name : r.id) + " requires Lv " + r.reqLevel);
            return false;
        }

        PlayerCharacter pc = loadOrCreatePlayer();

        // check inputs
        if (r.inputs == null || r.inputs.isEmpty()) { toast("Recipe has no inputs"); return false; }
        for (RecipeIO in : r.inputs) {
            if (in == null || in.id == null || in.qty <= 0) continue;
            String needId = resolveItemAlias(in.id);
            int need = Math.max(1, in.qty);
            int have = pc.bag.getOrDefault(needId, 0);
            if (have < need) { toast("Need " + itemName(needId) + " ×" + need); return false; }
        }

        // consume
        for (RecipeIO in : r.inputs) {
            if (in == null || in.id == null || in.qty <= 0) continue;
            String needId = resolveItemAlias(in.id);
            int need = Math.max(1, in.qty);
            int have = pc.bag.getOrDefault(needId, 0);
            pc.bag.put(needId, Math.max(0, have - need));
            if (pc.bag.get(needId) != null && pc.bag.get(needId) <= 0) pc.bag.remove(needId);
        }

        // outputs
        int totalOut = 0;
        String toastLabel = (r.name != null ? r.name : r.id);
        if (r.outputs != null) {
            for (RecipeIO out : r.outputs) {
                if (out == null || out.id == null || out.qty <= 0) continue;
                pc.addItem(out.id, out.qty);
                totalOut += out.qty;
                toastLabel = itemName(out.id); // show actual cooked item
            }
        }

        // XP
        if (r.xp > 0 && r.skill != null) addSkillExp(r.skill, r.xp);

        save();
        if (totalOut > 0) toast("Cooked " + toastLabel + " ×" + totalOut);
        else toast("Crafted " + (r.name != null ? r.name : r.id));
        return true;
    }




    /* ============================
     * Slayer regions registry + JSON config
     * ============================ */

    // in-memory registry used by the game
    public static class SlayerRegion {
        public final String id;
        public final String label;
        public final List<String> monsterIds;
        public SlayerRegion(String id, String label, List<String> monsterIds) {
            this.id = id; this.label = label; this.monsterIds = monsterIds;
        }
    }
    private final Map<String, SlayerRegion> slayerRegions = new HashMap<>();

    public List<SlayerRegion> listSlayerRegions() {
        return new ArrayList<>(slayerRegions.values());
    }

    public void registerSlayerRegion(String id, String label, List<String> monsterIds) {
        if (id == null || label == null || monsterIds == null || monsterIds.isEmpty()) return;
        List<String> filtered = new ArrayList<>();
        for (String mid : monsterIds) if (monsters.containsKey(mid)) filtered.add(mid);
        if (filtered.isEmpty()) return;
        slayerRegions.put(id, new SlayerRegion(id, label, filtered));
    }

    // ---- JSON config model (assets/game/slayer.v1.json) ----
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
                    if (!filtered.isEmpty()) {
                        slayerRegions.put(r.id, new SlayerRegion(r.id, r.label, filtered));
                    }
                }
            }
        } catch (Exception ignored) {
            // If JSON missing, keep fallback created after monsters load.
        }

        // still ensure at least one region exists
        if (slayerRegions.isEmpty() && !monsters.isEmpty()) {
            slayerRegions.put("basecamp",
                    new SlayerRegion("basecamp", "Basecamp", new ArrayList<>(monsters.keySet())));
        }
    }

    // helpers to fetch effective costs & rules (region override > global > fallback)
    @Nullable private RegionCfg getRegionCfg(@Nullable String regionId) {
        if (regionId == null || slayerCfg == null || slayerCfg.regions == null) return null;
        for (RegionCfg r : slayerCfg.regions) if (r != null && regionId.equals(r.id)) return r;
        return null;
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
        int x = 0;
        int L = Math.max(1, lvl);
        for (int i = 1; i < L; i++) x += PlayerCharacter.xpForNextLevel(i);
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
                            }
                        }
                    }
                    // Re-parse using migrated JSON
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

                // load persisted Slayer assignment if any
                loadSlayerFromSpIfPresent();

                return player;
            }
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
        loadSlayerFromSpIfPresent();
        return player;
    }

    private void loadSlayerFromSpIfPresent() {
        if (slayerLive.getValue() != null) return;
        String s = sp.getString(KEY_SLAYER_JSON, null);
        if (s != null) {
            try {
                SlayerAssignment a = gson.fromJson(s, SlayerAssignment.class);
                publishSlayer(a);
            } catch (Exception ignored) {}
        }
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
     * Slayer API (assignment + JSON rules + coins)
     * ============================ */

    private void publishSlayer(@Nullable SlayerAssignment a) {
        slayerLive.postValue(a);
    }
    private void persistSlayerToSp(@Nullable SlayerAssignment a) {
        SharedPreferences.Editor ed = sp.edit();
        if (a == null) ed.remove(KEY_SLAYER_JSON);
        else ed.putString(KEY_SLAYER_JSON, gson.toJson(a));
        ed.apply();
    }
    public @Nullable SlayerAssignment getSlayerAssignment() {
        loadSlayerFromSpIfPresent();
        return slayerLive.getValue();
    }

    /** Assign a task (stores regionId) */
    public void assignSlayerTask(String regionId, String monsterId, int required, int completionBonus, @Nullable String label) {
        Monster m = getMonster(monsterId);
        String fallback = (m != null && m.name != null) ? m.name : monsterId;
        SlayerAssignment a = new SlayerAssignment(
                regionId,
                monsterId,
                (label != null ? label : fallback),
                Math.max(1, required),
                Math.max(0, completionBonus)
        );
        persistSlayerToSp(a);
        publishSlayer(a);
        toast("Slayer task: " + a.label + " ×" + a.required);
    }

    /** Back-compat overload (no region label) */
    public void assignSlayerTask(String monsterId, int required, int completionBonus) {
        assignSlayerTask("basecamp", monsterId, required, completionBonus, null);
    }

    public void clearSlayerTask() {
        persistSlayerToSp(null);
        publishSlayer(null);
    }

    /**
     * Called by CombatEngine when a monster dies:
     * - Increments progress if it matches the active task
     * - Grants per-kill Slayer coins (Monster.slayerReward) while task is active
     * - If finished: just toast; actual coin reward is granted when the player claims
     */
    /** Call this once per monster death. Increments Slayer task if it matches. */
    public void onMonsterKilled(@Nullable String monsterId) {
        if (monsterId == null) return;
        SlayerAssignment a = getSlayerAssignment();
        if (a == null || a.isComplete()) return;

        // compare case-insensitively to be robust
        if (!monsterId.equalsIgnoreCase(a.monsterId)) return;

        // Per-kill Slayer coins (only while on task)
        Monster m = getMonster(a.monsterId);
        if (m != null && m.slayerReward > 0) {
            addCurrency("slayer", m.slayerReward);
            toast("+" + m.slayerReward + " Slayer");
        }

        a.done = Math.max(0, a.done) + 1;
        persistSlayerToSp(a);
        publishSlayer(a);

        if (a.isComplete()) {
            toast("Task complete — claim your reward!");
        }
    }

    /** Convenience overload. */
    public void onMonsterKilled(@Nullable Monster m) {
        onMonsterKilled(m != null ? m.id : null);
    }

    // Simple “combat level” scaler (not OSRS-accurate; good enough for task sizing)
    public int getCombatLevel() {
        int atk = skillLevel(SkillId.ATTACK);
        int str = skillLevel(SkillId.STRENGTH);
        int def = skillLevel(SkillId.DEFENSE);
        int arch= skillLevel(SkillId.ARCHERY);
        int mag = skillLevel(SkillId.MAGIC);
        int hp  = skillLevel(SkillId.HP);
        int offensive = Math.max(Math.max(atk, str), Math.max(arch, mag));
        double cl = (offensive + def + (hp * 0.5));
        cl = cl / 2.0;
        int out = (int)Math.round(cl);
        if (out < 1) out = 1;
        if (out > 99) out = 99;
        return out;
    }

    /* ---- Public API used by the Slayer Master NPC / Basecamp UI ---- */

    /** Roll a task in the default region. */
    @Nullable public SlayerAssignment rollNewSlayerTask() { return rollNewSlayerTask("basecamp", false); }
    /** Roll a task in the default region, optionally forcing replace. */
    @Nullable public SlayerAssignment rollNewSlayerTask(boolean forceReplace) { return rollNewSlayerTask("basecamp", forceReplace); }

    /** Roll a task for a region. Guard only; real roll handled by the monster overload. */
    @Nullable
    public SlayerAssignment rollNewSlayerTask(String regionId, boolean forceReplace) {
        SlayerAssignment cur = getSlayerAssignment();
        boolean rerolling = (cur != null && !cur.isComplete());

        // If there's an active (not complete) task and we're not forcing replace, block.
        if (rerolling && !forceReplace) {
            toast("Finish or abandon your current task first.");
            return cur;
        }

        SlayerRegion reg = slayerRegions.get(regionId);
        if (reg == null || reg.monsterIds == null || reg.monsterIds.isEmpty()) {
            toast("No Slayer monsters in this region yet.");
            return cur;
        }

        // ✅ Only charge on reroll
        if (rerolling) {
            int rollCost = effectiveRollCost(regionId);
            if (rollCost > 0 && !spendCurrency("slayer", rollCost)) {
                toast("Need " + rollCost + " Slayer coins to reroll.");
                return cur;
            }
        }

        // Pick a random monster in the region
        String monsterId = reg.monsterIds.get(rng.nextInt(reg.monsterIds.size()));
        Monster m = getMonster(monsterId);

        int required = rollKillCountForLevel(getCombatLevel(), regionId);
        int completionBonus = computeCompletionBonus(required, regionId);
        String label = reg.label + " — " + (m != null && m.name != null ? m.name : monsterId);

        assignSlayerTask(regionId, monsterId, required, completionBonus, label);
        toast(rerolling ? "Task rerolled." : "Task assigned.");  // no charge on first assignment
        return slayerLive.getValue();
    }

    /** NEW: Roll a task for a region, optionally specifying the monster to slay. Charges roll cost. */
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

        // ✅ Only charge on reroll
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

    /** Abandon current task. Costs per-region Slayer coins from JSON (or fallback). */
    public boolean abandonSlayerTask() {
        SlayerAssignment a = getSlayerAssignment();
        if (a == null) { toast("No Slayer task to abandon."); return false; }

        int cost = effectiveAbandonCost(a.regionId);
        if (!spendCurrency("slayer", cost)) {
            toast("Need " + cost + " Slayer coins to abandon.");
            return false;
        }
        clearSlayerTask();
        toast("Task abandoned (−" + cost + " Slayer).");
        return true;
    }

    /** Claim reward if task complete. Pays the precomputed (or computed) bonus. */
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
            pc.base.speed += 0.05;
            didSomething = true;
            toast("Speed +0.05");
        }

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
        return k.equals("ATTACK") || k.equals("STRENGTH") || k.equals("DEFENSE") || k.equals("HP")
                || k.equals("ARCHERY") || k.equals("MAGIC");
    }

    private static @Nullable String canonicalItemId(@Nullable String id) {
        if (id == null) return null;
        String s = id.toLowerCase();
        // All these should count as the same thing:
        if (s.equals("raw_shrimp") || s.equals("shore_shrimp") || s.equals("shrimp_raw")) {
            return "fish_raw_shrimp";
        }
        return id;
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

    /** Give a gathered ITEM straight to the bag. */
    public void grantGatheredItem(String itemId, int qty) {
        if (itemId == null || qty <= 0) return;
        PlayerCharacter pc = loadOrCreatePlayer();
        pc.addItem(itemId, qty);
        save();
        // (intentionally no per-item toast here; ActionEngine aggregates a toast)
    }

    /** Give a gathered CURRENCY straight to balances and toast it. */
    public void grantGatheredCurrency(String code, long amount) {
        if (code == null || amount <= 0) return;
        addCurrency(code, amount);
        toast("+" + amount + " " + capitalize(code));
    }

    /** Decide by id format; supports "currency:xxx" or plain item id. */
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

    /** Sell qty back at 25% of list price if shop carries the item. */
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

    public String normalizeItemIdForBag(String id) {
        // same logic you used in craftOnce()
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
