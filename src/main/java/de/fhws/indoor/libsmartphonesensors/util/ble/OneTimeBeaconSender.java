package de.fhws.indoor.libsmartphonesensors.util.ble;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertisingSet;
import android.bluetooth.le.AdvertisingSetCallback;
import android.bluetooth.le.AdvertisingSetParameters;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.os.Build;
import android.util.Log;

import androidx.annotation.RequiresApi;

import java.util.Optional;

import de.fhws.indoor.libsmartphonesensors.util.permissions.IPermissionRequester;

@RequiresApi(api = Build.VERSION_CODES.O)
public class OneTimeBeaconSender {
    private static boolean initialized = false;

    private BluetoothAdapter btAdapter;
    private BluetoothLeAdvertiser advertiser;
    private AdvertisingSetCallback currentCallback;

    private static void setup(IPermissionRequester permissionRequester) {
        if(!initialized) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                permissionRequester.add(Manifest.permission.BLUETOOTH_ADVERTISE);
            }
            initialized = true;
        }
    }
    private void ensureInitialized() {
        //check static initialization
        if(!initialized) { throw new IllegalStateException("Not initialized, permission not granted?"); }

        btAdapter = BluetoothAdapter.getDefaultAdapter();
        if(btAdapter == null) { throw new UnsupportedOperationException(); }
        advertiser = btAdapter.getBluetoothLeAdvertiser();
        if(advertiser == null) { throw new UnsupportedOperationException(); }
    }

    public OneTimeBeaconSender(IPermissionRequester permissionRequester) {
        setup(permissionRequester);
    }

    public void sendTimeouted(int txPower, byte[] data, int timeoutMilliseconds) {
        send(txPower, data, Optional.of(timeoutMilliseconds), Optional.empty());
    }

    @SuppressLint("MissingPermission")
    private void send(int txPower, byte[] data, Optional<Integer> timeoutMilliseconds, Optional<Integer> maxPacketCnt) {
        ensureInitialized();
        if(currentCallback != null) {
            advertiser.stopAdvertisingSet(currentCallback);
            currentCallback = null;
        }
        AdvertisingSetParameters advParameters = new AdvertisingSetParameters.Builder()
                .setConnectable(false)
                .setIncludeTxPower(false)
                .setTxPowerLevel(txPower)
                .setInterval(AdvertisingSetParameters.INTERVAL_MIN)
                .setLegacyMode(true)
                .setScannable(false)
                .build();
        AdvertiseData advData = new AdvertiseData.Builder()
                .setIncludeDeviceName(false)
                .setIncludeTxPowerLevel(false)
                .addManufacturerData(0x1337, data)
                .build();
        currentCallback = new AdvertisingSetCallback() {
            @Override
            public void onAdvertisingEnabled(AdvertisingSet advertisingSet, boolean enable, int status) {
                if(status == AdvertisingSetCallback.ADVERTISE_SUCCESS) {
                    Log.d("OneTimeBeaconSender", "Successfully enabled Advertiser");
                } else {
                    Log.e("OneTimeBeaconSender", "Failed to enable Advertiser: " + status);
                }
            }

            @Override
            public void onAdvertisingSetStarted(AdvertisingSet advertisingSet, int txPower, int status) {
                if(status == AdvertisingSetCallback.ADVERTISE_SUCCESS) {
                    Log.d("OneTimeBeaconSender", "Successfully enabled Advertiser");
                } else {
                    Log.e("OneTimeBeaconSender", "Failed to start advertising: " + status);
                }
            }
        };
        advertiser.startAdvertisingSet(advParameters, advData, null, null, null, timeoutMilliseconds.orElse(0), maxPacketCnt.orElse(0), currentCallback);
    }

}
