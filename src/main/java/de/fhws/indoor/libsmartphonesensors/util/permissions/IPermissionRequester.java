package de.fhws.indoor.libsmartphonesensors.util.permissions;

import android.app.Activity;

public interface IPermissionRequester {

    interface SuccessListener {
        void onFinished();
    }


    void requestLocationService();

    void add(String permission);

    void launch(SuccessListener successListener);

}
