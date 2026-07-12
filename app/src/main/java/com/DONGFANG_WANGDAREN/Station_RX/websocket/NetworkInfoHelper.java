package com.DONGFANG_WANGDAREN.Station_RX.websocket;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;

import androidx.annotation.NonNull;

public final class NetworkInfoHelper {

    private NetworkInfoHelper() {
    }

    public static boolean isWifiConnected(@NonNull Context context) {
        ConnectivityManager manager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (manager == null) {
            return false;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Network network = manager.getActiveNetwork();
            if (network == null) {
                return false;
            }
            NetworkCapabilities capabilities = manager.getNetworkCapabilities(network);
            return capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
        }
        return false;
    }

    /**
     * Returns the WiFi link speed in Mbps, or -1 if unavailable.
     */
    public static int getWifiLinkSpeedMbps(@NonNull Context context) {
        WifiManager wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wifiManager == null) {
            return -1;
        }
        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
        if (wifiInfo == null) {
            return -1;
        }
        return wifiInfo.getLinkSpeed();
    }

    /**
     * Returns the WiFi signal level in dBm, or {@link Integer#MIN_VALUE} if unavailable.
     */
    public static int getWifiSignalDbm(@NonNull Context context) {
        WifiManager wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wifiManager == null) {
            return Integer.MIN_VALUE;
        }
        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
        if (wifiInfo == null) {
            return Integer.MIN_VALUE;
        }
        return wifiInfo.getRssi();
    }

    /**
     * Returns the WiFi signal level as a number from 0 to 4, or -1 if unavailable.
     */
    public static int getWifiSignalLevel(@NonNull Context context) {
        int rssi = getWifiSignalDbm(context);
        if (rssi == Integer.MIN_VALUE) {
            return -1;
        }
        WifiManager wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wifiManager == null) {
            return -1;
        }
        return WifiManager.calculateSignalLevel(rssi, 5);
    }

    /**
     * Estimates the distance to the connected access point using a free-space path-loss model.
     * Result is in meters, or -1 if unavailable. This is only a rough estimate.
     */
    public static double estimateWifiDistanceMeters(@NonNull Context context) {
        int rssi = getWifiSignalDbm(context);
        if (rssi == Integer.MIN_VALUE) {
            return -1;
        }
        int txPower = -59;
        double pathLossDb = txPower - rssi;
        double pathLossExponent = 2.5;
        return Math.pow(10, pathLossDb / (10.0 * pathLossExponent));
    }
}
