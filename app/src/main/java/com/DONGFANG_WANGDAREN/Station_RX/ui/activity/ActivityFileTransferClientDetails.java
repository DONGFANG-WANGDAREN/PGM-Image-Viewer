package com.DONGFANG_WANGDAREN.Station_RX.ui.activity;

import android.os.Bundle;
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
import com.google.android.material.appbar.MaterialToolbar;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ActivityFileTransferClientDetails extends AppCompatActivity {

    public static final String EXTRA_SESSION_ID = "session_id";

    private final List<Row> rows = new ArrayList<>();
    private StatsAdapter adapter;
    @Nullable
    private ScheduledExecutorService refreshExecutor;
    @NonNull
    private String sessionId = "";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_web_socket_stats);

        sessionId = getIntent().getStringExtra(EXTRA_SESSION_ID);
        if (sessionId == null) {
            sessionId = "";
        }

        MaterialToolbar toolbar = findViewById(R.id.toolbar_stats);
        toolbar.setTitle(R.string.file_transfer_client_details_title);
        toolbar.setNavigationOnClickListener(view -> finish());

        RecyclerView recyclerView = findViewById(R.id.recycler_view_stats);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new StatsAdapter(rows);
        recyclerView.setAdapter(adapter);

        startRefreshTimer();
        refreshRows();
    }

    @Override
    protected void onDestroy() {
        stopRefreshTimer();
        super.onDestroy();
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
        WebHttpRouter.FileTransferClientSnapshot snapshot = WebHttpRouter.getFileTransferClientSnapshot(sessionId);

        rows.add(new Row(true, getString(R.string.file_transfer_stats_header_computer), null));
        if (snapshot == null) {
            rows.add(new Row(false, getString(R.string.file_transfer_stats_computer),
                    getString(R.string.file_transfer_stats_no_client)));
        } else {
            rows.add(new Row(false, getString(R.string.file_transfer_stats_computer), buildComputerName(snapshot)));
            rows.add(new Row(false, getString(R.string.file_transfer_stats_connection_state), buildConnectionState(snapshot)));
            rows.add(new Row(false, getString(R.string.file_transfer_stats_session_id), snapshot.sessionId));
            rows.add(new Row(false, getString(R.string.file_transfer_stats_browser), valueOrDefault(snapshot.browserName)));
            rows.add(new Row(false, getString(R.string.file_transfer_stats_system), valueOrDefault(snapshot.platform)));
            rows.add(new Row(false, getString(R.string.file_transfer_stats_language), valueOrDefault(snapshot.language)));
            rows.add(new Row(false, getString(R.string.file_transfer_stats_timezone), valueOrDefault(snapshot.timezone)));
            rows.add(new Row(false, getString(R.string.file_transfer_stats_resolution), formatResolution(snapshot.screenWidth, snapshot.screenHeight)));
            rows.add(new Row(false, getString(R.string.file_transfer_stats_ip), valueOrDefault(snapshot.remoteAddress)));
            rows.add(new Row(false, getString(R.string.file_transfer_stats_page), valueOrDefault(snapshot.currentPage)));
            rows.add(new Row(false, getString(R.string.file_transfer_stats_user_agent), valueOrDefault(snapshot.userAgent)));
            rows.add(new Row(false, getString(R.string.file_transfer_stats_created_at), formatTimestamp(snapshot.createdAt)));
            rows.add(new Row(false, getString(R.string.file_transfer_stats_last_seen), formatLastSeen(snapshot.lastSeenAt)));
            if (snapshot.authenticatedAt > 0) {
                rows.add(new Row(false, getString(R.string.file_transfer_stats_confirmed_at), formatTimestamp(snapshot.authenticatedAt)));
            }
        }

        adapter.notifyDataSetChanged();
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
    private String valueOrDefault(@Nullable String value) {
        return value != null && !value.trim().isEmpty() ? value.trim() : getString(R.string.reader_stats_empty_value);
    }

    @NonNull
    private String formatResolution(int width, int height) {
        if (width <= 0 || height <= 0) {
            return getString(R.string.reader_stats_empty_value);
        }
        return width + " x " + height;
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
        final boolean header;
        @NonNull
        final String label;
        @Nullable
        final String value;

        Row(boolean header, @NonNull String label, @Nullable String value) {
            this.header = header;
            this.label = label;
            this.value = value;
        }
    }

    private static final class StatsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private static final int VIEW_TYPE_HEADER = 0;
        private static final int VIEW_TYPE_STAT = 1;

        @NonNull
        private final List<Row> rows;

        StatsAdapter(@NonNull List<Row> rows) {
            this.rows = rows;
        }

        @Override
        public int getItemViewType(int position) {
            return rows.get(position).header ? VIEW_TYPE_HEADER : VIEW_TYPE_STAT;
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
            } else {
                ((StatViewHolder) holder).bind(row.label, row.value);
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
}
