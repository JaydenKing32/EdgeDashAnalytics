package com.example.edgedashanalytics.util.hardware;

import static android.content.Context.BATTERY_SERVICE;
import static com.example.edgedashanalytics.page.main.MainActivity.I_TAG;

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
    private static BroadcastReceiver batteryReceiver;
    private static long total = 0;
    private static int count = 0;

    public static void startPowerMonitor(Context context) {
        Log.v(TAG, "Starting Power Monitor");

        batteryReceiver = new BroadcastReceiver() {
            private final BatteryManager batteryManager = (BatteryManager) context.getSystemService(BATTERY_SERVICE);

            public void onReceive(Context context, Intent intent) {
                // Assumed to be millivolts
                int voltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1);
                // microamperes
                int current = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
                // Log.v(TAG, String.format("%d", Math.abs(voltage * current)));
                count++;
                // millivolts * microamperes = nanowatts
                total += Math.abs(voltage * current);
            }
        };
        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        context.registerReceiver(batteryReceiver, filter);
    }

    public static void stopPowerMonitor(Context context) {
        Log.v(TAG, "Stopping Power Monitor");
        context.unregisterReceiver(batteryReceiver);
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

        Log.i(I_TAG, message.toString());
    }
}
