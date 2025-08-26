package com.obliviongatestudio.akthosidle.ui;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.ToggleButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.obliviongatestudio.akthosidle.R;
import com.obliviongatestudio.akthosidle.data.dtos.InventoryItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Potions picker dialog with:
 * - Syrup action button (far left)
 * - Filters: All / Combat / Non-Combat (three ToggleButtons; mutually exclusive)
 * - Sort button (A–Z / Z–A)
 * - Grid list of potions
 *
 * Depends on GameViewModel methods: getPotions(...), usePotion(...), useSyrup()
 */
public class PotionsDialog extends DialogFragment implements PotionAdapter.OnPotionClick {

    private enum Filter { ALL, COMBAT, NONCOMBAT }

    private GameViewModel vm;
    private PotionAdapter adapter;
    private TextView tvEmpty;

    private ToggleButton tAll, tCombat, tNonCombat;
    private ImageButton btnSort, btnSyrup;

    private Filter current = Filter.ALL;
    private boolean sortAsc = true;

    public PotionsDialog() { }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        vm = new ViewModelProvider(requireActivity()).get(GameViewModel.class);

        View content = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_potions, null, false);

        // Header controls
        btnSyrup    = content.findViewById(R.id.btnSyrup);
        btnSort     = content.findViewById(R.id.btnSort);
        tAll        = content.findViewById(R.id.tAll);
        tCombat     = content.findViewById(R.id.tCombat);
        tNonCombat  = content.findViewById(R.id.tNonCombat);

        // List
        RecyclerView rv = content.findViewById(R.id.rvPotions);
        tvEmpty = content.findViewById(R.id.tvEmpty);
        rv.setLayoutManager(new GridLayoutManager(requireContext(), 3));
        adapter = new PotionAdapter(this);
        rv.setAdapter(adapter);

        // Listeners
        btnSyrup.setOnClickListener(_v -> {
            vm.useSyrup();
            dismiss();
        });

        btnSort.setOnClickListener(_v -> {
            sortAsc = !sortAsc;
            btnSort.setRotation(sortAsc ? 0f : 180f); // simple visual hint
            load();
        });

        tAll.setOnCheckedChangeListener((_b, checked) -> {
            if (checked) {
                current = Filter.ALL;
                setExclusive(tAll);
                load();
            }
        });
        tCombat.setOnCheckedChangeListener((_b, checked) -> {
            if (checked) {
                current = Filter.COMBAT;
                setExclusive(tCombat);
                load();
            }
        });
        tNonCombat.setOnCheckedChangeListener((_b, checked) -> {
            if (checked) {
                current = Filter.NONCOMBAT;
                setExclusive(tNonCombat);
                load();
            }
        });

        // Default state
        setExclusive(tAll);
        load();

        return new AlertDialog.Builder(requireContext())
                .setTitle("Potions")
                .setView(content)
                .setNegativeButton(android.R.string.cancel, (d, w) -> dismiss())
                .create();
    }

    private void setExclusive(ToggleButton active) {
        tAll.setChecked(active == tAll);
        tCombat.setChecked(active == tCombat);
        tNonCombat.setChecked(active == tNonCombat);
    }

    private void load() {
        List<InventoryItem> src;
        switch (current) {
            case COMBAT:
                src = vm.getPotions(true, false);
                break;
            case NONCOMBAT:
                src = vm.getPotions(false, true);
                break;
            case ALL:
            default:
                src = vm.getPotions(false, false);
                break;
        }

        // sort by name (A–Z or Z–A)
        Collections.sort(src, sortAsc
                ? Comparator.comparing(a -> a.name.toLowerCase())
                : (a, b) -> b.name.toLowerCase().compareTo(a.name.toLowerCase()));

        adapter.submit(new ArrayList<>(src));
        tvEmpty.setVisibility(src.isEmpty() ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onPotionClicked(InventoryItem item) {
        vm.usePotion(item.id);
        dismiss();
    }
}
