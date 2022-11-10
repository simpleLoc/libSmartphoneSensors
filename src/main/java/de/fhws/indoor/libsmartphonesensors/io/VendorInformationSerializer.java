package de.fhws.indoor.libsmartphonesensors.io;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.Arrays;
import java.util.Map;

import de.fhws.indoor.libsmartphonesensors.VendorInformation;

public class VendorInformationSerializer {

    private static void serialize(OutputStreamWriter outWriter, String keyPrefix,  VendorInformation.InformationStructure informationStructure) throws IOException {
        outWriter.write("[" + keyPrefix);
        outWriter.write(informationStructure.getId());
        outWriter.write("]\n");
        Map<String, String> data = informationStructure.getData();
        String[] keys = data.keySet().toArray(new String[0]);
        Arrays.sort(keys);
        for(String key : keys) {
            String value = data.get(key);
            outWriter.write("\t");
            outWriter.write(key);
            outWriter.write("=");
            outWriter.write(value);
            outWriter.write("\n");
        }
        outWriter.write("\n");
    }

    public static void serialize(OutputStream dst, VendorInformation vendorInformation) throws IOException {
        OutputStreamWriter outWriter = new OutputStreamWriter(dst);

        serialize(outWriter, "", vendorInformation.getDeviceInfo());
        for(VendorInformation.InformationStructure sensorInfo : vendorInformation.getSensorInfo()) {
            serialize(outWriter, "Sensor:", sensorInfo);
        }

        outWriter.flush();
        outWriter.close();
    }

}
