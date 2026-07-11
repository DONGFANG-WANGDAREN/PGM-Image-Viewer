package com.DONGFANG_WANGDAREN.Reader_RX;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import fi.iki.elonen.NanoHTTPD;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Enumeration;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class WebSocketService extends Service {

    private static final String TAG = "WebSocketService";
    private static final int WEBSOCKET_PORT = 8080;
    private static final int HTTP_PORT = 8081;
    private static final long BROADCAST_INTERVAL_MS = 2_000L;

    private final IBinder binder = new LocalBinder();
    private final CopyOnWriteArrayList<EventListener> listeners = new CopyOnWriteArrayList<>();
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.US);
    private final SimpleDateFormat isoFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
    private final AtomicInteger sequenceNumber = new AtomicInteger(0);

    @Nullable
    private ReaderWebSocketServer webSocketServer;
    @Nullable
    private ReaderHttpServer httpServer;
    private ScheduledExecutorService broadcastExecutor;
    private boolean running;
    private int connectedClientCount;

    public interface EventListener {
        void onServerStarted(@NonNull String address);

        void onServerStopped();

        void onClientConnected(int connectedClientCount);

        void onClientDisconnected(int connectedClientCount);

        void onLogEntry(@NonNull String logEntry);
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
            startBroadcast();
            running = true;
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

    public void stopServer() {
        if (!running) {
            return;
        }
        running = false;
        stopBroadcast();
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
        broadcastMessage(message);
        log("Custom message broadcast: " + message);
    }

    private void startBroadcast() {
        if (broadcastExecutor != null && !broadcastExecutor.isShutdown()) {
            return;
        }
        broadcastExecutor = Executors.newSingleThreadScheduledExecutor();
        broadcastExecutor.scheduleAtFixedRate(this::broadcastPeriodicMessage, BROADCAST_INTERVAL_MS, BROADCAST_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    private void stopBroadcast() {
        if (broadcastExecutor == null) {
            return;
        }
        broadcastExecutor.shutdownNow();
        broadcastExecutor = null;
    }

    private void broadcastPeriodicMessage() {
        if (!running || webSocketServer == null) {
            return;
        }
        String message = buildPeriodicMessage();
        broadcastMessage(message);
        log("Broadcast: " + message);
    }

    @NonNull
    private String buildPeriodicMessage() {
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("sequence", sequenceNumber.incrementAndGet());
            jsonObject.put("timestamp", isoFormat.format(new Date()));
            jsonObject.put("message", "Hello from Reader RX");
            jsonObject.put("source", "WebSocketService");
            jsonObject.put("clients", connectedClientCount);
        } catch (JSONException exception) {
            AppLogger.e(TAG, "Failed to build message JSON.", exception);
        }
        return jsonObject.toString();
    }

    private void broadcastMessage(@NonNull String message) {
        if (webSocketServer == null) {
            return;
        }
        webSocketServer.broadcast(message);
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
            String html = buildHtmlPage();
            byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
            return newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", new ByteArrayInputStream(bytes), bytes.length);
        }
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
                + "<input id=\"msg\" type=\"text\" placeholder=\"Send message to server...\">"
                + "<button onclick=\"send()\">Send</button>"
                + "</div>"
                + "<script>"
                + "const logEl=document.getElementById('log');"
                + "const statusEl=document.getElementById('status');"
                + "function append(text){const d=new Date();const t=d.getHours().toString().padStart(2,'0')+':'+d.getMinutes().toString().padStart(2,'0')+':'+d.getSeconds().toString().padStart(2,'0');logEl.textContent+='['+t+'] '+text+'\\n';logEl.scrollTop=logEl.scrollHeight;}"
                + "function connect(){const ws=new WebSocket('" + wsAddress + "');"
                + "ws.onopen=function(){statusEl.textContent='Connected to " + wsAddress + "';append('Connected.');};"
                + "ws.onmessage=function(e){append('Received: '+e.data);};"
                + "ws.onclose=function(){statusEl.textContent='Disconnected. Reconnecting...';append('Disconnected. Reconnecting...');setTimeout(connect,2000);};"
                + "ws.onerror=function(e){append('Error: '+e.type);};"
                + "window.send=function(){const input=document.getElementById('msg');const text=input.value.trim();if(text&&ws.readyState===1){ws.send(text);append('Sent: '+text);input.value='';}};"
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
            setConnectedClientCount(getConnections().size());
            log("Client connected: " + conn.getRemoteSocketAddress() + " (total: " + connectedClientCount + ")");
        }

        @Override
        public void onClose(@NonNull WebSocket conn, int code, @NonNull String reason, boolean remote) {
            setConnectedClientCount(getConnections().size());
            log("Client disconnected: " + conn.getRemoteSocketAddress() + " (total: " + connectedClientCount + ")");
        }

        @Override
        public void onMessage(@NonNull WebSocket conn, @NonNull String message) {
            log("Received from " + conn.getRemoteSocketAddress() + ": " + message);
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
}
