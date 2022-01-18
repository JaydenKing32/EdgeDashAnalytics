package com.example.edgedashanalytics.util.hardware;

import static android.content.Context.BATTERY_SERVICE;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.util.Log;

import java.util.Locale;
import java.util.StringJoiner;

public class PowerMonitor {
    private static final String TAG = PowerMonitor.class.getSimpleName();
    private static BroadcastReceiver batteryReceiver = null;
    private static long total = 0;
    private static int count = 0;
    private static boolean running = false;

    public static void startPowerMonitor(Context con) {
        if (batteryReceiver == null) {
            batteryReceiver = new BroadcastReceiver() {
                private final BatteryManager batteryManager = (BatteryManager) con.getSystemService(BATTERY_SERVICE);

                public void onReceive(Context context, Intent intent) {
                    // Assumed to be millivolts
                    long voltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1);
                    // Net charge in microamperes,
                    //  positive is net value entering battery, negative is net discharge from battery
                    // Samsung phones seem to provide milliamperes instead: https://stackoverflow.com/a/66933765
                    long current = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
                    // Log.v(TAG, String.format("%d", Math.abs(voltage * current)));
                    count++;
                    // millivolts * microamperes = nanowatts
                    total += voltage * current;
                }
            };
        }

        if (running) {
            Log.v(TAG, "Power Monitor is already running");
        } else {
            Log.v(TAG, "Starting Power Monitor");
            IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            con.registerReceiver(batteryReceiver, filter);
            running = true;
        }
    }

    public static void stopPowerMonitor(Context context) {
        if (running) {
            Log.v(TAG, "Stopping Power Monitor");
            context.unregisterReceiver(batteryReceiver);
            running = false;
        } else {
            Log.v(TAG, "Power Monitor is not running");
        }
    }

    private static double getAveragePower() {
        if (count == 0) {
            return total;
        } else {
            return total / (double) count;
        }
    }

    public static long getAveragePowerMilliWatts() {
        long milliDigits = 1000000;

        if (count == 0) {
            return total / milliDigits;
        } else {
            return (total / count) / milliDigits;
        }
    }

    public static void printSummary() {
        StringJoiner message = new StringJoiner("\n  ");
        message.add("Power usage:");

        message.add(String.format(Locale.ENGLISH, "Count: %d", count));
        message.add(String.format(Locale.ENGLISH, "Total: %dnW", total));
        message.add(String.format(Locale.ENGLISH, "Average: %.4fnW", getAveragePower()));

        Log.d(TAG, message.toString());
    }
}
