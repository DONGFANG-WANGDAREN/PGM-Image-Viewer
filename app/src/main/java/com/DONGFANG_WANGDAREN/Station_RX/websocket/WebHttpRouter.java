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

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
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
    private static final long UPLOAD_SESSION_MAX_AGE_MS = 24 * 60 * 60 * 1000;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final ConcurrentHashMap<String, WebLoginSession> WEB_LOGIN_SESSIONS = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<String, UploadSession> uploadSessions = new ConcurrentHashMap<>();

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

        if (!uri.equals("/")
                && !uri.equals("/chat")
                && !uri.equals("/api/chat/upload-image")
                && !isAuthenticated(session, params)) {
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
                return handleDownload(session, params);
            case "/api/view":
                return handleView(params);
            case "/api/device":
                return handleDeviceInfo();
            case "/api/upload":
                return handleUpload(session);
            case "/api/upload-init":
                return handleUploadInit(params);
            case "/api/upload-chunk":
                return handleUploadChunk(session);
            case "/api/upload-finish":
                return handleUploadFinish(session);
            case "/api/chat/upload-image":
                return handleChatImageUpload(session);
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
            wsAddress = "ws://" + LanServerHelper.getDisplayHost() + ":" + service.getWebSocketPort();
        }
        JSONObject result = new JSONObject();
        try {
            result.put("wsAddress", wsAddress);
            result.put("httpAddress", service.getHttpAddress());
            result.put("browserAddress", service.getBrowserAddress());
        } catch (JSONException exception) {
            AppLogger.e(TAG, "Failed to build config JSON.", exception);
        }
        return jsonResponse(result, NanoHTTPD.Response.Status.OK);
    }

    @NonNull
    private NanoHTTPD.Response handleWebLoginSession() {
        cleanupExpiredWebLoginSessions();
        WebLoginSession webSession = createWebLoginSession();
        webSession.qrReady = true;
        String loginUrl = service.getWebLoginUrl();
        JSONObject result = new JSONObject();
        try {
            result.put("sessionId", webSession.sessionId);
            result.put("token", webSession.token);
            result.put("loginUrl", loginUrl != null ? loginUrl : "");
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

    @Nullable
    public static PendingWebLoginInfo getLatestPendingWebLoginInfo() {
        cleanupExpiredStaticWebLoginSessions();
        WebLoginSession latestSession = null;
        for (WebLoginSession session : WEB_LOGIN_SESSIONS.values()) {
            if (!session.qrReady || session.authenticated) {
                continue;
            }
            if (latestSession == null || session.createdAt > latestSession.createdAt) {
                latestSession = session;
            }
        }
        if (latestSession == null) {
            return null;
        }
        return new PendingWebLoginInfo(latestSession.sessionId, latestSession.token);
    }

    @Nullable
    public static String confirmPendingWebLogin(@Nullable String sessionId, @Nullable String token) {
        cleanupExpiredStaticWebLoginSessions();
        if (sessionId == null || token == null) {
            return null;
        }
        WebLoginSession session = WEB_LOGIN_SESSIONS.get(sessionId);
        if (session == null || session.authenticated || !session.qrReady || !token.equals(session.token)) {
            return null;
        }
        session.authenticated = true;
        return session.authToken;
    }

    private static void cleanupExpiredStaticWebLoginSessions() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, WebLoginSession>> iterator = WEB_LOGIN_SESSIONS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, WebLoginSession> entry = iterator.next();
            if (now - entry.getValue().createdAt > WEB_LOGIN_SESSION_MAX_AGE_MS) {
                iterator.remove();
            }
        }
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

    private void cleanupExpiredUploadSessions() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, UploadSession>> iterator = uploadSessions.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, UploadSession> entry = iterator.next();
            if (now - entry.getValue().lastActiveAt > UPLOAD_SESSION_MAX_AGE_MS) {
                entry.getValue().tempFile.delete();
                iterator.remove();
            }
        }
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
        volatile boolean qrReady;

        WebLoginSession(@NonNull String sessionId, @NonNull String token, @NonNull String authToken) {
            this.sessionId = sessionId;
            this.token = token;
            this.authToken = authToken;
            this.createdAt = System.currentTimeMillis();
            this.authenticated = false;
            this.qrReady = false;
        }
    }

    public static final class PendingWebLoginInfo {
        @NonNull
        public final String sessionId;
        @NonNull
        public final String token;

        PendingWebLoginInfo(@NonNull String sessionId, @NonNull String token) {
            this.sessionId = sessionId;
            this.token = token;
        }
    }

    private static final class UploadSession {
        @NonNull
        final String uploadId;
        @NonNull
        final File targetDirectory;
        @NonNull
        final String fileName;
        @NonNull
        final File tempFile;
        volatile long lastActiveAt;
        int receivedChunks;

        UploadSession(@NonNull String uploadId, @NonNull File targetDirectory, @NonNull String fileName, @NonNull File tempFile, long lastActiveAt) {
            this.uploadId = uploadId;
            this.targetDirectory = targetDirectory;
            this.fileName = fileName;
            this.tempFile = tempFile;
            this.lastActiveAt = lastActiveAt;
            this.receivedChunks = 0;
        }
    }

    private static final class RandomAccessFileInputStream extends InputStream {
        @NonNull
        private final RandomAccessFile randomAccessFile;
        private long remaining;

        RandomAccessFileInputStream(@NonNull RandomAccessFile randomAccessFile, long length) {
            this.randomAccessFile = randomAccessFile;
            this.remaining = length;
        }

        @Override
        public int read() throws IOException {
            if (remaining <= 0) {
                return -1;
            }
            remaining--;
            return randomAccessFile.read();
        }

        @Override
        public int read(@NonNull byte[] buffer, int offset, int length) throws IOException {
            if (remaining <= 0) {
                return -1;
            }
            int toRead = (int) Math.min(length, remaining);
            int read = randomAccessFile.read(buffer, offset, toRead);
            if (read > 0) {
                remaining -= read;
            }
            return read;
        }

        @Override
        public void close() throws IOException {
            randomAccessFile.close();
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
    private NanoHTTPD.Response handleDownload(@NonNull NanoHTTPD.IHTTPSession session, @NonNull Map<String, String> params) {
        File file = resolveFileParam(params.get("path"));
        if (file == null) {
            return newFixedLengthResponse(NanoHTTPD.Response.Status.BAD_REQUEST, NanoHTTPD.MIME_PLAINTEXT, "Invalid path.");
        }
        String mimeType = getMimeType(file.getName());
        if (session.getMethod() == NanoHTTPD.Method.HEAD) {
            NanoHTTPD.Response response = NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK, mimeType, new ByteArrayInputStream(new byte[0]), 0);
            response.addHeader("Content-Length", String.valueOf(file.length()));
            response.addHeader("Accept-Ranges", "bytes");
            if (mimeType.startsWith("application/") || mimeType.startsWith("video/") || mimeType.startsWith("audio/")) {
                response.addHeader("Content-Disposition", "attachment; filename=\"" + file.getName().replace("\"", "'") + "\"");
            }
            return response;
        }
        String rangeHeader = session.getHeaders().get("range");
        if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
            return serveFileRange(file, mimeType, rangeHeader, true);
        }
        return serveFile(file, mimeType, true);
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
    private NanoHTTPD.Response handleUploadInit(@NonNull Map<String, String> params) {
        cleanupExpiredUploadSessions();
        String uploadId = params.get("id");
        String path = params.get("path");
        String fileName = params.get("name");
        if (uploadId == null || uploadId.isEmpty() || fileName == null || fileName.isEmpty()) {
            return jsonErrorResponse("Missing upload id or file name.");
        }
        if (path == null || path.isEmpty()) {
            path = Environment.getExternalStorageDirectory().getAbsolutePath();
        }
        File directory = new File(path);
        if (!directory.exists() || !directory.isDirectory() || !isUnderAllowedRoot(directory)) {
            return jsonErrorResponse("Invalid directory.");
        }
        File cacheDir = new File(context.getCacheDir(), "web_uploads");
        if (!cacheDir.exists()) {
            cacheDir.mkdirs();
        }
        File tempFile = new File(cacheDir, uploadId + ".tmp");
        try {
            if (tempFile.exists()) {
                tempFile.delete();
            }
            tempFile.createNewFile();
        } catch (IOException exception) {
            AppLogger.e(TAG, "Failed to create upload temp file.", exception);
            return jsonErrorResponse("Failed to initialize upload.");
        }
        uploadSessions.put(uploadId, new UploadSession(uploadId, directory, fileName, tempFile, System.currentTimeMillis()));
        JSONObject result = new JSONObject();
        try {
            result.put("success", true);
            result.put("id", uploadId);
        } catch (JSONException exception) {
            AppLogger.e(TAG, "Failed to build upload init response JSON.", exception);
        }
        return jsonResponse(result, NanoHTTPD.Response.Status.OK);
    }

    @NonNull
    private NanoHTTPD.Response handleUploadChunk(@NonNull NanoHTTPD.IHTTPSession session) {
        cleanupExpiredUploadSessions();
        if (session.getMethod() != NanoHTTPD.Method.POST) {
            return jsonErrorResponse("Use POST.");
        }
        Map<String, String> files = new HashMap<>();
        try {
            session.parseBody(files);
        } catch (Exception exception) {
            AppLogger.e(TAG, "Failed to parse upload chunk body.", exception);
            return jsonErrorResponse("Failed to parse chunk.");
        }
        Map<String, String> params = session.getParms();
        String uploadId = params.get("id");
        String indexText = params.get("index");
        String tmpPath = files.get("chunk");
        if (uploadId == null || indexText == null || tmpPath == null) {
            return jsonErrorResponse("Missing chunk parameters.");
        }
        UploadSession uploadSession = uploadSessions.get(uploadId);
        if (uploadSession == null) {
            return jsonErrorResponse("Upload session not found.");
        }
        int index;
        try {
            index = Integer.parseInt(indexText);
        } catch (NumberFormatException exception) {
            return jsonErrorResponse("Invalid chunk index.");
        }
        if (index != uploadSession.receivedChunks) {
            return jsonErrorResponse("Unexpected chunk index. Expected " + uploadSession.receivedChunks + " but got " + index + ".");
        }
        File chunkFile = new File(tmpPath);
        try (InputStream inputStream = new FileInputStream(chunkFile);
             OutputStream outputStream = new FileOutputStream(uploadSession.tempFile, true)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }
            outputStream.flush();
            uploadSession.receivedChunks++;
            uploadSession.lastActiveAt = System.currentTimeMillis();
        } catch (IOException exception) {
            AppLogger.e(TAG, "Failed to append upload chunk.", exception);
            return jsonErrorResponse("Failed to write chunk.");
        }
        JSONObject result = new JSONObject();
        try {
            result.put("success", true);
            result.put("received", uploadSession.receivedChunks);
        } catch (JSONException exception) {
            AppLogger.e(TAG, "Failed to build upload chunk response JSON.", exception);
        }
        return jsonResponse(result, NanoHTTPD.Response.Status.OK);
    }

    @NonNull
    private NanoHTTPD.Response handleUploadFinish(@NonNull NanoHTTPD.IHTTPSession session) {
        cleanupExpiredUploadSessions();
        if (session.getMethod() != NanoHTTPD.Method.POST) {
            return jsonErrorResponse("Use POST.");
        }
        try {
            session.parseBody(new HashMap<>());
        } catch (Exception ignored) {
        }
        Map<String, String> params = session.getParms();
        String uploadId = params.get("id");
        if (uploadId == null || uploadId.isEmpty()) {
            return jsonErrorResponse("Missing upload id.");
        }
        UploadSession uploadSession = uploadSessions.remove(uploadId);
        if (uploadSession == null) {
            return jsonErrorResponse("Upload session not found.");
        }
        File destination = resolveUniqueFile(uploadSession.targetDirectory, uploadSession.fileName);
        if (!uploadSession.tempFile.renameTo(destination)) {
            try {
                java.nio.file.Files.copy(uploadSession.tempFile.toPath(), destination.toPath());
                uploadSession.tempFile.delete();
            } catch (IOException exception) {
                AppLogger.e(TAG, "Failed to finalize uploaded file.", exception);
                uploadSession.tempFile.delete();
                return jsonErrorResponse("Failed to save file.");
            }
        }
        JSONObject result = new JSONObject();
        try {
            result.put("success", true);
            result.put("path", destination.getAbsolutePath());
            result.put("name", destination.getName());
        } catch (JSONException exception) {
            AppLogger.e(TAG, "Failed to build upload finish response JSON.", exception);
        }
        return jsonResponse(result, NanoHTTPD.Response.Status.OK);
    }

    @NonNull
    private NanoHTTPD.Response handleChatImageUpload(@NonNull NanoHTTPD.IHTTPSession session) {
        if (session.getMethod() != NanoHTTPD.Method.POST) {
            return jsonErrorResponse("Use POST.");
        }
        Map<String, String> files = new HashMap<>();
        try {
            session.parseBody(files);
        } catch (Exception exception) {
            AppLogger.e(TAG, "Failed to parse chat image upload body.", exception);
            return jsonErrorResponse("Failed to parse image.");
        }
        String tmpPath = files.get("image");
        if (tmpPath == null || tmpPath.isEmpty()) {
            return jsonErrorResponse("Missing image.");
        }
        File uploadedFile = new File(tmpPath);
        if (!uploadedFile.exists()) {
            return jsonErrorResponse("Uploaded image not found.");
        }

        String originalName = session.getParms().getOrDefault("image", "upload");
        String extension = getImageExtension(originalName);
        if (extension.isEmpty()) {
            extension = ".jpg";
        }
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(new Date());
        String randomSuffix = String.format(Locale.US, "%04d", SECURE_RANDOM.nextInt(10000));
        String fileName = "IMG_" + timeStamp + "_" + randomSuffix + extension;

        File imageDirectory = AppStoragePaths.resolveWebSocketChatImagesDirectory(context);
        File destinationFile = new File(imageDirectory, fileName);
        int conflictIndex = 1;
        while (destinationFile.exists()) {
            String conflictName = "IMG_" + timeStamp + "_" + randomSuffix + "_" + conflictIndex + extension;
            destinationFile = new File(imageDirectory, conflictName);
            conflictIndex++;
        }

        try {
            java.nio.file.Files.copy(uploadedFile.toPath(), destinationFile.toPath());
        } catch (IOException exception) {
            AppLogger.e(TAG, "Failed to save chat image.", exception);
            return jsonErrorResponse("Failed to save image.");
        }

        String imageUrl = AppConfig.get().getImageUrlPrefix() + fileName;
        service.sendImageMessage(imageUrl);

        JSONObject result = new JSONObject();
        try {
            result.put("success", true);
            result.put("url", imageUrl);
            result.put("name", fileName);
        } catch (JSONException exception) {
            AppLogger.e(TAG, "Failed to build chat image upload response JSON.", exception);
        }
        return jsonResponse(result, NanoHTTPD.Response.Status.OK);
    }

    @NonNull
    private String getImageExtension(@NonNull String fileName) {
        String lower = fileName.toLowerCase(Locale.US);
        if (lower.endsWith(".png")) {
            return ".png";
        }
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return ".jpg";
        }
        if (lower.endsWith(".gif")) {
            return ".gif";
        }
        if (lower.endsWith(".webp")) {
            return ".webp";
        }
        if (lower.endsWith(".bmp")) {
            return ".bmp";
        }
        return "";
    }

    @NonNull
    private NanoHTTPD.Response jsonErrorResponse(@NonNull String message) {
        JSONObject result = new JSONObject();
        try {
            result.put("success", false);
            result.put("error", message);
        } catch (JSONException exception) {
            AppLogger.e(TAG, "Failed to build error JSON.", exception);
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
            response.addHeader("Accept-Ranges", "bytes");
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
    private NanoHTTPD.Response serveFileRange(@NonNull File file, @NonNull String mimeType, @NonNull String rangeHeader, boolean forceDownload) {
        if (!file.exists() || !file.isFile()) {
            return newFixedLengthResponse(NanoHTTPD.Response.Status.NOT_FOUND, NanoHTTPD.MIME_PLAINTEXT, "File not found.");
        }
        long fileLength = file.length();
        String rangeValue = rangeHeader.substring(6).trim();
        int dashIndex = rangeValue.indexOf('-');
        if (dashIndex < 0) {
            return newFixedLengthResponse(NanoHTTPD.Response.Status.BAD_REQUEST, NanoHTTPD.MIME_PLAINTEXT, "Invalid range.");
        }
        long start;
        long end;
        try {
            String startText = rangeValue.substring(0, dashIndex).trim();
            String endText = rangeValue.substring(dashIndex + 1).trim();
            start = startText.isEmpty() ? 0 : Long.parseLong(startText);
            if (start < 0 || start >= fileLength) {
                NanoHTTPD.Response response = newFixedLengthResponse(NanoHTTPD.Response.Status.RANGE_NOT_SATISFIABLE, NanoHTTPD.MIME_PLAINTEXT, "Range not satisfiable.");
                response.addHeader("Content-Range", "bytes */" + fileLength);
                return response;
            }
            end = endText.isEmpty() ? fileLength - 1 : Long.parseLong(endText);
            if (end >= fileLength) {
                end = fileLength - 1;
            }
            if (end < start) {
                end = start;
            }
        } catch (NumberFormatException exception) {
            return newFixedLengthResponse(NanoHTTPD.Response.Status.BAD_REQUEST, NanoHTTPD.MIME_PLAINTEXT, "Invalid range.");
        }
        long contentLength = end - start + 1;
        try {
            RandomAccessFile randomAccessFile = new RandomAccessFile(file, "r");
            randomAccessFile.seek(start);
            NanoHTTPD.Response response = NanoHTTPD.newFixedLengthResponse(
                    NanoHTTPD.Response.Status.PARTIAL_CONTENT,
                    mimeType,
                    new RandomAccessFileInputStream(randomAccessFile, contentLength),
                    contentLength
            );
            response.addHeader("Content-Range", "bytes " + start + "-" + end + "/" + fileLength);
            response.addHeader("Accept-Ranges", "bytes");
            if (forceDownload) {
                response.addHeader("Content-Disposition", "attachment; filename=\"" + file.getName().replace("\"", "'") + "\"");
            }
            return response;
        } catch (Exception exception) {
            AppLogger.e(TAG, "Failed to serve file range: " + file.getAbsolutePath(), exception);
            return newFixedLengthResponse(NanoHTTPD.Response.Status.INTERNAL_ERROR, NanoHTTPD.MIME_PLAINTEXT, "Failed to serve range.");
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
            network.put("webSocketPort", service.getWebSocketPort());
            network.put("httpPort", service.getHttpPort());
            network.put("wifiConnected", NetworkInfoHelper.isWifiConnected(context));
            network.put("wifiLinkSpeedMbps", NetworkInfoHelper.getWifiLinkSpeedMbps(context));
            network.put("wifiSignalDbm", NetworkInfoHelper.getWifiSignalDbm(context));
            network.put("wifiSignalLevel", NetworkInfoHelper.getWifiSignalLevel(context));
            network.put("estimatedDistanceMeters", NetworkInfoHelper.estimateWifiDistanceMeters(context));
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
