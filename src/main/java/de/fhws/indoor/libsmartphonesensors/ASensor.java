package de.fhws.indoor.libsmartphonesensors;

import android.app.Activity;

/**
 * Base-class for all Sensors
 *
 * @author Frank Ebner
 * @author Markus Ebner
 */
public abstract class ASensor {

	protected SensorDataInterface sensorDataInterface = null;

	public ASensor(SensorDataInterface sensorDataInterface) {
		this.sensorDataInterface = sensorDataInterface;
	}

	/** start the sensor */
    public abstract void onResume(final Activity act);

	/** stop the sensor */
	public abstract void onPause(final Activity act);

}
