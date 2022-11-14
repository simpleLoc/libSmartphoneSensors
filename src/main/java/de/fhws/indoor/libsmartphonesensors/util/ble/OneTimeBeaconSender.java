package de.fhws.indoor.libsmartphonesensors.util.ble;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertisingSetCallback;
import android.bluetooth.le.AdvertisingSetParameters;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.os.Build;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

@RequiresApi(api = Build.VERSION_CODES.O)
public class OneTimeBeaconSender {
    private static boolean initialized = false;

    private BluetoothAdapter btAdapter;
    private BluetoothLeAdvertiser advertiser;

    private static void setup(MultiPermissionRequester permissionRequester) {
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

    public OneTimeBeaconSender(MultiPermissionRequester permissionRequester) {
        setup(permissionRequester);
    }

    @SuppressLint("MissingPermission")
    public void send(int txPower, byte[] data, int timeoutMilliseconds) {
        ensureInitialized();
        AdvertisingSetParameters advParameters = new AdvertisingSetParameters.Builder()
                .setConnectable(false)
                .setIncludeTxPower(false)
                .setTxPowerLevel(txPower)
                .setInterval(AdvertisingSetParameters.INTERVAL_HIGH)
                .setLegacyMode(true)
                .setScannable(false)
                .build();
        AdvertiseData advData = new AdvertiseData.Builder()
                .setIncludeDeviceName(false)
                .setIncludeTxPowerLevel(false)
                .addManufacturerData(0x1337, data)
                .build();
        AdvertisingSetCallback advCallback = new AdvertisingSetCallback() {
        };
        advertiser.startAdvertisingSet(advParameters, advData, null, null, null, timeoutMilliseconds, 0, advCallback);
    }

}
