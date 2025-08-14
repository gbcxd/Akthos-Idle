package com.example.akthosidle.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.akthosidle.R;

public class InventoryFragment extends Fragment implements InventoryAdapter.OnItemClick {
    private GameViewModel vm;
    private InventoryAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_inventory, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle s) {
        vm = new ViewModelProvider(requireActivity()).get(GameViewModel.class);

        RecyclerView rv = v.findViewById(R.id.rv);
        rv.setLayoutManager(new GridLayoutManager(getContext(), 4));
        adapter = new InventoryAdapter(vm.repo, vm.player().bag, this);
        rv.setAdapter(adapter);
    }

    @Override
    public void onItemClick(String itemId) {
        // Equip or use when tapped
        var item = vm.repo.getItem(itemId);
        if (item == null) return;
        if ("EQUIPMENT".equals(item.type)) {
            vm.equip(item.id);
        } else if ("CONSUMABLE".equals(item.type) && item.heal != null) {
            var p = vm.player();
            p.addItem(item.id, -1);
            p.base.health += item.heal;   // demo behavior
            vm.repo.save();
        }
        adapter.refresh(vm.player().bag);
    }
}
