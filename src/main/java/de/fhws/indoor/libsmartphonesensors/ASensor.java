package de.fhws.indoor.libsmartphonesensors;

import android.app.Activity;

/**
 * Base-class for all Sensors
 *
 * @author Frank Ebner
 * @author Markus Ebner
 */
public abstract class ASensor {

	/** listen for sensor events */
	public interface SensorListener {

		/** received data from the given sensor */
        public void onData(final SensorType id, final long timestamp, final String csv);

	}

	/** the listener to inform (if any) */
	protected SensorListener listener = null;


	/** start the sensor */
    public abstract void onResume(final Activity act);

	/** stop the sensor */
	public abstract void onPause(final Activity act);

	/** attach the given listener to the sensor */
	public void setListener(final SensorListener listener) {this.listener = listener;}

}
