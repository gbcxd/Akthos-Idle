package com.example.akthosidle.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.NavDestination;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.akthosidle.R;
import com.example.akthosidle.data.repo.GameRepository;
import com.example.akthosidle.domain.model.Monster;
import com.example.akthosidle.domain.model.SlayerAssignment;

import java.util.ArrayList;
import java.util.List;

public class basecampFragment extends Fragment {

    private GameViewModel vm;

    // Slayer card views
    private TextView tvSlayerTitle, tvSlayerRegion, tvSlayerBody;
    private Button btnPick, btnAbandon, btnClaim;
    private ProgressBar prog;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_basecamp, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle s) {
        super.onViewCreated(v, s);
        vm = new ViewModelProvider(requireActivity()).get(GameViewModel.class);

        // ---- Slayer card ----
        tvSlayerTitle  = v.findViewById(R.id.tvSlayerTitle);
        tvSlayerRegion = v.findViewById(R.id.tvSlayerRegion);
        tvSlayerBody   = v.findViewById(R.id.tvSlayerBody);
        btnPick        = v.findViewById(R.id.btnSlayerPick);
        btnAbandon     = v.findViewById(R.id.btnSlayerAbandon);
        btnClaim       = v.findViewById(R.id.btnSlayerClaim);
        prog           = v.findViewById(R.id.progSlayer);

        btnPick.setOnClickListener(_v -> openSlayerPicker());
        btnAbandon.setOnClickListener(_v -> {
            boolean ok = vm.repo.abandonSlayerTask();
            if (!ok) vm.repo.toast("No active task to abandon.");
        });
        btnClaim.setOnClickListener(_v -> {
            boolean ok = vm.repo.claimSlayerTaskIfComplete();
            if (!ok) vm.repo.toast("Task not complete yet.");
        });

        // Observe repository slayer LiveData
        vm.repo.slayerLive.observe(getViewLifecycleOwner(), this::renderSlayerCard);
        renderSlayerCard(vm.repo.getSlayerAssignment()); // initial paint

        // ---- Tiles grid (unchanged) ----
        RecyclerView rv = v.findViewById(R.id.recyclerBasecamp);
        rv.setLayoutManager(new GridLayoutManager(requireContext(), 3));
        rv.setAdapter(new TilesAdapter(buildTiles(), NavHostFragment.findNavController(this)));
    }

    private void renderSlayerCard(@Nullable SlayerAssignment a) {
        if (a == null) {
            tvSlayerRegion.setText("Region: —");
            tvSlayerBody.setText("No task. Tap Pick to choose a region & monster.");
            btnAbandon.setEnabled(false);
            btnClaim.setVisibility(View.GONE);
            prog.setProgress(0);
            return;
        }

        // Extract "Region — Monster" if label follows that format
        String label = (a.label == null ? "" : a.label);
        String regionLbl = "—";
        String body = label;
        int sep = label.indexOf(" — ");
        if (sep > 0) {
            regionLbl = label.substring(0, sep);
            body = label.substring(sep + 3);
        }
        tvSlayerRegion.setText("Region: " + regionLbl);

        int done = Math.max(0, a.getDone());
        int req  = Math.max(1, a.required);
        tvSlayerBody.setText(body + " — " + done + " / " + req);
        int pct = (int) Math.round(100.0 * Math.min(done, req) / req);
        prog.setProgress(pct);

        btnAbandon.setEnabled(!a.isComplete());
        btnClaim.setVisibility(a.isComplete() ? View.VISIBLE : View.GONE);
    }

    private void openSlayerPicker() {
        // Build region list
        List<GameRepository.SlayerRegion> regions = vm.repo.listSlayerRegions();
        if (regions.isEmpty()) {
            vm.repo.toast("No Slayer regions registered yet.");
            return;
        }

        View content = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_slayer_pick, null, false);
        Spinner spRegion  = content.findViewById(R.id.spRegion);
        Spinner spMonster = content.findViewById(R.id.spMonster);

        // Region adapter
        List<String> regionLabels = new ArrayList<>();
        for (GameRepository.SlayerRegion r : regions) regionLabels.add(r.label);
        ArrayAdapter<String> regionAdapter =
                new ArrayAdapter<>(requireContext(),
                        android.R.layout.simple_spinner_item, regionLabels);
        regionAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spRegion.setAdapter(regionAdapter);

        // Helper to refresh monster spinner when region changes
        final Runnable refreshMonsters = () -> {
            int idx = spRegion.getSelectedItemPosition();
            if (idx < 0 || idx >= regions.size()) return;
            GameRepository.SlayerRegion r = regions.get(idx);
            List<String> monsterLabels = new ArrayList<>();
            for (String mid : r.monsterIds) {
                Monster m = vm.repo.getMonster(mid);
                monsterLabels.add(m != null && m.name != null ? m.name : mid);
            }
            ArrayAdapter<String> monAdapter =
                    new ArrayAdapter<>(requireContext(),
                            android.R.layout.simple_spinner_item, monsterLabels);
            monAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spMonster.setAdapter(monAdapter);
        };

        // First paint and listener
        refreshMonsters.run();
        spRegion.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                refreshMonsters.run();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        new AlertDialog.Builder(requireContext())
                .setTitle("Choose Slayer Task")
                .setView(content)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Assign", (d, w) -> {
                    int rIdx = spRegion.getSelectedItemPosition();
                    int mIdx = spMonster.getSelectedItemPosition();
                    if (rIdx < 0 || rIdx >= regions.size()) return;
                    GameRepository.SlayerRegion r = regions.get(rIdx);
                    if (mIdx < 0 || mIdx >= r.monsterIds.size()) return;
                    String monsterId = r.monsterIds.get(mIdx);

                    // Assign & charge via repository (LiveData will update the card)
                    SlayerAssignment a = vm.repo.rollNewSlayerTask(r.id, monsterId);
                    if (a == null) {
                        // Repo already showed a toast on failure
                        return;
                    }
                })
                .show();
    }

    /* ------------ tiles (unchanged) ------------ */

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
                card  = v.findViewById(R.id.card);
                icon  = v.findViewById(R.id.icon);
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
