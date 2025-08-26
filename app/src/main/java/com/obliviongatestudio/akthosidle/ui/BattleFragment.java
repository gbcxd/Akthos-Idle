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
    private Button btnTrainSkill;
    private ImageView imgMonster;
    private LootAdapter lootAdapter;
    private LogAdapter logAdapter;

    private boolean keepFighting = false;

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

        // ... (your existing findViewById calls)
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
        btnTrainSkill = v.findViewById(R.id.btnTrainSkill);

        // ... (RecyclerView setup)
        RecyclerView rvLoot = v.findViewById(R.id.rvLoot);
        rvLoot.setLayoutManager(new LinearLayoutManager(requireContext()));
        lootAdapter = new LootAdapter(vm.repo.items);
        rvLoot.setAdapter(lootAdapter);

        RecyclerView rvLog = v.findViewById(R.id.rvCombatLog);
        rvLog.setLayoutManager(new LinearLayoutManager(requireContext()));
        logAdapter = new LogAdapter();
        rvLog.setAdapter(logAdapter);


        Monster m = vm.repo.getMonster(MONSTER_ID);
        tvEnemy.setText(m != null && m.name != null ? m.name : "Shadow Thief");

        btnFight.setOnClickListener(_v -> {
            CombatEngine.BattleState sNow = vm.battleState().getValue();
            boolean running = (sNow != null && sNow.running);
            if (running) {
                keepFighting = false;
                vm.stopFight();
            } else {
                // Ensure player is not dead before starting a new fight
                if (vm.player().currentHp > 0) {
                    keepFighting = true;
                    vm.startFight(MONSTER_ID);
                } else {
                    vm.repo.toast("You are defeated. Heal before fighting again.");
                }
            }
            // Button text will be updated by renderState observer
        });

        btnCollect.setOnClickListener(_v -> vm.collectLoot());
        renderStatsRow();
        setupQuickHealButton(); // This already sets up tap and long-press

        if (btnTrainSkill != null) {
            refreshTrainSkillLabel();
            btnTrainSkill.setOnClickListener(__ -> showTrainSkillPicker());
        }

        vm.battleState().observe(getViewLifecycleOwner(), this::renderState);
        vm.pendingLoot().observe(getViewLifecycleOwner(), this::renderLoot);
        vm.combatLog().observe(getViewLifecycleOwner(), logs -> {
            logAdapter.submit(logs == null ? new ArrayList<>() : logs);
        });

        // This observer is crucial for seeing HP updates from healing
        vm.repo.playerHpLive.observe(getViewLifecycleOwner(), hp -> {
            if (hp == null) return;
            renderPlayerHp(hp); // Make sure this updates the bar and text
            // NEW: After healing, check if player died but now has HP, so they can fight.
            // This is more for healing outside combat, but good to keep.
            CombatEngine.BattleState currentState = vm.battleState().getValue();
            if (currentState != null && !currentState.running && hp > 0 && vm.player().currentHp <= 0) {
                // This scenario is tricky: player was dead, healed, but combat isn't running.
                // We'll let the existing fight button logic handle restart.
            }
            refreshQuickHealLabel(); // Update consumable count on button
        });

        // Initial HP paint
        PlayerCharacter pc = vm.player();
        // Ensure currentHp is not null and has a sensible default if it is.
        // GameViewModel should initialize currentHp if it's ever null from data source.
        int currentHp = (pc.currentHp == null) ? pc.totalStats(vm.repo.gearStats(pc)).health : pc.currentHp;
        renderPlayerHp(currentHp);
    }

    private void renderStatsRow() {
        PlayerCharacter pc = vm.player();
        int atk = pc.totalStats(vm.repo.gearStats(pc)).attack;
        int def = pc.totalStats(vm.repo.gearStats(pc)).defense;
        double spd = pc.totalStats(vm.repo.gearStats(pc)).speed;
        tvStatsRow.setText(String.format(Locale.US, "Atk %d   Def %d   Spd %.2f", atk, def, spd));
    }

    private void setupQuickHealButton() {
        refreshQuickHealLabel(); // Initial label setup

        // Custom 2s long-press using onTouch for selecting food
        btnQuickHeal.setOnTouchListener((view, event) -> {
            CombatEngine.BattleState sNow = vm.battleState().getValue();
            boolean combatRunning = (sNow != null && sNow.running);

            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    longPressFired = false;
                    // Only allow food selection if combat is NOT running to avoid complex UI mid-fight
                    if (!combatRunning) {
                        lpHandler.postDelayed(() -> {
                            longPressFired = true;
                            showFoodPicker(); // open the picker
                        }, LONG_PRESS_MS);
                    }
                    return true; // Start tracking touch
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    lpHandler.removeCallbacksAndMessages(null); // Cancel pending long-press
                    if (!longPressFired) {
                        // Treat as click (short press) for healing
                        quickHeal();
                    }
                    return true;
            }
            return false;
        });
    }

    private void quickHeal() {
        PlayerCharacter pc = vm.player();
        if (pc.currentHp <= 0) {
            vm.repo.toast("You are defeated."); // Cannot heal if already dead
            return;
        }

        String foodId = vm.getQuickFoodId();
        if (foodId == null) {
            // Suggest selecting food only if not in combat, otherwise it's heal or nothing.
            CombatEngine.BattleState sNow = vm.battleState().getValue();
            boolean combatRunning = (sNow != null && sNow.running);
            if (!combatRunning) {
                vm.repo.toast("Long-press to choose a consumable");
            } else {
                vm.repo.toast("No healing item selected for quick heal.");
            }
            return;
        }

        Integer have = pc.bag.get(foodId);
        if (have == null || have <= 0) {
            vm.repo.toast("Out of " + vm.repo.itemName(foodId));
            refreshQuickHealLabel(); // Update button if count changed
            return;
        }

        // Check if player is already at max HP
        int maxHp = pc.totalStats(vm.repo.gearStats(pc)).health;
        if (pc.currentHp >= maxHp) {
            vm.repo.toast("Already at full health!");
            return;
        }


        // Consume food - this should trigger playerHpLive observer to update UI
        // And if CombatEngine pulls player HP each tick, it will see the update.
        vm.consumeFood(foodId);
        vm.repo.toast("Used " + vm.repo.itemName(foodId)); // Feedback for healing
        // refreshQuickHealLabel(); // playerHpLive observer should call this indirectly

    }

    private void showFoodPicker() {
        // Prevent food picker during active combat to keep UI simple
        CombatEngine.BattleState sNow = vm.battleState().getValue();
        if (sNow != null && sNow.running) {
            vm.repo.toast("Cannot change food during combat.");
            return;
        }

        List<InventoryItem> foods = vm.getFoodItems();
        if (foods == null || foods.isEmpty()) {
            vm.repo.toast("No healing consumables");
            return;
        }
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
        if (btnQuickHeal == null) return; // Guard against null button if view is not ready

        String id = vm.getQuickFoodId();
        CombatEngine.BattleState sNow = vm.battleState().getValue();
        boolean combatRunning = (sNow != null && sNow.running);

        if (id == null) {
            btnQuickHeal.setText(combatRunning ? "Heal (None)" : "Heal (Long-press)");
            return;
        }

        Item def = vm.repo.getItem(id);
        String name = def != null && def.name != null ? def.name : id;
        int qty = vm.player().bag.getOrDefault(id, 0);
        btnQuickHeal.setText(qty > 0 ? ("Heal: " + name + " (" + qty + ")")
                : ("Heal: " + name + " (0)"));

        // Enable button if player has the item and is not at max HP, or if no item is selected (to allow selection)
        PlayerCharacter pc = vm.player();
        int maxHp = pc.totalStats(vm.repo.gearStats(pc)).health;
        boolean canHeal = (qty > 0 && pc.currentHp < maxHp && pc.currentHp > 0) || (id == null && !combatRunning);
        btnQuickHeal.setEnabled(canHeal);
    }


    // === NEW: training-skill UI ===
    private void refreshTrainSkillLabel() {
        if (btnTrainSkill == null) return;
        SkillId s = vm.repo.getCombatTrainingSkill();
        String pretty = (s == null) ? "Attack" : capitalize(s.name());
        btnTrainSkill.setText("Train: " + pretty);
    }

    private void showTrainSkillPicker() {
        // ... (existing showTrainSkillPicker code)
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

        Monster m = s.monsterId != null ? vm.repo.getMonster(s.monsterId) : null;
        int eMax = Math.max(1, (m != null && m.stats != null) ? m.stats.health : 1);
        int ePct = (int) Math.round(100.0 * Math.max(0, Math.min(s.monsterHp, eMax)) / (double) eMax);
        barEnemy.setProgress(ePct);
        tvEnemyPct.setText(ePct + "%");

        renderPlayerHp(s.playerHp); // Player HP is updated here from combat state

        btnFight.setText(s.running ? "Stop" : "Fight");
        // Keep quick heal button enabled if conditions are met, otherwise disable if player is dead.
        refreshQuickHealLabel(); // Update QuickHeal button state based on combat and player status
        if (s.playerHp <= 0) {
            btnQuickHeal.setEnabled(false); // Cannot heal if dead
        }


        if (!s.running && keepFighting) {
            if (s.monsterHp <= 0) { // Monster defeated
                vm.startFight(s.monsterId != null ? s.monsterId : MONSTER_ID);
            } else if (s.playerHp <= 0) { // Player defeated
                keepFighting = false;
                btnFight.setText("Fight"); // Reset button text
                vm.repo.toast("You died — auto-loop stopped");
            }
        }
    }

    private void renderPlayerHp(int hpNow) {
        PlayerCharacter pc = vm.player();
        int pMax = Math.max(1, pc.totalStats(vm.repo.gearStats(pc)).health);
        // Ensure hpNow is within bounds [0, pMax] before calculating percentage
        int currentHpClamped = Math.min(Math.max(0, hpNow), pMax);
        int pPct = (int) Math.round(100.0 * currentHpClamped / (double) pMax);

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
