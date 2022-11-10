package de.fhws.indoor.libsmartphonesensors;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.location.LocationManager;
import android.os.Build;
import android.provider.Settings;
import android.util.AndroidException;

import androidx.core.app.ActivityCompat;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

import de.fhws.indoor.libsmartphonesensors.helpers.WifiScanProvider;
import de.fhws.indoor.libsmartphonesensors.io.VendorInformationSerializer;
import de.fhws.indoor.libsmartphonesensors.sensors.DecawaveUWB;
import de.fhws.indoor.libsmartphonesensors.sensors.EddystoneUIDBeacon;
import de.fhws.indoor.libsmartphonesensors.sensors.GpsNew;
import de.fhws.indoor.libsmartphonesensors.sensors.GroundTruth;
import de.fhws.indoor.libsmartphonesensors.sensors.HeadingChange;
import de.fhws.indoor.libsmartphonesensors.sensors.PhoneSensors;
import de.fhws.indoor.libsmartphonesensors.sensors.StepDetector;
import de.fhws.indoor.libsmartphonesensors.sensors.WiFi;
import de.fhws.indoor.libsmartphonesensors.sensors.WiFiRTTScan;
import de.fhws.indoor.libsmartphonesensors.sensors.iBeacon;

public class SensorManager {
    final private int MY_PERMISSIONS_REQUEST_READ_BT = 123;
    final private int MY_PERMISSIONS_REQUEST_READ_HEART = 321;

    private boolean running = false;
    private WifiScanProvider wifiScanProvider = null;

    public static class Config {
        public boolean hasGPS = false;
        public boolean hasWifi = false;
        public boolean hasWifiRTT = false;
        public boolean hasBluetooth = false;
        public boolean hasPhone = false;
        public boolean hasHeadingChange = false;
        public boolean hasStepDetector = false;
        public boolean hasDecawaveUWB = false;

        // uwb
        public String decawaveUWBTagMacAddress = "";
        public long wifiScanIntervalMSec;
        // ftm
        public long ftmRangingIntervalMSec;
    }

    public interface SensorListener {
        void onData(final long timestamp, final SensorType id, final String csv);
    }

    private ArrayList<ASensor> sensors = new ArrayList<>();
    private HashMap<Class<ASensor>, Integer> sensorTypeMap = new HashMap();
    private ArrayList<SensorListener> sensorListeners = new ArrayList<>();

    public void addSensorListener(SensorListener sensorListener) {
        sensorListeners.add(sensorListener);
    }
    public void removeSensorListener(SensorListener sensorListener) {
        sensorListeners.remove(sensorListener);
    }

    private void sendSensorEvent(final long timestamp, final SensorType id, final String csv) {
        for(SensorListener sensorListener : sensorListeners) {
            sensorListener.onData(timestamp, id, csv);
        }
    }

    public <T extends ASensor> T getSensor(Class<T> clazz) {
        if(running == false) { return null; }
        Integer idx = sensorTypeMap.get(clazz);
        if(idx == null) { return null; }
        return (T) sensors.get(idx);
    }

    public void configure(Activity activity, Config config) throws Exception {
        if(running == true) { throw new Exception("Can not reconfigure SensorManager while it is running"); }
        sensors.clear();
        sensorTypeMap.clear();

        wifiScanProvider = new WifiScanProvider(activity, config.wifiScanIntervalMSec);
        final ASensor.SensorListener sensorEvtForwarder = new ASensor.SensorListener(){
            @Override public void onData(final SensorType id, final long timestamp, final String csv) { sendSensorEvent(timestamp, id, csv); }
        };

        // add sensors
        final GroundTruth grndTruth = new GroundTruth(activity);
        sensors.add(grndTruth);
        grndTruth.setListener(sensorEvtForwarder);

        if(config.hasPhone) {
            PhoneSensors phoneSensors = new PhoneSensors(activity);
            phoneSensors.setListener(sensorEvtForwarder);
            sensors.add(phoneSensors);
        }
        if(config.hasHeadingChange) {
            final HeadingChange headingChange = new HeadingChange(activity);
            sensors.add(headingChange);
            headingChange.setListener(sensorEvtForwarder);
        }
        if(config.hasStepDetector) {
            final StepDetector stepDetector = new StepDetector(activity);
            sensors.add(stepDetector);
            stepDetector.setListener(sensorEvtForwarder);
        }
        if(config.hasGPS) {
            //log gps using sensor number 16
            final GpsNew gps = new GpsNew(activity);
            sensors.add(gps);
            gps.setListener(sensorEvtForwarder);
        }
        if(config.hasWifi) {
            // log wifi using sensor number 8
            final WiFi wifi = new WiFi(wifiScanProvider);
            sensors.add(wifi);
            wifi.setListener(sensorEvtForwarder);
        }
        if(config.hasWifiRTT) {
            if (WiFiRTTScan.isSupported(activity)) {
                if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.P) { return; }
                final WiFiRTTScan wiFiRTTScan = new WiFiRTTScan(activity, wifiScanProvider, config.ftmRangingIntervalMSec);
                sensors.add(wiFiRTTScan);
                // log wifi RTT using sensor number 17
                wiFiRTTScan.setListener(sensorEvtForwarder);
            }
        }
        if(config.hasBluetooth) {
            // bluetooth permission
            if(ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.ACCESS_FINE_LOCATION)) {
            } else {
                ActivityCompat.requestPermissions(activity, new String[]{
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.BLUETOOTH_CONNECT,
                        Manifest.permission.BLUETOOTH_ADMIN,
                        Manifest.permission.BLUETOOTH_SCAN
                }, MY_PERMISSIONS_REQUEST_READ_BT);
            }

