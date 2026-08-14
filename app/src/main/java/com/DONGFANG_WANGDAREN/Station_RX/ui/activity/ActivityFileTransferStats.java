package com.DONGFANG_WANGDAREN.Station_RX.ui.activity;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
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

import com.DONGFANG_WANGDAREN.Station_RX.R;
import com.DONGFANG_WANGDAREN.Station_RX.websocket.WebHttpRouter;
import com.DONGFANG_WANGDAREN.Station_RX.websocket.WebSocketService;
import com.google.android.material.appbar.MaterialToolbar;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ActivityFileTransferStats extends AppCompatActivity {

    private final List<Row> rows = new ArrayList<>();
    private StatsAdapter adapter;
    @Nullable
    private ScheduledExecutorService refreshExecutor;
    @Nullable
    private WebSocketService webSocketService;
    private boolean bound;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            webSocketService = ((WebSocketService.LocalBinder) service).getService();
            bound = true;
            refreshRows();
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
        setContentView(R.layout.activity_web_socket_stats);

        MaterialToolbar toolbar = findViewById(R.id.toolbar_stats);
        toolbar.setTitle(R.string.file_transfer_stats_title);
        toolbar.setNavigationOnClickListener(view -> finish());

        RecyclerView recyclerView = findViewById(R.id.recycler_view_stats);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new StatsAdapter(rows, this::openClientDetails);
        recyclerView.setAdapter(adapter);

        bindToService();
        startRefreshTimer();
        refreshRows();
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
        bindService(new Intent(this, WebSocketService.class), serviceConnection, Context.BIND_AUTO_CREATE);
    }

    private void startRefreshTimer() {
        stopRefreshTimer();
        refreshExecutor = Executors.newSingleThreadScheduledExecutor();
        refreshExecutor.scheduleAtFixedRate(() -> runOnUiThread(this::refreshRows), 0, 1000, TimeUnit.MILLISECONDS);
    }

    private void stopRefreshTimer() {
        if (refreshExecutor != null) {
            refreshExecutor.shutdownNow();
            refreshExecutor = null;
        }
    }

    private void refreshRows() {
        rows.clear();

        boolean running = webSocketService != null && webSocketService.isRunning();
        String loginUrl = running && webSocketService != null ? webSocketService.getWebLoginUrl() : null;
        List<WebHttpRouter.FileTransferClientSnapshot> snapshots = WebHttpRouter.getFileTransferClientSnapshots();
        int connectedCount = 0;
        int pendingCount = 0;
        for (WebHttpRouter.FileTransferClientSnapshot snapshot : snapshots) {
            if (snapshot.authenticated) {
                connectedCount++;
            } else {
                pendingCount++;
            }
        }

        rows.add(new Row(RowType.HEADER, getString(R.string.stats_header_service)));
        rows.add(new Row(RowType.STAT, getString(R.string.stats_status),
                getString(running ? R.string.stats_running : R.string.stats_stopped)));
        rows.add(new Row(RowType.STAT, getString(R.string.file_transfer_stats_login_url),
                loginUrl != null && !loginUrl.isEmpty() ? loginUrl : getString(R.string.reader_stats_empty_value)));
        rows.add(new Row(RowType.STAT, getString(R.string.file_transfer_stats_connection_state),
                buildOverallConnectionState(snapshots)));
        rows.add(new Row(RowType.STAT, getString(R.string.file_transfer_stats_client_count), String.valueOf(connectedCount)));
        rows.add(new Row(RowType.STAT, getString(R.string.file_transfer_stats_pending_count), String.valueOf(pendingCount)));

        rows.add(new Row(RowType.HEADER, getString(R.string.file_transfer_stats_header_clients)));
        if (snapshots.isEmpty()) {
            rows.add(new Row(RowType.STAT, getString(R.string.file_transfer_stats_computer),
                    getString(R.string.file_transfer_stats_no_clients)));
        } else {
            for (WebHttpRouter.FileTransferClientSnapshot snapshot : snapshots) {
                rows.add(Row.forClient(
                        buildComputerName(snapshot),
                        buildClientSummary(snapshot),
                        snapshot.sessionId
                ));
            }
        }

        adapter.notifyDataSetChanged();
    }

    private void openClientDetails(@NonNull String sessionId) {
        Intent intent = new Intent(this, ActivityFileTransferClientDetails.class);
        intent.putExtra(ActivityFileTransferClientDetails.EXTRA_SESSION_ID, sessionId);
        startActivity(intent);
    }

    @NonNull
    private String buildOverallConnectionState(@NonNull List<WebHttpRouter.FileTransferClientSnapshot> snapshots) {
        if (snapshots.isEmpty()) {
            return getString(R.string.file_transfer_stats_no_client);
        }
        for (WebHttpRouter.FileTransferClientSnapshot snapshot : snapshots) {
            if (snapshot.authenticated) {
                return getString(R.string.file_transfer_stats_state_connected);
            }
        }
        return getString(R.string.file_transfer_stats_state_waiting_confirm);
    }

    @NonNull
    private String buildClientSummary(@NonNull WebHttpRouter.FileTransferClientSnapshot snapshot) {
        return getString(
                R.string.file_transfer_stats_client_summary,
                buildConnectionState(snapshot),
                valueOrDefault(snapshot.remoteAddress),
                formatLastSeen(snapshot.lastSeenAt)
        );
    }

    @NonNull
    private String buildConnectionState(@NonNull WebHttpRouter.FileTransferClientSnapshot snapshot) {
        if (snapshot.authenticated) {
            return getString(R.string.file_transfer_stats_state_connected);
        }
        if (snapshot.qrReady) {
            return getString(R.string.file_transfer_stats_state_waiting_confirm);
        }
        return getString(R.string.file_transfer_stats_state_waiting_page);
    }

    @NonNull
    private String buildComputerName(@NonNull WebHttpRouter.FileTransferClientSnapshot snapshot) {
        String browser = valueOrDefault(snapshot.browserName);
        String platform = valueOrDefault(snapshot.platform);
        if (browser.equals(getString(R.string.reader_stats_empty_value))) {
            return platform;
        }
        if (platform.equals(getString(R.string.reader_stats_empty_value))) {
            return browser;
        }
        return browser + " / " + platform;
    }

    @NonNull
    private String valueOrDefault(@Nullable String value) {
        return value != null && !value.trim().isEmpty() ? value.trim() : getString(R.string.reader_stats_empty_value);
    }

    @NonNull
    private String formatLastSeen(long timestamp) {
        if (timestamp <= 0) {
            return getString(R.string.reader_stats_empty_value);
        }
        long diff = Math.max(0, System.currentTimeMillis() - timestamp);
        if (diff < 1000) {
            return getString(R.string.file_transfer_stats_just_now);
        }
        long seconds = diff / 1000;
        if (seconds < 60) {
            return getString(R.string.file_transfer_stats_seconds_ago, seconds);
        }
        long minutes = seconds / 60;
        if (minutes < 60) {
            return getString(R.string.file_transfer_stats_minutes_ago, minutes);
        }
        long hours = minutes / 60;
        if (hours < 24) {
            return getString(R.string.file_transfer_stats_hours_ago, hours);
        }
        return formatTimestamp(timestamp);
    }

    @NonNull
    private String formatTimestamp(long timestamp) {
        if (timestamp <= 0) {
            return getString(R.string.reader_stats_empty_value);
        }
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date(timestamp));
    }

    private static final class Row {
        @NonNull
        final RowType type;
        @NonNull
        final String label;
        @Nullable
        final String value;
        @Nullable
        final String sessionId;

        Row(@NonNull RowType type, @NonNull String label) {
            this(type, label, null, null);
        }

        Row(@NonNull RowType type, @NonNull String label, @Nullable String value) {
            this(type, label, value, null);
        }

        Row(@NonNull RowType type, @NonNull String label, @Nullable String value, @Nullable String sessionId) {
            this.type = type;
            this.label = label;
            this.value = value;
            this.sessionId = sessionId;
        }

        static Row forClient(@NonNull String label, @NonNull String value, @NonNull String sessionId) {
            return new Row(RowType.CLIENT, label, value, sessionId);
        }
    }

    private enum RowType {
        HEADER, STAT, CLIENT
    }

    private static final class StatsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private static final int VIEW_TYPE_HEADER = 0;
        private static final int VIEW_TYPE_STAT = 1;
        private static final int VIEW_TYPE_CLIENT = 2;

        @NonNull
        private final List<Row> rows;
        @NonNull
        private final OnClientClickListener listener;

        StatsAdapter(@NonNull List<Row> rows, @NonNull OnClientClickListener listener) {
            this.rows = rows;
            this.listener = listener;
        }

        @Override
        public int getItemViewType(int position) {
            RowType type = rows.get(position).type;
            if (type == RowType.HEADER) {
                return VIEW_TYPE_HEADER;
            }
            return type == RowType.CLIENT ? VIEW_TYPE_CLIENT : VIEW_TYPE_STAT;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            if (viewType == VIEW_TYPE_HEADER) {
                return new HeaderViewHolder(inflater.inflate(R.layout.item_stat_header, parent, false));
            }
            return new StatViewHolder(inflater.inflate(R.layout.item_stat, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            Row row = rows.get(position);
            if (holder instanceof HeaderViewHolder) {
                ((HeaderViewHolder) holder).bind(row.label);
                return;
            }
            StatViewHolder statHolder = (StatViewHolder) holder;
            statHolder.bind(row.label, row.value);
            if (row.type == RowType.CLIENT && row.sessionId != null) {
                statHolder.itemView.setClickable(true);
                statHolder.itemView.setOnClickListener(view -> listener.onClientClick(row.sessionId));
            } else {
                statHolder.itemView.setClickable(false);
                statHolder.itemView.setOnClickListener(null);
            }
        }

        @Override
        public int getItemCount() {
            return rows.size();
        }

        private static final class HeaderViewHolder extends RecyclerView.ViewHolder {
            @NonNull
            private final TextView textView;

            HeaderViewHolder(@NonNull View itemView) {
                super(itemView);
                textView = itemView.findViewById(R.id.text_view_stat_header);
            }

            void bind(@NonNull String label) {
                textView.setText(label);
            }
        }

        private static final class StatViewHolder extends RecyclerView.ViewHolder {
            @NonNull
            private final TextView labelView;
            @NonNull
            private final TextView valueView;

            StatViewHolder(@NonNull View itemView) {
                super(itemView);
                labelView = itemView.findViewById(R.id.text_view_stat_label);
                valueView = itemView.findViewById(R.id.text_view_stat_value);
            }

            void bind(@NonNull String label, @Nullable String value) {
                labelView.setText(label);
                valueView.setText(value);
            }
        }
    }

    private interface OnClientClickListener {
        void onClientClick(@NonNull String sessionId);
    }
}
