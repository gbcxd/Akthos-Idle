package com.example.akthosidle;

import android.animation.ValueAnimator;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.splashscreen.SplashScreen;

public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        // Show Android 12+ system splash instantly
        SplashScreen splash = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            splash = SplashScreen.installSplashScreen(this);
        }

        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this); // draws edge-to-edge for full coverage
        setContentView(R.layout.activity_splash);

        TextView tvPercent = findViewById(R.id.tvPercent);
        ProgressBar progressBar = findViewById(R.id.progress_bar);

        // Animate 0→100% over ~10 seconds
        ValueAnimator anim = ValueAnimator.ofInt(0, 100);
        anim.setDuration(10_000L);
        anim.addUpdateListener(a -> {
            int v = (int) a.getAnimatedValue();
            progressBar.setProgress(v);
            if (tvPercent != null) tvPercent.setText(v + "%");
        });
        anim.addListener(new SimpleAnimatorEndListener(() -> {
            // Loading done → go to MainActivity
            startActivity(new Intent(SplashActivity.this, MainActivity.class));
            finish();
        }));
        anim.start();

        // Optional: for Android 12+, keep the system splash until our UI is ready
        if (splash != null) {
            // We already swapped to our layout; don’t keep system splash longer than a frame
            splash.setKeepOnScreenCondition(() -> false);
        }
    }

    // tiny helper to avoid full AnimatorListenerAdapter boilerplate
    private static class SimpleAnimatorEndListener extends android.animation.AnimatorListenerAdapter {
        private final Runnable onEnd;
        SimpleAnimatorEndListener(Runnable r) { onEnd = r; }
        @Override public void onAnimationEnd(android.animation.Animator animation) { if (onEnd != null) onEnd.run(); }
        @Override public void onAnimationCancel(android.animation.Animator animation) { if (onEnd != null) onEnd.run(); }
    }
}
