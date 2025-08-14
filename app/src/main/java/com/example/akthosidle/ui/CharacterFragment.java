package com.example.akthosidle.ui;

import android.os.Bundle;
import android.view.*;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.example.akthosidle.R;
import com.example.akthosidle.model.*;

public class CharacterFragment extends Fragment {
    private GameViewModel vm;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_character, container, false);
    }

    @Override public void onViewCreated(@NonNull View v, @Nullable Bundle s) {
        vm = new ViewModelProvider(requireActivity()).get(GameViewModel.class);
        PlayerCharacter pc = vm.player();
        Stats gear = vm.repo.gearStats(pc);
        Stats total = pc.totalStats(gear);

        TextView tvStats = v.findViewById(R.id.tvStats);
        tvStats.setText("Lv " + pc.level + "  ATK " + total.attack + "  DEF " + total.defense + "  HP " + total.health);

        StringBuilder eq = new StringBuilder();
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            String id = pc.equipment.get(slot);
            if (id != null) eq.append(slot.name()).append(": ").append(vm.repo.getItem(id).name).append("\n");
        }
        ((TextView) v.findViewById(R.id.tvEquipment)).setText(eq.length() == 0 ? "No equipment yet" : eq.toString());
    }
}
