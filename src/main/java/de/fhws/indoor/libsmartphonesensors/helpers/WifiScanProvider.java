package de.fhws.indoor.libsmartphonesensors.helpers;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.util.Log;

import androidx.annotation.RequiresApi;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Helper to handle multiple sensors that all require Wifi scan results.
 * @author Markus Ebner
 */
public class WifiScanProvider {
    private static final String TAG = "WifiScanProvider";

    public interface WifiScanCallback {
        void onScanResult(List<ScanResult> scanResults);
    };

    private final Activity activity;
    private final long scanIntervalMSec;
    private final WifiManager wifiManager;
    private final ArrayList<WifiScanCallback> scanCallbacks = new ArrayList<>();
    private final IntentFilter scanAvailableFilter = new IntentFilter();

    // scan state
    private boolean currentlyScanning = false;
    private Timer scanTimer = null;

    public WifiScanProvider(Activity activity, long scanIntervalMSec) {
        this.activity = activity;
        this.scanIntervalMSec = scanIntervalMSec;
        wifiManager = (WifiManager) activity.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        scanAvailableFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
    }

    public void registerCallback(WifiScanCallback scanCallback) {
        scanCallbacks.add(scanCallback);
        startScanningIfRequired();
    }

    public void unregisterCallback(WifiScanCallback scanCallback) {
        scanCallbacks.remove(scanCallback);
        if (scanCallbacks.size() == 0) {
            stopScanning();
        }
    }

    private void notifyListeners(List<ScanResult> scanResults) {
        ArrayList<WifiScanCallback> tmpScanCallbacks = (ArrayList<WifiScanCallback>) this.scanCallbacks.clone();
        for (WifiScanCallback wifiScanCallback : tmpScanCallbacks) {
            try {
                wifiScanCallback.onScanResult(scanResults);
            } catch (Exception ex) {
                Log.e(TAG, ex.getMessage());
            }
        }
    }


    // ###########
    // # Scanning
    // ###########

    private void stopScanning() {
        if(currentlyScanning) {
            if(scanTimer != null) {
                scanTimer.cancel();
                scanTimer = null;
            }
            activity.unregisterReceiver(scanResultUpdateReceiver);
            currentlyScanning = false;
        }
    }
    private void startScanningIfRequired() {
        if(!currentlyScanning) {
            if(!wifiManager.isWifiEnabled()) {
                wifiManager.setWifiEnabled(true);
            }
            // make sure we're not currently connected
            wifiManager.disconnect();
            activity.registerReceiver(scanResultUpdateReceiver, scanAvailableFilter);
            scanTimer = new Timer();
            scanTimer.scheduleAtFixedRate(scanTimerTask(), 0, Math.max(scanIntervalMSec, 50));
            currentlyScanning = true;
        }
    }

    private TimerTask scanTimerTask() {
        return new TimerTask() {
            @Override
            public void run() {
                runScan();
            }
        };
    }
    private final BroadcastReceiver scanResultUpdateReceiver = new BroadcastReceiver() {
        @RequiresApi(api = Build.VERSION_CODES.M)
        @Override
        public void onReceive(Context context, Intent intent) {
            boolean success = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false);
            if (!success) { return; }

            List<ScanResult> scanResults = wifiManager.getScanResults();
            notifyListeners(scanResults);
            // if interval is set to 0, we try to start a new scan as soon as the previous one is done.
            // in addition to that, we use the timer as backup (in case startScan() == false)
            if(scanIntervalMSec == 0) {
                runScan();
            }
        }
    };

    private void runScan() {
        try {
            if(wifiManager.startScan() && scanIntervalMSec > 0) {
                Log.d(TAG, "Wifi Scan started");
            }
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
        }
    }
}
