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
import androidx.lifecycle.MutableLiveData;
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

import androidx.lifecycle.ViewModelProvider;

public class SkillDetailFragment extends Fragment {

    private static final String ARG_SKILL_ID = "skillId";

    private TextView tvSkillTitle, tvStatus, tvTimer, tvSelectedAction, tvActionReq;
    private ProgressBar progressBar;
    private ImageButton btnPickAction;
    private Button btnToggle;

    // Use the shared VM (and its single Repo instance)
    private GameViewModel vm;
    private ActionEngine engine;

    public final MutableLiveData<Boolean> battleLive = new MutableLiveData<>(false);

    private SkillId skillId;
    private Action selectedAction;
    private boolean running = false;

    @Nullable @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_skill_detail, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);

        // --- bind views ---
        tvSkillTitle     = v.findViewById(R.id.tvSkillTitle);
        tvStatus         = v.findViewById(R.id.tvStatus);
        tvTimer          = v.findViewById(R.id.tvTimer);
        progressBar      = v.findViewById(R.id.action_progress);
        btnPickAction    = v.findViewById(R.id.btn_pick_action);
        tvSelectedAction = v.findViewById(R.id.tvSelectedAction);
        tvActionReq      = v.findViewById(R.id.tvActionReq);
        btnToggle        = v.findViewById(R.id.btn_toggle);

        // Shared VM / Repo
        vm = new ViewModelProvider(requireActivity()).get(GameViewModel.class);

        // Args
        String arg = requireArguments().getString(ARG_SKILL_ID, "MINING");
        try { skillId = SkillId.valueOf(arg); } catch (IllegalArgumentException e) { skillId = SkillId.MINING; }

        // Title "Mining (Lv X)"
        PlayerCharacter pc = vm.player();
        int lvl = pc.getSkillLevel(skillId);
        tvSkillTitle.setText(capitalize(skillId.name().toLowerCase()) + " (Lv " + lvl + ")");

        btnPickAction.setOnClickListener(vw -> openActionPicker());

        btnToggle.setOnClickListener(vw -> {
            if (running) {
                stopLoop();
            } else {
                if (selectedAction == null) {
                    tvStatus.setText(getString(R.string.pick_a_resource_first));
                    return;
                }
                startLoop();
            }
        });
    }

    private void startLoop() {
        if (engine == null) {
            // Important: pass the shared repo from VM
            engine = new ActionEngine(requireContext().getApplicationContext(), vm.repo);
            engine.setListener(new ActionEngine.Listener() {
                @Override
                public void onTick(@NonNull Action action, int progressPercent, long elapsedMs, long remainingMs) {
                    if (!isAdded()) return;
                    progressBar.setProgress(progressPercent);
                    tvTimer.setText(progressPercent + "%");
                }

                @Override
                public void onActionComplete(@NonNull Action action, boolean leveledUp) {
                    if (!isAdded()) return;
                    tvStatus.setText((leveledUp ? "Level up! " : "Completed: ") + action.name);
                    if (leveledUp) {
                        int lvl = vm.player().getSkillLevel(skillId);
                        tvSkillTitle.setText(capitalize(skillId.name().toLowerCase()) + " (Lv " + lvl + ")");
                    }
                }

                @Override
                public void onLoopStateChanged(boolean runningNow) {
                    if (!isAdded()) return;
                    running = runningNow;
                    btnToggle.setText(runningNow ? getString(R.string.stop) : getString(R.string.play));
                    btnToggle.setEnabled(true);

                    // Update global gathering flag for the FAB logic
                    if (runningNow) vm.repo.startGathering(skillId);
                    else           vm.repo.stopGathering();

                    if (!runningNow) {
                        tvStatus.setText(getString(R.string.idle));
                        tvTimer.setText("0%");
                        progressBar.setProgress(0);
                    }
                }
            });
        }

        tvStatus.setText(getString(R.string.gathering_fmt, selectedAction.name));
        btnToggle.setEnabled(false); // will re-enable in onLoopStateChanged(true)
        engine.startLoop(selectedAction); // infinite until stop()
    }

    private void stopLoop() {
        if (engine != null) engine.stop();
        running = false;
        btnToggle.setText(getString(R.string.play));
        btnToggle.setEnabled(true);
        tvStatus.setText(getString(R.string.idle));
        tvTimer.setText("0%");
        progressBar.setProgress(0);

        // Clear global gathering flag (safety)
        vm.repo.stopGathering();
    }

    @Override
    public void onStop() {
        super.onStop();
        if (engine != null) engine.stop(); // stop worker thread cleanly
        running = false;
        if (btnToggle != null) btnToggle.setText(getString(R.string.play));
        vm.repo.stopGathering(); // safety so FAB hides if you leave the screen
    }

    private void openActionPicker() {
        List<Action> all = vm.repo.getActionsBySkill(skillId);
        int level = vm.player().getSkillLevel(skillId);

        // Filter unlocked
        List<Action> unlocked = new ArrayList<>();
        for (Action a : all) {
            if (a != null && a.reqLevel <= level) unlocked.add(a);
        }

        if (unlocked.isEmpty()) {
            tvStatus.setText(getString(R.string.no_actions_available));
            return;
        }

        ActionPickerDialog dlg = ActionPickerDialog.newInstance(unlocked, action -> {
            selectedAction = action;
            tvSelectedAction.setText(action.name);
            tvActionReq.setText(
                    getString(R.string.req_and_duration_fmt, action.reqLevel, action.durationMs / 1000)
            );
        });
        dlg.show(getChildFragmentManager(), "action_picker");
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    // ---------- Grid dialog ----------
    public static class ActionPickerDialog extends DialogFragment {
        public interface OnPick { void onPick(Action a); }

        private static final String KEY_COUNT = "count";
        private final List<Action> data = new ArrayList<>();
        private OnPick onPick;

        public static ActionPickerDialog newInstance(List<Action> in, OnPick pick) {
            ActionPickerDialog f = new ActionPickerDialog();
            f.data.addAll(in);
            f.onPick = pick;
            Bundle b = new Bundle();
            b.putInt(KEY_COUNT, in.size());
            f.setArguments(b);
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

    // ---------- Grid adapter ----------
    private static class ActionGridAdapter extends RecyclerView.Adapter<ActionGridAdapter.VH> {
        interface Click { void onClick(Action a); }
        private final List<Action> list;
        private final Click click;

        ActionGridAdapter(List<Action> list, Click click) {
            this.list = list; this.click = click;
        }

        static class VH extends RecyclerView.ViewHolder {
            ImageView img; TextView title;
            VH(@NonNull View v) {
                super(v);
                img = v.findViewById(R.id.img);
                title = v.findViewById(R.id.title);
            }
        }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_action_icon, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            Action a = list.get(pos);
            h.title.setText(a.name);
            // If/when you add Action icon: h.img.setImageResource(a.iconRes);
            h.itemView.setOnClickListener(v -> click.onClick(a));
        }

        @Override
        public int getItemCount() { return list.size(); }
    }

    // (unused, legacy from earlier pattern — safe to remove if not needed)
    public boolean isBattleActive() {
        Boolean v = battleLive.getValue();
        return v != null && v;
    }
    public void setBattleActive(boolean active) {
        Boolean cur = battleLive.getValue();
        if (cur == null || cur != active) battleLive.setValue(active);
    }
}
