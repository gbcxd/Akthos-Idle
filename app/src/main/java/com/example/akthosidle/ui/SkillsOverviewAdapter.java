package com.example.akthosidle.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.akthosidle.R;
import com.example.akthosidle.domain.model.SkillId;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SkillsOverviewAdapter extends RecyclerView.Adapter<SkillsOverviewAdapter.VH> {

    public static class Row {
        public final SkillId id;
        public final int iconRes;
        public final String name;
        public final int level;
        public final int xp;
        public final int xpToNext;

        public Row(SkillId id, int iconRes, String name, int level, int xp, int xpToNext) {
            this.id = id;
            this.iconRes = iconRes;
            this.name = name;
            this.level = level;
            this.xp = xp;
            this.xpToNext = Math.max(1, xpToNext);
        }
    }

    private final List<Row> rows = new ArrayList<>();

    public SkillsOverviewAdapter(List<Row> initial) {
        if (initial != null) rows.addAll(initial);
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_skill_overview, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        Row r = rows.get(pos);
        h.icon.setImageResource(r.iconRes);
        h.name.setText(r.name);
        h.level.setText(String.format(Locale.US, "Lv %d", r.level));

        h.progress.setMax(r.xpToNext);
        h.progress.setProgress(Math.min(r.xp, r.xpToNext));
        h.xpText.setText(String.format(Locale.US, "%d / %d", r.xp, r.xpToNext));
    }

    @Override
    public int getItemCount() { return rows.size(); }

    static class VH extends RecyclerView.ViewHolder {
        ImageView icon;
        TextView name;
        TextView level;
        ProgressBar progress;
        TextView xpText;

        VH(@NonNull View v) {
            super(v);
            icon     = v.findViewById(R.id.ivIcon);
            name     = v.findViewById(R.id.tvName);
            level    = v.findViewById(R.id.tvLevel);
            progress = v.findViewById(R.id.pbXp);
            xpText   = v.findViewById(R.id.tvXp);
        }
    }
}
