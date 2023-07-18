package de.fhws.indoor.libsmartphonesensors.helpers;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

import de.fhws.indoor.libsmartphonesensors.VendorInformation;
import de.fhws.indoor.libsmartphonesensors.util.permissions.IPermissionRequester;

/**
 * Helper to handle multiple sensors that all require Wifi scan results.
 * @author Markus Ebner
 */
@SuppressLint("MissingPermission") // checked manually
public class BLEScanProvider {
    private static final String TAG = "BLEScanProvider";

    // automatically restart ble scan every 25 minutes
    // this is because android automatically stops (down-prioritizes) the scan after ~30mins
    private static final long RESTART_INTERVAL_MSEC = 25 * 60 * 1000;
    private static final int REQUEST_ENABLE_BT = 4380643;

    public interface BLEScanCallback {
        void onScanResult(ScanResult scanResult);
    };

    @NonNull
    protected final Handler mainLoopHandler = new Handler(Looper.getMainLooper());

    private Activity activity;
    private BluetoothAdapter bt;
    private BluetoothLeScanner scanner;
    private static ScanSettings settings;
    private final Set<BLEScanCallback> scanCallbacks = new HashSet<>();
    private ScanCallback mLeScanCallback;
    private final Timer restartTimer = new Timer();
    private TimerTask restartTask = null;

    // scan state
    private boolean currentlyScanning = false;


    public BLEScanProvider(Activity activity, IPermissionRequester permissionRequester) {
        this.activity = activity;

        // bluetooth permission
        permissionRequester.add(Manifest.permission.ACCESS_FINE_LOCATION);
        permissionRequester.add(Manifest.permission.BLUETOOTH_ADMIN);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissionRequester.add(Manifest.permission.BLUETOOTH_CONNECT);
            permissionRequester.add(Manifest.permission.BLUETOOTH_SCAN);
        }
    }

    public void registerCallback(BLEScanCallback scanCallback) {
        synchronized (scanCallbacks) {
            scanCallbacks.add(scanCallback);
            startScanningIfRequired();
        }
    }

    public void unregisterCallback(BLEScanCallback scanCallback) {
        synchronized (scanCallbacks) {
            scanCallbacks.remove(scanCallback);
            if (scanCallbacks.size() == 0) {
                stopScanning();
            }
        }
    }

    private void notifyListeners(android.bluetooth.le.ScanResult scanResult) {
        synchronized (this.scanCallbacks) {
            for(BLEScanCallback scanCallback : scanCallbacks) {
                scanCallback.onScanResult(scanResult);
            }
        }
    }


    // ###########
    // # Scanning
    // ###########
    private boolean setupBluetooth() {
        if(bt != null) { return true; }

        // sanity check
        if (!activity.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(activity, "Bluetooth-LE not supported!", Toast.LENGTH_SHORT).show();
            return false;
        }

        // Initializes a Bluetooth adapter.  For API level 18 and above, get a reference to
        // BluetoothAdapter through BluetoothManager.
        final BluetoothManager mgr = (BluetoothManager) activity.getSystemService(Context.BLUETOOTH_SERVICE);
        bt = mgr.getAdapter();

        // bluetooth supported?
        if(bt == null) {
            Toast.makeText(activity, "Bluetooth not supported!", Toast.LENGTH_SHORT).show();
            return false;
        } else if(!bt.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            activity.startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }

        // create the scanner
        scanner = bt.getBluetoothLeScanner();

        // and attach the callback
        mLeScanCallback = new ScanCallback() {
            @Override public void onScanResult(int callbackType, ScanResult result) {
                //Log.d("BT", device + " " + rssi);
                notifyListeners(result);
            }
        };

        settings = new ScanSettings.Builder()
                .setReportDelay(0)
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build();

        return true;
    }

    private void stopScanning() {
        if(restartTask != null) {
            restartTask.cancel();
            restartTask = null;
            scanner.stopScan(mLeScanCallback);
            currentlyScanning = false;
        }
    }
    private void startScanningIfRequired() {
        if(!currentlyScanning) {
            if(setupBluetooth()) {
                restartTask = new TimerTask() {
                    @Override
                    public void run() {
                        try {
                            scanner.stopScan(mLeScanCallback);
                        } catch(Exception e) {}
                        mainLoopHandler.post(() -> {
                            List<ScanFilter> filters = new ArrayList<ScanFilter>();
                            scanner.startScan(filters, settings, mLeScanCallback);
                        });
                    }
                };
                restartTimer.schedule(restartTask, 0, RESTART_INTERVAL_MSEC);
                currentlyScanning = true;
            }
        }
    }



    public static void dumpVendorInformation(Activity activity, VendorInformation vendorInformation) {
        final BluetoothManager mgr = (BluetoothManager) activity.getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter bt = (mgr != null) ? mgr.getAdapter() : null;

        VendorInformation.InformationStructure bleInfo = vendorInformation.addSensor("Bluetooth");
        bleInfo.set("Available", (bt != null));
        if(bt == null) { return; }
        bleInfo.set("Name", bt.getName());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            bleInfo.set("BLEMaximumAdvertisingDataLength", bt.getLeMaximumAdvertisingDataLength());
            bleInfo.set("BLE2MPhySupported", bt.isLe2MPhySupported());
            bleInfo.set("BLECodedPhySupported", bt.isLeCodedPhySupported());
            bleInfo.set("BLEExtendedAdvertisingSupported", bt.isLeExtendedAdvertisingSupported());
            bleInfo.set("BLEPeriodicAdvertisingSupported", bt.isLePeriodicAdvertisingSupported());
            bleInfo.set("MultipleAdvertisementSupported", bt.isMultipleAdvertisementSupported());
            bleInfo.set("OffloadedFilteringSupported", bt.isOffloadedFilteringSupported());
            bleInfo.set("OffloadedScanBatchingSupported", bt.isOffloadedScanBatchingSupported());
        }
    }
}
