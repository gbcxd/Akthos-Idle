package com.example.akthosidle.ui;

import android.app.Dialog;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.view.LayoutInflater;
import android.view.View;

import com.example.akthosidle.R;
import com.example.akthosidle.data.dtos.InventoryItem;

import java.util.List;

public class FoodPickerDialog extends DialogFragment implements FoodAdapter.OnFoodClick {

    private GameViewModel vm;

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        vm = new ViewModelProvider(requireActivity()).get(GameViewModel.class);

        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("Select Food");

        View view = LayoutInflater.from(getContext()).inflate(R.layout.dialog_list, null);
        RecyclerView rv = view.findViewById(R.id.recyclerView);
        rv.setLayoutManager(new LinearLayoutManager(getContext()));

        List<InventoryItem> foods = vm.getFoodItems();
        FoodAdapter adapter = new FoodAdapter(foods, this);
        rv.setAdapter(adapter);

        builder.setView(view);
        builder.setNegativeButton("Cancel", (d, w) -> dismiss());

        return builder.create();
    }

    @Override
    public void onFoodSelected(InventoryItem item) {
        List<InventoryItem> foods = vm.getFoodItems();
        dismiss();
    }
}
