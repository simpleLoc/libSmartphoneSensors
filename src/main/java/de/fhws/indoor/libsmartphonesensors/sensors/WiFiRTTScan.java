package de.fhws.indoor.libsmartphonesensors.sensors;

import android.Manifest;
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
import android.util.Log;

import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

import de.fhws.indoor.libsmartphonesensors.ASensor;
import de.fhws.indoor.libsmartphonesensors.SensorType;
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
    private final ScanPlan scanPlan = new ScanPlan();

    private static class ScanPlan {

        private static class ScheduledMeasurement {
            ScanResult scanResult = null;
            int numberFails = 0;
            ScheduledMeasurement(ScanResult scanResult) {
                this.scanResult = scanResult;
            }
        }
        public interface ScanPlanIterFn {
            void iter(ScanResult scanResult);
        };

        /**
         * Threshold that defines when a scheduled measurement is part of the slow task list.
         * If the measurement to an AP failed for this amount of times, it is then scheduled
         * with a slower interval of 1/SLOW_TASK_SCHEDULE_INTERVAL.
         */
        private static long SLOW_TASK_FAILURE_THRESHOLD = 4; // 4 * 200ms
        /**
         * Interval with which measurements are scheduled if they are part of the slow task list.
         */
        private static long SLOW_TASK_SCHEDULE_INTERVAL = 10; // 10 * 200ms
        private long scanCnt = 0;
        private HashMap<String, ScheduledMeasurement> plannedMeasurements = new HashMap<>();

        /**
         * Add the macAddress of a ftm-able device that was found during a scan.
         * If already known, this resets the internal failCount, thus moving the device back
         * into the fast-measure list.
         */
        public boolean addMeasurementTarget(String macAddress, ScanResult scanResult) {
            synchronized (plannedMeasurements) {
                if (!plannedMeasurements.containsKey(macAddress)) {
                    plannedMeasurements.put(macAddress, new ScheduledMeasurement(scanResult));
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
        public void iteratePlannedMeasurements(ScanPlanIterFn iterFn) {
            synchronized (plannedMeasurements) {
                scanCnt += 1;
                for(ScheduledMeasurement plannedMeasurement : plannedMeasurements.values()) {
                    if(plannedMeasurement.numberFails >= SLOW_TASK_FAILURE_THRESHOLD) {
                        // measurement failed a couple of times before, and thus is part of the slow
                        // interval scan list. So we schedule these measurements with a larger interval.
                        if(scanCnt == SLOW_TASK_SCHEDULE_INTERVAL) {
                            iterFn.iter(plannedMeasurement.scanResult);
                        }
                    } else {
                        iterFn.iter(plannedMeasurement.scanResult);
                    }
                }
                scanCnt %= SLOW_TASK_SCHEDULE_INTERVAL;
            }
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

    private Timer rangeTimer;
    private TimerTask rangingTask() {
        return new TimerTask() {
            @RequiresApi(api = Build.VERSION_CODES.P)
            @Override
            public void run() {
                startRanging();
            }
        };
    }
    private final RangingResultCallback rangeCallback;

    @RequiresApi(api = Build.VERSION_CODES.P)
    public WiFiRTTScan(Activity activity, WifiScanProvider wifiScanProvider) {
        this.activity = activity;
        this.wifiScanProvider = wifiScanProvider;
        this.rangeCallback = new WiFiRTTScanRangingCallback();

        this.rttManager = (WifiRttManager) activity.getSystemService(Context.WIFI_RTT_RANGING_SERVICE);
        this.mainExecutor = activity.getMainExecutor();
    }

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

    private void startScanningAndRanging() {
        wifiScanProvider.registerCallback(this);

        // range to all available APs all 200ms
        rangeTimer = new Timer();
        rangeTimer.scheduleAtFixedRate(rangingTask(), 0, 200);
    }

    private void stopScanningAndRanging() {
        wifiScanProvider.unregisterCallback(this);
        rangeTimer.cancel();
    }

    @RequiresApi(api = Build.VERSION_CODES.P)
    private void startRanging() {
        LinkedList<RangingRequest.Builder> builders = new LinkedList<>();
        AtomicInteger cnt = new AtomicInteger(0);
        scanPlan.iteratePlannedMeasurements((ScanResult sr) -> {
            if (builders.size() == 0 || cnt.get() >= RangingRequest.getMaxPeers()) {
                builders.add(new RangingRequest.Builder());
                cnt.set(0);
            }
            builders.getLast().addAccessPoint(sr);
            cnt.incrementAndGet();
        });

        if (ActivityCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Can not start ranging. Permission not granted");
            stopScanningAndRanging();
        } else {
            for (RangingRequest.Builder builder : builders) {
                final RangingRequest request = builder.build();
                rttManager.startRanging(request, mainExecutor, rangeCallback);
            }
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.R)
    @Override
    public void onScanResult(List<ScanResult> scanResults) {
        for(ScanResult sr : scanResults) {
            if(sr.is80211mcResponder()) {
                sr.channelWidth = ScanResult.CHANNEL_WIDTH_20MHZ;
                if(scanPlan.addMeasurementTarget(sr.BSSID, sr)) {
                    Log.i(TAG, "Found new RTT-enabled ("+ sr.channelWidth+") AP: " + sr.BSSID);
                }
            }
        }
    }

    // result callback
    @RequiresApi(api = Build.VERSION_CODES.P)
    private class WiFiRTTScanRangingCallback extends RangingResultCallback {
        @Override
        public void onRangingFailure(final int i) {
            //emitter.onError(new RuntimeException("The WiFi-Ranging failed with error code: " + i));
            Log.d(TAG, "onRangingFailure: " + i);
        }

        @Override
        public void onRangingResults(final List<RangingResult> list) {
            //emitter.onSuccess(list);
            //Log.d("RTT", "onRangingResults: " + list.size());

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
}
