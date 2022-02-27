package com.example.edgedashanalytics.page.setting;

import static com.example.edgedashanalytics.page.main.MainActivity.I_TAG;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import com.example.edgedashanalytics.BuildConfig;
import com.example.edgedashanalytics.R;
import com.example.edgedashanalytics.util.file.FileManager;
import com.example.edgedashanalytics.util.nearby.Algorithm;
import com.example.edgedashanalytics.util.nearby.Algorithm.AlgorithmKey;
import com.example.edgedashanalytics.util.video.analysis.InnerAnalysis;
import com.example.edgedashanalytics.util.video.analysis.OuterAnalysis;

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

    private static String getWifiName(Context context) {
        WifiManager manager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);

        if (manager.isWifiEnabled()) {
            WifiInfo wifiInfo = manager.getConnectionInfo();

            if (wifiInfo != null) {
                // SSIDs are surrounded with quotation marks, should remove them
                return wifiInfo.getSSID().replace("\"", "");
            }
        }

        return "offline";
    }

    public static void printPreferences(boolean isMaster, boolean autoDownEnabled, Context c) {
        StringJoiner prefMessage = new StringJoiner("\n  ");
        prefMessage.add("Preferences:");

        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(c);
        String objectModel = pref.getString(c.getString(R.string.object_model_key),
                c.getString(R.string.default_object_model_key));
        String poseModel = pref.getString(c.getString(R.string.pose_model_key),
                c.getString(R.string.default_pose_model_key));
        String algorithmKey = c.getString(R.string.scheduling_algorithm_key);
        AlgorithmKey algorithm = AlgorithmKey.valueOf(pref.getString(algorithmKey, Algorithm.DEFAULT_ALGORITHM.name()));
        boolean local = pref.getBoolean(c.getString(R.string.local_process_key), false);
        boolean segmentationEnabled = pref.getBoolean(c.getString(R.string.enable_segment_key), false);
        int segNum = pref.getInt(c.getString(R.string.segment_number_key), -1);

        prefMessage.add(String.format("Master: %s", isMaster));
        prefMessage.add(String.format("Object detection model: %s", objectModel));
        prefMessage.add(String.format("Pose estimation model: %s", poseModel));
        prefMessage.add(String.format("Algorithm: %s", algorithm.name()));
        prefMessage.add(String.format("Local processing: %s", local));
        prefMessage.add(String.format("Auto download: %s", autoDownEnabled));
        prefMessage.add(String.format("Segmentation: %s", segmentationEnabled));
        prefMessage.add(String.format("Segment number: %s", segNum));
        prefMessage.add(String.format("Wi-Fi: %s", getWifiName(c)));

        Log.i(I_TAG, prefMessage.toString());

        final boolean sleep = false;
        OuterAnalysis outerAnalysis = new OuterAnalysis(c, sleep);
        InnerAnalysis innerAnalysis = new InnerAnalysis(c, sleep);
        outerAnalysis.printParameters();
        innerAnalysis.printParameters();
    }

    public static class SettingsFragment extends PreferenceFragmentCompat {
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey);

            Preference clearLogsButton = findPreference(getString(R.string.clear_logs_key));
            if (clearLogsButton != null) {
                clearLogsButton.setOnPreferenceClickListener(preference -> FileManager.clearLogs());
            }

            Preference versionNumber = findPreference(getString(R.string.version_number_key));
            if (versionNumber != null) {
                versionNumber.setTitle(getString(R.string.version_number_title) + BuildConfig.VERSION_CODE);
            }
        }
    }
}
