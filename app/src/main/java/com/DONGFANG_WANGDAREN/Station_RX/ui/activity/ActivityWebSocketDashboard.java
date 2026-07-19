package com.DONGFANG_WANGDAREN.Station_RX.ui.activity;


import com.DONGFANG_WANGDAREN.Station_RX.R;
import com.DONGFANG_WANGDAREN.Station_RX.app.AppConfig;
import com.DONGFANG_WANGDAREN.Station_RX.app.AppLogger;
import com.DONGFANG_WANGDAREN.Station_RX.rust.RustServerBridge;
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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ActivityWebSocketDashboard extends AppCompatActivity {

    private static final String TAG = "ActivityWebSocketDashboard";
    private static final int DASHBOARD_ITEM_SPACING_DP = 12;

    private static final int ID_CHAT_ROOM = 0;
    private static final int ID_SCAN_LOGIN = 1;
    private static final int ID_RUST_SERVER = 2;

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

        boolean chatRunning = webSocketService != null && webSocketService.isRunning();
        String chatStatus;
        if (chatRunning && webSocketService != null) {
            chatStatus = getString(R.string.dashboard_status_running,
                    webSocketService.getConnectedClientCount(),
                    webSocketService.getTotalMessagesSent());
        } else {
            chatStatus = getString(R.string.dashboard_status_stopped);
        }
        items.add(new ServiceItem(ID_CHAT_ROOM, getString(R.string.dashboard_chat_room), chatStatus, chatRunning));

        boolean scanLoginRunning = chatRunning;
        String scanLoginStatus = scanLoginRunning ? getString(R.string.dashboard_status_running_simple) : getString(R.string.dashboard_status_stopped);
        String httpAddress = webSocketService != null ? webSocketService.getHttpAddress() : null;
        if (scanLoginRunning && httpAddress != null && !httpAddress.isEmpty()) {
            scanLoginStatus = getString(R.string.dashboard_status_running_with_address, httpAddress + "/web-login");
        }
        items.add(new ServiceItem(ID_SCAN_LOGIN, getString(R.string.dashboard_scan_login), scanLoginStatus, scanLoginRunning));

        boolean rustRunning = RustServerBridge.isRunning();
        String rustStatus = rustRunning ? getString(R.string.dashboard_status_running_simple) : getString(R.string.dashboard_status_stopped);
        if (rustRunning) {
            String ip = WebSocketService.getLocalIpAddress();
            if (ip == null || ip.isEmpty()) {
                ip = "127.0.0.1";
            }
            String rustAddress = "http://" + ip + ":" + (AppConfig.get().getHttpPort() + 1000);
            rustStatus = getString(R.string.dashboard_status_running_with_address, rustAddress);
        }
        items.add(new ServiceItem(ID_RUST_SERVER, getString(R.string.dashboard_rust_server), rustStatus, rustRunning));

        adapter.notifyDataSetChanged();
    }

    private void openServiceStats(int position) {
        if (position < 0 || position >= items.size()) {
            return;
        }
        ServiceItem item = items.get(position);
        switch (item.id) {
            case ID_CHAT_ROOM:
                startActivity(new Intent(this, ActivityWebSocketStats.class));
                break;
            case ID_SCAN_LOGIN:
                startActivity(new Intent(this, ActivityTools.class));
                break;
            case ID_RUST_SERVER:
                startActivity(new Intent(this, ActivityRustServer.class));
                break;
        }
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
