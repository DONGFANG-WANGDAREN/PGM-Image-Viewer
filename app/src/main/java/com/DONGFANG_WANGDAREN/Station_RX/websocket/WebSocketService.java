package com.DONGFANG_WANGDAREN.Station_RX.websocket;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.DONGFANG_WANGDAREN.Station_RX.R;
import com.DONGFANG_WANGDAREN.Station_RX.app.AppConfig;
import com.DONGFANG_WANGDAREN.Station_RX.app.AppLogger;
import com.DONGFANG_WANGDAREN.Station_RX.ui.activity.ActivityTools;

import java.util.Random;

import fi.iki.elonen.NanoHTTPD;

public class WebSocketService extends Service {

    private static final String TAG = "WebSocketService";
    private static final String ACTION_STOP = "com.DONGFANG_WANGDAREN.Station_RX.STOP_WEBSOCKET";
    private static final String ACTION_REFRESH_NOTIFICATION = "com.DONGFANG_WANGDAREN.Station_RX.REFRESH_SERVICES_NOTIFICATION";
    private static final String NOTIFICATION_CHANNEL_ID = "websocket_service_channel";
    private static final int NOTIFICATION_ID = 1;

    private final IBinder binder = new LocalBinder();
    private final Random random = new Random();
    private final BroadcastReceiver refreshNotificationReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (running) {
                startForeground(NOTIFICATION_ID, buildNotification());
            }
        }
    };

    @Nullable
    private ReaderHttpServer httpServer;
    @Nullable
    private WebHttpRouter httpRouter;
    private boolean running;
    private int activeHttpPort = -1;
    @NonNull
    private String activeDisplayHost = "127.0.0.1";
    @NonNull
    private String httpAuthToken = "";

    private static volatile int runtimeHttpPort = -1;
    @NonNull
    private static volatile String runtimeDisplayHost = "127.0.0.1";
    private static volatile boolean runtimeRunning;

    public final class LocalBinder extends Binder {
        @NonNull
        public WebSocketService getService() {
            return WebSocketService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        registerRefreshNotificationReceiver();
        AppLogger.i(TAG, "File transfer service created.");
    }

    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopServer();
            return START_NOT_STICKY;
        }
        startServer();
        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(@Nullable Intent intent) {
        return binder;
    }

    @Override
    public void onDestroy() {
        stopServer();
        try {
            unregisterReceiver(refreshNotificationReceiver);
        } catch (IllegalArgumentException ignored) {
        }
        AppLogger.i(TAG, "File transfer service destroyed.");
        super.onDestroy();
    }

    public boolean isRunning() {
        return running;
    }

    public void startServer() {
        if (running) {
            startForeground(NOTIFICATION_ID, buildNotification());
            return;
        }
        try {
            activeDisplayHost = LanServerHelper.getDisplayHost();
            httpAuthToken = generateHttpAuthToken();
            int preferredHttpPort = AppConfig.get().getHttpPort();
            activeHttpPort = LanServerHelper.findAvailablePort(preferredHttpPort);
            httpRouter = new WebHttpRouter(this);
            httpServer = new ReaderHttpServer(activeHttpPort);
            startForeground(NOTIFICATION_ID, buildNotification());
            httpServer.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
            running = true;
            updateRuntimeState(true);
            if (activeHttpPort != preferredHttpPort) {
                AppLogger.i(TAG, "HTTP port switched from " + preferredHttpPort + " to " + activeHttpPort + ".");
            }
            AppLogger.i(TAG, "File transfer service started at " + getHttpAddress());
        } catch (Exception exception) {
            AppLogger.e(TAG, "Failed to start file transfer service.", exception);
            running = false;
            stopHttpServer();
            clearRuntimePorts();
            stopForegroundServiceIfNeeded();
        }
    }

    public void stopServer() {
        if (!running) {
            stopForegroundServiceIfNeeded();
            return;
        }
        running = false;
        stopHttpServer();
        clearRuntimePorts();
        AppLogger.i(TAG, "File transfer service stopped.");
        stopForegroundServiceIfNeeded();
    }

    public int getHttpPort() {
        return activeHttpPort > 0 ? activeHttpPort : AppConfig.get().getHttpPort();
    }

    public double getProcessCpuUsage() {
        return CpuUsageSampler.getProcessCpuUsage();
    }

    @Nullable
    public String getHttpAddress() {
        if (activeHttpPort <= 0) {
            return null;
        }
        return LanServerHelper.buildAddress("http", activeDisplayHost, activeHttpPort);
    }

    @Nullable
    public String getBrowserAddress() {
        return getHttpAddress();
    }

    @Nullable
    public String getWebLoginUrl() {
        String browserAddress = getBrowserAddress();
        if (browserAddress == null || browserAddress.isEmpty()) {
            return null;
        }
        return browserAddress + "/web-login";
    }

    @NonNull
    public String getHttpAuthToken() {
        return httpAuthToken;
    }

    @Nullable
    public static String getLocalIpAddress() {
        return LanServerHelper.getLocalIpAddress();
    }

    @Nullable
    public static String getRunningBrowserAddress() {
        if (!runtimeRunning || runtimeHttpPort <= 0) {
            return null;
        }
        return LanServerHelper.buildAddress("http", runtimeDisplayHost, runtimeHttpPort);
    }

    public static int getRunningHttpPort() {
        return runtimeHttpPort > 0 ? runtimeHttpPort : AppConfig.get().getHttpPort();
    }

    @NonNull
    public static String getLoopbackConfirmBaseAddress() {
        return "http://127.0.0.1:" + getRunningHttpPort();
    }

    @NonNull
    public static String getPreferredWebLoginUrl() {
        String runningAddress = getRunningBrowserAddress();
        if (runningAddress != null && !runningAddress.isEmpty()) {
            return runningAddress + "/web-login";
        }
        String displayHost = LanServerHelper.getDisplayHost();
        int predictedHttpPort = LanServerHelper.findAvailablePort(AppConfig.get().getHttpPort());
        return LanServerHelper.buildAddress("http", displayHost, predictedHttpPort) + "/web-login";
    }

    private void registerRefreshNotificationReceiver() {
        IntentFilter filter = new IntentFilter(ACTION_REFRESH_NOTIFICATION);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(refreshNotificationReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(refreshNotificationReceiver, filter);
        }
    }

    private void startHttpServer() {
    }

    private void stopHttpServer() {
        if (httpServer != null) {
            httpServer.stop();
            httpServer = null;
        }
        httpRouter = null;
        httpAuthToken = "";
        activeHttpPort = -1;
    }

    @NonNull
    private String generateHttpAuthToken() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder builder = new StringBuilder(32);
        for (int i = 0; i < 32; i++) {
            builder.append(chars.charAt(random.nextInt(chars.length())));
        }
        return builder.toString();
    }

    @NonNull
    private Notification buildNotification() {
        createNotificationChannel();
        String address = getWebLoginUrl();
        String content = address != null && !address.isEmpty()
                ? getString(R.string.websocket_browser_address) + ": " + address
                : getString(R.string.services_notification_text);
        return new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_share)
                .setContentTitle(getString(R.string.services_notification_title))
                .setContentText(content)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(content))
                .setContentIntent(getContentPendingIntent())
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, getString(R.string.websocket_notification_stop), getStopPendingIntent())
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null || manager.getNotificationChannel(NOTIFICATION_CHANNEL_ID) != null) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                getString(R.string.websocket_notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription(getString(R.string.websocket_notification_channel_description));
        manager.createNotificationChannel(channel);
    }

    @NonNull
    private PendingIntent getContentPendingIntent() {
        Intent intent = new Intent(this, ActivityTools.class);
        intent.putExtra(ActivityTools.EXTRA_OPEN_SCAN_LOGIN, true);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return PendingIntent.getActivity(this, 0, intent, flags);
    }

    @NonNull
    private PendingIntent getStopPendingIntent() {
        Intent intent = new Intent(this, WebSocketService.class);
        intent.setAction(ACTION_STOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return PendingIntent.getService(this, 1, intent, flags);
    }

    private void stopForegroundServiceIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE);
        } else {
            stopForeground(true);
        }
        stopSelf();
    }

    private void updateRuntimeState(boolean serverRunning) {
        runtimeRunning = serverRunning;
        runtimeDisplayHost = activeDisplayHost;
        runtimeHttpPort = serverRunning ? activeHttpPort : -1;
    }

    private void clearRuntimePorts() {
        activeHttpPort = -1;
        activeDisplayHost = LanServerHelper.getDisplayHost();
        updateRuntimeState(false);
    }

    private final class ReaderHttpServer extends NanoHTTPD {

        ReaderHttpServer(int port) {
            super(port);
        }

        @Override
        public Response serve(@NonNull IHTTPSession session) {
            if (httpRouter != null) {
                return httpRouter.route(session);
            }
            return NanoHTTPD.newFixedLengthResponse(Response.Status.SERVICE_UNAVAILABLE, NanoHTTPD.MIME_PLAINTEXT, "HTTP router not ready.");
        }
    }
}
