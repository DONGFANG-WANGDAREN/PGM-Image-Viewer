package com.DONGFANG_WANGDAREN.Station_RX.ui.activity;
import com.DONGFANG_WANGDAREN.Station_RX.R;
import com.DONGFANG_WANGDAREN.Station_RX.app.AppLogger;
import com.DONGFANG_WANGDAREN.Station_RX.websocket.WebHttpRouter;
import com.DONGFANG_WANGDAREN.Station_RX.websocket.WebSocketService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.IBinder;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ActivityWebSocketDashboard extends AppCompatActivity {

    private static final String TAG = "ActivityWebSocketDashboard";
    private static final int DASHBOARD_ITEM_SPACING_DP = 12;

    private static final int ID_READER = 0;
    private static final int ID_FILE_TRANSFER = 1;

    private final List<ServiceItem> items = new ArrayList<>();
    private ServiceAdapter adapter;
    private WebSocketService webSocketService;
    private boolean bound;
    @Nullable
    private ScheduledExecutorService refreshExecutor;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            webSocketService = ((WebSocketService.LocalBinder) service).getService();
            bound = true;
            refreshItems();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            webSocketService = null;
            bound = false;
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_web_socket_dashboard);

        MaterialToolbar toolbar = findViewById(R.id.toolbar_dashboard);
        toolbar.setNavigationOnClickListener(view -> finish());

        RecyclerView recyclerView = findViewById(R.id.recycler_view_dashboard);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.addItemDecoration(new DashboardSpacingItemDecoration(dpToPx(DASHBOARD_ITEM_SPACING_DP)));
        adapter = new ServiceAdapter(items, this::openServiceStats);
        recyclerView.setAdapter(adapter);

        bindToService();
        startRefreshTimer();

        AppLogger.i(TAG, "WebSocket dashboard opened.");
    }

    @Override
    protected void onDestroy() {
        stopRefreshTimer();
        if (bound) {
            unbindService(serviceConnection);
            bound = false;
        }
        webSocketService = null;
        super.onDestroy();
    }

    private void bindToService() {
        Intent intent = new Intent(this, WebSocketService.class);
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    private void startRefreshTimer() {
        stopRefreshTimer();
        refreshExecutor = Executors.newSingleThreadScheduledExecutor();
        refreshExecutor.scheduleAtFixedRate(this::runOnUiThreadRefresh, 0, 1000, TimeUnit.MILLISECONDS);
    }

    private void stopRefreshTimer() {
        if (refreshExecutor != null) {
            refreshExecutor.shutdownNow();
            refreshExecutor = null;
        }
    }

    private void runOnUiThreadRefresh() {
        runOnUiThread(this::refreshItems);
    }

    private void refreshItems() {
        items.clear();

        ActivityPictureViewer.ReaderDashboardSnapshot readerSnapshot = ActivityPictureViewer.getReaderDashboardSnapshot();
        String readerStatus;
        if (readerSnapshot.opened) {
            readerStatus = readerSnapshot.fileName
                    + "\n" + readerSnapshot.filePath
                    + "\n" + getString(R.string.dashboard_reader_status_detail,
                    readerSnapshot.detailedTypeLabel,
                    formatBytes(readerSnapshot.fileSizeBytes),
                    formatBytes(readerSnapshot.usedMemoryBytes));
            if (!readerSnapshot.mimeType.isEmpty()) {
                readerStatus = readerStatus + "\nMIME: " + readerSnapshot.mimeType;
            }
        } else {
            readerStatus = getString(R.string.dashboard_reader_status_empty, formatBytes(readerSnapshot.usedMemoryBytes));
        }
        items.add(new ServiceItem(ID_READER, getString(R.string.dashboard_reader), readerStatus, true));

        boolean scanLoginRunning = webSocketService != null && webSocketService.isRunning();
        String scanLoginStatus = scanLoginRunning ? getString(R.string.dashboard_status_running_simple) : getString(R.string.dashboard_status_stopped);
        String loginUrl = webSocketService != null ? webSocketService.getWebLoginUrl() : null;
        if (scanLoginRunning && loginUrl != null && !loginUrl.isEmpty()) {
            List<WebHttpRouter.FileTransferClientSnapshot> snapshots = WebHttpRouter.getFileTransferClientSnapshots();
            if (!snapshots.isEmpty()) {
                int connectedCount = 0;
                int pendingCount = 0;
                for (WebHttpRouter.FileTransferClientSnapshot snapshot : snapshots) {
                    if (snapshot.authenticated) {
                        connectedCount++;
                    } else {
                        pendingCount++;
                    }
                }
                StringBuilder builder = new StringBuilder(loginUrl)
                        .append('\n')
                        .append(getString(R.string.dashboard_file_transfer_client_counts, connectedCount, pendingCount));
                int previewCount = Math.min(3, snapshots.size());
                for (int i = 0; i < previewCount; i++) {
                    WebHttpRouter.FileTransferClientSnapshot snapshot = snapshots.get(i);
                    builder.append('\n').append(getString(
                            R.string.dashboard_file_transfer_client_line,
                            buildComputerName(snapshot),
                            buildClientStateLabel(snapshot)));
                }
                if (snapshots.size() > previewCount) {
                    builder.append('\n').append(getString(
                            R.string.dashboard_file_transfer_more_clients,
                            snapshots.size() - previewCount));
                }
                scanLoginStatus = builder.toString();
            } else {
                WebHttpRouter.PendingWebLoginInfo pendingInfo = WebHttpRouter.getLatestPendingWebLoginInfo();
                scanLoginStatus = loginUrl + "\n" + getString(
                        pendingInfo != null ? R.string.dashboard_file_transfer_pending : R.string.dashboard_file_transfer_idle);
            }
        }
        items.add(new ServiceItem(ID_FILE_TRANSFER, getString(R.string.dashboard_file_transfer), scanLoginStatus, scanLoginRunning));

        adapter.notifyDataSetChanged();
    }

    private void openServiceStats(int position) {
        if (position < 0 || position >= items.size()) {
            return;
        }
        ServiceItem item = items.get(position);
        switch (item.id) {
            case ID_READER:
                startActivity(new Intent(this, ActivityReaderStats.class));
                break;
            case ID_FILE_TRANSFER:
                startActivity(new Intent(this, ActivityFileTransferStats.class));
                break;
            default:
                break;
        }
    }

    @NonNull
    private String buildComputerName(@NonNull WebHttpRouter.FileTransferClientSnapshot snapshot) {
        String browser = snapshot.browserName != null && !snapshot.browserName.trim().isEmpty()
                ? snapshot.browserName.trim()
                : getString(R.string.reader_stats_empty_value);
        String platform = snapshot.platform != null && !snapshot.platform.trim().isEmpty()
                ? snapshot.platform.trim()
                : getString(R.string.reader_stats_empty_value);
        if (browser.equals(getString(R.string.reader_stats_empty_value))) {
            return platform;
        }
        if (platform.equals(getString(R.string.reader_stats_empty_value))) {
            return browser;
        }
        return browser + " / " + platform;
    }

    @NonNull
    private String buildClientStateLabel(@NonNull WebHttpRouter.FileTransferClientSnapshot snapshot) {
        String state = snapshot.authenticated
                ? getString(R.string.file_transfer_stats_state_connected)
                : getString(R.string.file_transfer_stats_state_waiting_confirm);
        String address = snapshot.remoteAddress != null && !snapshot.remoteAddress.trim().isEmpty()
                ? snapshot.remoteAddress.trim()
                : getString(R.string.reader_stats_empty_value);
        return String.format(Locale.US, "%s - %s", state, address);
    }

    @NonNull
    private String formatBytes(long bytes) {
        if (bytes < 0) {
            return getString(R.string.stats_unavailable);
        }
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return String.format(java.util.Locale.US, "%.2f KB", bytes / 1024.0);
        }
        if (bytes < 1024L * 1024 * 1024) {
            return String.format(java.util.Locale.US, "%.2f MB", bytes / (1024.0 * 1024.0));
        }
        return String.format(java.util.Locale.US, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private static final class ServiceItem {
        final int id;
        @NonNull
        final String name;
        @NonNull
        final String status;
        final boolean running;

        ServiceItem(int id, @NonNull String name, @NonNull String status, boolean running) {
            this.id = id;
            this.name = name;
            this.status = status;
            this.running = running;
        }
    }

    private static final class ServiceAdapter extends RecyclerView.Adapter<ServiceAdapter.ViewHolder> {

        private final List<ServiceItem> items;
        private final OnItemClickListener listener;

        ServiceAdapter(@NonNull List<ServiceItem> items, @NonNull OnItemClickListener listener) {
            this.items = items;
            this.listener = listener;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_dashboard_service, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            ServiceItem item = items.get(position);
            holder.nameView.setText(item.name);
            holder.statusView.setText(item.status);
            holder.itemView.setOnClickListener(view -> listener.onItemClick(position));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static final class ViewHolder extends RecyclerView.ViewHolder {
            final TextView nameView;
            final TextView statusView;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                nameView = itemView.findViewById(R.id.text_view_service_name);
                statusView = itemView.findViewById(R.id.text_view_service_status);
            }
        }
    }

    private interface OnItemClickListener {
        void onItemClick(int position);
    }

    private static final class DashboardSpacingItemDecoration extends RecyclerView.ItemDecoration {
        private final int verticalSpacingPx;

        DashboardSpacingItemDecoration(int verticalSpacingPx) {
            this.verticalSpacingPx = verticalSpacingPx;
        }

        @Override
        public void getItemOffsets(
                @NonNull Rect outRect,
                @NonNull View view,
                @NonNull RecyclerView parent,
                @NonNull RecyclerView.State state
        ) {
            int position = parent.getChildAdapterPosition(view);
            if (position > 0) {
                outRect.top = verticalSpacingPx;
            }
        }
    }
}
