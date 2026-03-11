package com.example.timerapp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

public class CircularTimerView extends View {

    private Paint backgroundPaint;
    private Paint progressPaint;
    private Paint warningPaint;
    private RectF rectF;
    private float progress = 1.0f; // 1.0 = full, 0.0 = empty
    private boolean isWarning = false;

    public CircularTimerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public CircularTimerView(Context context) {
        super(context);
        init();
    }

    private void init() {
        backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        backgroundPaint.setStyle(Paint.Style.STROKE);
        backgroundPaint.setStrokeWidth(16f);
        backgroundPaint.setColor(0xFFE0E0E0);

        progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        progressPaint.setStyle(Paint.Style.STROKE);
        progressPaint.setStrokeWidth(16f);
        progressPaint.setStrokeCap(Paint.Cap.ROUND);
        progressPaint.setColor(0xFF4A90D9);

        warningPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        warningPaint.setStyle(Paint.Style.STROKE);
        warningPaint.setStrokeWidth(16f);
        warningPaint.setStrokeCap(Paint.Cap.ROUND);
        warningPaint.setColor(0xFFE74C3C);

        rectF = new RectF();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float width = getWidth();
        float height = getHeight();
        float padding = 20f;

        rectF.set(padding, padding, width - padding, height - padding);

        // Draw background circle
        canvas.drawArc(rectF, -90, 360, false, backgroundPaint);

        // Draw progress arc
        float sweepAngle = 360 * progress;
        canvas.drawArc(rectF, -90, sweepAngle, false, isWarning ? warningPaint : progressPaint);
    }

    public void setProgress(float progress) {
        this.progress = Math.max(0, Math.min(1, progress));
        invalidate();
    }

    public void setWarning(boolean warning) {
        this.isWarning = warning;
        invalidate();
    }
}
