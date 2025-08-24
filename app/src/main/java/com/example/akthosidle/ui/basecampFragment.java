package com.example.akthosidle.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.NavDestination;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.akthosidle.R;
import com.example.akthosidle.domain.model.Monster;
import com.example.akthosidle.domain.model.SlayerAssignment;

import java.util.ArrayList;
import java.util.List;

/** Home / Basecamp hub with 3-column tiles + Slayer NPC card at top. */
public class basecampFragment extends Fragment {

    private GameViewModel vm;

    // Slayer card views
    private View slayerCard;
    private ImageView slayerPortrait;
    private TextView slayerTitle, slayerSubtitle, slayerTaskLine;
    private ProgressBar slayerProgress;
    private Button btnGetTask, btnAbandonTask, btnClaimTask;

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

        vm = new ViewModelProvider(requireActivity()).get(GameViewModel.class);

        // ----- Tiles grid (kept as before) -----
        RecyclerView rv = v.findViewById(R.id.recyclerBasecamp);
        rv.setLayoutManager(new GridLayoutManager(requireContext(), 3));
        rv.setAdapter(new TilesAdapter(buildTiles(), NavHostFragment.findNavController(this)));

        // ----- Slayer NPC card wiring -----
        slayerCard      = v.findViewById(R.id.slayer_card);
        slayerPortrait  = v.findViewById(R.id.slayer_portrait);
        slayerTitle     = v.findViewById(R.id.slayer_title);
        slayerSubtitle  = v.findViewById(R.id.slayer_subtitle);
        slayerTaskLine  = v.findViewById(R.id.slayer_task_line);
        slayerProgress  = v.findViewById(R.id.slayer_progress);
        btnGetTask      = v.findViewById(R.id.btn_get_task);
        btnAbandonTask  = v.findViewById(R.id.btn_abandon_task);
        btnClaimTask    = v.findViewById(R.id.btn_claim_task);

        slayerTitle.setText("Slayer Master");
        // Use your own drawable if you have one:
        slayerPortrait.setImageResource(android.R.drawable.ic_menu_help);

        btnGetTask.setOnClickListener(_v -> {
            SlayerAssignment cur = vm.repo.getSlayerAssignment();
            if (cur != null && !cur.isComplete()) {
                // already have a task — replace it
                vm.repo.rollNewSlayerTask(/*forceReplace=*/true);
            } else {
                vm.repo.rollNewSlayerTask();
            }
        });

        btnAbandonTask.setOnClickListener(_v -> vm.repo.abandonSlayerTask());

        btnClaimTask.setOnClickListener(_v -> {
            boolean ok = vm.repo.claimSlayerTaskIfComplete();
            if (!ok) vm.repo.toast("Task not complete yet");
        });

        // Observe assignment live updates
        vm.repo.slayerLive.observe(getViewLifecycleOwner(), this::renderSlayer);

        // Initial paint
        renderSlayer(vm.repo.getSlayerAssignment());
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

    private void renderSlayer(@Nullable SlayerAssignment a) {
        if (a == null) {
            slayerSubtitle.setText("Region: —");
            slayerTaskLine.setText("No task. Talk to the Slayer Master to get one!");
            slayerProgress.setMax(100);
            slayerProgress.setProgress(0);
            btnGetTask.setEnabled(true);
            btnAbandonTask.setEnabled(false);
            btnClaimTask.setEnabled(false);
            return;
        }

        // Region (optional; if you added a.region in your model, prefer that)
        String region = "Base Region";
        slayerSubtitle.setText("Region: " + region);

        // Monster display name
        String monsterName = (a.label != null && !a.label.isEmpty()) ? a.label : a.monsterId;
        if ((monsterName == null || monsterName.equals(a.monsterId)) && a.monsterId != null) {
            Monster mDef = vm.repo.getMonster(a.monsterId);
            if (mDef != null && mDef.name != null) monsterName = mDef.name;
        }

        int done = a.getDone(); // uses your helper; falls back to progress
        slayerTaskLine.setText("Kill " + monsterName + " (" + done + " / " + a.required + ")");

        slayerProgress.setMax(Math.max(1, a.required));
        slayerProgress.setProgress(Math.min(a.required, done));

        boolean complete = a.isComplete();
        btnGetTask.setEnabled(complete);      // new task once complete (or after abandon)
        btnAbandonTask.setEnabled(!complete);
        btnClaimTask.setEnabled(complete);
    }

    // ----- adapter (unchanged) -----
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
