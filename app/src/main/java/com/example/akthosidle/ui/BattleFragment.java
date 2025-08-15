package com.example.akthosidle.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.akthosidle.R;
import com.example.akthosidle.battle.CombatEngine;

public class BattleFragment extends Fragment {

    private GameViewModel vm;

    private TextView tvMonster;
    private TextView tvPlayerHp, tvMonsterHp;
    private ProgressBar barPlayerHp, barMonsterHp;
    private Button btnToggle;

    // set your default monster here
    private static final String DEFAULT_MONSTER_ID = "shadow_thief";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_battle, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle s) {
        vm = new ViewModelProvider(requireActivity()).get(GameViewModel.class);

        tvMonster     = v.findViewById(R.id.tvMonster);
        tvPlayerHp    = v.findViewById(R.id.tvPlayerHp);
        tvMonsterHp   = v.findViewById(R.id.tvMonsterHp);
        barPlayerHp   = v.findViewById(R.id.barPlayerHp);
        barMonsterHp  = v.findViewById(R.id.barMonsterHp);
        btnToggle     = v.findViewById(R.id.btnToggle);

        tvMonster.setText("Shadow Thief");

        btnToggle.setOnClickListener(_v -> vm.toggleFight(DEFAULT_MONSTER_ID));

        vm.battleState().observe(getViewLifecycleOwner(), this::renderState);
    }

    private void renderState(@Nullable CombatEngine.BattleState s) {
        if (s == null) {
            btnToggle.setText("Start");
            tvPlayerHp.setText("--/--");
            tvMonsterHp.setText("--/--");
            barPlayerHp.setProgress(0);
            barMonsterHp.setProgress(0);
            return;
        }

        // Update button label
        btnToggle.setText(s.running ? "Stop" : "Start");

        // Progress bars
        // Use current values as max if max isn't tracked; here we read from repo totals when needed.
        int pMax = Math.max(barPlayerHp.getMax(), s.playerHp);
        int mMax = Math.max(barMonsterHp.getMax(), s.monsterHp);
        if (barPlayerHp.getMax() != pMax) barPlayerHp.setMax(pMax);
        if (barMonsterHp.getMax() != mMax) barMonsterHp.setMax(mMax);

        barPlayerHp.setProgress(s.playerHp);
        barMonsterHp.setProgress(s.monsterHp);

        tvPlayerHp.setText(s.playerHp + " / " + pMax);
        tvMonsterHp.setText(s.monsterHp + " / " + mMax);
    }
}
