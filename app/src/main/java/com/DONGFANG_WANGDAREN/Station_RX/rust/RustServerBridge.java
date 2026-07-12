package com.DONGFANG_WANGDAREN.Station_RX.rust;

import com.DONGFANG_WANGDAREN.Station_RX.app.AppLogger;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class RustServerBridge {

    private static final String TAG = "RustServerBridge";

    static {
        try {
            System.loadLibrary("rust_server");
            AppLogger.i(TAG, "Rust server native library loaded.");
        } catch (Throwable throwable) {
            AppLogger.e(TAG, "Failed to load rust_server library.", throwable);
        }
    }

    private RustServerBridge() {
    }

    @NonNull
    public static String startServer(@NonNull String bindHost, @NonNull String displayHost, int port, @NonNull String webRoot, @NonNull String uploadRoot, @NonNull String chatImagesRoot, @NonNull String deviceInfoJson) {
        return nativeStartServer(bindHost, displayHost, port, webRoot, uploadRoot, chatImagesRoot, deviceInfoJson);
    }

    public static boolean stopServer() {
        return nativeStopServer();
    }

    public static boolean isRunning() {
        return nativeIsRunning();
    }

    @NonNull
    private static native String nativeStartServer(@NonNull String bindHost, @NonNull String displayHost, int port, @NonNull String webRoot, @NonNull String uploadRoot, @NonNull String chatImagesRoot, @NonNull String deviceInfoJson);

    private static native boolean nativeStopServer();

    private static native boolean nativeIsRunning();
}
