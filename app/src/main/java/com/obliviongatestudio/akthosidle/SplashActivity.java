package com.obliviongatestudio.akthosidle;

import static java.lang.Thread.sleep;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.MainThread;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.splashscreen.SplashScreen;

import com.google.android.material.progressindicator.LinearProgressIndicator;

import com.obliviongatestudio.akthosidle.data.repo.GameRepository;
import com.obliviongatestudio.akthosidle.data.seed.GameSeedImporter;
import com.obliviongatestudio.akthosidle.ui.auth.AuthActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class SplashActivity extends AppCompatActivity {

    private LinearProgressIndicator progressBar;
    private TextView tvPercent;

    private GameRepository repo;
    private FirebaseAuth mAuth;

    private volatile boolean isLoading = true;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            SplashScreen splash = SplashScreen.installSplashScreen(this);
            splash.setKeepOnScreenCondition(() -> isLoading);
        }

        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_splash);

        // Fade in the key art for a nice touch
        ImageView art = findViewById(R.id.imgKeyArt);
        if (art != null) { art.setAlpha(0f); art.animate().alpha(1f).setDuration(500).start(); }

        progressBar = findViewById(R.id.progress_bar);
        tvPercent   = findViewById(R.id.tvPercent);
        updateProgress(0);

        mAuth = FirebaseAuth.getInstance();
        repo  = new GameRepository(getApplicationContext());

        new Thread(this::loadAndRoute, "SplashLoader").start();
    }

    @WorkerThread
    private void loadAndRoute() {
        try {
            step(10, () -> GameSeedImporter.importAll(SplashActivity.this, repo)); sleep(1500);
            step(30, repo::loadItemsFromAssets); sleep(1500);
            step(55, repo::loadActionsFromAssets); sleep(1500);
            step(75, () -> { repo.loadDefinitions(); repo.loadOrCreatePlayer(); }); sleep(300);
            step(90, repo::totalStats);
            step(100, () -> { /* done */ });
        } catch (Throwable t) {
            t.printStackTrace();
            runOnUiThread(() ->
                    Toast.makeText(this, "Failed to load. Continuing…", Toast.LENGTH_LONG).show()
            );
        }

        FirebaseUser currentUser = mAuth.getCurrentUser();
        Class<?> next = (currentUser != null) ? MainActivity.class : AuthActivity.class;

        isLoading = false;
        runOnUiThread(() -> {
            startActivity(new Intent(SplashActivity.this, next));
            finish();
        });
    }

    @WorkerThread
    private void step(int targetPercent, Runnable work) {
        if (work != null) work.run();
        updateProgress(targetPercent);
        try { sleep(180); } catch (InterruptedException ignored) {}
    }

    @MainThread
    private void setProgressUi(int p) {
        if (progressBar != null) progressBar.setProgressCompat(p, true);
        if (tvPercent != null) tvPercent.setText(p + "%");
    }

    private void updateProgress(int p) {
        int clamped = Math.max(0, Math.min(100, p));
        runOnUiThread(() -> setProgressUi(clamped));
    }
}
