package com.obliviongatestudio.akthosidle.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.obliviongatestudio.akthosidle.R;

import java.util.List;

public class SkillAdapter extends RecyclerView.Adapter<SkillAdapter.VH> {

    public interface OnSkillClick {
        void onClick(SkillRow row);
    }

    public static class SkillRow {
        public final String id;      // SkillId enum name string (e.g., "ATTACK")
        public final String name;    // Display name
        public final int level;      // Current level
        public final int iconRes;    // Drawable res id

        // Optional progress (XP into current level / needed for next)
        public final int progress;      // e.g., 120
        public final int progressMax;   // e.g., 200

        /** Original 4-arg constructor (backwards compatible). */
        public SkillRow(String id, String name, int level, int iconRes) {
            this.id = id;
            this.name = name;
            this.level = level;
            this.iconRes = iconRes;
            this.progress = 0;
            this.progressMax = 0;
        }

        /** New 6-arg constructor used by SkillsFragment (id, name, iconRes, level, into, need). */
        public SkillRow(String id, String name, int iconRes, int level, int progress, int progressMax) {
            this.id = id;
            this.name = name;
            this.level = level;
            this.iconRes = iconRes;
            this.progress = progress;
            this.progressMax = progressMax;
        }
    }

    private final List<SkillRow> data;
    private final OnSkillClick onClick;

    public SkillAdapter(List<SkillRow> data, OnSkillClick onClick) {
        this.data = data;
        this.onClick = onClick;
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.row_skill, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        SkillRow r = data.get(pos);
        h.tvName.setText(r.name);
        h.tvLevel.setText("Level " + r.level);
        h.imgIcon.setImageResource(r.iconRes);

        if (r.progressMax > 0) {
            h.pbXp.setMax(Math.max(1, r.progressMax));
            h.pbXp.setProgress(Math.max(0, Math.min(r.progress, r.progressMax)));
            h.tvXp.setText(r.progress + " / " + r.progressMax);
            h.pbXp.setVisibility(View.VISIBLE);
            h.tvXp.setVisibility(View.VISIBLE);
        } else {
            h.pbXp.setVisibility(View.GONE);
            h.tvXp.setVisibility(View.GONE);
        }

        h.itemView.setOnClickListener(v -> {
            if (onClick != null) onClick.onClick(r);
        });
    }

    @Override public int getItemCount() { return data.size(); }

    static class VH extends RecyclerView.ViewHolder {
        ImageView imgIcon;
        TextView tvName, tvLevel, tvXp;
        ProgressBar pbXp;
        VH(@NonNull View itemView) {
            super(itemView);
            imgIcon = itemView.findViewById(R.id.imgIcon);
            tvName  = itemView.findViewById(R.id.tvName);
            tvLevel = itemView.findViewById(R.id.tvLevel);
            pbXp    = itemView.findViewById(R.id.pbXp);
            tvXp    = itemView.findViewById(R.id.tvXp);
        }
    }
}
