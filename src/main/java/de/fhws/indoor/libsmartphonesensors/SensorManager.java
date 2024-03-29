package de.fhws.indoor.libsmartphonesensors;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.os.PowerManager;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

import de.fhws.indoor.libsmartphonesensors.helpers.BLEScanProvider;
import de.fhws.indoor.libsmartphonesensors.helpers.WifiScanProvider;
import de.fhws.indoor.libsmartphonesensors.io.VendorInformationSerializer;
import de.fhws.indoor.libsmartphonesensors.sensors.BLE;
import de.fhws.indoor.libsmartphonesensors.sensors.DecawaveUWB;
import de.fhws.indoor.libsmartphonesensors.sensors.EddystoneUIDBeacon;
import de.fhws.indoor.libsmartphonesensors.sensors.GpsNew;
import de.fhws.indoor.libsmartphonesensors.sensors.GroundTruth;
import de.fhws.indoor.libsmartphonesensors.sensors.HeadingChange;
import de.fhws.indoor.libsmartphonesensors.sensors.Microphone;
import de.fhws.indoor.libsmartphonesensors.sensors.PhoneSensors;
import de.fhws.indoor.libsmartphonesensors.sensors.StepDetector;
import de.fhws.indoor.libsmartphonesensors.sensors.WiFi;
import de.fhws.indoor.libsmartphonesensors.sensors.WiFiRTTScan;
import de.fhws.indoor.libsmartphonesensors.util.permissions.IPermissionRequester;

public class SensorManager {
    private boolean running = false;
    private WifiScanProvider wifiScanProvider = null;
    private BLEScanProvider bleScanProvider = null;
    private PowerManager.WakeLock wakeLock = null;

    public static class Config {
        public boolean hasGPS = false;
        public boolean hasWifi = false;
        public boolean hasWifiRTT = false;
        public boolean hasBluetooth = false;
        public boolean hasPhone = false;
        public boolean hasHeadingChange = false;
        public boolean hasStepDetector = false;
        public boolean hasDecawaveUWB = false;
        public boolean hasMicrophone = false;

        // uwb
        public String decawaveUWBTagMacAddress = "";
        public long wifiScanIntervalMSec;
        // ftm
        public long ftmRangingIntervalMSec;
        public int ftmBurstSize;

        @Override
        public String toString() {
            return "Config{" +
                    "hasGPS=" + hasGPS +
                    ", hasWifi=" + hasWifi +
                    ", hasWifiRTT=" + hasWifiRTT +
                    ", hasBluetooth=" + hasBluetooth +
                    ", hasPhone=" + hasPhone +
                    ", hasHeadingChange=" + hasHeadingChange +
                    ", hasStepDetector=" + hasStepDetector +
                    ", hasDecawaveUWB=" + hasDecawaveUWB +
                    ", hasMicrophone=" + hasMicrophone +
                    ", decawaveUWBTagMacAddress='" + decawaveUWBTagMacAddress + '\'' +
                    ", wifiScanIntervalMSec=" + wifiScanIntervalMSec +
                    ", ftmRangingIntervalMSec=" + ftmRangingIntervalMSec +
                    ", ftmBurstSize=" + ftmBurstSize +
                    '}';
        }
    }

    private ArrayList<ASensor> sensors = new ArrayList<>();
    private HashMap<Class<ASensor>, Integer> sensorTypeMap = new HashMap();
    private SensorDataInterface sensorDataInterface;

    public SensorManager(@NonNull SensorDataInterface sensorDataInterface) {
        this.sensorDataInterface = sensorDataInterface;
    }

    public <T extends ASensor> T getSensor(Class<T> clazz) {
        if(running == false) { return null; }
        Integer idx = sensorTypeMap.get(clazz);
        if(idx == null) { return null; }
        return (T) sensors.get(idx);
    }

    public void configure(Activity activity, Config config, IPermissionRequester permissionRequester) throws Exception {
        if(running == true) { throw new Exception("Can not reconfigure SensorManager while it is running"); }
        sensors.clear();
        sensorTypeMap.clear();
        if(wakeLock == null) {
            PowerManager powerManager = (PowerManager) activity.getSystemService(Context.POWER_SERVICE);
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "libSmartphoneSensors::WakeLock");
        }

