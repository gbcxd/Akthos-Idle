package com.example.akthosidle.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.akthosidle.R;
import com.example.akthosidle.data.dtos.InventoryItem;
import com.example.akthosidle.domain.model.Item;
import com.example.akthosidle.domain.model.PlayerCharacter;
import com.example.akthosidle.engine.CombatEngine;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class BattleFragment extends Fragment {

    private GameViewModel vm;

    private ProgressBar barEnemy, barPlayer;
    private TextView tvEnemyPct, tvPlayerPct, tvEnemy, tvStatsRow, tvNoLoot;
    private Button btnFight, btnCollect, btnQuickHeal;
    private CheckBox cbAutoRespawn;
    private ImageView imgMonster;
    private LootAdapter lootAdapter;
    private LogAdapter logAdapter;

    // long-press (2s) detector for Quick Heal
    private final Handler lpHandler = new Handler(Looper.getMainLooper());
    private boolean longPressFired = false;
    private final int LONG_PRESS_MS = 2000;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_battle, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle s) {
        super.onViewCreated(v, s);
        vm = new ViewModelProvider(requireActivity()).get(GameViewModel.class);

        tvEnemy      = v.findViewById(R.id.tvEnemy);
        barEnemy     = v.findViewById(R.id.barEnemy);
        barPlayer    = v.findViewById(R.id.barPlayer);
        tvEnemyPct   = v.findViewById(R.id.tvEnemyPct);
        tvPlayerPct  = v.findViewById(R.id.tvPlayerPct);
        tvStatsRow   = v.findViewById(R.id.tvStatsRow);
        btnQuickHeal = v.findViewById(R.id.btnQuickHeal);
        btnFight     = v.findViewById(R.id.btnFight);
        cbAutoRespawn= v.findViewById(R.id.cbAutoRespawn);
        imgMonster   = v.findViewById(R.id.imgMonster);
        btnCollect   = v.findViewById(R.id.btnCollect);
        tvNoLoot     = v.findViewById(R.id.tvNoLoot);

        // Loot list
        RecyclerView rvLoot = v.findViewById(R.id.rvLoot);
        rvLoot.setLayoutManager(new LinearLayoutManager(requireContext()));
        lootAdapter = new LootAdapter(vm.repo.items); // public defs map in repo
        rvLoot.setAdapter(lootAdapter);

        // Combat log list
        RecyclerView rvLog = v.findViewById(R.id.rvCombatLog);
        rvLog.setLayoutManager(new LinearLayoutManager(requireContext()));
        logAdapter = new LogAdapter();
        rvLog.setAdapter(logAdapter);

        // Example enemy label (you can wire a real selector)
        tvEnemy.setText("Shadow Thief (Lvl 1)");

        // Fight toggle
        btnFight.setOnClickListener(_v -> vm.toggleFight("shadow_thief"));

        // Collect button
        btnCollect.setOnClickListener(_v -> vm.collectLoot());

        // Stats row (Attack / Defense / Speed)
        renderStatsRow();

        // Quick Heal button: tap = heal; 2s long-press = select consumable
        setupQuickHealButton();

        // Observe combat
        vm.battleState().observe(getViewLifecycleOwner(), this::renderState);

        // Observe loot
        vm.pendingLoot().observe(getViewLifecycleOwner(), this::renderLoot);

        // Observe combat log
        vm.combatLog().observe(getViewLifecycleOwner(), logs -> {
            logAdapter.submit(logs == null ? new ArrayList<>() : logs);
        });

        // Observe player HP so the bar updates when healing outside ticks
        vm.repo.playerHpLive.observe(getViewLifecycleOwner(), hp -> {
            if (hp == null) return;
            renderPlayerHp(hp);
        });

        // Initial HP bar paint
        PlayerCharacter pc = vm.player();
        int pMax = Math.max(1, pc.totalStats(vm.repo.gearStats(pc)).health);
        int pPct = (int) Math.round(100.0 * (pc.currentHp == null ? pMax : pc.currentHp) / (double) pMax);
        barPlayer.setProgress(pPct);
        tvPlayerPct.setText(pPct + "%");
    }

    private void renderStatsRow() {
        PlayerCharacter pc = vm.player();
        int atk = pc.totalStats(vm.repo.gearStats(pc)).attack;
        int def = pc.totalStats(vm.repo.gearStats(pc)).defense;
        double spd = pc.totalStats(vm.repo.gearStats(pc)).speed;
        tvStatsRow.setText(String.format(Locale.US, "Atk %d   Def %d   Spd %.2f", atk, def, spd));
    }

    private void setupQuickHealButton() {
        refreshQuickHealLabel();

        // Custom 2s long-press using onTouch
        btnQuickHeal.setOnTouchListener((view, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    longPressFired = false;
                    lpHandler.postDelayed(() -> {
                        longPressFired = true;
                        showFoodPicker(); // open the picker
                    }, LONG_PRESS_MS);
                    return true; // start tracking
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    lpHandler.removeCallbacksAndMessages(null);
                    if (!longPressFired) {
                        // Treat as click (short press)
                        quickHeal();
                    }
                    return true;
            }
            return false;
        });
    }

    private void quickHeal() {
        String id = vm.getQuickFoodId();
        if (id == null) {
            vm.repo.toast("Long-press to choose a consumable");
            return;
        }
        // Check quantity still available
        Integer have = vm.player().bag.get(id);
        if (have == null || have <= 0) {
            vm.repo.toast("Out of " + vm.repo.itemName(id));
            refreshQuickHealLabel(); // will show "(none)"
            return;
        }
        vm.consumeFood(id); // this updates HP LiveData
        refreshQuickHealLabel();
    }

    private void showFoodPicker() {
        List<InventoryItem> foods = vm.getFoodItems();
        if (foods == null || foods.isEmpty()) {
            vm.repo.toast("No healing consumables");
            return;
        }
        // Build names list
        final List<String> labels = new ArrayList<>();
        for (InventoryItem it : foods) {
            String name = vm.repo.itemName(it.id);
            labels.add(name + " ×" + it.quantity);
        }
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Select healing item")
                .setItems(labels.toArray(new String[0]), (d, which) -> {
                    InventoryItem pick = foods.get(which);
                    vm.setQuickFoodId(pick.id);
                    vm.repo.toast("Selected: " + vm.repo.itemName(pick.id));
                    refreshQuickHealLabel();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void refreshQuickHealLabel() {
        String id = vm.getQuickFoodId();
        if (id == null) {
            btnQuickHeal.setText("Heal (long-press to select)");
            return;
        }
        Item def = vm.repo.getItem(id);
        String name = def != null && def.name != null ? def.name : id;
        int qty = vm.player().bag.getOrDefault(id, 0);
        btnQuickHeal.setText(qty > 0 ? ("Heal: " + name + " (" + qty + ")")
                : ("Heal: " + name + " (0)"));
    }

    private void renderState(CombatEngine.BattleState s) {
        if (s == null) return;

        int eMax = Math.max(1, vm.repo.getMonster(s.monsterId).stats.health);
        int ePct = (int) Math.round(100.0 * s.monsterHp / (double) eMax);
        barEnemy.setProgress(ePct);
        tvEnemyPct.setText(ePct + "%");

        renderPlayerHp(s.playerHp);
        btnFight.setText(s.running ? "Stop" : "Fight");
    }

    private void renderPlayerHp(int hpNow) {
        PlayerCharacter pc = vm.player();
        int pMax = Math.max(1, pc.totalStats(vm.repo.gearStats(pc)).health);
        int pPct = (int) Math.round(100.0 * Math.min(Math.max(0, hpNow), pMax) / (double) pMax);
        barPlayer.setProgress(pPct);
        tvPlayerPct.setText(pPct + "%");
    }

    private void renderLoot(List<InventoryItem> loot) {
        lootAdapter.submit(loot);
        boolean has = loot != null && !loot.isEmpty();
        btnCollect.setEnabled(has);
        tvNoLoot.setVisibility(has ? View.GONE : View.VISIBLE);
    }
}
