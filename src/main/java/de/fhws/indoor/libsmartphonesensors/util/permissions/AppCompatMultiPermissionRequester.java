package de.fhws.indoor.libsmartphonesensors.util.permissions;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.Lifecycle;

import java.util.ArrayList;

import de.fhws.indoor.libsmartphonesensors.R;

public class AppCompatMultiPermissionRequester implements IPermissionRequester {

    public AppCompatMultiPermissionRequester(AppCompatActivity activity) {
        MultiPermissionRequesterImpl.init(activity);
    }

    @Override
    public void requestLocationService() {
        if(!LocationServiceHelper.isEnabled(MultiPermissionRequesterImpl.get().activity)) {
            MultiPermissionRequesterImpl.get().requestLocationService();
        }
    }

    @Override
    public void add(String permission) {
        MultiPermissionRequesterImpl.get().add(permission);
    }

    @Override
    public void launch(SuccessListener successListener) {
        MultiPermissionRequesterImpl.get().launch(successListener);
    }










    private static class MultiPermissionRequesterImpl {
        private static Object syncObj = new Object();
        private static MultiPermissionRequesterImpl instance = null;

        private AppCompatActivity activity;
        private final ArrayList<String> permissionsRequired = new ArrayList<>();
        private boolean shouldRequestLocationService = false;

        private final ArrayList<String> permissionsToRequest = new ArrayList<>();
        private final ActivityResultContracts.RequestMultiplePermissions requestPermissionContract;
        private ActivityResultLauncher<String[]> permissionRequestLauncher;
        private SuccessListener successListener;

        public static void init(AppCompatActivity activity) {
            if(activity.getLifecycle().getCurrentState() != Lifecycle.State.INITIALIZED) {
                throw new RuntimeException("Call MultiPermissionRequester::init() in onCreate() of Activity!");
            }
            synchronized (syncObj) {
                if(instance != null) { instance.unregister(); }
                instance = null;
                instance = new MultiPermissionRequesterImpl(activity);
            }
        }

        public static MultiPermissionRequesterImpl get() {
            synchronized (syncObj) {
                if(instance == null) { throw new RuntimeException("Forgot to call init() in Activity!"); }
                return instance;
            }
        }

        private MultiPermissionRequesterImpl(AppCompatActivity activity) {
            this.activity = activity;
            this.requestPermissionContract = new ActivityResultContracts.RequestMultiplePermissions();
            this.permissionRequestLauncher = activity.registerForActivityResult(requestPermissionContract, granteds -> {
                if(granteds.isEmpty()) { return; }

                int idx = 0; while(idx < permissionsToRequest.size()) {
                    String perm = permissionsToRequest.get(idx);
                    if(granteds.get(perm)) { permissionsToRequest.remove(idx); }
                    else { idx += 1; }
                }

                boolean shittyDialogRequired = false;
                for(String perm : granteds.keySet()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        if(activity.shouldShowRequestPermissionRationale(perm)) { shittyDialogRequired = true; }
                    } else {
                        shittyDialogRequired = true;
                    }
                }
                if(shittyDialogRequired) {
                    new AlertDialog.Builder(activity)
                            .setTitle("Permissions required")
                            .setMessage("All Permissions are required for the app to work. If they are denied, the app will exit.")
                            .setCancelable(false)
                            .setPositiveButton("Request Again", (a, b) -> { permissionRequestLauncher.launch(permissionsToRequest.toArray(new String[]{})); })
                            .setNegativeButton("Close", (a, b) -> {
                                Toast.makeText(activity, "Required permission was denied, app won't work!", Toast.LENGTH_LONG).show();
                                activity.finishAffinity();
                            })
                            .create()
                            .show();
                } else {
                    if(granteds.containsValue(false)) { // some permission denied, try again
                        Toast.makeText(activity, "Required permission was denied, app won't work!", Toast.LENGTH_LONG).show();
                        activity.finishAffinity();
                    } else { // succeeded
                        if(this.successListener != null) {
                            this.successListener.onFinished();
                            this.successListener = null;
                        }
                    }
                }
            });
        }

        private void unregister() {
            if(permissionRequestLauncher != null) {
                permissionRequestLauncher.unregister();
                permissionRequestLauncher = null;
            }
        }

        public void requestLocationService() {
            shouldRequestLocationService = true;
        }

        public void add(String permission) {
            this.permissionsRequired.add(permission);
        }

        public synchronized void launch(SuccessListener successListener) {
            if(this.successListener != null) { return; }
            this.successListener = successListener;

            if(shouldRequestLocationService) {
                shouldRequestLocationService = false;
                new AlertDialog.Builder(activity)
                        .setMessage(R.string.gps_not_enabled)
                        .setCancelable(false)
                        .setPositiveButton(R.string.open_location_settings, (paramDialogInterface, paramInt) -> activity.startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)))
                        .show();
                return;
            }

            for(String permission : permissionsRequired) {
                if(ContextCompat.checkSelfPermission(activity, permission) != PackageManager.PERMISSION_GRANTED) {
                    permissionsToRequest.add(permission);
                }
            }

            if(permissionsToRequest.size() > 0) {
                permissionRequestLauncher.launch(permissionsToRequest.toArray(new String[]{}));
            } else {
                if(this.successListener != null) {
                    this.successListener.onFinished();
                    this.successListener = null;
                }
            }
        }
    }
}
