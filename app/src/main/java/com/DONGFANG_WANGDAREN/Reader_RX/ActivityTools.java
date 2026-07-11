package com.DONGFANG_WANGDAREN.Reader_RX;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;

import java.util.ArrayList;
import java.util.List;

public class ActivityTools extends AppCompatActivity {

    private static final String TAG = "ActivityTools";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tools);

        MaterialToolbar toolbar = findViewById(R.id.toolbar_tools);
        toolbar.setNavigationOnClickListener(view -> finish());

        RecyclerView recyclerView = findViewById(R.id.recycler_view_tools);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        List<ToolItem> items = new ArrayList<>();
        items.add(new ToolItem(getString(R.string.tools_chat_room), this::openChatRoom));

        recyclerView.setAdapter(new ToolsAdapter(items));

        AppLogger.i(TAG, "Tools list opened.");
    }

    private void openChatRoom() {
        AppLogger.i(TAG, "Open chat room.");
        startActivity(new Intent(this, ActivityWebSocket.class));
    }

    static final class ToolItem {

        @NonNull
        final String title;
        @NonNull
        final Runnable action;

        ToolItem(@NonNull String title, @NonNull Runnable action) {
            this.title = title;
            this.action = action;
        }
    }
}
