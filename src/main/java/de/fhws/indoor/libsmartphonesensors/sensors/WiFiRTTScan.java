package de.fhws.indoor.libsmartphonesensors.sensors;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.MacAddress;
import android.net.wifi.ScanResult;
import android.net.wifi.rtt.RangingRequest;
import android.net.wifi.rtt.RangingResult;
import android.net.wifi.rtt.RangingResultCallback;
import android.net.wifi.rtt.WifiRttManager;
import android.os.Build;
import android.os.Handler;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

import de.fhws.indoor.libsmartphonesensors.ASensor;
import de.fhws.indoor.libsmartphonesensors.SensorType;
import de.fhws.indoor.libsmartphonesensors.VendorInformation;
import de.fhws.indoor.libsmartphonesensors.helpers.WifiScanProvider;

/**
 * Wifi RTT sensor exporting time-of-flight measurements.
 * @author Steffen Kastner
 * @author Markus Ebner
 */
public class WiFiRTTScan extends ASensor implements WifiScanProvider.WifiScanCallback {
    private final String TAG = "WiFiRTTScan";

    private final Activity activity;
    private final WifiRttManager rttManager;
    private final WifiScanProvider wifiScanProvider;
    private final Executor mainExecutor;
    private final ScanPlan scanPlan;
    private ScanConfig scanConfig = new ScanConfig();

    private static class ScanConfig {
        long minRangingIntervalMSec;
        long rangingIntervalMSec;
        int ftmBurstSize;
    }

    private static class ScanPlan {

        private static class ScheduledMeasurement implements Comparable<ScheduledMeasurement> {
            ScanResult scanResult = null;
            int numberFails = 0;
            long nextPlanned;
            ScheduledMeasurement(ScanResult scanResult) {
                this.scanResult = scanResult;
                this.nextPlanned = System.currentTimeMillis();
            }

            @Override
            public int compareTo(@NonNull ScheduledMeasurement another) {
                return Long.compare(nextPlanned, another.nextPlanned);
            }
        }
        public interface ScanPlanIterFn {
            void iter(ScanResult scanResult);
        };

        /**
         * Threshold that defines when a scheduled measurement is part of the slow task list.
         * If the measurement to an AP failed for this amount of times, it is then scheduled
         * with the slower interval.
         */
        private static long SLOW_TASK_FAILURE_THRESHOLD = 4;
        /**
         * Interval with which measurements are scheduled if they are part of the slow task list.
         */
        private static long MEASURE_INTERVAL_FACTOR_SLOW = 5;
        /**
         * Interval with which measurements are scheduled if they are part of the slow task list.
         */
        private static long MEASURE_INTERVAL_FACTOR_FAST = 1;

        ScanConfig scanConfig;
        private HashMap<String, ScheduledMeasurement> plannedMeasurements = new HashMap<>();
        private ArrayList<ScheduledMeasurement> scanQueue = new ArrayList<>();

        public ScanPlan(ScanConfig scanConfig) {
            this.scanConfig = scanConfig;
        }

        private void sortScanQueue() {
            synchronized (plannedMeasurements) {
                Collections.sort(scanQueue);
            }
        }

        /**
         * Add the macAddress of a ftm-able device that was found during a scan.
         * If already known, this resets the internal failCount, thus moving the device back
         * into the fast-measure list.
         */
        public boolean addMeasurementTarget(String macAddress, ScanResult scanResult) {
            synchronized (plannedMeasurements) {
                if (!plannedMeasurements.containsKey(macAddress)) {
                    ScheduledMeasurement scheduledMeasurement = new ScheduledMeasurement(scanResult);
                    plannedMeasurements.put(macAddress, scheduledMeasurement);
                    scanQueue.add(scheduledMeasurement);
                    return true;
                } else { // measurement-device was found in scan result, reset failure count to move it back to fast list!
                    setMeasurementSuccess(macAddress, true);
                    return false;
                }
            }
        }

