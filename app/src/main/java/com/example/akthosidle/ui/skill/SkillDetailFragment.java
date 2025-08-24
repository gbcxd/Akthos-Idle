package com.example.akthosidle.ui.skill;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.akthosidle.R;
import com.example.akthosidle.domain.model.Action;
import com.example.akthosidle.domain.model.PlayerCharacter;
import com.example.akthosidle.domain.model.SkillId;
import com.example.akthosidle.engine.ActionEngine;
import com.example.akthosidle.ui.GameViewModel;

import java.util.ArrayList;
import java.util.List;


public class SkillDetailFragment extends Fragment {

    private static final String ARG_SKILL_ID = "skillId";

    private TextView tvSkillTitle, tvStatus, tvTimer, tvSelectedAction, tvActionReq;
    private ProgressBar progressBar;
    private ImageButton btnPickAction;
    private Button btnToggle;

    private GameViewModel vm;

    private SkillId skillId;
    private Action selectedAction;
    private boolean running = false;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_skill_detail, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle s) {
        super.onViewCreated(v, s);
        tvSkillTitle     = v.findViewById(R.id.tvSkillTitle);
        tvStatus         = v.findViewById(R.id.tvStatus);
        tvTimer          = v.findViewById(R.id.tvTimer);
        progressBar      = v.findViewById(R.id.action_progress);
        btnPickAction    = v.findViewById(R.id.btn_pick_action);
        tvSelectedAction = v.findViewById(R.id.tvSelectedAction);
        tvActionReq      = v.findViewById(R.id.tvActionReq);
        btnToggle        = v.findViewById(R.id.btn_toggle);

        vm = new ViewModelProvider(requireActivity()).get(GameViewModel.class);

        String arg = requireArguments().getString(ARG_SKILL_ID, "MINING");
        try { skillId = SkillId.valueOf(arg); } catch (IllegalArgumentException e) { skillId = SkillId.MINING; }

        PlayerCharacter pc = vm.player();
        int lvl = pc.getSkillLevel(skillId);
        tvSkillTitle.setText(capitalize(skillId.name().toLowerCase()) + " (Lv " + lvl + ")");

        // Initial selection: saved last-picked OR best unlocked fallback
        applyInitialSelection();

        btnPickAction.setOnClickListener(vw -> openActionPicker());
        btnToggle.setOnClickListener(vw -> {
            if (running) stopLoop();
            else {
                if (selectedAction == null) {
                    tvStatus.setText(getString(R.string.pick_a_resource_first));
                    return;
                }
                startLoop();
            }
        });
    }

    @Override public void onStart() {
        super.onStart();
        // Attach listener to shared engine (no new engine here)
        vm.setGatherListener(new ActionEngine.Listener() {
            @Override public void onTick(@NonNull Action action, int percent, long elapsed, long remaining) {
                if (!isAdded()) return;
                progressBar.setProgress(percent);
                tvTimer.setText(percent + "%");
            }
            @Override public void onActionComplete(@NonNull Action action, boolean leveledUp) {
                if (!isAdded()) return;
                tvStatus.setText((leveledUp ? getString(R.string.level_up_short, vm.player().getSkillLevel(skillId))
                        : getString(R.string.completed_fmt, action.name)));
                if (leveledUp) {
                    int lvl = vm.player().getSkillLevel(skillId);
                    tvSkillTitle.setText(capitalize(skillId.name().toLowerCase()) + " (Lv " + lvl + ")");
                }
            }
            @Override public void onLoopStateChanged(boolean runningNow) {
                if (!isAdded()) return;
                running = runningNow;
                btnToggle.setText(runningNow ? getString(R.string.stop) : getString(R.string.play));
                btnToggle.setEnabled(true);
                if (!runningNow) {
                    tvStatus.setText(getString(R.string.idle));
                    tvTimer.setText("0%");
                    progressBar.setProgress(0);
                }
            }
        });
        // reflect actual state if restored
        running = vm.isGatherRunning();
        btnToggle.setText(running ? getString(R.string.stop) : getString(R.string.play));
    }

    @Override public void onStop() {
        super.onStop();
        vm.clearGatherListener(); // keep engine running if user navigates away
    }

    private void startLoop() {
        tvStatus.setText(getString(R.string.gathering_fmt, selectedAction.name));
        btnToggle.setEnabled(false);
        vm.startGather(selectedAction);
    }

    private void stopLoop() {
        vm.stopGather();
        running = false;
        btnToggle.setText(getString(R.string.play));
        btnToggle.setEnabled(true);
        tvStatus.setText(getString(R.string.idle));
        tvTimer.setText("0%");
        progressBar.setProgress(0);
    }

    private void applyInitialSelection() {
        int level = vm.player().getSkillLevel(skillId);
        Action saved = vm.repo.getLastPickedAction(skillId);
        if (saved != null && vm.repo.isUnlocked(saved, level)) {
            setSelected(saved);
            return;
        }
        Action best = vm.repo.bestUnlockedFor(skillId, level);
        if (best != null) setSelected(best);
    }

    private void setSelected(Action a) {
        selectedAction = a;
        if (tvSelectedAction != null) tvSelectedAction.setText(a.name);
        if (tvActionReq != null) tvActionReq.setText(getString(R.string.req_and_duration_fmt, a.reqLevel, a.durationMs / 1000));
        // Save last-picked for this skill
        vm.repo.setLastPickedAction(skillId, a.id);
    }

    private void openActionPicker() {
        List<Action> all = vm.repo.getActionsBySkill(skillId);
        int level = vm.player().getSkillLevel(skillId);

        List<Action> unlocked = new ArrayList<>();
        for (Action a : all) if (vm.repo.isUnlocked(a, level)) unlocked.add(a);

        if (unlocked.isEmpty()) {
            tvStatus.setText(getString(R.string.no_actions_available));
            return;
        }

        ActionPickerDialog dlg = ActionPickerDialog.newInstance(unlocked, action -> setSelected(action));
        dlg.show(getChildFragmentManager(), "action_picker");
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    // ---------- Grid dialog ----------
    public static class ActionPickerDialog extends DialogFragment {
        public interface OnPick { void onPick(Action a); }
        private final List<Action> data = new ArrayList<>();
        private OnPick onPick;

        public static ActionPickerDialog newInstance(List<Action> in, OnPick pick) {
            ActionPickerDialog f = new ActionPickerDialog();
            f.data.addAll(in);
            f.onPick = pick;
            return f;
        }

        @NonNull @Override
        public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
            Dialog d = new Dialog(requireContext());
            d.setContentView(R.layout.dialog_actions_grid);
            RecyclerView rv = d.findViewById(R.id.recycler_actions);
            rv.setLayoutManager(new GridLayoutManager(requireContext(), 3));
            rv.setAdapter(new ActionGridAdapter(data, a -> {
                if (onPick != null) onPick.onPick(a);
                d.dismiss();
            }));
            return d;
        }
    }

    private static class ActionGridAdapter extends RecyclerView.Adapter<ActionGridAdapter.VH> {
        interface Click { void onClick(Action a); }
        private final List<Action> list; private final Click click;
        ActionGridAdapter(List<Action> list, Click click) { this.list = list; this.click = click; }

        static class VH extends RecyclerView.ViewHolder {
            ImageView img; TextView title;
            VH(@NonNull View v) { super(v); img = v.findViewById(R.id.img); title = v.findViewById(R.id.title); }
        }

        @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_action_icon, parent, false);
            return new VH(v);
        }
        @Override public void onBindViewHolder(@NonNull VH h, int pos) {
            Action a = list.get(pos);
            h.title.setText(a.name);
            h.itemView.setOnClickListener(v -> click.onClick(a));
        }
        @Override public int getItemCount() { return list.size(); }
    }
}
