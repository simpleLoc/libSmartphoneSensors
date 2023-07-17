package de.fhws.indoor.libsmartphonesensors.sensors;

import android.app.Activity;
import android.bluetooth.le.ScanResult;

import java.util.concurrent.atomic.AtomicLong;

import de.fhws.indoor.libsmartphonesensors.ASensor;
import de.fhws.indoor.libsmartphonesensors.SensorDataInterface;
import de.fhws.indoor.libsmartphonesensors.SensorType;
import de.fhws.indoor.libsmartphonesensors.helpers.BLEScanProvider;
import de.fhws.indoor.libsmartphonesensors.util.HexConverter;


/**
 * BLE sensor exporting scan/advertisement events.
 * @author Markus Ebner
 */
public class BLE extends ASensor implements BLEScanProvider.BLEScanCallback {

	private final BLEScanProvider bleScanProvider;

	public BLE(SensorDataInterface sensorDataInterface, BLEScanProvider bleScanProvider) {
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
	public void onScanResult(ScanResult result) {
		if (sensorDataInterface != null) {
			sensorDataInterface.onData(result.getTimestampNanos(), SensorType.IBEACON, Helper.stripMAC(result.getDevice().getAddress()) + ";" + result.getRssi() + ";"
				+ result.getScanRecord().getTxPowerLevel() + ";"
				+ HexConverter.bytesToHex(result.getScanRecord().getBytes()));
		}
	}
}
