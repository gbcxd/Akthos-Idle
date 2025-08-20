package com.example.akthosidle.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.akthosidle.R;
import com.example.akthosidle.data.repo.GameRepository;
import com.example.akthosidle.databinding.FragmentSkillsBinding;
import com.example.akthosidle.domain.model.PlayerCharacter;
import com.example.akthosidle.domain.model.Skill;
import com.example.akthosidle.domain.model.SkillId;
import com.example.akthosidle.ui.SkillAdapter;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class SkillsFragment extends Fragment {

    private FragmentSkillsBinding b;
    private GameRepository repo;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        b = FragmentSkillsBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);

        repo = new GameRepository(requireContext().getApplicationContext());
        repo.loadDefinitions();
        PlayerCharacter pc = repo.loadOrCreatePlayer();

        b.recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        b.recycler.setHasFixedSize(true);

        List<SkillAdapter.SkillRow> rows = buildRows(pc);
        SkillAdapter adapter = new SkillAdapter(rows, row -> {
            Bundle args = new Bundle();
            // nav_graph arg name is "skillId" (string)
            args.putString("skillId", row.id);
            NavHostFragment.findNavController(this)
                    .navigate(R.id.action_nav_skills_to_skillDetail, args);
        });
        b.recycler.setAdapter(adapter);
    }

    private List<SkillAdapter.SkillRow> buildRows(PlayerCharacter pc) {
        Map<SkillId, Skill> map = (pc.skills != null) ? pc.skills : new EnumMap<>(SkillId.class);
        List<SkillAdapter.SkillRow> out = new ArrayList<>();

        for (SkillId id : SkillId.values()) {
            // Skip combat skills in the skilling menu
            if (isCombat(id)) continue;

            Skill s = map.get(id);
            int lvl = (s != null) ? s.level : 1;
            String name = prettify(id.name());
            int icon = iconFor(id);
            out.add(new SkillAdapter.SkillRow(id.name(), name, lvl, icon));
        }
        return out;
    }

    /** Treat these as combat skills and exclude them from the skilling list. */
    private boolean isCombat(SkillId id) {
        switch (id) {
            case ATTACK:
            case STRENGTH:
            case DEFENSE:
            case ARCHERY:
            case MAGIC:
            case HP:
                return true;
            default:
                return false;
        }
    }

    private static String prettify(String enumName) {
        String lower = enumName.replace('_',' ').toLowerCase(Locale.US);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private int iconFor(SkillId id) {
        // Map per-skill icons here (use your actual drawables; fallback to generic)
        switch (id) {
            case ATTACK:     return R.drawable.ic_gauntlet;
            case STRENGTH:   return R.drawable.ic_sword;
            case DEFENSE:    return R.drawable.ic_shield;
            case ARCHERY:    return R.drawable.ic_shield;
            case MAGIC:      return R.drawable.ic_shield;
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

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        b = null;
    }
}
