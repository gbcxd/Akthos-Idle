package com.example.akthosidle;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.MainThread;
import androidx.annotation.WorkerThread;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.splashscreen.SplashScreen;

import com.example.akthosidle.data.repo.GameRepository;

public class SplashActivity extends AppCompatActivity {

    private ProgressBar progressBar;
    private TextView tvPercent;

    private GameRepository repo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SplashScreen splash = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            splash = SplashScreen.installSplashScreen(this);
            // don’t keep the system splash; we immediately draw our own layout
            splash.setKeepOnScreenCondition(() -> false);
        }

        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_splash);

        progressBar = findViewById(R.id.progress_bar);
        tvPercent   = findViewById(R.id.tvPercent);
        updateProgress(0);

        // Kick off real loading work on a background thread
        new Thread(this::runLoadingSequence, "SplashLoader").start();
    }

    // --- Loading sequence (milestones ~10/30/60/80/100) ---

    @WorkerThread
    private void runLoadingSequence() {
        try {
            // 10% – create repo
            step(10, () -> repo = new GameRepository(getApplicationContext()));

            // 30% – load static definitions (items/monsters)
            step(30, () -> repo.loadDefinitions());

            // 60% – load actions from assets (with fallback)
            step(60, () -> repo.loadActionsFromAssets());

            // 80% – load or create player, compute stats once
            step(80, () -> {
                repo.loadOrCreatePlayer();
                repo.totalStats(); // warm-up calculation
            });

            // 100% – done → open Main
            step(100, () -> { /* no-op */ });

            runOnUiThread(() -> {
                startActivity(new Intent(SplashActivity.this, MainActivity.class));
                finish();
            });
        } catch (Throwable t) {
            t.printStackTrace();
            runOnUiThread(() -> {
                Toast.makeText(this, "Failed to load. Please restart.", Toast.LENGTH_LONG).show();
                // Fallback: still try to continue to Main to avoid trap on splash
                startActivity(new Intent(SplashActivity.this, MainActivity.class));
                finish();
            });
        }
    }

    @WorkerThread
    private void step(int targetPercent, Runnable work) {
        if (work != null) work.run();
        setProgress(targetPercent);
    }

    @MainThread
    private void setProgressUi(int p) {
        if (progressBar != null) progressBar.setProgress(p);
        if (tvPercent != null) tvPercent.setText(p + "%");
    }

    private void updateProgress(int p) {
        runOnUiThread(() -> setProgressUi(Math.max(0, Math.min(100, p))));
    }
}
