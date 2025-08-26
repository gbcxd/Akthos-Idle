package com.obliviongatestudio.akthosidle.domain.model;

public class Currency {
    public String id;        // "silver", "gold", "slayer", "craft_marks", ...
    public String name;      // "Silver", "Gold", ...
    public String icon;      // optional: "ic_silver"
    public boolean premium;  // true for Gold (IAP later), false otherwise

    public Currency() {}
    public Currency(String id, String name, String icon, boolean premium) {
        this.id = id; this.name = name; this.icon = icon; this.premium = premium;
    }
}
