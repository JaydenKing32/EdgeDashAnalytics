package com.example.edgedashanalytics.page.main;

import android.os.Bundle;

import androidx.fragment.app.Fragment;

import android.text.method.ScrollingMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import com.example.edgedashanalytics.R;
import com.example.edgedashanalytics.util.video.VideoAnalysis;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link VideoAnalysisFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class VideoAnalysisFragment extends Fragment {
    private TextView resultTextView;

    public VideoAnalysisFragment() {
        // Required empty public constructor
    }

    public static VideoAnalysisFragment newInstance() {
        return new VideoAnalysisFragment();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_video_analysis, container, false);

        resultTextView = rootView.findViewById(R.id.analysis_result_text);
        resultTextView.setMovementMethod(new ScrollingMovementMethod());

        Button startButton = rootView.findViewById(R.id.start_analysis_button);
        startButton.setOnClickListener(this::startAnalysis);

        return rootView;
    }

    public void startAnalysis(View view) {
        VideoAnalysis.analyse(view.getContext(), resultTextView);
    }
}
