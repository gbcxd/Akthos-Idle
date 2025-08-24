package com.example.akthosidle.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.NavDestination;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.akthosidle.R;

import java.util.ArrayList;
import java.util.List;

/** Home / Basecamp hub with 3-column tiles for non-core menus. */
public class basecampFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        return inflater.inflate(R.layout.fragment_basecamp, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle s) {
        super.onViewCreated(v, s);
        RecyclerView rv = v.findViewById(R.id.recyclerBasecamp);
        rv.setLayoutManager(new GridLayoutManager(requireContext(), 3));
        rv.setAdapter(new TilesAdapter(buildTiles(), NavHostFragment.findNavController(this)));
    }

    private List<Tile> buildTiles() {
        List<Tile> list = new ArrayList<>();

        list.add(new Tile("Shop",         android.R.drawable.ic_menu_view,         R.id.shopFragment));
        list.add(new Tile("Quests",       android.R.drawable.ic_menu_agenda,       R.id.questsFragment));
        list.add(new Tile("Achievements", android.R.drawable.ic_menu_myplaces,     R.id.achievementsFragment));
        list.add(new Tile("Bank",         android.R.drawable.ic_menu_slideshow,    R.id.bankFragment));
        list.add(new Tile("Forge",        android.R.drawable.ic_menu_manage,       R.id.forgeFragment));
        list.add(new Tile("Options",      android.R.drawable.ic_menu_preferences,  R.id.optionsFragment));
        list.add(new Tile("About",        android.R.drawable.ic_menu_info_details, R.id.aboutFragment));

        return list;
    }

    // ----- adapter -----
    private static class Tile {
        final String label; @DrawableRes final int icon; final int destId;
        Tile(String label, int icon, int destId) { this.label = label; this.icon = icon; this.destId = destId; }
    }

    private static class TilesAdapter extends RecyclerView.Adapter<TilesAdapter.VH> {
        private final List<Tile> data; private final NavController nav;

        TilesAdapter(List<Tile> data, NavController nav) { this.data = data; this.nav = nav; }

        static class VH extends RecyclerView.ViewHolder {
            final View card; final ImageView icon; final TextView label;
            VH(@NonNull View v) {
                super(v);
                card = v.findViewById(R.id.card);
                icon = v.findViewById(R.id.icon);
                label = v.findViewById(R.id.label);
            }
        }

        @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup p, int vt) {
            View v = LayoutInflater.from(p.getContext()).inflate(R.layout.item_basecamp_tile, p, false);
            return new VH(v);
        }

        @Override public void onBindViewHolder(@NonNull VH h, int pos) {
            Tile t = data.get(pos);
            h.label.setText(t.label);
            h.icon.setImageResource(t.icon);
            h.card.setOnClickListener(v -> {
                NavDestination node = null;
                try { node = nav.getGraph().findNode(t.destId); } catch (Exception ignored) {}
                if (node != null) {
                    try { nav.navigate(t.destId); }
                    catch (Exception e) { Toast.makeText(v.getContext(), "Navigation error", Toast.LENGTH_SHORT).show(); }
                } else {
                    Toast.makeText(v.getContext(), t.label + " • Coming soon", Toast.LENGTH_SHORT).show();
                }
            });
        }

        @Override public int getItemCount() { return data.size(); }
    }
}
