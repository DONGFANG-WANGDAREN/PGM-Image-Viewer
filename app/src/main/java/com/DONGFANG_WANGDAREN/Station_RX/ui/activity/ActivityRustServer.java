package com.DONGFANG_WANGDAREN.Station_RX.ui.activity;

import com.DONGFANG_WANGDAREN.Station_RX.R;
import com.DONGFANG_WANGDAREN.Station_RX.app.AppConfig;
import com.DONGFANG_WANGDAREN.Station_RX.app.AppLogger;
import com.DONGFANG_WANGDAREN.Station_RX.rust.RustServerBridge;
import com.DONGFANG_WANGDAREN.Station_RX.storage.AppStoragePaths;
import com.DONGFANG_WANGDAREN.Station_RX.websocket.NetworkInfoHelper;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;

import android.content.Context;
import android.content.Intent;
import android.content.res.AssetManager;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.StatFs;
import android.util.DisplayMetrics;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.Locale;

public class ActivityRustServer extends AppCompatActivity {

    private static final String TAG = "ActivityRustServer";
    private static final int RUST_PORT_OFFSET = 1000;
    private static final String ACTION_REFRESH_SERVICES_NOTIFICATION = "com.DONGFANG_WANGDAREN.Station_RX.REFRESH_SERVICES_NOTIFICATION";

