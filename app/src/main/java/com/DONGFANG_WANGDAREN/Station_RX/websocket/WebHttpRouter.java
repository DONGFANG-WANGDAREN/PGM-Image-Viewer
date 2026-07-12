package com.DONGFANG_WANGDAREN.Station_RX.websocket;


import com.DONGFANG_WANGDAREN.Station_RX.app.AppConfig;
import com.DONGFANG_WANGDAREN.Station_RX.app.AppLogger;
import com.DONGFANG_WANGDAREN.Station_RX.storage.AppStoragePaths;
import com.DONGFANG_WANGDAREN.Station_RX.util.QRCodeHelper;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.util.DisplayMetrics;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;

public final class WebHttpRouter {

    private static final String TAG = "WebHttpRouter";
    private static final String COOKIE_NAME = "rrx_token";
    private static final String WEB_AUTH_COOKIE_NAME = "rrx_web_auth";
    private static final long WEB_LOGIN_SESSION_MAX_AGE_MS = 10 * 60 * 1000;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final ConcurrentHashMap<String, WebLoginSession> WEB_LOGIN_SESSIONS = new ConcurrentHashMap<>();

    private final WebSocketService service;
    private final Context context;

    public WebHttpRouter(@NonNull WebSocketService service) {
        this.service = service;
        this.context = service.getApplicationContext();
    }

    @NonNull
    public NanoHTTPD.Response route(@NonNull NanoHTTPD.IHTTPSession session) {
        String uri = session.getUri();
        if (uri == null) {
            uri = "/";
        }
        Map<String, String> params = session.getParms();

        if (uri.startsWith(AppConfig.get().getImageUrlPrefix())) {
            return serveChatImage(uri);
        }

        if (uri.equals("/web-login")) {
            return serveAssetFile("web/web-login.html", "text/html; charset=utf-8");
        }

        if (uri.equals("/api/qr.png")) {
            return serveQrCode(params);
        }

        if (uri.equals("/api/confirm")) {
            return handleConfirm(params);
        }

        if (uri.equals("/api/session-status")) {
            return handleSessionStatus(session, params);
        }

        if (uri.equals("/api/config")) {
            return handleConfig();
        }

        if (uri.equals("/api/web-login-session")) {
            return handleWebLoginSession();
        }

        if (uri.equals("/login")) {
            return handleLogin(session, params);
        }

        if (!uri.equals("/") && !uri.equals("/chat") && !isAuthenticated(session, params)) {
            return serveAssetFile("web/login.html", "text/html; charset=utf-8");
        }

        switch (uri) {
            case "/":
            case "/chat":
                return serveAssetFile("web/index.html", "text/html; charset=utf-8");
            case "/files":
                return serveAssetFile("web/files.html", "text/html; charset=utf-8");
            case "/api/files":
                return handleFileList(params);
            case "/api/download":
                return handleDownload(params);
            case "/api/view":
                return handleView(params);
            case "/api/device":
                return handleDeviceInfo();
            case "/api/upload":
                return handleUpload(session);
            default:
                return newFixedLengthResponse(NanoHTTPD.Response.Status.NOT_FOUND, NanoHTTPD.MIME_PLAINTEXT, "Not found");
        }
    }

    @NonNull
    private NanoHTTPD.Response handleLogin(@NonNull NanoHTTPD.IHTTPSession session, @NonNull Map<String, String> params) {
        String token = params.get("token");
        if (token != null && token.equals(service.getHttpAuthToken())) {
            NanoHTTPD.Response response = newFixedLengthResponse(NanoHTTPD.Response.Status.REDIRECT, NanoHTTPD.MIME_PLAINTEXT, "");
            response.addHeader("Location", "/files");
            response.addHeader("Set-Cookie", COOKIE_NAME + "=" + token + "; Path=/; Max-Age=604800");
            return response;
        }
        return serveAssetFile("web/login.html", "text/html; charset=utf-8");
    }

