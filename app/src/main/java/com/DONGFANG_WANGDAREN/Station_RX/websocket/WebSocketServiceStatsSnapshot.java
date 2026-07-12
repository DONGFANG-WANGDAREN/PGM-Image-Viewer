package com.DONGFANG_WANGDAREN.Station_RX.websocket;

import androidx.annotation.NonNull;

public final class WebSocketServiceStatsSnapshot {

    public final boolean running;
    public final long uptimeMs;
    public final long totalMessagesReceived;
    public final long totalMessagesSent;
    public final long totalUniqueUsers;
    public final int activeUsers;
    public final int peakActiveUsers;
    public final int webSocketPort;
    public final int httpPort;
    public final double cpuUsage;
    public final long bytesReceived;
    public final long bytesSent;
    public final int wifiLinkSpeedMbps;
    public final int wifiSignalDbm;
    public final int wifiSignalLevel;
    public final boolean wifiConnected;

    public WebSocketServiceStatsSnapshot(boolean running,
                                         long uptimeMs,
                                         long totalMessagesReceived,
                                         long totalMessagesSent,
                                         long totalUniqueUsers,
                                         int activeUsers,
                                         int peakActiveUsers,
                                         int webSocketPort,
                                         int httpPort,
                                         double cpuUsage,
                                         long bytesReceived,
                                         long bytesSent,
                                         int wifiLinkSpeedMbps,
                                         int wifiSignalDbm,
                                         int wifiSignalLevel,
                                         boolean wifiConnected) {
        this.running = running;
        this.uptimeMs = uptimeMs;
        this.totalMessagesReceived = totalMessagesReceived;
        this.totalMessagesSent = totalMessagesSent;
        this.totalUniqueUsers = totalUniqueUsers;
        this.activeUsers = activeUsers;
        this.peakActiveUsers = peakActiveUsers;
        this.webSocketPort = webSocketPort;
        this.httpPort = httpPort;
        this.cpuUsage = cpuUsage;
        this.bytesReceived = bytesReceived;
        this.bytesSent = bytesSent;
        this.wifiLinkSpeedMbps = wifiLinkSpeedMbps;
        this.wifiSignalDbm = wifiSignalDbm;
        this.wifiSignalLevel = wifiSignalLevel;
        this.wifiConnected = wifiConnected;
    }

    @NonNull
    public String formatUptime() {
        long totalSeconds = uptimeMs / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        return String.format(java.util.Locale.US, "%02d:%02d:%02d", hours, minutes, seconds);
    }

    @NonNull
    public String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return String.format(java.util.Locale.US, "%.2f KB", bytes / 1024.0);
        }
        return String.format(java.util.Locale.US, "%.2f MB", bytes / (1024.0 * 1024.0));
    }
}
