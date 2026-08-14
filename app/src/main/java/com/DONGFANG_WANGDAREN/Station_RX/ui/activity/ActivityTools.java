package com.DONGFANG_WANGDAREN.Station_RX.ui.activity;


import com.DONGFANG_WANGDAREN.Station_RX.R;
import com.DONGFANG_WANGDAREN.Station_RX.app.AppLogger;
import com.DONGFANG_WANGDAREN.Station_RX.ui.adapter.ToolsAdapter;
import com.DONGFANG_WANGDAREN.Station_RX.websocket.WebHttpRouter;
import com.DONGFANG_WANGDAREN.Station_RX.websocket.WebSocketService;
import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class ActivityTools extends AppCompatActivity {

    private static final String TAG = "ActivityTools";
    public static final String EXTRA_OPEN_SCAN_LOGIN = "open_scan_login";
    private static final long WEB_LOGIN_STATUS_POLL_INTERVAL_MS = 1500L;

    @NonNull
    private final Handler handler = new Handler(Looper.getMainLooper());
    @Nullable
    private Runnable pendingWebLoginPoll;

    private final ActivityResultLauncher<String> cameraPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            granted -> {
                if (granted) {
                    startScan();
                } else {
                    Toast.makeText(this, R.string.tools_scan_login_camera_denied, Toast.LENGTH_SHORT).show();
                }
            });

    private final ActivityResultLauncher<ScanOptions> scanLauncher = registerForActivityResult(
            new ScanContract(),
            result -> {
                if (result.getContents() != null) {
                    handleScanResult(result.getContents());
                }
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tools);

        MaterialToolbar toolbar = findViewById(R.id.toolbar_tools);
        toolbar.setNavigationOnClickListener(view -> finish());
        View aboutHeader = findViewById(R.id.card_about_app);
        aboutHeader.setOnClickListener(view -> openAboutApp());

        RecyclerView recyclerView = findViewById(R.id.recycler_view_tools);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        List<ToolItem> items = new ArrayList<>();
        items.add(new ToolItem(getString(R.string.tools_dashboard), this::openDashboard));
        items.add(new ToolItem(getString(R.string.tools_scan_login), this::openScanLogin));

        recyclerView.setAdapter(new ToolsAdapter(items));

        AppLogger.i(TAG, "Tools list opened.");
        if (getIntent().getBooleanExtra(EXTRA_OPEN_SCAN_LOGIN, false)) {
            recyclerView.post(this::openScanLogin);
        }
    }

    private void openAboutApp() {
        AppLogger.i(TAG, "Open about app page.");
        startActivity(new Intent(this, ActivityAboutApp.class));
    }

    private void openDashboard() {
        AppLogger.i(TAG, "Open WebSocket dashboard.");
        startActivity(new Intent(this, ActivityWebSocketDashboard.class));
    }

    private void openScanLogin() {
        AppLogger.i(TAG, "Open file transfer.");
        ContextCompat.startForegroundService(this, new Intent(this, WebSocketService.class));
        String loginUrl = WebSocketService.getPreferredWebLoginUrl();
        showScanLoginDialog(loginUrl);
    }

    private void showScanLoginDialog(@NonNull String loginUrl) {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_scan_login, null);
        TextView urlText = view.findViewById(R.id.text_view_scan_login_url);
        TextView hintText = view.findViewById(R.id.text_view_scan_login_hint);
        MaterialButton copyButton = view.findViewById(R.id.button_copy_scan_login_url);
        MaterialButton confirmButton = view.findViewById(R.id.button_confirm_scan_login);
        MaterialButton scanButton = view.findViewById(R.id.button_start_scan);
        urlText.setText(loginUrl);
        if (hintText != null) {
            hintText.setText(R.string.tools_scan_login_confirm_waiting);
        }
        confirmButton.setVisibility(View.GONE);
        confirmButton.setEnabled(false);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.tools_scan_login)
                .setView(view)
                .setCancelable(true)
                .create();

        copyButton.setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null) {
                ClipData clip = ClipData.newPlainText(getString(R.string.tools_scan_login), loginUrl);
                clipboard.setPrimaryClip(clip);
                Toast.makeText(this, R.string.tools_scan_login_url_copied, Toast.LENGTH_SHORT).show();
            }
        });

        confirmButton.setOnClickListener(v -> {
            WebHttpRouter.PendingWebLoginInfo pendingInfo = WebHttpRouter.getLatestPendingWebLoginInfo();
            if (pendingInfo == null) {
                Toast.makeText(this, R.string.tools_scan_login_failed, Toast.LENGTH_SHORT).show();
                return;
            }
            confirmButton.setEnabled(false);
            new Thread(() -> {
                boolean success = WebHttpRouter.confirmPendingWebLogin(pendingInfo.sessionId, pendingInfo.token) != null;
                runOnUiThread(() -> {
                    confirmButton.setEnabled(true);
                    Toast.makeText(this, success ? R.string.tools_scan_login_success : R.string.tools_scan_login_failed, Toast.LENGTH_SHORT).show();
                    if (success) {
                        dialog.dismiss();
                    }
                });
            }).start();
        });

        scanButton.setOnClickListener(v -> {
            dialog.dismiss();
            startCameraScan();
        });

        dialog.setOnDismissListener(d -> stopPendingWebLoginPolling());
        dialog.show();
        startPendingWebLoginPolling(confirmButton, hintText);
    }

    private void startCameraScan() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startScan();
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void startScan() {
        ScanOptions options = new ScanOptions();
        options.setDesiredBarcodeFormats(ScanOptions.QR_CODE);
        options.setPrompt(getString(R.string.tools_scan_login_prompt));
        options.setCameraId(0);
        options.setBeepEnabled(true);
        options.setBarcodeImageEnabled(false);
        options.setOrientationLocked(true);
        scanLauncher.launch(options);
    }

    private void handleScanResult(@NonNull String contents) {
        String confirmUrl = resolveConfirmUrl(contents);
        if (confirmUrl == null) {
            Toast.makeText(this, R.string.tools_scan_login_invalid, Toast.LENGTH_SHORT).show();
            return;
        }
        new Thread(() -> {
            boolean success = callConfirmUrl(confirmUrl);
            runOnUiThread(() -> Toast.makeText(this, success ? R.string.tools_scan_login_success : R.string.tools_scan_login_failed, Toast.LENGTH_SHORT).show());
        }).start();
    }

    @Nullable
    private String resolveConfirmUrl(@NonNull String contents) {
        if (contents.startsWith("http://") || contents.startsWith("https://")) {
            if (contents.contains("/api/confirm?")) {
                return contents;
            }
            return null;
        }
        if (contents.startsWith("rrx://login")) {
            Uri uri = Uri.parse(contents);
            String session = uri.getQueryParameter("session");
            String token = uri.getQueryParameter("token");
            if (session == null || token == null) {
                return null;
            }
            return WebSocketService.getLoopbackConfirmBaseAddress()
                    + "/api/confirm?session=" + Uri.encode(session)
                    + "&token=" + Uri.encode(token);
        }
        return null;
    }

    private boolean callConfirmUrl(@NonNull String urlString) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(urlString);
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setRequestMethod("GET");
            return connection.getResponseCode() == HttpURLConnection.HTTP_OK;
        } catch (Exception exception) {
            AppLogger.e(TAG, "Failed to call confirm URL.", exception);
            return false;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private void startPendingWebLoginPolling(@NonNull MaterialButton confirmButton, @Nullable TextView hintText) {
        stopPendingWebLoginPolling();
        pendingWebLoginPoll = new Runnable() {
            @Override
            public void run() {
                WebHttpRouter.PendingWebLoginInfo pendingInfo = WebHttpRouter.getLatestPendingWebLoginInfo();
                boolean ready = pendingInfo != null;
                confirmButton.setVisibility(ready ? View.VISIBLE : View.GONE);
                confirmButton.setEnabled(ready);
                if (hintText != null) {
                    hintText.setText(ready ? R.string.tools_scan_login_confirm_ready : R.string.tools_scan_login_confirm_waiting);
                }
                handler.postDelayed(this, WEB_LOGIN_STATUS_POLL_INTERVAL_MS);
            }
        };
        handler.post(pendingWebLoginPoll);
    }

    private void stopPendingWebLoginPolling() {
        if (pendingWebLoginPoll != null) {
            handler.removeCallbacks(pendingWebLoginPoll);
            pendingWebLoginPoll = null;
        }
    }

    @Override
    protected void onDestroy() {
        stopPendingWebLoginPolling();
        super.onDestroy();
    }

    public static final class ToolItem {

        @NonNull
        public final String title;
        @NonNull
        public final Runnable action;

        public ToolItem(@NonNull String title, @NonNull Runnable action) {
            this.title = title;
            this.action = action;
        }
    }
}
