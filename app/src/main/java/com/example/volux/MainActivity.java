package com.example.volux;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.PorterDuff;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.cardview.widget.CardView;
import com.google.android.material.switchmaterial.SwitchMaterial;
import android.widget.LinearLayout;

public class MainActivity extends AppCompatActivity {

    private static final int OVERLAY_PERMISSION_REQUEST = 1001;
    private static final int NOTIFICATION_PERMISSION_REQUEST = 1002;
    private static final String CHANNEL_ID = "VoluxService";

    private SharedPreferences prefs;
    private SwitchMaterial switchFloatingButtons, switchGestureBox, switchBothModes,
            switchAlwaysVisible, switchMoveMode;
    private SeekBar seekBarOpacity, seekBarAutoHideDelay, seekBarGestureBoxSize;
    private TextView textOpacity, textAutoHideDelay, textGestureBoxSize;
    private Button btnStartService, btnStopService, btnSettings, btnCustomSize;
    private CardView settingsCard;

    // New UI elements for enhanced features
    private TextView textCurrentSize, textButtonSize;
    private SeekBar seekBarButtonSize;
    private Button btnResetPositions;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        setupPreferences();
        createNotificationChannel();
        checkPermissions();
        setupListeners();
        updateUI();
    }

    private void initViews() {
        // Existing views
        switchFloatingButtons = findViewById(R.id.switchFloatingButtons);
        switchGestureBox = findViewById(R.id.switchGestureBox);
        switchBothModes = findViewById(R.id.switchBothModes);
        seekBarOpacity = findViewById(R.id.seekBarOpacity);
        seekBarAutoHideDelay = findViewById(R.id.seekBarAutoHideDelay);
        seekBarGestureBoxSize = findViewById(R.id.seekBarGestureBoxSize);
        textOpacity = findViewById(R.id.textOpacity);
        textAutoHideDelay = findViewById(R.id.textAutoHideDelay);
        textGestureBoxSize = findViewById(R.id.textGestureBoxSize);
        btnStartService = findViewById(R.id.btnStartService);
        btnStopService = findViewById(R.id.btnStopService);
        btnSettings = findViewById(R.id.btnSettings);
        settingsCard = findViewById(R.id.settingsCard);
        switchAlwaysVisible = findViewById(R.id.switchAlwaysVisible);
        switchMoveMode = findViewById(R.id.switchMoveMode);

        // Add new UI elements programmatically since they're not in the XML
        addEnhancedUIElements();
    }

    private void addEnhancedUIElements() {
        // Get the settings card layout
        LinearLayout settingsLayout = (LinearLayout) settingsCard.getChildAt(0);

        // Add button size control
        LinearLayout buttonSizeLayout = new LinearLayout(this);
        buttonSizeLayout.setOrientation(LinearLayout.VERTICAL);
        buttonSizeLayout.setPadding(0, 0, 0, dpToPx(20));

        textButtonSize = new TextView(this);
        textButtonSize.setText("Button Size: 60dp");
        textButtonSize.setTextSize(16);
        textButtonSize.setTextColor(getResources().getColor(R.color.text_primary));
        textButtonSize.setPadding(0, 0, 0, dpToPx(8));

        seekBarButtonSize = new SeekBar(this);
        seekBarButtonSize.setMax(80); // 40-120 range
        seekBarButtonSize.setProgress(20); // Default 60dp

        // Set tint colors with API level check
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            seekBarButtonSize.setProgressTintList(ColorStateList.valueOf(getResources().getColor(R.color.primary_cyan)));
            seekBarButtonSize.setThumbTintList(ColorStateList.valueOf(getResources().getColor(R.color.primary_cyan)));
        }
        else {
            // Fallback for older versions
            seekBarButtonSize.getProgressDrawable().setColorFilter(
                    getResources().getColor(R.color.primary_cyan), PorterDuff.Mode.SRC_IN);
            seekBarButtonSize.getThumb().setColorFilter(
                    getResources().getColor(R.color.primary_cyan), PorterDuff.Mode.SRC_IN);
        }

        buttonSizeLayout.addView(textButtonSize);
        buttonSizeLayout.addView(seekBarButtonSize);
        settingsLayout.addView(buttonSizeLayout);

        // Add custom size button
        btnCustomSize = new Button(this);
        btnCustomSize.setText("Set Custom Size");
        btnCustomSize.setBackgroundColor(getResources().getColor(R.color.primary_purple));
        btnCustomSize.setTextColor(getResources().getColor(R.color.accent_white));
        btnCustomSize.setPadding(dpToPx(16), dpToPx(12), dpToPx(16), dpToPx(12));
        LinearLayout.LayoutParams customSizeParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        customSizeParams.setMargins(0, dpToPx(16), 0, dpToPx(16));
        btnCustomSize.setLayoutParams(customSizeParams);
        settingsLayout.addView(btnCustomSize);

        // Add current size display
        textCurrentSize = new TextView(this);
        textCurrentSize.setText("Current Gesture Box: 200 x 100dp");
        textCurrentSize.setTextSize(14);
        textCurrentSize.setTextColor(getResources().getColor(R.color.text_secondary));
        textCurrentSize.setPadding(0, 0, 0, dpToPx(16));
        settingsLayout.addView(textCurrentSize);

        // Add reset positions button
        btnResetPositions = new Button(this);
        btnResetPositions.setText("Reset Positions");
        btnResetPositions.setBackgroundColor(getResources().getColor(R.color.accent_magenta));
        btnResetPositions.setTextColor(getResources().getColor(R.color.accent_white));
        btnResetPositions.setPadding(dpToPx(16), dpToPx(12), dpToPx(16), dpToPx(12));
        LinearLayout.LayoutParams resetParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        resetParams.setMargins(0, 0, 0, dpToPx(16));
        btnResetPositions.setLayoutParams(resetParams);
        settingsLayout.addView(btnResetPositions);
    }

    private void setupPreferences() {
        prefs = getSharedPreferences("VoluxPrefs", MODE_PRIVATE);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Volux Service", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Assistive Volume Control Service");
            channel.setShowBadge(false);
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
    }

    private void checkPermissions() {
        if (!Settings.canDrawOverlays(this)) {
            requestOverlayPermission();
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        NOTIFICATION_PERMISSION_REQUEST);
            }
        }
    }

    private void requestOverlayPermission() {
        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName()));
        startActivityForResult(intent, OVERLAY_PERMISSION_REQUEST);
    }

    private void setupListeners() {
        // Control mode switches
        switchFloatingButtons.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("floating_buttons", isChecked).apply();
            if (isChecked) {
                switchGestureBox.setChecked(false);
                switchBothModes.setChecked(false);
            }
            updateServiceIfRunning();
        });

        switchGestureBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("gesture_box", isChecked).apply();
            if (isChecked) {
                switchFloatingButtons.setChecked(false);
                switchBothModes.setChecked(false);
            }
            updateServiceIfRunning();
        });

        switchBothModes.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("both_modes", isChecked).apply();
            if (isChecked) {
                switchFloatingButtons.setChecked(false);
                switchGestureBox.setChecked(false);
            }
            updateServiceIfRunning();
        });

        // Move Mode Switch - Enhanced with visual feedback
        switchMoveMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("move_mode_enabled", isChecked).apply();

            // Show toast to inform user about move mode
            if (isChecked) {
                Toast.makeText(this, "Move Mode ON: Drag to reposition controls. Pinch to resize.",
                        Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, "Move Mode OFF: Controls are now locked in position.",
                        Toast.LENGTH_SHORT).show();
            }

            updateServiceIfRunning();
        });

        // Always Visible Switch
        switchAlwaysVisible.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("always_visible", isChecked).apply();
            updateServiceIfRunning();
        });

        // Opacity SeekBar - Enhanced with real-time feedback
        seekBarOpacity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float opacity = (progress + 20) / 100f; // Min opacity 0.2, max 1.0
                textOpacity.setText("Opacity: " + (int)(opacity * 100) + "%");
                prefs.edit().putFloat("opacity", opacity).apply();

                // Provide visual feedback
                if (fromUser) {
                    updateServiceIfRunning();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                Toast.makeText(MainActivity.this, "Opacity updated", Toast.LENGTH_SHORT).show();
            }
        });

        // Auto-hide Delay SeekBar
        seekBarAutoHideDelay.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int delay = progress + 1; // 1-10 seconds
                textAutoHideDelay.setText("Auto-hide Delay: " + delay + "s");
                prefs.edit().putInt("auto_hide_delay", delay * 1000).apply();

                if (fromUser) {
                    updateServiceIfRunning();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // Enhanced Gesture Box Size SeekBar
        seekBarGestureBoxSize.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int width = progress + 100; // Width: 100-300
                int height = (progress / 2) + 80; // Height: 80-180
                textGestureBoxSize.setText("Gesture Box: " + width + " x " + height + "dp");
                textCurrentSize.setText("Current Gesture Box: " + width + " x " + height + "dp");

                prefs.edit()
                        .putInt("gesture_box_width", width)
                        .putInt("gesture_box_height", height)
                        .apply();

                if (fromUser) {
                    updateServiceIfRunning();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // Button Size SeekBar
        seekBarButtonSize.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int size = progress + 40; // Range: 40-120dp
                textButtonSize.setText("Button Size: " + size + "dp");
                prefs.edit().putInt("current_button_size", size).apply();

                if (fromUser) {
                    updateServiceIfRunning();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // Service Control Buttons
        btnStartService.setOnClickListener(v -> {
            if (Settings.canDrawOverlays(this)) {
                Intent serviceIntent = new Intent(this, VoluxService.class);
                startForegroundService(serviceIntent);
                Toast.makeText(this, "Volux Service Started", Toast.LENGTH_SHORT).show();
                updateServiceButtons(true);
            } else {
                Toast.makeText(this, "Overlay permission required", Toast.LENGTH_SHORT).show();
                requestOverlayPermission();
            }
        });

        btnStopService.setOnClickListener(v -> {
            Intent serviceIntent = new Intent(this, VoluxService.class);
            stopService(serviceIntent);
            Toast.makeText(this, "Volux Service Stopped", Toast.LENGTH_SHORT).show();
            updateServiceButtons(false);
        });

        // Settings Button with enhanced animation
        btnSettings.setOnClickListener(v -> {
            toggleSettingsCard();
        });

        // Custom Size Button
        btnCustomSize.setOnClickListener(v -> {
            showCustomSizeDialog();
        });

        // Reset Positions Button
        btnResetPositions.setOnClickListener(v -> {
            showResetPositionsDialog();
        });
    }

    private void toggleSettingsCard() {
        if (settingsCard.getVisibility() == View.VISIBLE) {
            // Hide with smooth animation
            settingsCard.animate()
                    .alpha(0f)
                    .scaleY(0f)
                    .translationY(-50f)
                    .setDuration(250)
                    .withEndAction(() -> settingsCard.setVisibility(View.GONE))
                    .start();
        } else {
            // Show with smooth animation
            settingsCard.setVisibility(View.VISIBLE);
            settingsCard.setAlpha(0f);
            settingsCard.setScaleY(0f);
            settingsCard.setTranslationY(-50f);
            settingsCard.animate()
                    .alpha(1f)
                    .scaleY(1f)
                    .translationY(0f)
                    .setDuration(250)
                    .start();
        }
    }

    private void showCustomSizeDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Set Custom Size");

        // Create layout for the dialog
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dpToPx(20), dpToPx(20), dpToPx(20), dpToPx(20));

        // Width input
        TextView widthLabel = new TextView(this);
        widthLabel.setText("Width (dp):");
        widthLabel.setTextSize(16);
        layout.addView(widthLabel);

        EditText widthInput = new EditText(this);
        widthInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        widthInput.setText(String.valueOf(prefs.getInt("gesture_box_width", 200)));
        layout.addView(widthInput);

        // Height input
        TextView heightLabel = new TextView(this);
        heightLabel.setText("Height (dp):");
        heightLabel.setTextSize(16);
        heightLabel.setPadding(0, dpToPx(16), 0, 0);
        layout.addView(heightLabel);

        EditText heightInput = new EditText(this);
        heightInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        heightInput.setText(String.valueOf(prefs.getInt("gesture_box_height", 100)));
        layout.addView(heightInput);

        builder.setView(layout);

        builder.setPositiveButton("Apply", (dialog, which) -> {
            try {
                int width = Integer.parseInt(widthInput.getText().toString());
                int height = Integer.parseInt(heightInput.getText().toString());

                // Validate ranges
                width = Math.max(30, Math.min(400, width));
                height = Math.max(60, Math.min(300, height));

                // Save preferences
                prefs.edit()
                        .putInt("gesture_box_width", width)
                        .putInt("gesture_box_height", height)
                        .apply();

                // Update UI
                textCurrentSize.setText("Current Gesture Box: " + width + " x " + height + "dp");
                textGestureBoxSize.setText("Gesture Box: " + width + " x " + height + "dp");

                // Update service
                updateServiceIfRunning();

                Toast.makeText(this, "Custom size applied: " + width + "x" + height,
                        Toast.LENGTH_SHORT).show();

            } catch (NumberFormatException e) {
                Toast.makeText(this, "Please enter valid numbers", Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void showResetPositionsDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Reset Positions")
                .setMessage("This will reset all floating buttons and gesture box positions to default. Continue?")
                .setPositiveButton("Reset", (dialog, which) -> {
                    // Reset all position preferences
                    prefs.edit()
                            .remove("floating_buttons_x")
                            .remove("floating_buttons_y")
                            .remove("gesture_box_x")
                            .remove("gesture_box_y")
                            .putInt("gesture_box_width", 200)
                            .putInt("gesture_box_height", 100)
                            .putInt("current_button_size", 60)
                            .apply();

                    // Update UI
                    updateUI();
                    updateServiceIfRunning();

                    Toast.makeText(this, "Positions reset to default", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void updateServiceIfRunning() {
        Intent updateIntent = new Intent(this, VoluxService.class);
        updateIntent.setAction("UPDATE_SETTINGS");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                startForegroundService(updateIntent);
            } catch (Exception e) {
                // Service not running, ignore
            }
        }
    }

    private void updateServiceButtons(boolean serviceRunning) {
        btnStartService.setEnabled(!serviceRunning);
        btnStopService.setEnabled(serviceRunning);
        btnStartService.setAlpha(serviceRunning ? 0.5f : 1.0f);
        btnStopService.setAlpha(serviceRunning ? 1.0f : 0.5f);
    }

    private void updateUI() {
        // Update switches
        switchFloatingButtons.setChecked(prefs.getBoolean("floating_buttons", true));
        switchGestureBox.setChecked(prefs.getBoolean("gesture_box", false));
        switchBothModes.setChecked(prefs.getBoolean("both_modes", false));
        switchMoveMode.setChecked(prefs.getBoolean("move_mode_enabled", false));
        switchAlwaysVisible.setChecked(prefs.getBoolean("always_visible", false));

        // Update opacity
        float opacity = prefs.getFloat("opacity", 0.8f);
        seekBarOpacity.setProgress((int)(opacity * 100) - 20);
        textOpacity.setText("Opacity: " + (int)(opacity * 100) + "%");

        // Update auto-hide delay
        int autoHideDelay = prefs.getInt("auto_hide_delay", 3000) / 1000;
        seekBarAutoHideDelay.setProgress(autoHideDelay - 1);
        textAutoHideDelay.setText("Auto-hide Delay: " + autoHideDelay + "s");

        // Update gesture box size
        int gestureBoxWidth = prefs.getInt("gesture_box_width", 200);
        int gestureBoxHeight = prefs.getInt("gesture_box_height", 100);
        seekBarGestureBoxSize.setProgress(gestureBoxWidth - 100);
        textGestureBoxSize.setText("Gesture Box: " + gestureBoxWidth + " x " + gestureBoxHeight + "dp");
        textCurrentSize.setText("Current Gesture Box: " + gestureBoxWidth + " x " + gestureBoxHeight + "dp");

        // Update button size
        int buttonSize = prefs.getInt("current_button_size", 60);
        seekBarButtonSize.setProgress(buttonSize - 40);
        textButtonSize.setText("Button Size: " + buttonSize + "dp");
    }

    // Remove volume key handling since we want gesture-based control
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        // Allow normal volume key behavior - no longer blocking them
        return super.dispatchKeyEvent(event);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == OVERLAY_PERMISSION_REQUEST) {
            if (Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Overlay permission granted", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Overlay permission denied. App won't work properly.",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == NOTIFICATION_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Notification permission granted", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Notification permission denied", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Check if service is running and update button states accordingly
        // This is a simplified check - you might want to implement a proper service status check
        updateUI();
    }
}