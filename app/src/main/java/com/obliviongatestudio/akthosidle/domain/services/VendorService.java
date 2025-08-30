package com.obliviongatestudio.akthosidle.domain.services;

import com.obliviongatestudio.akthosidle.domain.model.PlayerCharacter;
import com.obliviongatestudio.akthosidle.domain.model.ShopEntry;

/**
 * Basic vendor economy handling purchases and sales with silver/gold.
 */
public class VendorService {

    public boolean buy(PlayerCharacter pc, ShopEntry entry, int qty) {
        if (pc == null || entry == null || qty <= 0) return false;
        if (entry.stock != null && entry.stock < qty) return false;
        long silverCost = (long) (entry.priceSilver != null ? entry.priceSilver : 0) * qty;
        long goldCost = (long) (entry.priceGold != null ? entry.priceGold : 0) * qty;
        if (pc.getCurrency("silver") < silverCost) return false;
        if (pc.getCurrency("gold") < goldCost) return false;
        if (silverCost > 0 && !pc.spendCurrency("silver", silverCost)) return false;
        if (goldCost > 0 && !pc.spendCurrency("gold", goldCost)) return false;
        pc.addItem(entry.itemId, qty);
        if (entry.stock != null) entry.stock -= qty;
        return true;
    }

    public boolean sell(PlayerCharacter pc, String itemId, int qty, int priceSilver) {
        if (pc == null || itemId == null || qty <= 0 || priceSilver < 0) return false;
        int have = pc.bag.getOrDefault(itemId, 0);
        if (have < qty) return false;
        pc.addItem(itemId, -qty);
        pc.addCurrency("silver", (long) priceSilver * qty);
        return true;
    }
}
