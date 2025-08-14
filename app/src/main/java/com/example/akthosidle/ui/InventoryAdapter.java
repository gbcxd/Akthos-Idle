package com.example.akthosidle.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.akthosidle.R;
import com.example.akthosidle.data.GameRepository;
import com.example.akthosidle.model.Item;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class InventoryAdapter extends RecyclerView.Adapter<InventoryAdapter.VH> {
    public interface OnItemClick { void onItemClick(String itemId); }

    private final GameRepository repo;
    private final OnItemClick cb;
    private final List<String> ids = new ArrayList<>();
    private final List<Integer> qtys = new ArrayList<>();

    public InventoryAdapter(GameRepository repo, Map<String,Integer> bag, OnItemClick cb) {
        this.repo = repo; this.cb = cb; refresh(bag);
    }

    public void refresh(Map<String,Integer> bag) {
        ids.clear(); qtys.clear();
        for (Map.Entry<String,Integer> e : bag.entrySet()) { ids.add(e.getKey()); qtys.add(e.getValue()); }
        notifyDataSetChanged();
    }

    @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.row_inventory, parent, false);
        return new VH(v);
    }

    @Override public void onBindViewHolder(@NonNull VH h, int pos) {
        String id = ids.get(pos); int q = qtys.get(pos);
        Item it = repo.getItem(id);
        h.name.setText(it.name);
        h.qty.setText("x" + q);

        Context ctx = h.itemView.getContext();
        int resId = ctx.getResources().getIdentifier(it.icon, "drawable", ctx.getPackageName());
        h.icon.setImageResource(resId != 0 ? resId : R.drawable.ic_bag);

        h.itemView.setOnClickListener(v -> cb.onItemClick(id));
    }

    @Override public int getItemCount() { return ids.size(); }

    static class VH extends RecyclerView.ViewHolder {
        ImageView icon; TextView name; TextView qty;
        VH(View v) { super(v); icon = v.findViewById(R.id.icon); name = v.findViewById(R.id.name); qty = v.findViewById(R.id.qty); }
    }
}
