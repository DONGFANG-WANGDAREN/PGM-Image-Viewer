package com.DONGFANG_WANGDAREN.Reader_RX.app;

import android.app.Application;

import androidx.annotation.NonNull;

public class AppPgmImageViewer extends Application {

    private static final String TAG = "AppPgmImageViewer";

    @Override
    public void onCreate() {
        super.onCreate();
        AppConfig.init(this);
        AppLogger.init(this);
        AppLogger.i(TAG, "Application started.");

        Thread.UncaughtExceptionHandler defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            AppLogger.e(TAG, "Uncaught exception on thread=" + getThreadName(thread), throwable);
            if (defaultHandler != null) {
                defaultHandler.uncaughtException(thread, throwable);
            }
        });
    }

    @NonNull
    private String getThreadName(@NonNull Thread thread) {
        String threadName = thread.getName();
        return threadName == null || threadName.isEmpty() ? "unknown" : threadName;
    }
}