        /**
         * Report back whether the last scan of a device with the given macAddress worked.
         * This is used to decide whether to move devices from/to fast/slow measure list.
         */
        public void setMeasurementSuccess(String macAddress, boolean success) {
            synchronized (plannedMeasurements) {
                ScheduledMeasurement plannedMeasurement = plannedMeasurements.get(macAddress);
                if (plannedMeasurement == null) return;

                if(success == true) {
                    if(plannedMeasurement.numberFails >= SLOW_TASK_FAILURE_THRESHOLD) {
                        Log.d("RTTScanPlan", plannedMeasurement.scanResult.BSSID + ": Slow -> Fast");
                    }
                    plannedMeasurement.numberFails = 0;
                } else {
                    if(plannedMeasurement.numberFails < SLOW_TASK_FAILURE_THRESHOLD && (plannedMeasurement.numberFails + 1) >= SLOW_TASK_FAILURE_THRESHOLD) {
                        Log.d("RTTScanPlan", plannedMeasurement.scanResult.BSSID + ": Fast -> Slow");
                    }
                    plannedMeasurement.numberFails += 1;
                }
            }
        }
        /**
         * Iterate over the device that should be included in the next ftm scan.
         */
        public int iterateNextNPlanned(int n, ScanPlanIterFn iterFn) {
            Long currentTs = System.currentTimeMillis();
            int scanJobCnt = 0;
            synchronized (plannedMeasurements) {
                for(int i = 0; i < Math.min(n, scanQueue.size()); ++i) {
                    ScheduledMeasurement scheduledMeasurement = scanQueue.get(i);
                    if(scheduledMeasurement.nextPlanned <= currentTs) {
                        iterFn.iter(scheduledMeasurement.scanResult);
                        scanJobCnt += 1;

                        // advance nextPlanned timestamp of measurement
                        if(scheduledMeasurement.numberFails >= SLOW_TASK_FAILURE_THRESHOLD) {
                            scheduledMeasurement.nextPlanned = System.currentTimeMillis() + scanConfig.rangingIntervalMSec * MEASURE_INTERVAL_FACTOR_SLOW;
                        } else {
                            scheduledMeasurement.nextPlanned = System.currentTimeMillis() + scanConfig.rangingIntervalMSec * MEASURE_INTERVAL_FACTOR_FAST;
                        }

                    }
                }
                sortScanQueue();
            }
            return scanJobCnt;
        }

        /**
         * Clear planned measurements
         */
        public void clear() {
            synchronized (plannedMeasurements) {
                plannedMeasurements.clear();
            }
        }
    }

    private final AtomicBoolean rangingRunning = new AtomicBoolean(false);
    private final Handler delayNextMeasurementHandler = new Handler();
    private final RangingResultCallback rangeCallback;

    @RequiresApi(api = Build.VERSION_CODES.P)
    public WiFiRTTScan(Activity activity, WifiScanProvider wifiScanProvider, long rangingIntervalMSec, int ftmBurstSize) {
        this.activity = activity;
        this.wifiScanProvider = wifiScanProvider;
        this.rangeCallback = new WiFiRTTScanRangingCallback();
        this.scanConfig.minRangingIntervalMSec = rangingIntervalMSec;
        this.scanConfig.rangingIntervalMSec = this.scanConfig.minRangingIntervalMSec;
        this.scanConfig.ftmBurstSize = ftmBurstSize;
        this.scanPlan = new ScanPlan(scanConfig);

        this.rttManager = (WifiRttManager) activity.getSystemService(Context.WIFI_RTT_RANGING_SERVICE);
        this.mainExecutor = activity.getMainExecutor();
    }

    @RequiresApi(api = Build.VERSION_CODES.P)
    @Override
    public void onResume(Activity act) {
        startScanningAndRanging();
    }

    @Override
    public void onPause(Activity act) {
        stopScanningAndRanging();

        // reset rtt scan results
        scanPlan.clear();
    }

    @RequiresApi(api = Build.VERSION_CODES.P)
    private void startScanningAndRanging() {
        wifiScanProvider.registerCallback(this);
        rangingRunning.set(true);
        startRanging();
    }

    private void stopScanningAndRanging() {
        wifiScanProvider.unregisterCallback(this);
        rangingRunning.set(false);
    }

    @SuppressLint("MissingPermission")
    @RequiresApi(api = Build.VERSION_CODES.P)
    private void startRanging() {
        if(rangingRunning.get() == false) { return; }

        RangingRequest.Builder builder = new RangingRequest.Builder();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setRttBurstSize(scanConfig.ftmBurstSize);
        }
        int scanJobCnt = scanPlan.iterateNextNPlanned(RangingRequest.getMaxPeers(), (ScanResult sr) -> {
            builder.addAccessPoint(sr);
        });

