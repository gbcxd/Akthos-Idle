package com.example.akthosidle.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.akthosidle.R;
import com.example.akthosidle.domain.model.Recipe;
import com.example.akthosidle.domain.model.RecipeIO;
import com.example.akthosidle.domain.model.SkillId;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Cooking screen; “Cook All” crafts one per turn at recipe.timeSec until resources run out or you stop. */
public class cookingFragment extends Fragment {

    private GameViewModel vm;
    private RecyclerView list;
    private RecipeAdapter adapter;

    // --- auto-cook loop state ---
    private final Handler handler = new Handler(Looper.getMainLooper());
    @Nullable private String autoRecipeId = null;   // which recipe is auto-running
    private long autoIntervalMs = 0L;               // cached from recipe.timeSec
    private final Runnable autoTick = new Runnable() {
        @Override public void run() {
            if (autoRecipeId == null) return;

            // Craft exactly one. If success, schedule next “turn”; else stop.
            boolean ok = vm.repo.craftOnce(autoRecipeId);
            if (ok) {
                refresh(); // update counts/UI
                handler.postDelayed(this, autoIntervalMs);
            } else {
                stopAuto();
            }
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_cooking, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle s) {
        super.onViewCreated(v, s);
        vm = new ViewModelProvider(requireActivity()).get(GameViewModel.class);

        list = v.findViewById(R.id.recyclerCooking);
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new RecipeAdapter();
        list.setAdapter(adapter);

        refresh();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        stopAuto(); // avoid leaks
    }

    private void refresh() {
        Map<String, Integer> bag = new HashMap<>(vm.player().bag);
        int level = vm.repo.skillLevel(SkillId.COOKING);

        List<Recipe> recipes = vm.repo.getRecipesBySkill(SkillId.COOKING);
        List<RecipeRow> rows = new ArrayList<>();
        for (Recipe r : recipes) rows.add(buildRow(r, level, bag));

        adapter.setData(rows);
    }

    /** Build a display row with craftability and reasons. */
    private RecipeRow buildRow(Recipe r, int level, Map<String, Integer> bag) {
        RecipeRow row = new RecipeRow();
        row.recipe = r;
        row.name = (r.name != null ? r.name : r.id);

        // Level requirement
        if (level < Math.max(1, r.reqLevel)) {
            row.reasons.add("Level " + r.reqLevel + " required");
        }

        // Station availability (assumed available; wire real check if you gate by stations)
        if (r.station != null && !r.station.trim().isEmpty()) {
            boolean stationAvailable = true;
            if (!stationAvailable) row.reasons.add("Need " + r.station);
        }

        int maxCraft = Integer.MAX_VALUE;
        if (r.inputs != null && !r.inputs.isEmpty()) {
            for (RecipeIO in : r.inputs) {
                if (in == null || in.id == null) continue;
                String needId = in.id;
                int needQty = Math.max(1, in.qty);

                // allow recipe id "raw_shrimp" to match item "fish_raw_shrimp"
                int have = bag.getOrDefault(needId, 0);
                if ("raw_shrimp".equalsIgnoreCase(needId)) {
                    have += bag.getOrDefault("fish_raw_shrimp", 0);
                }

                int canMake = have / needQty;
                maxCraft = Math.min(maxCraft, canMake);

                if (have < needQty) {
                    String nice = vm.repo.itemName(
                            "raw_shrimp".equalsIgnoreCase(needId) ? "fish_raw_shrimp" : needId
                    );
                    row.reasons.add("Need " + nice + " ×" + needQty);
                }
            }
        } else {
            maxCraft = 0;
        }
        row.maxCraftable = (maxCraft == Integer.MAX_VALUE ? 0 : maxCraft);
        row.craftable = (row.maxCraftable > 0 && row.reasons.isEmpty());

        boolean isAuto = r.id.equals(autoRecipeId);
        if (row.reasons.isEmpty()) {
            row.subtitle = (isAuto ? "Cooking..." : ("XP " + r.xp + " • " + r.timeSec + "s • Can make ×" + row.maxCraftable));
        } else {
            row.subtitle = joinReasons(row.reasons);
        }

        row.isAuto = isAuto;
        return row;
    }

    private String joinReasons(List<String> reasons) {
        if (reasons == null || reasons.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < reasons.size(); i++) {
            if (i > 0) sb.append(" • ");
            sb.append(reasons.get(i));
        }
        return sb.toString();
    }

    private void startAuto(Recipe r) {
        stopAuto(); // only one auto loop at a time
        if (r == null || r.id == null) return;

        // Quick sanity: try a dry-run craft to ensure we can craft at least one this turn.
        // We don't actually want to consume immediately, so we just check from a row build.
        Map<String, Integer> bag = new HashMap<>(vm.player().bag);
        RecipeRow row = buildRow(r, vm.repo.skillLevel(SkillId.COOKING), bag);
        if (!row.craftable) {
            // UI already shows reasons; no toast needed
            return;
        }

        autoRecipeId = r.id;
        autoIntervalMs = Math.max(1, (long) (Math.max(0.1f, r.timeSec) * 1000L));
        refresh();                 // redraw rows (button becomes Stop)
        handler.post(autoTick);    // first craft happens immediately; next after timeSec
    }

    private void stopAuto() {
        handler.removeCallbacks(autoTick);
        if (autoRecipeId != null) {
            autoRecipeId = null;
            refresh(); // redraw rows back to normal
        }
    }

    // ===== view model for a row =====
    private static class RecipeRow {
        Recipe recipe;
        String name;
        String subtitle;
        int maxCraftable;
        boolean craftable;
        boolean isAuto;
        final List<String> reasons = new ArrayList<>();
    }

    // ===== adapter =====
    private class RecipeAdapter extends RecyclerView.Adapter<RecipeAdapter.VH> {
        private final List<RecipeRow> data = new ArrayList<>();

        void setData(List<RecipeRow> rows) {
            data.clear();
            if (rows != null) data.addAll(rows);
            notifyDataSetChanged();
        }

        class VH extends RecyclerView.ViewHolder {
            final TextView title, sub;
            final Button btnOne, btnAll;
            VH(@NonNull View v) {
                super(v);
                title = v.findViewById(R.id.tvName);
                sub   = v.findViewById(R.id.tvSub);
                btnOne= v.findViewById(R.id.btnCraftOne);
                btnAll= v.findViewById(R.id.btnCraftAll);
            }
        }

        @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup p, int vt) {
            View v = LayoutInflater.from(p.getContext()).inflate(R.layout.item_cook_recipe, p, false);
            return new VH(v);
        }

        @Override public void onBindViewHolder(@NonNull VH h, int pos) {
            RecipeRow r = data.get(pos);
            h.title.setText(r.name);
            h.sub.setText(r.subtitle);

            boolean anyAutoRunning = (autoRecipeId != null);

            // “Cook One” = immediate single craft
            h.btnOne.setEnabled(r.craftable && !r.isAuto && !anyAutoRunning);
            h.btnOne.setOnClickListener(v -> {
                boolean ok = vm.repo.craftOnce(r.recipe.id);
                if (ok) refresh();
            });

            // “Cook All” becomes “Stop” while this recipe is auto-cooking
            h.btnAll.setText(r.isAuto ? "Stop" : "Cook All");
            h.btnAll.setEnabled(r.isAuto || (r.craftable && !anyAutoRunning));
            h.btnAll.setOnClickListener(v -> {
                if (r.isAuto) {
                    stopAuto();
                } else {
                    startAuto(r.recipe);
                }
            });

            // Optional: visually mute non-craftable rows
            float alpha = r.craftable || r.isAuto ? 1f : 0.55f;
            h.itemView.setAlpha(alpha);
        }

        @Override public int getItemCount() { return data.size(); }
    }
}