    @NonNull
    private NanoHTTPD.Response handleConfig() {
        String wsAddress = service.getServerAddress();
        if (wsAddress == null) {
            wsAddress = "ws://" + service.getLocalIpAddress() + ":" + AppConfig.get().getWebSocketPort();
        }
        JSONObject result = new JSONObject();
        try {
            result.put("wsAddress", wsAddress);
        } catch (JSONException exception) {
            AppLogger.e(TAG, "Failed to build config JSON.", exception);
        }
        return jsonResponse(result, NanoHTTPD.Response.Status.OK);
    }

    @NonNull
    private NanoHTTPD.Response handleWebLoginSession() {
        cleanupExpiredWebLoginSessions();
        WebLoginSession webSession = createWebLoginSession();
        String localIp = service.getLocalIpAddress();
        if (localIp == null || localIp.isEmpty()) {
            localIp = "127.0.0.1";
        }
        String loginUrl = "http://" + localIp + ":" + AppConfig.get().getHttpPort() + "/web-login";
        JSONObject result = new JSONObject();
        try {
            result.put("sessionId", webSession.sessionId);
            result.put("token", webSession.token);
            result.put("loginUrl", loginUrl);
        } catch (JSONException exception) {
            AppLogger.e(TAG, "Failed to build web login session JSON.", exception);
        }
        return jsonResponse(result, NanoHTTPD.Response.Status.OK);
    }

    @NonNull
    private NanoHTTPD.Response serveAssetFile(@NonNull String assetPath, @NonNull String mimeType) {
        try {
            InputStream stream = context.getAssets().open(assetPath);
            return NanoHTTPD.newChunkedResponse(NanoHTTPD.Response.Status.OK, mimeType, stream);
        } catch (Exception exception) {
            AppLogger.e(TAG, "Failed to open asset: " + assetPath, exception);
            return newFixedLengthResponse(NanoHTTPD.Response.Status.NOT_FOUND, NanoHTTPD.MIME_PLAINTEXT, "Not found.");
        }
    }

