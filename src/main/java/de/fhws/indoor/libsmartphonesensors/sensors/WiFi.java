package de.fhws.indoor.libsmartphonesensors.sensors;

import android.app.Activity;
import android.net.wifi.ScanResult;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import de.fhws.indoor.libsmartphonesensors.ASensor;
import de.fhws.indoor.libsmartphonesensors.SensorType;
import de.fhws.indoor.libsmartphonesensors.helpers.WifiScanProvider;


/**
 * Wifi sensor exporting scan/advertisement events.
 * @author Markus Ebner
 */
public class WiFi extends ASensor implements WifiScanProvider.WifiScanCallback {

	private final WifiScanProvider wifiScanProvider;
	private AtomicLong scanResultCnt = new AtomicLong(0);

	public WiFi(WifiScanProvider wifiScanProvider) {
		this.wifiScanProvider = wifiScanProvider;
	}

	@Override
	public void onResume(Activity act) {
		wifiScanProvider.registerCallback(this);
		scanResultCnt.set(0);
	}

	@Override
	public void onPause(Activity act) {
		wifiScanProvider.unregisterCallback(this);
		scanResultCnt.set(0);
    }

	@Override
	public void onScanResult(List<ScanResult> scanResults) {
		final StringBuilder sb = new StringBuilder(1024);
		scanResultCnt.incrementAndGet();
		for(final ScanResult sr : scanResults) {
			sb.append(Helper.stripMAC(sr.BSSID)).append(';');
			sb.append(sr.frequency).append(';');
			sb.append(sr.level);
			listener.onData(SensorType.WIFI, sr.timestamp * 1000, sb.toString());
			sb.setLength(0);
		}
	}

	/**
	 * Get the number of scan results (batches of measurements from the OS) that were received since
	 * the sensor was started.
	 * @return Number of scan results received since sensor start.
	 */
	public Long getScanResultCount() {
		return scanResultCnt.get();
	}
}
