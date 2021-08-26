package com.example.edgedashanalytics.page.main;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.edgedashanalytics.R;
import com.example.edgedashanalytics.data.result.ResultViewModel;
import com.example.edgedashanalytics.model.Result;
import com.example.edgedashanalytics.util.video.viewholderprocessor.ResultViewHolderProcessor;

import java.util.List;

public class ResultRecyclerViewAdapter extends RecyclerView.Adapter<ResultRecyclerViewAdapter.ResultViewHolder> {
    private static final String TAG = ResultRecyclerViewAdapter.class.getSimpleName();
    private final ResultsFragment.OnListFragmentInteractionListener listFragmentInteractionListener;
    private final String BUTTON_ACTION_TEXT;

    private final Context context;
    private List<Result> results;
    private final ResultViewHolderProcessor resultViewHolderProcessor;
    private final ResultViewModel viewModel;

    ResultRecyclerViewAdapter(ResultsFragment.OnListFragmentInteractionListener listener,
                              Context context,
                              String buttonText,
                              ResultViewHolderProcessor resultViewHolderProcessor,
                              ResultViewModel viewModel) {
        this.listFragmentInteractionListener = listener;
        this.context = context;
        this.BUTTON_ACTION_TEXT = buttonText;
        this.resultViewHolderProcessor = resultViewHolderProcessor;
        this.viewModel = viewModel;
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

        resultViewHolderProcessor.process(context, viewModel, holder, position);

        holder.view.setOnClickListener(v -> {
            if (null != listFragmentInteractionListener) {
                listFragmentInteractionListener.onListFragmentInteraction(holder.result);
            }
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

    public void setResults(List<Result> results) {
        this.results = results;
        notifyDataSetChanged();
    }

    public static class ResultViewHolder extends RecyclerView.ViewHolder {
        public final View view;
        final TextView resultFileNameView;
        public final Button actionButton;
        public Result result;
        final LinearLayout layout;

        public ResultViewHolder(@NonNull View itemView) {
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
