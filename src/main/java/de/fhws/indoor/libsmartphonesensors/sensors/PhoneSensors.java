package de.fhws.indoor.libsmartphonesensors.sensors;

import android.app.Activity;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import de.fhws.indoor.libsmartphonesensors.ASensor;
import de.fhws.indoor.libsmartphonesensors.SensorDataInterface;
import de.fhws.indoor.libsmartphonesensors.SensorType;
import de.fhws.indoor.libsmartphonesensors.VendorInformation;

/**
 * Sensor that surfaces all Sensors a phone has.
 * <p>
 *     While the term "sensor" is used for all implementations, such as Wifi and iBeacon, the term
 *     "sensor" in this context actually refers to a smartphone's sensors, such as
 *     Accelerometer, Gyroscope, MagneticField, Light, Pressure, ...
 *
 *     This Sensor implementation exports all sensors supported by the smartphone.
 * </p>
 *
 * Created by Toni on 25.03.2015.
 */
public class PhoneSensors extends ASensor implements SensorEventListener {

	//private static final int SENSOR_TYPE_HEARTRATE = 65562;

    private SensorManager sensorManager;
    private Sensor acc;
    private Sensor grav;
   	private Sensor lin_acc;
    private Sensor gyro;
    private Sensor magnet;
    private Sensor press;
	private Sensor ori;
	//private Sensor heart;
	private Sensor humidity;
	private Sensor rotationVector;
	private Sensor light;
	private Sensor temperature;
	private Sensor gameRotationVector;

	/** local gravity copy (needed for orientation matrix) */
    private float[] mGravity = new float[3];
	/** local geomagnetic copy (needed for orientation matrix) */
    private float[] mGeomagnetic = new float[3];


