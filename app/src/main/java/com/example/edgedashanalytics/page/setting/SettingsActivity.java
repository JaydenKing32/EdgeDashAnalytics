package com.example.edgedashanalytics.page.setting;

import static com.example.edgedashanalytics.page.main.MainActivity.I_TAG;

import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.widget.Button;
import android.widget.LinearLayout;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import com.example.edgedashanalytics.R;
import com.example.edgedashanalytics.util.dashcam.DashCam;
import com.example.edgedashanalytics.util.file.FileManager;
import com.example.edgedashanalytics.util.nearby.Algorithm;
import com.example.edgedashanalytics.util.nearby.Algorithm.AlgorithmKey;
import com.example.edgedashanalytics.util.video.analysis.InnerAnalysis;
import com.example.edgedashanalytics.util.video.analysis.OuterAnalysis;

import java.util.StringJoiner;

public class SettingsActivity extends AppCompatActivity {
    private static final String TAG = SettingsActivity.class.getSimpleName();

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
        final SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(c);
        final boolean defaultBool = false;
        final int defaultInt = 1;
        final String defaultString = "1";

        String objectModel = pref.getString(c.getString(R.string.object_model_key),
                c.getString(R.string.default_object_model_key));
        String poseModel = pref.getString(c.getString(R.string.pose_model_key),
                c.getString(R.string.default_pose_model_key));
        String algorithmKey = c.getString(R.string.scheduling_algorithm_key);
        AlgorithmKey algorithm = AlgorithmKey.valueOf(pref.getString(algorithmKey, Algorithm.DEFAULT_ALGORITHM.name()));
        boolean local = pref.getBoolean(c.getString(R.string.local_process_key), defaultBool);
        boolean segmentationEnabled = pref.getBoolean(c.getString(R.string.enable_segment_key), defaultBool);
        int segNum = pref.getInt(c.getString(R.string.segment_number_key), defaultInt);
        int delay = Integer.parseInt(pref.getString(c.getString(R.string.download_delay_key), defaultString));
        boolean dualDownload = pref.getBoolean(c.getString(R.string.dual_download_key), defaultBool);
        boolean sim = pref.getBoolean(c.getString(R.string.enable_download_simulation_key), defaultBool);
        int simDelay = Integer.parseInt(pref.getString(c.getString(R.string.simulation_delay_key), defaultString));

        StringJoiner prefMessage = new StringJoiner("\n  ");
        prefMessage.add("Preferences:");
        prefMessage.add(String.format("Master: %s", isMaster));
        prefMessage.add(String.format("Object detection model: %s", objectModel));
        prefMessage.add(String.format("Pose estimation model: %s", poseModel));
        prefMessage.add(String.format("Algorithm: %s", algorithm.name()));
        prefMessage.add(String.format("Local processing: %s", local));
        prefMessage.add(String.format("Auto download: %s", autoDownEnabled));
        prefMessage.add(String.format("Segmentation: %s", segmentationEnabled));
        prefMessage.add(String.format("Segment number: %s", segNum));
        prefMessage.add(String.format("Download delay: %s", delay));
        prefMessage.add(String.format("Dual download: %s", dualDownload));
        prefMessage.add(String.format("Wi-Fi: %s", getWifiName(c)));
        prefMessage.add(String.format("Concurrent downloads: %s", DashCam.concurrentDownloads));
        prefMessage.add(String.format("Simulated downloads: %s", sim));
        prefMessage.add(String.format("Simulated delay: %s", simDelay));

        Log.i(I_TAG, prefMessage.toString());

        OuterAnalysis outerAnalysis = new OuterAnalysis(c);
        InnerAnalysis innerAnalysis = new InnerAnalysis(c);
        outerAnalysis.printParameters();
        innerAnalysis.printParameters();
    }

    public static class SettingsFragment extends PreferenceFragmentCompat {
        AlertDialog clearDialog = null;

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey);

            Preference clearLogsButton = findPreference(getString(R.string.clear_logs_key));
            if (clearLogsButton != null) {
                clearLogsButton.setOnPreferenceClickListener(preference -> clearLogsPrompt());
            }

            Context context = getContext();
            if (context == null) {
                Log.w(TAG, "Null context");
                return;
            }

            clearDialog = new AlertDialog.Builder(context)
                    .setTitle("Clear logs?")
                    .setPositiveButton(android.R.string.yes,
                            (DialogInterface dialog, int which) -> FileManager.clearLogs())
                    .setNegativeButton(android.R.string.no,
                            (DialogInterface dialog, int which) -> Log.v(TAG, "Canceled log clearing"))
                    .create();

            // https://stackoverflow.com/a/34556215
            clearDialog.setOnShowListener(dialog -> {
                Button posButton = clearDialog.getButton(DialogInterface.BUTTON_POSITIVE);
                Button negButton = clearDialog.getButton(DialogInterface.BUTTON_NEGATIVE);

                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT, 2f);
                negButton.setLayoutParams(params);
                posButton.setLayoutParams(params);

                negButton.invalidate();
                posButton.invalidate();
            });

            EditTextPreference downloadDelay = findPreference(getString(R.string.download_delay_key));
            setupTextPreference(downloadDelay);
            EditTextPreference simDelay = findPreference(getString(R.string.simulation_delay_key));
            setupTextPreference(simDelay);
        }

        private boolean clearLogsPrompt() {
            if (clearDialog == null) {
                Log.w(TAG, "Null dialog");
                return false;
            } else {
                clearDialog.show();
                return true;
            }
        }

        private void setupTextPreference(EditTextPreference editTextPreference) {
            if (editTextPreference != null) {
                setTextSummaryToValue(editTextPreference, editTextPreference.getText());
                editTextPreference.setOnPreferenceChangeListener((preference, newValue) ->
                        setTextSummaryToValue((EditTextPreference) preference, (String) newValue));
                editTextPreference.setOnBindEditTextListener(editText -> {
                    editText.setInputType(InputType.TYPE_CLASS_NUMBER);
                    editText.selectAll();
                });
            }
        }

        private boolean setTextSummaryToValue(EditTextPreference editTextPreference, String value) {
            if (editTextPreference == null) {
                Log.w(TAG, "Null EditTextPreference");
                return false;
            } else {
                editTextPreference.setSummary(value);
                return true;
            }
        }
    }
}
