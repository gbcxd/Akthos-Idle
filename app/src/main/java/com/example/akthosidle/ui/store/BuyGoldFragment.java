package com.example.akthosidle.ui.store;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.akthosidle.R;
import com.example.akthosidle.data.repo.GameRepository;

public class BuyGoldFragment extends Fragment {

    private GameRepository repo;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup container, @Nullable Bundle s) {
        return inf.inflate(R.layout.fragment_buy_gold, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle s) {
        super.onViewCreated(v, s);
        repo = new GameRepository(requireContext().getApplicationContext());

        Button btnSmall  = v.findViewById(R.id.btn_buy_small);
        Button btnMedium = v.findViewById(R.id.btn_buy_medium);
        Button btnLarge  = v.findViewById(R.id.btn_buy_large);

        // Offline placeholder: add gold directly.
        btnSmall.setOnClickListener(view -> { repo.addCurrency("gold", 100); });
        btnMedium.setOnClickListener(view -> { repo.addCurrency("gold", 500); });
        btnLarge.setOnClickListener(view -> { repo.addCurrency("gold", 2000); });
    }
}
