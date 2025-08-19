package com.example.akthosidle;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.example.akthosidle.data.repo.GameRepository;
import com.example.akthosidle.databinding.ActivityMainBinding;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.text.NumberFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding b;
    private NavController navController;
    private AppBarConfiguration appBarConfig;

    // Repo + currency views
    private GameRepository repo;
    private TextView tvSilver, tvGold, tvSlayer;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        b = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());

        // Hook the Toolbar as the Activity's ActionBar
        setSupportActionBar(b.toolbar);

        // ===== Bind currency strip views inside the toolbar =====
        tvSilver = b.toolbar.findViewById(R.id.amount_silver);
        tvGold   = b.toolbar.findViewById(R.id.amount_gold);
        tvSlayer = b.toolbar.findViewById(R.id.amount_slayer);

        // ===== Repo setup & initial data =====
        repo = new GameRepository(getApplicationContext());
        repo.loadDefinitions();
        repo.loadOrCreatePlayer(); // publishes initial balances

        // Observe currency balances and render into the toolbar
        repo.currenciesLive().observe(this, this::renderCurrencies);
        // Initial paint in case observer hasn't fired yet
        renderCurrencies(repo.currenciesLive().getValue());

        // ===== Nav setup =====
        NavHostFragment navHostFragment = (NavHostFragment)
                getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment);
        if (navHostFragment == null) {
            throw new IllegalStateException("NavHostFragment R.id.nav_host_fragment not found");
        }
        navController = navHostFragment.getNavController();

        appBarConfig = new AppBarConfiguration.Builder(navController.getGraph()).build();
        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfig);

        BottomNavigationView bottomNav = b.bottomNav; // viewBinding id from XML
        NavigationUI.setupWithNavController(bottomNav, navController);
        bottomNav.setOnItemReselectedListener(item -> { /* no-op */ });
    }

    @Override
    public boolean onSupportNavigateUp() {
        return NavigationUI.navigateUp(navController, appBarConfig) || super.onSupportNavigateUp();
    }

    // ===== Rendering helpers =====

    private void renderCurrencies(Map<String, Long> balances) {
        if (balances == null) balances = new HashMap<>();

        long silver = get(balances, "silver");
        long gold   = Math.max(get(balances, "gold"), repo.getGold()); // legacy compat
        long slayer = get(balances, "slayer");

        setAmount(tvSilver, silver, R.id.cell_silver);
        setAmount(tvGold,   gold,   R.id.cell_gold);
        setAmount(tvSlayer, slayer, R.id.cell_slayer);
    }

    private long get(Map<String, Long> m, String k) {
        Long v = m.get(k);
        return v == null ? 0L : v;
    }

    private void setAmount(TextView tv, long value, int cellId) {
        if (tv == null) return;
        tv.setText(NumberFormat.getIntegerInstance(Locale.getDefault()).format(value));
        // If you want to hide cells when zero, toggle here (currently always visible):
        View cell = b.toolbar.findViewById(cellId);
        if (cell != null) cell.setVisibility(View.VISIBLE);
        // To hide zeros instead, use:
        // if (cell != null) cell.setVisibility(value > 0 ? View.VISIBLE : View.GONE);
    }
}
