package com.example.akthosidle.data;

import android.content.Context;
import android.content.SharedPreferences;

public class Prefs {
    private final SharedPreferences sp;
    public Prefs(Context ctx) { sp = ctx.getSharedPreferences("game", Context.MODE_PRIVATE); }
    public void putJson(String key, String json) { sp.edit().putString(key, json).apply(); }
    public String getJson(String key) { return sp.getString(key, null); }
}
