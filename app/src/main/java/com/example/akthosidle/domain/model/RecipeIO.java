package com.example.akthosidle.domain.model;

/** Simple pair for recipe inputs/outputs (id + qty). */
public class RecipeIO {
    public String id;
    public int qty;

    public RecipeIO() {}
    public RecipeIO(String id, int qty) { this.id = id; this.qty = qty; }
}
