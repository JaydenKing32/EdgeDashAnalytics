package com.example.edgedashanalytics.page.main;

import android.content.Context;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.edgedashanalytics.R;
import com.example.edgedashanalytics.data.result.ResultRepository;
import com.example.edgedashanalytics.data.result.ResultViewModel;
import com.example.edgedashanalytics.data.result.ResultViewModelFactory;
import com.example.edgedashanalytics.model.Result;
import com.example.edgedashanalytics.page.adapter.ResultRecyclerViewAdapter;
import com.example.edgedashanalytics.util.video.eventhandler.ResultEventHandler;

import org.greenrobot.eventbus.EventBus;

import java.util.ArrayList;
import java.util.List;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link ResultsFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class ResultsFragment extends Fragment {
    private static final String ARG_COLUMN_COUNT = "column-count";

    private int columnCount = 1;
    private Listener listener;
    private ActionButton actionButton;
    private ResultRepository repository;
    private ResultViewModel resultViewModel;
    private ResultEventHandler resultEventHandler;
    private ResultRecyclerViewAdapter adapter;

    /**
     * Mandatory empty constructor for the fragment manager to instantiate the
     * fragment (e.g. upon screen orientation changes).
     */
    public ResultsFragment() {
    }

    public static ResultsFragment newInstance(ActionButton actionButton, ResultEventHandler handler) {
        ResultsFragment fragment = new ResultsFragment();
        Bundle args = new Bundle();
        int columnCount = 1;
        args.putInt(ARG_COLUMN_COUNT, columnCount);
        fragment.setArguments(args);
        fragment.actionButton = actionButton;
        fragment.resultEventHandler = handler;
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getArguments() != null) {
            columnCount = getArguments().getInt(ARG_COLUMN_COUNT);
        }

        FragmentActivity activity = getActivity();
        if (activity == null) {
            return;
        }

        this.resultViewModel = new ViewModelProvider(this,
                new ResultViewModelFactory(activity.getApplication(), repository)).get(ResultViewModel.class);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_result_list, container, false);

        EventBus.getDefault().register(resultEventHandler);

        // Set the adapter
        if (view instanceof RecyclerView) {
            Context context = view.getContext();
            RecyclerView recyclerView = (RecyclerView) view;
            if (columnCount <= 1) {
                recyclerView.setLayoutManager(new LinearLayoutManager(context));
            } else {
                recyclerView.setLayoutManager(new GridLayoutManager(context, columnCount));
            }

            adapter = new ResultRecyclerViewAdapter(listener, actionButton.toString());
            recyclerView.setAdapter(adapter);

            FragmentActivity activity = getActivity();
            if (activity == null) {
                return null;
            }
            resultViewModel.getResults().observe(activity, results -> adapter.setResults(results));
        }
        return view;
    }


    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);

        if (context instanceof Listener) {
            listener = (Listener) context;
        } else {
            throw new RuntimeException(context.toString() + " must implement ResultsFragment.Listener");
        }
    }

    @Override
    public void onDetach() {
        EventBus.getDefault().unregister(resultEventHandler);
        super.onDetach();
        listener = null;
    }

    public void setRepository(ResultRepository repository) {
        this.repository = repository;
    }

    void cleanRepository(Context context) {
        List<Result> results = repository.getResults().getValue();

        if (results != null) {
            int resultsCount = results.size();

            for (int i = 0; i < resultsCount; i++) {
                Result result = results.get(0);
                context.getContentResolver().delete(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        MediaStore.MediaColumns.DATA + "=?", new String[]{result.getData()});
                repository.delete(0);
            }
        }
        adapter.setResults(new ArrayList<>());
    }

    /**
     * This interface must be implemented by activities that contain this
     * fragment to allow an interaction in this fragment to be communicated
     * to the activity and potentially other fragments contained in that
     * activity.
     * <p/>
     * See the Android Training lesson <a href=
     * "http://developer.android.com/training/basics/fragments/communicating.html"
     * >Communicating with Other Fragments</a> for more information.
     */
    public interface Listener {
        void onListFragmentInteraction(Result result);
    }
}
