package com.example.edgedashanalytics.util.nearby;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
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
import com.example.edgedashanalytics.util.dashcam.DashCam;
import com.example.edgedashanalytics.util.file.FileManager;
import com.example.edgedashanalytics.util.hardware.HardwareInfo;
import com.example.edgedashanalytics.util.nearby.Algorithm.AlgorithmKey;
import com.example.edgedashanalytics.util.nearby.Message.Command;
import com.example.edgedashanalytics.util.video.VideoManager;
import com.example.edgedashanalytics.util.video.analysis.VideoAnalysis;
import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.connection.AdvertisingOptions;
import com.google.android.gms.nearby.connection.ConnectionInfo;
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback;
import com.google.android.gms.nearby.connection.ConnectionResolution;
import com.google.android.gms.nearby.connection.ConnectionsClient;
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes;
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo;
import com.google.android.gms.nearby.connection.DiscoveryOptions;
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback;
import com.google.android.gms.nearby.connection.Payload;
import com.google.android.gms.nearby.connection.PayloadCallback;
import com.google.android.gms.nearby.connection.PayloadTransferUpdate;
import com.google.android.gms.nearby.connection.Strategy;
import com.google.gson.Gson;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.time.DurationFormatUtils;
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
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public abstract class NearbyFragment extends Fragment {
    private static final String TAG = NearbyFragment.class.getSimpleName();
    private static final Strategy STRATEGY = Strategy.P2P_STAR;
    private static final String SERVICE_ID = "com.example.edgesum";
    private static final String LOCAL_NAME_KEY = "LOCAL_NAME";
    private static final String MESSAGE_SEPARATOR = "~";
    private static int transferCount = 0;

    private final PayloadCallback payloadCallback = new ReceiveFilePayloadCallback();
    private final Queue<Message> transferQueue = new LinkedList<>();
    private final LinkedHashMap<String, Endpoint> discoveredEndpoints = new LinkedHashMap<>();
    private final ExecutorService analysisExecutor = Executors.newSingleThreadExecutor();
    private final List<Future<?>> analysisFutures = new ArrayList<>();
    private final ScheduledExecutorService downloadTaskExecutor = Executors.newSingleThreadScheduledExecutor();
    private final Gson gson = new Gson();

    private ConnectionsClient connectionsClient;
    protected DeviceListAdapter deviceAdapter;
    protected String localName = null;
    private Listener listener;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
//        discoveredEndpoints.put("testing1", new Endpoint("testing1", "testing1"));
//        discoveredEndpoints.put("testing2", new Endpoint("testing2", "testing2"));
        deviceAdapter = new DeviceListAdapter(listener, getContext(), discoveredEndpoints);

        Context context = getContext();
        if (context != null) {
            connectionsClient = Nearby.getConnectionsClient(context);
            setLocalName(context);
        }
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

                    Context context = getContext();
                    if (context != null) {
                        new AlertDialog.Builder(context)
                                .setTitle("Accept connection to " + connectionInfo.getEndpointName())
                                .setMessage("Confirm the code matches on both devices: " + connectionInfo.getAuthenticationDigits())
                                .setPositiveButton(android.R.string.ok,
                                        (DialogInterface dialog, int which) ->
                                                // The user confirmed, so we can accept the connection.
                                                connectionsClient.acceptConnection(endpointId, payloadCallback))
                                .setNegativeButton(android.R.string.cancel,
                                        (DialogInterface dialog, int which) ->
                                                // The user canceled, so we should reject the connection.
                                                connectionsClient.rejectConnection(endpointId))
                                .setIcon(android.R.drawable.ic_dialog_alert)
                                .show();
                    }
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
                    }
                }

                @Override
                public void onDisconnected(@NonNull String endpointId) {
                    // We've been disconnected from this endpoint. No more data can be sent or received.
                    Endpoint endpoint = discoveredEndpoints.get(endpointId);
                    Log.d(TAG, String.format("Disconnected from %s", endpoint));

                    if (endpoint != null) {
                        endpoint.connected = false;
                        deviceAdapter.notifyDataSetChanged();
                    }
                }
            };

    protected void startAdvertising() {
        AdvertisingOptions advertisingOptions = new AdvertisingOptions.Builder().setStrategy(STRATEGY).build();
        connectionsClient.startAdvertising(localName, SERVICE_ID, connectionLifecycleCallback, advertisingOptions)
                .addOnSuccessListener((Void unused) ->
                        Log.d(TAG, "Started advertising"))
                .addOnFailureListener((Exception e) ->
                        Log.e(TAG, String.format("Advertisement failure: \n%s", e.getMessage())));
    }

    protected void startDiscovery() {
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
        Context context = getContext();
        if (context == null) {
            Log.e(TAG, "No context");
            return;
        }
        int defaultDelay = 1;
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(context);
        int delay = pref.getInt(getString(R.string.download_delay_key), defaultDelay);

        Log.i(String.format("!%s", TAG), String.format("Download delay: %ds", delay));
        Log.i(String.format("!%s", TAG), "Started downloading from dashcam");

        downloadTaskExecutor.scheduleWithFixedDelay(DashCam.downloadTestVideos(this::downloadCallback, context),
                0, delay, TimeUnit.SECONDS);
    }

    protected void stopDashDownload() {
        Log.i(String.format("!%s", TAG), "Stopped downloading from dashcam");
        downloadTaskExecutor.shutdown();
    }

    private void downloadCallback(Video video) {
        if (video == null) {
            stopDashDownload();
            return;
        }
        if (isConnected()) {
            EventBus.getDefault().post(new AddEvent(video, Type.RAW));
            addVideo(video);
            nextTransfer();
        } else {
            analyse(video, true);
        }
    }

    private List<Endpoint> getConnectedEndpoints() {
        return discoveredEndpoints.values().stream().filter(e -> e.connected).collect(Collectors.toList());
    }

    public boolean isConnected() {
        return discoveredEndpoints.values().stream().anyMatch(e -> e.connected);
    }

    public void connectEndpoint(Endpoint endpoint) {
        Log.d(TAG, String.format("Selected '%s'", endpoint));
        if (!endpoint.connected) {
            connectionsClient.requestConnection(localName, endpoint.id, connectionLifecycleCallback)
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

    void sendHardwareInfo(Context context) {
        HardwareInfo hwi = new HardwareInfo(context);
        String hwiMessage = String.join(MESSAGE_SEPARATOR, Command.HW_INFO.toString(), gson.toJson(hwi));
        Payload messageBytesPayload = Payload.fromBytes(hwiMessage.getBytes(UTF_8));
        Log.d(TAG, String.format("Sending hardware information: \n%s", hwi));

        // Only sent from worker to master, might be better to make bidirectional
        List<String> connectedEndpointIds = discoveredEndpoints.values().stream()
                .filter(e -> e.connected)
                .map(e -> e.id)
                .collect(Collectors.toList());
        connectionsClient.sendPayload(connectedEndpointIds, messageBytesPayload);
    }

    void requestHardwareInfo(String toEndpoint) {
        Log.d(TAG, String.format("Requesting hardware information from %s", toEndpoint));
        String hwrMessage = String.format("%s%s", Command.HW_INFO_REQUEST, MESSAGE_SEPARATOR);
        Payload messageBytesPayload = Payload.fromBytes(hwrMessage.getBytes(UTF_8));
        connectionsClient.sendPayload(toEndpoint, messageBytesPayload);
    }

    public void addVideo(Video video) {
        transferQueue.add(new Message(video, Command.ANALYSE));
    }

    private void returnContent(Content content) {
        List<Endpoint> endpoints = getConnectedEndpoints();
        Message message = new Message(content, Command.RETURN);

        // Workers should only have a single connection to the master endpoint
        if (endpoints.size() == 1) {
            sendFile(message, endpoints.get(0));
        } else {
            Log.e(TAG, "Non-worker attempting to return a video");
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

        if (localProcess && localFree && !anyFreeEndpoint) {
            Video video = (Video) transferQueue.remove().content;
            Log.d(String.format("!%s", TAG), String.format("Processing %s locally", video.getName()));
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
        }

        sendFile(transferQueue.remove(), selected);
        transferCount++;
    }

    private void sendFile(Message message, Endpoint toEndpoint) {
        if (message == null || toEndpoint == null) {
            Log.e(TAG, "No message or endpoint selected");
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
            Log.e(TAG, String.format("Could not create file payload for %s", message.content));
            return;
        }
        Log.i(String.format("!%s", TAG), String.format("Sending %s to %s", message.content.getName(), toEndpoint.name));

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
    }

    private void analyse(File videoFile) {
        analyse(VideoManager.getVideoFromPath(getContext(), videoFile.getAbsolutePath()), true);
    }

    private void analyse(Video video, boolean returnResult) {
        Context context = getContext();
        if (context == null) {
            Log.e(TAG, "No context");
            return;
        }

        Log.d(TAG, String.format("Analysing %s", video.getName()));

        EventBus.getDefault().post(new AddEvent(video, Type.PROCESSING));
        EventBus.getDefault().post(new RemoveEvent(video, Type.RAW));

        String outPath = FileManager.getResultPathFromVideoName(video.getName());
        Future<?> future = analysisExecutor.submit(analysisRunnable(video, outPath, context, returnResult));
        analysisFutures.add(future);
    }

    private Runnable analysisRunnable(Video video, String outPath, Context context, boolean returnResult) {
        return () -> {
            VideoAnalysis videoAnalysis = new VideoAnalysis(video.getData(), outPath);
            videoAnalysis.analyse(context);

            Result result = new Result(outPath, FileManager.getFilenameFromPath(outPath));
            EventBus.getDefault().post(new AddResultEvent(result));
            EventBus.getDefault().post(new RemoveEvent(video, Type.PROCESSING));

            if (returnResult) {
                returnContent(result);
            }
        };
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);

        if (context instanceof Listener) {
            listener = (Listener) context;
        } else {
            throw new RuntimeException(context.toString() + " must implement NearbyFragment.Listener");
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
    }

    private class ReceiveFilePayloadCallback extends PayloadCallback {
        private final SimpleArrayMap<Long, Payload> incomingFilePayloads = new SimpleArrayMap<>();
        private final SimpleArrayMap<Long, Payload> completedFilePayloads = new SimpleArrayMap<>();
        private final SimpleArrayMap<Long, String> filePayloadFilenames = new SimpleArrayMap<>();
        private final SimpleArrayMap<Long, Command> filePayloadCommands = new SimpleArrayMap<>();
        private final SimpleArrayMap<Long, Instant> startTimes = new SimpleArrayMap<>();

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
                        Log.v(TAG, String.format("Started downloading %s from %s", message, fromEndpoint));
                        payloadId = addPayloadFilename(parts);
                        startTimes.put(payloadId, Instant.now());

                        processFilePayload(payloadId, endpointId);
                        break;
                    case RETURN:
                        videoName = parts[2];
                        Log.v(TAG, String.format("Started downloading %s from %s", videoName, fromEndpoint));
                        payloadId = addPayloadFilename(parts);
                        startTimes.put(payloadId, Instant.now());
                        fromEndpoint.removeJob(FileManager.getVideoNameFromResultName(videoName));
                        fromEndpoint.completeCount++;

                        processFilePayload(payloadId, endpointId);
                        break;
                    case COMPLETE:
                        requestHardwareInfo(endpointId);
                        videoName = parts[1];
                        Log.d(TAG, String.format("%s has finished downloading %s", fromEndpoint, videoName));

                        videoPath = String.format("%s/%s", FileManager.getRawDirPath(), videoName);
                        video = VideoManager.getVideoFromPath(context, videoPath);

                        EventBus.getDefault().post(new AddEvent(video, Type.PROCESSING));
                        EventBus.getDefault().post(new RemoveEvent(video, Type.RAW));
                        nextTransfer();

                        break;
                    case HW_INFO:
                        HardwareInfo hwi = gson.fromJson(parts[1], HardwareInfo.class);
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
                long duration = Duration.between(startTimes.remove(payloadId), Instant.now()).toMillis();
                String time = DurationFormatUtils.formatDuration(duration, "ss.SSS");
                Log.i(String.format("!%s", TAG), String.format("Completed downloading %s from %s in %ss",
                        filename, discoveredEndpoints.get(fromEndpointId), time));

                completedFilePayloads.remove(payloadId);
                filePayloadFilenames.remove(payloadId);
                filePayloadCommands.remove(payloadId);

                if (command.equals(Command.ANALYSE)) {
                    sendCommandMessage(Command.COMPLETE, filename, fromEndpointId);
                }

                // Get the received file (which will be in the Downloads folder)
                Payload.File payload = filePayload.asFile();
                if (payload == null) {
                    Log.e(TAG, String.format("Could not create file payload for %s", filename));
                    return;
                }

                //noinspection deprecation
                File payloadFile = payload.asJavaFile();
                if (payloadFile == null) {
                    Log.e(TAG, String.format("Could not create file payload for %s", filename));
                    return;
                }

                // Rename the file.
                File receivedFile = new File(payloadFile.getParentFile(), filename);
                if (!payloadFile.renameTo(receivedFile)) {
                    Log.e(TAG, String.format("Could not rename received file as %s", filename));
                    return;
                }

                String resultsDestPath = FileManager.getResultPathFromVideoName(filename);

                if (command.equals(Command.ANALYSE)) {
                    analyse(receivedFile);

                } else if (command.equals(Command.RETURN)) {
                    File resultsDest = new File(resultsDestPath);

                    try {
                        Files.copy(receivedFile.toPath(), resultsDest.toPath(), REPLACE_EXISTING);
                    } catch (IOException e) {
                        Log.e(TAG, String.format("processFilePayload copy error: \n%s", e.getMessage()));
                        return;
                    }

                    Result result = new Result(resultsDestPath, FileManager.getFilenameFromPath(resultsDestPath));
                    EventBus.getDefault().post(new AddResultEvent(result));
                    String videoName = FileManager.getVideoNameFromResultName(filename);
                    EventBus.getDefault().post(new RemoveByNameEvent(videoName, Type.PROCESSING));
                }
            }
        }

        @Override
        public void onPayloadTransferUpdate(@NonNull String endpointId, @NonNull PayloadTransferUpdate update) {
            // int progress = (int) (100.0 * (update.getBytesTransferred() / (double) update.getTotalBytes()));
            // Log.v(TAG, String.format("Transfer to %s: %d%%", endpointId, progress));

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
    }
}
