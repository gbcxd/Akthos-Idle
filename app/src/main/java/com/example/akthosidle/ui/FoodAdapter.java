package com.example.akthosidle.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.akthosidle.R;
import com.example.akthosidle.data.dtos.InventoryItem;

import java.util.List;

public class FoodAdapter extends RecyclerView.Adapter<FoodAdapter.ViewHolder> {

    public interface OnFoodClick {
        void onFoodSelected(InventoryItem item);
    }

    private final List<InventoryItem> foods;
    private final OnFoodClick listener;

    public FoodAdapter(List<InventoryItem> foods, OnFoodClick listener) {
        this.foods = foods;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.list_item_food, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        InventoryItem item = foods.get(position);
        holder.tvName.setText(item.name + " x" + item.quantity);

        holder.itemView.setOnClickListener(v -> listener.onFoodSelected(item));
    }

    @Override
    public int getItemCount() {
        return foods.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvName;
        ViewHolder(View v) {
            super(v);
            tvName = v.findViewById(R.id.tvName);
        }
    }
}
