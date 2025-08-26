package com.obliviongatestudio.akthosidle;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.MainThread;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.splashscreen.SplashScreen;

import com.obliviongatestudio.akthosidle.data.repo.GameRepository;
import com.obliviongatestudio.akthosidle.data.seed.GameSeedImporter;
import com.obliviongatestudio.akthosidle.ui.auth.AuthActivity; // <-- make sure this exists
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class SplashActivity extends AppCompatActivity {

    private ProgressBar progressBar;
    private TextView tvPercent;

    private GameRepository repo;
    private FirebaseAuth mAuth;

    // Controls the Android 12+ splash persistence
    private volatile boolean isLoading = true;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        // Android 12+ system splash
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            SplashScreen splash = SplashScreen.installSplashScreen(this);
            splash.setKeepOnScreenCondition(() -> isLoading);
        }

        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_splash);

        progressBar = findViewById(R.id.progress_bar);
        tvPercent   = findViewById(R.id.tvPercent);
        updateProgress(0);

        mAuth = FirebaseAuth.getInstance();
        repo  = new GameRepository(getApplicationContext());

        // Start background loading work
        new Thread(this::loadAndRoute, "SplashLoader").start();
    }

    // -------------------------
    // Loading sequence + routing
    // -------------------------
    @WorkerThread
    private void loadAndRoute() {
        try {
            step(10, () -> GameSeedImporter.importAll(SplashActivity.this, repo));
            step(25, () -> repo.loadItemsFromAssets());
            step(45, () -> repo.loadActionsFromAssets());
            step(70, () -> {
                repo.loadDefinitions();
                repo.loadOrCreatePlayer();
            });
            step(85, () -> repo.totalStats());
            step(100, () -> { /* done */ });

        } catch (Throwable t) {
            t.printStackTrace();
            runOnUiThread(() ->
                    Toast.makeText(this, "Failed to load. Continuing…", Toast.LENGTH_LONG).show()
            );
        }

        // Decide destination based on auth state
        FirebaseUser currentUser = mAuth.getCurrentUser();
        Class<?> next = (currentUser != null)
                ? MainActivity.class                  // user is signed in → game
                : AuthActivity.class;                 // not signed in → auth flow

        // Release the 12+ splash and navigate
        isLoading = false;
        runOnUiThread(() -> {
            startActivity(new Intent(SplashActivity.this, next));
            finish(); // prevent back to splash
        });
    }

    @WorkerThread
    private void step(int targetPercent, Runnable work) {
        if (work != null) work.run();
        updateProgress(targetPercent);
    }

    @MainThread
    private void setProgressUi(int p) {
        if (progressBar != null) progressBar.setProgress(p);
        if (tvPercent != null) tvPercent.setText(p + "%");
    }

    private void updateProgress(int p) {
        int clamped = Math.max(0, Math.min(100, p));
        runOnUiThread(() -> setProgressUi(clamped));
    }
}
