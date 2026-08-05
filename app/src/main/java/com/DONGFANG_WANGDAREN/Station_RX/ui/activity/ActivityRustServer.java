package com.DONGFANG_WANGDAREN.Station_RX.ui.activity;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.DONGFANG_WANGDAREN.Station_RX.R;
import com.DONGFANG_WANGDAREN.Station_RX.app.AppConfig;
import com.DONGFANG_WANGDAREN.Station_RX.app.AppLogger;
import com.DONGFANG_WANGDAREN.Station_RX.rust.RustServerBridge;
import com.DONGFANG_WANGDAREN.Station_RX.storage.AppStoragePaths;
import com.DONGFANG_WANGDAREN.Station_RX.websocket.LanServerHelper;
import com.DONGFANG_WANGDAREN.Station_RX.websocket.NetworkInfoHelper;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.enums.ReadyState;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ActivityRustServer extends AppCompatActivity {

    private static final String TAG = "ActivityRustServer";
    private static final int RUST_PORT_OFFSET = 1000;
    private static final String ACTION_REFRESH_SERVICES_NOTIFICATION = "com.DONGFANG_WANGDAREN.Station_RX.REFRESH_SERVICES_NOTIFICATION";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final SimpleDateFormat isoFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.US);

    private TextView statusText;
    private TextView wsAddressText;
    private TextView browserAddressText;
    private TextView logText;
    private ScrollView scrollViewLog;
    private EditText editTextMessage;
    private MaterialButton toggleButton;
    private MaterialButton sendButton;
    private MaterialButton imageButton;

    @Nullable
    private WebSocketClient monitorSocket;
    private boolean destroyed;

    private final Runnable reconnectRunnable = this::ensureMonitorSocket;

    private final ActivityResultLauncher<String> imagePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            result -> {
                if (result != null) {
                    sendSelectedImage(result);
                }
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rust_server);

        MaterialToolbar toolbar = findViewById(R.id.toolbar_rust_server);
        toolbar.setNavigationOnClickListener(view -> finish());

        statusText = findViewById(R.id.text_view_rust_server_status);
        wsAddressText = findViewById(R.id.text_view_rust_server_ws_address);
        browserAddressText = findViewById(R.id.text_view_rust_server_browser_address);
        logText = findViewById(R.id.text_view_rust_server_log);
        scrollViewLog = findViewById(R.id.scroll_view_rust_server_log);
        editTextMessage = findViewById(R.id.edit_text_rust_server_message);
        toggleButton = findViewById(R.id.button_rust_server_toggle);
        sendButton = findViewById(R.id.button_rust_server_send);
        imageButton = findViewById(R.id.button_rust_server_image);

        toggleButton.setOnClickListener(view -> toggleServer());
        sendButton.setOnClickListener(view -> sendCustomMessage());
        imageButton.setOnClickListener(view -> pickImage());
        browserAddressText.setOnClickListener(view -> openBrowser());

        refreshUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshUi();
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        handler.removeCallbacks(reconnectRunnable);
        disconnectMonitorSocket();
        super.onDestroy();
    }

    private void toggleServer() {
        if (RustServerBridge.isRunning()) {
            boolean stopped = RustServerBridge.stopServer();
            AppLogger.i(TAG, "Rust server stop result: " + stopped);
            Toast.makeText(this, R.string.rust_server_stopped, Toast.LENGTH_SHORT).show();
        } else {
            String ip = LanServerHelper.getDisplayHost();
            int preferredPort = AppConfig.get().getHttpPort() + RUST_PORT_OFFSET;
            int port = LanServerHelper.findAvailablePort(preferredPort);
            String bindHost = "0.0.0.0";
            String displayHost = ip;
            String webRoot = prepareWebRoot();
            String uploadRoot = ensureUploadRoot();
            String chatImagesRoot = ensureChatImagesRoot();
            String deviceInfoJson = buildDeviceInfoJson(port);
            String result = RustServerBridge.startServer(bindHost, displayHost, port, webRoot, uploadRoot, chatImagesRoot, deviceInfoJson);
            AppLogger.i(TAG, "Rust server start result: " + result + " on port " + port);
            Toast.makeText(this, R.string.rust_server_started, Toast.LENGTH_SHORT).show();
        }
        refreshUi();
        sendBroadcast(new Intent(ACTION_REFRESH_SERVICES_NOTIFICATION));
    }

    private void refreshUi() {
        boolean running = RustServerBridge.isRunning();
        String wsAddress = getWebSocketAddress();
        String browserAddress = getBrowserChatAddress();

        if (running) {
            statusText.setText(R.string.rust_server_status_running);
            statusText.setTextColor(ContextCompat.getColor(this, R.color.green_600));
            toggleButton.setText(R.string.rust_server_stop);
            toggleButton.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.red_500));
            ensureMonitorSocket();
        } else {
            statusText.setText(R.string.rust_server_status_stopped);
            statusText.setTextColor(ContextCompat.getColor(this, R.color.slate_900));
            toggleButton.setText(R.string.rust_server_start);
            toggleButton.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.green_600));
            disconnectMonitorSocket();
        }

        wsAddressText.setText(wsAddress != null ? wsAddress : "Unknown");
        browserAddressText.setText(browserAddress != null ? browserAddress : "Unknown");
        sendButton.setEnabled(running);
        imageButton.setEnabled(running);
    }

    @Nullable
    private String getWebSocketAddress() {
        String baseAddress = RustServerBridge.getCurrentAddress();
        if (baseAddress == null || baseAddress.isEmpty()) {
            return null;
        }
        return baseAddress.replace("http://", "ws://") + "/ws";
    }

    @Nullable
    private String getBrowserChatAddress() {
        String baseAddress = RustServerBridge.getCurrentAddress();
        if (baseAddress == null || baseAddress.isEmpty()) {
            return null;
        }
        return baseAddress + "/chat";
    }

    @Nullable
    private String getLoopbackBaseAddress() {
        int currentPort = RustServerBridge.getCurrentPort();
        if (!RustServerBridge.isRunning() || currentPort <= 0) {
            return null;
        }
        return "http://127.0.0.1:" + currentPort;
    }

    @Nullable
    private String getLoopbackMonitorSocketAddress() {
        int currentPort = RustServerBridge.getCurrentPort();
        if (!RustServerBridge.isRunning() || currentPort <= 0) {
            return null;
        }
        try {
            return "ws://127.0.0.1:" + currentPort + "/ws?name="
                    + URLEncoder.encode(AppConfig.get().getSenderApp() + "-monitor", StandardCharsets.UTF_8.name())
                    + "&monitor=1";
        } catch (Exception exception) {
            AppLogger.e(TAG, "Failed to encode rust monitor socket name.", exception);
            return null;
        }
    }

    private void ensureMonitorSocket() {
        if (destroyed || !RustServerBridge.isRunning()) {
            return;
        }
        if (monitorSocket != null
                && (monitorSocket.isOpen() || monitorSocket.getReadyState() == ReadyState.NOT_YET_CONNECTED)) {
            return;
        }
        String socketAddress = getLoopbackMonitorSocketAddress();
        if (socketAddress == null || socketAddress.isEmpty()) {
            return;
        }
        try {
            monitorSocket = new WebSocketClient(URI.create(socketAddress)) {
                @Override
                public void onOpen(ServerHandshake handshakedata) {
                    AppLogger.i(TAG, "Rust monitor socket connected.");
                }

                @Override
                public void onMessage(String message) {
                    handleIncomingPayload(message);
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    AppLogger.i(TAG, "Rust monitor socket closed: " + reason);
                    monitorSocket = null;
                    if (!destroyed && RustServerBridge.isRunning()) {
                        handler.removeCallbacks(reconnectRunnable);
                        handler.postDelayed(reconnectRunnable, 1000L);
                    }
                }

                @Override
                public void onError(Exception ex) {
                    AppLogger.e(TAG, "Rust monitor socket error.", ex);
                }
            };
            monitorSocket.connect();
        } catch (Exception exception) {
            AppLogger.e(TAG, "Failed to connect rust monitor socket.", exception);
        }
    }

    private void disconnectMonitorSocket() {
        handler.removeCallbacks(reconnectRunnable);
        if (monitorSocket != null) {
            try {
                monitorSocket.close();
            } catch (Exception ignored) {
                // Ignore close failure.
            }
            monitorSocket = null;
        }
    }

    private void openBrowser() {
        String url = getBrowserChatAddress();
        if (url == null || url.isEmpty()) {
            return;
        }
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        startActivity(intent);
    }

    private void sendCustomMessage() {
        if (!RustServerBridge.isRunning()) {
            showToast(R.string.websocket_error_not_connected);
            return;
        }
        String message = editTextMessage.getText().toString().trim();
        if (message.isEmpty()) {
            return;
        }
        new Thread(() -> {
            boolean success = postTextMessage(message);
            runOnUiThread(() -> {
                if (success) {
                    editTextMessage.setText("");
                } else {
                    showToast(R.string.websocket_error_send_failed);
                }
            });
        }).start();
    }

    private boolean postTextMessage(@NonNull String message) {
        String baseAddress = getLoopbackBaseAddress();
        if (baseAddress == null || baseAddress.isEmpty()) {
            return false;
        }
        HttpURLConnection connection = null;
        try {
            byte[] body = ("message=" + URLEncoder.encode(message, StandardCharsets.UTF_8.name()))
                    .getBytes(StandardCharsets.UTF_8);
            connection = (HttpURLConnection) new URL(baseAddress + "/api/chat/send-text").openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
            connection.setRequestProperty("Content-Length", String.valueOf(body.length));
            try (OutputStream outputStream = connection.getOutputStream()) {
                outputStream.write(body);
            }
            return parseSuccessResponse(connection);
        } catch (Exception exception) {
            AppLogger.e(TAG, "Failed to post rust text message.", exception);
            return false;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private void pickImage() {
        imagePickerLauncher.launch("image/*");
    }

    private void sendSelectedImage(@NonNull Uri imageUri) {
        if (!RustServerBridge.isRunning()) {
            showToast(R.string.websocket_error_not_connected);
            return;
        }
        new Thread(() -> {
            boolean success = uploadImage(imageUri);
            if (!success) {
                runOnUiThread(() -> showToast(R.string.websocket_error_send_failed));
            }
        }).start();
    }

    private boolean uploadImage(@NonNull Uri imageUri) {
        String baseAddress = getLoopbackBaseAddress();
        if (baseAddress == null || baseAddress.isEmpty()) {
            return false;
        }
        HttpURLConnection connection = null;
        String boundary = "----StationRx" + System.currentTimeMillis();
        try {
            connection = (HttpURLConnection) new URL(baseAddress + "/api/chat/upload-image").openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
            String fileName = buildUploadFileName(imageUri);
            try (OutputStream outputStream = connection.getOutputStream();
                 InputStream inputStream = getContentResolver().openInputStream(imageUri)) {
                if (inputStream == null) {
                    return false;
                }
                String header = "--" + boundary + "\r\n"
                        + "Content-Disposition: form-data; name=\"image\"; filename=\"" + fileName + "\"\r\n"
                        + "Content-Type: " + resolveImageContentType(fileName) + "\r\n\r\n";
                outputStream.write(header.getBytes(StandardCharsets.UTF_8));
                byte[] buffer = new byte[8192];
                int read;
                while ((read = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, read);
                }
                outputStream.write("\r\n".getBytes(StandardCharsets.UTF_8));
                outputStream.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
                outputStream.flush();
            }
            return parseSuccessResponse(connection);
        } catch (Exception exception) {
            AppLogger.e(TAG, "Failed to upload rust chat image.", exception);
            return false;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private boolean parseSuccessResponse(@NonNull HttpURLConnection connection) {
        try {
            int code = connection.getResponseCode();
            InputStream stream = code >= 200 && code < 400 ? connection.getInputStream() : connection.getErrorStream();
            if (stream == null) {
                return false;
            }
            String body;
            try (InputStream inputStream = stream;
                 ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096];
                int read;
                while ((read = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, read);
                }
                body = outputStream.toString(StandardCharsets.UTF_8.name());
            }
            if (code < 200 || code >= 300) {
                return false;
            }
            JSONObject jsonObject = new JSONObject(body);
            return jsonObject.optBoolean("success", false);
        } catch (Exception exception) {
            AppLogger.e(TAG, "Failed to parse rust response.", exception);
            return false;
        }
    }

    @NonNull
    private String buildUploadFileName(@NonNull Uri imageUri) {
        String lastSegment = imageUri.getLastPathSegment();
        if (lastSegment != null && lastSegment.contains(".")) {
            return lastSegment;
        }
        String extension = resolveImageExtension(imageUri);
        return new SimpleDateFormat(AppConfig.get().getChatImageFileNameFormat(), Locale.US).format(new Date()) + extension;
    }

    @NonNull
    private String resolveImageExtension(@NonNull Uri imageUri) {
        String lastSegment = imageUri.getLastPathSegment();
        if (lastSegment != null) {
            int dotIndex = lastSegment.lastIndexOf('.');
            if (dotIndex >= 0 && dotIndex < lastSegment.length() - 1) {
                String extension = lastSegment.substring(dotIndex + 1).toLowerCase(Locale.US);
                if (extension.equals("png") || extension.equals("jpg") || extension.equals("jpeg")
                        || extension.equals("gif") || extension.equals("webp") || extension.equals("bmp")) {
                    return "." + extension;
                }
            }
        }
        return ".jpg";
    }

    @NonNull
    private String resolveImageContentType(@NonNull String fileName) {
        String lower = fileName.toLowerCase(Locale.US);
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".gif")) {
            return "image/gif";
        }
        if (lower.endsWith(".webp")) {
            return "image/webp";
        }
        if (lower.endsWith(".bmp")) {
            return "image/bmp";
        }
        return "image/jpeg";
    }

    private void handleIncomingPayload(@NonNull String payload) {
        runOnUiThread(() -> {
            try {
                JSONObject jsonObject = new JSONObject(payload);
                String sender = jsonObject.optString("from", "Unknown");
                String message = jsonObject.optString("message", "");
                boolean system = jsonObject.optBoolean("system", false);
                String type = jsonObject.optString("type", AppConfig.get().getMessageTypeText());
                String timestamp = extractTime(jsonObject.optString("timestamp", ""));
                if (AppConfig.get().getMessageTypeImage().equals(type) && jsonObject.has("url")) {
                    appendImageMessage(timestamp, sender, jsonObject.optString("url", ""), system);
                    return;
                }
                String prefix = system ? "[System]" : "[" + sender + "]";
                appendToLog("[" + timestamp + "] " + prefix + " " + message);
                scrollToBottomWithoutFocus();
            } catch (JSONException exception) {
                appendToLog(payload);
                scrollToBottomWithoutFocus();
            }
        });
    }

    @NonNull
    private String extractTime(@Nullable String isoTimestamp) {
        if (isoTimestamp == null || isoTimestamp.isEmpty()) {
            return timeFormat.format(new Date());
        }
        try {
            Date parsed = isoFormat.parse(isoTimestamp);
            return parsed != null ? timeFormat.format(parsed) : isoTimestamp;
        } catch (ParseException exception) {
            return isoTimestamp;
        }
    }

    private void appendToLog(@NonNull CharSequence text) {
        if (logText.getText().length() > 0) {
            logText.append("\n");
        }
        logText.append(text);
        logText.setMovementMethod(LinkMovementMethod.getInstance());
    }

    private void appendImageMessage(@NonNull String timestamp, @NonNull String sender, @NonNull String imageUrl, boolean system) {
        String label = getString(R.string.websocket_image_label);
        String displayText = "[" + timestamp + "] " + (system ? "[System]" : "[" + sender + "]") + " " + label;
        SpannableString spannable = new SpannableString(displayText);
        ClickableSpan span = new ClickableSpan() {
            @Override
            public void onClick(@NonNull View widget) {
                showImageViewer(imageUrl);
            }
        };
        int start = displayText.lastIndexOf(label);
        if (start >= 0) {
            spannable.setSpan(span, start, start + label.length(), SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        appendToLog(spannable);
        scrollToBottomWithoutFocus();
    }

    private void scrollToBottomWithoutFocus() {
        scrollViewLog.post(() -> {
            if (scrollViewLog.getChildCount() == 0) {
                return;
            }
            int contentHeight = scrollViewLog.getChildAt(0).getHeight();
            int scrollViewHeight = scrollViewLog.getHeight();
            int scrollTo = Math.max(0, contentHeight - scrollViewHeight);
            scrollViewLog.scrollTo(0, scrollTo);
        });
    }

    private void showToast(int stringResId) {
        Toast.makeText(this, stringResId, Toast.LENGTH_SHORT).show();
    }

    private void showImageViewer(@NonNull String imageUrl) {
        String fileName = extractFileNameFromUrl(imageUrl);
        if (fileName == null) {
            showToast(R.string.websocket_image_open_failed);
            return;
        }
        File imageFile = new File(AppStoragePaths.resolveWebSocketChatImagesDirectory(this), fileName);
        if (!imageFile.exists()) {
            showToast(R.string.websocket_image_open_failed);
            return;
        }
        Bitmap bitmap = BitmapFactory.decodeFile(imageFile.getAbsolutePath());
        if (bitmap == null) {
            showToast(R.string.websocket_image_open_failed);
            return;
        }
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_image_viewer);
        ImageView imageView = dialog.findViewById(R.id.image_view_dialog);
        if (imageView != null) {
            imageView.setImageBitmap(bitmap);
            imageView.setOnClickListener(view -> dialog.dismiss());
        }
        dialog.show();
    }

    @Nullable
    private static String extractFileNameFromUrl(@NonNull String url) {
        if (TextUtils.isEmpty(url)) {
            return null;
        }
        int lastSlash = url.lastIndexOf('/');
        if (lastSlash < 0 || lastSlash >= url.length() - 1) {
            return null;
        }
        String candidate = url.substring(lastSlash + 1);
        int queryIndex = candidate.indexOf('?');
        if (queryIndex >= 0) {
            candidate = candidate.substring(0, queryIndex);
        }
        return candidate.isEmpty() ? null : candidate;
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
    private String buildDeviceInfoJson(int httpPort) {
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
            String ip = LanServerHelper.getDisplayHost();
            network.put("localIp", ip != null ? ip : "");
            network.put("webSocketPort", httpPort);
            network.put("httpPort", httpPort);
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
            Intent intent = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
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
}
