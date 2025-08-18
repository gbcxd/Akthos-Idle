package com.example.akthosidle.ui;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.akthosidle.R;
import com.example.akthosidle.databinding.RowSkillBinding;
import com.example.akthosidle.model.SkillId;

import java.util.ArrayList;
import java.util.List;

public class SkillAdapter extends RecyclerView.Adapter<SkillAdapter.VH> {

    public static class Row {
        public final SkillId id;
        public final int level;
        public final int xp;
        public final int nextReq;

        public Row(SkillId id, int level, int xp, int nextReq) {
            this.id = id; this.level = level; this.xp = xp; this.nextReq = nextReq;
        }
    }

    public interface OnSkillClick {
        void onSkillClick(Row row);
    }

    private final List<Row> items = new ArrayList<>();
    private final OnSkillClick click;

    public SkillAdapter(OnSkillClick click) { this.click = click; }

    public void submit(List<Row> rows) {
        items.clear();
        items.addAll(rows);
        notifyDataSetChanged();
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        RowSkillBinding b = RowSkillBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new VH(b);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        Row r = items.get(pos);
        h.bind(r, click);
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        private final RowSkillBinding b;
        VH(RowSkillBinding b) {
            super(b.getRoot());
            this.b = b;
        }

        void bind(Row r, OnSkillClick click) {
            // Icon (use your own drawables if you have them; fallback generic)
            int iconRes = iconFor(r.id);
            b.imgIcon.setImageResource(iconRes);

            // Title & subtitle
            b.tvName.setText(labelFor(r.id));
            b.tvSub.setText("Lv " + Math.max(1, r.level) + " • " + r.xp + " / " + r.nextReq + " XP");

            // Progress
            b.bar.setMax(Math.max(1, r.nextReq));
            b.bar.setProgress(Math.min(r.nextReq, Math.max(0, r.xp)));

            b.getRoot().setOnClickListener(_v -> {
                if (click != null) click.onSkillClick(r);
            });
        }

        private static int iconFor(SkillId id) {
            switch (id) {
                case ATTACK:      return R.drawable.ic_sword;
                case STRENGTH:    return R.drawable.ic_gauntlet;
                case DEFENSE:     return R.drawable.ic_shield;
                case HP:          return R.drawable.ic_heart;
                case WOODCUTTING: return R.drawable.ic_tree;
                case MINING:      return R.drawable.ic_skill_smithing;
                case FISHING:     return R.drawable.ic_skill_fishing;
                case GATHERING:   return R.drawable.ic_skill_gathering;
                case HUNTING:     return R.drawable.ic_skill_harvesting;
                case CRAFTING:    return R.drawable.ic_skill_crafting;
                case SMITHING:    return R.drawable.ic_skill_smithing;
                case COOKING:     return R.drawable.ic_skill_cooking;
                case ALCHEMY:     return R.drawable.ic_flask;
                case TAILORING:   return R.drawable.ic_skill_tailoring;
                case CARPENTRY:   return R.drawable.ic_skill_carpentry;
                case ENCHANTING:  return R.drawable.ic_skill_enchanting;
                case COMMUNITY:   return R.drawable.ic_skill_community;
                case HARVESTING:  return R.drawable.ic_skill_harvesting;
                default:          return android.R.drawable.ic_menu_info_details;
            }
        }

        private static String labelFor(SkillId id) {
            // Human-friendly capitalization
            String raw = id.name().replace('_', ' ').toLowerCase();
            return Character.toUpperCase(raw.charAt(0)) + raw.substring(1);
        }
    }
}
