package com.example.akthosidle.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.akthosidle.R;

public class CharacterFragment extends Fragment {

    private View scrollRoot;

    // Buff rows
    private View boxBuffs;
    private View chipCombat, chipNonCombat;
    private TextView tvCombatPct, tvNonCombatPct;

    // bottom row
    private ImageButton btnPotions;
    private View btnFood;
    private ImageView imgFood;
    private TextView tvFoodHeals, tvAtkVal, tvDefVal, tvHpVal;

    private GameViewModel vm;

    public CharacterFragment() { }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_character, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle s) {
        super.onViewCreated(v, s);

        vm = new ViewModelProvider(requireActivity()).get(GameViewModel.class);

        scrollRoot = v.findViewById(R.id.scrollRoot);

        boxBuffs      = v.findViewById(R.id.boxBuffs);
        chipCombat    = v.findViewById(R.id.chipCombat);
        chipNonCombat = v.findViewById(R.id.chipNonCombat);
        tvCombatPct   = v.findViewById(R.id.tvCombatPct);
        tvNonCombatPct= v.findViewById(R.id.tvNonCombatPct);

        btnPotions   = v.findViewById(R.id.btnPotions);
        btnFood      = v.findViewById(R.id.btnFood);
        imgFood      = v.findViewById(R.id.imgFood);
        tvFoodHeals  = v.findViewById(R.id.tvFoodHeals);
        tvAtkVal     = v.findViewById(R.id.tvAtkVal);
        tvDefVal     = v.findViewById(R.id.tvDefVal);
        tvHpVal      = v.findViewById(R.id.tvHpVal);

        btnPotions.setOnClickListener(_x ->
                new PotionsDialog().show(getParentFragmentManager(), "potions"));

        btnFood.setOnClickListener(_x ->
                new FoodPickerDialog().show(getParentFragmentManager(), "food"));

        ViewCompat.setOnApplyWindowInsetsListener(scrollRoot, (view, insets) -> {
            Insets sys = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(view.getPaddingLeft(), view.getPaddingTop(),
                    view.getPaddingRight(), sys.bottom + view.getPaddingBottom());
            return insets;
        });

        // Sample data – replace with observers from vm
        updateBuffs(23, 18);
        updateStatsChip(18, 5, 370);
        updateFoodChip("+15");
    }

    private void updateBuffs(int combatPct, int nonCombatPct) {
        if (combatPct <= 0) { chipCombat.setVisibility(View.GONE); }
        else { chipCombat.setVisibility(View.VISIBLE); tvCombatPct.setText(combatPct + "%"); }

        if (nonCombatPct <= 0) { chipNonCombat.setVisibility(View.GONE); }
        else { chipNonCombat.setVisibility(View.VISIBLE); tvNonCombatPct.setText(nonCombatPct + "%"); }

        boxBuffs.setVisibility(
                chipCombat.getVisibility() == View.GONE && chipNonCombat.getVisibility() == View.GONE
                        ? View.GONE : View.VISIBLE);
    }

    private void updateStatsChip(int atk, int def, int hp) {
        tvAtkVal.setText(String.valueOf(atk));
        tvDefVal.setText(String.valueOf(def));
        tvHpVal.setText(String.valueOf(hp));
    }

    private void updateFoodChip(String healsText) {
        tvFoodHeals.setText(healsText);
        // imgFood.setImageResource(...) if you have per-food icons
    }
}
