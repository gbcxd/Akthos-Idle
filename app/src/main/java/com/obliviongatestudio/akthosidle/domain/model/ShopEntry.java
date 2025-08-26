package com.obliviongatestudio.akthosidle.domain.model;

public class ShopEntry {
    public String id;        // unique row id
    public String itemId;    // item sold
    public String name;      // optional display override
    public Integer priceGold;
    public Integer priceSilver;
    public Integer stock;    // null = infinite
}
