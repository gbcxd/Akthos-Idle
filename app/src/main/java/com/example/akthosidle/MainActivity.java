package com.example.akthosidle;

import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
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
import com.google.gson.internal.GsonBuildConfig;
// ✅ Import your app's BuildConfig

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

        // "+" button next to gold → navigate to buy screen
        View btnAddGold = b.toolbar.findViewById(R.id.btn_add_gold);
        if (btnAddGold != null) {
            btnAddGold.setOnClickListener(v -> {
                if (navController != null) {
                    navController.navigate(R.id.buyGoldFragment);
                }
            });
        }

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

    // ===== Dev overflow menu (debug only, with safe fallback) =====
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_dev, menu);

        boolean isDebug;
        try {
            isDebug = BuildConfig.DEBUG; // normal path
        } catch (Throwable t) {
            // Fallback: if something odd happens with BuildConfig, show dev menu
            isDebug = true;
        }

        menu.setGroupVisible(R.id.group_dev, isDebug);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_give_apples) {
            // 🍎 Give 5x Apple
            repo.giveItem("food_apple", 5);
            return true;
        } else if (id == R.id.action_give_heal_pots) {
            // 🧪 Give 2x Lesser Healing Potion (ensure item exists in loadDefinitions)
            repo.giveItem("pot_heal_small", 2);
            return true;
        }
        return super.onOptionsItemSelected(item);
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
        View cell = b.toolbar.findViewById(cellId);
        if (cell != null) cell.setVisibility(View.VISIBLE);
        // To hide zeros instead, use:
        // if (cell != null) cell.setVisibility(value > 0 ? View.VISIBLE : View.GONE);
    }
}
