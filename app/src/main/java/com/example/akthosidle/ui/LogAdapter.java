package com.example.akthosidle.ui;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.akthosidle.R;

import java.util.ArrayList;
import java.util.List;

public class LogAdapter extends RecyclerView.Adapter<LogAdapter.VH> {

    private final List<String> lines = new ArrayList<>();

    public void submit(List<String> newLines) {
        lines.clear();
        if (newLines != null) lines.addAll(newLines);
        notifyDataSetChanged();
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        TextView tv = (TextView) LayoutInflater.from(parent.getContext())
                .inflate(R.layout.simple_list_item_1, parent, false);
        return new VH(tv);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        holder.text.setText(lines.get(position));
    }

    @Override
    public int getItemCount() {
        return lines.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView text;
        VH(@NonNull TextView tv) { super(tv); this.text = tv; }
    }
}
