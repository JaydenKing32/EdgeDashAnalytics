package com.example.edgedashanalytics.util.hardware;

import static android.content.Context.BATTERY_SERVICE;

import android.app.ActivityManager;
import android.content.Context;
import android.os.BatteryManager;
import android.os.Environment;
import android.os.StatFs;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.io.RandomAccessFile;

public class HardwareInfo {
    public final long cpuFreq;
    public final int cpuCores;
    public final long totalRam;
    public final long availRam;
    public final long totalStorage;
    public final long availStorage;
    public final int batteryLevel;

    public HardwareInfo(Context context) {
        cpuFreq = getCpuFreq();
        cpuCores = getCpuCoreCount();
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
        int freq = -1;
        try {
            RandomAccessFile raf = new RandomAccessFile("/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq", "r");
            String line = raf.readLine();

            if (line != null) {
                return Long.parseLong(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return freq;
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
}

