package com.example.akthosidle.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.akthosidle.R;
import com.example.akthosidle.data.dtos.InventoryItem;
import com.example.akthosidle.domain.model.Item;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class LootAdapter extends RecyclerView.Adapter<LootAdapter.VH> {
    private final List<InventoryItem> data = new ArrayList<>();
    private final Map<String, Item> defs;

    public LootAdapter(Map<String, Item> itemDefs) {
        this.defs = itemDefs;
    }

    public void submit(List<InventoryItem> items) {
        data.clear();
        if (items != null) data.addAll(items);
        notifyDataSetChanged();
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.row_loot, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        InventoryItem it = data.get(pos);
        h.tvName.setText(it.name + " x" + it.quantity);
        h.img.setImageResource(R.drawable.ic_loot_bag);
    }

    @Override public int getItemCount() { return data.size(); }

    static class VH extends RecyclerView.ViewHolder {
        ImageView img; TextView tvName;
        VH(@NonNull View v) {
            super(v);
            img = v.findViewById(R.id.img);
            tvName = v.findViewById(R.id.tvName);
        }
    }
}
