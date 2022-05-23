package com.example.edgedashanalytics.util.nearby;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.edgedashanalytics.R;

import java.util.LinkedHashMap;

public class DeviceListAdapter extends RecyclerView.Adapter<DeviceListAdapter.DeviceViewHolder> {
    private final NearbyFragment.Listener listener;
    private final LinkedHashMap<String, Endpoint> endpoints;
    private final LayoutInflater inflater;

    DeviceListAdapter(NearbyFragment.Listener listener, Context context, LinkedHashMap<String, Endpoint> endpoints) {
        this.listener = listener;
        this.inflater = LayoutInflater.from(context);
        this.endpoints = endpoints;
    }

    @NonNull
    @Override
    public DeviceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View itemView = inflater.inflate(R.layout.device_list_item, parent, false);
        return new DeviceViewHolder(itemView, this);
    }

    @Override
    public void onBindViewHolder(@NonNull DeviceViewHolder holder, int position) {
        Endpoint endpoint = (Endpoint) endpoints.values().toArray()[position];

        holder.deviceName.setText(endpoint.name);
        holder.deviceName.setClickable(!endpoint.connected);
        holder.disconnectButton.setEnabled(endpoint.connected);
        holder.removeButton.setEnabled(!endpoint.connected);

        if (endpoint.connected) {
            holder.connectionStatus.setImageResource(R.drawable.status_connected);
            holder.disconnectButton.clearColorFilter();
            holder.removeButton.setColorFilter(Color.LTGRAY);
        } else {
            holder.connectionStatus.setImageResource(R.drawable.status_disconnected);
            holder.disconnectButton.setColorFilter(Color.LTGRAY);
            holder.removeButton.clearColorFilter();
        }

        holder.deviceName.setOnClickListener(v -> {
            if (!endpoint.connected) {
                Toast.makeText(v.getContext(), String.format("Connecting to %s", endpoint.name), Toast.LENGTH_LONG).show();
                listener.connectEndpoint(endpoint);
            }
        });
        holder.disconnectButton.setOnClickListener(v -> listener.disconnectEndpoint(endpoint));
        holder.removeButton.setOnClickListener(v -> listener.removeEndpoint(endpoint));
    }

    @Override
    public int getItemCount() {
        return endpoints.size();
    }

    static class DeviceViewHolder extends RecyclerView.ViewHolder {
        @SuppressWarnings({"FieldCanBeLocal", "unused"})
        private final DeviceListAdapter adapter;
        private final TextView deviceName;
        private final ImageView connectionStatus;
        private final ImageView disconnectButton;
        private final ImageView removeButton;

        private DeviceViewHolder(View itemView, DeviceListAdapter adapter) {
            super(itemView);
            this.adapter = adapter;
            this.deviceName = itemView.findViewById(R.id.device_item_text);
            this.connectionStatus = itemView.findViewById(R.id.connection_status);
            this.disconnectButton = itemView.findViewById(R.id.disconnect_button);
            this.removeButton = itemView.findViewById(R.id.remove_device_button);
        }
    }
}