    private TextView statusText;
    private TextView addressText;
    private TextView logText;
    private MaterialButton toggleButton;
    private MaterialButton browserButton;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rust_server);

        MaterialToolbar toolbar = findViewById(R.id.toolbar_rust_server);
        toolbar.setNavigationOnClickListener(view -> finish());

        statusText = findViewById(R.id.text_view_rust_server_status);
        addressText = findViewById(R.id.text_view_rust_server_address);
        logText = findViewById(R.id.text_view_rust_server_log);
        toggleButton = findViewById(R.id.button_rust_server_toggle);
        browserButton = findViewById(R.id.button_rust_server_open_browser);

        toggleButton.setOnClickListener(view -> toggleServer());
        browserButton.setOnClickListener(view -> openBrowser());

        refreshUi();
        logEndpoints();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshUi();
    }

    private void toggleServer() {
        if (RustServerBridge.isRunning()) {
            boolean stopped = RustServerBridge.stopServer();
            AppLogger.i(TAG, "Rust server stop result: " + stopped);
            Toast.makeText(this, R.string.rust_server_stopped, Toast.LENGTH_SHORT).show();
        } else {
            String ip = getLocalIpAddress();
            if (ip == null || ip.isEmpty()) {
                ip = "0.0.0.0";
            }
            int port = AppConfig.get().getHttpPort() + RUST_PORT_OFFSET;
            String bindHost = "0.0.0.0";
            String displayHost = ip;
            String webRoot = prepareWebRoot();
            String uploadRoot = ensureUploadRoot();
            String chatImagesRoot = ensureChatImagesRoot();
            String deviceInfoJson = buildDeviceInfoJson();
            String result = RustServerBridge.startServer(bindHost, displayHost, port, webRoot, uploadRoot, chatImagesRoot, deviceInfoJson);
            AppLogger.i(TAG, "Rust server start result: " + result + " on port " + port);
            Toast.makeText(this, R.string.rust_server_started, Toast.LENGTH_SHORT).show();
        }
        refreshUi();
        sendBroadcast(new Intent(ACTION_REFRESH_SERVICES_NOTIFICATION));
    }

    @NonNull
    private String prepareWebRoot() {
        File webRoot = new File(getFilesDir(), "rust_web");
        try {
            copyAssetsDir(getAssets(), "web", webRoot);
        } catch (IOException exception) {
            AppLogger.e(TAG, "Failed to copy web assets.", exception);
        }
        return webRoot.getAbsolutePath();
    }

    @NonNull
    private String ensureUploadRoot() {
        File uploadRoot = AppStoragePaths.resolveWebSocketChatDirectory(this);
        if (!uploadRoot.exists() && !uploadRoot.mkdirs()) {
            AppLogger.e(TAG, "Failed to create upload root: " + uploadRoot.getAbsolutePath());
        }
        return uploadRoot.getAbsolutePath();
    }

    @NonNull
    private String ensureChatImagesRoot() {
        File imagesRoot = AppStoragePaths.resolveWebSocketChatImagesDirectory(this);
        if (!imagesRoot.exists() && !imagesRoot.mkdirs()) {
            AppLogger.e(TAG, "Failed to create chat images root: " + imagesRoot.getAbsolutePath());
        }
        return imagesRoot.getAbsolutePath();
    }

    private void copyAssetsDir(@NonNull AssetManager assets, @NonNull String assetPath, @NonNull File targetDir) throws IOException {
        if (!targetDir.exists() && !targetDir.mkdirs()) {
            throw new IOException("Cannot create directory: " + targetDir.getAbsolutePath());
        }
        String[] entries = assets.list(assetPath);
        if (entries == null || entries.length == 0) {
            copyAssetFile(assets, assetPath, targetDir);
            return;
        }
        for (String entry : entries) {
            String childAssetPath = assetPath.isEmpty() ? entry : assetPath + "/" + entry;
            File childTarget = new File(targetDir, entry);
            String[] childEntries = assets.list(childAssetPath);
            if (childEntries == null || childEntries.length == 0) {
                copyAssetFile(assets, childAssetPath, childTarget);
            } else {
                copyAssetsDir(assets, childAssetPath, childTarget);
            }
        }
    }

    private void copyAssetFile(@NonNull AssetManager assets, @NonNull String assetPath, @NonNull File targetFile) throws IOException {
        try (InputStream input = assets.open(assetPath);
             OutputStream output = new FileOutputStream(targetFile)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
        }
    }

    @NonNull
    private String buildDeviceInfoJson() {
        JSONObject info = new JSONObject();
        try {
            info.put("appName", AppStoragePaths.resolveBaseDirectory(this).getName());
            info.put("packageName", getPackageName());
            info.put("model", Build.MODEL);
            info.put("manufacturer", Build.MANUFACTURER);
            info.put("brand", Build.BRAND);
            info.put("device", Build.DEVICE);
            info.put("product", Build.PRODUCT);
            info.put("hardware", Build.HARDWARE);
            info.put("board", Build.BOARD);
            info.put("bootloader", Build.BOOTLOADER);
            info.put("androidVersion", Build.VERSION.RELEASE);
            info.put("sdk", Build.VERSION.SDK_INT);
            info.put("fingerprint", Build.FINGERPRINT);
            info.put("display", Build.DISPLAY);
            info.put("host", Build.HOST);
            info.put("id", Build.ID);
            info.put("tags", Build.TAGS);
            info.put("type", Build.TYPE);
            info.put("user", Build.USER);
            info.put("time", Build.TIME);

            WindowManager windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
            if (windowManager != null) {
                DisplayMetrics metrics = new DisplayMetrics();
                windowManager.getDefaultDisplay().getRealMetrics(metrics);
                JSONObject display = new JSONObject();
                display.put("widthPixels", metrics.widthPixels);
                display.put("heightPixels", metrics.heightPixels);
                display.put("densityDpi", metrics.densityDpi);
                display.put("density", metrics.density);
                display.put("scaledDensity", metrics.scaledDensity);
                display.put("xdpi", metrics.xdpi);
                display.put("ydpi", metrics.ydpi);
                info.put("display", display);
            }

            JSONObject memory = new JSONObject();
            android.app.ActivityManager activityManager = (android.app.ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
            if (activityManager != null) {
                android.app.ActivityManager.MemoryInfo memoryInfo = new android.app.ActivityManager.MemoryInfo();
                activityManager.getMemoryInfo(memoryInfo);
                memory.put("totalRam", memoryInfo.totalMem);
                memory.put("availableRam", memoryInfo.availMem);
                memory.put("lowMemory", memoryInfo.lowMemory);
            }
            StatFs storageStat = new StatFs(Environment.getExternalStorageDirectory().getAbsolutePath());
            memory.put("totalStorage", storageStat.getTotalBytes());
            memory.put("availableStorage", storageStat.getAvailableBytes());
            info.put("memory", memory);

            info.put("battery", readBatteryInfo());

            JSONObject cpu = new JSONObject();
            cpu.put("cores", Runtime.getRuntime().availableProcessors());
            cpu.put("usage", -1.0);
            info.put("cpu", cpu);

            JSONObject network = new JSONObject();
            String ip = getLocalIpAddress();
            network.put("localIp", ip != null ? ip : "");
            network.put("webSocketPort", AppConfig.get().getWebSocketPort());
            network.put("httpPort", AppConfig.get().getHttpPort());
            network.put("wifiConnected", NetworkInfoHelper.isWifiConnected(this));
            network.put("wifiLinkSpeedMbps", NetworkInfoHelper.getWifiLinkSpeedMbps(this));
            network.put("wifiSignalDbm", NetworkInfoHelper.getWifiSignalDbm(this));
            network.put("wifiSignalLevel", NetworkInfoHelper.getWifiSignalLevel(this));
            network.put("estimatedDistanceMeters", NetworkInfoHelper.estimateWifiDistanceMeters(this));
            info.put("network", network);
        } catch (JSONException exception) {
            AppLogger.e(TAG, "Failed to build device info JSON.", exception);
        }
        return info.toString();
    }

    @NonNull
    private JSONObject readBatteryInfo() {
        JSONObject battery = new JSONObject();
        try {
            Intent intent = registerReceiver(null, new android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (intent != null) {
                int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                int plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
                int temperature = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1);
                int voltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1);
                float percent = level >= 0 && scale > 0 ? (level * 100.0f) / scale : -1;
                battery.put("level", (int) percent);
                battery.put("status", status);
                battery.put("plugged", plugged);
                battery.put("temperature", temperature / 10.0);
                battery.put("voltage", voltage);
            }
        } catch (JSONException exception) {
            AppLogger.e(TAG, "Failed to read battery info.", exception);
        }
        return battery;
    }

    private void refreshUi() {
        boolean running = RustServerBridge.isRunning();
        if (running) {
            statusText.setText(R.string.rust_server_status_running);
            statusText.setTextColor(ContextCompat.getColor(this, R.color.green_600));
            toggleButton.setText(R.string.rust_server_stop);
            toggleButton.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.red_500));

            String ip = getLocalIpAddress();
            int port = AppConfig.get().getHttpPort() + RUST_PORT_OFFSET;
            addressText.setText("http://" + ip + ":" + port);
        } else {
            statusText.setText(R.string.rust_server_status_stopped);
            statusText.setTextColor(ContextCompat.getColor(this, R.color.slate_900));
            toggleButton.setText(R.string.rust_server_start);
            toggleButton.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.green_600));
            addressText.setText("-");
        }
    }

    private void openBrowser() {
        String ip = getLocalIpAddress();
        int port = AppConfig.get().getHttpPort() + RUST_PORT_OFFSET;
        String url = "http://" + ip + ":" + port;
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        startActivity(intent);
    }

    private void logEndpoints() {
        int port = AppConfig.get().getHttpPort() + RUST_PORT_OFFSET;
        String ip = getLocalIpAddress();
        StringBuilder builder = new StringBuilder();
        builder.append("Rust Axum server endpoints\n");
        builder.append("==========================\n\n");
        builder.append("HTTP base:\nhttp://").append(ip).append(":").append(port).append("\n\n");
        builder.append("GET /\n  Chat room (index.html)\n\n");
        builder.append("GET /files\n  File manager\n\n");
        builder.append("GET /login?token=...\n  Token login\n\n");
        builder.append("GET /web-login\n  QR web login page\n\n");
        builder.append("GET /api/config\n  WebSocket / HTTP addresses\n\n");
        builder.append("GET /api/web-login-session /api/session-status /api/confirm /api/qr.png\n  QR login flow\n\n");
        builder.append("GET /api/device\n  Device & network info\n\n");
        builder.append("GET /ws?name=...\n  WebSocket chat\n\n");
        builder.append("GET /api/files?path=...\n  List files\n\n");
        builder.append("GET /api/download?path=...\n  Download file (Range supported)\n\n");
        builder.append("POST /api/upload-init /upload-chunk /upload-finish\n  Chunked upload (2MB chunks)\n\n");
        builder.append("POST /api/chat/upload-image\n  Send chat image\n\n");
        builder.append("Note: this runs in parallel with the existing Java server.");
        logText.setText(builder.toString());
    }

    @Nullable
    private String getLocalIpAddress() {
        try {
            for (NetworkInterface networkInterface : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                for (InetAddress address : Collections.list(networkInterface.getInetAddresses())) {
                    if (!address.isLoopbackAddress() && address instanceof Inet4Address) {
                        return address.getHostAddress();
                    }
                }
            }
        } catch (Exception exception) {
            AppLogger.e(TAG, "Failed to get local IP address.", exception);
        }
        return "127.0.0.1";
    }
}
