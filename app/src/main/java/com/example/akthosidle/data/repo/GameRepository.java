package com.example.akthosidle.data.repo;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.AssetManager;

import androidx.annotation.Nullable;
import androidx.lifecycle.MutableLiveData;

import com.example.akthosidle.data.dtos.InventoryItem;
import com.example.akthosidle.domain.model.Action;
import com.example.akthosidle.domain.model.Drop;
import com.example.akthosidle.domain.model.EquipmentSlot;
import com.example.akthosidle.domain.model.Item;
import com.example.akthosidle.domain.model.Monster;
import com.example.akthosidle.domain.model.PlayerCharacter;
import com.example.akthosidle.domain.model.SkillId;
import com.example.akthosidle.domain.model.Stats;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

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

    private final Context app;
    private final SharedPreferences sp;
    private final Gson gson = new Gson();

    // Definitions
    public final Map<String, Item> items = new HashMap<>();
    private final Map<String, Monster> monsters = new HashMap<>();

    // NEW: Actions (progression content)
    private final Map<String, Action> actions = new HashMap<>();

    // Runtime save
    private PlayerCharacter player;

    // Live currency balances for top bar
    public final MutableLiveData<Map<String, Long>> currencyLive =
            new MutableLiveData<>(new HashMap<>());

    public GameRepository(Context appContext) {
        this.app = appContext.getApplicationContext();
        this.sp = app.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
    }

    /* =========================================================
     * Load static definitions (items / monsters / actions).
     * ========================================================= */
    public void loadDefinitions() {
        if (!items.isEmpty() || !monsters.isEmpty()) return;

        // ---------- Items ----------
        Item rustySword = new Item();
        rustySword.id = "wpn_rusty_sword";
        rustySword.name = "Rusty Sword";
        rustySword.icon = "ic_sword";
        rustySword.type = "EQUIPMENT";
        rustySword.slot = "WEAPON";
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

        Item apple = new Item();
        apple.id = "food_apple";
        apple.name = "Apple";
        apple.icon = "ic_food_apple";
        apple.type = "CONSUMABLE";
        apple.rarity = "COMMON";
        apple.heal = 10;
        items.put(apple.id, apple);

        Item warDraught = new Item();
        warDraught.id = "pot_basic_combat";
        warDraught.name = "War Draught";
        warDraught.icon = "ic_potion_red";
        warDraught.type = "CONSUMABLE";
        warDraught.rarity = "UNCOMMON";
        warDraught.stats = new Stats(2, 0, 0.0, 0, 0.05, 0.0);
        items.put(warDraught.id, warDraught);

        Item focusTonic = new Item();
        focusTonic.id = "pot_basic_noncombat";
        focusTonic.name = "Focus Tonic";
        focusTonic.icon = "ic_potion_blue";
        focusTonic.type = "CONSUMABLE";
        focusTonic.rarity = "UNCOMMON";
        focusTonic.skillBuffs = new HashMap<>();
        focusTonic.skillBuffs.put("MINING", 5);
        items.put(focusTonic.id, focusTonic);

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
        thief.expReward = 20;
        thief.goldReward = 0;
        thief.silverReward = 12;    // soft currency
        thief.slayerReward = 0;     // slayer coins
        thief.drops = new ArrayList<>();
        thief.drops.add(new Drop("food_apple", 1, 3, 0.5));
        monsters.put(thief.id, thief);
    }

    /** Load Actions from assets; call once on startup (e.g., Application or first screen). */
    public void loadActionsFromAssets() {
        if (!actions.isEmpty()) return;
        AssetManager am = app.getAssets();
        try (InputStream is = am.open("game/actions.v1.json")) {
            String json = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            Type t = new TypeToken<List<Action>>() {}.getType();
            List<Action> list = gson.fromJson(json, t);
            if (list != null) {
                for (Action a : list) {
                    if (a != null && a.id != null) actions.put(a.id, a);
                }
            }
        } catch (Exception e) {
            // Fallback: a couple of hardcoded actions so UI can run without assets
            Action mineCopper = new Action();
            mineCopper.id = "mine_copper";
            mineCopper.name = "Mine Copper";
            mineCopper.skill = SkillId.MINING;
            mineCopper.durationMs = 3000;
            mineCopper.exp = 8;
            mineCopper.outputs = new HashMap<>();
            mineCopper.outputs.put("ore_copper", 1);
            mineCopper.reqLevel = 1;
            actions.put(mineCopper.id, mineCopper);

            Action fishShrimp = new Action();
            fishShrimp.id = "fish_shrimp";
            fishShrimp.name = "Fish Shrimp";
            fishShrimp.skill = SkillId.FISHING;
            fishShrimp.durationMs = 3000;
            fishShrimp.exp = 7;
            fishShrimp.outputs = new HashMap<>();
            fishShrimp.outputs.put("fish_shrimp", 1);
            fishShrimp.reqLevel = 1;
            actions.put(fishShrimp.id, fishShrimp);
        }
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
            if (player.currencies == null) player.currencies = new HashMap<>();
            player.normalizeCurrencies();
            if (player.currentHp == null) {
                int maxHp = totalStats().health;
                player.currentHp = maxHp;
                save();
            }
            publishCurrencies();
            return player;
        }

        // Create new player
        player = new PlayerCharacter();
        player.normalizeCurrencies();

        // Starter inventory
        addToBag("wpn_rusty_sword", 1);
        addToBag("helm_leather_cap", 1);
        addToBag("food_apple", 5);
        addToBag("pot_basic_combat", 2);
        addToBag("pot_basic_noncombat", 2);
        addToBag("syrup_basic", 1);

        // First-time HP init to max
        if (player.currentHp == null) {
            int maxHp = totalStats().health;
            player.currentHp = maxHp;
        }

        save();
        publishCurrencies();
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

    /** Filter actions by skill for the Skill Detail screen. */
    public List<Action> getActionsBySkill(SkillId skill) {
        List<Action> out = new ArrayList<>();
        for (Action a : actions.values()) {
            if (a != null && a.skill == skill) out.add(a);
        }
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

    public void consumePotion(String potionId) {
        PlayerCharacter pc = loadOrCreatePlayer();
        Integer have = pc.bag.get(potionId);
        if (have == null || have <= 0) return;

        Item it = getItem(potionId);
        if (it == null || !isPotion(it)) return;

        if (it.heal != null && it.heal > 0) {
            int maxHp = totalStats().health;
            int cur = pc.currentHp == null ? maxHp : pc.currentHp;
            pc.currentHp = Math.min(maxHp, Math.max(0, cur) + it.heal);
        }

        pc.bag.put(potionId, have - 1);
        if (pc.bag.get(potionId) != null && pc.bag.get(potionId) <= 0) pc.bag.remove(potionId);

        save();
    }

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
            pc.base.speed += 0.05; // placeholder buff
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
                && !isFood(it);
    }

    private boolean isCombatPotion(Item it) {
        if (it == null) return false;
        if (it.stats != null) return true;
        if (it.skillBuffs != null) {
            for (String key : it.skillBuffs.keySet()) {
                if (isCombatSkill(key)) return true;
            }
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
     * Pending loot (buffer + LiveData)
     * ============================ */

    // Internal buffer of pending loot (supports items & currencies)
    private final List<PendingLoot> pendingLoot = new ArrayList<>();

    // Live list for UI that shows items only (non-currency) as InventoryItem
    public final MutableLiveData<List<InventoryItem>> pendingLootLive =
            new MutableLiveData<>(new ArrayList<>());

    public void addPendingCurrency(String code, String name, int qty) {
        if (qty <= 0) return;
        String id = "currency:" + code; // e.g., currency:silver
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
        pl.id = id;
        pl.name = name != null ? name : code;
        pl.quantity = qty;
        pl.isCurrency = true;
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
        pl.id = itemId;
        pl.name = (name != null ? name : itemId);
        pl.quantity = qty;
        pl.isCurrency = false;
        pendingLoot.add(pl);
        updatePendingLootLive();
        save();
    }

    private void updatePendingLootLive() {
        List<InventoryItem> view = new ArrayList<>();
        for (PendingLoot pl : pendingLoot) {
            if (!pl.isCurrency) {
                view.add(new InventoryItem(pl.id, pl.name, pl.quantity));
            }
        }
        pendingLootLive.postValue(view);
    }

    public List<PendingLoot> getPendingLoot() {
        return new ArrayList<>(pendingLoot);
    }

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
        List<InventoryItem> cur = new ArrayList<>(pendingLootLive.getValue());
        for (InventoryItem it : cur) {
            pc.addItem(it.id, it.quantity);
        }
        pendingLoot.removeIf(pl -> !pl.isCurrency);
        updatePendingLootLive();
        save();
        publishCurrencies();
    }

    /* ============================
     * Currency convenience API
     * ============================ */
    public long getCurrency(String id) {
        return loadOrCreatePlayer().getCurrency(id);
    }

    public void addCurrency(String id, long amount) {
        PlayerCharacter pc = loadOrCreatePlayer();
        pc.addCurrency(id, amount);
        save();
        publishCurrencies();
    }

    public boolean spendCurrency(String id, long amount) {
        PlayerCharacter pc = loadOrCreatePlayer();
        boolean ok = pc.spendCurrency(id, amount);
        if (ok) {
            save();
            publishCurrencies();
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

    public MutableLiveData<Map<String, Long>> currenciesLive() { return currencyLive; }

    /* ============================
     * Offline progress timestamps
     * ============================ */
    public void setLastSeen(long ms) { sp.edit().putLong(KEY_LAST_SEEN, ms).apply(); }
    public long getLastSeen() { return sp.getLong(KEY_LAST_SEEN, System.currentTimeMillis()); }

    /* ============================
     * Utils
     * ============================ */
    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    public static class PendingLoot {
        public String id;        // e.g. "iron_ore" OR "currency:silver"
        public String name;      // display label, e.g. "Silver"
        public int quantity;
        public boolean isCurrency;
    }
}
