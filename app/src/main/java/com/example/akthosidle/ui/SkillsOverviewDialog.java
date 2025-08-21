package com.example.akthosidle.ui;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.akthosidle.R;
import com.example.akthosidle.domain.model.PlayerCharacter;
import com.example.akthosidle.domain.model.Skill;
import com.example.akthosidle.domain.model.SkillId;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class SkillsOverviewDialog extends DialogFragment {

    private GameViewModel vm;

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        vm = new ViewModelProvider(requireActivity()).get(GameViewModel.class);

        LayoutInflater inf = LayoutInflater.from(requireContext());
        View content = inf.inflate(R.layout.dialog_skills_overview, null, false);

        RecyclerView rv = content.findViewById(R.id.rvSkillsOverview);
        rv.setLayoutManager(new GridLayoutManager(requireContext(), 2));
        SkillsOverviewAdapter adapter = new SkillsOverviewAdapter(buildRows());
        rv.setAdapter(adapter);

        return new AlertDialog.Builder(requireContext())
                .setTitle("Skills")
                .setView(content)
                .setNegativeButton("Close", null)
                .create();
    }

    private List<SkillsOverviewAdapter.Row> buildRows() {
        PlayerCharacter pc = vm.player();
        Map<SkillId, Skill> map = (pc.skills != null) ? pc.skills : new EnumMap<>(SkillId.class);

        List<SkillsOverviewAdapter.Row> out = new ArrayList<>();
        for (SkillId id : SkillId.values()) {
            Skill s = map.get(id);
            int lvl = (s != null) ? s.level : 1;
            int xp  = (s != null) ? s.xp    : 0;   // if your field is "exp", change to s.exp
            int toNext = reqExp(lvl);             // same curve we used elsewhere

            out.add(new SkillsOverviewAdapter.Row(
                    id,
                    iconFor(id),
                    prettify(id.name()),
                    lvl,
                    xp,
                    toNext
            ));
        }
        return out;
    }

    private static String prettify(String enumName) {
        String lower = enumName.replace('_',' ').toLowerCase();
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private static int reqExp(int lvl) {
        // keep in sync with CombatEngine's curve
        return 50 + (lvl * 25);
    }

    // Reuse the same mapping you used in SkillsFragment
    private int iconFor(SkillId id) {
        switch (id) {
            case ATTACK:     return R.drawable.ic_gauntlet;
            case STRENGTH:   return R.drawable.ic_sword;
            case DEFENSE:    return R.drawable.ic_shield;
            case ARCHERY:    return R.drawable.ic_shield;     // adjust when you have archery icon
            case MAGIC:      return R.drawable.ic_shield;     // adjust when you have magic icon
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
}
