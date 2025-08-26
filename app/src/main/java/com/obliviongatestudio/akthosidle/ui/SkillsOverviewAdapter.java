package com.obliviongatestudio.akthosidle.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.obliviongatestudio.akthosidle.R;
import com.obliviongatestudio.akthosidle.domain.model.Skill;
import com.obliviongatestudio.akthosidle.domain.model.SkillId;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class SkillsOverviewAdapter extends RecyclerView.Adapter<SkillsOverviewAdapter.VH> {

    public static class Row {
        public SkillId id;
        public String name;
        public int icon;
        public int level;
        public int xpNow;
        public int xpNext;
    }

    private final LayoutInflater inflater;
    private final List<Row> rows = new ArrayList<>();

    public SkillsOverviewAdapter(Context ctx) {
        this.inflater = LayoutInflater.from(ctx);
    }

    public void submit(List<Row> list) {
        rows.clear();
        if (list != null) rows.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull
    @Override public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = inflater.inflate(R.layout.item_skill_overview, parent, false);
        return new VH(v);
    }

    @Override public void onBindViewHolder(@NonNull VH h, int pos) {
        Row r = rows.get(pos);
        h.ivIcon.setImageResource(r.icon);
        h.tvName.setText(r.name);
        h.tvLevel.setText(String.format(Locale.US, "Lv %d", r.level));

        // Progress bar: max = xp to next, progress = current xp at this level
        h.pb.setMax(Math.max(1, r.xpNext));
        h.pb.setProgress(Math.max(0, Math.min(r.xpNow, r.xpNext)));
        h.tvXpText.setText(String.format(Locale.US, "%d / %d", r.xpNow, r.xpNext));
    }

    @Override public int getItemCount() { return rows.size(); }

    static class VH extends RecyclerView.ViewHolder {
        ImageView ivIcon;
        TextView tvName, tvLevel, tvXpText;
        ProgressBar pb;
        VH(View v) {
            super(v);
            ivIcon = v.findViewById(R.id.ivIcon);
            tvName = v.findViewById(R.id.tvName);
            tvLevel= v.findViewById(R.id.tvLevel);
            tvXpText = v.findViewById(R.id.tvXpText);
            pb = v.findViewById(R.id.pbXp);
        }
    }

    /* ---------- helpers for building rows ---------- */

    public static List<Row> buildRows(Map<SkillId, Skill> map, Context ctx) {
        if (map == null) map = new EnumMap<>(SkillId.class);
        List<Row> out = new ArrayList<>();
        for (SkillId id : SkillId.values()) {
            Row r = new Row();
            r.id = id;
            r.name = prettify(id.name());
            r.icon = iconFor(id);
            Skill s = map.get(id);
            r.level = (s != null) ? s.level : 1;
            int xpAtLvl = (s != null) ? Math.max(0, s.xp) : 0; // assumes Skill.xp holds current-level xp
            r.xpNow = xpAtLvl;
            r.xpNext = reqExp(r.level); // simple formula; keep in sync with your model if you have one
            out.add(r);
        }
        return out;
    }

    private static String prettify(String enumName) {
        String lower = enumName.replace('_',' ').toLowerCase(Locale.US);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private static int iconFor(SkillId id) {
        switch (id) {
            case ATTACK:     return R.drawable.ic_gauntlet;
            case STRENGTH:   return R.drawable.ic_sword;
            case DEFENSE:    return R.drawable.ic_shield;
            case ARCHERY:    return R.drawable.ic_shield;
            case MAGIC:      return R.drawable.ic_shield;
            case HP:         return R.drawable.ic_heart;

            case WOODCUTTING:return R.drawable.ic_skill_woodcutting;
            case MINING:     return R.drawable.ic_skill_mining;
            case FISHING:    return R.drawable.ic_skill_fishing;
            case GATHERING:  return R.drawable.ic_skill_gathering;
            case HUNTING:    return R.drawable.ic_skill_hunting;
            case CRAFTING:   return R.drawable.ic_skill_crafting;
            case SMITHING:   return R.drawable.ic_skill_smithing;
            case COOKING:    return R.drawable.ic_skill_cooking;
            case ALCHEMY:    return R.drawable.ic_skill_alchemy;
            case TAILORING:  return R.drawable.ic_skill_tailoring;
            case CARPENTRY:  return R.drawable.ic_skill_carpentry;
            case ENCHANTING: return R.drawable.ic_skill_enchanting;
            case COMMUNITY:  return R.drawable.ic_skill_community;
            case HARVESTING: return R.drawable.ic_skill_harvesting;
            default:         return R.drawable.ic_skill_generic;
        }
    }

    // same shape as your player XP formula; gives 75 xp for Lv1 (matches your screenshot)
    private static int reqExp(int lvl) {
        return 50 + (lvl * 25);
    }
}
