package com.DONGFANG_WANGDAREN.Station_RX.rust;

import com.DONGFANG_WANGDAREN.Station_RX.app.AppLogger;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class RustServerBridge {

    private static final String TAG = "RustServerBridge";
    private static volatile int currentPort = -1;
    @NonNull
    private static volatile String currentDisplayHost = "127.0.0.1";

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
        String result = nativeStartServer(bindHost, displayHost, port, webRoot, uploadRoot, chatImagesRoot, deviceInfoJson);
        if ("started".equalsIgnoreCase(result)) {
            currentDisplayHost = displayHost;
            currentPort = port;
        }
        return result;
    }

    public static boolean stopServer() {
        boolean stopped = nativeStopServer();
        if (stopped) {
            currentPort = -1;
        }
        return stopped;
    }

    public static boolean isRunning() {
        return nativeIsRunning();
    }

    @Nullable
    public static String getCurrentAddress() {
        if (!isRunning() || currentPort <= 0) {
            return null;
        }
        return "http://" + currentDisplayHost + ":" + currentPort;
    }

    public static int getCurrentPort() {
        return currentPort;
    }

    @NonNull
    private static native String nativeStartServer(@NonNull String bindHost, @NonNull String displayHost, int port, @NonNull String webRoot, @NonNull String uploadRoot, @NonNull String chatImagesRoot, @NonNull String deviceInfoJson);

    private static native boolean nativeStopServer();

    private static native boolean nativeIsRunning();
}
