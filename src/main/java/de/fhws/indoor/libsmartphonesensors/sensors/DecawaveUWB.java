package de.fhws.indoor.libsmartphonesensors.sensors;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.NonNull;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import de.fhws.indoor.libsmartphonesensors.ASensor;
import de.fhws.indoor.libsmartphonesensors.SensorDataInterface;
import de.fhws.indoor.libsmartphonesensors.SensorType;
import de.fhws.indoor.libsmartphonesensors.helpers.BleDataStream;
import no.nordicsemi.android.ble.BleManager;
import no.nordicsemi.android.ble.callback.profile.ProfileDataCallback;
import no.nordicsemi.android.ble.data.Data;

/**
 * Decawave UWB DWM1000 module readout via BLE.
 * @author Markus Bullmann
 */
public class DecawaveUWB extends ASensor {

    private boolean running = false;
    private Object lifecycleMutex = new Object();
    private AtomicBoolean currentlyConnecting = new AtomicBoolean(false);
    private AtomicBoolean connectedToTag = new AtomicBoolean(false);
    private Config config = null;

    public static class Config {
        public String tagMacAddress;
    };

    private class DecawaveManager extends BleManager {
        private static final String TAG = "DecaManager";

        /** Decawave Service UUID. */
        public final  UUID LBS_UUID_SERVICE = UUID.fromString("680c21d9-c946-4c1f-9c11-baa1c21329e7");
        /** Location data  */
        public final UUID LBS_UUID_LOCATION_DATA_CHAR = UUID.fromString("003bbdf2-c634-4b3d-ab56-7ec889b89a37");
        /** Location data mode */
        public final UUID LBS_UUID_LOCATION_MODE_CHAR = UUID.fromString("a02b947e-df97-4516-996a-1882521e0ead");

        private BluetoothGattCharacteristic _locationDataCharacteristic;
        private BluetoothGattCharacteristic _locationModeCharacteristic;
        private BluetoothDevice currentDevice = null;

        public DecawaveManager(@NonNull final Context context)
        {
            super(context);
        }

        @Override
        public void log(final int priority, @NonNull final String message) {
            // Log.println(priority, TAG, message);
        }

        public void connectToDevice(BluetoothDevice device) {
            currentDevice = device;
            currentlyConnecting.set(true);
            connect(currentDevice)
                    .retry(3, 100)
                    .useAutoConnect(false)
                    .done((dev) -> { connectedToTag.set(true); currentlyConnecting.set(false); })
                    .fail((a, b) -> { // if we are still running, try again
                        synchronized (lifecycleMutex) {
                            if(running == true) { connectToDevice(currentDevice); }
                        }
                    })
                    .enqueue();
        }

        @NonNull
        @Override
        protected BleManagerGattCallback getGattCallback() {
            return new BleManagerGattCallback() {
                @Override
                protected void initialize() {

                    setNotificationCallback(_locationDataCharacteristic).with(mLocationDataCallback);

                    beginAtomicRequestQueue()
                            .add(requestMtu(512) // Remember, GATT needs 3 bytes extra. This will allow packet size of 244 bytes.
                                    .with((device, mtu) -> log(Log.INFO, "MTU set to " + mtu))
                                    .fail((device, status) -> log(Log.WARN, "Requested MTU not supported: " + status)))
                            .add(writeCharacteristic(_locationModeCharacteristic, new byte[] {2}))
                            .add(enableNotifications(_locationDataCharacteristic))
                            .done(device -> log(Log.INFO, "Target initialized"))
                            .enqueue();
                }

                @Override
                protected boolean isRequiredServiceSupported(@NonNull final BluetoothGatt gatt) {
                    final BluetoothGattService service = gatt.getService(LBS_UUID_SERVICE);
                    if (service != null) {
                        _locationDataCharacteristic = service.getCharacteristic(LBS_UUID_LOCATION_DATA_CHAR);
                        _locationModeCharacteristic = service.getCharacteristic(LBS_UUID_LOCATION_MODE_CHAR);
                    }

                    return _locationDataCharacteristic != null && _locationModeCharacteristic != null;
                }

                @Override
                protected void onDeviceDisconnected() {
                    synchronized (lifecycleMutex) {
                        connectedToTag.set(false);
                        if(running == false) {
                            _locationDataCharacteristic = null;
                            _locationModeCharacteristic = null;
                        } else { // attempt to reconnect
                            connectToDevice(currentDevice);
                        }
                    }
                }
            };
        }

