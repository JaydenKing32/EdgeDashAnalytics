package com.example.edgedashanalytics.util.hardware;

import static android.content.Context.BATTERY_SERVICE;

import android.app.ActivityManager;
import android.content.Context;
import android.os.BatteryManager;
import android.os.Environment;
import android.os.StatFs;
import android.util.Log;

import androidx.annotation.NonNull;

import com.example.edgedashanalytics.util.file.JsonManager;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Comparator;

public class HardwareInfo {
    private static final String TAG = HardwareInfo.class.getSimpleName();

    public final int cpuCores;
    public final long cpuFreq;
    public final long totalRam;
    public final long availRam;
    public final long totalStorage;
    public final long availStorage;
    public final int batteryLevel;

    @JsonCreator
    public HardwareInfo(@JsonProperty("cpuCores") int cpuCores, @JsonProperty("cpuFreq") long cpuFreq,
                        @JsonProperty("totalRam") long totalRam,
                        @JsonProperty("availRam") long availRam,
                        @JsonProperty("totalStorage") long totalStorage,
                        @JsonProperty("availStorage") long availStorage,
                        @JsonProperty("batteryLevel") int batteryLevel) {
        this.cpuCores = cpuCores;
        this.cpuFreq = cpuFreq;
        this.totalRam = totalRam;
        this.availRam = availRam;
        this.totalStorage = totalStorage;
        this.availStorage = availStorage;
        this.batteryLevel = batteryLevel;
    }

    public HardwareInfo(Context context) {
        cpuCores = getCpuCoreCount();
        cpuFreq = getCpuFreq();
        totalRam = getTotalRam(context);
        availRam = getAvailRam(context);
        totalStorage = getTotalStorage();
        availStorage = getAvailStorage();
        batteryLevel = getBatteryLevel(context);
    }

    /**
     * @return CPU clock speed in Hz
     */
    private long getCpuFreq() {
        long maxFreq = -1;

        for (int i = 0; i < cpuCores; i++) {
            try {
                String filepath = "/sys/devices/system/cpu/cpu" + i + "/cpufreq/cpuinfo_max_freq";
                RandomAccessFile raf = new RandomAccessFile(filepath, "r");
                String line = raf.readLine();

                if (line != null) {
                    long freq = Long.parseLong(line);
                    if (freq > maxFreq) {
                        maxFreq = freq;
                    }
                }
            } catch (IOException e) {
                Log.e(TAG, String.format("Could not retrieve CPU frequency: \n%s", e.getMessage()));
            }
        }

        return maxFreq;
    }

    private int getCpuCoreCount() {
        return Runtime.getRuntime().availableProcessors();
    }

    /**
     * @return total size of RAM in bytes
     */
    private long getTotalRam(Context context) {
        ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo memInfo = new ActivityManager.MemoryInfo();
        activityManager.getMemoryInfo(memInfo);
        return memInfo.totalMem;
    }

    /**
     * @return size of available RAM in bytes
     */
    private long getAvailRam(Context context) {
        ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo memInfo = new ActivityManager.MemoryInfo();
        activityManager.getMemoryInfo(memInfo);
        return memInfo.availMem;
    }

    /**
     * @return total storage size in bytes
     */
    private long getTotalStorage() {
        StatFs stat = new StatFs(Environment.getExternalStorageDirectory().getPath());
        return stat.getBlockSizeLong() * stat.getBlockCountLong();
    }

    /**
     * @return size of available storage in bytes
     */
    private long getAvailStorage() {
        StatFs stat = new StatFs(Environment.getExternalStorageDirectory().getPath());
        return stat.getBlockSizeLong() * stat.getAvailableBlocksLong();
    }

    /**
     * @return percentage of battery level as an integer
     */
    private int getBatteryLevel(Context context) {
        BatteryManager batteryManager = (BatteryManager) context.getSystemService(BATTERY_SERVICE);
        return batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
    }

    @NonNull
    @Override
    public String toString() {
        return "HardwareInfo{" +
                "cpuFreq=" + cpuFreq +
                ", cpuCores=" + cpuCores +
                ", totalRam=" + totalRam +
                ", availRam=" + availRam +
                ", totalStorage=" + totalStorage +
                ", availStorage=" + availStorage +
                ", batteryLevel=" + batteryLevel +
                '}';
    }

    public String toJson() {
        return JsonManager.writeToString(this);
    }

    public static HardwareInfo fromJson(String json) {
        return (HardwareInfo) JsonManager.readFromString(json, HardwareInfo.class);
    }

    public static Comparator<HardwareInfo> compareProcessing() {
        return Comparator.comparing((HardwareInfo hwi) -> hwi.cpuFreq)
                .thenComparing(hwi -> hwi.cpuCores)
                .thenComparing(hwi -> hwi.totalRam);
    }
}

