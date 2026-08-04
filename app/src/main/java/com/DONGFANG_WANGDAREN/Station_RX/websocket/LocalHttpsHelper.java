package com.DONGFANG_WANGDAREN.Station_RX.websocket;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.Calendar;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.TrustManagerFactory;
import javax.security.auth.x500.X500Principal;

public final class LocalHttpsHelper {

    private static final String KEYSTORE_PROVIDER = "AndroidKeyStore";
    private static final String CERTIFICATE_ALIAS = "station_rx_local_https";
    private static final String DEBUG_SERVER_URL = "http://192.168.1.68:7777/event";
    private static final String DEBUG_SESSION_ID = "https-login-failure";

    private LocalHttpsHelper() {
    }

    @NonNull
    public static SSLServerSocketFactory createServerSocketFactory() throws GeneralSecurityException, IOException {
        try {
            SSLServerSocketFactory factory = createServerSslContext().getServerSocketFactory();
            // #region debug-point B:https-factory-created
            reportDebug("B", "LocalHttpsHelper:createServerSocketFactory", "[DEBUG] HTTPS server socket factory created", "\"alias\":\"" + CERTIFICATE_ALIAS + "\"");
            // #endregion
            return factory;
        } catch (GeneralSecurityException | IOException exception) {
            // #region debug-point B:https-factory-failed
            reportDebug("B", "LocalHttpsHelper:createServerSocketFactory", "[DEBUG] HTTPS server socket factory failed", "\"alias\":\"" + CERTIFICATE_ALIAS + "\",\"errorType\":\"" + exception.getClass().getSimpleName() + "\",\"errorMessage\":\"" + escapeJson(exception.getMessage()) + "\"");
            // #endregion
            throw exception;
        }
    }

    @NonNull
    public static SSLContext createServerSslContext() throws GeneralSecurityException, IOException {
        try {
            KeyStore keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER);
            keyStore.load(null);
            boolean aliasExists = keyStore.containsAlias(CERTIFICATE_ALIAS);
            // #region debug-point B:https-keystore-loaded
            reportDebug("B", "LocalHttpsHelper:createServerSslContext", "[DEBUG] HTTPS keystore loaded", "\"provider\":\"" + KEYSTORE_PROVIDER + "\",\"alias\":\"" + CERTIFICATE_ALIAS + "\",\"aliasExists\":" + aliasExists);
            // #endregion
            if (!aliasExists) {
                generateCertificate();
                keyStore.load(null);
                // #region debug-point B:https-certificate-generated
                reportDebug("B", "LocalHttpsHelper:createServerSslContext", "[DEBUG] HTTPS certificate generated", "\"alias\":\"" + CERTIFICATE_ALIAS + "\",\"aliasExistsAfter\":" + keyStore.containsAlias(CERTIFICATE_ALIAS));
                // #endregion
            }

            KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keyManagerFactory.init(keyStore, null);
            // #region debug-point B:https-key-manager-init
            reportDebug("B", "LocalHttpsHelper:createServerSslContext", "[DEBUG] HTTPS key manager initialized", "\"algorithm\":\"" + keyManagerFactory.getAlgorithm() + "\"");
            // #endregion

            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(keyStore);
            // #region debug-point B:https-trust-manager-init
            reportDebug("B", "LocalHttpsHelper:createServerSslContext", "[DEBUG] HTTPS trust manager initialized", "\"algorithm\":\"" + trustManagerFactory.getAlgorithm() + "\"");
            // #endregion

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(keyManagerFactory.getKeyManagers(), trustManagerFactory.getTrustManagers(), new SecureRandom());
            // #region debug-point B:https-context-ready
            reportDebug("B", "LocalHttpsHelper:createServerSslContext", "[DEBUG] HTTPS SSL context ready", "\"protocol\":\"" + sslContext.getProtocol() + "\"");
            // #endregion
            return sslContext;
        } catch (GeneralSecurityException | IOException exception) {
            // #region debug-point B:https-context-failed
            reportDebug("B", "LocalHttpsHelper:createServerSslContext", "[DEBUG] HTTPS SSL context failed", "\"alias\":\"" + CERTIFICATE_ALIAS + "\",\"errorType\":\"" + exception.getClass().getSimpleName() + "\",\"errorMessage\":\"" + escapeJson(exception.getMessage()) + "\"");
            // #endregion
            throw exception;
        }
    }

    private static void generateCertificate() throws GeneralSecurityException {
        Calendar notBefore = Calendar.getInstance();
        Calendar notAfter = Calendar.getInstance();
        notAfter.add(Calendar.YEAR, 10);

        KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(
                CERTIFICATE_ALIAS,
                KeyProperties.PURPOSE_SIGN | KeyProperties.PURPOSE_DECRYPT | KeyProperties.PURPOSE_ENCRYPT)
                .setKeySize(2048)
                .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
                .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                .setEncryptionPaddings(
                        KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1,
                        KeyProperties.ENCRYPTION_PADDING_RSA_OAEP)
                .setCertificateSubject(new X500Principal("CN=Station RX Local HTTPS,O=Station RX"))
                .setCertificateSerialNumber(new BigInteger(64, new SecureRandom()).abs().add(BigInteger.ONE))
                .setCertificateNotBefore(notBefore.getTime())
                .setCertificateNotAfter(notAfter.getTime())
                .build();

        KeyPairGenerator generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, KEYSTORE_PROVIDER);
        generator.initialize(spec);
        generator.generateKeyPair();
    }

    private static void reportDebug(@NonNull String hypothesisId, @NonNull String location, @NonNull String msg, @NonNull String dataJson) {
        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(DEBUG_SERVER_URL).openConnection();
                connection.setConnectTimeout(1500);
                connection.setReadTimeout(1500);
                connection.setRequestMethod("POST");
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json");
                String payload = "{\"sessionId\":\"" + DEBUG_SESSION_ID + "\",\"runId\":\"pre-fix\",\"hypothesisId\":\"" + hypothesisId + "\",\"location\":\"" + location + "\",\"msg\":\"" + escapeJson(msg) + "\",\"data\":{" + dataJson + "},\"ts\":" + System.currentTimeMillis() + "}";
                try (OutputStream outputStream = connection.getOutputStream()) {
                    outputStream.write(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }
                connection.getResponseCode();
            } catch (Exception ignored) {
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }).start();
    }

    @NonNull
    private static String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }
}
