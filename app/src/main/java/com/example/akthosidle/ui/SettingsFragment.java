package com.example.akthosidle.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;

import com.example.akthosidle.R;
import com.google.android.material.button.MaterialButton;

public class SettingsFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle s) {
        super.onViewCreated(v, s);

        MaterialButton btnOpenShop = v.findViewById(R.id.btnOpenShop);
        if (btnOpenShop != null) {
            btnOpenShop.setOnClickListener(view -> {
                NavController nav = NavHostFragment.findNavController(this);
                // Navigate to your existing Shop / Buy screen
                nav.navigate(R.id.buyGoldFragment);
            });
        }
    }
}
