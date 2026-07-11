package com.DONGFANG_WANGDAREN.Reader_RX.app;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;

import androidx.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public final class AppConfig {

    private static final String TAG = "AppConfig";
    private static final String CONFIG_FILE_NAME = "app_config.json";

    private static volatile AppConfig instance;

    private final String appFolderName;
    private final String logsFolder;
    private final String historyFolder;
    private final String webSocketChatFolder;
    private final String webSocketChatImagesFolder;

    private final String logDayFolderFormat;
    private final String logFileNameFormat;
    private final String logFileExtension;
    private final String historyFileName;
    private final String chatDayFolderFormat;
    private final String chatFileNameFormat;
    private final String chatFileExtension;
    private final String chatLogTimeFormat;
    private final String chatImageFileNameFormat;
    private final String chatImageFileDefaultExtension;

    private final int webSocketPort;
    private final int httpPort;
    private final String senderApp;
    private final String senderSystem;
    private final String messageTypeText;
    private final String messageTypeImage;
    private final String imageUrlPrefix;

    private final int textPreviewInitialCharacters;
    private final int largeTextInteractionThreshold;
    private final int maxHistoryRecords;
    private final float defaultTextSizeSp;
    private final float minTextSizeSp;
    private final float maxTextSizeSp;

    private AppConfig(@NonNull JSONObject config) {
        JSONObject storage = config.optJSONObject("storage");
        if (storage == null) {
            storage = new JSONObject();
        }
        this.appFolderName = storage.optString("appFolderName", "Reader RX");
        this.logsFolder = storage.optString("logsFolder", "Log");
        this.historyFolder = storage.optString("historyFolder", "History");
        this.webSocketChatFolder = storage.optString("webSocketChatFolder", "WebSocket/Chat");
        this.webSocketChatImagesFolder = storage.optString("webSocketChatImagesFolder", "Images");

        JSONObject naming = config.optJSONObject("naming");
        if (naming == null) {
            naming = new JSONObject();
        }
        this.logDayFolderFormat = naming.optString("logDayFolderFormat", "yyyy-MM-dd");
        this.logFileNameFormat = naming.optString("logFileNameFormat", "Log_HH-mm_yyyy-MM-dd");
        this.logFileExtension = naming.optString("logFileExtension", ".txt");
        this.historyFileName = naming.optString("historyFileName", "History.json");
        this.chatDayFolderFormat = naming.optString("chatDayFolderFormat", "yyyy-MM-dd");
        this.chatFileNameFormat = naming.optString("chatFileNameFormat", "Chat_HH-mm-ss_yyyy-MM-dd");
        this.chatFileExtension = naming.optString("chatFileExtension", ".txt");
        this.chatLogTimeFormat = naming.optString("chatLogTimeFormat", "HH:mm:ss");
        this.chatImageFileNameFormat = naming.optString("chatImageFileNameFormat", "Chat_yyyy-MM-dd_HH-mm-ss");
        this.chatImageFileDefaultExtension = naming.optString("chatImageFileDefaultExtension", ".jpg");

        JSONObject websocket = config.optJSONObject("websocket");
        if (websocket == null) {
            websocket = new JSONObject();
        }
        this.webSocketPort = websocket.optInt("port", 8080);
        this.httpPort = websocket.optInt("httpPort", 8081);
        this.senderApp = websocket.optString("senderApp", "Reader RX");
        this.senderSystem = websocket.optString("senderSystem", "System");
        this.messageTypeText = websocket.optString("messageTypeText", "text");
        this.messageTypeImage = websocket.optString("messageTypeImage", "image");
        this.imageUrlPrefix = websocket.optString("imageUrlPrefix", "/images/");

        JSONObject viewer = config.optJSONObject("viewer");
        if (viewer == null) {
            viewer = new JSONObject();
        }
        this.textPreviewInitialCharacters = viewer.optInt("textPreviewInitialCharacters", 24 * 1024);
        this.largeTextInteractionThreshold = viewer.optInt("largeTextInteractionThreshold", 512 * 1024);
        this.maxHistoryRecords = viewer.optInt("maxHistoryRecords", 20);
        this.defaultTextSizeSp = (float) viewer.optDouble("defaultTextSizeSp", 14.0);
        this.minTextSizeSp = (float) viewer.optDouble("minTextSizeSp", 10.0);
        this.maxTextSizeSp = (float) viewer.optDouble("maxTextSizeSp", 30.0);
    }

    public static void init(@NonNull Context context) {
        if (instance != null) {
            return;
        }
        synchronized (AppConfig.class) {
            if (instance != null) {
                return;
            }
            instance = load(context);
        }
    }

    @NonNull
    private static AppConfig load(@NonNull Context context) {
        AssetManager assets = context.getAssets();
        try (InputStream inputStream = assets.open(CONFIG_FILE_NAME);
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
            JSONObject config = new JSONObject(builder.toString());
            return new AppConfig(config);
        } catch (IOException | JSONException exception) {
            Log.e(TAG, "Failed to load app config, using defaults.", exception);
            return defaults();
        }
    }

    @NonNull
    private static AppConfig defaults() {
        return new AppConfig(new JSONObject());
    }

    @NonNull
    public static AppConfig get() {
        AppConfig config = instance;
        if (config == null) {
            Log.w(TAG, "AppConfig not initialized, returning defaults.");
            return defaults();
        }
        return config;
    }

    @NonNull
    public String getAppFolderName() {
        return appFolderName;
    }

    @NonNull
    public String getLogsFolder() {
        return logsFolder;
    }

    @NonNull
    public String getHistoryFolder() {
        return historyFolder;
    }

    @NonNull
    public String getWebSocketChatFolder() {
        return webSocketChatFolder;
    }

    @NonNull
    public String getWebSocketChatImagesFolder() {
        return webSocketChatImagesFolder;
    }

    @NonNull
    public String getLogDayFolderFormat() {
        return logDayFolderFormat;
    }

    @NonNull
    public String getLogFileNameFormat() {
        return logFileNameFormat;
    }

    @NonNull
    public String getLogFileExtension() {
        return logFileExtension;
    }

    @NonNull
    public String getHistoryFileName() {
        return historyFileName;
    }

    @NonNull
    public String getChatDayFolderFormat() {
        return chatDayFolderFormat;
    }

    @NonNull
    public String getChatFileNameFormat() {
        return chatFileNameFormat;
    }

    @NonNull
    public String getChatFileExtension() {
        return chatFileExtension;
    }

    @NonNull
    public String getChatLogTimeFormat() {
        return chatLogTimeFormat;
    }

    @NonNull
    public String getChatImageFileNameFormat() {
        return chatImageFileNameFormat;
    }

    @NonNull
    public String getChatImageFileDefaultExtension() {
        return chatImageFileDefaultExtension;
    }

    public int getWebSocketPort() {
        return webSocketPort;
    }

    public int getHttpPort() {
        return httpPort;
    }

    @NonNull
    public String getSenderApp() {
        return senderApp;
    }

    @NonNull
    public String getSenderSystem() {
        return senderSystem;
    }

    @NonNull
    public String getMessageTypeText() {
        return messageTypeText;
    }

    @NonNull
    public String getMessageTypeImage() {
        return messageTypeImage;
    }

    @NonNull
    public String getImageUrlPrefix() {
        return imageUrlPrefix;
    }

    public int getTextPreviewInitialCharacters() {
        return textPreviewInitialCharacters;
    }

    public int getLargeTextInteractionThreshold() {
        return largeTextInteractionThreshold;
    }

    public int getMaxHistoryRecords() {
        return maxHistoryRecords;
    }

    public float getDefaultTextSizeSp() {
        return defaultTextSizeSp;
    }

    public float getMinTextSizeSp() {
        return minTextSizeSp;
    }

    public float getMaxTextSizeSp() {
        return maxTextSizeSp;
    }
}
