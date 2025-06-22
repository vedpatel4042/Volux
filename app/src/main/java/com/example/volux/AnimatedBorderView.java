package com.example.volux;


import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.util.AttributeSet;
import android.view.View;
import android.animation.ValueAnimator;
import android.graphics.Color;
import android.graphics.BlurMaskFilter;

public class AnimatedBorderView extends View {

    private Paint paint;
    private Path borderPath;
    private PathMeasure pathMeasure;
    private float phase = 0f;
    private ValueAnimator animator;

    private int[] colors = {Color.CYAN, Color.MAGENTA, Color.YELLOW, Color.WHITE, Color.BLUE};
    private int currentColorIndex = 0;

    public AnimatedBorderView(Context context) {
        super(context);
        init();
    }

    public AnimatedBorderView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(8f);
        paint.setColor(Color.CYAN);

        // Add glow effect
        paint.setMaskFilter(new BlurMaskFilter(10f, BlurMaskFilter.Blur.NORMAL));

        borderPath = new Path();
        pathMeasure = new PathMeasure();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        // Create rounded rectangle path
        float cornerRadius = 20f;
        borderPath.reset();
        borderPath.addRoundRect(10f, 10f, w - 10f, h - 10f, cornerRadius, cornerRadius, Path.Direction.CW);
        pathMeasure.setPath(borderPath, true);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (pathMeasure.getLength() > 0) {
            // Draw the animated border
            float[] pos = new float[2];
            float[] tan = new float[2];

            // Calculate current position on path
            float distance = (phase * pathMeasure.getLength()) % pathMeasure.getLength();
            pathMeasure.getPosTan(distance, pos, tan);

            // Draw multiple moving dots
            for (int i = 0; i < 5; i++) {
                float offsetDistance = (distance + i * 100) % pathMeasure.getLength();
                pathMeasure.getPosTan(offsetDistance, pos, tan);

                paint.setColor(colors[i]);
                canvas.drawCircle(pos[0], pos[1], 6f, paint);
            }
        }
    }

    public void startAnimation() {
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(2000);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.addUpdateListener(animation -> {
            phase = (float) animation.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    public void stopAnimation() {
        if (animator != null) {
            animator.cancel();
        }
    }
}
