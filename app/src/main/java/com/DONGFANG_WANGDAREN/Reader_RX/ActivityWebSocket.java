package com.DONGFANG_WANGDAREN.Reader_RX;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

public class ActivityWebSocket extends AppCompatActivity {

    private static final String TAG = "ActivityWebSocket";

    private TextView textViewAddress;
    private TextView textViewHttpAddress;
    private TextView textViewStatus;
    private MaterialButton buttonToggle;
    private EditText editTextMessage;
    private MaterialButton buttonSend;
    private TextView textViewLog;
    private ScrollView scrollViewLog;

    @Nullable
    private WebSocketService webSocketService;
    private boolean serviceBound;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(@NonNull ComponentName name, @NonNull IBinder service) {
            WebSocketService.LocalBinder binder = (WebSocketService.LocalBinder) service;
            webSocketService = binder.getService();
            serviceBound = true;
            webSocketService.addEventListener(serviceEventListener);
            updateUi(webSocketService.isRunning(), webSocketService.getConnectedClientCount());
            AppLogger.i(TAG, "Service connected.");
        }

        @Override
        public void onServiceDisconnected(@NonNull ComponentName name) {
            webSocketService = null;
            serviceBound = false;
            updateUi(false, 0);
            AppLogger.i(TAG, "Service disconnected.");
        }
    };

    private final WebSocketService.EventListener serviceEventListener = new WebSocketService.EventListener() {
        @Override
        public void onServerStarted(@NonNull String address) {
            updateUi(true, webSocketService != null ? webSocketService.getConnectedClientCount() : 0);
        }

        @Override
        public void onServerStopped() {
            updateUi(false, 0);
        }

        @Override
        public void onClientConnected(int connectedClientCount) {
            updateClientCount(connectedClientCount);
        }

        @Override
        public void onClientDisconnected(int connectedClientCount) {
            updateClientCount(connectedClientCount);
        }

        @Override
        public void onLogEntry(@NonNull String logEntry) {
            log(logEntry);
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_web_socket);
        AppLogger.i(TAG, "onCreate.");

        textViewAddress = findViewById(R.id.text_view_websocket_address);
        textViewHttpAddress = findViewById(R.id.text_view_websocket_http_address);
        textViewStatus = findViewById(R.id.text_view_websocket_status);
        buttonToggle = findViewById(R.id.button_websocket_toggle);
        textViewHttpAddress.setOnClickListener(view -> openHttpAddressInBrowser());
        editTextMessage = findViewById(R.id.edit_text_websocket_message);
        buttonSend = findViewById(R.id.button_websocket_send);
        textViewLog = findViewById(R.id.text_view_websocket_log);
        scrollViewLog = findViewById(R.id.scroll_view_websocket_log);

        buttonToggle.setOnClickListener(view -> toggleServer());
        buttonSend.setOnClickListener(view -> sendCustomMessage());

        startAndBindService();
        updateUi(false, 0);
    }

    private void startAndBindService() {
        Intent serviceIntent = new Intent(this, WebSocketService.class);
        startService(serviceIntent);
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    private void toggleServer() {
        if (webSocketService == null) {
            showToast(R.string.websocket_error_not_connected);
            return;
        }
        if (webSocketService.isRunning()) {
            webSocketService.stopServer();
        } else {
            webSocketService.startServer();
        }
    }

    private void sendCustomMessage() {
        if (webSocketService == null || !webSocketService.isRunning()) {
            showToast(R.string.websocket_error_not_connected);
            return;
        }
        String message = editTextMessage.getText().toString().trim();
        if (message.isEmpty()) {
            return;
        }
        webSocketService.sendCustomMessage(message);
        editTextMessage.setText("");
    }

    private void updateUi(boolean running, int clientCount) {
        runOnUiThread(() -> {
            String address = webSocketService != null ? webSocketService.getServerAddress() : null;
            String httpAddress = webSocketService != null ? webSocketService.getHttpAddress() : null;
            textViewAddress.setText(address != null ? address : "Unknown");
            textViewHttpAddress.setText(httpAddress != null ? httpAddress : "Unknown");
            buttonToggle.setText(running ? R.string.websocket_stop : R.string.websocket_start);
            textViewStatus.setText(running ? R.string.websocket_status_running : R.string.websocket_status_stopped);
            updateClientCount(clientCount);
            buttonSend.setEnabled(running);
        });
    }

    private void updateClientCount(int count) {
        runOnUiThread(() -> {
            String status = getString(R.string.websocket_status_running)
                    + " — " + getString(R.string.websocket_clients, count);
            if (webSocketService != null && webSocketService.isRunning()) {
                textViewStatus.setText(status);
            }
        });
    }

    private void log(@NonNull String line) {
        runOnUiThread(() -> {
            if (textViewLog.getText().length() > 0) {
                textViewLog.append("\n");
            }
            textViewLog.append(line);
            scrollToBottomWithoutFocus();
        });
    }

    private void scrollToBottomWithoutFocus() {
        scrollViewLog.post(() -> {
            int contentHeight = scrollViewLog.getChildAt(0).getHeight();
            int scrollViewHeight = scrollViewLog.getHeight();
            int scrollTo = Math.max(0, contentHeight - scrollViewHeight);
            scrollViewLog.scrollTo(0, scrollTo);
        });
    }

    private void showToast(int stringResId) {
        Toast.makeText(this, stringResId, Toast.LENGTH_SHORT).show();
    }

    private void openHttpAddressInBrowser() {
        String httpAddress = webSocketService != null ? webSocketService.getHttpAddress() : null;
        if (httpAddress == null || httpAddress.isEmpty()) {
            return;
        }
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(httpAddress));
        startActivity(intent);
    }

    @Override
    protected void onDestroy() {
        if (serviceBound) {
            if (webSocketService != null) {
                webSocketService.removeEventListener(serviceEventListener);
            }
            unbindService(serviceConnection);
            serviceBound = false;
        }
        AppLogger.i(TAG, "onDestroy.");
        super.onDestroy();
    }
}
