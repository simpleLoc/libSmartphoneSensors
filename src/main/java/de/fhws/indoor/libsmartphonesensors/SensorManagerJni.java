package de.fhws.indoor.libsmartphonesensors;

import android.app.Activity;
import android.util.Log;
import android.widget.Toast;

import java.io.IOException;
import java.io.OutputStream;
import java.util.HashSet;
import java.util.Set;

import de.fhws.indoor.libsmartphonesensors.util.permissions.AppCompatMultiPermissionRequester;
import de.fhws.indoor.libsmartphonesensors.util.permissions.IPermissionRequester;
import de.fhws.indoor.libsmartphonesensors.util.permissions.LocationServiceHelper;

public class SensorManagerJni {
    private static Activity activity = null;
    private static SensorManager sensorManager = null;
    private static JniPermissionRequester permissionRequester = null;

    private static class JniPermissionRequester implements IPermissionRequester {
        private static boolean requestLocation = false;
        private static Set<String> permissionsToRequest = new HashSet<>();

        @Override
        public synchronized void requestLocationService() {
            if(!LocationServiceHelper.isEnabled(activity)) {
                requestLocation = true;
            }
        }

        @Override
        public synchronized void add(String permission) {
            permissionsToRequest.add(permission);
        }

        @Override
        public synchronized void launch(SuccessListener successListener) {
            SensorManagerJni.startPermissionRequest(requestLocation, permissionsToRequest.toArray(new String[]{}), new SuccessListener() {
                @Override public void onFinished() {
                    Log.i("SensorManagerJni", "permissionRequest completed");
                    successListener.onFinished();
                }
            });
        }
    }

    private static class NativeDataInterface implements SensorDataInterface {
        @Override
        public long getStartTimestamp() {
            return 0;
        }

        @Override
        public void onData(long timestamp, SensorType id, String csv) {
            SensorManagerJni.onSensorEvent(timestamp, id.id(), csv);
        }

        @Override
        public OutputStream requestAuxiliaryChannel(String id) throws UnsupportedOperationException, IOException {
            return null;
        }
    }

    public static void init(Activity activity) {
        Log.i("SensorManagerJni", "init()");
        SensorManagerJni.activity = activity;
        permissionRequester = new JniPermissionRequester();
    }

    public static void start() {
        Log.i("SensorManagerJni", "start()");
        if(SensorManagerJni.sensorManager == null) { throw new RuntimeException("SensorManager not yet configured"); }
        try {
            SensorManagerJni.sensorManager.start(activity);
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(activity, "Failed to start sensors", Toast.LENGTH_LONG).show();
        }
    }
    public static void stop() {
        Log.i("SensorManagerJni", "stop()");
        if(SensorManagerJni.sensorManager == null) { throw new RuntimeException("SensorManager not started"); }
        try {
            SensorManagerJni.sensorManager.stop(activity);
        } catch (Exception e) { e.printStackTrace(); }
        SensorManagerJni.sensorManager = null;
    }

    public static void configure(SensorManager.Config config) {
        Log.i("SensorManagerJni", "configure(): " + config);
        SensorManagerJni.sensorManager = new SensorManager(new NativeDataInterface());
        try {
            SensorManagerJni.sensorManager.configure(activity, config, permissionRequester);
            permissionRequester.launch(() -> {});
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(activity, "Failed to configure sensors", Toast.LENGTH_LONG).show();
        }
    }

    protected static native void onSensorEvent(long timestamp, long evtId, String evtData);

    protected static native void startPermissionRequest(boolean requestLocation, String[] permissions, IPermissionRequester.SuccessListener callback);

}
