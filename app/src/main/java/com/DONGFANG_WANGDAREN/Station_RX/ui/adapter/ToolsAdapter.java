package com.DONGFANG_WANGDAREN.Station_RX.ui.adapter;


import com.DONGFANG_WANGDAREN.Station_RX.R;
import com.DONGFANG_WANGDAREN.Station_RX.ui.activity.ActivityTools;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public final class ToolsAdapter extends RecyclerView.Adapter<ToolsAdapter.ViewHolder> {

    private final List<ActivityTools.ToolItem> items;

    public ToolsAdapter(@NonNull List<ActivityTools.ToolItem> items) {
        this.items = items;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_tool, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ActivityTools.ToolItem item = items.get(position);
        holder.titleView.setText(item.title);
        holder.itemView.setOnClickListener(view -> item.action.run());
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static final class ViewHolder extends RecyclerView.ViewHolder {

        final TextView titleView;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            titleView = itemView.findViewById(R.id.text_view_tool_title);
        }
    }
}