        wifiScanProvider = new WifiScanProvider(activity, config.wifiScanIntervalMSec);

        // add sensors
        final GroundTruth grndTruth = new GroundTruth(sensorDataInterface, activity);
        sensors.add(grndTruth);

        if(config.hasPhone) {
            PhoneSensors phoneSensors = new PhoneSensors(sensorDataInterface, activity);
            sensors.add(phoneSensors);
        }
        if(config.hasHeadingChange) {
            final HeadingChange headingChange = new HeadingChange(sensorDataInterface, activity);
            sensors.add(headingChange);
        }
        if(config.hasStepDetector) {
            final StepDetector stepDetector = new StepDetector(sensorDataInterface, activity);
            sensors.add(stepDetector);
        }
        if(config.hasGPS) {
            permissionRequester.add(Manifest.permission.ACCESS_FINE_LOCATION);
            permissionRequester.add(Manifest.permission.ACCESS_COARSE_LOCATION);
            permissionRequester.requestLocationService();
            //log gps using sensor number 16
            final GpsNew gps = new GpsNew(sensorDataInterface, activity);
            sensors.add(gps);
        }
        if(config.hasWifi) {
            // log wifi using sensor number 8
            final WiFi wifi = new WiFi(sensorDataInterface, wifiScanProvider);
            sensors.add(wifi);
        }
        if(config.hasWifiRTT) {
            if (WiFiRTTScan.isSupported(activity)) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) { return; }
                if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    permissionRequester.add(Manifest.permission.NEARBY_WIFI_DEVICES);
                }
                final WiFiRTTScan wiFiRTTScan = new WiFiRTTScan(sensorDataInterface, activity, wifiScanProvider, config.ftmRangingIntervalMSec, config.ftmBurstSize);
                sensors.add(wiFiRTTScan); // log wifi RTT using sensor number 17
            }
        }
        if(config.hasBluetooth) {
            bleScanProvider = new BLEScanProvider(activity, permissionRequester);
            permissionRequester.requestLocationService();

            // log iBeacons using sensor number 9
            final BLE ble = new BLE(sensorDataInterface, bleScanProvider);
            sensors.add(ble);

            final EddystoneUIDBeacon eddystone = new EddystoneUIDBeacon(sensorDataInterface, bleScanProvider);
            sensors.add(eddystone);
        }

        if (config.hasDecawaveUWB) {
            DecawaveUWB.Config uwbConfig = new DecawaveUWB.Config();
            uwbConfig.tagMacAddress = config.decawaveUWBTagMacAddress;
            DecawaveUWB sensorUWB = new DecawaveUWB(sensorDataInterface, activity, uwbConfig);
            sensors.add(sensorUWB);
        }

        if(config.hasMicrophone) {
            permissionRequester.add(Manifest.permission.RECORD_AUDIO);
            Microphone sensorMic = new Microphone(sensorDataInterface);
            sensors.add(sensorMic);
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            deviceInfo.set("SOCManufacturer" , Build.SOC_MANUFACTURER);
            deviceInfo.set("SOCModel" , Build.SOC_MODEL);
        }
        deviceInfo.set("Manufacturer" , Build.MANUFACTURER);
        deviceInfo.set("Brand" , Build.BRAND);
        deviceInfo.set("Model" , Build.MODEL);
        deviceInfo.set("Android" , Build.VERSION.RELEASE);
        // sensor info
        PhoneSensors.dumpVendorInformation(androidSensorManager, vendorInformation);
        WiFiRTTScan.dumpVendorInformation(activity, vendorInformation);
        BLEScanProvider.dumpVendorInformation(activity, vendorInformation);

        VendorInformationSerializer.serialize(outputStream, vendorInformation);
        outputStream.flush();
        outputStream.close();
    }

    public void start(Activity activity) throws Exception {
        if(running == true) { throw new Exception("SensorManager already running"); }
        if(wakeLock != null) { wakeLock.acquire(); }
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
        if(wakeLock != null) { wakeLock.release(); }
    }

}
