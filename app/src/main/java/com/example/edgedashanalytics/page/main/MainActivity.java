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
import com.example.edgedashanalytics.data.ExternalStorageVideosRepository;
import com.example.edgedashanalytics.data.ProcessingVideosRepository;
import com.example.edgedashanalytics.data.VideosRepository;
import com.example.edgedashanalytics.model.Video;
import com.example.edgedashanalytics.util.dashcam.DashCam;
import com.example.edgedashanalytics.util.file.FileManager;
import com.example.edgedashanalytics.util.video.videoeventhandler.CompleteVideosEventHandler;
import com.example.edgedashanalytics.util.video.videoeventhandler.ProcessingVideosEventHandler;
import com.example.edgedashanalytics.util.video.videoeventhandler.RawVideosEventHandler;
import com.example.edgedashanalytics.util.video.viewholderprocessor.CompleteVideosViewHolderProcessor;
import com.example.edgedashanalytics.util.video.viewholderprocessor.ProcessingVideosViewHolderProcessor;
import com.example.edgedashanalytics.util.video.viewholderprocessor.RawVideosViewHolderProcessor;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity implements VideoFragment.OnListFragmentInteractionListener {
    private static final String TAG = MainActivity.class.getSimpleName();

    private VideoFragment rawFragment;
    private VideoFragment processingFragment;
    private VideoFragment completeFragment;

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
                showNewFragmentAndHideOldFragment(completeFragment);
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
        VideosRepository completeRepository = new ProcessingVideosRepository();

        rawFragment = VideoFragment.newInstance(new RawVideosViewHolderProcessor(), ActionButton.ADD,
                new RawVideosEventHandler(rawRepository));
        processingFragment = VideoFragment.newInstance(new ProcessingVideosViewHolderProcessor(), ActionButton.REMOVE,
                new ProcessingVideosEventHandler(processingRepository));
        completeFragment = VideoFragment.newInstance(new CompleteVideosViewHolderProcessor(), ActionButton.NULL,
                new CompleteVideosEventHandler(completeRepository));

        supportFragmentManager.beginTransaction().add(R.id.main_container, completeFragment, "3").hide(completeFragment).commit();
        supportFragmentManager.beginTransaction().add(R.id.main_container, processingFragment, "2").hide(processingFragment).commit();
        supportFragmentManager.beginTransaction().add(R.id.main_container, rawFragment, "1").commit();

        rawFragment.setRepository(rawRepository);
        processingFragment.setRepository(processingRepository);
        completeFragment.setRepository(completeRepository);

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

        if (itemId == R.id.action_download) {
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
        completeFragment.cleanRepository(this);
        FileManager.cleanDirectories();
    }

    @Override
    public void onListFragmentInteraction(Video item) {
    }
}
