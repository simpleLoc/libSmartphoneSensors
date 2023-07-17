package de.fhws.indoor.libsmartphonesensors.sensors;

import android.app.Activity;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.os.ParcelUuid;

import java.nio.ByteBuffer;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import de.fhws.indoor.libsmartphonesensors.ASensor;
import de.fhws.indoor.libsmartphonesensors.SensorDataInterface;
import de.fhws.indoor.libsmartphonesensors.SensorType;
import de.fhws.indoor.libsmartphonesensors.helpers.BLEScanProvider;

/**
 * Wifi sensor exporting scan/advertisement events.
 * @author Markus Ebner
 */
public class EddystoneUIDBeacon extends ASensor implements BLEScanProvider.BLEScanCallback {

    private static final ParcelUuid EDDYSTONE_UUID = ParcelUuid.fromString("0000feaa-0000-1000-8000-00805f9b34fb");
    private static final ScanFilter eddystoneScanFilter = new ScanFilter.Builder().setServiceUuid(EDDYSTONE_UUID).build();

    private final BLEScanProvider bleScanProvider;

    public EddystoneUIDBeacon(SensorDataInterface sensorDataInterface, BLEScanProvider bleScanProvider) {
        super(sensorDataInterface);
        this.bleScanProvider = bleScanProvider;
    }

    @Override
    public void onResume(Activity act) {
        bleScanProvider.registerCallback(this);
    }

    @Override
    public void onPause(Activity act) {
        bleScanProvider.unregisterCallback(this);
    }

    @Override
    public void onScanResult(ScanResult scanResult) {
        if(!eddystoneScanFilter.matches(scanResult)) { return; }

        byte[] payload = scanResult.getScanRecord().getBytes();
        if(payload.length > 9) {
            // Check whether the Eddystone-Frame we got is a EddystoneUID frame (instead of: TLM, EID, URL, ...)
            // see: https://github.com/google/eddystone/blob/master/protocol-specification.md
            int i = 8;
            for(; i < payload.length; ++i) {
                if(payload[i-8] == (byte)0x03 && payload[i-7] == (byte)0x03 && payload[i-6] == (byte)0xAA && payload[i-5] == (byte)0xFE
                        && payload[i-3] == (byte)0x16 && payload[i-2] == (byte)0xAA && payload[i-1] == (byte)0xFE) {
                    // found eddystone frame header
                    if(payload[i] == (byte)0x00) { // EddystoneUID
                        handleBluetoothAdvertisement(scanResult, i);
                    }
                }
            }
        }
    }

    private void handleBluetoothAdvertisement(ScanResult advertisement, int uidFrameOffset) {
        byte[] payload = advertisement.getScanRecord().getBytes();
        if(payload.length - uidFrameOffset < 31) {
            return; // not a valid Eddystone UID frame
        }
        byte[] uuidBytes = new byte[16];
        System.arraycopy(payload, uidFrameOffset + 2, uuidBytes, 0, 16);
        ByteBuffer bb = ByteBuffer.wrap(uuidBytes);
        long uuidHigh = bb.getLong();
        long uuidLow = bb.getLong();
        UUID uuid = new UUID(uuidHigh, uuidLow);

        // For EddystoneUID layout, see: https://github.com/google/eddystone/tree/master/eddystone-uid
        if(sensorDataInterface != null) {
            sensorDataInterface.onData(advertisement.getTimestampNanos(), SensorType.EDDYSTONE_UID, Helper.stripMAC(advertisement.getDevice().getAddress()) + ";"
                    + advertisement.getRssi() + ";"
                    + advertisement.getScanRecord().getTxPowerLevel() + ";"
                    + uuid.toString());
        }
    }
}
