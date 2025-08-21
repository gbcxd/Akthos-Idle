package com.example.akthosidle.ui;

import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.akthosidle.R;
import com.example.akthosidle.data.repo.GameRepository;
import com.example.akthosidle.domain.model.Item;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class InventoryAdapter extends RecyclerView.Adapter<InventoryAdapter.VH> {

    public interface OnItemClick { void onItemClick(String itemId); }

    /** Visible filter buckets. */
    public enum Filter { ALL, MATERIAL, FOOD, POTION, EQUIPMENT, OTHER }

    private final GameRepository repo;
    private final OnItemClick cb;

    private final List<String> allIds = new ArrayList<>();
    private final List<Integer> allQtys = new ArrayList<>();

    private final List<String> ids = new ArrayList<>();
    private final List<Integer> qtys = new ArrayList<>();

    private Filter filter = Filter.ALL;

    public InventoryAdapter(GameRepository repo, Map<String,Integer> bag, OnItemClick cb) {
        this.repo = repo; this.cb = cb; refresh(bag);
    }

    public void refresh(Map<String,Integer> bag) {
        allIds.clear(); allQtys.clear();
        if (bag != null) {
            for (Map.Entry<String,Integer> e : bag.entrySet()) {
                allIds.add(e.getKey());
                allQtys.add(e.getValue());
            }
        }
        applyFilter();
    }

    public void setFilter(Filter f) {
        this.filter = (f == null) ? Filter.ALL : f;
        applyFilter();
    }

    private void applyFilter() {
        ids.clear(); qtys.clear();
        for (int i = 0; i < allIds.size(); i++) {
            String id = allIds.get(i);
            int q = allQtys.get(i);
            Item it = repo.getItem(id);
            if (passes(it, filter)) {
                ids.add(id);
                qtys.add(q);
            }
        }
        notifyDataSetChanged();
    }

    private boolean passes(Item it, Filter f) {
        if (f == Filter.ALL) return true;
        if (it == null) return f == Filter.OTHER;

        final String type = (it.type == null) ? "" : it.type.toUpperCase();
        final boolean isFood = it.heal != null && it.heal > 0;
        final boolean isPotion = "CONSUMABLE".equals(type) && !isFood;
        final boolean isEquip = "EQUIPMENT".equals(type);
        final boolean isMaterial = TextUtils.isEmpty(type) ||
                "RESOURCE".equals(type) || "MATERIAL".equals(type) || "ORE".equals(type);

        switch (f) {
            case FOOD: return isFood;
            case POTION: return isPotion;
            case EQUIPMENT: return isEquip;
            case MATERIAL: return isMaterial && !isFood && !isPotion && !isEquip;
            case OTHER: default:
                return !(isFood || isPotion || isEquip || isMaterial);
        }
    }

    @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.row_inventory, parent, false);
        return new VH(v);
    }

    @Override public void onBindViewHolder(@NonNull VH h, int pos) {
        String id = ids.get(pos); int q = qtys.get(pos);
        Item it = repo.getItem(id);

        // name & quantity (safe fallbacks)
        h.name.setText(it != null && it.name != null ? it.name : id);
        h.qty.setText("x" + q);

        // icon resolution (safe fallback)
        Context ctx = h.itemView.getContext();
        String iconName = (it != null ? it.icon : null);
        int resId = 0;
        if (iconName != null && !iconName.isEmpty()) {
            resId = ctx.getResources().getIdentifier(iconName, "drawable", ctx.getPackageName());
        }
        if (resId == 0) resId = R.drawable.ic_bag; // fallback
        h.icon.setImageResource(resId);

        h.itemView.setOnClickListener(v -> {
            if (cb != null) cb.onItemClick(id);
        });
    }

    @Override public int getItemCount() { return ids.size(); }

    static class VH extends RecyclerView.ViewHolder {
        ImageView icon; TextView name; TextView qty;
        VH(View v) {
            super(v);
            icon = v.findViewById(R.id.icon);
            name = v.findViewById(R.id.name);
            qty  = v.findViewById(R.id.qty);
        }
    }
}
