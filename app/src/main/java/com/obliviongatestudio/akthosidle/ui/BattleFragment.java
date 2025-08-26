package com.obliviongatestudio.akthosidle.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.obliviongatestudio.akthosidle.R;
import com.obliviongatestudio.akthosidle.data.dtos.InventoryItem;
import com.obliviongatestudio.akthosidle.domain.model.Item;
import com.obliviongatestudio.akthosidle.domain.model.Monster;
import com.obliviongatestudio.akthosidle.domain.model.PlayerCharacter;
import com.obliviongatestudio.akthosidle.domain.model.SkillId;
import com.obliviongatestudio.akthosidle.engine.CombatEngine;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class BattleFragment extends Fragment {

    private static final String MONSTER_ID = "shadow_thief"; // change if you wire a selector

    private GameViewModel vm;

    private ProgressBar barEnemy, barPlayer;
    private TextView tvEnemyPct, tvPlayerPct, tvEnemy, tvStatsRow, tvNoLoot;
    private Button btnFight, btnCollect, btnQuickHeal;
    private Button btnTrainSkill; // NEW: pick training skill
    private ImageView imgMonster;
    private LootAdapter lootAdapter;
    private LogAdapter logAdapter;

    // Infinite-loop toggle: true while user has pressed Fight and not pressed Stop
    private boolean keepFighting = false;

    // long-press (2s) detector for Quick Heal
    private final Handler lpHandler = new Handler(Looper.getMainLooper());
    private boolean longPressFired = false;
    private static final int LONG_PRESS_MS = 2000;

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
        imgMonster   = v.findViewById(R.id.imgMonster);
        btnCollect   = v.findViewById(R.id.btnCollect);
        tvNoLoot     = v.findViewById(R.id.tvNoLoot);

        // NEW
        btnTrainSkill = v.findViewById(R.id.btnTrainSkill);

        // Loot list
        RecyclerView rvLoot = v.findViewById(R.id.rvLoot);
        rvLoot.setLayoutManager(new LinearLayoutManager(requireContext()));
        lootAdapter = new LootAdapter(vm.repo.items);
        rvLoot.setAdapter(lootAdapter);

        // Combat log list
        RecyclerView rvLog = v.findViewById(R.id.rvCombatLog);
        rvLog.setLayoutManager(new LinearLayoutManager(requireContext()));
        logAdapter = new LogAdapter();
        rvLog.setAdapter(logAdapter);

        // Enemy label (fallback text if monster not found)
        Monster m = vm.repo.getMonster(MONSTER_ID);
        tvEnemy.setText(m != null && m.name != null ? m.name : "Shadow Thief");

        // Fight/Stop with infinite loop while keepFighting == true
        btnFight.setOnClickListener(_v -> {
            CombatEngine.BattleState sNow = vm.battleState().getValue();
            boolean running = (sNow != null && sNow.running);
            if (running) {
                // User pressed Stop → end loop and stop fight
                keepFighting = false;
                vm.stopFight();
            } else {
                // User pressed Fight → set loop flag and start fighting
                keepFighting = true;
                vm.startFight(MONSTER_ID);
            }
            btnFight.setText(running ? "Fight" : "Stop");
        });

        // Collect button
        btnCollect.setOnClickListener(_v -> vm.collectLoot());

        // Stats row (Attack / Defense / Speed)
        renderStatsRow();

        // Quick Heal button: tap = heal; 2s long-press = select consumable
        setupQuickHealButton();

        // NEW: training-skill picker
        if (btnTrainSkill != null) {
            refreshTrainSkillLabel();
            btnTrainSkill.setOnClickListener(__ -> showTrainSkillPicker());
        }

        // Observe combat state
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

        // Initial HP paint
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
        Integer have = vm.player().bag.get(id);
        if (have == null || have <= 0) {
            vm.repo.toast("Out of " + vm.repo.itemName(id));
            refreshQuickHealLabel();
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

    // === NEW: training-skill UI ===

    private void refreshTrainSkillLabel() {
        if (btnTrainSkill == null) return;
        SkillId s = vm.repo.getCombatTrainingSkill();
        String pretty = (s == null) ? "Attack" : capitalize(s.name());
        btnTrainSkill.setText("Train: " + pretty);
    }

    private void showTrainSkillPicker() {
        final SkillId[] choices = new SkillId[] {
                SkillId.ATTACK, SkillId.STRENGTH, SkillId.DEFENSE,
                SkillId.ARCHERY, SkillId.MAGIC, SkillId.HP
        };
        String[] labels = new String[] { "Attack", "Strength", "Defense", "Archery", "Magic", "HP" };

        SkillId current = vm.repo.getCombatTrainingSkill();
        int checked = 0;
        for (int i = 0; i < choices.length; i++) {
            if (choices[i] == current) { checked = i; break; }
        }

        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Train which skill?")
                .setSingleChoiceItems(labels, checked, (d, which) -> {
                    SkillId pick = choices[which];
                    vm.repo.setCombatTrainingSkill(pick);
                    refreshTrainSkillLabel();
                    vm.repo.toast("Training: " + labels[which]);
                    d.dismiss();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.substring(0,1).toUpperCase(Locale.US) + s.substring(1).toLowerCase(Locale.US);
    }

    // === end training-skill UI ===

    private void renderState(CombatEngine.BattleState s) {
        if (s == null) return;

        // Enemy bar
        Monster m = s.monsterId != null ? vm.repo.getMonster(s.monsterId) : null;
        int eMax = Math.max(1, (m != null && m.stats != null) ? m.stats.health : 1);
        int ePct = (int) Math.round(100.0 * Math.max(0, Math.min(s.monsterHp, eMax)) / (double) eMax);
        barEnemy.setProgress(ePct);
        tvEnemyPct.setText(ePct + "%");

        // Player bar
        renderPlayerHp(s.playerHp);

        // Button label reflects running state
        btnFight.setText(s.running ? "Stop" : "Fight");

        // Auto-loop logic
        if (!s.running && keepFighting) {
            if (s.monsterHp == 0) {
                vm.startFight(s.monsterId != null ? s.monsterId : MONSTER_ID);
            } else if (s.playerHp == 0) {
                keepFighting = false;
                btnFight.setText("Fight");
                vm.repo.toast("You died — auto-loop stopped");
            }
        }
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
