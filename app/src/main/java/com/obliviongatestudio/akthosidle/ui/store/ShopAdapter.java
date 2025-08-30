package com.obliviongatestudio.akthosidle.ui.store;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.obliviongatestudio.akthosidle.R;
import com.obliviongatestudio.akthosidle.data.repo.GameRepository;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Adapter displaying shop items with buy/sell buttons. */
public class ShopAdapter extends RecyclerView.Adapter<ShopAdapter.VH> {

    private final GameRepository repo;
    private final List<GameRepository.ShopRow> data = new ArrayList<>();
    private final NumberFormat nf = NumberFormat.getIntegerInstance(Locale.getDefault());

    public ShopAdapter(GameRepository repo) {
        this.repo = repo;
    }

    /** Reload rows from repository and refresh UI. */
    public void refresh() {
        data.clear();
        data.addAll(repo.getShopRows());
        notifyDataSetChanged();
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.row_shop, parent, false);
        return new VH(v);
    }

    @Override public void onBindViewHolder(@NonNull VH h, int pos) {
        GameRepository.ShopRow row = data.get(pos);
        h.name.setText(row.name != null ? row.name : row.itemId);

        StringBuilder sb = new StringBuilder();
        if (row.priceGold > 0) sb.append(nf.format(row.priceGold)).append("g");
        if (row.priceSilver > 0) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(nf.format(row.priceSilver)).append("s");
        }
        if (sb.length() == 0) sb.append("Free");
        h.price.setText(sb.toString());

        h.owned.setText("Owned: " + nf.format(row.ownedQty));

        h.btnBuy.setOnClickListener(v -> {
            if (repo.buyItem(row.itemId, 1)) refresh();
        });
        h.btnSell.setOnClickListener(v -> {
            if (repo.sellItem(row.itemId, 1)) refresh();
        });
    }

    @Override public int getItemCount() { return data.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView name, price, owned;
        Button btnBuy, btnSell;
        VH(View v) {
            super(v);
            name = v.findViewById(R.id.name);
            price = v.findViewById(R.id.price);
            owned = v.findViewById(R.id.owned);
            btnBuy = v.findViewById(R.id.btn_buy);
            btnSell = v.findViewById(R.id.btn_sell);
        }
    }
}
