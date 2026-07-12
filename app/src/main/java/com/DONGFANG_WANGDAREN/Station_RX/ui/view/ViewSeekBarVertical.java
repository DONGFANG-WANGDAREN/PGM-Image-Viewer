package com.DONGFANG_WANGDAREN.Station_RX.ui.view;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.view.MotionEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatSeekBar;

public class ViewSeekBarVertical extends AppCompatSeekBar {

    public ViewSeekBarVertical(@NonNull Context context) {
        super(context);
    }

    public ViewSeekBarVertical(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public ViewSeekBarVertical(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected synchronized void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(heightMeasureSpec, widthMeasureSpec);
        setMeasuredDimension(getMeasuredHeight(), getMeasuredWidth());
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(height, width, oldHeight, oldWidth);
    }

    @Override
    protected synchronized void onDraw(@NonNull Canvas canvas) {
        canvas.rotate(-90f);
        canvas.translate(-getHeight(), 0f);
        super.onDraw(canvas);
    }

    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event) {
        if (!isEnabled()) {
            return false;
        }
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE:
            case MotionEvent.ACTION_UP:
                float clampedY = Math.max(0f, Math.min(event.getY(), getHeight()));
                int progress = getMax() - Math.round((getMax() * clampedY) / getHeight());
                setProgress(progress);
                onSizeChanged(getWidth(), getHeight(), 0, 0);
                break;
            case MotionEvent.ACTION_CANCEL:
                break;
            default:
                return super.onTouchEvent(event);
        }
        return true;
    }
}
