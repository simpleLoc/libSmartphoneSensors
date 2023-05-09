package de.fhws.indoor.libsmartphonesensors.util;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Application;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.Lifecycle;

import java.util.ArrayList;
import java.util.HashSet;

import de.fhws.indoor.libsmartphonesensors.R;

public class MultiPermissionRequester {

    private static final ArrayList<String> permsToRequest = new ArrayList<>();

    private final AppCompatActivity activity;
    private final HashSet<String> permissions = new HashSet<>();
    private boolean shouldRequestLocationService = false;

    private final ActivityResultContracts.RequestPermission requestPermissionContract;
    private ActivityResultLauncher<String> permissionRequestLauncher;
    private SuccessListener successListener;
    
    public static interface SuccessListener {
        void onFinished();
    }

    public MultiPermissionRequester(AppCompatActivity activity) {
        this.activity = activity;
        if(activity.getLifecycle().getCurrentState() != Lifecycle.State.INITIALIZED) {
            throw new RuntimeException("MultiPermissionRequester has to be instantiated before onCreate()");
        }
        this.requestPermissionContract = new ActivityResultContracts.RequestPermission();
        this.permissionRequestLauncher = activity.registerForActivityResult(requestPermissionContract, granted -> {
            activity.runOnUiThread(() -> {
                if(granted == false) { // try again
                    permissionRequestLauncher.launch(permsToRequest.get(0));
                } else {
                    if(permsToRequest.size() > 0) { permsToRequest.remove(0); }
                    if(permsToRequest.size() == 0) {
                        if(successListener != null) {
                            successListener.onFinished();
                        }
                        return;
                    }
                    permissionRequestLauncher.launch(permsToRequest.get(0));
                }
            });
        });
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            activity.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
                @Override public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {}
                @Override public void onActivityStarted(@NonNull Activity activity) {}
                @Override public void onActivityResumed(@NonNull Activity activity) {}
                @Override public void onActivityPaused(@NonNull Activity activity) {}
                @Override public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {}
                @Override public void onActivityDestroyed(@NonNull Activity activity) {}

                @Override
                public void onActivityStopped(@NonNull Activity activity) {
                    if(permissionRequestLauncher != null) {
                        permissionRequestLauncher.unregister();
                        permissionRequestLauncher = null;
                    }
                }
            });
        }
    }

    public void setSuccessListener(SuccessListener successListener) {
        this.successListener = successListener;
    }
    
    public void requestLocationService() {
        shouldRequestLocationService = true;
    }

    public void add(String permission) {
        this.permissions.add(permission);
    }

    public void launch() {
        if(shouldRequestLocationService) {
            shouldRequestLocationService = false;
            new AlertDialog.Builder(activity)
                    .setMessage(R.string.gps_not_enabled)
                    .setCancelable(false)
                    .setPositiveButton(R.string.open_location_settings, (paramDialogInterface, paramInt) -> activity.startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)))
                    .show();
            return;
        }

        permsToRequest.clear();
        for(String permission : permissions) {
            if(ContextCompat.checkSelfPermission(activity, permission) != PackageManager.PERMISSION_GRANTED) {
                permsToRequest.add(permission);
            }
        }

        if(permsToRequest.size() > 0) {
            permissionRequestLauncher.launch(permsToRequest.get(0));
        } else {
            if(successListener != null) {
                successListener.onFinished();
            }
        }
    }

}
