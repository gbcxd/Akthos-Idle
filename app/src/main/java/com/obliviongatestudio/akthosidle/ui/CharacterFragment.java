package com.obliviongatestudio.akthosidle.ui;

import android.content.Context;
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

import com.obliviongatestudio.akthosidle.R;
import com.obliviongatestudio.akthosidle.domain.model.EquipmentSlot;
import com.obliviongatestudio.akthosidle.domain.model.Item;
import com.obliviongatestudio.akthosidle.domain.model.PlayerCharacter;
import com.obliviongatestudio.akthosidle.domain.model.Stats;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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

        // Equip slot click listeners (resolved by name so there’s no compile-time ID errors)
        wireEquipmentSlots(v);

        // Observe HP → recompute full stats (equip/unequip and healing both publish HP)
        vm.repo.playerHpLive.observe(getViewLifecycleOwner(), hp -> refreshStatsAndFood());

        // First paint
        refreshStatsAndFood();

        // (Optional) if you have real buff sources, wire them here instead of sample:
        updateBuffs(0, 0); // hide both by default
    }

    // ---------- Equip UI wiring ----------

    private void wireEquipmentSlots(View root) {
        tryAttachSlot(root, new String[]{"slot_weapon","slotWeapon","weapon_slot","cellWeapon"}, EquipmentSlot.WEAPON);
        tryAttachSlot(root, new String[]{"slot_helmet","slotHelmet","helmet_slot","cellHelmet"}, EquipmentSlot.HELMET);
        tryAttachSlot(root, new String[]{"slot_amor","slotArmor","armor_slot","cellArmor"}, EquipmentSlot.ARMOR);
        tryAttachSlot(root, new String[]{"slot_pants","slotPants","pants_slot","cellPants"}, EquipmentSlot.PANTS);
        tryAttachSlot(root, new String[]{"slot_boots","slotBoots","boots_slot","cellBoots"}, EquipmentSlot.BOOTS);
        tryAttachSlot(root, new String[]{"slot_gloves","slotGloves","gloves_slot","cellGloves"}, EquipmentSlot.GLOVES);
        tryAttachSlot(root, new String[]{"slot_cape","slotCape","cape_slot","cellCape"}, EquipmentSlot.CAPE);
        tryAttachSlot(root, new String[]{"slot_ring","slotRing","ring_slot","cellRing"}, EquipmentSlot.RING);
        tryAttachSlot(root, new String[]{"slot_shield","slotShield","shield_slot","cellShield"}, EquipmentSlot.SHIELD);
    }

    private void tryAttachSlot(View root, String[] nameCandidates, EquipmentSlot slot) {
        Context ctx = root.getContext();
        for (String name : nameCandidates) {
            int id = ctx.getResources().getIdentifier(name, "id", ctx.getPackageName());
            if (id != 0) {
                View cell = root.findViewById(id);
                if (cell != null) {
                    cell.setOnClickListener(v -> showEquipPicker(slot));
                    return; // attached for this slot
                }
            }
        }
        // If none found, silently skip; layout simply doesn't have that slot.
    }

    private void showEquipPicker(EquipmentSlot slot) {
        PlayerCharacter pc = vm.player();

        // Build choices
        final List<String> labels = new ArrayList<>();
        final List<Runnable> actions = new ArrayList<>();

        // Currently equipped?
        String equipped = pc.equipment != null ? pc.equipment.get(slot) : null;
        if (equipped != null) {
            String name = nameOf(equipped);
            labels.add("Unequip (" + name + ")");
            actions.add(() -> {
                vm.unequip(slot);
                refreshStatsAndFood();
            });
        }

        // All bag items matching this slot
        for (Map.Entry<String, Integer> e : pc.bag.entrySet()) {
            if (e.getValue() == null || e.getValue() <= 0) continue;
            Item it = vm.repo.getItem(e.getKey());
            if (it == null) continue;
            if (vm.repo.slotOf(it) != slot) continue;
            String label = String.format(Locale.US, "%s ×%d", nameOf(it.id), e.getValue());
            String itemId = it.id;
            labels.add(label);
            actions.add(() -> {
                vm.equip(itemId);
                refreshStatsAndFood();
            });
        }

        if (labels.isEmpty()) {
            vm.repo.toast("No gear for " + pretty(slot.name()));
            return;
        }

        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Equip " + pretty(slot.name()))
                .setItems(labels.toArray(new CharSequence[0]), (d, which) -> actions.get(which).run())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private String nameOf(String itemId) {
        Item def = vm.repo.getItem(itemId);
        return def != null && def.name != null ? def.name : itemId;
    }

    private static String pretty(String enumName) {
        String s = enumName.replace('_',' ').toLowerCase(Locale.US);
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    // ---------- Stats / Food ----------

    private void refreshStatsAndFood() {
        PlayerCharacter pc = vm.player();
        Stats total = pc.totalStats(vm.repo.gearStats(pc));

        tvAtkVal.setText(String.valueOf(total.attack));
        tvDefVal.setText(String.valueOf(total.defense));
        tvHpVal.setText(String.valueOf(Math.max(1, total.health)));

        // Quick food heal preview (if set)
        String qf = vm.getQuickFoodId();
        String heals = "(none)";
        if (qf != null) {
            Item it = vm.repo.getItem(qf);
            if (it != null && it.heal != null && it.heal > 0) {
                heals = "+" + it.heal + " HP";
            } else {
                heals = it != null ? it.name : qf;
            }
        }
        updateFoodChip(heals);
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

    private void updateFoodChip(String healsText) {
        tvFoodHeals.setText(healsText);
        // imgFood.setImageResource(...) if you have per-food icons
    }
}
