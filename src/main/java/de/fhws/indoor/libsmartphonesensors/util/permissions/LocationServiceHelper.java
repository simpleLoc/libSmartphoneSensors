package de.fhws.indoor.libsmartphonesensors.util.permissions;

import android.app.Activity;
import android.content.Context;
import android.location.LocationManager;

public class LocationServiceHelper {

    public static boolean isEnabled(Activity activity) {
        LocationManager lm = (LocationManager)activity.getSystemService(Context.LOCATION_SERVICE);
        try {
            if(lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) { return true; }
        } catch(Exception ex) {}
        try {
            if(lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) { return true; }
        } catch(Exception ex) {}
        return false;
    }

}
