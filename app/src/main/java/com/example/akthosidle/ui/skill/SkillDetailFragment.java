package com.example.akthosidle.ui.skill;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.akthosidle.R;
import com.example.akthosidle.data.repo.GameRepository;
import com.example.akthosidle.domain.model.Action;
import com.example.akthosidle.domain.model.SkillId;

import java.util.ArrayList;
import java.util.List;

/**
 * Shows details for a single skill and lets the player run an action.
 * Expects a nav argument "skill_id" (string matching SkillId enum name).
 */
public class SkillDetailFragment extends Fragment {

    private TextView tvSkillTitle, tvStatus, tvTimer;
    private ProgressBar progressBar;
    private Spinner spinner;
    private Button btnStart, btnCancel;

    private GameRepository repo;
    private List<Action> actionsForSkill = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_skill_detail, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);

        // --- bind views ---
        tvSkillTitle = v.findViewById(R.id.tvSkillTitle);
        tvStatus     = v.findViewById(R.id.tvStatus);
        tvTimer      = v.findViewById(R.id.tvTimer);
        progressBar  = v.findViewById(R.id.action_progress);
        spinner      = v.findViewById(R.id.spinner_actions);
        btnStart     = v.findViewById(R.id.btn_start);
        btnCancel    = v.findViewById(R.id.btn_cancel);

        // --- repo & data ---
        repo = new GameRepository(requireContext().getApplicationContext());
        repo.loadDefinitions();
        repo.loadActionsFromAssets();
        repo.loadOrCreatePlayer();

        String skillArg = getArguments() != null ? getArguments().getString("skill_id", "MINING") : "MINING";
        tvSkillTitle.setText(skillArg);

        SkillId skill = SkillId.valueOf(skillArg);
        actionsForSkill = repo.getActionsBySkill(skill);

        // Populate spinner with action names
        List<String> actionNames = new ArrayList<>();
        for (Action a : actionsForSkill) actionNames.add(a.name != null ? a.name : a.id);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_spinner_dropdown_item,
                actionNames
        );
        spinner.setAdapter(adapter);

        // --- button wiring (stubbed for now) ---
        btnCancel.setEnabled(false);
        btnStart.setOnClickListener(view -> {
            int idx = spinner.getSelectedItemPosition();
            if (idx < 0 || idx >= actionsForSkill.size()) return;
            Action a = actionsForSkill.get(idx);

            // Simple demo: show status and a fake progress tick
            tvStatus.setText("Doing: " + (a.name != null ? a.name : a.id));
            progressBar.setProgress(0);
            tvTimer.setText("0%");
            btnStart.setEnabled(false);
            btnCancel.setEnabled(true);

            // Fake progress for now (replace with ActionEngine later)
            v.postDelayed(new Runnable() {
                int p = 0;
                @Override public void run() {
                    if (!isAdded()) return;
                    p += 10;
                    progressBar.setProgress(p);
                    tvTimer.setText(p + "%");
                    if (p < 100 && btnCancel.isEnabled()) {
                        v.postDelayed(this, 200); // ~2s total; adjust as you like
                    } else {
                        tvStatus.setText("Done");
                        btnStart.setEnabled(true);
                        btnCancel.setEnabled(false);
                    }
                }
            }, 200);
        });

        btnCancel.setOnClickListener(view -> {
            // Stop the fake loop by just disabling cancel—runner checks it
            btnCancel.setEnabled(false);
            tvStatus.setText("Cancelled");
            btnStart.setEnabled(true);
        });
    }
}
