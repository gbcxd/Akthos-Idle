package com.example.akthosidle.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.akthosidle.R;
import com.example.akthosidle.data.dtos.InventoryItem;

import java.util.ArrayList;
import java.util.List;

/** Simple grid adapter for potions. */
public class PotionAdapter extends RecyclerView.Adapter<PotionAdapter.VH> {

    public interface OnPotionClick {
        void onPotionClicked(InventoryItem item);
    }

    private final List<InventoryItem> items = new ArrayList<>();
    private final OnPotionClick cb;

    public PotionAdapter(OnPotionClick cb) {
        this.cb = cb;
    }

    public void submit(List<InventoryItem> data) {
        items.clear();
        if (data != null) items.addAll(data);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.row_potion, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        InventoryItem it = items.get(position);
        h.tvName.setText(it.name);
        h.tvQty.setText("x" + it.quantity);
        // Optional: set icon if available
        // h.img.setImageResource(...);
        h.itemView.setOnClickListener(_v -> cb.onPotionClicked(it));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvName, tvQty;
        ImageButton img;

        VH(@NonNull View v) {
            super(v);
            tvName = v.findViewById(R.id.tvName);
            tvQty  = v.findViewById(R.id.tvQty);
            img    = v.findViewById(R.id.img);
        }
    }
}
