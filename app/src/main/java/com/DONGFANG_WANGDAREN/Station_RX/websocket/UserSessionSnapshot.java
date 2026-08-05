package com.DONGFANG_WANGDAREN.Station_RX.websocket;

import androidx.annotation.NonNull;

public final class UserSessionSnapshot {

    @NonNull
    public final String userName;
    @NonNull
    public final String address;
    public final long connectTime;
    public final long lastActiveTime;
    public final long disconnectTime;
    public final long messageCount;
    public final boolean online;
    public final boolean typing;

    public UserSessionSnapshot(@NonNull String userName,
                               @NonNull String address,
                               long connectTime,
                               long lastActiveTime,
                               long disconnectTime,
                               long messageCount,
                               boolean online,
                               boolean typing) {
        this.userName = userName;
        this.address = address;
        this.connectTime = connectTime;
        this.lastActiveTime = lastActiveTime;
        this.disconnectTime = disconnectTime;
        this.messageCount = messageCount;
        this.online = online;
        this.typing = typing;
    }

    @NonNull
    public String formatStayDuration(long now) {
        long end = online ? now : disconnectTime;
        long durationMs = Math.max(0, end - connectTime);
        long totalSeconds = durationMs / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        return String.format(java.util.Locale.US, "%02d:%02d:%02d", hours, minutes, seconds);
    }
}
