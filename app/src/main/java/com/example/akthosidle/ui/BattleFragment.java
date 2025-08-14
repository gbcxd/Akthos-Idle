package com.example.akthosidle.ui;

import android.os.Bundle;
import android.view.*;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.example.akthosidle.R;
import com.example.akthosidle.battle.CombatEngine;

public class BattleFragment extends Fragment {
    private GameViewModel vm;
    private TextView tvMonster, tvHP;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_battle, container, false);
    }

    @Override public void onViewCreated(@NonNull View v, @Nullable Bundle s) {
        vm = new ViewModelProvider(requireActivity()).get(GameViewModel.class);
        tvMonster = v.findViewById(R.id.tvMonster);
        tvHP = v.findViewById(R.id.tvHP);
        Button btnStart = v.findViewById(R.id.btnStart);
        Button btnStop = v.findViewById(R.id.btnStop);

        btnStart.setOnClickListener(_v -> vm.startFight("shadow_thief"));
        btnStop.setOnClickListener(_v -> vm.stopFight());

        vm.battleState().observe(getViewLifecycleOwner(), this::render);
    }

    private void render(CombatEngine.BattleState s) {
        if (s == null) return;
        tvMonster.setText("Monster: " + s.monsterId + (s.running ? " (fighting)" : ""));
        tvHP.setText("Player HP: " + s.playerHp + "   Monster HP: " + s.monsterHp);
    }
}
