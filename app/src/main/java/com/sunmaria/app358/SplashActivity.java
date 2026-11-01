package com.sunmaria.app358;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatActivity;

/** Shows the logo for SPLASH_DURATION_MS (1800 ms in the original project) then opens MainActivity. */
public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            Intent next = new Intent(this, MainActivity.class);
            // Forward notification extras (e.g. "url") from FCM tap
            if (getIntent() != null && getIntent().getExtras() != null) {
                next.putExtras(getIntent().getExtras());
            }
            startActivity(next);
            finish();
        }, BuildConfig.SPLASH_DURATION_MS);
    }
}
