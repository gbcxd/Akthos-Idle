package com.obliviongatestudio.akthosidle.ui;

import android.app.Dialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.obliviongatestudio.akthosidle.R;
import com.obliviongatestudio.akthosidle.domain.model.PlayerCharacter;
import com.obliviongatestudio.akthosidle.domain.model.SkillId;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;

/** Long-press Skills tab => shows all skills with XP bars (cur/req). */
public class SkillsOverviewDialog extends DialogFragment {

    private GameViewModel vm;
    private Adapter adapter;
    private final Handler tick = new Handler(Looper.getMainLooper());
    private final Runnable refresher = new Runnable() {
        @Override public void run() {
            if (adapter != null) adapter.setData(buildRows());
            tick.postDelayed(this, 1000);
        }
    };

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        vm = new ViewModelProvider(requireActivity()).get(GameViewModel.class);

        View root = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_skills_overview, null, false);

        RecyclerView rv = root.findViewById(R.id.rvSkills);
        rv.setLayoutManager(new GridLayoutManager(requireContext(), 2));
        adapter = new Adapter();
        rv.setAdapter(adapter);
        adapter.setData(buildRows());

        return new AlertDialog.Builder(requireContext())
                .setTitle("Skills")
                .setView(root)
                .setPositiveButton("Close", null)
                .create();
    }

    @Override public void onResume() {
        super.onResume();
        tick.post(refresher);
    }

    @Override public void onPause() {
        super.onPause();
        tick.removeCallbacksAndMessages(null);
    }

    /* ---------- data shaping ---------- */

    private List<Row> buildRows() {
        PlayerCharacter pc = vm.player();
        EnumMap<SkillId, Integer> xpMap =
                (pc.skills != null) ? pc.skills : new EnumMap<>(SkillId.class);

        List<Row> rows = new ArrayList<>();
        for (SkillId id : SkillId.values()) {
            int xp    = xpMap.getOrDefault(id, 0);
            int lvl   = PlayerCharacter.levelForExp(xp);
            int cur   = PlayerCharacter.xpIntoLevel(xp, lvl);
            int req   = PlayerCharacter.xpForNextLevel(lvl);

            // Optional: at max level, pin bar to full to avoid odd visuals
            if (req <= 0) { req = 1; cur = 1; }

            rows.add(new Row(id, iconFor(id), pretty(id.name()), lvl, cur, req));
        }
        return rows;
    }

    private static String pretty(String enumName) {
        String lower = enumName.replace('_',' ').toLowerCase(Locale.US);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private int iconFor(SkillId id) {
        switch (id) {
            case ATTACK: return R.drawable.ic_gauntlet;
            case STRENGTH: return R.drawable.ic_sword;
            case DEFENSE: return R.drawable.ic_shield;
            case ARCHERY: return R.drawable.ic_shield;
            case MAGIC: return R.drawable.ic_shield;
            case HP: return R.drawable.ic_heart;

            case WOODCUTTING: return R.drawable.ic_skill_woodcutting;
            case MINING:      return R.drawable.ic_skill_mining;
            case FISHING:     return R.drawable.ic_skill_fishing;
            case GATHERING:   return R.drawable.ic_skill_gathering;
            case HUNTING:     return R.drawable.ic_skill_hunting;
            case CRAFTING:    return R.drawable.ic_skill_crafting;
            case SMITHING:    return R.drawable.ic_skill_smithing;
            case COOKING:     return R.drawable.ic_skill_cooking;
            case ALCHEMY:     return R.drawable.ic_skill_alchemy;
            case TAILORING:   return R.drawable.ic_skill_tailoring;
            case CARPENTRY:   return R.drawable.ic_skill_carpentry;
            case ENCHANTING:  return R.drawable.ic_skill_enchanting;
            case COMMUNITY:   return R.drawable.ic_skill_community;
            case HARVESTING:  return R.drawable.ic_skill_harvesting;

            default: return R.drawable.ic_skill_generic;
        }
    }

    /* ---------- adapter ---------- */

    private static class Row {
        final SkillId id; final int icon; final String name;
        final int level; final int cur; final int req;
        Row(SkillId id, int icon, String name, int level, int cur, int req) {
            this.id=id; this.icon=icon; this.name=name; this.level=level; this.cur=cur; this.req=req;
        }
    }

    private static class VH extends RecyclerView.ViewHolder {
        ImageView icon;
        TextView name, level, xpText;
        ProgressBar bar;
        VH(@NonNull View v) {
            super(v);
            icon = v.findViewById(R.id.ivIcon);
            name = v.findViewById(R.id.tvName);
            level = v.findViewById(R.id.tvLevel);
            xpText = v.findViewById(R.id.tvXpText);
            bar = v.findViewById(R.id.pbXp);
        }
    }

    private class Adapter extends RecyclerView.Adapter<VH> {
        private final List<Row> data = new ArrayList<>();

        void setData(List<Row> rows) {
            data.clear();
            data.addAll(rows);
            notifyDataSetChanged();
        }

        @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup p, int vtype) {
            View v = LayoutInflater.from(p.getContext())
                    .inflate(R.layout.row_skill_overview, p, false);
            return new VH(v);
        }

        @Override public void onBindViewHolder(@NonNull VH h, int pos) {
            Row r = data.get(pos);
            h.icon.setImageResource(r.icon);
            h.name.setText(r.name);
            h.level.setText(String.format(Locale.US, "Lv %d", r.level));

            // Progress bar + text uses "xp into level / xp for next level"
            int req = Math.max(1, r.req);
            int cur = Math.max(0, Math.min(r.cur, req));
            h.bar.setMax(req);
            h.bar.setProgress(cur);
            h.xpText.setText(String.format(Locale.US, "%d / %d", cur, req));
        }

        @Override public int getItemCount() { return data.size(); }
    }
}
