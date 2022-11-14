package de.fhws.indoor.libsmartphonesensors.sensors;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks;
import com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

import java.text.DateFormat;
import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;

import static android.content.ContentValues.TAG;

import de.fhws.indoor.libsmartphonesensors.ASensor;
import de.fhws.indoor.libsmartphonesensors.SensorType;

/**
 * Created by student on 21.03.17.
 */

public class GpsNew extends ASensor implements ConnectionCallbacks, OnConnectionFailedListener, LocationListener {

    private AtomicBoolean running = new AtomicBoolean(false);
    protected GoogleApiClient mGoogleApiClient;
    protected Location mLastLocation;
    private Activity act;
    private Location mCurrentLocation;
    private LocationRequest mLocationRequest;

    protected String mLastUpdateTime;

    public GpsNew(Activity act) {
        this.act = act;
        this.mLastUpdateTime = "";

        buildGoogleApiClient();
    }

    /**
     * Builds a GoogleApiClient. Uses the addApi() method to request the LocationServices API.
     */
    protected synchronized void buildGoogleApiClient() {
        mGoogleApiClient = new GoogleApiClient.Builder(this.act)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();

        createLocationRequest();
        mGoogleApiClient.connect();
    }

    /**
     * Sets up the location request.
     */
    protected void createLocationRequest() {
        mLocationRequest = new LocationRequest();

        mLocationRequest.setInterval(1);
        mLocationRequest.setFastestInterval(1);

        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
    }

    /**
     * Requests location updates from the FusedLocationApi.
     */
    @SuppressLint("MissingPermission")
    protected void startLocationUpdates() {
        LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, this);
    }

    /**
     * Removes location updates from the FusedLocationApi.
     */
    protected void stopLocationUpdates() {
        // It is a good practice to remove location requests when the activity is in a paused or
        // stopped state. Doing so helps battery performance and is especially
        // recommended in applications that request frequent location updates.

        // The final argument to {@code requestLocationUpdates()} is a LocationListener
        // (http://developer.android.com/reference/com/google/android/gms/location/LocationListener.html).
        LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this);
    }

    @Override
    public void onResume(Activity act) {
        running.set(true);
        if (mGoogleApiClient.isConnected()) {
            startLocationUpdates();
        }
    }

    @Override
    public void onPause(Activity act) {
        running.set(false);
        if (mGoogleApiClient.isConnected()) {
            stopLocationUpdates();
        }
    }

    @SuppressLint("MissingPermission")
    @Override
    public void onConnected(@Nullable Bundle bundle) {
        Log.i(TAG, "Connected to GoogleApiClient");

        if (this.mCurrentLocation == null) {
            this.mCurrentLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
            mLastUpdateTime = DateFormat.getTimeInstance().format(new Date());
        }

        if (running.get()) {
            startLocationUpdates();
        }
    }

    @Override
    public void onConnectionSuspended(int i) {
        // The connection to Google Play services was lost for some reason. We call connect() to
        // attempt to re-establish the connection.
        Log.i(TAG, "Connection suspended");
        mGoogleApiClient.connect();
    }

    @Override
    public void onConnectionFailed(ConnectionResult result) {
        // Refer to the javadoc for ConnectionResult to see what error codes might be returned in
        // onConnectionFailed.
        Log.i(TAG, "Connection failed: ConnectionResult.getErrorCode() = " + result.getErrorCode());
    }

    @Override
    public void onLocationChanged(Location location) {
        if(running.get()) {
            this.mCurrentLocation = location;
            mLastUpdateTime = DateFormat.getTimeInstance().format(new Date());

            // inform listeners
            if (listener != null){
                listener.onData(SensorType.GPS, location.getElapsedRealtimeNanos(), //TODO: Is this correct? SystemClock.elapsedRealtimeNanos() otherwise..
                        Double.toString(location.getLatitude()) + ";" +
                                Double.toString(location.getLongitude()) + ";" +
                                Double.toString(location.getAltitude()) + ";" +
                                Double.toString(location.getBearing())
                );
            }
        }
    }
}
