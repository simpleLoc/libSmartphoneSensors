package de.fhws.indoor.libsmartphonesensors;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class VendorInformation {

    public static class InformationStructure {
        String id;
        HashMap<String, String> data = new HashMap<>();

        public InformationStructure(String id) {
            this.id = id;
        }

        public String getId() { return id; }

        public Map<String, String> getData() { return data;}

        public void set(String key, String value) { data.put(key, value); }
        public void set(String key, int value) { set(key, Integer.toString(value)); }
        public void set(String key, float value) { set(key, Float.toString(value)); }
        public void set(String key, boolean value) { set(key, ((value) ? "true" : "false")); }
    }

    InformationStructure deviceInfo = new InformationStructure("Device");
    ArrayList<InformationStructure> sensors = new ArrayList<>();

    public InformationStructure addSensor(String sensorKey) {
        InformationStructure sensorInformation = new InformationStructure(sensorKey);
        sensors.add(sensorInformation);
        return sensorInformation;
    }

    public InformationStructure getDeviceInfo() { return deviceInfo; }

    @NonNull
    public List<InformationStructure> getSensorInfo() {
        return sensors;
    }

}
