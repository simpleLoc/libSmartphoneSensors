package de.fhws.indoor.libsmartphonesensors.sensors;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import de.fhws.indoor.libsmartphonesensors.ASensor;
import de.fhws.indoor.libsmartphonesensors.SensorDataInterface;
import de.fhws.indoor.libsmartphonesensors.SensorType;
import de.fhws.indoor.libsmartphonesensors.VendorInformation;

/**
 * Bluetooth iBeacon sensor.
 * @author Frank Ebner
 */
public class iBeacon extends ASensor {

	// automatically restart ble scan every 25 minutes
	// this is because android automatically stops (down-prioritizes) the scan after ~30mins
	private static final long RESTART_INTERVAL_MSEC = 25 * 60 * 1000;

	private BluetoothAdapter bt;
	private BluetoothLeScanner scanner;
	private static ScanSettings settings;
	private static final int REQUEST_ENABLE_BT = 1;
	private ScanCallback mLeScanCallback;
	private final Timer restartTimer = new Timer();
	private TimerTask restartTask;

	// ctor
	public iBeacon(SensorDataInterface sensorDataInterface, final Activity act) {
		super(sensorDataInterface);

		// sanity check
		if (!act.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
			Toast.makeText(act, "Bluetooth-LE not supported!", Toast.LENGTH_SHORT).show();
			return;
		}

		// Initializes a Bluetooth adapter.  For API level 18 and above, get a reference to
		// BluetoothAdapter through BluetoothManager.
		final BluetoothManager mgr = (BluetoothManager) act.getSystemService(Context.BLUETOOTH_SERVICE);
		bt = mgr.getAdapter();

		// bluetooth supported?
		if (bt == null || !bt.isEnabled()) {

			//TODO: add something that asks the user to enable BLE. this need also be called in onResum()
			Toast.makeText(act, "Bluetooth not supported!", Toast.LENGTH_SHORT).show();
			return;
		}

		// create the scanner
		scanner = bt.getBluetoothLeScanner();

		// and attach the callback
		mLeScanCallback = new ScanCallback() {
			@Override public void onScanResult(int callbackType, android.bluetooth.le.ScanResult result) {
			//Log.d("BT", device + " " + rssi);
			if (sensorDataInterface != null) {
				sensorDataInterface.onData(result.getTimestampNanos(), SensorType.IBEACON, Helper.stripMAC(result.getDevice().getAddress()) + ";" + result.getRssi() + ";" + result.getScanRecord().getTxPowerLevel());
			}
			}
		};

		settings = new ScanSettings.Builder()
				.setReportDelay(0)
				.setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
				//.setNumOfMatches(ScanSettings.MATCH_NUM_MAX_ADVERTISEMENT) //comment this out for apk < 23
				.build();
	}

	private void enableBT(final Activity act) {
		if (bt == null) {throw new RuntimeException("BT not supported!");}
		if (!bt.isEnabled()) {
			Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
			act.startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
		}
	}

	@Override public void onResume(final Activity act) {
		if (bt != null) {
			enableBT(act);
			restartTask = new TimerTask() {
				@Override
				public void run() {
					try {
						scanner.stopScan(mLeScanCallback);
					} catch(Exception e) {}
					List<ScanFilter> filters = new ArrayList<ScanFilter>();
					scanner.startScan(filters, settings, mLeScanCallback);
				}
			};
			restartTimer.schedule(restartTask, 0, RESTART_INTERVAL_MSEC);
		}
	}

	@Override public void onPause(final Activity act) {
		if (bt != null) {
			restartTask.cancel();
			scanner.stopScan(mLeScanCallback);
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
