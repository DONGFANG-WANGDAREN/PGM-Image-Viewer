package com.DONGFANG_WANGDAREN.Station_RX.ui.activity;


import com.DONGFANG_WANGDAREN.Station_RX.R;
import com.DONGFANG_WANGDAREN.Station_RX.app.AppLogger;
import com.DONGFANG_WANGDAREN.Station_RX.websocket.UserSessionSnapshot;
import com.DONGFANG_WANGDAREN.Station_RX.websocket.WebSocketService;
import com.DONGFANG_WANGDAREN.Station_RX.websocket.WebSocketServiceStatsSnapshot;
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

import com.google.android.material.appbar.MaterialToolbar;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ActivityWebSocketStats extends AppCompatActivity {

    private static final String TAG = "ActivityWebSocketStats";

    private final List<Row> rows = new ArrayList<>();
    private StatsAdapter adapter;
    private WebSocketService webSocketService;
    private boolean bound;
    @Nullable
    private ScheduledExecutorService refreshExecutor;

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
        toolbar.setNavigationOnClickListener(view -> finish());

        RecyclerView recyclerView = findViewById(R.id.recycler_view_stats);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new StatsAdapter(rows);
        recyclerView.setAdapter(adapter);

        bindToService();
        startRefreshTimer();

        AppLogger.i(TAG, "WebSocket stats opened.");
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
        runOnUiThread(this::refreshRows);
    }

    private void refreshRows() {
        rows.clear();
        WebSocketServiceStatsSnapshot snapshot = webSocketService != null ? webSocketService.getServiceStatsSnapshot() : null;
        List<UserSessionSnapshot> users = webSocketService != null ? webSocketService.getUserSessionSnapshots() : new ArrayList<>();
        long now = System.currentTimeMillis();

        rows.add(new Row(RowType.HEADER, getString(R.string.stats_header_service)));
        if (snapshot != null) {
            rows.add(new Row(RowType.STAT, getString(R.string.stats_status), snapshot.running ? getString(R.string.stats_running) : getString(R.string.stats_stopped)));
            rows.add(new Row(RowType.STAT, getString(R.string.stats_uptime), snapshot.formatUptime()));
            rows.add(new Row(RowType.STAT, getString(R.string.stats_messages_received), String.valueOf(snapshot.totalMessagesReceived)));
            rows.add(new Row(RowType.STAT, getString(R.string.stats_messages_sent), String.valueOf(snapshot.totalMessagesSent)));
            rows.add(new Row(RowType.STAT, getString(R.string.stats_total_users), String.valueOf(snapshot.totalUniqueUsers)));
            rows.add(new Row(RowType.STAT, getString(R.string.stats_active_users), String.valueOf(snapshot.activeUsers)));
            rows.add(new Row(RowType.STAT, getString(R.string.stats_peak_active_users), String.valueOf(snapshot.peakActiveUsers)));
            rows.add(new Row(RowType.STAT, getString(R.string.stats_websocket_port), String.valueOf(snapshot.webSocketPort)));
            rows.add(new Row(RowType.STAT, getString(R.string.stats_http_port), String.valueOf(snapshot.httpPort)));
        } else {
            rows.add(new Row(RowType.STAT, getString(R.string.stats_status), getString(R.string.stats_stopped)));
        }

        rows.add(new Row(RowType.HEADER, getString(R.string.stats_header_network)));
        if (snapshot != null) {
            rows.add(new Row(RowType.STAT, getString(R.string.stats_wifi_status), snapshot.wifiConnected ? getString(R.string.stats_wifi_connected) : getString(R.string.stats_wifi_disconnected)));
            rows.add(new Row(RowType.STAT, getString(R.string.stats_wifi_speed), snapshot.wifiLinkSpeedMbps < 0 ? getString(R.string.stats_unavailable) : snapshot.wifiLinkSpeedMbps + " Mbps"));
            String signalText;
            if (snapshot.wifiSignalDbm == Integer.MIN_VALUE) {
                signalText = getString(R.string.stats_unavailable);
            } else {
                signalText = snapshot.wifiSignalDbm + " dBm";
                if (snapshot.wifiSignalLevel >= 0) {
                    signalText += " (" + snapshot.wifiSignalLevel + "/4)";
                }
            }
            rows.add(new Row(RowType.STAT, getString(R.string.stats_wifi_signal), signalText));
            rows.add(new Row(RowType.STAT, getString(R.string.stats_bytes_received), snapshot.formatBytes(snapshot.bytesReceived)));
            rows.add(new Row(RowType.STAT, getString(R.string.stats_bytes_sent), snapshot.formatBytes(snapshot.bytesSent)));
        }

        rows.add(new Row(RowType.HEADER, getString(R.string.stats_header_device)));
        if (snapshot != null) {
            rows.add(new Row(RowType.STAT, getString(R.string.stats_cpu_usage), snapshot.cpuUsage < 0 ? getString(R.string.stats_cpu_unavailable) : String.format(Locale.US, "%.2f%%", snapshot.cpuUsage)));
        }

        rows.add(new Row(RowType.HEADER, getString(R.string.stats_header_users), getString(R.string.stats_header_users_count, users.size())));
        if (users.isEmpty()) {
            rows.add(new Row(RowType.STAT, getString(R.string.stats_no_users), ""));
        } else {
            for (UserSessionSnapshot user : users) {
                rows.add(new Row(user));
            }
        }

        adapter.notifyDataSetChanged();
    }

    private static final class Row {
        final RowType type;
        @Nullable
        final String label;
        @Nullable
        final String value;
        @Nullable
        final UserSessionSnapshot user;

        Row(@NonNull RowType type, @NonNull String label) {
            this(type, label, null, null);
        }

        Row(@NonNull RowType type, @NonNull String label, @Nullable String value) {
            this(type, label, value, null);
        }

        Row(@NonNull UserSessionSnapshot user) {
            this(RowType.USER, null, null, user);
        }

        Row(@NonNull RowType type, @Nullable String label, @Nullable String value, @Nullable UserSessionSnapshot user) {
            this.type = type;
            this.label = label;
            this.value = value;
            this.user = user;
        }
    }

    private enum RowType {
        HEADER, STAT, USER
    }

    private static final class StatsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        private static final int VIEW_TYPE_HEADER = 0;
        private static final int VIEW_TYPE_STAT = 1;
        private static final int VIEW_TYPE_USER = 2;

        private final List<Row> rows;

        StatsAdapter(@NonNull List<Row> rows) {
            this.rows = rows;
        }

        @Override
        public int getItemViewType(int position) {
            Row row = rows.get(position);
            switch (row.type) {
                case HEADER:
                    return VIEW_TYPE_HEADER;
                case USER:
                    return VIEW_TYPE_USER;
                default:
                    return VIEW_TYPE_STAT;
            }
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            if (viewType == VIEW_TYPE_HEADER) {
                return new HeaderViewHolder(inflater.inflate(R.layout.item_stat_header, parent, false));
            }
            if (viewType == VIEW_TYPE_USER) {
                return new UserViewHolder(inflater.inflate(R.layout.item_user_session, parent, false));
            }
            return new StatViewHolder(inflater.inflate(R.layout.item_stat, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            Row row = rows.get(position);
            if (holder instanceof HeaderViewHolder) {
                ((HeaderViewHolder) holder).bind(row.label);
            } else if (holder instanceof StatViewHolder) {
                ((StatViewHolder) holder).bind(row.label, row.value);
            } else if (holder instanceof UserViewHolder && row.user != null) {
                ((UserViewHolder) holder).bind(row.user, System.currentTimeMillis());
            }
        }

        @Override
        public int getItemCount() {
            return rows.size();
        }

        static final class HeaderViewHolder extends RecyclerView.ViewHolder {
            final TextView textView;

            HeaderViewHolder(@NonNull View itemView) {
                super(itemView);
                textView = itemView.findViewById(R.id.text_view_stat_header);
            }

            void bind(@Nullable String label) {
                textView.setText(label);
            }
        }

        static final class StatViewHolder extends RecyclerView.ViewHolder {
            final TextView labelView;
            final TextView valueView;

            StatViewHolder(@NonNull View itemView) {
                super(itemView);
                labelView = itemView.findViewById(R.id.text_view_stat_label);
                valueView = itemView.findViewById(R.id.text_view_stat_value);
            }

            void bind(@Nullable String label, @Nullable String value) {
                labelView.setText(label);
                valueView.setText(value);
            }
        }

        static final class UserViewHolder extends RecyclerView.ViewHolder {
            final TextView nameView;
            final TextView onlineView;
            final TextView addressView;
            final TextView statsView;

            UserViewHolder(@NonNull View itemView) {
                super(itemView);
                nameView = itemView.findViewById(R.id.text_view_user_name);
                onlineView = itemView.findViewById(R.id.text_view_user_online);
                addressView = itemView.findViewById(R.id.text_view_user_address);
                statsView = itemView.findViewById(R.id.text_view_user_stats);
            }

            void bind(@NonNull UserSessionSnapshot user, long now) {
                nameView.setText(user.userName);
                onlineView.setText(user.online ? R.string.stats_user_online : R.string.stats_user_offline);
                onlineView.setTextColor(itemView.getContext().getColor(user.online ? R.color.green_600 : R.color.slate_700));
                addressView.setText(user.address);

                String stay = user.formatStayDuration(now);
                String lastActive = formatDuration(now - user.lastActiveTime);
                String text = itemView.getContext().getString(
                        R.string.stats_user_summary,
                        stay,
                        user.messageCount,
                        lastActive);
                statsView.setText(text);
            }

            @NonNull
            private String formatDuration(long durationMs) {
                long totalSeconds = Math.max(0, durationMs / 1000);
                long minutes = totalSeconds / 60;
                long seconds = totalSeconds % 60;
                if (minutes > 0) {
                    return String.format(Locale.US, "%02d:%02d", minutes, seconds);
                }
                return String.format(Locale.US, "%02ds", seconds);
            }
        }
    }
}
