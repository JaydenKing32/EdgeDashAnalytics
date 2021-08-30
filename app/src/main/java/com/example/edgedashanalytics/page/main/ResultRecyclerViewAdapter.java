package com.example.edgedashanalytics.page.main;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.edgedashanalytics.R;
import com.example.edgedashanalytics.model.Result;

import java.util.List;

public class ResultRecyclerViewAdapter extends RecyclerView.Adapter<ResultRecyclerViewAdapter.ResultViewHolder> {
    private static final String TAG = ResultRecyclerViewAdapter.class.getSimpleName();
    private final ResultsFragment.Listener listener;
    private final String BUTTON_ACTION_TEXT;

    private List<Result> results;

    ResultRecyclerViewAdapter(ResultsFragment.Listener listener, String buttonText) {
        this.listener = listener;
        this.BUTTON_ACTION_TEXT = buttonText;
        setHasStableIds(true);
    }

    @NonNull
    @Override
    public ResultViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.result_list_item, parent, false);
        return new ResultViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ResultViewHolder holder, int position) {
        holder.result = results.get(position);
        holder.resultFileNameView.setText(results.get(position).getName());
        holder.actionButton.setText(BUTTON_ACTION_TEXT);

        holder.actionButton.setOnClickListener(v -> {
            final Result result = holder.result;
            Toast.makeText(v.getContext(), String.format("Completed analysis of %s", result.getName()),
                    Toast.LENGTH_SHORT).show();
//            if (null != listener) {
//                listener.onListFragmentInteraction(holder.result);
//            }
        });
    }

    @Override
    public int getItemCount() {
        return results.size();
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    void setResults(List<Result> results) {
        this.results = results;
        notifyDataSetChanged();
    }

    public static class ResultViewHolder extends RecyclerView.ViewHolder {
        private final View view;
        private final TextView resultFileNameView;
        private final Button actionButton;
        public Result result;
        private final LinearLayout layout;

        private ResultViewHolder(@NonNull View itemView) {
            super(itemView);
            view = itemView;
            resultFileNameView = itemView.findViewById(R.id.result_name);
            actionButton = itemView.findViewById(R.id.result_action_button);
            layout = itemView.findViewById(R.id.result_row);
        }

        @NonNull
        @Override
        public String toString() {
            return super.toString() + " '" + resultFileNameView.getText() + "'";
        }
    }
}
