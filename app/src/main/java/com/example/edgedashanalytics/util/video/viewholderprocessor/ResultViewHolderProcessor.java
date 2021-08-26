package com.example.edgedashanalytics.util.video.viewholderprocessor;

import android.content.Context;
import android.widget.Toast;

import com.example.edgedashanalytics.data.result.ResultViewModel;
import com.example.edgedashanalytics.model.Result;
import com.example.edgedashanalytics.page.main.ResultRecyclerViewAdapter;

public class ResultViewHolderProcessor {
    public void process(Context context, ResultViewModel vm, ResultRecyclerViewAdapter.ResultViewHolder holder, int pos) {
        holder.actionButton.setOnClickListener(view -> {
            // TODO add action, maybe popup results?
            final Result result = holder.result;
            Toast.makeText(context, String.format("Completed analysis of %s", result.getName()), Toast.LENGTH_SHORT).show();
        });
    }
}
