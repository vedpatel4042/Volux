package com.example.volux;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageButton;
import androidx.core.app.NotificationCompat;

public class VoluxService extends Service {

    private WindowManager windowManager;
    private View floatingView, gestureBoxView, dotIndicatorView;
    private ImageButton btnVolumeUp, btnVolumeDown;
    private AudioManager audioManager;
    private SharedPreferences prefs;
    private Handler hideHandler;
    private Runnable hideRunnable;
    private ScaleGestureDetector scaleGestureDetector;

    // Button size limits (in dp)
    private static final int MIN_BUTTON_SIZE = 40;
    private static final int MAX_BUTTON_SIZE = 120;
    private int currentButtonSize = 60;

    // Gesture box variables
    private int currentGestureBoxWidth = 200;
    private int currentGestureBoxHeight = 100;
    private static final int MIN_GESTURE_BOX_SIZE = 40;
    private static final int MAX_GESTURE_BOX_SIZE = 400;

    // Opacity and visibility states
    private boolean isControlsVisible = true;
    private boolean isMoveMode = false;
    private static final float FULL_OPACITY = 1.0f;
    private static final float HIDDEN_OPACITY = 0.0f;

    private static final String CHANNEL_ID = "VoluxService";
    private static final int NOTIFICATION_ID = 1;
    private static final String TAG = "VoluxService";

