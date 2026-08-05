package com.DONGFANG_WANGDAREN.Station_RX.websocket;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.SocketException;
import java.util.Enumeration;

public final class LanServerHelper {

    private static final int PORT_SCAN_LIMIT = 100;

    private LanServerHelper() {
    }

    @Nullable
    public static String getLocalIpAddress() {
        String fallbackAddress = null;
        try {
            Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
            while (networkInterfaces.hasMoreElements()) {
                NetworkInterface networkInterface = networkInterfaces.nextElement();
                Enumeration<InetAddress> inetAddresses = networkInterface.getInetAddresses();
                while (inetAddresses.hasMoreElements()) {
                    InetAddress inetAddress = inetAddresses.nextElement();
                    if (!(inetAddress instanceof Inet4Address) || inetAddress.isLoopbackAddress()) {
                        continue;
                    }
                    String hostAddress = inetAddress.getHostAddress();
                    if (hostAddress == null || hostAddress.isEmpty()) {
                        continue;
                    }
                    if (inetAddress.isSiteLocalAddress()) {
                        return hostAddress;
                    }
                    if (fallbackAddress == null) {
                        fallbackAddress = hostAddress;
                    }
                }
            }
        } catch (SocketException exception) {
            return null;
        }
        return fallbackAddress;
    }

    @NonNull
    public static String getDisplayHost() {
        String ipAddress = getLocalIpAddress();
        return ipAddress == null || ipAddress.isEmpty() ? "127.0.0.1" : ipAddress;
    }

    public static int findAvailablePort(int preferredPort) {
        int startPort = Math.max(1024, preferredPort);
        for (int port = startPort; port < startPort + PORT_SCAN_LIMIT; port++) {
            if (isPortAvailable(port)) {
                return port;
            }
        }
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException exception) {
            return startPort;
        }
    }

    public static boolean isPortAvailable(int port) {
        try (ServerSocket socket = new ServerSocket()) {
            socket.setReuseAddress(true);
            socket.bind(new InetSocketAddress("0.0.0.0", port));
            return true;
        } catch (IOException exception) {
            return false;
        }
    }

    @NonNull
    public static String buildAddress(@NonNull String scheme, @NonNull String host, int port) {
        return scheme + "://" + host + ":" + port;
    }
}
