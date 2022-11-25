package de.fhws.indoor.libsmartphonesensors.sensors;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import androidx.core.content.ContextCompat;

import de.fhws.indoor.libsmartphonesensors.ASensor;
import de.fhws.indoor.libsmartphonesensors.SensorDataInterface;
import de.fhws.indoor.libsmartphonesensors.SensorType;

/**
 * GPS sensor.
 */
@TargetApi(23)
public class Gps extends ASensor implements LocationListener {

    private Activity act;
    private LocationManager locationManager;
    private Location location;

    public Gps(SensorDataInterface sensorDataInterface, Activity act) throws Exception {
        super(sensorDataInterface);
        this.act = act;
        initGPS();

    }

    private void initGPS() throws Exception {
        if ( Build.VERSION.SDK_INT >= 23 &&
                ContextCompat.checkSelfPermission( act, android.Manifest.permission.ACCESS_FINE_LOCATION ) != PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission( act, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return  ;
        }

        try   {
            this.locationManager = (LocationManager) act.getSystemService(Context.LOCATION_SERVICE);

            // Get GPS
            boolean isGPSEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);

            if (isGPSEnabled)  {

                //get the most accurate provider
                Criteria criteria = new Criteria();
                criteria.setAccuracy(Criteria.ACCURACY_FINE);
                String provider = locationManager.getBestProvider(criteria, true);

                //use only gps and not network 0 and 0 for fastest updates possible
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,
                        0,
                        0, this);

                if (locationManager != null)  {
                    location = locationManager.getLastKnownLocation(provider);
                    setMostRecentLocation(location);
                }
            }
        } catch (Exception ex)  {
            throw new Exception("error creating gps!");

        }
    }

    private void setMostRecentLocation(Location loc){
        this.location = loc;
    }

    @Override
    public void onResume(Activity act) {
        try {
            initGPS();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onPause(Activity act) {
        locationManager.removeUpdates(this);
    }

    @Override
    public void onLocationChanged(Location location) {
        this.location = location;

        // inform listeners
        if (sensorDataInterface != null){
            sensorDataInterface.onData(location.getElapsedRealtimeNanos(), SensorType.GRAVITY,
                    Double.toString(location.getLatitude()) + ";" +
                    Double.toString(location.getLongitude()) + ";" +
                    Double.toString(location.getAltitude()) + ";" +
                    Double.toString(location.getBearing())
            );
        }
    }

    @Override
    public void onStatusChanged(String s, int i, Bundle bundle) {

    }

    @Override
    public void onProviderEnabled(String s) {

    }

    @Override
    public void onProviderDisabled(String s) {

    }
}
