package de.fhws.indoor.libsmartphonesensors.util;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.provider.Settings;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.HashSet;

import de.fhws.indoor.libsmartphonesensors.R;

public class MultiPermissionRequester {

    private final AppCompatActivity activity;
    private final HashSet<String> permissions = new HashSet<>();
    private boolean shouldRequestLocationService = false;
    private int currentIdx = 0;

    private final ActivityResultContracts.RequestPermission requestPermissionContract;
    private ActivityResultLauncher<String> permissionRequestLauncher;
    private SuccessListener successListener;
    
    public static interface SuccessListener {
        void onFinished();
    }

    public MultiPermissionRequester(AppCompatActivity activity) {
        this.activity = activity;
        this.requestPermissionContract = new ActivityResultContracts.RequestPermission();
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

        ArrayList<String> permsToRequest = new ArrayList<>();
        for(String permission : permissions) {
            if(ContextCompat.checkSelfPermission(activity, permission) != PackageManager.PERMISSION_GRANTED) {
                permsToRequest.add(permission);
            }
        }

        if(permsToRequest.size() > 0) {
            permissionRequestLauncher = activity.registerForActivityResult(requestPermissionContract, granted -> {
                activity.runOnUiThread(() -> {
                    if(granted == false) { // try again
                        permissionRequestLauncher.launch(permsToRequest.get(currentIdx));
                    } else {
                        currentIdx += 1;
                        if(permsToRequest.size() == currentIdx) {
                            if(successListener != null) {
                                successListener.onFinished();
                            }
                            return;
                        }
                        permissionRequestLauncher.launch(permsToRequest.get(currentIdx));
                    }
                });
            });
            permissionRequestLauncher.launch(permsToRequest.get(currentIdx));
        } else {
            if(successListener != null) {
                successListener.onFinished();
            }
        }
    }

}