    @NonNull
    private NanoHTTPD.Response serveQrCode(@NonNull Map<String, String> params) {
        String data = params.get("data");
        if (data == null || data.isEmpty()) {
            return newFixedLengthResponse(NanoHTTPD.Response.Status.BAD_REQUEST, NanoHTTPD.MIME_PLAINTEXT, "Missing data.");
        }
        Bitmap bitmap = QRCodeHelper.generateQrCode(data, 512);
        if (bitmap == null) {
            return newFixedLengthResponse(NanoHTTPD.Response.Status.INTERNAL_ERROR, NanoHTTPD.MIME_PLAINTEXT, "Failed to generate QR code.");
        }
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream);
        byte[] bytes = outputStream.toByteArray();
        return newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "image/png", new ByteArrayInputStream(bytes), bytes.length);
    }

    @NonNull
    private NanoHTTPD.Response handleConfirm(@NonNull Map<String, String> params) {
        String sessionId = params.get("session");
        String token = params.get("token");
        String authToken = confirmWebLoginSession(sessionId, token);
        JSONObject result = new JSONObject();
        try {
            result.put("success", authToken != null);
        } catch (JSONException exception) {
            AppLogger.e(TAG, "Failed to build confirm response JSON.", exception);
        }
        if (authToken != null) {
            return jsonResponse(result, NanoHTTPD.Response.Status.OK);
        }
        return jsonResponse(result, NanoHTTPD.Response.Status.UNAUTHORIZED);
    }

    @NonNull
    private NanoHTTPD.Response handleSessionStatus(@NonNull NanoHTTPD.IHTTPSession session, @NonNull Map<String, String> params) {
        String sessionId = params.get("session");
        WebLoginSession webSession = sessionId != null ? WEB_LOGIN_SESSIONS.get(sessionId) : null;
        boolean authenticated = webSession != null && webSession.authenticated;
        JSONObject result = new JSONObject();
        try {
            result.put("authenticated", authenticated);
        } catch (JSONException exception) {
            AppLogger.e(TAG, "Failed to build session status JSON.", exception);
        }
        NanoHTTPD.Response response = jsonResponse(result, NanoHTTPD.Response.Status.OK);
        if (authenticated && webSession != null) {
            response.addHeader("Set-Cookie", WEB_AUTH_COOKIE_NAME + "=" + webSession.authToken + "; Path=/; Max-Age=604800");
        }
        return response;
    }

    private boolean isAuthenticated(@NonNull NanoHTTPD.IHTTPSession session, @NonNull Map<String, String> params) {
        String token = service.getHttpAuthToken();
        if (!token.isEmpty()) {
            String queryToken = params.get("token");
            if (token.equals(queryToken)) {
                return true;
            }
            String existingToken = getCookieValue(session, COOKIE_NAME);
            if (token.equals(existingToken)) {
                return true;
            }
        }
        String webAuthToken = getCookieValue(session, WEB_AUTH_COOKIE_NAME);
        if (webAuthToken != null && isWebLoginSessionAuthenticated(webAuthToken)) {
            return true;
        }
        return false;
    }

    @Nullable
    private static String getCookieValue(@NonNull NanoHTTPD.IHTTPSession session, @NonNull String name) {
        String cookieHeader = session.getHeaders().get("cookie");
        if (cookieHeader == null) {
            return null;
        }
        String[] cookies = cookieHeader.split(";");
        for (String cookie : cookies) {
            String trimmed = cookie.trim();
            if (trimmed.startsWith(name + "=")) {
                return trimmed.substring(name.length() + 1);
            }
        }
        return null;
    }

    @NonNull
    private WebLoginSession createWebLoginSession() {
        WebLoginSession session = new WebLoginSession(generateRandomToken(16), generateRandomToken(32), generateRandomToken(32));
        WEB_LOGIN_SESSIONS.put(session.sessionId, session);
        return session;
    }

    @Nullable
    private String confirmWebLoginSession(@Nullable String sessionId, @Nullable String token) {
        if (sessionId == null || token == null) {
            return null;
        }
        WebLoginSession session = WEB_LOGIN_SESSIONS.get(sessionId);
        if (session == null || !session.token.equals(token)) {
            return null;
        }
        session.authenticated = true;
        return session.authToken;
    }

    private boolean isWebLoginSessionAuthenticated(@NonNull String authToken) {
        for (WebLoginSession session : WEB_LOGIN_SESSIONS.values()) {
            if (session.authenticated && session.authToken.equals(authToken)) {
                return true;
            }
        }
        return false;
    }

    private void cleanupExpiredWebLoginSessions() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, WebLoginSession>> iterator = WEB_LOGIN_SESSIONS.entrySet().iterator();
        while (iterator.hasNext()) {
            WebLoginSession session = iterator.next().getValue();
            if (now - session.createdAt > WEB_LOGIN_SESSION_MAX_AGE_MS) {
                iterator.remove();
            }
        }
    }

    @NonNull
    private static String generateRandomToken(int length) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            builder.append(chars.charAt(SECURE_RANDOM.nextInt(chars.length())));
        }
        return builder.toString();
    }

    private static final class WebLoginSession {
        @NonNull
        final String sessionId;
        @NonNull
        final String token;
        @NonNull
        final String authToken;
        final long createdAt;
        volatile boolean authenticated;

        WebLoginSession(@NonNull String sessionId, @NonNull String token, @NonNull String authToken) {
            this.sessionId = sessionId;
            this.token = token;
            this.authToken = authToken;
            this.createdAt = System.currentTimeMillis();
            this.authenticated = false;
        }
    }

    @NonNull
    private NanoHTTPD.Response serveChatImage(@NonNull String uri) {
        String prefix = AppConfig.get().getImageUrlPrefix();
        String fileName = uri.substring(prefix.length());
        if (fileName.isEmpty() || fileName.contains("..") || fileName.contains("/")) {
            return newFixedLengthResponse(NanoHTTPD.Response.Status.BAD_REQUEST, NanoHTTPD.MIME_PLAINTEXT, "Invalid image name.");
        }
        File imageFile = new File(AppStoragePaths.resolveWebSocketChatImagesDirectory(context), fileName);
        return serveFile(imageFile, getImageMimeType(fileName), false);
    }

    @NonNull
    private NanoHTTPD.Response handleFileList(@NonNull Map<String, String> params) {
        String path = params.get("path");
        if (path == null || path.isEmpty()) {
            path = Environment.getExternalStorageDirectory().getAbsolutePath();
        }
        File directory = new File(path);
        if (!directory.exists() || !directory.isDirectory()) {
            return jsonResponse(new JSONObject(), NanoHTTPD.Response.Status.NOT_FOUND);
        }
        if (!isUnderAllowedRoot(directory)) {
            return jsonResponse(new JSONObject(), NanoHTTPD.Response.Status.FORBIDDEN);
        }
        JSONObject result = new JSONObject();
        try {
            result.put("path", directory.getAbsolutePath());
            result.put("parent", directory.getParent());
            JSONArray files = new JSONArray();
            File[] children = directory.listFiles();
            if (children != null) {
                List<File> sorted = new ArrayList<>();
                Collections.addAll(sorted, children);
                Collections.sort(sorted, new Comparator<File>() {
                    @Override
                    public int compare(File o1, File o2) {
                        if (o1.isDirectory() && !o2.isDirectory()) {
                            return -1;
                        }
                        if (!o1.isDirectory() && o2.isDirectory()) {
                            return 1;
                        }
                        return o1.getName().compareToIgnoreCase(o2.getName());
                    }
                });
                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
                for (File child : sorted) {
                    JSONObject item = new JSONObject();
                    item.put("name", child.getName());
                    item.put("path", child.getAbsolutePath());
                    item.put("directory", child.isDirectory());
                    item.put("size", child.length());
                    item.put("modified", dateFormat.format(new Date(child.lastModified())));
                    files.put(item);
                }
            }
            result.put("files", files);
        } catch (JSONException exception) {
            AppLogger.e(TAG, "Failed to build file list JSON.", exception);
        }
        return jsonResponse(result, NanoHTTPD.Response.Status.OK);
    }

    @NonNull
    private NanoHTTPD.Response handleDownload(@NonNull Map<String, String> params) {
        File file = resolveFileParam(params.get("path"));
        if (file == null) {
            return newFixedLengthResponse(NanoHTTPD.Response.Status.BAD_REQUEST, NanoHTTPD.MIME_PLAINTEXT, "Invalid path.");
        }
        return serveFile(file, getMimeType(file.getName()), true);
    }

    @NonNull
    private NanoHTTPD.Response handleView(@NonNull Map<String, String> params) {
        File file = resolveFileParam(params.get("path"));
        if (file == null) {
            return newFixedLengthResponse(NanoHTTPD.Response.Status.BAD_REQUEST, NanoHTTPD.MIME_PLAINTEXT, "Invalid path.");
        }
        String mimeType = getMimeType(file.getName());
        return serveFile(file, mimeType, false);
    }

    @NonNull
    private NanoHTTPD.Response handleUpload(@NonNull NanoHTTPD.IHTTPSession session) {
        if (session.getMethod() != NanoHTTPD.Method.POST) {
            return newFixedLengthResponse(NanoHTTPD.Response.Status.METHOD_NOT_ALLOWED, NanoHTTPD.MIME_PLAINTEXT, "Use POST.");
        }
        Map<String, String> files = new HashMap<>();
        try {
            session.parseBody(files);
        } catch (Exception exception) {
            AppLogger.e(TAG, "Failed to parse upload body.", exception);
            return newFixedLengthResponse(NanoHTTPD.Response.Status.BAD_REQUEST, NanoHTTPD.MIME_PLAINTEXT, "Failed to parse upload.");
        }
        Map<String, String> params = session.getParms();
        String path = params.get("path");
        if (path == null || path.isEmpty()) {
            path = Environment.getExternalStorageDirectory().getAbsolutePath();
        }
        File directory = new File(path);
        if (!directory.exists() || !directory.isDirectory() || !isUnderAllowedRoot(directory)) {
            return newFixedLengthResponse(NanoHTTPD.Response.Status.FORBIDDEN, NanoHTTPD.MIME_PLAINTEXT, "Invalid directory.");
        }
        String fileName = params.get("file");
        String tmpPath = files.get("file");
        if (tmpPath == null || fileName == null || fileName.isEmpty()) {
            return newFixedLengthResponse(NanoHTTPD.Response.Status.BAD_REQUEST, NanoHTTPD.MIME_PLAINTEXT, "Missing file.");
        }
        File tempFile = new File(tmpPath);
        File destination = resolveUniqueFile(directory, fileName);
        try {
            java.nio.file.Files.copy(tempFile.toPath(), destination.toPath());
        } catch (Exception exception) {
            AppLogger.e(TAG, "Failed to save uploaded file.", exception);
            return newFixedLengthResponse(NanoHTTPD.Response.Status.INTERNAL_ERROR, NanoHTTPD.MIME_PLAINTEXT, "Failed to save file.");
        }
        JSONObject result = new JSONObject();
        try {
            result.put("success", true);
            result.put("path", destination.getAbsolutePath());
        } catch (JSONException exception) {
            AppLogger.e(TAG, "Failed to build upload response JSON.", exception);
        }
        return jsonResponse(result, NanoHTTPD.Response.Status.OK);
    }

    @NonNull
    private File resolveUniqueFile(@NonNull File directory, @NonNull String fileName) {
        File file = new File(directory, fileName);
        if (!file.exists()) {
            return file;
        }
        int dotIndex = fileName.lastIndexOf('.');
        String base = dotIndex > 0 ? fileName.substring(0, dotIndex) : fileName;
        String extension = dotIndex > 0 ? fileName.substring(dotIndex) : "";
        int index = 1;
        while (file.exists()) {
            file = new File(directory, base + "_" + index + extension);
            index++;
        }
        return file;
    }

    @Nullable
    private File resolveFileParam(@Nullable String path) {
        if (path == null || path.isEmpty() || path.contains("..")) {
            return null;
        }
        File file = new File(path);
        if (!file.exists() || !isUnderAllowedRoot(file)) {
            return null;
        }
        return file;
    }

    private boolean isUnderAllowedRoot(@NonNull File file) {
        try {
            File root = Environment.getExternalStorageDirectory();
            String canonicalRoot = root.getCanonicalPath();
            String canonicalFile = file.getCanonicalPath();
            return canonicalFile.startsWith(canonicalRoot);
        } catch (Exception exception) {
            return false;
        }
    }

    @NonNull
    private NanoHTTPD.Response serveFile(@NonNull File file, @NonNull String mimeType, boolean forceDownload) {
        if (!file.exists() || !file.isFile()) {
            return newFixedLengthResponse(NanoHTTPD.Response.Status.NOT_FOUND, NanoHTTPD.MIME_PLAINTEXT, "File not found.");
        }
        try {
            FileInputStream inputStream = new FileInputStream(file);
            NanoHTTPD.Response response = newFixedLengthResponse(NanoHTTPD.Response.Status.OK, mimeType, inputStream, file.length());
            if (forceDownload) {
                response.addHeader("Content-Disposition", "attachment; filename=\"" + file.getName().replace("\"", "'") + "\"");
            }
            return response;
        } catch (FileNotFoundException exception) {
            AppLogger.e(TAG, "File not found: " + file.getAbsolutePath(), exception);
            return newFixedLengthResponse(NanoHTTPD.Response.Status.NOT_FOUND, NanoHTTPD.MIME_PLAINTEXT, "File not found.");
        }
    }

    @NonNull
    private NanoHTTPD.Response handleDeviceInfo() {
        return jsonResponse(buildDeviceInfo(), NanoHTTPD.Response.Status.OK);
    }

    @NonNull
    private JSONObject buildDeviceInfo() {
        JSONObject info = new JSONObject();
        try {
            info.put("appName", AppStoragePaths.resolveBaseDirectory(context).getName());
            info.put("packageName", context.getPackageName());
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

            WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
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
            android.app.ActivityManager activityManager = (android.app.ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
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
            cpu.put("usage", service.getProcessCpuUsage());
            info.put("cpu", cpu);

            JSONObject network = new JSONObject();
            String ip = service.getLocalIpAddress();
            network.put("localIp", ip != null ? ip : "");
            network.put("webSocketPort", AppConfig.get().getWebSocketPort());
            network.put("httpPort", AppConfig.get().getHttpPort());
            network.put("wifiConnected", NetworkInfoHelper.isWifiConnected(context));
            network.put("wifiLinkSpeedMbps", NetworkInfoHelper.getWifiLinkSpeedMbps(context));
            network.put("wifiSignalDbm", NetworkInfoHelper.getWifiSignalDbm(context));
            network.put("wifiSignalLevel", NetworkInfoHelper.getWifiSignalLevel(context));
            info.put("network", network);
        } catch (JSONException exception) {
            AppLogger.e(TAG, "Failed to build device info JSON.", exception);
        }
        return info;
    }

    @NonNull
    private JSONObject readBatteryInfo() {
        JSONObject battery = new JSONObject();
        try {
            IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            Intent intent = context.registerReceiver(null, filter);
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
        } catch (Exception exception) {
            AppLogger.e(TAG, "Failed to read battery info.", exception);
        }
        return battery;
    }

    @NonNull
    private NanoHTTPD.Response jsonResponse(@NonNull JSONObject json, @NonNull NanoHTTPD.Response.Status status) {
        byte[] bytes = json.toString().getBytes(StandardCharsets.UTF_8);
        return newFixedLengthResponse(status, "application/json; charset=utf-8", new java.io.ByteArrayInputStream(bytes), bytes.length);
    }

    @NonNull
    private static NanoHTTPD.Response newFixedLengthResponse(@NonNull NanoHTTPD.Response.Status status, @NonNull String mimeType, @NonNull String message) {
        byte[] bytes = message.getBytes(StandardCharsets.UTF_8);
        return NanoHTTPD.newFixedLengthResponse(status, mimeType, new java.io.ByteArrayInputStream(bytes), bytes.length);
    }

    @NonNull
    private static NanoHTTPD.Response newFixedLengthResponse(@NonNull NanoHTTPD.Response.Status status, @NonNull String mimeType, @NonNull InputStream stream, long length) {
        return NanoHTTPD.newFixedLengthResponse(status, mimeType, stream, length);
    }

    @NonNull
    private static String getMimeType(@NonNull String fileName) {
        String lower = fileName.toLowerCase(Locale.US);
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".bmp")) return "image/bmp";
        if (lower.endsWith(".heic")) return "image/heic";
        if (lower.endsWith(".heif")) return "image/heif";
        if (lower.endsWith(".html") || lower.endsWith(".htm")) return "text/html; charset=utf-8";
        if (lower.endsWith(".css")) return "text/css; charset=utf-8";
        if (lower.endsWith(".js")) return "application/javascript; charset=utf-8";
        if (lower.endsWith(".json")) return "application/json; charset=utf-8";
        if (lower.endsWith(".xml")) return "application/xml; charset=utf-8";
        if (lower.endsWith(".txt") || lower.endsWith(".log") || lower.endsWith(".md")
                || lower.endsWith(".java") || lower.endsWith(".kt") || lower.endsWith(".swift")
                || lower.endsWith(".gradle") || lower.endsWith(".properties") || lower.endsWith(".ini")
                || lower.endsWith(".cfg") || lower.endsWith(".conf") || lower.endsWith(".csv")
                || lower.endsWith(".yaml") || lower.endsWith(".yml") || lower.endsWith(".ts")) {
            return "text/plain; charset=utf-8";
        }
        if (lower.endsWith(".pdf")) return "application/pdf";
        if (lower.endsWith(".mp4") || lower.endsWith(".m4v") || lower.endsWith(".mov") || lower.endsWith(".mkv")
                || lower.endsWith(".webm") || lower.endsWith(".avi") || lower.endsWith(".3gp")) {
            return "video/*";
        }
        if (lower.endsWith(".mp3") || lower.endsWith(".wav") || lower.endsWith(".flac") || lower.endsWith(".m4a")
                || lower.endsWith(".aac") || lower.endsWith(".ogg") || lower.endsWith(".opus")) {
            return "audio/*";
        }
        return "application/octet-stream";
    }

    @NonNull
    private static String getImageMimeType(@NonNull String fileName) {
        return getMimeType(fileName);
    }
}