	/** ctor */
    public PhoneSensors(SensorDataInterface sensorDataInterface, final Activity act){
		super(sensorDataInterface);
		// fetch the sensor manager from the activity
        sensorManager = (SensorManager) act.getSystemService(Context.SENSOR_SERVICE);

		// try to get each sensor
        acc = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        grav = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);
        lin_acc = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        gyro = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        magnet = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        press = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE);
		ori = sensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION);
		//heart = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE);
		humidity = sensorManager.getDefaultSensor(Sensor.TYPE_RELATIVE_HUMIDITY);
		rotationVector = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
		light = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
		temperature = sensorManager.getDefaultSensor(Sensor.TYPE_AMBIENT_TEMPERATURE);
		gameRotationVector = sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR);
	}

    @Override
    public void onSensorChanged(SensorEvent event) {
		if(sensorDataInterface == null) { return; }
		// to compare with the other orientation
		if(event.sensor.getType() == Sensor.TYPE_ORIENTATION) {
			// inform listeners
			sensorDataInterface.onData(event.timestamp, SensorType.ORIENTATION_OLD,
				Float.toString(event.values[0]) + ";" +
				Float.toString(event.values[1]) + ";" +
				Float.toString(event.values[2])
			);
		}
//		else if(event.sensor.getType() == Sensor.TYPE_HEART_RATE) {
//
//			// inform listeners
//			if (listener != null){
//				listener.onData(SensorType.HEART_RATE, event.timestamp,
//						Float.toString(event.values[0])
//				);
//			}
//
//		}
		else if(event.sensor.getType() == Sensor.TYPE_LIGHT) {
			// inform listeners
			sensorDataInterface.onData(event.timestamp, SensorType.LIGHT,
					Float.toString(event.values[0])
			);
		} else if(event.sensor.getType() == Sensor.TYPE_AMBIENT_TEMPERATURE) {
			// inform listeners
			sensorDataInterface.onData(event.timestamp, SensorType.AMBIENT_TEMPERATURE,
					Float.toString(event.values[0])
			);
		} else if(event.sensor.getType() == Sensor.TYPE_RELATIVE_HUMIDITY) {
			// inform listeners
			sensorDataInterface.onData(event.timestamp, SensorType.RELATIVE_HUMIDITY,
					Float.toString(event.values[0])
			);
		} else if(event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
			// inform listeners
			if(event.values.length > 3){
				sensorDataInterface.onData(event.timestamp, SensorType.ROTATION_VECTOR,
						Float.toString(event.values[0]) + ";" +
						Float.toString(event.values[1]) + ";" +
						Float.toString(event.values[2]) + ";" +
						Float.toString(event.values[3])
				);
			} else {
				sensorDataInterface.onData(event.timestamp, SensorType.ROTATION_VECTOR,
						Float.toString(event.values[0]) + ";" +
						Float.toString(event.values[1]) + ";" +
						Float.toString(event.values[2])
				);
			}
		} else if(event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
			// inform listeners
			sensorDataInterface.onData(event.timestamp, SensorType.GYROSCOPE,
				Float.toString(event.values[0]) + ";" +
				Float.toString(event.values[1]) + ";" +
				Float.toString(event.values[2])
			);
		} else if(event.sensor.getType() == Sensor.TYPE_PRESSURE) {
			// inform listeners
			sensorDataInterface.onData(event.timestamp, SensorType.PRESSURE,
				Float.toString(event.values[0])
			);
		} else if(event.sensor.getType() == Sensor.TYPE_LINEAR_ACCELERATION) {
			// inform listeners
			sensorDataInterface.onData(event.timestamp, SensorType.LINEAR_ACCELERATION,
				Float.toString(event.values[0]) + ";" +
				Float.toString(event.values[1]) + ";" +
				Float.toString(event.values[2])
			);
		} else if(event.sensor.getType() == Sensor.TYPE_GRAVITY) {
			// inform listeners
			sensorDataInterface.onData(event.timestamp, SensorType.GRAVITY,
					Float.toString(event.values[0]) + ";" +
					Float.toString(event.values[1]) + ";" +
					Float.toString(event.values[2])
			);
        } else if(event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
			// inform listeners
			sensorDataInterface.onData(event.timestamp, SensorType.ACCELEROMETER,
				Float.toString(event.values[0]) + ";" +
				Float.toString(event.values[1]) + ";" +
				Float.toString(event.values[2])
			);
			// keep a local copy (needed for orientation matrix)
			System.arraycopy(event.values, 0, mGravity, 0, 3);

			// NOTE:
			// @see TYPE_MAGNETIC_FIELD
			//updateOrientation();
		} else if(event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
			// inform listeners
			sensorDataInterface.onData(event.timestamp, SensorType.MAGNETIC_FIELD,
					Float.toString(event.values[0]) + ";" +
					Float.toString(event.values[1]) + ";" +
					Float.toString(event.values[2])
			);
			// keep a local copy (needed for orientation matrix)
			System.arraycopy(event.values, 0, mGeomagnetic, 0, 3);

			// NOTE
			// @see TYPE_ACCELEROMETER
			// only MAG updates the current orientation as MAG is usually slower than ACC and this reduces the file-footprint
			updateOrientation(event.timestamp);
        } else if(event.sensor.getType() == Sensor.TYPE_GAME_ROTATION_VECTOR) {
        	// inform listeners
			sensorDataInterface.onData(event.timestamp, SensorType.GAME_ROTATION_VECTOR,
					Float.toString(event.values[0]) + ";" +
							Float.toString(event.values[1]) + ";" +
							Float.toString(event.values[2])
			);
		}
    }

	/** calculate orientation from acc and mag */
	private void updateOrientation(long timestamp) {

		// skip orientation update if either grav or geo is missing
		if (mGravity == null) {return;}
		if (mGeomagnetic == null) {return;}

		// calculate rotationMatrix and orientation
		// see: https://developer.android.com/reference/android/hardware/SensorManager#getRotationMatrix(float[],%20float[],%20float[],%20float[])
		// these are row-major
		float R[] = new float[9];
		float I[] = new float[9];

		// derive rotation matrix from grav and geo sensors
		boolean success = SensorManager.getRotationMatrix(R, I, mGravity, mGeomagnetic);
		if (!success) {return;}

		// derive orientation-vector using the rotation matrix
		float orientationNew[] = new float[3];
		SensorManager.getOrientation(R, orientationNew);

		// inform listeners
		if (sensorDataInterface != null) {

			// orientation vector
			sensorDataInterface.onData(timestamp, SensorType.ORIENTATION_NEW,
				Float.toString(orientationNew[0]) + ";" +
				Float.toString(orientationNew[1]) + ";" +
				Float.toString(orientationNew[2])
			);

			// rotation matrix
			final StringBuilder sb = new StringBuilder(1024);
			sb.append(R[0]).append(';');
			sb.append(R[1]).append(';');
			sb.append(R[2]).append(';');
			sb.append(R[3]).append(';');
			sb.append(R[4]).append(';');
			sb.append(R[5]).append(';');
			sb.append(R[6]).append(';');
			sb.append(R[7]).append(';');
			sb.append(R[8]);

			//Write the whole rotationMatrix R into the Listener.
			sensorDataInterface.onData(timestamp, SensorType.ROTATION_MATRIX, sb.toString());

//				Float.toString(R[0]) + ";" +
//				Float.toString(R[1]) + ";" +
//				Float.toString(R[2]) + ";" +
//				Float.toString(R[3]) + ";" +
//				Float.toString(R[4]) + ";" +
//				Float.toString(R[5]) + ";" +
//				Float.toString(R[6]) + ";" +
//				Float.toString(R[7]) + ";" +
//				Float.toString(R[8])
//				Float.toString(R[9]) + ";" +
//				Float.toString(R[10]) + ";" +
//				Float.toString(R[11]) + ";" +
//				Float.toString(R[12]) + ";" +
//				Float.toString(R[13]) + ";" +
//				Float.toString(R[14]) + ";" +
//				Float.toString(R[15])
//			);

		}

	}

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
		// nothing to-do here
    }

    @Override
    public void onResume(final Activity act) {
		// attach as listener to each of the available sensors
        registerIfPresent(acc, SensorManager.SENSOR_DELAY_FASTEST);
      	registerIfPresent(grav, SensorManager.SENSOR_DELAY_FASTEST);
       	registerIfPresent(gyro, SensorManager.SENSOR_DELAY_FASTEST);
        registerIfPresent(lin_acc, SensorManager.SENSOR_DELAY_FASTEST);
        registerIfPresent(magnet, SensorManager.SENSOR_DELAY_FASTEST);
        registerIfPresent(press, SensorManager.SENSOR_DELAY_FASTEST);
		registerIfPresent(ori, SensorManager.SENSOR_DELAY_FASTEST);
		//registerIfPresent(heart, SensorManager.SENSOR_DELAY_FASTEST);
		registerIfPresent(humidity, SensorManager.SENSOR_DELAY_FASTEST);
		registerIfPresent(rotationVector, SensorManager.SENSOR_DELAY_FASTEST);
		registerIfPresent(light, SensorManager.SENSOR_DELAY_FASTEST);
		registerIfPresent(temperature, SensorManager.SENSOR_DELAY_FASTEST);
		registerIfPresent(gameRotationVector, SensorManager.SENSOR_DELAY_FASTEST);
    }

	private void registerIfPresent(final Sensor sens, final int delay) {
		if (sens != null) {
			sensorManager.registerListener(this, sens, delay);
			Log.d("PhoneSensors", "added sensor " + sens.toString());
		} else {
			Log.d("PhoneSensors", "sensor not present. skipping");
		}
	}

    @Override
    public void onPause(final Activity act) {
		// detach from all events
		sensorManager.unregisterListener(this);
    }




	public static void dumpVendorInformation(SensorManager sensorManager, VendorInformation vendorInformation) {
		dumpSensorInformation(vendorInformation.addSensor("Accelerometer"), sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER));
		dumpSensorInformation(vendorInformation.addSensor("Gravity"), sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY));
		dumpSensorInformation(vendorInformation.addSensor("LinearAcceleration"), sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION));
		dumpSensorInformation(vendorInformation.addSensor("Gyroscope"), sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE));
		dumpSensorInformation(vendorInformation.addSensor("Magnetometer"), sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD));
		dumpSensorInformation(vendorInformation.addSensor("Pressure"), sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE));
		dumpSensorInformation(vendorInformation.addSensor("RelativeHumidity"), sensorManager.getDefaultSensor(Sensor.TYPE_RELATIVE_HUMIDITY));
		dumpSensorInformation(vendorInformation.addSensor("OrientationOld"), sensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION));
		dumpSensorInformation(vendorInformation.addSensor("Light"), sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT));
		dumpSensorInformation(vendorInformation.addSensor("AmbientTemperature"), sensorManager.getDefaultSensor(Sensor.TYPE_AMBIENT_TEMPERATURE));
		//dumpSensorInformation(vendorInformation.addSensor("HeartRate"), sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE));
		dumpSensorInformation(vendorInformation.addSensor("GameRotationVector"), sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR));
	}

	private static void dumpSensorInformation(final VendorInformation.InformationStructure sensorInfo, final Sensor sensor) {
		sensorInfo.set("Available", (sensor != null));
		if(sensor == null) { return; }
		sensorInfo.set("TypeId", sensor.getType());
		sensorInfo.set("Type", sensor.getStringType());

		sensorInfo.set("Available", "true");
		sensorInfo.set("Vendor", sensor.getVendor());
		sensorInfo.set("Name", sensor.getName());
		sensorInfo.set("Version", sensor.getVersion());
		sensorInfo.set("MinDelay", sensor.getMinDelay());
		sensorInfo.set("MaxDelay", sensor.getMaxDelay());
		sensorInfo.set("MaxRange", sensor.getMaximumRange());
		sensorInfo.set("Power", sensor.getPower());
		sensorInfo.set("ReportingMode", sensor.getReportingMode());
		sensorInfo.set("Resolution", sensor.getResolution());
		sensorInfo.set("Type", sensor.getType());
	}
}
