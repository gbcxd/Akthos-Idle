package com.example.akthosidle.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
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
import com.example.akthosidle.battle.CombatEngine;
import com.example.akthosidle.model.InventoryItem;
import com.example.akthosidle.model.PlayerCharacter;

import java.util.List;

public class BattleFragment extends Fragment {

    private GameViewModel vm;

    private ProgressBar barEnemy, barPlayer;
    private TextView tvEnemyPct, tvPlayerPct, tvEnemy, tvAtk, tvDef, tvSpd, tvNoLoot;
    private Button btnFight, btnCollect;
    private CheckBox cbAutoRespawn;
    private ImageView imgMonster;
    private LootAdapter lootAdapter;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
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
        tvAtk        = v.findViewById(R.id.tvAtk);
        tvDef        = v.findViewById(R.id.tvDef);
        tvSpd        = v.findViewById(R.id.tvSpd);
        btnFight     = v.findViewById(R.id.btnFight);
        cbAutoRespawn= v.findViewById(R.id.cbAutoRespawn);
        imgMonster   = v.findViewById(R.id.imgMonster);
        btnCollect   = v.findViewById(R.id.btnCollect);
        tvNoLoot     = v.findViewById(R.id.tvNoLoot);

        RecyclerView rvLoot = v.findViewById(R.id.rvLoot);
        rvLoot.setLayoutManager(new LinearLayoutManager(requireContext()));
        lootAdapter = new LootAdapter(vm.repo.items); // public defs map in repo
        rvLoot.setAdapter(lootAdapter);

        // Simple example monster label
        tvEnemy.setText("Shadow Thief (Lvl 1)");

        // Start/Stop single button
        btnFight.setOnClickListener(_v -> vm.toggleFight("shadow_thief"));

        // Collect button
        btnCollect.setOnClickListener(_v -> vm.collectLoot());

        // Observe combat
        vm.battleState().observe(getViewLifecycleOwner(), this::renderState);

        // Observe loot
        vm.pendingLoot().observe(getViewLifecycleOwner(), this::renderLoot);

        // Show basic player stats
        PlayerCharacter pc = vm.player();
        int atk = pc.totalStats(vm.repo.gearStats(pc)).attack;
        int def = pc.totalStats(vm.repo.gearStats(pc)).defense;
        double spd = pc.totalStats(vm.repo.gearStats(pc)).speed;
        tvAtk.setText("Atk " + atk);
        tvDef.setText("   Def " + def);
        tvSpd.setText("   Spd " + String.format("%.2f", spd));
    }

    private void renderState(CombatEngine.BattleState s) {
        if (s == null) return;
        // bars
        int eMax = Math.max(1, vm.repo.getMonster(s.monsterId).stats.health);
        int pMax = Math.max(1, vm.player().totalStats(vm.repo.gearStats(vm.player())).health);
        int ePct = (int)Math.round(100.0 * s.monsterHp / (double)eMax);
        int pPct = (int)Math.round(100.0 * s.playerHp  / (double)pMax);
        barEnemy.setProgress(ePct);
        barPlayer.setProgress(pPct);
        tvEnemyPct.setText(ePct + "%");
        tvPlayerPct.setText(pPct + "%");

        btnFight.setText(s.running ? "Stop" : "Fight");
    }

    private void renderLoot(List<InventoryItem> loot) {
        lootAdapter.submit(loot);
        boolean has = loot != null && !loot.isEmpty();
        btnCollect.setEnabled(has);
        tvNoLoot.setVisibility(has ? View.GONE : View.VISIBLE);
    }
}
