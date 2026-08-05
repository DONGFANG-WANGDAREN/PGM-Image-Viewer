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
import com.google.android.material.appbar.MaterialToolbar;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ActivityReaderStats extends AppCompatActivity {

    private final List<Row> rows = new ArrayList<>();
    private StatsAdapter adapter;
    @Nullable
    private ScheduledExecutorService refreshExecutor;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_web_socket_stats);

        MaterialToolbar toolbar = findViewById(R.id.toolbar_stats);
        toolbar.setTitle(R.string.dashboard_reader);
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
        ActivityPictureViewer.ReaderDashboardSnapshot snapshot = ActivityPictureViewer.getReaderDashboardSnapshot();

        rows.add(new Row(RowType.HEADER, getString(R.string.reader_stats_header_document)));
        rows.add(new Row(RowType.STAT, getString(R.string.reader_stats_status), snapshot.opened
                ? getString(R.string.reader_stats_opened)
                : getString(R.string.reader_stats_not_opened)));
        rows.add(new Row(RowType.STAT, getString(R.string.reader_stats_file_name),
                snapshot.opened ? snapshot.fileName : getString(R.string.reader_stats_empty_value)));
        rows.add(new Row(RowType.STAT, getString(R.string.reader_stats_file_path),
                snapshot.opened && !snapshot.filePath.isEmpty() ? snapshot.filePath : getString(R.string.reader_stats_empty_value)));
        rows.add(new Row(RowType.STAT, getString(R.string.reader_stats_file_type),
                snapshot.opened ? snapshot.openModeLabel : getString(R.string.reader_stats_empty_value)));
        rows.add(new Row(RowType.STAT, getString(R.string.reader_stats_file_size), formatBytes(snapshot.fileSizeBytes)));

        rows.add(new Row(RowType.HEADER, getString(R.string.reader_stats_header_memory)));
        rows.add(new Row(RowType.STAT, getString(R.string.reader_stats_memory_used), formatBytes(snapshot.usedMemoryBytes)));
        rows.add(new Row(RowType.STAT, getString(R.string.reader_stats_memory_max_heap), formatBytes(snapshot.maxHeapBytes)));
        rows.add(new Row(RowType.STAT, getString(R.string.reader_stats_memory_usage_ratio), formatPercent(snapshot.usedMemoryBytes, snapshot.maxHeapBytes)));

        adapter.notifyDataSetChanged();
    }

    @NonNull
    private String formatPercent(long usedBytes, long totalBytes) {
        if (usedBytes < 0 || totalBytes <= 0) {
            return getString(R.string.stats_unavailable);
        }
        double ratio = usedBytes * 100.0 / totalBytes;
        return String.format(Locale.US, "%.2f%%", ratio);
    }

    @NonNull
    private String formatBytes(long bytes) {
        if (bytes < 0) {
            return getString(R.string.reader_stats_empty_value);
        }
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return String.format(Locale.US, "%.2f KB", bytes / 1024.0);
        }
        if (bytes < 1024L * 1024 * 1024) {
            return String.format(Locale.US, "%.2f MB", bytes / (1024.0 * 1024.0));
        }
        return String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }

    private static final class Row {
        @NonNull
        final RowType type;
        @NonNull
        final String label;
        @Nullable
        final String value;

        Row(@NonNull RowType type, @NonNull String label) {
            this(type, label, null);
        }

        Row(@NonNull RowType type, @NonNull String label, @Nullable String value) {
            this.type = type;
            this.label = label;
            this.value = value;
        }
    }

    private enum RowType {
        HEADER, STAT
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
            return rows.get(position).type == RowType.HEADER ? VIEW_TYPE_HEADER : VIEW_TYPE_STAT;
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
            ((StatViewHolder) holder).bind(row.label, row.value);
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
