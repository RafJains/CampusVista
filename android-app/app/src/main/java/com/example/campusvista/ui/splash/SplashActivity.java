package com.example.campusvista.ui.splash;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;

import com.example.campusvista.R;
import com.example.campusvista.network.BackendClient;
import com.example.campusvista.network.BackendClient.BackendCallback;
import com.example.campusvista.network.BackendDtos.HealthDto;
import com.example.campusvista.ui.home.HomeMapActivity;

public final class SplashActivity extends Activity {
    private static final long SPLASH_DELAY_MS = 650L;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        TextView status = findViewById(R.id.splashStatus);
        status.setText("Checking campus services...");
        BackendClient.getInstance(this).checkHealth(new BackendCallback<HealthDto>() {
            @Override
            public void onSuccess(HealthDto value) {
                if (!isFinishing() && !isDestroyed()) {
                    status.setText("Live campus service ready");
                }
            }

            @Override
            public void onFallback(Throwable throwable) {
                if (!isFinishing() && !isDestroyed()) {
                    status.setText("Saved campus data ready");
                }
            }
        });

        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                startActivity(new Intent(SplashActivity.this, HomeMapActivity.class));
                finish();
            }
        }, SPLASH_DELAY_MS);
    }
}
