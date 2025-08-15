package com.example.akthosidle.ui;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.akthosidle.R;
import com.example.akthosidle.model.EquipmentSlot;
import com.example.akthosidle.model.Item;
import com.example.akthosidle.model.PlayerCharacter;
import com.example.akthosidle.model.SkillId;
import com.example.akthosidle.model.Stats;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class CharacterFragment extends Fragment {

    private GameViewModel vm;

    // Buff chips
    private TextView tvCombatPct, tvNonCombatPct;
    private LinearLayout combatIcons, nonCombatIcons;

    // Stats chip numbers
    private TextView tvAtkVal, tvDefVal, tvHpVal;

    // Food chip
    private ImageView imgFood;
    private TextView tvFoodHeals;

    // Small potions button
    private ImageButton btnPotions;

    // Equipment slots
    private final EnumMap<EquipmentSlot, Integer> slotIdMap = new EnumMap<>(EquipmentSlot.class);

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_character, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle s) {
        vm = new ViewModelProvider(requireActivity()).get(GameViewModel.class);

        // Buffs
        tvCombatPct     = v.findViewById(R.id.tvCombatPct);
        tvNonCombatPct  = v.findViewById(R.id.tvNonCombatPct);
        combatIcons     = v.findViewById(R.id.combatIcons);
        nonCombatIcons  = v.findViewById(R.id.nonCombatIcons);

        // Stats
        tvAtkVal        = v.findViewById(R.id.tvAtkVal);
        tvDefVal        = v.findViewById(R.id.tvDefVal);
        tvHpVal         = v.findViewById(R.id.tvHpVal);

        // Food
        imgFood         = v.findViewById(R.id.imgFood);
        tvFoodHeals     = v.findViewById(R.id.tvFoodHeals);

        // Potions
        btnPotions      = v.findViewById(R.id.btnPotions);
        btnPotions.setOnClickListener(_v -> openPotions());

        // Bottom bar is optional to wire; keeping focus on character UI.

        // Map each slot enum to its ImageButton id in the grid
        slotIdMap.put(EquipmentSlot.WEAPON, R.id.slot_weapon);
        slotIdMap.put(EquipmentSlot.HELMET, R.id.slot_helmet);
        slotIdMap.put(EquipmentSlot.CAPE,   R.id.slot_cape);
        slotIdMap.put(EquipmentSlot.GLOVES, R.id.slot_gloves);
        slotIdMap.put(EquipmentSlot.ARMOR,  R.id.slot_armor);
        slotIdMap.put(EquipmentSlot.SHIELD, R.id.slot_shield);
        slotIdMap.put(EquipmentSlot.BOW,    R.id.slot_bow);
        slotIdMap.put(EquipmentSlot.PANTS,  R.id.slot_pants);
        slotIdMap.put(EquipmentSlot.RING1,  R.id.slot_ring1);
        slotIdMap.put(EquipmentSlot.RING2,  R.id.slot_ring2);
        slotIdMap.put(EquipmentSlot.BOOTS,  R.id.slot_boots);

        // Click to equip picker
        for (Map.Entry<EquipmentSlot,Integer> e : slotIdMap.entrySet()) {
            ImageButton b = v.findViewById(e.getValue());
            EquipmentSlot slot = e.getKey();
            b.setOnClickListener(_v -> openEquipPicker(slot));
        }

        // Also wire the “left top” extra slot if you plan to use it; we leave it decorative for now
        // v.findViewById(R.id.slot_left_top) ...

        render();
    }

    // ------------------ Render ------------------

    private void render() {
        PlayerCharacter pc = vm.player();

        // Stats
        Stats gear  = vm.repo.gearStats(pc);
        Stats total = pc.totalStats(gear);
        tvAtkVal.setText(String.valueOf(total.attack));
        tvDefVal.setText(String.valueOf(total.defense));
        tvHpVal .setText(String.valueOf(total.health));

        // Equipment buttons: set icon or empty placeholder
        for (Map.Entry<EquipmentSlot,Integer> e : slotIdMap.entrySet()) {
            String itemId = pc.equipment.get(e.getKey());
            ImageButton btn = requireView().findViewById(e.getValue());
            if (itemId == null) {
                btn.setImageResource(R.drawable.ic_slot_empty);
            } else {
                Item it = vm.repo.getItem(itemId);
                if (it != null && it.icon != null) {
                    btn.setImageResource(iconRes(it.icon));
                } else {
                    btn.setImageResource(R.drawable.ic_slot_empty);
                }
            }
        }

        // Food chip (show selected food heal or default)
        String quickFoodId = pc.getQuickFoodId();
        if (quickFoodId != null) {
            Item food = vm.repo.getItem(quickFoodId);
            if (food != null) {
                tvFoodHeals.setText(food.heal != null ? "+" + food.heal : "+0");
                if (food.icon != null) imgFood.setImageResource(iconRes(food.icon));
            } else {
                tvFoodHeals.setText("+0");
                imgFood.setImageResource(R.drawable.ic_food_apple);
            }
        } else {
            tvFoodHeals.setText("+0");
            imgFood.setImageResource(R.drawable.ic_food_apple);
        }

        // Buff chips
        Map<String,Integer> buffs = vm.repo.aggregatedSkillBuffs(pc);
        int combatSum = 0, nonCombatSum = 0;
        combatIcons.removeAllViews();
        nonCombatIcons.removeAllViews();

        for (Map.Entry<String,Integer> b : buffs.entrySet()) {
            String key = b.getKey();
            int val = (b.getValue() == null ? 0 : b.getValue());
            boolean combat = isCombatSkill(key);
            if (combat) combatSum += val; else nonCombatSum += val;

            // Add small icon bubbles to the strip if you have per-skill icons
            ImageView iv = new ImageView(requireContext());
            iv.setLayoutParams(new LinearLayout.LayoutParams(dp(18), dp(18)));
            iv.setImageResource(skillIconRes(key)); // falls back to sword/shield/etc or generic
            (combat ? combatIcons : nonCombatIcons).addView(iv);
        }
        tvCombatPct.setText(combatSum + "%");
        tvNonCombatPct.setText(nonCombatSum + "%");
    }

    // ------------------ Actions ------------------

    private void openPotions() {
        new AlertDialog.Builder(requireContext())
                .setTitle("Potions")
                .setMessage("Open your potions mini-screen here.")
                .setPositiveButton("OK", null)
                .show();
    }

    private void openEquipPicker(EquipmentSlot slot) {
        Map<String,Integer> bag = vm.player().bag;
        List<Item> candidates = new ArrayList<>();
        for (Map.Entry<String,Integer> e : bag.entrySet()) {
            int qty = e.getValue() == null ? 0 : e.getValue();
            if (qty <= 0) continue;
            Item it = vm.repo.getItem(e.getKey());
            if (it != null && "EQUIPMENT".equals(it.type) && slot == vm.repo.slotOf(it)) {
                candidates.add(it);
            }
        }

        if (candidates.isEmpty()) {
            new AlertDialog.Builder(requireContext())
                    .setTitle(nice(slot))
                    .setMessage("No items for this slot.")
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }

        candidates.sort(Comparator.comparing(i -> i.name.toLowerCase(Locale.US)));
        CharSequence[] names = new CharSequence[candidates.size()];
        for (int i = 0; i < candidates.size(); i++) names[i] = candidates.get(i).name;

        new AlertDialog.Builder(requireContext())
                .setTitle("Equip " + nice(slot))
                .setItems(names, (d, which) -> {
                    Item choose = candidates.get(which);
                    vm.equip(choose.id);
                    render();
                })
                .show();
    }

    // ------------------ Helpers ------------------

    private boolean isCombatSkill(String name) {
        try {
            SkillId id = SkillId.valueOf(name);
            return id == SkillId.ATTACK || id == SkillId.STRENGTH || id == SkillId.DEFENSE || id == SkillId.HP;
        } catch (Exception e) {
            return false;
        }
    }

    private String nice(EquipmentSlot s) {
        switch (s) {
            case RING1: return "Ring 1";
            case RING2: return "Ring 2";
            default:
                String n = s.name().toLowerCase(Locale.US);
                return Character.toUpperCase(n.charAt(0)) + n.substring(1);
        }
    }

    private int dp(int v) {
        float d = requireContext().getResources().getDisplayMetrics().density;
        return Math.round(v * d);
    }

    /** Resolve an icon name from items.json to a drawable resource id. */
    private int iconRes(String iconName) {
        if (iconName == null) return R.drawable.ic_slot_empty;
        int id = getResources().getIdentifier(iconName, "drawable", requireContext().getPackageName());
        return id != 0 ? id : R.drawable.ic_slot_empty;
    }

    /** Very simple mapping for skill icons; point these to your drawables. */
    private int skillIconRes(String skillKey) {
        // Map known keys; fall back to sword icon
        try {
            SkillId id = SkillId.valueOf(skillKey);
            switch (id) {
                case ATTACK:    return R.drawable.ic_sword;
                case STRENGTH:  return R.drawable.ic_sword;    // replace with your icon
                case DEFENSE:   return R.drawable.ic_shield;
                case HP:        return R.drawable.ic_heart;
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
            }
        } catch (Exception ignored) { }
        return R.drawable.ic_sword;
    }
}
