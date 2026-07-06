package com.DONGFANG_WANGDAREN.PGM_Image_Viewer;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;

public class ViewPictureZoom extends AppCompatImageView {

    private static final String TAG = "ViewPictureZoom";
    private final Matrix matrixPicture = new Matrix();
    private final ScaleGestureDetector detectorScale;

    private float scaleMinimum = 1.0f;
    private float scaleMaximum = 8.0f;
    private float scaleCurrent = 1.0f;

    public ViewPictureZoom(@NonNull Context context) {
        this(context, null);
    }

    public ViewPictureZoom(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ViewPictureZoom(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setScaleType(ScaleType.MATRIX);
        detectorScale = new ScaleGestureDetector(context, new ListenerScale());
    }

    public void setBitmap(@NonNull Bitmap bitmap) {
        AppLogger.d(TAG, "Set bitmap. width=" + bitmap.getWidth() + ", height=" + bitmap.getHeight());
        setImageBitmap(bitmap);
        post(this::resetScaleToFit);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (getDrawable() == null) {
            return false;
        }

        detectorScale.onTouchEvent(event);
        return true;
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        resetScaleToFit();
    }

    private void resetScaleToFit() {
        Drawable drawable = getDrawable();
        if (drawable == null || getWidth() == 0 || getHeight() == 0) {
            return;
        }

        float drawableWidth = drawable.getIntrinsicWidth();
        float drawableHeight = drawable.getIntrinsicHeight();
        if (drawableWidth <= 0.0f || drawableHeight <= 0.0f) {
            return;
        }

        float scaleX = getWidth() / drawableWidth;
        float scaleY = getHeight() / drawableHeight;
        scaleMinimum = Math.min(scaleX, scaleY);
        scaleMaximum = Math.max(scaleMinimum * 8.0f, 8.0f);
        scaleCurrent = scaleMinimum;
        AppLogger.d(TAG, "Reset scale. min=" + scaleMinimum + ", max=" + scaleMaximum + ", current=" + scaleCurrent);

        applyMatrix();
    }

    private void applyMatrix() {
        Drawable drawable = getDrawable();
        if (drawable == null) {
            return;
        }

        float drawableWidth = drawable.getIntrinsicWidth();
        float drawableHeight = drawable.getIntrinsicHeight();
        float scaledWidth = drawableWidth * scaleCurrent;
        float scaledHeight = drawableHeight * scaleCurrent;
        float translateX = (getWidth() - scaledWidth) / 2.0f;
        float translateY = (getHeight() - scaledHeight) / 2.0f;

        matrixPicture.reset();
        matrixPicture.postScale(scaleCurrent, scaleCurrent);
        matrixPicture.postTranslate(translateX, translateY);
        setImageMatrix(matrixPicture);
    }

    private final class ListenerScale extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            float updatedScale = scaleCurrent * detector.getScaleFactor();
            scaleCurrent = Math.max(scaleMinimum, Math.min(updatedScale, scaleMaximum));
            AppLogger.d(TAG, "Scale gesture. factor=" + detector.getScaleFactor() + ", current=" + scaleCurrent);
            applyMatrix();
            return true;
        }
    }
}