            LocationManager lm = (LocationManager)activity.getSystemService(Context.LOCATION_SERVICE);
            boolean gps_enabled = false;
            boolean network_enabled = false;
            try {
                gps_enabled = lm.isProviderEnabled(LocationManager.GPS_PROVIDER);
            } catch(Exception ex) {}
            try {
                network_enabled = lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
            } catch(Exception ex) {}
            if(!gps_enabled && !network_enabled) {
                // notify user
                new AlertDialog.Builder(activity)
                        .setMessage(R.string.gps_not_enabled)
                        .setCancelable(false)
                        .setPositiveButton(R.string.open_location_settings, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface paramDialogInterface, int paramInt) {
                                activity.startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                            }
                        })
                        .show();
            }

            // log iBeacons using sensor number 9
            final ASensor beacon;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                beacon = new iBeacon(activity);
            } else {
                beacon = null;
                //beacon = new iBeaconOld(this);
            }

            if (beacon != null) {
                sensors.add(beacon);
                beacon.setListener(sensorEvtForwarder);
            }

            final ASensor eddystone;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                eddystone = new EddystoneUIDBeacon(activity);
                sensors.add(eddystone);
                eddystone.setListener(sensorEvtForwarder);
            }
        }

        if (config.hasDecawaveUWB) {
            DecawaveUWB.Config uwbConfig = new DecawaveUWB.Config();
            uwbConfig.tagMacAddress = config.decawaveUWBTagMacAddress;
            DecawaveUWB sensorUWB = new DecawaveUWB(activity, uwbConfig);
            sensors.add(sensorUWB);
            sensorUWB.setListener(sensorEvtForwarder);
        }

        // fill sensorTypeMap for easier access
        for(int idx = 0; idx < sensors.size(); ++idx) {
            sensorTypeMap.put((Class<ASensor>) sensors.get(idx).getClass(), idx);
        }
    }

    public void dumpVendorInformation(Activity activity, File targetFile) throws IOException {
        android.hardware.SensorManager androidSensorManager = (android.hardware.SensorManager)activity.getSystemService(Context.SENSOR_SERVICE);
        FileOutputStream outputStream = new FileOutputStream(targetFile);
        VendorInformation vendorInformation = new VendorInformation();
        // device info
        VendorInformation.InformationStructure deviceInfo = vendorInformation.getDeviceInfo();
        deviceInfo.set("SOCManufacturer" , Build.SOC_MANUFACTURER);
        deviceInfo.set("SOCModel" , Build.SOC_MODEL);
        deviceInfo.set("Manufacturer" , Build.MANUFACTURER);
        deviceInfo.set("Brand" , Build.BRAND);
        deviceInfo.set("Model" , Build.MODEL);
        deviceInfo.set("Android" , Build.VERSION.RELEASE);
        // sensor info
        PhoneSensors.dumpVendorInformation(androidSensorManager, vendorInformation);
        WiFiRTTScan.dumpVendorInformation(activity, vendorInformation);
        iBeacon.dumpVendorInformation(activity, vendorInformation);

        VendorInformationSerializer.serialize(outputStream, vendorInformation);
        outputStream.flush();
        outputStream.close();
    }

    public void start(Activity activity) throws Exception {
        if(running == true) { throw new Exception("SensorManager already running"); }
        for(ASensor sensor : sensors) {
            sensor.onResume(activity);
        }
        running = true;
    }

    public void stop(Activity activity) throws Exception {
        if(running == false) { throw new Exception("SensorManager currently not running"); }
        for(ASensor sensor : sensors) {
            sensor.onPause(activity);
        }
        running = false;
    }

}
