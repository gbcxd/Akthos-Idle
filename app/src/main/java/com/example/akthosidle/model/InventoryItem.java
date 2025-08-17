package com.example.akthosidle.model;

/** Lightweight DTO for listing inventory items in UI. */
public class InventoryItem {
    public String id;
    public String name;
    public int quantity;

    public InventoryItem() {}

    public InventoryItem(String id, String name, int qty) {
        this.id = id;
        this.name = name;
        this.quantity = qty;
    }
}
