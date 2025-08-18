package com.example.akthosidle.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.akthosidle.databinding.FragmentSkillsBinding;
import com.example.akthosidle.model.Skill;
import com.example.akthosidle.model.SkillId;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SkillsFragment extends Fragment implements SkillAdapter.OnSkillClick {

    private FragmentSkillsBinding b;
    private GameViewModel vm;
    private SkillAdapter adapter;

    public SkillsFragment() {}

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        b = FragmentSkillsBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);
        vm = new ViewModelProvider(requireActivity()).get(GameViewModel.class);

        adapter = new SkillAdapter(this::onSkillClick);

        b.recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        b.recycler.setAdapter(adapter);
        b.recycler.addItemDecoration(new DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL));

        refreshList();
    }

    private void refreshList() {
        Map<SkillId, Skill> map = vm.player().skills; // already ensured in repo/player init
        List<SkillAdapter.Row> rows = new ArrayList<>();
        for (Map.Entry<SkillId, Skill> e : map.entrySet()) {
            SkillId id = e.getKey();
            Skill s   = e.getValue();

            int lvl = Math.max(1, s.level);
            int need = vm.skillReqXp(lvl);          // next-level requirement
            int cur  = Math.max(0, s.xp);
            int prog = Math.min(need, cur);

            rows.add(new SkillAdapter.Row(id, s.level, s.xp, need));
        }
        // Stable order: Combat first, then non-combat (optional: sort by enum ordinal)
        rows.sort((a,b) -> a.id.ordinal() - b.id.ordinal());
        adapter.submit(rows);
    }

    @Override
    public void onSkillClick(SkillAdapter.Row row) {
        // Placeholder: plug your training/open-detail later
        // e.g., navigate to a SkillDetailFragment
        // Toast.makeText(requireContext(), row.id.name() + " tapped", Toast.LENGTH_SHORT).show();
    }
}
