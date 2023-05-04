package de.fhws.indoor.libsmartphonesensors.helpers;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import no.nordicsemi.android.ble.BleManager;

public class UwbConfigurator {

    public final  UUID LBS_UUID_SERVICE = UUID.fromString("680c21d9-c946-4c1f-9c11-baa1c21329e7");

    public interface UWBDeviceConfiguredCallback {
        void done();
        void fail();
    };

    public static class UwbAnchorPosition {
        public float x;
        public float y;
        public float z;

        public UwbAnchorPosition(float x, float y, float z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    public static class UwbAnchorConfiguration {
        public UwbAnchorPosition position;

        public UwbAnchorConfiguration(UwbAnchorPosition position) {
            this.position = position;
        }
    }

    // ######################
    private final BluetoothAdapter bluetoothAdapter;
    private HashMap<String, DecawaveConfigurator> configuratorInstances = new HashMap<>();
    private boolean running = false;
    private Object lifecycleMutex = new Object();
    private Activity activity;

    public UwbConfigurator(BluetoothAdapter bluetoothAdapter) {
        this.bluetoothAdapter = bluetoothAdapter;
    }

    public void onResume(Activity act) {
        this.activity = act;
        synchronized (lifecycleMutex) {
            running = true;
        }
    }

    public void onPause(Activity act) {
        synchronized (lifecycleMutex) {
            running = false;
            for(DecawaveConfigurator configurator : configuratorInstances.values()) {
                configurator.disconnect();
            }
            configuratorInstances.clear();
        }
    }

    private class DecawaveConfigurator extends BleManager {
        /** Anchor Persisted Position */
        public final UUID LBS_UUID_ANCHOR_PERSISTED_POSITION = UUID.fromString("f0f26c9b-2c8c-49ac-ab60-fe03def1b40c");
        private BluetoothGattCharacteristic _positionConfigurationCharacteristic;
        private byte[] configData = null;
        private String bleMac;
        private UwbAnchorConfiguration anchorConfiguration;
        private UWBDeviceConfiguredCallback configuredCallback;
        private BluetoothDevice currentDevice = null;

        public DecawaveConfigurator(@NonNull Context context, String bleMac, UwbAnchorConfiguration anchorConfiguration, UWBDeviceConfiguredCallback configuredCallback) {
            super(context);
            this.bleMac = bleMac;
            this.anchorConfiguration = anchorConfiguration;
            this.configuredCallback = configuredCallback;

            ByteBuffer configBuffer = ByteBuffer.allocate(13).order(ByteOrder.LITTLE_ENDIAN);
            int xMM = (int)(anchorConfiguration.position.x / 0.001);
            int yMM = (int)(anchorConfiguration.position.y / 0.001);
            int zMM = (int)(anchorConfiguration.position.z / 0.001);
            configBuffer.putInt(xMM);
            configBuffer.putInt(yMM);
            configBuffer.putInt(zMM);
            configBuffer.put((byte)100);
            configData = configBuffer.array();

            BluetoothDevice anchorDevice = bluetoothAdapter.getRemoteDevice(bleMac);
            connectToDevice(anchorDevice);
            connect(anchorDevice)
                    .retry(3, 100)
                    .useAutoConnect(false)
                    .fail((a, b) -> {
                        configuredCallback.fail();
                    })
                    .enqueue();
        }

        public void connectToDevice(BluetoothDevice device) {
            currentDevice = device;
            connect(currentDevice)
                    .retry(3, 100)
                    .useAutoConnect(false)
                    .fail((a, b) -> { // if we are still running, try again
                        synchronized (lifecycleMutex) {
                            if(running) { connectToDevice(currentDevice); }
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
                    beginAtomicRequestQueue()
                            .add(requestMtu(512) // Remember, GATT needs 3 bytes extra. This will allow packet size of 244 bytes.
                                    .with((device, mtu) -> log(Log.INFO, "MTU set to " + mtu))
                                    .fail((device, status) -> log(Log.WARN, "Requested MTU not supported: " + status)))
                            .add(writeCharacteristic(_positionConfigurationCharacteristic, configData))
                            .done(device -> {
                                log(Log.INFO, "Successfully configured device: " + bleMac);
                                configuredCallback.done();
                            })
                            .fail((a, b) -> {
                                configuredCallback.fail();
                            })
                            .enqueue();
                }

                @Override
                public boolean isRequiredServiceSupported(@NonNull final BluetoothGatt gatt) {
                    final BluetoothGattService service = gatt.getService(LBS_UUID_SERVICE);
                    if (service != null) {
                        _positionConfigurationCharacteristic = service.getCharacteristic(LBS_UUID_ANCHOR_PERSISTED_POSITION);
                    }
                    return _positionConfigurationCharacteristic != null;
                }

                @Override
                protected void onDeviceDisconnected() {
                    _positionConfigurationCharacteristic = null;
                }
            };
        }
    }


    public void configureDevicePosition(String bleMac, UwbAnchorConfiguration uwbAnchorConfiguration, UWBDeviceConfiguredCallback configuredCallback) {
        synchronized (configuratorInstances) {
            if (configuratorInstances.containsKey(bleMac)) {
                return;
            } // already running
            configuratorInstances.put(bleMac, new DecawaveConfigurator(activity, bleMac, uwbAnchorConfiguration, new UWBDeviceConfiguredCallback() {
                @Override
                public void done() {
                    synchronized (configuratorInstances) {
                        configuratorInstances.remove(bleMac);
                        configuredCallback.done();
                    }
                }

                @Override
                public void fail() {
                    synchronized (configuratorInstances) {
                        configuratorInstances.remove(bleMac);
                        configuredCallback.fail();
                    }
                }
            }));
        }
    }

}
