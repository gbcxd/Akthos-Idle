package com.example.akthosidle;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;
import androidx.navigation.NavDestination;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.example.akthosidle.data.repo.GameRepository;
import com.example.akthosidle.data.tracking.ExpTracker;
import com.example.akthosidle.databinding.ActivityMainBinding;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding b;
    private NavController navController;
    private AppBarConfiguration appBarConfig;

    // Repo + currency views
    private GameRepository repo;
    private TextView tvSilver, tvGold, tvSlayer;

    // XP/hour mini-panel wiring
    private final Handler xpUiHandler = new Handler(Looper.getMainLooper());
    private final Set<String> xpSelectedKeys = new HashSet<>();
    private long xpWindowMs = ExpTracker.DEFAULT_WINDOW_MS;

    private MaterialCardView xpPanel;
    private FloatingActionButton fabXp;
    private ImageButton btnXpFilter;
    private TextView txtXpRate, txtXpKeys;

    public GameRepository getRepo() { return repo; }

    // Visibility control
    private boolean gatheringActive = false; // set by SkillsFragment when gathering starts/stops

    private final Runnable xpUpdater = new Runnable() {
        @Override public void run() {
            if (repo != null) {
                double rate = repo.xpTracker.ratePerHour(xpSelectedKeys, xpWindowMs);
                if (txtXpRate != null) {
                    txtXpRate.setText(String.format(Locale.US, "%.0f xp/h", rate));
                }
            }
            xpUiHandler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        b = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());

        // Toolbar as ActionBar
        setSupportActionBar(b.toolbar);

        // "+" button next to gold → buy screen
        View btnAddGold = b.toolbar.findViewById(R.id.btn_add_gold);
        if (btnAddGold != null) {
            btnAddGold.setOnClickListener(v -> {
                if (navController != null) navController.navigate(R.id.buyGoldFragment);
            });
        }

        // Currency labels in toolbar
        tvSilver = b.toolbar.findViewById(R.id.amount_silver);
        tvGold   = b.toolbar.findViewById(R.id.amount_gold);
        tvSlayer = b.toolbar.findViewById(R.id.amount_slayer);

        // Repo
        repo = new GameRepository(getApplicationContext());
        repo.loadDefinitions();
        repo.loadOrCreatePlayer();

        repo.currenciesLive().observe(this, this::renderCurrencies);
        renderCurrencies(repo.currenciesLive().getValue());

        // Nav setup
        NavHostFragment navHostFragment = (NavHostFragment)
                getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment);
        if (navHostFragment == null) throw new IllegalStateException("NavHostFragment not found");
        navController = navHostFragment.getNavController();

        appBarConfig = new AppBarConfiguration.Builder(navController.getGraph()).build();
        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfig);

        BottomNavigationView bottomNav = b.bottomNav;
        NavigationUI.setupWithNavController(bottomNav, navController);
        bottomNav.setOnItemReselectedListener(item -> { /* no-op */ });

        // XP/hour mini-panel wiring
        setupXpUi();

        // Hide by default, then toggle per-destination
        setXpFabVisible(false);
        setXpPanelVisible(false);

        // 🔁 Show FAB only on Battle OR (Skills & gatheringActive)
        navController.addOnDestinationChangedListener((controller, destination, args) -> {
            updateXpFabForDestination(destination);
        });
    }

    // ===== Dev overflow menu =====
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_dev, menu);
        boolean isDebug;
        try { isDebug = BuildConfig.DEBUG; } catch (Throwable t) { isDebug = true; }
        menu.setGroupVisible(R.id.group_dev, isDebug);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_give_apples) {
            repo.giveItem("food_apple", 5);
            return true;
        } else if (id == R.id.action_give_heal_pots) {
            repo.giveItem("pot_heal_small", 2);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onSupportNavigateUp() {
        return NavigationUI.navigateUp(navController, appBarConfig) || super.onSupportNavigateUp();
    }

    // ===== Lifecycle for XP updater =====
    @Override protected void onResume() {
        super.onResume();
        xpUiHandler.post(xpUpdater);
    }
    @Override protected void onPause() {
        super.onPause();
        xpUiHandler.removeCallbacksAndMessages(null);
    }

    // ===== Public hooks for fragments =====
    /** Call from SkillsFragment when gathering starts/stops. */
    public void setGatheringActive(boolean active) {
        this.gatheringActive = active;
        if (navController != null) {
            updateXpFabForDestination(navController.getCurrentDestination());
        }
    }
    public void setXpFabVisible(boolean visible) {
        if (fabXp != null) fabXp.setVisibility(visible ? View.VISIBLE : View.GONE);
    }
    public void setXpPanelVisible(boolean visible) {
        if (xpPanel != null) xpPanel.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    // ===== Rendering helpers =====
    private void renderCurrencies(Map<String, Long> balances) {
        if (balances == null) balances = new HashMap<>();
        long silver = get(balances, "silver");
        long gold   = Math.max(get(balances, "gold"), repo.getGold());
        long slayer = get(balances, "slayer");
        setAmount(tvSilver, silver, R.id.cell_silver);
        setAmount(tvGold,   gold,   R.id.cell_gold);
        setAmount(tvSlayer, slayer, R.id.cell_slayer);
    }
    private long get(Map<String, Long> m, String k) { Long v = m.get(k); return v == null ? 0L : v; }
    private void setAmount(TextView tv, long value, int cellId) {
        if (tv == null) return;
        tv.setText(NumberFormat.getIntegerInstance(Locale.getDefault()).format(value));
        View cell = b.toolbar.findViewById(cellId);
        if (cell != null) cell.setVisibility(View.VISIBLE);
    }

    // ===== XP UI wiring =====
    private void setupXpUi() {
        xpPanel     = b.xpPanel;
        fabXp       = b.fabXp;
        btnXpFilter = b.btnXpFilter;
        txtXpRate   = b.txtXpRate;
        txtXpKeys   = b.txtXpKeys;

        // default sources
        xpSelectedKeys.clear();
        xpSelectedKeys.add("combat");
        if (txtXpKeys != null) txtXpKeys.setText("(combat)");

        if (fabXp != null) {
            fabXp.setOnClickListener(v -> {
                boolean showing = xpPanel.getVisibility() == View.VISIBLE;
                setXpPanelVisible(!showing);
            });
        }

        if (btnXpFilter != null) {
            btnXpFilter.setOnClickListener(v -> {
                List<String> all = new ArrayList<>();
                all.add("combat");
                for (String k : repo.xpTracker.keys()) if (!"combat".equals(k)) all.add(k);

                final String[] arr = all.toArray(new String[0]);
                final boolean[] checked = new boolean[arr.length];
                for (int i = 0; i < arr.length; i++) checked[i] = xpSelectedKeys.contains(arr[i]);

                new AlertDialog.Builder(this)
                        .setTitle("Select XP sources")
                        .setMultiChoiceItems(arr, checked, (d, which, isChecked) -> checked[which] = isChecked)
                        .setPositiveButton("OK", (d, w) -> {
                            xpSelectedKeys.clear();
                            List<String> chosen = new ArrayList<>();
                            for (int i = 0; i < arr.length; i++) if (checked[i]) { xpSelectedKeys.add(arr[i]); chosen.add(arr[i]); }
                            if (chosen.isEmpty()) { xpSelectedKeys.add("combat"); chosen.add("combat"); }
                            if (txtXpKeys != null) txtXpKeys.setText("(" + String.join(", ", chosen) + ")");
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            });
        }
    }

    // ===== Destination helpers to avoid hard-coded R.id.* names =====

    /** Resolve an R.id.* by name at runtime; returns 0 if not found. */
    private int idByName(String name) {
        return getResources().getIdentifier(name, "id", getPackageName());
    }

    private boolean idMatchesAny(int id, String... candidates) {
        for (String name : candidates) {
            int resId = idByName(name);
            if (resId != 0 && id == resId) return true;
        }
        return false;
    }

    private boolean labelIsOneOf(@Nullable NavDestination dest, String... labels) {
        if (dest == null) return false;
        CharSequence lbl = dest.getLabel();
        if (lbl == null) return false;
        String s = lbl.toString().trim().toLowerCase(Locale.US);
        for (String want : labels) {
            if (s.equals(want.toLowerCase(Locale.US))) return true;
        }
        return false;
    }

    /** Decide whether to show FAB on this destination. */
    private void updateXpFabForDestination(@Nullable NavDestination dest) {
        if (dest == null) return;
        int id = dest.getId();

        // Try common ID names; tweak these strings to your actual nav_graph IDs if needed.
        boolean onBattle =
                idMatchesAny(id, "battleFragment", "nav_battle", "battle") ||
                        labelIsOneOf(dest, "Battle");

        boolean onSkills =
                idMatchesAny(id, "skillsFragment", "nav_skills", "skills") ||
                        labelIsOneOf(dest, "Skills");

        boolean show = onBattle || (onSkills && gatheringActive);
        setXpFabVisible(show);
        if (!show) setXpPanelVisible(false); // auto-hide panel when FAB hides
    }
}
