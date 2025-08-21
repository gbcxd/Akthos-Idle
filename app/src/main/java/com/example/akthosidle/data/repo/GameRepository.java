package com.example.akthosidle.data.repo;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.widget.Toast;

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

    // Actions (progression content)
    private final Map<String, Action> actions = new HashMap<>();

    // Runtime save
    private PlayerCharacter player;

    // Live currency balances for top bar
    public final MutableLiveData<Map<String, Long>> currencyLive =
            new MutableLiveData<>(new HashMap<>());

    // Live HP for UI (e.g., health bar / label)
    public final MutableLiveData<Integer> playerHpLive = new MutableLiveData<>();

    public GameRepository(Context appContext) {
        this.app = appContext.getApplicationContext();
        this.sp = app.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
    }

    /* =========================================================
     * Load static definitions (items / monsters).
     * ========================================================= */
    public void loadDefinitions() {
        if (!items.isEmpty() || !monsters.isEmpty()) return;

        loadItemsFromAssets();     // game/items.v1.json
        loadMonstersFromAssets();  // game/monsters.v1.json
        loadActionsFromAssets();  // game/actions.v1.json

        // No hardcoded defaults here. If files are missing or empty,
        // the maps will remain empty and UI should handle that state gracefully.
    }

    /** Load Actions from assets; call once on startup (e.g., Application or first screen). */
    public void loadActionsFromAssets() {
        if (!actions.isEmpty()) return; // already loaded

        try (InputStream is = app.getAssets().open("game/actions.v1.json")) {
            String json = readStream(is); // works on API 24+
            Type t = new TypeToken<List<Action>>() {}.getType();
            List<Action> list = gson.fromJson(json, t);
            if (list != null) {
                for (Action a : list) {
                    if (a != null && a.id != null) {
                        actions.put(a.id, a);
                    }
                }
            }
        } catch (Exception e) {
            // Keep empty if file is missing/corrupt; UI should handle no actions gracefully.
            // Optionally log/Toast in debug builds.
            // toast("Actions file not found: game/actions.v1.json");
        }
    }

    /** Load Items from assets/game/items.v1.json (if present). */
    private void loadItemsFromAssets() {
        AssetManager am = app.getAssets();
        try (InputStream is = am.open("game/items.v1.json")) {
            String json = readStream(is);
            Type t = new TypeToken<List<Item>>() {}.getType();
            List<Item> list = gson.fromJson(json, t);
            if (list != null) {
                for (Item it : list) {
                    if (it != null && it.id != null) {
                        items.put(it.id, it);
                    }
                }
            }
        } catch (Exception e) {
            // Safe fallback: keep empty. Optionally log or toast once in debug.
            // toast("Items file not found (game/items.v1.json). Using empty items.");
        }
    }

    /** Load Monsters from assets/game/monsters.v1.json (if present). */
    private void loadMonstersFromAssets() {
        AssetManager am = app.getAssets();
        try (InputStream is = am.open("game/monsters.v1.json")) {
            String json = readStream(is);
            Type t = new TypeToken<List<Monster>>() {}.getType();
            List<Monster> list = gson.fromJson(json, t);
            if (list != null) {
                for (Monster m : list) {
                    if (m != null && m.id != null) {
                        monsters.put(m.id, m);
                    }
                }
            }
        } catch (Exception e) {
            // Safe fallback: keep empty. Optionally log or toast once in debug.
            // toast("Monsters file not found (game/monsters.v1.json). Using empty monsters.");
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
            publishHp(); // NEW: update HP observers
            return player;
        }

        // Create new player
        player = new PlayerCharacter();
        player.normalizeCurrencies();

        // Starter inventory
        addToBag("wpn_rusty_sword", 1);
        addToBag("helm_leather_cap", 1);
        addToBag("food_apple", 1000);
        addToBag("pot_basic_combat", 2);
        addToBag("pot_basic_noncombat", 2);
        addToBag("syrup_basic", 1000);
        addToBag("pot_heal_small", 1000);

        // First-time HP init to max
        if (player.currentHp == null) {
            int maxHp = totalStats().health;
            player.currentHp = maxHp;
        }

        save();
        publishCurrencies();
        publishHp(); // NEW
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

    /** Consume a potion. Heals immediately if it has a heal value, then decrements quantity. */
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

            if (healed > 0) {
                toast("+" + healed + " HP");
            } else {
                toast("HP already full");
            }
        }

        // Decrement inventory
        pc.bag.put(potionId, have - 1);
        if (pc.bag.get(potionId) != null && pc.bag.get(potionId) <= 0) pc.bag.remove(potionId);

        save();
        publishHp(); // notify UI about HP change
    }

    /** Consume "syrup": heal if it has a heal value, otherwise apply a small speed buff. */
    public void consumeSyrup() {
        PlayerCharacter pc = loadOrCreatePlayer();

        // find first syrup-like consumable in the bag
        String syrupId = null;
        for (String id : new HashSet<>(pc.bag.keySet())) {
            Item it = getItem(id);
            if (it != null && "CONSUMABLE".equals(it.type)) {
                String nid = it.id != null ? it.id.toLowerCase() : "";
                String nname = it.name != null ? it.name.toLowerCase() : "";
                if (nid.contains("syrup") || nname.contains("syrup")) {
                    syrupId = id; break;
                }
            }
        }
        if (syrupId == null) return;

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
            // fallback effect until timed buffs are implemented
            pc.base.speed += 0.05;
            didSomething = true;
            toast("Speed +0.05");
        }

        // decrement inventory only if we used/applied it
        if (didSomething) {
            Integer have = pc.bag.get(syrupId);
            if (have != null) {
                pc.bag.put(syrupId, have - 1);
                if (pc.bag.get(syrupId) != null && pc.bag.get(syrupId) <= 0) pc.bag.remove(syrupId);
            }
            save();
            publishHp(); // notify UI in case HP changed
        }
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

    // Publish balances to observers (MainActivity toolbar, etc.)
    private void publishCurrencies() {
        PlayerCharacter pc = loadOrCreatePlayer();
        currencyLive.postValue(new HashMap<>(pc.currencies));
    }

    // Publish HP to observers
    private void publishHp() {
        PlayerCharacter pc = loadOrCreatePlayer();
        playerHpLive.postValue(pc.currentHp);
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

    /** Backwards-compatible stream reader (API 24+). */
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
     * Toast Helper
     * ============================ */
    public void toast(String msg) {
        Toast.makeText(app, msg, Toast.LENGTH_SHORT).show();
    }

    public static class PendingLoot {
        public String id;        // e.g. "iron_ore" OR "currency:silver"
        public String name;      // display label, e.g. "Silver"
        public int quantity;
        public boolean isCurrency;
    }

    public void giveItem(String itemId, int qty) {
        if (itemId == null || qty == 0) return;
        PlayerCharacter pc = loadOrCreatePlayer();
        pc.addItem(itemId, qty);
        save();
        toast("Granted " + qty + "× " + itemName(itemId));
    }
}
