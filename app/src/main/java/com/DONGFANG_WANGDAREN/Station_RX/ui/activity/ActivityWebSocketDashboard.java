package com.DONGFANG_WANGDAREN.Station_RX.ui.activity;


import com.DONGFANG_WANGDAREN.Station_RX.R;
import com.DONGFANG_WANGDAREN.Station_RX.app.AppLogger;
import com.DONGFANG_WANGDAREN.Station_RX.websocket.WebSocketService;
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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ActivityWebSocketDashboard extends AppCompatActivity {

    private static final String TAG = "ActivityWebSocketDashboard";

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
        boolean running = webSocketService != null && webSocketService.isRunning();
        String status;
        if (running && webSocketService != null) {
            status = getString(R.string.dashboard_status_running,
                    webSocketService.getConnectedClientCount(),
                    webSocketService.getTotalMessagesSent());
        } else {
            status = getString(R.string.dashboard_status_stopped);
        }
        items.add(new ServiceItem(getString(R.string.dashboard_local_server), status, running));
        adapter.notifyDataSetChanged();
    }

    private void openServiceStats(int position) {
        if (position < 0 || position >= items.size()) {
            return;
        }
        startActivity(new Intent(this, ActivityWebSocketStats.class));
    }

    private static final class ServiceItem {
        @NonNull
        final String name;
        @NonNull
        final String status;
        final boolean running;

        ServiceItem(@NonNull String name, @NonNull String status, boolean running) {
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
}
