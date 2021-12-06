package de.fhws.indoor.libsmartphonesensors;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.location.LocationManager;
import android.os.Build;

import androidx.core.app.ActivityCompat;

import java.lang.reflect.Array;
import java.util.ArrayList;

import de.fhws.indoor.libsmartphonesensors.helpers.WifiScanProvider;
import de.fhws.indoor.libsmartphonesensors.sensors.DecawaveUWB;
import de.fhws.indoor.libsmartphonesensors.sensors.EddystoneUIDBeacon;
import de.fhws.indoor.libsmartphonesensors.sensors.GpsNew;
import de.fhws.indoor.libsmartphonesensors.sensors.HeadingChange;
import de.fhws.indoor.libsmartphonesensors.sensors.PhoneSensors;
import de.fhws.indoor.libsmartphonesensors.sensors.SensorType;
import de.fhws.indoor.libsmartphonesensors.sensors.WiFi;
import de.fhws.indoor.libsmartphonesensors.sensors.WiFiRTTScan;

public class SensorManager {
//    final private int MY_PERMISSIONS_REQUEST_READ_BT = 123;
//    final private int MY_PERMISSIONS_REQUEST_READ_HEART = 321;
//
//    public static class Config {
//        boolean hasGPS = false;
//        boolean hasWifi = false;
//        boolean hasWifiRTT = false;
//        boolean hasBluetooth = false;
//        boolean hasPhone = false;
//        boolean hasHeadingChange = false;
//        boolean hasDecawaveUWB = false;
//
//        // uwb
//        String decawaveUWBTagMacAddress = "";
//        long wifiScanIntervalSec;
//    }
//
//    public interface SensorListener {
//        void onData(final long timestamp, final SensorType id, final String csv);
//    }
//
//    private ArrayList<ASensor> sensors = new ArrayList<>();
//    private ArrayList<SensorListener> sensorListeners = new ArrayList<>();
//
//    private void sendSensorEvent(final long timestamp, final SensorType id, final String csv) {
//        for(SensorListener sensorListener : sensorListeners) {
//            sensorListener.onData(timestamp, id, csv);
//        }
//    }
//
//
//    public void configure(Activity activity, Config config) {
//        sensors.clear();
//
//        final WifiScanProvider wifiScanProvider = new WifiScanProvider(activity, config.wifiScanIntervalSec);
//
//        if(config.hasPhone) {
//            PhoneSensors phoneSensors = new PhoneSensors(activity);
//            phoneSensors.setListener(new ASensor.SensorListener(){
//                @Override public void onData(final long timestamp, final String csv) { return; }
//                @Override public void onData(final SensorType id, final long timestamp, final String csv) { sendSensorEvent(timestamp, id, csv); }
//            });
//            sensors.add(phoneSensors);
//        }
//        if(config.hasHeadingChange) {
//            final HeadingChange headingChange = new HeadingChange(activity);
//            sensors.add(headingChange);
//            headingChange.setListener(new ASensor.SensorListener(){
//                @Override public void onData(final long timestamp, final String csv) { sendSensorEvent(timestamp, SensorType.HEADING_CHANGE, csv); }
//                @Override public void onData(final SensorType id, final long timestamp, final String csv) { return; }
//            });
//        }
//        if(config.hasGPS) {
//            //log gps using sensor number 16
//            final GpsNew gps = new GpsNew(activity);
//            sensors.add(gps);
//            gps.setListener(new ASensor.SensorListener(){
//                @Override public void onData(final long timestamp, final String csv) { sendSensorEvent(timestamp, SensorType.GPS, csv); }
//                @Override public void onData(final SensorType id, final long timestamp, final String csv) { return; }
//            });
//        }
//        if(config.hasWifi) {
//            // log wifi using sensor number 8
//            final WiFi wifi = new WiFi(wifiScanProvider);
//            sensors.add(wifi);
//            wifi.setListener(new ASensor.SensorListener() {
//                @Override public void onData(final long timestamp, final String csv) { sendSensorEvent(timestamp, SensorType.WIFI, csv); }
//                @Override public void onData(final SensorType id, final long timestamp, final String csv) {return; }
//            });
//        }
//        if(config.hasWifiRTT) {
//            if (WiFiRTTScan.isSupported(activity)) {
//                if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.P) { return; }
//                final WiFiRTTScan wiFiRTTScan = new WiFiRTTScan(activity, wifiScanProvider);
//                sensors.add(wiFiRTTScan);
//                // log wifi RTT using sensor number 17
//                wiFiRTTScan.setListener(new ASensor.SensorListener() {
//                    @Override public void onData(final long timestamp, final String csv) { sendSensorEvent(timestamp, SensorType.WIFIRTT, csv); }
//                    @Override public void onData(final SensorType id, final long timestamp, final String csv) { sendSensorEvent(timestamp, SensorType.WIFIRTT, csv); }
//                });
//            }
//        }
//        if(config.hasBluetooth) {
//            // bluetooth permission
//            if(ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.ACCESS_FINE_LOCATION)) {
//            } else {
//                ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, MY_PERMISSIONS_REQUEST_READ_BT);
//            }
//
//            LocationManager lm = (LocationManager)activity.getSystemService(Context.LOCATION_SERVICE);
//            boolean gps_enabled = false;
//            boolean network_enabled = false;
//            try {
//                gps_enabled = lm.isProviderEnabled(LocationManager.GPS_PROVIDER);
//            } catch(Exception ex) {}
//            try {
//                network_enabled = lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
//            } catch(Exception ex) {}
//            if(!gps_enabled && !network_enabled) {
//                // notify user
//                new AlertDialog.Builder(activity)
//                        .setMessage(R.string.gps_not_enabled)
//                        .setCancelable(false)
//                        .setPositiveButton(R.string.open_location_settings, new DialogInterface.OnClickListener() {
//                            @Override
//                            public void onClick(DialogInterface paramDialogInterface, int paramInt) {
//                                startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
//                            }
//                        })
//                        .show();
//            }
//
//            // log iBeacons using sensor number 9
//            final ASensor beacon;
//            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
//                beacon = new iBeacon(this);
//            } else {
//                beacon = null;
//                //beacon = new iBeaconOld(this);
//            }
//
//            if (beacon != null) {
//                sensors.add(beacon);
//                beacon.setListener(new ASensor.SensorListener() {
//                    @Override public void onData(final long timestamp, final String csv) { add(SensorType.IBEACON, csv, timestamp); }
//                    @Override public void onData(final SensorType id, final long timestamp, final String csv) {return; }
//                });
//            }
//
//            final ASensor eddystone;
//            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
//                eddystone = new EddystoneUIDBeacon(this);
//                sensors.add(eddystone);
//                eddystone.setListener(new ASensor.SensorListener() {
//                    @Override public void onData(long timestamp, String csv) { add(SensorType.EDDYSTONE_UID, csv, timestamp); }
//                    @Override public void onData(SensorType id, long timestamp, String csv) { return; }
//                });
//            }
//        }
//
//        if (config.hasDecawaveUWB) {
//            sensorUWB = new DecawaveUWB(this, config.decawaveUWBTagMacAddress);
//            sensors.add(sensorUWB);
//            sensorUWB.setListener(new ASensor.SensorListener() {
//                @Override public void onData(final long timestamp, final String csv) { add(SensorType.DECAWAVE_UWB, csv, timestamp); }
//                @Override public void onData(final SensorType id, final long timestamp, final String csv) { add(id, csv, timestamp); }
//            });
//        }
//    }
//
//    public void start(Activity activity) {
//        for(ASensor sensor : sensors) {
//            sensor.onResume(activity);
//        }
//    }
//
//    public void stop(Activity activity) {
//        for(ASensor sensor : sensors) {
//            sensor.onPause(activity);
//        }
//    }

}
