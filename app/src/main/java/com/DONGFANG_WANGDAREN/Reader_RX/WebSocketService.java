package com.DONGFANG_WANGDAREN.Reader_RX;

import android.app.Service;
import android.content.Intent;
import android.net.Uri;
import android.os.Binder;
import android.os.IBinder;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;

import fi.iki.elonen.NanoHTTPD;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class WebSocketService extends Service {

    private static final String TAG = "WebSocketService";
    private static final int WEBSOCKET_PORT = 8080;
    private static final int HTTP_PORT = 8081;
    private static final String SENDER_APP = "Reader RX";
    private static final String SENDER_SYSTEM = "System";
    private static final String MESSAGE_TYPE_TEXT = "text";
    private static final String MESSAGE_TYPE_IMAGE = "image";
    private static final String IMAGE_URL_PREFIX = "/images/";

    private final IBinder binder = new LocalBinder();
    private final CopyOnWriteArrayList<EventListener> listeners = new CopyOnWriteArrayList<>();
    private final Map<WebSocket, String> clientNames = new ConcurrentHashMap<>();
    private final Random random = new Random();
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.US);
    private final SimpleDateFormat isoFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);

    @Nullable
    private ReaderWebSocketServer webSocketServer;
    @Nullable
    private ReaderHttpServer httpServer;
    private boolean running;
    private int connectedClientCount;

    @Nullable
    private File chatFile;
    @Nullable
    private BufferedWriter chatWriter;
    private final Object chatWriterLock = new Object();
    private final SimpleDateFormat chatDateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
    private final SimpleDateFormat chatTimeFormat = new SimpleDateFormat("HH-mm-ss", Locale.US);
    private final SimpleDateFormat chatLogTimeFormat = new SimpleDateFormat("HH:mm:ss", Locale.US);

    public interface EventListener {
        void onServerStarted(@NonNull String address);

        void onServerStopped();

        void onClientConnected(int connectedClientCount);

        void onClientDisconnected(int connectedClientCount);

        void onLogEntry(@NonNull String logEntry);

        void onImageMessage(@NonNull String sender, @NonNull String imageUrl);
    }

    public final class LocalBinder extends Binder {
        @NonNull
        public WebSocketService getService() {
            return WebSocketService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        AppLogger.i(TAG, "Service created.");
    }

    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        AppLogger.d(TAG, "onStartCommand. startId=" + startId);
        startServer();
        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(@Nullable Intent intent) {
        AppLogger.d(TAG, "onBind.");
        return binder;
    }

    @Override
    public boolean onUnbind(@Nullable Intent intent) {
        AppLogger.d(TAG, "onUnbind.");
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        stopServer();
        listeners.clear();
        AppLogger.i(TAG, "Service destroyed.");
        super.onDestroy();
    }

    public void addEventListener(@NonNull EventListener listener) {
        listeners.add(listener);
        String address = getServerAddress();
        listener.onLogEntry(buildLogEntry(running && address != null ? "Server running at " + address : "Server ready. Press Start to begin broadcasting."));
        if (running) {
            listener.onServerStarted(address != null ? address : "");
        } else {
            listener.onServerStopped();
        }
    }

    public void removeEventListener(@NonNull EventListener listener) {
        listeners.remove(listener);
    }

    public void startServer() {
        if (running) {
            log("Server is already running.");
            return;
        }
        try {
            webSocketServer = new ReaderWebSocketServer(new InetSocketAddress(WEBSOCKET_PORT));
            webSocketServer.setReuseAddr(true);
            webSocketServer.start();
            startHttpServer();
            running = true;
            openChatFile();
            String address = getServerAddress();
            String httpAddress = getHttpAddress();
            log("WebSocket server started at " + address);
            if (httpAddress != null) {
                log("Browser page available at " + httpAddress);
            }
            notifyServerStarted(address);
        } catch (Exception exception) {
            running = false;
            log("Failed to start server: " + exception.getMessage());
            AppLogger.e(TAG, "Failed to start WebSocket server.", exception);
        }
    }

    public void rotateChatFile() {
        if (!running) {
            return;
        }
        closeChatFile();
        openChatFile();
    }

    public void stopServer() {
        if (!running) {
            return;
        }
        running = false;
        closeChatFile();
        stopHttpServer();
        if (webSocketServer != null) {
            try {
                webSocketServer.stop();
            } catch (InterruptedException exception) {
                AppLogger.e(TAG, "Interrupted while stopping server.", exception);
                Thread.currentThread().interrupt();
            }
            webSocketServer = null;
        }
        connectedClientCount = 0;
        log("Server stopped.");
        notifyServerStopped();
    }

    private void openChatFile() {
        File baseDirectory = AppStoragePaths.resolveWebSocketChatDirectory(this);
        String date = chatDateFormat.format(new Date());
        File dayDirectory = new File(baseDirectory, date);
        if (!dayDirectory.exists() && !dayDirectory.mkdirs()) {
            AppLogger.e(TAG, "Failed to create chat day directory: " + dayDirectory.getAbsolutePath());
            return;
        }
        String time = chatTimeFormat.format(new Date());
        String baseName = "Chat_" + time + "_" + date;
        File file = new File(dayDirectory, baseName + ".txt");
        int suffix = 1;
        while (file.exists()) {
            file = new File(dayDirectory, baseName + "_" + suffix + ".txt");
            suffix++;
        }
        try {
            synchronized (chatWriterLock) {
                chatWriter = new BufferedWriter(new FileWriter(file, true));
                chatFile = file;
            }
            log("Chat log file: " + file.getAbsolutePath());
        } catch (IOException exception) {
            AppLogger.e(TAG, "Failed to create chat log file: " + file.getAbsolutePath(), exception);
            chatFile = null;
            chatWriter = null;
        }
    }

    private void closeChatFile() {
        synchronized (chatWriterLock) {
            if (chatWriter != null) {
                try {
                    chatWriter.close();
                } catch (IOException exception) {
                    AppLogger.e(TAG, "Failed to close chat log file.", exception);
                }
                chatWriter = null;
                chatFile = null;
            }
        }
    }

    private void appendChatMessage(@NonNull String payload) {
        synchronized (chatWriterLock) {
            if (chatWriter == null) {
                return;
            }
            try {
                chatWriter.write(formatChatLogLine(payload));
                chatWriter.newLine();
                chatWriter.flush();
            } catch (IOException exception) {
                AppLogger.e(TAG, "Failed to write chat log.", exception);
            }
        }
    }

    @NonNull
    private String formatChatLogLine(@NonNull String payload) {
        try {
            JSONObject jsonObject = new JSONObject(payload);
            String timestamp = jsonObject.optString("timestamp", isoFormat.format(new Date()));
            String from = jsonObject.optString("from", "Unknown");
            String message = jsonObject.optString("message", "");
            boolean system = jsonObject.optBoolean("system", false);
            String timeOnly;
            try {
                Date parsed = isoFormat.parse(timestamp);
                timeOnly = parsed != null ? chatLogTimeFormat.format(parsed) : timestamp;
            } catch (ParseException exception) {
                timeOnly = timestamp;
            }
            String prefix = system ? "[System]" : "[" + from + "]";
            return "[" + timeOnly + "] " + prefix + " " + message;
        } catch (JSONException exception) {
            return payload;
        }
    }

    public boolean isRunning() {
        return running;
    }

    public int getConnectedClientCount() {
        return connectedClientCount;
    }

    @Nullable
    public String getServerAddress() {
        String ipAddress = getLocalIpAddress();
        if (ipAddress == null) {
            return null;
        }
        return "ws://" + ipAddress + ":" + WEBSOCKET_PORT;
    }

    @Nullable
    public String getHttpAddress() {
        String ipAddress = getLocalIpAddress();
        if (ipAddress == null) {
            return null;
        }
        return "http://" + ipAddress + ":" + HTTP_PORT;
    }

    private void startHttpServer() {
        if (httpServer != null) {
            return;
        }
        try {
            httpServer = new ReaderHttpServer(HTTP_PORT);
            httpServer.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
        } catch (Exception exception) {
            log("Failed to start HTTP server: " + exception.getMessage());
            AppLogger.e(TAG, "Failed to start HTTP server.", exception);
        }
    }

    private void stopHttpServer() {
        if (httpServer != null) {
            httpServer.stop();
            httpServer = null;
        }
    }

    public void sendCustomMessage(@NonNull String message) {
        String payload = buildChatMessage(SENDER_APP, message, false);
        broadcastMessage(payload);
        log("[" + SENDER_APP + "] " + message);
    }

    public void sendImageMessage(@NonNull String imageUrl) {
        String payload = buildChatMessage(SENDER_APP, getString(R.string.websocket_image_label), false, MESSAGE_TYPE_IMAGE, imageUrl);
        broadcastMessage(payload);
        log("[" + SENDER_APP + "] " + getString(R.string.websocket_image_label));
    }

    @NonNull
    private String buildChatMessage(@NonNull String sender, @NonNull String message, boolean isSystem) {
        return buildChatMessage(sender, message, isSystem, MESSAGE_TYPE_TEXT, null);
    }

    @NonNull
    private String buildChatMessage(@NonNull String sender, @NonNull String message, boolean isSystem, @NonNull String type, @Nullable String url) {
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("timestamp", isoFormat.format(new Date()));
            jsonObject.put("from", sender);
            jsonObject.put("message", message);
            jsonObject.put("system", isSystem);
            jsonObject.put("type", type);
            if (url != null) {
                jsonObject.put("url", url);
            }
        } catch (JSONException exception) {
            AppLogger.e(TAG, "Failed to build chat message JSON.", exception);
        }
        return jsonObject.toString();
    }

    private void broadcastMessage(@NonNull String message) {
        if (webSocketServer == null) {
            return;
        }
        webSocketServer.broadcast(message);
        appendChatMessage(message);
        notifyImageMessageIfNeeded(message);
    }

    private void notifyImageMessageIfNeeded(@NonNull String message) {
        try {
            JSONObject jsonObject = new JSONObject(message);
            if (MESSAGE_TYPE_IMAGE.equals(jsonObject.optString("type")) && jsonObject.has("url")) {
                String sender = jsonObject.optString("from", "Unknown");
                String url = jsonObject.optString("url", "");
                if (!url.isEmpty()) {
                    notifyImageMessage(sender, url);
                }
            }
        } catch (JSONException exception) {
            // Not a JSON message, ignore.
        }
    }

    private void notifyImageMessage(@NonNull String sender, @NonNull String imageUrl) {
        for (EventListener listener : listeners) {
            listener.onImageMessage(sender, imageUrl);
        }
    }

    private void setConnectedClientCount(int count) {
        connectedClientCount = count;
        notifyClientCountChanged();
    }

    private void log(@NonNull String message) {
        String entry = buildLogEntry(message);
        AppLogger.d(TAG, entry);
        for (EventListener listener : listeners) {
            listener.onLogEntry(entry);
        }
    }

    @NonNull
    private String buildLogEntry(@NonNull String message) {
        return "[" + timeFormat.format(new Date()) + "] " + message;
    }

    private void notifyServerStarted(@Nullable String address) {
        for (EventListener listener : listeners) {
            listener.onServerStarted(address != null ? address : "");
        }
    }

    private void notifyServerStopped() {
        for (EventListener listener : listeners) {
            listener.onServerStopped();
        }
    }

    private void notifyClientCountChanged() {
        for (EventListener listener : listeners) {
            if (connectedClientCount > 0) {
                listener.onClientConnected(connectedClientCount);
            } else {
                listener.onClientDisconnected(0);
            }
        }
    }

    @Nullable
    private static String getLocalIpAddress() {
        try {
            Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
            while (networkInterfaces.hasMoreElements()) {
                NetworkInterface networkInterface = networkInterfaces.nextElement();
                Enumeration<InetAddress> inetAddresses = networkInterface.getInetAddresses();
                while (inetAddresses.hasMoreElements()) {
                    InetAddress inetAddress = inetAddresses.nextElement();
                    if (inetAddress.isLoopbackAddress() || inetAddress.isLinkLocalAddress()) {
                        continue;
                    }
                    String hostAddress = inetAddress.getHostAddress();
                    if (hostAddress != null && (hostAddress.contains(":") || hostAddress.startsWith("10.") || hostAddress.startsWith("192.168."))) {
                        return hostAddress;
                    }
                }
            }
        } catch (SocketException exception) {
            AppLogger.e(TAG, "Failed to get local IP address.", exception);
        }
        return null;
    }

    private final class ReaderHttpServer extends NanoHTTPD {

        ReaderHttpServer(int port) {
            super(port);
        }

        @Override
        public Response serve(@NonNull IHTTPSession session) {
            String uri = session.getUri();
            if (uri != null && uri.startsWith(IMAGE_URL_PREFIX)) {
                return serveImage(uri);
            }
            String html = buildHtmlPage();
            byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
            return newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", new ByteArrayInputStream(bytes), bytes.length);
        }

        @NonNull
        private Response serveImage(@NonNull String uri) {
            String fileName = uri.substring(IMAGE_URL_PREFIX.length());
            if (fileName.isEmpty() || fileName.contains("..") || fileName.contains("/")) {
                return newFixedLengthResponse(Response.Status.BAD_REQUEST, NanoHTTPD.MIME_PLAINTEXT, "Invalid image name.");
            }
            File imageFile = new File(AppStoragePaths.resolveWebSocketChatImagesDirectory(WebSocketService.this), fileName);
            if (!imageFile.exists() || !imageFile.isFile()) {
                return newFixedLengthResponse(Response.Status.NOT_FOUND, NanoHTTPD.MIME_PLAINTEXT, "Image not found.");
            }
            String mimeType = getImageMimeType(fileName);
            try {
                FileInputStream inputStream = new FileInputStream(imageFile);
                return newFixedLengthResponse(Response.Status.OK, mimeType, inputStream, imageFile.length());
            } catch (FileNotFoundException exception) {
                AppLogger.e(TAG, "Image file not found: " + imageFile.getAbsolutePath(), exception);
                return newFixedLengthResponse(Response.Status.NOT_FOUND, NanoHTTPD.MIME_PLAINTEXT, "Image not found.");
            }
        }
    }

    @NonNull
    private static String getImageMimeType(@NonNull String fileName) {
        String lower = fileName.toLowerCase(Locale.US);
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return "image/jpeg";
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
        return "application/octet-stream";
    }

    @NonNull
    private String buildHtmlPage() {
        String wsAddress = getServerAddress();
        if (wsAddress == null) {
            wsAddress = "ws://" + getLocalIpAddress() + ":" + WEBSOCKET_PORT;
        }
        return "<!DOCTYPE html>"
                + "<html><head>"
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
                + "</style></head><body>"
                + "<h1>Reader RX WebSocket Monitor</h1>"
                + "<div id=\"status\">Connecting to " + wsAddress + " ...</div>"
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
                + "ws.onopen=function(){statusEl.textContent='Connected as '+userName+' to " + wsAddress + "';append('Connected as '+userName+'.',false,null);};"
                + "ws.onmessage=function(e){formatPayload(e.data);};"
                + "ws.onclose=function(){statusEl.textContent='Disconnected. Reconnecting...';append('Disconnected. Reconnecting...',false,null);setTimeout(connect,2000);};"
                + "ws.onerror=function(e){append('Error: '+e.type,false,null);};"
                + "window.send=function(){const input=document.getElementById('msg');const text=input.value.trim();if(text&&ws.readyState===1){ws.send(text);input.value='';}};"
                + "document.getElementById('msg').addEventListener('keypress',function(e){if(e.key==='Enter')send();});"
                + "}"
                + "connect();"
                + "</script></body></html>";
    }

    private final class ReaderWebSocketServer extends WebSocketServer {

        ReaderWebSocketServer(@NonNull InetSocketAddress address) {
            super(address);
        }

        @Override
        public void onOpen(@NonNull WebSocket conn, @NonNull ClientHandshake handshake) {
            String userName = resolveClientName(conn, handshake);
            clientNames.put(conn, userName);
            setConnectedClientCount(getConnections().size());
            log("Client connected: " + userName + " @ " + conn.getRemoteSocketAddress() + " (total: " + connectedClientCount + ")");
            String joinMessage = buildChatMessage(SENDER_SYSTEM, userName + " 进入聊天室", true);
            broadcastMessage(joinMessage);
        }

        @Override
        public void onClose(@NonNull WebSocket conn, int code, @NonNull String reason, boolean remote) {
            String userName = clientNames.remove(conn);
            setConnectedClientCount(getConnections().size());
            log("Client disconnected: " + (userName != null ? userName : conn.getRemoteSocketAddress()) + " (total: " + connectedClientCount + ")");
            if (userName != null) {
                String leaveMessage = buildChatMessage(SENDER_SYSTEM, userName + " 离开聊天室", true);
                broadcastMessage(leaveMessage);
            }
        }

        @Override
        public void onMessage(@NonNull WebSocket conn, @NonNull String message) {
            String userName = clientNames.get(conn);
            if (userName == null) {
                userName = conn.getRemoteSocketAddress().toString();
            }
            log("Received from " + userName + ": " + message);
            String payload = buildChatMessage(userName, message, false);
            broadcastMessage(payload);
        }

        @Override
        public void onError(@NonNull WebSocket conn, @NonNull Exception ex) {
            log("Server error: " + ex.getMessage());
            AppLogger.e(TAG, "WebSocket server error.", ex);
        }

        @Override
        public void onStart() {
            log("Server thread started on port " + getPort() + ".");
        }
    }

    @NonNull
    private String resolveClientName(@NonNull WebSocket conn, @NonNull ClientHandshake handshake) {
        String resourceDescriptor = handshake.getResourceDescriptor();
        if (resourceDescriptor != null && resourceDescriptor.contains("?")) {
            try {
                Uri uri = Uri.parse("ws://localhost" + resourceDescriptor);
                String name = uri.getQueryParameter("name");
                if (name != null && !name.trim().isEmpty()) {
                    return name.trim();
                }
            } catch (Exception exception) {
                AppLogger.e(TAG, "Failed to parse client name from handshake.", exception);
            }
        }
        return "User-" + String.format(Locale.US, "%04d", random.nextInt(10000));
    }
}
