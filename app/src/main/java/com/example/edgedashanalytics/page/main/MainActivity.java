package com.example.edgedashanalytics.page.main;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.MediaScannerConnection;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.example.edgedashanalytics.R;
import com.example.edgedashanalytics.data.result.ResultRepository;
import com.example.edgedashanalytics.data.video.ExternalStorageVideosRepository;
import com.example.edgedashanalytics.data.video.ProcessingVideosRepository;
import com.example.edgedashanalytics.data.video.VideosRepository;
import com.example.edgedashanalytics.model.Result;
import com.example.edgedashanalytics.model.Video;
import com.example.edgedashanalytics.util.dashcam.DashCam;
import com.example.edgedashanalytics.util.file.FileManager;
import com.example.edgedashanalytics.util.nearby.Endpoint;
import com.example.edgedashanalytics.util.nearby.NearbyFragment;
import com.example.edgedashanalytics.util.video.eventhandler.ProcessingVideosEventHandler;
import com.example.edgedashanalytics.util.video.eventhandler.RawVideosEventHandler;
import com.example.edgedashanalytics.util.video.eventhandler.ResultEventHandler;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity implements
        VideoFragment.Listener, ResultsFragment.Listener, NearbyFragment.Listener {
    private static final String TAG = MainActivity.class.getSimpleName();

    private VideoFragment rawFragment;
    private VideoFragment processingFragment;
    private ResultsFragment resultsFragment;
    private ConnectionFragment connectionFragment;

    private final FragmentManager supportFragmentManager = getSupportFragmentManager();
    private Fragment activeFragment;

    private final BottomNavigationView.OnItemSelectedListener bottomNavigationOnItemSelectedListener
            = new BottomNavigationView.OnItemSelectedListener() {
        @Override
        public boolean onNavigationItemSelected(@NonNull MenuItem item) {
            int itemId = item.getItemId();

            if (itemId == R.id.navigation_raw) {
                Log.v(TAG, "Navigation raw button clicked");
                showNewFragmentAndHideOldFragment(rawFragment);
                return true;
            } else if (itemId == R.id.navigation_processing) {
                Log.v(TAG, "Navigation processing button clicked");
                showNewFragmentAndHideOldFragment(processingFragment);
                return true;
            } else if (itemId == R.id.navigation_completed) {
                Log.v(TAG, "Navigation completed button clicked");
                showNewFragmentAndHideOldFragment(resultsFragment);
                return true;
            }
            return false;
        }
    };

    private void showNewFragmentAndHideOldFragment(Fragment newFragment) {
        supportFragmentManager.beginTransaction().hide(activeFragment).show(newFragment).commit();
        setActiveFragment(newFragment);
    }

    private void setActiveFragment(Fragment newFragment) {
        activeFragment = newFragment;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        checkPermissions();
        scanVideoDirectories();
        setToolBarAsTheAppBar();
        setUpBottomNavigation();
        setUpFragments();
        FileManager.initialiseDirectories();
    }

    private void checkPermissions() {
        final int REQUEST_PERMISSIONS = 1;
        String[] PERMISSIONS = {
                Manifest.permission.INTERNET,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_NETWORK_STATE,
                Manifest.permission.ACCESS_WIFI_STATE,
                Manifest.permission.CHANGE_WIFI_STATE,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION
        };

        if (hasPermissions()) {
            requestPermissions(PERMISSIONS, REQUEST_PERMISSIONS);
        }
    }

    private boolean hasPermissions(String... permissions) {
        if (permissions != null) {
            for (String permission : permissions) {
                if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                    return false;
                }
            }
        }
        return true;
    }

    private void scanVideoDirectories() {
        MediaScannerConnection.OnScanCompletedListener scanCompletedListener = (path, uri) ->
                Log.d(TAG, String.format("Scanned %s\n  -> uri=%s", path, uri));

        MediaScannerConnection.scanFile(this, new String[]{FileManager.getRawDirPath()},
                null, scanCompletedListener);
    }

    private void setToolBarAsTheAppBar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
    }

    private void setUpBottomNavigation() {
        BottomNavigationView bottomNavigation = findViewById(R.id.navigation);
        bottomNavigation.setOnItemSelectedListener(bottomNavigationOnItemSelectedListener);
    }

    private void setUpFragments() {
        VideosRepository rawRepository = new ExternalStorageVideosRepository(this, FileManager.getRawDirPath());
        VideosRepository processingRepository = new ProcessingVideosRepository();
        ResultRepository resultRepository = new ResultRepository();

        connectionFragment = new ConnectionFragment();
        rawFragment = VideoFragment.newInstance(ActionButton.ADD,
                new RawVideosEventHandler(rawRepository), RawAdapter::new);
        processingFragment = VideoFragment.newInstance(ActionButton.REMOVE,
                new ProcessingVideosEventHandler(processingRepository), ProcessingAdapter::new);
        resultsFragment = ResultsFragment.newInstance(ActionButton.NULL, new ResultEventHandler(resultRepository));

        supportFragmentManager.beginTransaction().add(R.id.main_container, connectionFragment, "4").hide(connectionFragment).commit();
        supportFragmentManager.beginTransaction().add(R.id.main_container, resultsFragment, "3").hide(resultsFragment).commit();
        supportFragmentManager.beginTransaction().add(R.id.main_container, processingFragment, "2").hide(processingFragment).commit();
        supportFragmentManager.beginTransaction().add(R.id.main_container, rawFragment, "1").commit();

        rawFragment.setRepository(rawRepository);
        processingFragment.setRepository(processingRepository);
        resultsFragment.setRepository(resultRepository);

        setActiveFragment(rawFragment);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();

        if (itemId == R.id.action_connect) {
            Log.v(TAG, "Connect button clicked");
            showNewFragmentAndHideOldFragment(connectionFragment);
            return true;
        } else if (itemId == R.id.action_download) {
            Log.v(TAG, "Download button clicked");
            Toast.makeText(this, "Starting download", Toast.LENGTH_SHORT).show();
            DashCam.startDownloadAll(this);
            return true;
        } else if (itemId == R.id.action_clean) {
            Log.v(TAG, "Clean button clicked");
            Toast.makeText(this, "Cleaning video directories", Toast.LENGTH_SHORT).show();
            cleanDirectories();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void cleanDirectories() {
        // TODO add preference to choose removing raw videos
        // rawFragment.cleanRepository(this);

        processingFragment.cleanRepository(this);
        resultsFragment.cleanRepository(this);
        FileManager.cleanDirectories();
    }

    @Override
    public boolean getIsConnected() {
        return isConnected();
    }

    @Override
    public void getAddVideo(Video video) {
        addVideo(video);
    }

    @Override
    public void getNextTransfer() {
        nextTransfer();
    }

    @Override
    public void onListFragmentInteraction(Result result) {
    }

    @Override
    public void connectEndpoint(Endpoint endpoint) {
        connectionFragment.connectEndpoint(endpoint);
    }

    @Override
    public void disconnectEndpoint(Endpoint endpoint) {
        connectionFragment.disconnectEndpoint(endpoint);
    }

    @Override
    public void removeEndpoint(Endpoint endpoint) {
        connectionFragment.removeEndpoint(endpoint);
    }

    @Override
    public boolean isConnected() {
        return connectionFragment.isConnected();
    }

    @Override
    public void addVideo(Video video) {
        connectionFragment.addVideo(video);
    }

    @Override
    public void nextTransfer() {
        connectionFragment.nextTransfer();
    }
}
