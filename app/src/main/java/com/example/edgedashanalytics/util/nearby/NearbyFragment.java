package com.example.edgedashanalytics.util.nearby;

import static com.example.edgedashanalytics.page.main.MainActivity.I_TAG;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.collection.SimpleArrayMap;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;

import com.example.edgedashanalytics.R;
import com.example.edgedashanalytics.event.result.AddResultEvent;
import com.example.edgedashanalytics.event.video.AddEvent;
import com.example.edgedashanalytics.event.video.RemoveByNameEvent;
import com.example.edgedashanalytics.event.video.RemoveEvent;
import com.example.edgedashanalytics.event.video.Type;
import com.example.edgedashanalytics.model.Content;
import com.example.edgedashanalytics.model.Result;
import com.example.edgedashanalytics.model.Video;
import com.example.edgedashanalytics.page.setting.SettingsActivity;
import com.example.edgedashanalytics.util.TimeManager;
import com.example.edgedashanalytics.util.dashcam.DashCam;
import com.example.edgedashanalytics.util.file.FileManager;
import com.example.edgedashanalytics.util.file.JsonManager;
import com.example.edgedashanalytics.util.hardware.HardwareInfo;
import com.example.edgedashanalytics.util.hardware.PowerMonitor;
import com.example.edgedashanalytics.util.nearby.Algorithm.AlgorithmKey;
import com.example.edgedashanalytics.util.nearby.Message.Command;
import com.example.edgedashanalytics.util.video.FfmpegTools;
import com.example.edgedashanalytics.util.video.VideoManager;
import com.example.edgedashanalytics.util.video.analysis.InnerAnalysis;
import com.example.edgedashanalytics.util.video.analysis.OuterAnalysis;
import com.example.edgedashanalytics.util.video.analysis.VideoAnalysis;
import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.connection.AdvertisingOptions;
import com.google.android.gms.nearby.connection.ConnectionInfo;
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback;
import com.google.android.gms.nearby.connection.ConnectionOptions;
import com.google.android.gms.nearby.connection.ConnectionResolution;
import com.google.android.gms.nearby.connection.ConnectionType;
import com.google.android.gms.nearby.connection.ConnectionsClient;
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes;
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo;
import com.google.android.gms.nearby.connection.DiscoveryOptions;
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback;
import com.google.android.gms.nearby.connection.Payload;
import com.google.android.gms.nearby.connection.PayloadCallback;
import com.google.android.gms.nearby.connection.PayloadTransferUpdate;
import com.google.android.gms.nearby.connection.Strategy;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.greenrobot.eventbus.EventBus;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public abstract class NearbyFragment extends Fragment {
    private static final String TAG = NearbyFragment.class.getSimpleName();
    private static final Strategy STRATEGY = Strategy.P2P_STAR;
    private static final String SERVICE_ID = "com.example.edgedashanalytics";
    private static final String LOCAL_NAME_KEY = "LOCAL_NAME";
    private static final String MESSAGE_SEPARATOR = "~";
    private static int transferCount = 0;

    private final ReceiveFilePayloadCallback payloadCallback = new ReceiveFilePayloadCallback();
    private final Queue<Message> transferQueue = new LinkedList<>();
    private final LinkedHashMap<String, Endpoint> discoveredEndpoints = new LinkedHashMap<>();
    private final ExecutorService analysisExecutor = Executors.newSingleThreadExecutor();
    private final List<Future<?>> analysisFutures = new ArrayList<>();
    private final ScheduledExecutorService downloadTaskExecutor = Executors.newSingleThreadScheduledExecutor();
    private final LinkedHashMap<String, Instant> waitTimes = new LinkedHashMap<>();

    private ConnectionsClient connectionsClient;
    protected DeviceListAdapter deviceAdapter;
    protected String localName = null;
    private Listener listener;
    private boolean verbose;
    private boolean master = false;
    private boolean isMasterFastest = false;
    private InnerAnalysis innerAnalysis;
    private OuterAnalysis outerAnalysis;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Activity activity = getActivity();
        if (activity == null) {
            Log.e(TAG, "No activity");
            return;
        }

        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(activity);
        verbose = pref.getBoolean(getString(R.string.verbose_output_key), false);

        deviceAdapter = new DeviceListAdapter(listener, activity, discoveredEndpoints);
        connectionsClient = Nearby.getConnectionsClient(activity);
        setLocalName(activity);

        innerAnalysis = new InnerAnalysis(activity);
        outerAnalysis = new OuterAnalysis(activity);
    }

    @Override
    public void onStop() {
        super.onStop();

        stopDashDownload();
        DashCam.clearDownloads();

        stopAdvertising();
        stopDiscovery();

        payloadCallback.cancelAllPayloads();
        connectionsClient.stopAllEndpoints();
    }

    private void setLocalName(Context context) {
        if (localName != null) {
            return;
        }
        SharedPreferences sharedPrefs = context.getSharedPreferences(LOCAL_NAME_KEY, Context.MODE_PRIVATE);
        String uniqueId = sharedPrefs.getString(LOCAL_NAME_KEY, null);

        if (uniqueId == null) {
            uniqueId = RandomStringUtils.randomAlphanumeric(8);
            SharedPreferences.Editor editor = sharedPrefs.edit();
            editor.putString(LOCAL_NAME_KEY, uniqueId);
            editor.apply();
        }
        localName = String.format("%s [%s]", Build.MODEL, uniqueId);
    }

    private final EndpointDiscoveryCallback endpointDiscoveryCallback =
            new EndpointDiscoveryCallback() {
                @Override
                public void onEndpointFound(@NonNull String endpointId, @NonNull DiscoveredEndpointInfo info) {
                    Log.d(TAG, String.format("Found endpoint %s: %s", endpointId, info.getEndpointName()));

                    if (!discoveredEndpoints.containsKey(endpointId)) {
                        discoveredEndpoints.put(endpointId, new Endpoint(endpointId, info.getEndpointName()));
                        deviceAdapter.notifyItemInserted(discoveredEndpoints.size() - 1);
                    }
                }

                @Override
                public void onEndpointLost(@NonNull String endpointId) {
                    // A previously discovered endpoint has gone away.
                    Log.d(TAG, String.format("Lost endpoint %s", discoveredEndpoints.get(endpointId)));

                    if (discoveredEndpoints.containsKey(endpointId)) {
                        discoveredEndpoints.remove(endpointId);
                        deviceAdapter.notifyDataSetChanged();
                    }
                }
            };

    private final ConnectionLifecycleCallback connectionLifecycleCallback =
            new ConnectionLifecycleCallback() {
                @Override
                public void onConnectionInitiated(@NonNull String endpointId, @NonNull ConnectionInfo connectionInfo) {
                    Log.d(TAG, String.format("Initiated connection with %s: %s",
                            endpointId, connectionInfo.getEndpointName()));

                    if (!discoveredEndpoints.containsKey(endpointId)) {
                        discoveredEndpoints.put(endpointId, new Endpoint(endpointId, connectionInfo.getEndpointName()));
                        deviceAdapter.notifyItemInserted(discoveredEndpoints.size() - 1);
                    }

                    connectionsClient.acceptConnection(endpointId, payloadCallback);
                }

                @Override
                public void onConnectionResult(@NonNull String endpointId, ConnectionResolution result) {
                    switch (result.getStatus().getStatusCode()) {
                        case ConnectionsStatusCodes.STATUS_OK:
                            // We're connected! Can now start sending and receiving data.
                            Endpoint endpoint = discoveredEndpoints.get(endpointId);
                            Log.i(TAG, String.format("Connected to %s", endpoint));

                            if (endpoint != null) {
                                endpoint.connected = true;
                                endpoint.connectionAttempt = 0;
                                requestHardwareInfo(endpointId);
                                deviceAdapter.notifyDataSetChanged();
                            }
                            break;
                        case ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED:
                            // The connection was rejected by one or both sides.
                            Log.i(TAG, String.format("Connection rejected by %s", discoveredEndpoints.get(endpointId)));
                            break;
                        case ConnectionsStatusCodes.STATUS_ERROR:
                            // The connection broke before it was able to be accepted.
                            Log.e(TAG, "Connection error");
                            break;
                        default:
                            // Unknown status code
                            Log.e(TAG, "Unknown status code");
                    }
                }

                @Override
                public void onDisconnected(@NonNull String endpointId) {
                    // We've been disconnected from this endpoint. No more data can be sent or received.
                    Endpoint endpoint = discoveredEndpoints.get(endpointId);

                    if (endpoint == null) {
                        Log.d(TAG, String.format("Disconnected from %s", endpointId));
                        return;
                    }

                    if (endpoint.connected) {
                        Log.d(TAG, String.format("Disconnected from %s", endpoint));
                        endpoint.connected = false;
                        deviceAdapter.notifyDataSetChanged();
                    }

                    if (master && endpoint.connectionAttempt < Endpoint.MAX_CONNECTION_ATTEMPTS) {
                        int delay = 5;
                        ScheduledExecutorService reconnectExecutor = Executors.newSingleThreadScheduledExecutor();
                        ScheduledFuture<?> promise = reconnectExecutor.scheduleWithFixedDelay(reconnect(endpoint),
                                1, delay, TimeUnit.SECONDS);
                        reconnectExecutor.schedule(() -> promise.cancel(false),
                                delay * Endpoint.MAX_CONNECTION_ATTEMPTS, TimeUnit.SECONDS);
                    }
                }

                private Runnable reconnect(Endpoint endpoint) {
                    return () -> {
                        Log.d(TAG, String.format("Attempting reconnection with %s (attempt %s)",
                                endpoint, endpoint.connectionAttempt));
                        endpoint.connectionAttempt++;
                        connectEndpoint(endpoint);
                    };
                }
            };

    protected void startAdvertising(Context context) {
        // Only master should advertise
        master = true;
        PowerMonitor.startPowerMonitor(context);

        AdvertisingOptions advertisingOptions = new AdvertisingOptions.Builder()
                .setStrategy(STRATEGY).setConnectionType(ConnectionType.DISRUPTIVE).build();
        connectionsClient.startAdvertising(localName, SERVICE_ID, connectionLifecycleCallback, advertisingOptions)
                .addOnSuccessListener((Void unused) ->
                        Log.d(TAG, "Started advertising"))
                .addOnFailureListener((Exception e) ->
                        Log.e(TAG, String.format("Advertisement failure: \n%s", e.getMessage())));
    }

    protected void startDiscovery(Context context) {
        PowerMonitor.startPowerMonitor(context);
        SettingsActivity.printPreferences(master, false, context);

        DiscoveryOptions discoveryOptions = new DiscoveryOptions.Builder().setStrategy(STRATEGY).build();
        connectionsClient.startDiscovery(SERVICE_ID, endpointDiscoveryCallback, discoveryOptions)
                .addOnSuccessListener((Void unused) ->
                        Log.d(TAG, "Started discovering"))
                .addOnFailureListener((Exception e) ->
                        Log.e(TAG, String.format("Discovery failure: \n%s", e.getMessage())));
    }

    protected void stopAdvertising() {
        Log.d(TAG, "Stopped advertising");
        connectionsClient.stopAdvertising();
    }

    protected void stopDiscovery() {
        Log.d(TAG, "Stopped discovering");
        connectionsClient.stopDiscovery();
    }

    // https://stackoverflow.com/a/11944965/8031185
    protected void startDashDownload() {
        // Only master (or offline device) should start downloads
        master = true;

        Context context = getContext();
        if (context == null) {
            Log.e(TAG, "No context");
            return;
        }

        String defaultDelay = "1000";
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(context);
        int delay = Integer.parseInt(pref.getString(context.getString(R.string.download_delay_key), defaultDelay));

        SettingsActivity.printPreferences(master, true, context);
        Log.w(I_TAG, "Started downloading from dash cam");
        PowerMonitor.startPowerMonitor(context);

        boolean simDownload = pref.getBoolean(context.getString(R.string.enable_download_simulation_key), false);
        int simDelay = Integer.parseInt(pref.getString(context.getString(R.string.simulation_delay_key), defaultDelay));
        boolean dualDownload = pref.getBoolean(context.getString(R.string.dual_download_key), false);

        Endpoint fastest = getConnectedEndpoints().stream().max(Endpoint.compareProcessing()).orElse(null);
        isMasterFastest = fastest != null &&
                HardwareInfo.compareProcessing(new HardwareInfo(context), fastest.hardwareInfo) > 0;

        if (simDownload) {
            downloadTaskExecutor.scheduleWithFixedDelay(listener.getSimulateDownloads(simDelay,
                    this::downloadCallback, dualDownload), 0, delay, TimeUnit.MILLISECONDS);
        } else {
            DashCam.setDownloadCallback(context, this::downloadCallback);
            downloadTaskExecutor.scheduleWithFixedDelay(DashCam.downloadTestVideos(), 0, delay, TimeUnit.MILLISECONDS);
        }
    }

    protected void stopDashDownload() {
        if (!downloadTaskExecutor.isShutdown()) {
            Log.w(I_TAG, "Stopped downloading from dash cam");
            downloadTaskExecutor.shutdown();
        } else {
            Log.w(TAG, "Dash cam downloads have already stopped");
        }
    }

    private void downloadCallback(Video video) {
        if (video == null) {
            stopDashDownload();
            return;
        }

        if (!isConnected()) {
            analyse(video, false);
            return;
        }

        Context context = getContext();
        if (context == null) {
            Log.e(TAG, "No context");
            return;
        }

        EventBus.getDefault().post(new AddEvent(video, Type.RAW));

        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(context);
        boolean segmentationEnabled = pref.getBoolean(getString(R.string.enable_segment_key), false);
        int segNum = pref.getInt(getString(R.string.segment_number_key), 0);

        if (!segmentationEnabled) {
            addVideo(video);
            nextTransfer();
            return;
        }

        if (segNum < 2 && getConnectedEndpoints().size() > 1) {
            // Dynamic segmentation
            evenSegmentation(video);
        } else {
            // Static segmentation
            splitAndQueue(video.getData(), segNum);
        }
    }

    private void evenSegmentation(Video video) {
        List<Endpoint> endpoints = getConnectedEndpoints();

        if (isMasterFastest) {
            if (video.isOuter()) {
                // Process whole video locally
                Log.d(I_TAG, String.format("Processing %s locally", video.getName()));
                analyse(video, false);
            } else {
                // Split video and send to workers
                List<Video> segments = FfmpegTools.splitAndReturn(getContext(), video.getData(), endpoints.size());
                List<Endpoint> sortedEndpoints = endpoints.stream()
                        .sorted(Endpoint.compareProcessing())
                        .collect(Collectors.toList());

                for (int i = 0; i < segments.size() && i < sortedEndpoints.size(); i++) {
                    sendFile(new Message(segments.get(i), Command.SEGMENT), sortedEndpoints.get(i));
                }
            }
        } else {
            Endpoint fastest = endpoints.stream().max(Endpoint.compareProcessing()).orElse(null);

            if (video.isOuter()) {
                // Send whole video to the fastest worker
                sendFile(new Message(video, Command.ANALYSE), fastest);
            } else {
                // Master will locally process one segment
                List<Video> segments = FfmpegTools.splitAndReturn(getContext(), video.getData(), endpoints.size());
                analyse(segments.remove(0), false);

                // Send remaining segments to non-fastest workers
                List<Endpoint> remainingEndpoints = endpoints.stream()
                        .filter(e -> e != fastest)
                        .sorted(Endpoint.compareProcessing())
                        .collect(Collectors.toList());

                for (int i = 0; i < segments.size() && i < remainingEndpoints.size(); i++) {
                    sendFile(new Message(segments.get(i), Command.SEGMENT), remainingEndpoints.get(i));
                }
            }
        }
    }

    private List<Endpoint> getConnectedEndpoints() {
        return discoveredEndpoints.values().stream().filter(e -> e.connected).collect(Collectors.toList());
    }

    public boolean isConnected() {
        return discoveredEndpoints.values().stream().anyMatch(e -> e.connected);
    }

    public void connectEndpoint(Endpoint endpoint) {
        Log.d(TAG, String.format("Attempting to connect to %s", endpoint));
        if (!endpoint.connected) {
            ConnectionOptions connectionOptions = new ConnectionOptions.Builder()
                    .setConnectionType(ConnectionType.DISRUPTIVE).build();

            connectionsClient.requestConnection(localName, endpoint.id, connectionLifecycleCallback, connectionOptions)
                    .addOnSuccessListener(
                            // We successfully requested a connection. Now both sides
                            // must accept before the connection is established.
                            (Void unused) -> Log.d(TAG, String.format("Requested connection with %s", endpoint)))
                    .addOnFailureListener(
                            // Nearby Connections failed to request the connection.
                            (Exception e) -> Log.e(TAG, String.format("Endpoint failure: \n%s", e.getMessage())));
        } else {
            Log.d(TAG, String.format("'%s' is already connected", endpoint));
        }
    }

    public void disconnectEndpoint(Endpoint endpoint) {
        Log.d(TAG, String.format("Disconnected from '%s'", endpoint));

        connectionsClient.disconnectFromEndpoint(endpoint.id);
        endpoint.connected = false;
        deviceAdapter.notifyDataSetChanged();
    }

    public void removeEndpoint(Endpoint endpoint) {
        Log.d(TAG, String.format("Removed %s", endpoint));

        discoveredEndpoints.remove(endpoint.id);
        deviceAdapter.notifyDataSetChanged();
    }

    @SuppressWarnings("SameParameterValue")
    private void sendCommandMessage(Command command, String filename, String toEndpointId) {
        String commandMessage = String.join(MESSAGE_SEPARATOR, command.toString(), filename);
        Payload filenameBytesPayload = Payload.fromBytes(commandMessage.getBytes(UTF_8));
        connectionsClient.sendPayload(toEndpointId, filenameBytesPayload);
    }

    private void sendHardwareInfo(Context context) {
        HardwareInfo hwi = new HardwareInfo(context);
        String hwiMessage = String.join(MESSAGE_SEPARATOR, Command.HW_INFO.toString(), hwi.toJson());
        Payload messageBytesPayload = Payload.fromBytes(hwiMessage.getBytes(UTF_8));
        Log.d(TAG, String.format("Sending hardware information: \n%s", hwi));

        // Only sent from worker to master, might be better to make bidirectional
        List<String> connectedEndpointIds = discoveredEndpoints.values().stream()
                .filter(e -> e.connected)
                .map(e -> e.id)
                .collect(Collectors.toList());
        connectionsClient.sendPayload(connectedEndpointIds, messageBytesPayload);
    }

    private void requestHardwareInfo(String toEndpoint) {
        Log.d(TAG, String.format("Requesting hardware information from %s", toEndpoint));
        String hwrMessage = String.format("%s%s", Command.HW_INFO_REQUEST, MESSAGE_SEPARATOR);
        Payload messageBytesPayload = Payload.fromBytes(hwrMessage.getBytes(UTF_8));
        connectionsClient.sendPayload(toEndpoint, messageBytesPayload);
    }

    private void queueVideo(Video video, Command command) {
        transferQueue.add(new Message(video, command));
    }

    public void addVideo(Video video) {
        queueVideo(video, Command.ANALYSE);
    }

    private void returnContent(Content content) {
        List<Endpoint> endpoints = getConnectedEndpoints();
        Message message = new Message(content, Command.RETURN);

        if (!master) {
            sendFile(message, endpoints.get(0));
        } else {
            Log.e(TAG, "Non-worker attempting to return a video");
        }
    }

    private void splitAndQueue(String videoPath, int segNum) {
        Context context = getContext();
        if (context == null) {
            Log.e(TAG, "No context");
            return;
        }

        Instant start = Instant.now();
        String baseVideoName = FilenameUtils.getBaseName(videoPath);
        List<Video> videos = FfmpegTools.splitAndReturn(context, videoPath, segNum);

        if (videos == null || videos.size() == 0) {
            Log.e(I_TAG, String.format("Could not split %s", baseVideoName));
            return;
        }

        String time = TimeManager.getDurationString(start);
        Log.i(I_TAG, String.format("Split %s into %s segments in %ss", baseVideoName, segNum, time));

        int vidNum = videos.size();
        if (vidNum != segNum) {
            Log.w(TAG, String.format("Number of segmented videos (%d) does not match intended value (%d)",
                    vidNum, segNum));
        }

        if (vidNum == 1) {
            queueVideo(videos.get(0), Command.ANALYSE);
            return;
        }

        for (Video segment : videos) {
            queueVideo(segment, Command.SEGMENT);
        }
    }

    private void handleSegment(String resultName) {
        String segmentName = FileManager.getVideoNameFromResultName(resultName);
        String baseName = FfmpegTools.getBaseName(resultName);
        String parentName = String.format("%s.%s", baseName, FilenameUtils.getExtension(resultName));
        String videoName = FileManager.getVideoNameFromResultName(parentName);

        TimeManager.printTurnaroundTime(videoName, segmentName);

        List<Result> results = FileManager.getResultsFromDir(FileManager.getSegmentResSubDirPath(resultName));
        int resultTotal = FfmpegTools.getSegmentCount(resultName);

        if (results == null) {
            Log.d(TAG, "Couldn't retrieve results");
            return;
        }

        if (results.size() == resultTotal) {
            Log.d(TAG, String.format("Received all result segments of %s", baseName));
            Result result = JsonManager.mergeResults(parentName);

            EventBus.getDefault().post(new AddResultEvent(result));
            EventBus.getDefault().post(new RemoveByNameEvent(videoName, Type.RAW));
            EventBus.getDefault().post(new RemoveByNameEvent(videoName, Type.PROCESSING));

            // TimeManager.printTurnaroundTime(videoName);
            PowerMonitor.printSummary();
            PowerMonitor.printBatteryLevel(getContext());
        } else {
            Log.v(TAG, String.format("Received a segment of %s", baseName));
        }
    }

    public void nextTransfer() {
        if (transferQueue.isEmpty()) {
            Log.i(TAG, "Transfer queue is empty");
            return;
        }

        Context context = getContext();
        if (context == null) {
            Log.e(TAG, "No context");
            return;
        }

        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(context);
        String algorithmKey = getString(R.string.scheduling_algorithm_key);
        String localProcessKey = getString(R.string.local_process_key);

        AlgorithmKey algorithm = AlgorithmKey.valueOf(pref.getString(algorithmKey, Algorithm.DEFAULT_ALGORITHM.name()));
        boolean localProcess = pref.getBoolean(localProcessKey, false);
        Log.v(TAG, String.format("nextTransfer with selected algorithm: %s", algorithm.name()));

        List<Endpoint> endpoints = getConnectedEndpoints();
        boolean localFree = analysisFutures.stream().allMatch(Future::isDone);
        boolean anyFreeEndpoint = endpoints.stream().anyMatch(Endpoint::isInactive);

        if (localProcess && endpoints.size() == 1 && algorithm.equals(AlgorithmKey.max_capacity)) {
            Message message = transferQueue.remove();
            Video video = (Video) message.content;
            boolean isOuter = video.isOuter();

            if ((isMasterFastest && isOuter) || (!isMasterFastest && !isOuter)) {
                Log.d(I_TAG, String.format("Processing %s locally", video.getName()));
                analyse(video, false);
            } else {
                sendFile(message, endpoints.get(0));
            }
            return;
        }

        if (localProcess && localFree && (isMasterFastest || !anyFreeEndpoint)) {
            Video video = (Video) transferQueue.remove().content;
            Log.d(I_TAG, String.format("Processing %s locally", video.getName()));
            analyse(video, false);
            return;
        }

        Endpoint selected = null;
        switch (algorithm) {
            case round_robin:
                selected = Algorithm.getRoundRobinEndpoint(endpoints, transferCount);
                break;
            case fastest:
                selected = Algorithm.getFastestEndpoint(endpoints);
                break;
            case least_busy:
                selected = Algorithm.getLeastBusyEndpoint(endpoints);
                break;
            case fastest_cpu:
                selected = Algorithm.getFastestCpuEndpoint(endpoints);
                break;
            case most_cpu_cores:
                selected = Algorithm.getMaxCpuCoreEndpoint(endpoints);
                break;
            case most_ram:
                selected = Algorithm.getMaxRamEndpoint(endpoints);
                break;
            case most_storage:
                selected = Algorithm.getMaxStorageEndpoint(endpoints);
                break;
            case highest_battery:
                selected = Algorithm.getMaxBatteryEndpoint(endpoints);
                break;
            case max_capacity:
                selected = Algorithm.getMaxCapacityEndpoint(endpoints);
                break;
        }

        sendFile(transferQueue.remove(), selected);
    }

    private void sendFile(Message message, Endpoint toEndpoint) {
        if (message == null || toEndpoint == null) {
            Log.e(I_TAG, "No message or endpoint selected");
            return;
        }

        File fileToSend = new File(message.content.getData());
        Uri uri = Uri.fromFile(fileToSend);
        Payload filePayload = null;
        Context context = getContext();

        if (context == null) {
            Log.e(TAG, "No context");
            return;
        }

        try {
            ParcelFileDescriptor pfd = context.getContentResolver().openFileDescriptor(uri, "r");
            if (pfd != null) {
                filePayload = Payload.fromFile(pfd);
            }
        } catch (FileNotFoundException e) {
            Log.e(TAG, String.format("sendFile ParcelFileDescriptor error: \n%s", e.getMessage()));
        }

        if (filePayload == null) {
            Log.e(I_TAG, String.format("Could not create file payload for %s", message.content));
            return;
        }
        Log.i(I_TAG, String.format("Sending %s to %s", message.content.getName(), toEndpoint.name));

        // Construct a message mapping the ID of the file payload to the desired filename and command.
        String bytesMessage = String.join(MESSAGE_SEPARATOR, message.command.toString(),
                Long.toString(filePayload.getId()), uri.getLastPathSegment());

        // Send the filename message as a bytes payload.
        // Master will send to all workers, workers will just send to master
        Payload filenameBytesPayload = Payload.fromBytes(bytesMessage.getBytes(UTF_8));
        connectionsClient.sendPayload(toEndpoint.id, filenameBytesPayload);

        // Finally, send the file payload.
        connectionsClient.sendPayload(toEndpoint.id, filePayload);
        toEndpoint.addJob(uri.getLastPathSegment());
        transferCount++;
    }

    private void analyse(File videoFile) {
        analyse(VideoManager.getVideoFromPath(getContext(), videoFile.getAbsolutePath()), true);
    }

    private void analyse(Video video, boolean returnResult) {
        if (video == null) {
            Log.e(TAG, "No video");
            return;
        }

        if (master) {
            waitTimes.put(video.getName(), Instant.now());
        }

        Log.d(TAG, String.format("Analysing %s", video.getName()));

        EventBus.getDefault().post(new AddEvent(video, Type.PROCESSING));
        EventBus.getDefault().post(new RemoveEvent(video, Type.RAW));

        String outPath = FileManager.getResultPathOrSegmentResPathFromVideoName(video.getName());
        Future<?> future = analysisExecutor.submit(analysisRunnable(video, outPath, returnResult));
        analysisFutures.add(future);
    }

    private Runnable analysisRunnable(Video video, String outPath, boolean returnResult) {
        return () -> {
            String videoName = video.getName();

            if (waitTimes.containsKey(videoName)) {
                Instant start = waitTimes.remove(videoName);
                String time = TimeManager.getDurationString(start, false);

                Log.i(I_TAG, String.format("Wait time of %s: %ss", videoName, time));
            } else {
                Log.e(TAG, String.format("Could not record wait time of %s", videoName));
            }

            VideoAnalysis videoAnalysis = video.isInner() ? innerAnalysis : outerAnalysis;
            videoAnalysis.analyse(video.getData(), outPath);

            Result result = new Result(outPath);
            EventBus.getDefault().post(new RemoveEvent(video, Type.PROCESSING));

            if (!FfmpegTools.isSegment(result.getName())) {
                EventBus.getDefault().post(new AddResultEvent(result));

                if (master) {
                    long turnaround = Duration.between(TimeManager.getStartTime(videoName), Instant.now()).toMillis();

                    if (turnaround > FfmpegTools.getDurationMillis()) {
                        videoAnalysis.increaseEsd();
                    }

                    TimeManager.printTurnaroundTime(videoName);
                }
            }

            if (returnResult) {
                returnContent(result);
            } else if (FfmpegTools.isSegment(result.getName())) {
                // Master completed analysing a segment
                handleSegment(result.getName());
                nextTransfer();
            }

            PowerMonitor.printBatteryLevel(getContext());
        };
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);

        if (context instanceof Listener) {
            listener = (Listener) context;
        } else {
            throw new RuntimeException(context + " must implement NearbyFragment.Listener");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        listener = null;
    }

    public interface Listener {
        void connectEndpoint(Endpoint endpoint);

        void disconnectEndpoint(Endpoint endpoint);

        void removeEndpoint(Endpoint endpoint);

        boolean isConnected();

        void addVideo(Video video);

        void nextTransfer();

        Runnable getSimulateDownloads(int delay, Consumer<Video> downloadCallback, boolean dualDownload);
    }

    private class ReceiveFilePayloadCallback extends PayloadCallback {
        private final SimpleArrayMap<Long, Payload> incomingFilePayloads = new SimpleArrayMap<>();
        private final SimpleArrayMap<Long, Payload> completedFilePayloads = new SimpleArrayMap<>();
        private final SimpleArrayMap<Long, String> filePayloadFilenames = new SimpleArrayMap<>();
        private final SimpleArrayMap<Long, Command> filePayloadCommands = new SimpleArrayMap<>();
        private final SimpleArrayMap<Long, Instant> startTimes = new SimpleArrayMap<>();
        private final SimpleArrayMap<Long, Long> startPowers = new SimpleArrayMap<>();

        private Instant lastUpdate = Instant.now();
        private static final int updateInterval = 10;

        @Override
        public void onPayloadReceived(@NonNull String endpointId, @NonNull Payload payload) {
            Log.d(TAG, String.format("onPayloadReceived(endpointId=%s, payload=%s)", endpointId, payload));

            if (payload.getType() == Payload.Type.BYTES) {
                String message = new String(Objects.requireNonNull(payload.asBytes()), UTF_8);
                String[] parts = message.split(MESSAGE_SEPARATOR);
                long payloadId;
                String videoName;
                String videoPath;
                Video video;

                Endpoint fromEndpoint = discoveredEndpoints.get(endpointId);
                if (fromEndpoint == null) {
                    Log.e(TAG, String.format("Failed to retrieve endpoint %s", endpointId));
                    return;
                }

                Context context = getContext();
                if (context == null) {
                    Log.e(TAG, "No context");
                    return;
                }

                switch (Command.valueOf(parts[0])) {
                    case ERROR:
                        //
                        break;
                    case ANALYSE:
                    case SEGMENT:
                        Log.v(TAG, String.format("Started downloading %s from %s", message, fromEndpoint));
                        PowerMonitor.startPowerMonitor(context);
                        payloadId = addPayloadFilename(parts);

                        startTimes.put(payloadId, Instant.now());
                        startPowers.put(payloadId, PowerMonitor.getTotalPowerConsumption());

                        processFilePayload(payloadId, endpointId);
                        break;
                    case RETURN:
                        videoName = parts[2];
                        Log.v(TAG, String.format("Started downloading %s from %s", videoName, fromEndpoint));
                        payloadId = addPayloadFilename(parts);

                        startTimes.put(payloadId, Instant.now());
                        startPowers.put(payloadId, PowerMonitor.getTotalPowerConsumption());

                        fromEndpoint.removeJob(FileManager.getVideoNameFromResultName(videoName));
                        fromEndpoint.completeCount++;

                        processFilePayload(payloadId, endpointId);
                        PowerMonitor.printSummary();
                        PowerMonitor.printBatteryLevel(getContext());
                        break;
                    case COMPLETE:
                        // requestHardwareInfo(endpointId);
                        videoName = parts[1];
                        Log.d(TAG, String.format("%s has finished downloading %s", fromEndpoint, videoName));

                        if (!FfmpegTools.isSegment(videoName)) {
                            videoPath = String.format("%s/%s", FileManager.getRawDirPath(), videoName);
                            video = VideoManager.getVideoFromPath(context, videoPath);

                            EventBus.getDefault().post(new AddEvent(video, Type.PROCESSING));
                            EventBus.getDefault().post(new RemoveEvent(video, Type.RAW));
                        }
                        nextTransfer();

                        break;
                    case HW_INFO:
                        HardwareInfo hwi = HardwareInfo.fromJson(parts[1]);
                        Log.i(TAG, String.format("Received hardware information from %s: \n%s", fromEndpoint, hwi));
                        fromEndpoint.hardwareInfo = hwi;
                        break;
                    case HW_INFO_REQUEST:
                        sendHardwareInfo(context);
                        break;
                }
            } else if (payload.getType() == Payload.Type.FILE) {
                // Add this to our tracking map, so that we can retrieve the payload later.
                incomingFilePayloads.put(payload.getId(), payload);
            }
        }

        /**
         * Extracts the payloadId and filename from the message and stores it in the
         * filePayloadFilenames map. The format is command:payloadId:filename
         * where ":" is MESSAGE_SEPARATOR.
         */
        private long addPayloadFilename(String[] message) {
            Command command = Command.valueOf(message[0]);
            long payloadId = Long.parseLong(message[1]);
            String filename = message[2];
            filePayloadFilenames.put(payloadId, filename);
            filePayloadCommands.put(payloadId, command);

            return payloadId;
        }

        private void processFilePayload(long payloadId, String fromEndpointId) {
            // BYTES and FILE could be received in any order, so we call when either the BYTES or the FILE
            // payload is completely received. The file payload is considered complete only when both have
            // been received.
            Payload filePayload = completedFilePayloads.get(payloadId);
            String filename = filePayloadFilenames.get(payloadId);
            Command command = filePayloadCommands.get(payloadId);

            if (filePayload != null && filename != null && command != null) {
                String time;
                long power;

                if (startTimes.containsKey(payloadId)) {
                    Instant start = startTimes.remove(payloadId);
                    time = TimeManager.getDurationString(start);
                } else {
                    Log.e(I_TAG, String.format("Could not record transfer time of %s", filename));
                    time = "0.000";
                }

                if (startPowers.containsKey(payloadId)) {
                    power = PowerMonitor.getPowerConsumption(startPowers.remove(payloadId));
                } else {
                    Log.e(TAG, String.format("Could not record transfer power consumption of %s", filename));
                    power = 0;
                }

                Log.i(I_TAG, String.format("Completed downloading %s from %s in %ss, %dnW consumed",
                        filename, discoveredEndpoints.get(fromEndpointId), time, power));

                completedFilePayloads.remove(payloadId);
                filePayloadFilenames.remove(payloadId);
                filePayloadCommands.remove(payloadId);

                if (Message.isAnalyse(command)) {
                    sendCommandMessage(Command.COMPLETE, filename, fromEndpointId);
                }

                // Get the received file (which will be in the Downloads folder)
                Payload.File payload = filePayload.asFile();
                if (payload == null) {
                    Log.e(I_TAG, String.format("Could not create file payload for %s", filename));
                    return;
                }

                //noinspection deprecation
                File payloadFile = payload.asJavaFile();
                if (payloadFile == null) {
                    Log.e(I_TAG, String.format("Could not create file payload for %s", filename));
                    return;
                }

                if (Message.isAnalyse(command)) {
                    waitTimes.put(filename, Instant.now());

                    // Video needs to be in a video directory for it to be scanned on Android version 28 and below
                    File videoDest = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ?
                            new File(payloadFile.getParentFile(), filename) :
                            new File(FileManager.getRawDirPath(), filename);

                    boolean rename = payloadFile.renameTo(videoDest);
                    if (!rename) {
                        Log.e(I_TAG, String.format("Could not rename %s", filename));
                        return;
                    }

                    MediaScannerConnection.scanFile(getContext(), new String[]{videoDest.getAbsolutePath()}, null,
                            (path, uri) -> analyse(videoDest));

                } else if (command.equals(Command.RETURN)) {
                    String resultName = FileManager.getResultNameFromVideoName(filename);
                    String resultsDestPath = FileManager.getResultPathOrSegmentResPathFromVideoName(resultName);
                    File resultsDest = new File(resultsDestPath);

                    try {
                        Files.move(payloadFile.toPath(), resultsDest.toPath(), REPLACE_EXISTING);
                    } catch (IOException e) {
                        Log.e(I_TAG, String.format("processFilePayload copy error: \n%s", e.getMessage()));
                        return;
                    }

                    if (FfmpegTools.isSegment(resultName)) {
                        handleSegment(resultName);
                        return;
                    }

                    Result result = new Result(resultsDestPath);
                    EventBus.getDefault().post(new AddResultEvent(result));
                    String videoName = FileManager.getVideoNameFromResultName(filename);
                    EventBus.getDefault().post(new RemoveByNameEvent(videoName, Type.PROCESSING));

                    TimeManager.printTurnaroundTime(videoName);
                }
            }
        }

        @Override
        public void onPayloadTransferUpdate(@NonNull String endpointId, @NonNull PayloadTransferUpdate update) {
            if (verbose) {
                Instant now = Instant.now();

                if (Duration.between(lastUpdate, now).compareTo(Duration.ofSeconds(updateInterval)) > 0) {
                    lastUpdate = now;
                    int progress = (int) (100.0 * (update.getBytesTransferred() / (double) update.getTotalBytes()));
                    Log.v(TAG, String.format("Transfer to %s: %d%%", endpointId, progress));
                }
            }

            if (update.getStatus() == PayloadTransferUpdate.Status.SUCCESS) {
                Log.v(TAG, String.format("Transfer to %s complete", discoveredEndpoints.get(endpointId)));

                long payloadId = update.getPayloadId();
                Payload payload = incomingFilePayloads.remove(payloadId);
                completedFilePayloads.put(payloadId, payload);

                if (payload != null && payload.getType() == Payload.Type.FILE) {
                    processFilePayload(payloadId, endpointId);
                }
            }
        }

        private void cancelAllPayloads() {
            Log.v(TAG, "Cancelling all payloads");

            for (int i = 0; i < incomingFilePayloads.size(); i++) {
                Long payloadId = incomingFilePayloads.keyAt(i);
                connectionsClient.cancelPayload(payloadId);
            }
        }
    }
}
