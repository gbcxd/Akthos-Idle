package com.obliviongatestudio.akthosidle.domain.services;

import static org.junit.Assert.*;

import com.obliviongatestudio.akthosidle.domain.model.PlayerCharacter;
import com.obliviongatestudio.akthosidle.domain.model.ShopEntry;

import org.junit.Test;

public class VendorServiceTest {
    @Test
    public void buyDeductsCurrencyAndStock() {
        PlayerCharacter pc = new PlayerCharacter();
        pc.normalizeCurrencies();
        pc.addCurrency("silver", 100);

        ShopEntry e = new ShopEntry();
        e.itemId = "apple";
        e.priceSilver = 10;
        e.stock = 5;

        VendorService svc = new VendorService();
        assertTrue(svc.buy(pc, e, 2));
        assertEquals(80L, pc.getCurrency("silver"));
        assertEquals(2, pc.bag.getOrDefault("apple", 0).intValue());
        assertEquals(Integer.valueOf(3), e.stock);
    }

    @Test
    public void sellAddsCurrency() {
        PlayerCharacter pc = new PlayerCharacter();
        pc.normalizeCurrencies();
        pc.addItem("apple", 2);

        VendorService svc = new VendorService();
        assertTrue(svc.sell(pc, "apple", 1, 5));
        assertEquals(1, pc.bag.getOrDefault("apple", 0).intValue());
        assertEquals(5L, pc.getCurrency("silver"));
    }
}
