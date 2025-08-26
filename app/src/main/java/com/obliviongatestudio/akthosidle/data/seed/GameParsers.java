package com.obliviongatestudio.akthosidle.data.seed;

import com.obliviongatestudio.akthosidle.domain.model.Drop;
import com.obliviongatestudio.akthosidle.domain.model.Monster;
import com.obliviongatestudio.akthosidle.domain.model.Stats;
import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/** Parses assets JSON and maps to domain models. */
public final class GameParsers {
    private GameParsers() {}
    private static final Gson GSON = new Gson();

    // ---------- DTOs that mirror your assets/game/*.vN.json ----------
    public static final class StatsDto {
        public int health;
        public int attack;
        public int defense;
        public double speed;          // 0..1
        @SerializedName("crit_chance") public double critChance;      // 0..1
        @SerializedName("crit_multiplier") public double critMultiplier; // >= 1.0
    }

    public static final class DropDto {
        @SerializedName("item_id") public String itemId;
        public double chance; // 0..1
        public int min;
        public int max;
    }

    public static final class MonsterDto {
        public String id;
        public String name;
        public StatsDto stats;
        @SerializedName("exp")  public int expReward;
        @SerializedName("gold") public int goldReward;
        public List<DropDto> drops;
    }

    // ---------- JSON ➜ DTO lists ----------
    public static List<MonsterDto> parseMonstersDto(String json) {
        Type t = new TypeToken<List<MonsterDto>>(){}.getType();
        return GSON.fromJson(json, t);
    }

    // ---------- DTO ➜ DOMAIN ----------
    public static Stats toDomain(StatsDto s) {
        if (s == null) return new Stats();
        Stats d = new Stats();
        d.health = s.health;
        d.attack = s.attack;
        d.defense = s.defense;
        d.speed = s.speed;
        d.critChance = s.critChance;
        d.critMultiplier = s.critMultiplier;
        return d;
    }

    public static Drop toDomain(DropDto d) {
        if (d == null) return null;
        Drop x = new Drop();
        x.itemId = d.itemId;
        x.chance = d.chance;
        x.min = d.min;
        x.max = d.max;
        return x;
    }

    public static Monster toDomain(MonsterDto m) {
        if (m == null) return null;
        Monster x = new Monster();
        x.id = m.id;
        x.name = m.name;
        x.stats = toDomain(m.stats);
        x.expReward = m.expReward;
        x.goldReward = m.goldReward;
        if (m.drops != null) {
            x.drops = new ArrayList<>();
            for (DropDto d : m.drops) {
                Drop conv = toDomain(d);
                if (conv != null) x.drops.add(conv);
            }
        }
        return x;
    }

    public static List<Monster> toDomainMonsters(List<MonsterDto> list) {
        List<Monster> out = new ArrayList<>();
        if (list != null) for (MonsterDto m : list) {
            Monster conv = toDomain(m);
            if (conv != null) out.add(conv);
        }
        return out;
    }
}
