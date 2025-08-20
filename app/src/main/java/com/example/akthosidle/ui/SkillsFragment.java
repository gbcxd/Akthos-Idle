package com.example.akthosidle.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.akthosidle.R;
import com.example.akthosidle.domain.model.SkillId;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Shows the list of skills. Clicking one navigates to SkillDetailFragment,
 * passing the selected skill id via the "skill_id" argument.
 */
public class SkillsFragment extends Fragment {

    private RecyclerView recycler;
    private SkillAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        return inflater.inflate(R.layout.fragment_skills, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);

        recycler = v.findViewById(R.id.recycler);
        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        recycler.addItemDecoration(new DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL));

        // Build a simple list of all skills (you can filter/order later)
        List<String> skills = new ArrayList<>();
        for (SkillId s : SkillId.values()) {
            skills.add(s.name());
        }

        adapter = new SkillAdapter(skills, this::onSkillClick);
        recycler.setAdapter(adapter);
    }

    private void onSkillClick(String skillId) {
        Bundle args = new Bundle();
        args.putString("skill_id", skillId);
        NavHostFragment.findNavController(this)
                .navigate(R.id.action_nav_skills_to_skillDetail, args);
    }

    // --- Minimal inline adapter (use your own if you already have one) ---
    private static class SkillAdapter extends RecyclerView.Adapter<SkillViewHolder> {
        interface OnClick { void onClick(String skillId); }

        private final List<String> data;
        private final OnClick click;

        SkillAdapter(List<String> data, OnClick click) {
            this.data = data != null ? data : Arrays.asList();
            this.click = click;
        }

        @NonNull @Override
        public SkillViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View item = LayoutInflater.from(parent.getContext())
                    .inflate(android.R.layout.simple_list_item_1, parent, false);
            return new SkillViewHolder(item);
        }

        @Override
        public void onBindViewHolder(@NonNull SkillViewHolder h, int position) {
            final String skillId = data.get(position);
            h.bind(skillId);
            h.itemView.setOnClickListener(v -> {
                if (click != null) click.onClick(skillId);
            });
        }

        @Override public int getItemCount() { return data.size(); }
    }

    private static class SkillViewHolder extends RecyclerView.ViewHolder {
        private final android.widget.TextView tv;
        SkillViewHolder(@NonNull View itemView) {
            super(itemView);
            tv = itemView.findViewById(android.R.id.text1);
        }
        void bind(String skillId) {
            tv.setText(skillId);
        }
    }
}
