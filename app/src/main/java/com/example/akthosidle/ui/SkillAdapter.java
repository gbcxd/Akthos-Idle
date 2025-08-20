package com.example.akthosidle.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.akthosidle.R;

import java.util.List;

public class SkillAdapter extends RecyclerView.Adapter<SkillAdapter.VH> {

    public interface OnSkillClick {
        void onClick(SkillRow row);
    }

    public static class SkillRow {
        public final String id;    // SkillId enum name string (e.g., "ATTACK")
        public final String name;  // Display name
        public final int level;    // Current level
        public final int iconRes;  // Drawable res id

        public SkillRow(String id, String name, int level, int iconRes) {
            this.id = id; this.name = name; this.level = level; this.iconRes = iconRes;
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
        h.itemView.setOnClickListener(v -> {
            if (onClick != null) onClick.onClick(r);
        });
    }

    @Override public int getItemCount() { return data.size(); }

    static class VH extends RecyclerView.ViewHolder {
        ImageView imgIcon;
        TextView tvName, tvLevel;
        VH(@NonNull View itemView) {
            super(itemView);
            imgIcon = itemView.findViewById(R.id.imgIcon);
            tvName  = itemView.findViewById(R.id.tvName);
            tvLevel = itemView.findViewById(R.id.tvLevel);
        }
    }
}
