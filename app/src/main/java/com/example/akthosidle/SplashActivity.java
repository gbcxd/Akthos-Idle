package com.example.akthosidle;

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

import com.example.akthosidle.data.repo.GameRepository;
import com.example.akthosidle.data.seed.GameSeedImporter;

public class SplashActivity extends AppCompatActivity {

    private ProgressBar progressBar;
    private TextView tvPercent;

    private GameRepository repo;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        // Android 12+ system splash (we immediately draw our own UI afterwards)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            SplashScreen splash = SplashScreen.installSplashScreen(this);
            splash.setKeepOnScreenCondition(() -> false);
        }

        GameRepository repo = new GameRepository(getApplicationContext());

        GameSeedImporter.importAll(this, repo);
        repo.loadItemsFromAssets();
        repo.loadActionsFromAssets();

        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_splash);

        progressBar = findViewById(R.id.progress_bar);
        tvPercent   = findViewById(R.id.tvPercent);
        updateProgress(0);

        // Kick off fake loading on a background thread
        new Thread(this::runLoadingSequence, "SplashLoader").start();
    }

    // -------------------------
    // Fake loading sequence
    // -------------------------
    @WorkerThread
    private void runLoadingSequence() {
        try {
            // Total ~3 seconds; tweak sleeps to taste
            step(10,  () -> { repo = new GameRepository(getApplicationContext()); sleep(300); });
            step(30,  () -> { repo.loadDefinitions();                           sleep(600); });
            step(60,  () -> { repo.loadActionsFromAssets();                     sleep(700); });
            step(80,  () -> { repo.loadOrCreatePlayer(); repo.totalStats();     sleep(700); });
            step(100, () -> { /* done */                                        sleep(200); });

            runOnUiThread(() -> {
                startActivity(new Intent(SplashActivity.this, MainActivity.class));
                finish();
            });
        } catch (Throwable t) {
            t.printStackTrace();
            runOnUiThread(() -> {
                Toast.makeText(this, "Failed to load. Continuing…", Toast.LENGTH_LONG).show();
                startActivity(new Intent(SplashActivity.this, MainActivity.class));
                finish();
            });
        }
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

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }
}
