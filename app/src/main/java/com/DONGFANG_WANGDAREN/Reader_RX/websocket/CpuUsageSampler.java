package com.DONGFANG_WANGDAREN.Reader_RX.websocket;

import android.os.Process;

public final class CpuUsageSampler {

    private static final Object LOCK = new Object();
    private static long lastCpuTimeMs = -1;
    private static long lastWallTimeMs = -1;
    private static double lastCpuUsage = -1;

    private CpuUsageSampler() {
    }

    /**
     * Returns the estimated app process CPU usage as a percentage, or -1 if it cannot be read.
     * Uses {@link Process#getElapsedCpuTime()} which does not require reading /proc files.
     */
    public static double getProcessCpuUsage() {
        synchronized (LOCK) {
            long now = System.currentTimeMillis();
            long cpuTime = Process.getElapsedCpuTime();
            if (cpuTime < 0) {
                return -1;
            }
            if (lastCpuTimeMs < 0) {
                lastCpuTimeMs = cpuTime;
                lastWallTimeMs = now;
                return -1;
            }
            long cpuDiff = cpuTime - lastCpuTimeMs;
            long wallDiff = now - lastWallTimeMs;
            if (wallDiff <= 0) {
                return lastCpuUsage < 0 ? -1 : lastCpuUsage;
            }
            lastCpuUsage = Math.min(100.0, (cpuDiff * 100.0) / wallDiff);
            lastCpuTimeMs = cpuTime;
            lastWallTimeMs = now;
            return lastCpuUsage;
        }
    }
}
