package com.example.edgedashanalytics.page.setting;

import static com.example.edgedashanalytics.page.main.MainActivity.I_TAG;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import com.example.edgedashanalytics.R;
import com.example.edgedashanalytics.util.nearby.Algorithm;
import com.example.edgedashanalytics.util.nearby.Algorithm.AlgorithmKey;
import com.example.edgedashanalytics.util.video.analysis.VideoAnalysis;

import java.util.StringJoiner;

public class SettingsActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.settings_activity);
        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.settings, new SettingsFragment())
                    .commit();
        }
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    public static void printPreferences(boolean autoDown, Context c) {
        StringJoiner prefMessage = new StringJoiner("\n  ");
        prefMessage.add("Preferences:");

        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(c);
        String model = pref.getString(c.getString(R.string.model_key), c.getString(R.string.default_model_key));
        String algorithmKey = c.getString(R.string.scheduling_algorithm_key);
        AlgorithmKey algorithm = AlgorithmKey.valueOf(pref.getString(algorithmKey, Algorithm.DEFAULT_ALGORITHM.name()));
        boolean local = pref.getBoolean(c.getString(R.string.local_process_key), false);
        boolean segmentationEnabled = pref.getBoolean(c.getString(R.string.enable_segment_key), false);
        int segNum = pref.getInt(c.getString(R.string.segment_number_key), -1);

        prefMessage.add(String.format("Model: %s", model));
        prefMessage.add(String.format("Algorithm: %s", algorithm.name()));
        prefMessage.add(String.format("Local processing: %s", local));
        prefMessage.add(String.format("Auto download: %s", autoDown));
        prefMessage.add(String.format("Segmentation: %s", segmentationEnabled));
        prefMessage.add(String.format("Segment number: %s", segNum));

        Log.i(I_TAG, prefMessage.toString());

        VideoAnalysis videoAnalysis = new VideoAnalysis(c);
        videoAnalysis.printParameters();
    }

    public static class SettingsFragment extends PreferenceFragmentCompat {
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey);
        }
    }
}