        private	final ProfileDataCallback mLocationDataCallback = new ProfileDataCallback() {
            @Override
            public void onDataReceived(@NonNull BluetoothDevice device, @NonNull Data data) {
                // Log.d(TAG, "onDataReceived: length=" + data.size() + " data=" + data);

                if (data.size() == 0) {
                    return;
                }

                final long timestamp = SystemClock.elapsedRealtimeNanos();

                BleDataStream stream = new BleDataStream(data);

                // 0: Position only
                // 1: Distance
                // 2: Position and Distance
                int mode = stream.readByte();
                final boolean readPos = mode == 0 || mode == 2;
                final boolean readDist = mode == 1 || mode == 2;

                if (!readPos) {
                    return;
                }

                StringBuilder csv = new StringBuilder(); // X;Y;Z;QualiFactor;[NodeID;DistInMM;QualiFactor]

                if (readPos) {
                    // X,Y,Z coordinates (each 4 bytes) and quality factor in percent (0-100) (1 byte), total size: 13 bytes

                    int x = stream.readUInt32();
                    int y = stream.readUInt32();
                    int z = stream.readUInt32();
                    byte quality = stream.readByte();

                    csv.append(x).append(';');
                    csv.append(y).append(';');
                    csv.append(z).append(';');
                    csv.append(quality).append(';');
                }

                if (readDist) {
                    // First byte is distance count(1 byte)
                    // Sequence of node ID(2 bytes), distance in mm(4 bytes) and quality factor(1 byte)
                    // Max value contains 15 elements, size: 8 - 106
                    byte numOfAnchors = stream.readByte();

                    for (int i = 0; i < numOfAnchors; i++) {

                        int nodeID = stream.readUInt16();
                        int distInMM = stream.readUInt32();
                        byte quality = stream.readByte();

                        csv.append(nodeID).append(';');
                        csv.append(distInMM).append(';');
                        csv.append(quality).append(';');
                    }
                }

                csv.deleteCharAt(csv.length()-1); // remove last ';'

                boolean everything = stream.eof();  // debug helper

                sensorDataInterface.onData(timestamp, SensorType.DECAWAVE_UWB, csv.toString());
            }

            @Override
            public void onInvalidDataReceived(@NonNull final BluetoothDevice device,
                                              @NonNull final Data data) {
                Log.w(TAG, "Invalid data received: " + data);
            }
        };
    }

    private final BluetoothAdapter bluetoothAdapter;
    private final DecawaveManager decaManager;

    public DecawaveUWB(SensorDataInterface sensorDataInterface, final Activity act, Config config) {
        super(sensorDataInterface);
        this.config = config;
        BluetoothManager bluetoothManager = (BluetoothManager) act.getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();

        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent =
                    new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            act.startActivityForResult(enableBtIntent, 42);
        }

        decaManager = new DecawaveManager(act);
    }

    @Override
    public void onResume(Activity act) {
        synchronized (lifecycleMutex) {
            running = true;
            if (bluetoothAdapter != null) {
                BluetoothDevice device = bluetoothAdapter.getRemoteDevice(config.tagMacAddress);
                decaManager.connectToDevice(device);
            }
        }
    }

    @Override
    public void onPause(Activity act) {
        synchronized (lifecycleMutex) {
            running = false;
            decaManager.disconnect().enqueue();
            connectedToTag.set(false);
        }
    }

    public boolean isConnectedToTag() { return connectedToTag.get(); }
    public boolean isCurrentlyConnecting() { return currentlyConnecting.get(); }
}
