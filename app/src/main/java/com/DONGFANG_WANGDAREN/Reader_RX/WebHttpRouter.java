package com.DONGFANG_WANGDAREN.Reader_RX;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.net.Uri;
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
            return handleWebLogin(session);
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

        if (uri.equals("/login")) {
            return handleLogin(session, params);
        }

        if (!uri.equals("/") && !uri.equals("/chat") && !isAuthenticated(session, params)) {
            return newFixedLengthResponse(NanoHTTPD.Response.Status.UNAUTHORIZED, "text/html; charset=utf-8", buildLoginPage(false));
        }

        switch (uri) {
            case "/":
            case "/chat":
                return newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "text/html; charset=utf-8", buildChatPage());
            case "/files":
                return newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "text/html; charset=utf-8", buildFileManagerPage());
            case "/api/files":
                return handleFileList(params);
            case "/api/download":
                return handleDownload(params);
            case "/api/view":
                return handleView(params);
            case "/api/device":
                return handleDeviceInfo();
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
        return newFixedLengthResponse(NanoHTTPD.Response.Status.UNAUTHORIZED, "text/html; charset=utf-8", buildLoginPage(true));
    }

    @NonNull
    private NanoHTTPD.Response handleWebLogin(@NonNull NanoHTTPD.IHTTPSession session) {
        cleanupExpiredWebLoginSessions();
        WebLoginSession webSession = createWebLoginSession();
        String qrData = "rrx://login?session=" + webSession.sessionId + "&token=" + webSession.token;
        String qrImageUrl = "/api/qr.png?data=" + Uri.encode(qrData);
        String localIp = service.getLocalIpAddress();
        if (localIp == null || localIp.isEmpty()) {
            localIp = "127.0.0.1";
        }
        String loginUrl = "http://" + localIp + ":" + AppConfig.get().getHttpPort() + "/web-login";
        return newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "text/html; charset=utf-8", buildWebLoginPage(webSession.sessionId, qrImageUrl, loginUrl));
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
    private String buildLoginPage(boolean invalid) {
        String body = "<!DOCTYPE html><html><head>"
                + "<meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
                + "<title>Reader RX Login</title>"
                + "<style>"
                + "body{font-family:sans-serif;background:#0f172a;color:#f8fafc;display:flex;align-items:center;justify-content:center;height:100vh;margin:0;}"
                + ".box{background:#1e293b;padding:32px;border-radius:12px;max-width:360px;width:100%;box-shadow:0 10px 25px rgba(0,0,0,0.3);}"
                + "h1{font-size:20px;margin:0 0 16px;}"
                + "p{color:#94a3b8;font-size:14px;margin-bottom:16px;}"
                + "input{width:100%;padding:10px;border-radius:6px;border:none;background:#334155;color:#f8fafc;box-sizing:border-box;}"
                + "button{width:100%;margin-top:12px;padding:10px;border:none;border-radius:6px;background:#22c55e;color:#fff;cursor:pointer;font-weight:bold;}"
                + ".error{color:#f87171;font-size:13px;margin-top:8px;}"
                + "</style></head><body>"
                + "<div class=\"box\">"
                + "<h1>Reader RX Web Login</h1>"
                + "<p>Please scan the QR code in the app or enter the login token.</p>"
                + "<form method=\"get\" action=\"/login\">"
                + "<input type=\"text\" name=\"token\" placeholder=\"Token\" required>"
                + "<button type=\"submit\">Login</button>"
                + (invalid ? "<div class=\"error\">Invalid token. Please try again.</div>" : "")
                + "</form></div></body></html>";
        return body;
    }

    @NonNull
    private String buildWebLoginPage(@NonNull String sessionId, @NonNull String qrImageUrl, @NonNull String loginUrl) {
        return "<!DOCTYPE html><html><head>"
                + "<meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
                + "<title>Reader RX Web Login</title>"
                + "<style>"
                + "body{font-family:sans-serif;background:#0f172a;color:#f8fafc;display:flex;align-items:center;justify-content:center;min-height:100vh;margin:0;padding:16px;box-sizing:border-box;}"
                + ".box{background:#1e293b;padding:32px;border-radius:12px;max-width:380px;width:100%;text-align:center;box-shadow:0 10px 25px rgba(0,0,0,0.3);}"
                + "h1{font-size:20px;margin:0 0 16px;}"
                + "p{color:#94a3b8;font-size:14px;margin-bottom:12px;line-height:1.5;}"
                + ".url{background:#334155;padding:12px;border-radius:8px;word-break:break-all;margin-bottom:16px;}"
                + ".url strong{color:#f8fafc;font-size:15px;}"
                + "#qr{width:240px;height:240px;background:#fff;border-radius:8px;margin:0 auto 16px;}"
                + "#status{font-size:13px;color:#94a3b8;}"
                + "</style></head><body>"
                + "<div class=\"box\">"
                + "<h1>Reader RX Web Login</h1>"
                + "<p>在另一台设备上打开以下网址，然后用本 App 的“扫码登录”扫描页面中的二维码。</p>"
                + "<div class=\"url\"><strong>" + escapeHtml(loginUrl) + "</strong></div>"
                + "<div id=\"qr\"><img src=\"" + escapeHtml(qrImageUrl) + "\" style=\"width:100%;height:100%;border-radius:8px;\"></div>"
                + "<div id=\"status\">等待扫码...</div>"
                + "</div>"
                + "<script>"
                + "const sessionId='" + escapeHtml(sessionId) + "';"
                + "async function check(){try{const r=await fetch('/api/session-status?session='+encodeURIComponent(sessionId));const d=await r.json();if(d.authenticated){window.location.href='/files';return;}document.getElementById('status').textContent='等待扫码...';}catch(e){document.getElementById('status').textContent='连接错误';}}"
                + "setInterval(check,2000);"
                + "</script></body></html>";
    }

    @NonNull
    private String buildChatPage() {
        String wsAddress = service.getServerAddress();
        if (wsAddress == null) {
            wsAddress = "ws://" + service.getLocalIpAddress() + ":" + AppConfig.get().getWebSocketPort();
        }
        return "<!DOCTYPE html><html><head>"
                + "<meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
                + "<title>Reader RX WebSocket</title>"
                + "<style>"
                + "body{font-family:sans-serif;margin:16px;background:#0f172a;color:#f8fafc;line-height:1.5;}"
                + "h1{font-size:20px;margin-bottom:8px;}"
                + "#status{font-size:14px;color:#94a3b8;margin-bottom:12px;}"
                + "#log{white-space:pre-wrap;background:#1e293b;border-radius:8px;padding:12px;font-family:monospace;font-size:13px;height:calc(100vh - 180px);overflow-y:auto;}"
                + "#sendBox{display:flex;gap:8px;margin-top:12px;}"
                + "#msg{flex:1;padding:8px 12px;border-radius:6px;border:none;background:#334155;color:#f8fafc;}"
                + "button{padding:8px 16px;border:none;border-radius:6px;background:#22c55e;color:#fff;cursor:pointer;}"
                + "nav{margin-bottom:12px;}"
                + "nav a{color:#60a5fa;margin-right:12px;text-decoration:none;}"
                + "</style></head><body>"
                + "<nav><a href=\"/files\">File Manager</a><a href=\"/chat\">Chat</a></nav>"
                + "<h1>Reader RX WebSocket Monitor</h1>"
                + "<div id=\"status\">Connecting to " + escapeHtml(wsAddress) + " ...</div>"
                + "<div id=\"log\"></div>"
                + "<div id=\"sendBox\">"
                + "<input id=\"msg\" type=\"text\" placeholder=\"Send message...\">"
                + "<button onclick=\"send()\">Send</button>"
                + "</div>"
                + "<script>"
                + "function getQueryParam(key){const params=new URLSearchParams(window.location.search);return params.get(key);}"
                + "function generateName(){return 'Browser-'+Math.floor(Math.random()*10000).toString().padStart(4,'0');}"
                + "const userName=(getQueryParam('name')||'').trim()||generateName();"
                + "const wsAddress='" + wsAddress + "';"
                + "const wsUrl=wsAddress+(wsAddress.includes('?')?'&':'?')+'name='+encodeURIComponent(userName);"
                + "const logEl=document.getElementById('log');"
                + "const statusEl=document.getElementById('status');"
                + "function append(text,isImage,imageUrl){const d=new Date();const t=d.getHours().toString().padStart(2,'0')+':'+d.getMinutes().toString().padStart(2,'0')+':'+d.getSeconds().toString().padStart(2,'0');const line=document.createElement('div');line.style.marginBottom='6px';line.textContent='['+t+'] '+text;if(isImage&&imageUrl){const img=document.createElement('img');img.src=imageUrl;img.style.maxWidth='200px';img.style.maxHeight='200px';img.style.display='block';img.style.marginTop='4px';img.style.borderRadius='4px';line.appendChild(img);}logEl.appendChild(line);logEl.scrollTop=logEl.scrollHeight;}"
                + "function formatPayload(raw){try{const data=JSON.parse(raw);const prefix=data.system?'[System]':('['+data.from+']');if(data.type==='image'&&data.url){append(prefix+' '+data.message,true,data.url);return;}if(data.from!==undefined&&data.message!==undefined){append(prefix+' '+data.message+(data.clients!==undefined?' (clients:'+data.clients+')':''),false,null);return;}append(raw,false,null);}catch(e){append(raw,false,null);}}"
                + "function connect(){const ws=new WebSocket(wsUrl);"
                + "ws.onopen=function(){statusEl.textContent='Connected as '+userName+' to " + escapeHtml(wsAddress) + "';append('Connected as '+userName+'.',false,null);};"
                + "ws.onmessage=function(e){formatPayload(e.data);};"
                + "ws.onclose=function(){statusEl.textContent='Disconnected. Reconnecting...';append('Disconnected. Reconnecting...',false,null);setTimeout(connect,2000);};"
                + "ws.onerror=function(e){append('Error: '+e.type,false,null);};"
                + "window.send=function(){const input=document.getElementById('msg');const text=input.value.trim();if(text&&ws.readyState===1){ws.send(text);input.value='';}};"
                + "document.getElementById('msg').addEventListener('keypress',function(e){if(e.key==='Enter')send();});"
                + "}"
                + "connect();"
                + "</script></body></html>";
    }

    @NonNull
    private String buildFileManagerPage() {
        return "<!DOCTYPE html><html><head>"
                + "<meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
                + "<title>Reader RX File Manager</title>"
                + "<style>"
                + "body{font-family:sans-serif;margin:16px;background:#0f172a;color:#f8fafc;}"
                + "h1{font-size:20px;margin-bottom:8px;}"
                + "nav{margin-bottom:16px;}"
                + "nav a{color:#60a5fa;margin-right:12px;text-decoration:none;}"
                + "#device{background:#1e293b;border-radius:8px;padding:12px;margin-bottom:16px;font-size:13px;line-height:1.6;}"
                + "#device h2{font-size:16px;margin:0 0 8px;}"
                + "#device table{width:100%;border-collapse:collapse;}"
                + "#device td{padding:4px 8px;border-bottom:1px solid #334155;}"
                + "#device td:first-child{color:#94a3b8;width:40%;}"
                + "#pathBar{background:#1e293b;border-radius:8px;padding:10px 12px;margin-bottom:12px;word-break:break-all;font-size:13px;}"
                + "#pathBar a{color:#60a5fa;text-decoration:none;margin-right:4px;}"
                + "#list{background:#1e293b;border-radius:8px;overflow:hidden;}"
                + ".item{display:flex;align-items:center;padding:10px 12px;border-bottom:1px solid #334155;cursor:pointer;}"
                + ".item:hover{background:#334155;}"
                + ".item:last-child{border-bottom:none;}"
                + ".icon{width:24px;text-align:center;margin-right:10px;font-size:16px;}"
                + ".name{flex:1;word-break:break-all;font-size:14px;}"
                + ".meta{color:#94a3b8;font-size:12px;text-align:right;white-space:nowrap;margin-left:8px;}"
                + ".actions a{color:#60a5fa;text-decoration:none;margin-left:10px;font-size:12px;}"
                + "#preview{position:fixed;top:0;left:0;right:0;bottom:0;background:rgba(0,0,0,0.9);display:none;align-items:center;justify-content:center;z-index:1000;flex-direction:column;}"
                + "#previewContent{max-width:90%;max-height:80%;overflow:auto;background:#1e293b;border-radius:8px;padding:16px;}"
                + "#preview img{max-width:100%;max-height:70vh;}"
                + "#preview pre{white-space:pre-wrap;word-break:break-all;font-family:monospace;font-size:13px;}"
                + "#previewClose{position:absolute;top:16px;right:16px;color:#fff;font-size:24px;cursor:pointer;}"
                + "</style></head><body>"
                + "<nav><a href=\"/files\">File Manager</a><a href=\"/chat\">Chat</a></nav>"
                + "<h1>File Manager</h1>"
                + "<div id=\"device\"><h2>Device Info</h2><div id=\"deviceContent\">Loading...</div></div>"
                + "<div id=\"pathBar\"></div>"
                + "<div id=\"list\">Loading...</div>"
                + "<div id=\"preview\"><div id=\"previewClose\" onclick=\"closePreview()\">&times;</div><div id=\"previewContent\"></div></div>"
                + "<script>"
                + "let currentPath='';"
                + "function formatBytes(b){if(b<1024)return b+' B';if(b<1024*1024)return (b/1024).toFixed(2)+' KB';return (b/(1024*1024)).toFixed(2)+' MB';}"
                + "async function loadDevice(){try{const r=await fetch('/api/device');const d=await r.json();let html='<table>';"
                + "html+='<tr><td>Model</td><td>'+escape(d.manufacturer+' '+d.model)+'</td></tr>';"
                + "html+='<tr><td>Android</td><td>'+escape(d.androidVersion)+' (SDK '+d.sdk+')</td></tr>';"
                + "if(d.display){html+='<tr><td>Display</td><td>'+d.display.widthPixels+' x '+d.display.heightPixels+' @ '+d.display.densityDpi+'dpi</td></tr>';}"
                + "if(d.memory){html+='<tr><td>RAM</td><td>Total '+formatBytes(d.memory.totalRam)+', Available '+formatBytes(d.memory.availableRam)+'</td></tr>';"
                + "html+='<tr><td>Storage</td><td>Total '+formatBytes(d.memory.totalStorage)+', Available '+formatBytes(d.memory.availableStorage)+'</td></tr>';}"
                + "if(d.battery){html+='<tr><td>Battery</td><td>'+d.battery.level+'%'+(d.battery.plugged>0?' (charging)':'')+'</td></tr>';}"
                + "if(d.cpu){html+='<tr><td>CPU</td><td>'+d.cpu.cores+' cores, '+(d.cpu.usage>=0?d.cpu.usage.toFixed(2)+'%':'N/A')+'</td></tr>';}"
                + "if(d.network){html+='<tr><td>Network</td><td>IP '+escape(d.network.localIp)+(d.network.wifiConnected?' · WiFi '+d.network.wifiLinkSpeedMbps+'Mbps · '+d.network.wifiSignalDbm+'dBm':'')+'</td></tr>';}"
                + "html+='</table>';document.getElementById('deviceContent').innerHTML=html;}catch(e){document.getElementById('deviceContent').textContent='Error loading device info.';}}"
                + "function escape(s){const d=document.createElement('div');d.textContent=s||'';return d.innerHTML;}"
                + "function buildPathBar(path){const parts=path.split('/').filter(Boolean);let html='<a href=\"#\" onclick=\"loadFileList(\\\"\\\");return false;\">Storage</a>';let acc='';for(let p of parts){acc+='/'+p;html+='/';html+='<a href=\"#\" onclick=\"loadFileList(\\\"'+escape(acc)+'\\\");return false;\">'+escape(p)+'</a>';}document.getElementById('pathBar').innerHTML=html;}"
                + "async function loadFileList(path){currentPath=path||'';try{const r=await fetch('/api/files?path='+encodeURIComponent(currentPath));const d=await r.json();buildPathBar(d.path);let html='';if(d.parent){html+='<div class=\"item\" onclick=\"loadFileList(\\\"'+escape(d.parent)+'\\\")\"><span class=\"icon\">&#8593;</span><span class=\"name\">..</span></div>';}"
                + "for(const f of d.files){html+='<div class=\"item\">';if(f.directory){html+='<span class=\"icon\">&#128193;</span><span class=\"name\" onclick=\"loadFileList(\\\"'+escape(f.path)+'\\\")\">'+escape(f.name)+'</span><span class=\"meta\">'+f.modified+'</span>';}"
                + "else{html+='<span class=\"icon\">&#128196;</span><span class=\"name\" onclick=\"viewFile(\\\"'+escape(f.path)+'\\\",\\\"'+escape(f.name)+'\\\")\">'+escape(f.name)+'</span><span class=\"meta\">'+formatBytes(f.size)+' · '+f.modified+'</span><span class=\"actions\"><a href=\"/api/download?path='+encodeURIComponent(f.path)+'\" download>Download</a></span>';}"
                + "html+='</div>';}document.getElementById('list').innerHTML=html||'<div style=\"padding:12px;color:#94a3b8\">Empty folder</div>';}catch(e){document.getElementById('list').textContent='Error loading files.';}}"
                + "async function viewFile(path,name){const lower=name.toLowerCase();const isImage=/\\.(jpg|jpeg|png|gif|webp|bmp|heic|heif)$/.test(lower);const isText=/\\.(txt|json|xml|md|java|kt|swift|csv|log|ini|cfg|conf|properties|gradle|css|js|ts|html|htm|yaml|yml)$/.test(lower);const content=document.getElementById('previewContent');if(isImage){content.innerHTML='<img src=\"/api/view?path='+encodeURIComponent(path)+'\" alt=\"'+escape(name)+'\">';}"
                + "else if(isText){try{const r=await fetch('/api/view?path='+encodeURIComponent(path));const t=await r.text();content.innerHTML='<pre>'+escape(t)+'</pre>';}catch(e){content.textContent='Error reading file.';}}"
                + "else{content.innerHTML='<p>Preview not available. Use Download.</p><a href=\"/api/download?path='+encodeURIComponent(path)+'\" download>Download '+escape(name)+'</a>';}"
                + "document.getElementById('preview').style.display='flex';}"
                + "function closePreview(){document.getElementById('preview').style.display='none';document.getElementById('previewContent').innerHTML='';}"
                + "loadDevice();loadFileList();"
                + "</script></body></html>";
    }

    @NonNull
    private static String escapeHtml(@NonNull String input) {
        return input.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
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
