package com.example.akthosidle.data.repo;

import android.content.Context;
import android.content.SharedPreferences;
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

    private static final String ASSET_ITEMS    = "game/items.v1.json";
    private static final String ASSET_ACTIONS  = "game/actions.v1.json";
    private static final String ASSET_MONSTERS = "game/monsters.v1.json";

    private final Context app;
    private final SharedPreferences sp;
    private final Gson gson = new Gson();

    // Definitions
    public final Map<String, Item> items = new HashMap<>();
    private final Map<String, Monster> monsters = new HashMap<>();
    private final Map<String, Action> actions = new HashMap<>();

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

    public GameRepository(Context appContext) {
        this.app = appContext.getApplicationContext();
        this.sp = app.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
    }

    /* =========================================================
     * Load static definitions (items / monsters / actions).
     * ========================================================= */
    public void loadDefinitions() {
        loadItemsFromAssets();
        loadMonstersFromAssets();
        loadActionsFromAssets();
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

                    // NEW: parse stats if present
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

                    // NEW: parse skillBuffs if present
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
    public PlayerCharacter loadOrCreatePlayer() {
        if (player != null) return player;

        String json = sp.getString(KEY_PLAYER, null);
        if (json != null) {
            Type t = new TypeToken<PlayerCharacter>() {}.getType();
            player = gson.fromJson(json, t);

            if (player.bag == null) player.bag = new HashMap<>();
            if (player.equipment == null) player.equipment = new EnumMap<>(EquipmentSlot.class);
            if (player.skills == null) player.skills = new EnumMap<>(SkillId.class);
            if (player.currencies == null) player.currencies = new HashMap<>();
            if (player.base == null) player.base = new Stats(12, 6, 0.0, 100, 0.05, 1.5);

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

        // new player
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
     * XP helpers
     * ============================ */
    public void addPlayerExp(int amount) {
        if (amount <= 0) return;
        PlayerCharacter pc = loadOrCreatePlayer();
        pc.exp += amount;
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
        if (leveled) toast(id.name().charAt(0) + id.name().substring(1).toLowerCase() +
                " Lv " + pc.getSkillLevel(id) + "!");
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
        toast("+" + qty + "× " + name);
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
     * Toast Helper
     * ============================ */
    public void toast(String msg) { Toast.makeText(app, msg, Toast.LENGTH_SHORT).show(); }

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
