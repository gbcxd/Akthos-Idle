package com.obliviongatestudio.akthosidle.domain.model;

import android.graphics.Color; // Import Android's Color class
import androidx.annotation.ColorInt; // For better type safety

/**
 * Basic runtime status effect used by the {@link com.obliviongatestudio.akthosidle.engine.CombatEngine}.
 */
public class StatusEffect {

    public static enum Type {
        DOT, // Damage Over Time (e.g., Burn, Poison)
        HOT, // Heal Over Time
        STUN,
        SLOW
        // You can add more types like BLEED, FREEZE, etc.
    }

    public final Type type;
    public double remaining;
    public double value;
    public double tickAcc;
    public String name; // Specific name like "Burn", "Minor Heal", "Deep Wound"

    @ColorInt // Annotation to indicate this is an Android color integer
    public final int color;

    // Constructor updated to accept a color
    public StatusEffect(String name, Type type, double durationSec, double value, @ColorInt int color) {
        this.name = name;
        this.type = type;
        this.remaining = durationSec;
        this.value = value;
        this.color = color; // Assign the color
        this.tickAcc = 0.0;
    }

    // Overloaded constructor for effects where the color can be derived from the type or name
    // Or you can have a dedicated method to assign color based on name/type
    public StatusEffect(String name, Type type, double durationSec, double value) {
        this.name = name;
        this.type = type;
        this.remaining = durationSec;
        this.value = value;
        this.tickAcc = 0.0;
        this.color = getDefaultColorForEffect(name, type); // Assign a default color
    }


    public StatusEffect copy() {
        // Ensure the color and name are also copied
        StatusEffect c = new StatusEffect(this.name, this.type, this.remaining, this.value, this.color);
        c.tickAcc = this.tickAcc;
        return c;
    }

    public String getName() {
        return this.name;
    }

    @ColorInt
    public int getColor() {
        return this.color;
    }

    public String getTypeName() {
        return this.type.name();
    }

    // Helper method to determine default color based on name or type
    @ColorInt
    private static int getDefaultColorForEffect(String name, Type type) {
        String lowerName = name.toLowerCase();
        if (lowerName.contains("burn")) {
            return Color.RED;
        } else if (lowerName.contains("poison")) {
            return Color.GREEN;
        } else if (lowerName.contains("bleed")) {
            return Color.parseColor("#8B0000"); // Dark Red
        } else if (lowerName.contains("freeze") || lowerName.contains("chill")) {
            return Color.CYAN;
        } else if (type == Type.HOT || lowerName.contains("regen") || lowerName.contains("heal")) {
            return Color.parseColor("#90EE90"); // Light Green
        } else if (type == Type.STUN) {
            return Color.YELLOW;
        } else if (type == Type.SLOW) {
            return Color.LTGRAY; // Light Gray
        }
        // Default color if no specific match
        return Color.WHITE;
    }
}
