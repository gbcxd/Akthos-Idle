package com.example.akthosidle.ui;

import android.content.Context;
import android.view.*;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.akthosidle.R;
import com.example.akthosidle.data.GameRepository;
import com.example.akthosidle.model.Item;

import java.util.*;

public class InventoryAdapter extends RecyclerView.Adapter<InventoryAdapter.VH> {
    public interface OnItemClick { void onItemClick(String itemId); }

    private final GameRepository repo;
    private final OnItemClick cb;
    private final List<String> itemIds = new ArrayList<>();
    private final List<Integer> qtys = new ArrayList<>();

    public InventoryAdapter(GameRepository repo, Map<String,Integer> bag, OnItemClick cb) {
        this.repo = repo; this.cb = cb; refresh(bag);
    }

    public void refresh(Map<String,Integer> bag) {
        itemIds.clear(); qtys.clear();
        for (Map.Entry<String,Integer> e : bag.entrySet()) { itemIds.add(e.getKey()); qtys.add(e.getValue()); }
        notifyDataSetChanged();
    }

    @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(LayoutInflater.from(parent.getContext()).inflate(R.layout.row_inventory, parent, false));
    }

    @Override public void onBindViewHolder(@NonNull VH h, int pos) {
        String id = itemIds.get(pos); int q = qtys.get(pos);
        Item it = repo.getItem(id);
        h.name.setText(it.name);
        h.qty.setText("x" + q);
        Context ctx = h.itemView.getContext();
        int resId = ctx.getResources().getIdentifier(it.icon, "drawable", ctx.getPackageName());
        if (resId != 0) h.icon.setImageResource(resId); else h.icon.setImageResource(R.drawable.ic_bag);
        h.itemView.setOnClickListener(v -> cb.onItemClick(id));
    }

    @Override public int getItemCount() { return itemIds.size(); }

    static class VH extends RecyclerView.ViewHolder {
        ImageView icon; TextView name; TextView qty;
        VH(View v) { super(v); icon = v.findViewById(R.id.icon); name = v.findViewById(R.id.name); qty = v.findViewById(R.id.qty); }
    }
}