    @Override
    public void onCreate() {
        super.onCreate();

        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        prefs = getSharedPreferences("VoluxPrefs", MODE_PRIVATE);
        hideHandler = new Handler(Looper.getMainLooper());

        createNotificationChannel();

        // Start foreground service
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, createNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIFICATION_ID, createNotification());
        }

        createDotIndicator();
        setupControls();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "UPDATE_SETTINGS".equals(intent.getAction())) {
            updateControlsBasedOnSettings();
        }
        return START_STICKY;
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

    private Notification createNotification() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0,
                notificationIntent, PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Volux Active")
                .setContentText("Assistive volume controls running")
                .setSmallIcon(R.drawable.ic_volume)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    private void createDotIndicator() {
        dotIndicatorView = new DotIndicatorView(this);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                dpToPx(12), dpToPx(12),
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                        WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);

        params.gravity = Gravity.START | Gravity.CENTER_VERTICAL;
        params.x = 0;
        params.y = 0;

        windowManager.addView(dotIndicatorView, params);
        dotIndicatorView.setVisibility(View.GONE);
    }

    private void setupControls() {
        boolean floatingButtons = prefs.getBoolean("floating_buttons", true);
        boolean gestureBox = prefs.getBoolean("gesture_box", false);
        boolean bothModes = prefs.getBoolean("both_modes", false);
        isMoveMode = prefs.getBoolean("move_mode_enabled", false);

        if (floatingButtons || bothModes) {
            createFloatingButtons();
        }

        if (gestureBox || bothModes) {
            createGestureBox();
        }

        // Start with controls visible
        showControls();
        startAutoHideTimer();
    }

    private void createFloatingButtons() {
        floatingView = LayoutInflater.from(this).inflate(R.layout.floating_buttons_enhanced, null);

        btnVolumeUp = floatingView.findViewById(R.id.btnVolumeUp);
        btnVolumeDown = floatingView.findViewById(R.id.btnVolumeDown);

        setupButtonListeners();
        setupPinchToResize();
        setupDragAndDrop();
        updateButtonAppearance();

        WindowManager.LayoutParams params = createWindowParams();
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = prefs.getInt("floating_buttons_x", 100);
        params.y = prefs.getInt("floating_buttons_y", 100);

        windowManager.addView(floatingView, params);
    }

    private void createGestureBox() {
        gestureBoxView = new GestureBoxView(this);

        WindowManager.LayoutParams params = createWindowParams();
        currentGestureBoxWidth = prefs.getInt("gesture_box_width", 200);
        currentGestureBoxHeight = prefs.getInt("gesture_box_height", 100);
        params.width = dpToPx(currentGestureBoxWidth);
        params.height = dpToPx(currentGestureBoxHeight);

        params.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
        params.x = prefs.getInt("gesture_box_x", 50);
        params.y = prefs.getInt("gesture_box_y", 0);

        windowManager.addView(gestureBoxView, params);
    }

    private WindowManager.LayoutParams createWindowParams() {
        return new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                        WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
    }

    private void setupButtonListeners() {
        btnVolumeUp.setOnClickListener(v -> {
            animateButtonBounce(btnVolumeUp, true);
            adjustVolume(AudioManager.ADJUST_RAISE);
            onUserInteraction();
        });

        btnVolumeDown.setOnClickListener(v -> {
            animateButtonBounce(btnVolumeDown, false);
            adjustVolume(AudioManager.ADJUST_LOWER);
            onUserInteraction();
        });
    }

    private void setupPinchToResize() {
        scaleGestureDetector = new ScaleGestureDetector(this, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                if (isMoveMode) {
                    float scaleFactor = detector.getScaleFactor();
                    int newSize = (int) (currentButtonSize * scaleFactor);
                    newSize = Math.max(MIN_BUTTON_SIZE, Math.min(MAX_BUTTON_SIZE, newSize));

                    if (newSize != currentButtonSize) {
                        currentButtonSize = newSize;
                        updateButtonSize();
                        prefs.edit().putInt("current_button_size", currentButtonSize).apply();
                    }
                    onUserInteraction();
                }
                return true;
            }
        });
    }

    private void setupDragAndDrop() {
        floatingView.setOnTouchListener(new View.OnTouchListener() {
            private int initialX, initialY;
            private float initialTouchX, initialTouchY;
            private boolean isDragging = false;
            private long touchStartTime;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                scaleGestureDetector.onTouchEvent(event);

                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        touchStartTime = System.currentTimeMillis();
                        if (isMoveMode) {
                            WindowManager.LayoutParams params = (WindowManager.LayoutParams) floatingView.getLayoutParams();
                            initialX = params.x;
                            initialY = params.y;
                            initialTouchX = event.getRawX();
                            initialTouchY = event.getRawY();
                            isDragging = false;
                        }
                        onUserInteraction();
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        if (isMoveMode && event.getPointerCount() == 1) {
                            float deltaX = event.getRawX() - initialTouchX;
                            float deltaY = event.getRawY() - initialTouchY;

                            if (Math.abs(deltaX) > 10 || Math.abs(deltaY) > 10) {
                                isDragging = true;
                                WindowManager.LayoutParams params = (WindowManager.LayoutParams) floatingView.getLayoutParams();
                                params.x = initialX + (int) deltaX;
                                params.y = initialY + (int) deltaY;
                                windowManager.updateViewLayout(floatingView, params);

                                // Save position
                                prefs.edit()
                                        .putInt("floating_buttons_x", params.x)
                                        .putInt("floating_buttons_y", params.y)
                                        .apply();
                            }
                        }
                        return true;

                    case MotionEvent.ACTION_UP:
                        if (!isControlsVisible && !isDragging) {
                            // Tapped on invisible controls - show with zoom animation
                            showControlsWithZoomAnimation();
                        } else if (!isDragging && System.currentTimeMillis() - touchStartTime < 200) {
                            // Quick tap - ensure visibility
                            showControls();
                        }
                        onUserInteraction();
                        return true;
                }
                return false;
            }
        });
    }

    private class GestureBoxView extends View {
        private float startY;
        private int initialX, initialY;
        private float initialTouchX, initialTouchY;
        private ScaleGestureDetector gestureScaleDetector;
        private Paint paint;
        private boolean isResizing = false;

        public GestureBoxView(Context context) {
            super(context);
            setBackgroundResource(R.drawable.gesture_box_background);

            paint = new Paint();
            paint.setColor(0x88000000);
            paint.setStyle(Paint.Style.FILL);

            gestureScaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                @Override
                public boolean onScale(ScaleGestureDetector detector) {
                    if (isMoveMode) {
                        float scaleFactor = detector.getScaleFactor();

                        int newWidth = (int) (currentGestureBoxWidth * scaleFactor);
                        int newHeight = (int) (currentGestureBoxHeight * scaleFactor);

                        newWidth = Math.max(MIN_GESTURE_BOX_SIZE, Math.min(MAX_GESTURE_BOX_SIZE, newWidth));
                        newHeight = Math.max(MIN_GESTURE_BOX_SIZE, Math.min(MAX_GESTURE_BOX_SIZE, newHeight));

                        if (newWidth != currentGestureBoxWidth || newHeight != currentGestureBoxHeight) {
                            currentGestureBoxWidth = newWidth;
                            currentGestureBoxHeight = newHeight;
                            updateGestureBoxSize();
                            prefs.edit()
                                    .putInt("gesture_box_width", currentGestureBoxWidth)
                                    .putInt("gesture_box_height", currentGestureBoxHeight)
                                    .apply();
                        }
                        isResizing = true;
                    }
                    return true;
                }
            });
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            // Draw a subtle border to indicate gesture area
            canvas.drawRoundRect(0, 0, getWidth(), getHeight(), 12, 12, paint);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            gestureScaleDetector.onTouchEvent(event);

            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    startY = event.getY();
                    isResizing = false;

                    if (isMoveMode) {
                        WindowManager.LayoutParams params = (WindowManager.LayoutParams) getLayoutParams();
                        initialX = params.x;
                        initialY = params.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                    }

                    if (!isControlsVisible) {
                        showControlsWithZoomAnimation();
                    } else {
                        onUserInteraction();
                    }
                    return true;

                case MotionEvent.ACTION_MOVE:
                    if (isResizing) {
                        return true; // Let scale detector handle resizing
                    }

                    if (isMoveMode && event.getPointerCount() == 1) {
                        // Handle dragging in move mode
                        float deltaX = event.getRawX() - initialTouchX;
                        float deltaY = event.getRawY() - initialTouchY;

                        WindowManager.LayoutParams params = (WindowManager.LayoutParams) getLayoutParams();
                        params.x = initialX + (int) deltaX;
                        params.y = initialY + (int) deltaY;
                        windowManager.updateViewLayout(this, params);

                        // Save position
                        prefs.edit()
                                .putInt("gesture_box_x", params.x)
                                .putInt("gesture_box_y", params.y)
                                .apply();
                    } else if (!isMoveMode) {
                        // Handle volume gestures when not in move mode
                        float deltaY = startY - event.getY();
                        float threshold = dpToPx(20);

                        if (Math.abs(deltaY) > threshold) {
                            if (deltaY > 0) {
                                adjustVolume(AudioManager.ADJUST_RAISE);
                                animateVolumeGesture(true);
                            } else {
                                adjustVolume(AudioManager.ADJUST_LOWER);
                                animateVolumeGesture(false);
                            }
                            startY = event.getY();
                            onUserInteraction();
                        }
                    }
                    return true;

                case MotionEvent.ACTION_UP:
                    isResizing = false;
                    onUserInteraction();
                    return true;
            }
            return super.onTouchEvent(event);
        }
    }

    private class DotIndicatorView extends View {
        private Paint dotPaint;

        public DotIndicatorView(Context context) {
            super(context);
            dotPaint = new Paint();
            dotPaint.setColor(0xFF00BCD4); // Cyan color
            dotPaint.setAntiAlias(true);

            setOnClickListener(v -> {
                if (!isControlsVisible) {
                    showControlsWithZoomAnimation();
                }
            });
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float radius = Math.min(getWidth(), getHeight()) / 2f;
            canvas.drawCircle(getWidth() / 2f, getHeight() / 2f, radius, dotPaint);
        }
    }

    private void showControls() {
        isControlsVisible = true;
        float opacity = prefs.getFloat("opacity", 0.8f);

        if (floatingView != null) {
            floatingView.animate()
                    .alpha(opacity)
                    .setDuration(200)
                    .start();
        }

        if (gestureBoxView != null) {
            gestureBoxView.animate()
                    .alpha(opacity)
                    .setDuration(200)
                    .start();
        }

        dotIndicatorView.setVisibility(View.GONE);
    }

    private void hideControls() {
        if (isMoveMode) return; // Don't hide in move mode

        isControlsVisible = false;

        if (floatingView != null) {
            floatingView.animate()
                    .alpha(HIDDEN_OPACITY)
                    .setDuration(300)
                    .start();
        }

        if (gestureBoxView != null) {
            gestureBoxView.animate()
                    .alpha(HIDDEN_OPACITY)
                    .setDuration(300)
                    .start();
        }

        dotIndicatorView.setVisibility(View.VISIBLE);
    }

    private void showControlsWithZoomAnimation() {
        isControlsVisible = true;
        float opacity = prefs.getFloat("opacity", 0.8f);

        if (floatingView != null) {
            floatingView.setScaleX(0.1f);
            floatingView.setScaleY(0.1f);
            floatingView.setAlpha(0f);

            AnimatorSet animSet = new AnimatorSet();
            ObjectAnimator scaleX = ObjectAnimator.ofFloat(floatingView, "scaleX", 0.1f, 1.0f);
            ObjectAnimator scaleY = ObjectAnimator.ofFloat(floatingView, "scaleY", 0.1f, 1.0f);
            ObjectAnimator alpha = ObjectAnimator.ofFloat(floatingView, "alpha", 0f, opacity);

            animSet.playTogether(scaleX, scaleY, alpha);
            animSet.setDuration(400);
            animSet.start();
        }

        if (gestureBoxView != null) {
            gestureBoxView.setScaleX(0.1f);
            gestureBoxView.setScaleY(0.1f);
            gestureBoxView.setAlpha(0f);

            AnimatorSet animSet = new AnimatorSet();
            ObjectAnimator scaleX = ObjectAnimator.ofFloat(gestureBoxView, "scaleX", 0.1f, 1.0f);
            ObjectAnimator scaleY = ObjectAnimator.ofFloat(gestureBoxView, "scaleY", 0.1f, 1.0f);
            ObjectAnimator alpha = ObjectAnimator.ofFloat(gestureBoxView, "alpha", 0f, opacity);

            animSet.playTogether(scaleX, scaleY, alpha);
            animSet.setDuration(400);
            animSet.start();
        }

        dotIndicatorView.setVisibility(View.GONE);
    }

    private void onUserInteraction() {
        if (!isMoveMode) {
            showControls();
            startAutoHideTimer();
        }
    }

    private void startAutoHideTimer() {
        if (hideHandler != null && hideRunnable != null) {
            hideHandler.removeCallbacks(hideRunnable);
        }

        int hideDelay = prefs.getInt("auto_hide_delay", 3000);
        hideRunnable = this::hideControls;
        hideHandler.postDelayed(hideRunnable, hideDelay);
    }

    private void animateButtonBounce(View button, boolean upDirection) {
        float translationY = upDirection ? -20f : 20f;

        ObjectAnimator bounce = ObjectAnimator.ofFloat(button, "translationY", 0f, translationY, 0f);
        bounce.setDuration(300);
        bounce.start();

        ObjectAnimator scaleX = ObjectAnimator.ofFloat(button, "scaleX", 1f, 1.1f, 1f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(button, "scaleY", 1f, 1.1f, 1f);
        scaleX.setDuration(200);
        scaleY.setDuration(200);
        scaleX.start();
        scaleY.start();
    }

    private void animateVolumeGesture(boolean volumeUp) {
        if (gestureBoxView == null) return;

        float translationY = volumeUp ? -30f : 30f;
        ObjectAnimator bounce = ObjectAnimator.ofFloat(gestureBoxView, "translationY", 0f, translationY, 0f);
        bounce.setDuration(200);
        bounce.start();

        // Scale feedback
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(gestureBoxView, "scaleX", 1f, 1.05f, 1f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(gestureBoxView, "scaleY", 1f, 1.05f, 1f);
        scaleX.setDuration(150);
        scaleY.setDuration(150);
        scaleX.start();
        scaleY.start();
    }

    private void updateButtonSize() {
        if (btnVolumeUp != null && btnVolumeDown != null) {
            int sizePx = dpToPx(currentButtonSize);
            ViewGroup.LayoutParams paramsUp = btnVolumeUp.getLayoutParams();
            ViewGroup.LayoutParams paramsDown = btnVolumeDown.getLayoutParams();

            paramsUp.width = sizePx;
            paramsUp.height = sizePx;
            paramsDown.width = sizePx;
            paramsDown.height = sizePx;

            btnVolumeUp.setLayoutParams(paramsUp);
            btnVolumeDown.setLayoutParams(paramsDown);
        }
    }

    private void updateGestureBoxSize() {
        if (gestureBoxView != null) {
            WindowManager.LayoutParams params = (WindowManager.LayoutParams) gestureBoxView.getLayoutParams();
            params.width = dpToPx(currentGestureBoxWidth);
            params.height = dpToPx(currentGestureBoxHeight);
            windowManager.updateViewLayout(gestureBoxView, params);
        }
    }

    private void updateButtonAppearance() {
        currentButtonSize = prefs.getInt("current_button_size", 60);
        updateButtonSize();
    }

    private void updateControlsBasedOnSettings() {
        // Update move mode
        isMoveMode = prefs.getBoolean("move_mode_enabled", false);

        // Remove existing views
        if (floatingView != null) {
            windowManager.removeView(floatingView);
            floatingView = null;
        }
        if (gestureBoxView != null) {
            windowManager.removeView(gestureBoxView);
            gestureBoxView = null;
        }

        // Recreate based on current settings
        setupControls();
    }

    public void updateGestureBoxCustomSize(int width, int height) {
        currentGestureBoxWidth = Math.max(MIN_GESTURE_BOX_SIZE, Math.min(MAX_GESTURE_BOX_SIZE, width));
        currentGestureBoxHeight = Math.max(MIN_GESTURE_BOX_SIZE, Math.min(MAX_GESTURE_BOX_SIZE, height));
        updateGestureBoxSize();
        prefs.edit()
                .putInt("gesture_box_width", currentGestureBoxWidth)
                .putInt("gesture_box_height", currentGestureBoxHeight)
                .apply();
    }

    private void adjustVolume(int direction) {
        try {
            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, 0);
        } catch (Exception e) {
            Log.e(TAG, "Error adjusting volume", e);
        }
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (hideHandler != null && hideRunnable != null) {
            hideHandler.removeCallbacks(hideRunnable);
        }

        try {
            if (floatingView != null) {
                windowManager.removeView(floatingView);
            }
            if (gestureBoxView != null) {
                windowManager.removeView(gestureBoxView);
            }
            if (dotIndicatorView != null) {
                windowManager.removeView(dotIndicatorView);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error removing views", e);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}


//package com.example.volux;
//
//import android.animation.AnimatorSet;
//import android.animation.ObjectAnimator;
//import android.animation.ValueAnimator;
//import android.app.Notification;
//import android.app.NotificationChannel;
//import android.app.NotificationManager;
//import android.app.PendingIntent;
//import android.app.Service;
//import android.content.Context;
//import android.content.Intent;
//import android.content.SharedPreferences;
//import android.content.pm.ServiceInfo;
//import android.graphics.Canvas;
//import android.graphics.Paint;
//import android.graphics.PixelFormat;
//import android.media.AudioManager;
//import android.os.Build;
//import android.os.Handler;
//import android.os.IBinder;
//import android.os.Looper;
//import android.util.Log;
//import android.view.Gravity;
//import android.view.LayoutInflater;
//import android.view.MotionEvent;
//import android.view.ScaleGestureDetector;
//import android.view.View;
//import android.view.ViewGroup;
//import android.view.WindowManager;
//import android.view.animation.AccelerateDecelerateInterpolator;
//import android.widget.ImageButton;
//import androidx.core.app.NotificationCompat;
//
//public class VoluxService extends Service {
//
//    private WindowManager windowManager;
//    private View floatingView, gestureBoxView, dotIndicatorView;
//    private ImageButton btnVolumeUp, btnVolumeDown;
//    private AudioManager audioManager;
//    private SharedPreferences prefs;
//    private Handler hideHandler;
//    private Runnable hideRunnable;
//    private ScaleGestureDetector scaleGestureDetector;
//
//    // Button size limits (in dp)
//    private static final int MIN_BUTTON_SIZE = 40;
//    private static final int MAX_BUTTON_SIZE = 120;
//    private int currentButtonSize = 60;
//
//    // Gesture box variables
//    private int currentGestureBoxWidth = 200;
//    private int currentGestureBoxHeight = 100;
//    private static final int MIN_GESTURE_BOX_SIZE = 30;
//    private static final int MAX_GESTURE_BOX_SIZE = 400;
//
//    // Opacity and visibility states
//    private boolean isControlsVisible = true;
//    private boolean isMoveMode = false;
//    private static final float FULL_OPACITY = 1.0f;
//    private static final float HIDDEN_OPACITY = 0.0f;
//
//    private static final String CHANNEL_ID = "VoluxService";
//    private static final int NOTIFICATION_ID = 1;
//    private static final String TAG = "VoluxService";
//
//    @Override
//    public void onCreate() {
//        super.onCreate();
//
//        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
//        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
//        prefs = getSharedPreferences("VoluxPrefs", MODE_PRIVATE);
//        hideHandler = new Handler(Looper.getMainLooper());
//
//        createNotificationChannel();
//
//        // Start foreground service
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
//            startForeground(NOTIFICATION_ID, createNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
//        } else {
//            startForeground(NOTIFICATION_ID, createNotification());
//        }
//
//
//        setupControls();
//    }
//
//    @Override
//    public int onStartCommand(Intent intent, int flags, int startId) {
//        if (intent != null && "UPDATE_SETTINGS".equals(intent.getAction())) {
//            updateControlsBasedOnSettings();
//        }
//        return START_STICKY;
//    }
//
//    private void createNotificationChannel() {
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//            NotificationChannel channel = new NotificationChannel(
//                    CHANNEL_ID, "Volux Service", NotificationManager.IMPORTANCE_LOW);
//            channel.setDescription("Assistive Volume Control Service");
//            channel.setShowBadge(false);
//            NotificationManager manager = getSystemService(NotificationManager.class);
//            manager.createNotificationChannel(channel);
//        }
//    }
//
//    private Notification createNotification() {
//        Intent notificationIntent = new Intent(this, MainActivity.class);
//        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0,
//                notificationIntent, PendingIntent.FLAG_IMMUTABLE);
//
//        return new NotificationCompat.Builder(this, CHANNEL_ID)
//                .setContentTitle("Volux Active")
//                .setContentText("Assistive volume controls running")
//                .setSmallIcon(R.drawable.ic_volume)
//                .setContentIntent(pendingIntent)
//                .setOngoing(true)
//                .setPriority(NotificationCompat.PRIORITY_LOW)
//                .build();
//    }
//
//
//    private void setupControls() {
//        boolean floatingButtons = prefs.getBoolean("floating_buttons", true);
//        boolean gestureBox = prefs.getBoolean("gesture_box", false);
//        boolean bothModes = prefs.getBoolean("both_modes", false);
//        isMoveMode = prefs.getBoolean("move_mode_enabled", false);
//
//        if (floatingButtons || bothModes) {
//            createFloatingButtons();
//        }
//
//        if (gestureBox || bothModes) {
//            createGestureBox();
//        }
//
//        // Start with controls visible
//        showControls();
//        startAutoHideTimer();
//    }
//
//    private void createFloatingButtons() {
//        floatingView = LayoutInflater.from(this).inflate(R.layout.floating_buttons_enhanced, null);
//
//        btnVolumeUp = floatingView.findViewById(R.id.btnVolumeUp);
//        btnVolumeDown = floatingView.findViewById(R.id.btnVolumeDown);
//
//        setupButtonListeners();
//        setupPinchToResize();
//        setupDragAndDrop();
//        updateButtonAppearance();
//
//        WindowManager.LayoutParams params = createWindowParams();
//        params.gravity = Gravity.TOP | Gravity.START;
//        params.x = prefs.getInt("floating_buttons_x", 100);
//        params.y = prefs.getInt("floating_buttons_y", 100);
//
//        windowManager.addView(floatingView, params);
//    }
//
//    private void createGestureBox() {
//        gestureBoxView = new GestureBoxView(this);
//
//        WindowManager.LayoutParams params = createWindowParams();
//        currentGestureBoxWidth = prefs.getInt("gesture_box_width", 200);
//        currentGestureBoxHeight = prefs.getInt("gesture_box_height", 100);
//        params.width = dpToPx(currentGestureBoxWidth);
//        params.height = dpToPx(currentGestureBoxHeight);
//
//        params.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
//        params.x = prefs.getInt("gesture_box_x", 50);
//        params.y = prefs.getInt("gesture_box_y", 0);
//
//        windowManager.addView(gestureBoxView, params);
//    }
//
//    private WindowManager.LayoutParams createWindowParams() {
//        return new WindowManager.LayoutParams(
//                WindowManager.LayoutParams.WRAP_CONTENT,
//                WindowManager.LayoutParams.WRAP_CONTENT,
//                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
//                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
//                        WindowManager.LayoutParams.TYPE_PHONE,
//                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
//                PixelFormat.TRANSLUCENT);
//    }
//
//    private void setupButtonListeners() {
//        btnVolumeUp.setOnClickListener(v -> {
//            animateButtonBounce(btnVolumeUp, true);
//            adjustVolume(AudioManager.ADJUST_RAISE);
//            onUserInteraction();
//        });
//
//        btnVolumeDown.setOnClickListener(v -> {
//            animateButtonBounce(btnVolumeDown, false);
//            adjustVolume(AudioManager.ADJUST_LOWER);
//            onUserInteraction();
//        });
//    }
//
//    private void setupPinchToResize() {
//        scaleGestureDetector = new ScaleGestureDetector(this, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
//            @Override
//            public boolean onScale(ScaleGestureDetector detector) {
//                if (isMoveMode) {
//                    float scaleFactor = detector.getScaleFactor();
//                    int newSize = (int) (currentButtonSize * scaleFactor);
//                    newSize = Math.max(MIN_BUTTON_SIZE, Math.min(MAX_BUTTON_SIZE, newSize));
//
//                    if (newSize != currentButtonSize) {
//                        currentButtonSize = newSize;
//                        updateButtonSize();
//                        prefs.edit().putInt("current_button_size", currentButtonSize).apply();
//                    }
//                    onUserInteraction();
//                }
//                return true;
//            }
//        });
//    }
//
//    private void setupDragAndDrop() {
//        floatingView.setOnTouchListener(new View.OnTouchListener() {
//            private int initialX, initialY;
//            private float initialTouchX, initialTouchY;
//            private boolean isDragging = false;
//            private long touchStartTime;
//
//            @Override
//            public boolean onTouch(View v, MotionEvent event) {
//                scaleGestureDetector.onTouchEvent(event);
//
//                switch (event.getAction()) {
//                    case MotionEvent.ACTION_DOWN:
//                        touchStartTime = System.currentTimeMillis();
//                        if (isMoveMode) {
//                            WindowManager.LayoutParams params = (WindowManager.LayoutParams) floatingView.getLayoutParams();
//                            initialX = params.x;
//                            initialY = params.y;
//                            initialTouchX = event.getRawX();
//                            initialTouchY = event.getRawY();
//                            isDragging = false;
//                        }
//                        onUserInteraction();
//                        return true;
//
//                    case MotionEvent.ACTION_MOVE:
//                        if (isMoveMode && event.getPointerCount() == 1) {
//                            float deltaX = event.getRawX() - initialTouchX;
//                            float deltaY = event.getRawY() - initialTouchY;
//
//                            if (Math.abs(deltaX) > 10 || Math.abs(deltaY) > 10) {
//                                isDragging = true;
//                                WindowManager.LayoutParams params = (WindowManager.LayoutParams) floatingView.getLayoutParams();
//                                params.x = initialX + (int) deltaX;
//                                params.y = initialY + (int) deltaY;
//                                windowManager.updateViewLayout(floatingView, params);
//
//                                // Save position
//                                prefs.edit()
//                                        .putInt("floating_buttons_x", params.x)
//                                        .putInt("floating_buttons_y", params.y)
//                                        .apply();
//                            }
//                        }
//                        return true;
//
//                    case MotionEvent.ACTION_UP:
//                        if (!isControlsVisible && !isDragging) {
//                            // Tapped on invisible controls - show with zoom animation
//                            showControlsWithZoomAnimation();
//                        } else if (!isDragging && System.currentTimeMillis() - touchStartTime < 200) {
//                            // Quick tap - ensure visibility
//                            showControls();
//                        }
//                        onUserInteraction();
//                        return true;
//                }
//                return false;
//            }
//        });
//    }
//
//    private class GestureBoxView extends View {
//        private float startY;
//        private int initialX, initialY;
//        private float initialTouchX, initialTouchY;
//        private ScaleGestureDetector gestureScaleDetector;
//        private Paint paint;
//        private boolean isResizing = false;
//
//        public GestureBoxView(Context context) {
//            super(context);
//            setBackgroundResource(R.drawable.gesture_box_background);
//
//            paint = new Paint();
//            paint.setColor(0x88000000);
//            paint.setStyle(Paint.Style.FILL);
//
//            gestureScaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
//                @Override
//                public boolean onScale(ScaleGestureDetector detector) {
//                    if (isMoveMode) {
//                        float scaleFactor = detector.getScaleFactor();
//
//                        int newWidth = (int) (currentGestureBoxWidth * scaleFactor);
//                        int newHeight = (int) (currentGestureBoxHeight * scaleFactor);
//
//                        newWidth = Math.max(MIN_GESTURE_BOX_SIZE, Math.min(MAX_GESTURE_BOX_SIZE, newWidth));
//                        newHeight = Math.max(MIN_GESTURE_BOX_SIZE, Math.min(MAX_GESTURE_BOX_SIZE, newHeight));
//
//                        if (newWidth != currentGestureBoxWidth || newHeight != currentGestureBoxHeight) {
//                            currentGestureBoxWidth = newWidth;
//                            currentGestureBoxHeight = newHeight;
//                            updateGestureBoxSize();
//                            prefs.edit()
//                                    .putInt("gesture_box_width", currentGestureBoxWidth)
//                                    .putInt("gesture_box_height", currentGestureBoxHeight)
//                                    .apply();
//                        }
//                        isResizing = true;
//                    }
//                    return true;
//                }
//            });
//        }
//
//        @Override
//        protected void onDraw(Canvas canvas) {
//            super.onDraw(canvas);
//            // Draw a subtle border to indicate gesture area
//            canvas.drawRoundRect(0, 0, getWidth(), getHeight(), 12, 12, paint);
//        }
//
//        @Override
//        public boolean onTouchEvent(MotionEvent event) {
//            gestureScaleDetector.onTouchEvent(event);
//
//            switch (event.getAction()) {
//                case MotionEvent.ACTION_DOWN:
//                    startY = event.getY();
//                    isResizing = false;
//
//                    if (isMoveMode) {
//                        WindowManager.LayoutParams params = (WindowManager.LayoutParams) getLayoutParams();
//                        initialX = params.x;
//                        initialY = params.y;
//                        initialTouchX = event.getRawX();
//                        initialTouchY = event.getRawY();
//                    }
//
//                    if (!isControlsVisible) {
//                        showControlsWithZoomAnimation();
//                    } else {
//                        onUserInteraction();
//                    }
//                    return true;
//
//                case MotionEvent.ACTION_MOVE:
//                    if (isResizing) {
//                        return true; // Let scale detector handle resizing
//                    }
//
//                    if (isMoveMode && event.getPointerCount() == 1) {
//                        // Handle dragging in move mode
//                        float deltaX = event.getRawX() - initialTouchX;
//                        float deltaY = event.getRawY() - initialTouchY;
//
//                        WindowManager.LayoutParams params = (WindowManager.LayoutParams) getLayoutParams();
//                        params.x = initialX + (int) deltaX;
//                        params.y = initialY + (int) deltaY;
//                        windowManager.updateViewLayout(this, params);
//
//                        // Save position
//                        prefs.edit()
//                                .putInt("gesture_box_x", params.x)
//                                .putInt("gesture_box_y", params.y)
//                                .apply();
//                    } else if (!isMoveMode) {
//                        // Handle volume gestures when not in move mode
//                        float deltaY = startY - event.getY();
//                        float threshold = dpToPx(20);
//
//                        if (Math.abs(deltaY) > threshold) {
//                            if (deltaY > 0) {
//                                adjustVolume(AudioManager.ADJUST_RAISE);
//                                animateVolumeGesture(true);
//                            } else {
//                                adjustVolume(AudioManager.ADJUST_LOWER);
//                                animateVolumeGesture(false);
//                            }
//                            startY = event.getY();
//                            onUserInteraction();
//                        }
//                    }
//                    return true;
//
//                case MotionEvent.ACTION_UP:
//                    isResizing = false;
//                    onUserInteraction();
//                    return true;
//            }
//            return super.onTouchEvent(event);
//        }
//    }
//
//    private class DotIndicatorView extends View {
//        private Paint dotPaint;
//
//        public DotIndicatorView(Context context) {
//            super(context);
//            dotPaint = new Paint();
//            dotPaint.setColor(0xFF00BCD4); // Cyan color
//            dotPaint.setAntiAlias(true);
//
//            setOnClickListener(v -> {
//                if (!isControlsVisible) {
//                    showControlsWithZoomAnimation();
//                }
//            });
//        }
//
//        @Override
//        protected void onDraw(Canvas canvas) {
//            super.onDraw(canvas);
//            float radius = Math.min(getWidth(), getHeight()) / 2f;
//            canvas.drawCircle(getWidth() / 2f, getHeight() / 2f, radius, dotPaint);
//        }
//    }
//
//    private void showControls() {
//        isControlsVisible = true;
//        float opacity = prefs.getFloat("opacity", 0.8f);
//
//        if (floatingView != null) {
//            floatingView.animate()
//                    .alpha(opacity)
//                    .setDuration(200)
//                    .start();
//        }
//
//        if (gestureBoxView != null) {
//            gestureBoxView.animate()
//                    .alpha(opacity)
//                    .setDuration(200)
//                    .start();
//        }
//
//
//        dotIndicatorView.setVisibility(View.GONE);
//    }
//
//    private void hideControls() {
//        if (isMoveMode) return; // Don't hide in move mode
//
//        isControlsVisible = false;
//
//        if (floatingView != null) {
//            floatingView.animate()
//                    .alpha(HIDDEN_OPACITY)
//                    .setDuration(300)
//                    .start();
//        }
//
//        if (gestureBoxView != null) {
//            gestureBoxView.animate()
//                    .alpha(HIDDEN_OPACITY)
//                    .setDuration(300)
//                    .start();
//        }
//
//        dotIndicatorView.setVisibility(View.VISIBLE);
//    }
//
//    private void showControlsWithZoomAnimation() {
//        isControlsVisible = true;
//        float opacity = prefs.getFloat("opacity", 0.8f);
//
//        if (floatingView != null) {
//            floatingView.setScaleX(0.1f);
//            floatingView.setScaleY(0.1f);
//            floatingView.setAlpha(0f);
//
//            AnimatorSet animSet = new AnimatorSet();
//            ObjectAnimator scaleX = ObjectAnimator.ofFloat(floatingView, "scaleX", 0.1f, 1.0f);
//            ObjectAnimator scaleY = ObjectAnimator.ofFloat(floatingView, "scaleY", 0.1f, 1.0f);
//            ObjectAnimator alpha = ObjectAnimator.ofFloat(floatingView, "alpha", 0f, opacity);
//
//            animSet.playTogether(scaleX, scaleY, alpha);
//            animSet.setDuration(400);
//            animSet.start();
//        }
//
//        if (gestureBoxView != null) {
//            gestureBoxView.setScaleX(0.1f);
//            gestureBoxView.setScaleY(0.1f);
//            gestureBoxView.setAlpha(0f);
//
//            AnimatorSet animSet = new AnimatorSet();
//            ObjectAnimator scaleX = ObjectAnimator.ofFloat(gestureBoxView, "scaleX", 0.1f, 1.0f);
//            ObjectAnimator scaleY = ObjectAnimator.ofFloat(gestureBoxView, "scaleY", 0.1f, 1.0f);
//            ObjectAnimator alpha = ObjectAnimator.ofFloat(gestureBoxView, "alpha", 0f, opacity);
//
//            animSet.playTogether(scaleX, scaleY, alpha);
//            animSet.setDuration(400);
//            animSet.start();
//        }
//
//        dotIndicatorView.setVisibility(View.GONE);
//    }
//
//    private void onUserInteraction() {
//        if (!isMoveMode) {
//            showControls();
//            startAutoHideTimer();
//        }
//    }
//
//    private void startAutoHideTimer() {
//        if (hideHandler != null && hideRunnable != null) {
//            hideHandler.removeCallbacks(hideRunnable);
//        }
//
//        int hideDelay = prefs.getInt("auto_hide_delay", 3000);
//        hideRunnable = this::hideControls;
//        hideHandler.postDelayed(hideRunnable, hideDelay);
//    }
//
//    private void animateButtonBounce(View button, boolean upDirection) {
//        float translationY = upDirection ? -20f : 20f;
//
//        ObjectAnimator bounce = ObjectAnimator.ofFloat(button, "translationY", 0f, translationY, 0f);
//        bounce.setDuration(300);
//        bounce.start();
//
//        ObjectAnimator scaleX = ObjectAnimator.ofFloat(button, "scaleX", 1f, 1.1f, 1f);
//        ObjectAnimator scaleY = ObjectAnimator.ofFloat(button, "scaleY", 1f, 1.1f, 1f);
//        scaleX.setDuration(200);
//        scaleY.setDuration(200);
//        scaleX.start();
//        scaleY.start();
//    }
//
//    private void animateVolumeGesture(boolean volumeUp) {
//        if (gestureBoxView == null) return;
//
//        float translationY = volumeUp ? -30f : 30f;
//        ObjectAnimator bounce = ObjectAnimator.ofFloat(gestureBoxView, "translationY", 0f, translationY, 0f);
//        bounce.setDuration(200);
//        bounce.start();
//
//        // Scale feedback
//        ObjectAnimator scaleX = ObjectAnimator.ofFloat(gestureBoxView, "scaleX", 1f, 1.05f, 1f);
//        ObjectAnimator scaleY = ObjectAnimator.ofFloat(gestureBoxView, "scaleY", 1f, 1.05f, 1f);
//        scaleX.setDuration(150);
//        scaleY.setDuration(150);
//        scaleX.start();
//        scaleY.start();
//    }
//
//    private void updateButtonSize() {
//        if (btnVolumeUp != null && btnVolumeDown != null) {
//            int sizePx = dpToPx(currentButtonSize);
//            ViewGroup.LayoutParams paramsUp = btnVolumeUp.getLayoutParams();
//            ViewGroup.LayoutParams paramsDown = btnVolumeDown.getLayoutParams();
//
//            paramsUp.width = sizePx;
//            paramsUp.height = sizePx;
//            paramsDown.width = sizePx;
//            paramsDown.height = sizePx;
//
//            btnVolumeUp.setLayoutParams(paramsUp);
//            btnVolumeDown.setLayoutParams(paramsDown);
//        }
//    }
//
//    private void updateGestureBoxSize() {
//        if (gestureBoxView != null) {
//            WindowManager.LayoutParams params = (WindowManager.LayoutParams) gestureBoxView.getLayoutParams();
//            params.width = dpToPx(currentGestureBoxWidth);
//            params.height = dpToPx(currentGestureBoxHeight);
//            windowManager.updateViewLayout(gestureBoxView, params);
//        }
//    }
//
//    private void updateButtonAppearance() {
//        currentButtonSize = prefs.getInt("current_button_size", 60);
//        updateButtonSize();
//    }
//
//    private void updateControlsBasedOnSettings() {
//        // Update move mode
//        isMoveMode = prefs.getBoolean("move_mode_enabled", false);
//
//        // Remove existing views
//        if (floatingView != null) {
//            windowManager.removeView(floatingView);
//            floatingView = null;
//        }
//        if (gestureBoxView != null) {
//            windowManager.removeView(gestureBoxView);
//            gestureBoxView = null;
//        }
//
//        // Recreate based on current settings
//        setupControls();
//    }
//
//    public void updateGestureBoxCustomSize(int width, int height) {
//        currentGestureBoxWidth = Math.max(MIN_GESTURE_BOX_SIZE, Math.min(MAX_GESTURE_BOX_SIZE, width));
//        currentGestureBoxHeight = Math.max(MIN_GESTURE_BOX_SIZE, Math.min(MAX_GESTURE_BOX_SIZE, height));
//        updateGestureBoxSize();
//        prefs.edit()
//                .putInt("gesture_box_width", currentGestureBoxWidth)
//                .putInt("gesture_box_height", currentGestureBoxHeight)
//                .apply();
//    }
//
//    private void adjustVolume(int direction) {
//        try {
//            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, 0);
//        } catch (Exception e) {
//            Log.e(TAG, "Error adjusting volume", e);
//        }
//    }
//
//    private int dpToPx(int dp) {
//        return (int) (dp * getResources().getDisplayMetrics().density);
//    }
//
//    @Override
//    public void onDestroy() {
//        super.onDestroy();
//
//        if (hideHandler != null && hideRunnable != null) {
//            hideHandler.removeCallbacks(hideRunnable);
//        }
//
//        try {
//            if (floatingView != null) {
//                windowManager.removeView(floatingView);
//            }
//            if (gestureBoxView != null) {
//                windowManager.removeView(gestureBoxView);
//            }
//            if (dotIndicatorView != null) {
//                windowManager.removeView(dotIndicatorView);
//            }
//        } catch (Exception e) {
//            Log.e(TAG, "Error removing views", e);
//        }
//    }
//
//    @Override
//    public IBinder onBind(Intent intent) {
//        return null;
//    }
//}
