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

    private final GameRepository repo;
    private final OnItemClick cb;
    private final List<String> ids = new ArrayList<>();
    private final List<Integer> qtys = new ArrayList<>();

    public InventoryAdapter(@NonNull GameRepository repo,
                            @NonNull Map<String, Integer> bag,
                            @NonNull OnItemClick cb) {
        this.repo = repo;
        this.cb = cb;
        refresh(bag);
        setHasStableIds(true);
    }

    public void refresh(@NonNull Map<String, Integer> bag) {
        ids.clear();
        qtys.clear();
        for (Map.Entry<String, Integer> e : bag.entrySet()) {
            if (e.getKey() == null) continue;
            ids.add(e.getKey());
            qtys.add(e.getValue() == null ? 0 : e.getValue());
        }
        notifyDataSetChanged();
    }

    @Override
    public long getItemId(int position) {
        // Helps RecyclerView animations; hash of ID string is fine here
        return ids.get(position).hashCode();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.row_inventory, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        String id = ids.get(pos);
        int q = qtys.get(pos);

        Item it = repo.getItem(id);

        // --- title & qty (null-safe) ---
        String title = (it != null && !TextUtils.isEmpty(it.name))
                ? it.name
                : (id != null ? id : h.itemView.getContext().getString(R.string.app_name)); // fallback
        h.name.setText(title);
        h.qty.setText("x" + Math.max(0, q));

        // --- icon resolve (null-safe with fallbacks) ---
        Context ctx = h.itemView.getContext();
        String iconName = (it != null) ? it.icon : null;
        int iconRes = resolveIcon(ctx, iconName, id);
        h.icon.setImageResource(iconRes);

        // --- click ---
        h.itemView.setOnClickListener(v -> {
            if (cb != null && id != null) cb.onItemClick(id);
        });
    }

    @Override
    public int getItemCount() { return ids.size(); }

    static class VH extends RecyclerView.ViewHolder {
        final ImageView icon;
        final TextView name;
        final TextView qty;
        VH(@NonNull View v) {
            super(v);
            icon = v.findViewById(R.id.icon);
            name = v.findViewById(R.id.name);
            qty  = v.findViewById(R.id.qty);
        }
    }

    /**
     * Resolve a drawable resource id from an optional icon name.
     * Falls back to a convention guess ("ic_" + sanitizedId) and finally to a placeholder.
     */
    private int resolveIcon(@NonNull Context ctx, String iconName, String fallbackId) {
        // 1) if JSON provided an explicit icon name like "ic_ore_copper"
        if (!TextUtils.isEmpty(iconName)) {
            int id = ctx.getResources().getIdentifier(iconName.trim(), "drawable", ctx.getPackageName());
            if (id != 0) return id;
        }

        // 2) try a convention-based name derived from item id (e.g., "ic_pot_heal_small")
        if (!TextUtils.isEmpty(fallbackId)) {
            String sanitized = fallbackId.toLowerCase().replaceAll("[^a-z0-9_]", "_");
            String guess = "ic_" + sanitized;
            int id = ctx.getResources().getIdentifier(guess, "drawable", ctx.getPackageName());
            if (id != 0) return id;
        }

        // 3) final fallback placeholder
        // Use whatever you already have — keeping ic_bag since it exists in your project.
        return R.drawable.ic_bag;
    }
}
