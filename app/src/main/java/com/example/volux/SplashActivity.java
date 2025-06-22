package com.example.volux;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

public class SplashActivity extends AppCompatActivity {

    private TextView textVolux;
    private CardView splashCard;
    private AnimatedBorderView animatedBorder;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        initViews();
        startAnimations();

        // Navigate to MainActivity after 3 seconds
        new Handler().postDelayed(() -> {
            Intent intent = new Intent(SplashActivity.this, MainActivity.class);
            startActivity(intent);
            finish();
        }, 3000);
    }

    private void initViews() {
        textVolux = findViewById(R.id.textVolux);
        splashCard = findViewById(R.id.splashCard);
        animatedBorder = findViewById(R.id.animatedBorder);
    }

    private void startAnimations() {
        // Zoom animation for Volux text
        Animation zoomIn = AnimationUtils.loadAnimation(this, R.anim.zoom_in);
        textVolux.startAnimation(zoomIn);

        // Card fade in animation
        Animation fadeIn = AnimationUtils.loadAnimation(this, R.anim.fade_in);
        splashCard.startAnimation(fadeIn);

        // Start animated border
        animatedBorder.startAnimation();
    }
}