package com.obliviongatestudio.akthosidle.ui.currency;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.obliviongatestudio.akthosidle.R;
import com.obliviongatestudio.akthosidle.data.repo.GameRepository;

public class CurrenciesFragment extends Fragment {

    private CurrencyAdapter adapter;
    private GameRepository repo;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup container, @Nullable Bundle s) {
        return inf.inflate(R.layout.fragment_currencies, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle s) {
        super.onViewCreated(v, s);
        RecyclerView list = v.findViewById(R.id.list);
        list.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new CurrencyAdapter();
        list.setAdapter(adapter);

        repo = new GameRepository(requireContext().getApplicationContext());
        repo.loadDefinitions(); // ensure any defaults are loaded
    }

    @Override
    public void onResume() {
        super.onResume();
        // Simple refresh: pull latest balances each time screen appears
        adapter.submit(repo.listCurrencies());
    }
}