        if(scanJobCnt == 0) {
            // retry later
            queueNextDelayedRangingRequest();
        } else {
            rttManager.startRanging(builder.build(), mainExecutor, rangeCallback);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.R)
    @Override
    public void onScanResult(List<ScanResult> scanResults) {
        for(ScanResult sr : scanResults) {
            if(sr.is80211mcResponder()) {
                if(sr.channelWidth != ScanResult.CHANNEL_WIDTH_20MHZ && sr.channelWidth != ScanResult.CHANNEL_WIDTH_40MHZ) {
                    sr.channelWidth = ScanResult.CHANNEL_WIDTH_20MHZ;
                }
                if(scanPlan.addMeasurementTarget(sr.BSSID, sr)) {
                    Log.i(TAG, "Found new RTT-enabled ("+ sr.channelWidth+") AP: " + sr.BSSID);
                }
            }
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.P)
    private void queueNextDelayedRangingRequest() {
        delayNextMeasurementHandler.postDelayed(() -> startRanging(), this.scanConfig.rangingIntervalMSec);
    }

    // result callback
    @RequiresApi(api = Build.VERSION_CODES.P)
    private class WiFiRTTScanRangingCallback extends RangingResultCallback {
        @Override
        public void onRangingFailure(final int i) {
            //emitter.onError(new RuntimeException("The WiFi-Ranging failed with error code: " + i));
            Log.d(TAG, "onRangingFailure: " + i);
            scanConfig.rangingIntervalMSec += 50; // ranging failed, increase ranging delay by 50ms
            queueNextDelayedRangingRequest();
        }

        @Override
        public void onRangingResults(final List<RangingResult> list) {
            if(rangingRunning.get() == false) { return; }
            // ranging worked, decrease delay ranging by 10ms
            scanConfig.rangingIntervalMSec = Math.max(scanConfig.rangingIntervalMSec - 10, scanConfig.minRangingIntervalMSec);
            queueNextDelayedRangingRequest();

            for (final RangingResult res : list) {
                if(res.getStatus() != RangingResult.STATUS_SUCCESS) {
                    scanPlan.setMeasurementSuccess(res.getMacAddress().toString(), false);
                    continue;
                }
                scanPlan.setMeasurementSuccess(res.getMacAddress().toString(), true);

                int success = 1;
                MacAddress mac = res.getMacAddress();
                long timeStampInNS = res.getRangingTimestampMillis() * 1000000;
                int dist = res.getDistanceMm();
                int stdDevDist = res.getDistanceStdDevMm();
                int rssi = res.getRssi();
                int numAttemptedMeas = res.getNumAttemptedMeasurements();
                int numSuccessfulMeas = res.getNumSuccessfulMeasurements();

                Log.d(TAG, mac.toString() + " " + dist + " " + stdDevDist + " " + rssi);

                if (listener != null) {
                    // success; mac; dist; stdDevDist; RSSI; numAttemptedMeas; numSuccessfulMeas
                    StringBuilder sb = new StringBuilder();

                    sb.append(success).append(';');
                    sb.append(Helper.stripMAC(mac.toString())).append(';');
                    sb.append(dist).append(';');
                    sb.append(stdDevDist).append(';');
                    sb.append(rssi).append(';');
                    sb.append(numAttemptedMeas).append(';');
                    sb.append(numSuccessfulMeas);

                    listener.onData(SensorType.WIFIRTT, timeStampInNS, sb.toString());
                }
            }
        }
    };

    public static boolean isSupported(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P)
            return false;

        if(!context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WIFI_RTT)) {
            return false;
        }

        WifiRttManager rttManager = (WifiRttManager) context.getSystemService(Context.WIFI_RTT_RANGING_SERVICE);
        if(rttManager == null) {
            return false;
        }

        return rttManager.isAvailable();
    }


    public static void dumpVendorInformation(Activity activity, VendorInformation vendorInformation) {
        VendorInformation.InformationStructure sensorInfo = vendorInformation.addSensor("WifiFTM");
        boolean available = isSupported(activity);
        sensorInfo.set("Available", available);
        if(!available) { return; }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            sensorInfo.set("RangingRequest_MinBurstSize", RangingRequest.getMinRttBurstSize());
            sensorInfo.set("RangingRequest_MaxBurstSize", RangingRequest.getMaxRttBurstSize());
            sensorInfo.set("RangingRequest_DefaultBurstSize", RangingRequest.getDefaultRttBurstSize());
            sensorInfo.set("RangingRequest_MaxPeers", RangingRequest.getMaxPeers());
        }
    }
}
