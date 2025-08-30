package com.obliviongatestudio.akthosidle.ui.currency;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.obliviongatestudio.akthosidle.R;
import com.obliviongatestudio.akthosidle.data.dtos.InventoryItem;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CurrencyAdapter extends RecyclerView.Adapter<CurrencyAdapter.VH> {

    private final List<InventoryItem> data = new ArrayList<>();
    private final NumberFormat nf = NumberFormat.getIntegerInstance(Locale.getDefault());

    public void submit(List<InventoryItem> items) {
        data.clear();
        if (items != null) data.addAll(items);
        notifyDataSetChanged();
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_currency, parent, false);
        return new VH(v);
    }

    @Override public void onBindViewHolder(@NonNull VH h, int pos) {
        InventoryItem it = data.get(pos);
        h.name.setText(it.name != null ? it.name : it.id);
        h.amount.setText(nf.format(it.quantity));
        int resId = resIdFromName(h.icon.getContext(), "ic_" + it.id);
        if (resId != 0) h.icon.setImageResource(resId);
    }

    @Override public int getItemCount() { return data.size(); }

    private static int resIdFromName(Context ctx, String name) {
        return ctx.getResources().getIdentifier(name, "drawable", ctx.getPackageName());
    }

    static class VH extends RecyclerView.ViewHolder {
        ImageView icon; TextView name; TextView amount;
        VH(@NonNull View v) {
            super(v);
            icon = v.findViewById(R.id.icon);
            name = v.findViewById(R.id.name);
            amount = v.findViewById(R.id.amount);
        }
    }
}
