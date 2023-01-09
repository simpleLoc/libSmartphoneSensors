package de.fhws.indoor.libsmartphonesensors.sensors;

import android.app.Activity;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

import java.util.Arrays;

import de.fhws.indoor.libsmartphonesensors.ASensor;
import de.fhws.indoor.libsmartphonesensors.SensorDataInterface;
import de.fhws.indoor.libsmartphonesensors.SensorType;
import de.fhws.indoor.libsmartphonesensors.math.Vec3;

/**
 * Virtual sensor, trying to detect steps, yielding the start- and end timestamp of a detected step.
 * @author Markus Ebner
 */
public class StepDetector extends ASensor implements SensorEventListener {

    private static final long SENSOR_SECOND = 1000000000;
    ///
    /// \brief SENSOR_HZ Frequency with which the sensor should be sampled
    ///
    private static final long SENSOR_HZ = 50;
    ///
    /// \brief THRESHOLD_LEARN_RATE Learning rate that is used to adapt the current thresholds (upper/lower).
    /// \details The higher this is, the faster the algorithm can adapt to changes in step-intensity.
    ///
    private static final double THRESHOLD_LEARN_RATE = 0.1;
    ///
    /// \brief MIN_LOWER_THRESHOLD Lower bound for the automatically adjusted lower threshold.
    /// \details lowerThreshold may only be LOWER than this value.
    ///
    private static final double MIN_LOWER_THRESHOLD = -1;
    ///
    /// \brief MIN_UPPER_THRESHOLD Lower bound for the automatically adjusted lower threshold.
    /// \details upperThreshold may onle be HIGHER than this value.
    ///
    private static final double MIN_UPPER_THRESHOLD = 1.25;
    ///
    /// \brief VARIANCE_THRESHOLD Threshold for the variance, to detect whether there currently is movement or not.
    /// \details If the variance is not high enough, step detection will be paused and no adjustments will be done.
    ///
    private static final double VARIANCE_THRESHOLD = 0.5;
    ///
    /// \brief STEP_MAX_LENGTH Maximum length a step region can take.
    ///
    private static final long STEP_MAX_LENGTH_NS = (3 * SENSOR_SECOND) / 2; //  1.5 * SENSOR_SECOND;

    private static final long SENSOR_SAMPLE_INTERVAL_NS = (SENSOR_SECOND / SENSOR_HZ);
    private static final long SENSOR_SAMPLE_INTERVAL_US = SENSOR_SAMPLE_INTERVAL_NS / 1000;

    private SensorManager sensorManager;
    private Sensor gravitySensor;
    private Sensor accelerometerSensor;
    private DoubleHysteresisStepDetector stepDetector = new DoubleHysteresisStepDetector();
    private long recordingStartTimestamp = 0;

    public StepDetector(SensorDataInterface sensorDataInterface, Activity activity) {
        super(sensorDataInterface);
        this.sensorManager = (SensorManager) activity.getSystemService(Context.SENSOR_SERVICE);
        this.gravitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);
        this.accelerometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
    }

    // ###########
    // # DETECTION
    // ###########
    static class CircularBuffer {
        private double buffer[];
        private int headPtr = 0;
        public int length;

        public CircularBuffer(int length) {
            this.length = length;
            this.buffer = new double[length];
        }
        public void insert(double value) {
            buffer[headPtr] = value;
            headPtr = (headPtr + 1) % length;
        }

        /**
         * Get element from buffer, oldest (idx = 0) to newest (idx = length)
         */
        public double get(int idx) { return buffer[(headPtr + idx) % length]; }
        public double getRaw(int idx) { return buffer[idx]; }
        public double getOldest() { return getRaw(headPtr); }
        public double[] getBufferAsSorted() {
            double[] result = buffer.clone();
            Arrays.sort(result);
            return result;
        }
    }
    static class MovementBuffer {
        private static final long SYNC_TICKS = 10000;
        private CircularBuffer buffer;
        long tick = 0;
        double sum = 0;
        double qSum = 0;

        public MovementBuffer(int length) {
            this.buffer = new CircularBuffer(length);
        }
        public void insert(double value) {
            // avoid drifting completely, by synchronizing the incremental calculation every now and then
            if(++tick > SYNC_TICKS) {
                sum = value;
                qSum = (value * value);
                for(int i = 1; i < buffer.length; ++i) { // skip oldest element, because this is about to be replaced!
                    double tmp = buffer.get(i);
                    sum += tmp;
                    qSum += (tmp * tmp);
                }
                tick = 0;
            } else {
                sum = sum - buffer.getOldest() + value;
                qSum = qSum - (buffer.getOldest() * buffer.getOldest()) + (value * value);
            }
            buffer.insert(value);
        }
        public double variance() {
            double E1 = qSum / (double) buffer.length;
            double E2 = sum / (double) buffer.length;
            return E1 - (E2*E2);
        }
        public double[] percentiles(int[] percents) {
            double[] result = new double[percents.length];
            double[] sorted = buffer.getBufferAsSorted();
            //TODO: at least, use linear interpolation
            for(int i = 0; i < percents.length; ++i) {
                int idx = (int)(buffer.length * ((float)percents[i] / 100.0f));
                result[i] = sorted[idx];
            }
            return result;
        }
    };
    class DoubleHysteresisStepDetector {
        double lowerThreshold = MIN_LOWER_THRESHOLD;
        double upperThreshold = MIN_UPPER_THRESHOLD;
        double prevAccelV = lowerThreshold;
        double gradient = 0.0;
        MovementBuffer stBuffer = new MovementBuffer(30);
        MovementBuffer ltBuffer = new MovementBuffer(100);
        // current detection
        Long lowerRegionStart = null;
        Long upperRegionStart = null;
        Long upperRegionEnd = null;

        public void update(long timestamp, Vec3 accel, Vec3 gravity) {
            Vec3 rawAccel = Vec3.sub(accel, gravity);
            gravity.normalize();
            double accelV = rawAccel.dot(gravity);

            stBuffer.insert(accelV);
            ltBuffer.insert(accelV);
            gradient = accelV - prevAccelV;
            double currentVariance = stBuffer.variance();

            // only enter on raising edge
            if(currentVariance > VARIANCE_THRESHOLD && Math.min(accelV, prevAccelV) < lowerThreshold && gradient > 0) {
                if(lowerRegionStart == null) { lowerRegionStart = timestamp; }
            }
            if(lowerRegionStart != null) {
                if(upperRegionStart != null && accelV > lowerThreshold) {
                    // narrow down region to last sample that fit below lowerThreshold, before an upper region is found
                    lowerRegionStart += 1;
                }
                if(accelV > upperThreshold) {
                    // start an upper region, if not already running
                    if(upperRegionStart == null) { upperRegionStart = timestamp; }
                } else {
                    // this is the end of our upper region
                    if(upperRegionStart != null) { upperRegionEnd = timestamp; }
                }
                if(accelV < lowerThreshold && upperRegionStart != null) {
                    // We are below lowerThreshold again, and have an upper region -> found a complete step region
                    long centerTimestamp = (timestamp + lowerRegionStart) / 2;

                    // detected step, take start and end timestamp, and send StepDetector event
                    sensorDataInterface.onData(timestamp, SensorType.STEP_DETECTOR,
                            (lowerRegionStart - recordingStartTimestamp) + ";" + (timestamp - recordingStartTimestamp) + ";1.0"
                    );
                    lowerRegionStart = null;
                    upperRegionStart = null;
                    upperRegionEnd = null;
                }
            }
            if(lowerRegionStart != null && (timestamp - lowerRegionStart) > STEP_MAX_LENGTH_NS) {
                // if our step region already is too long, move lower region start forward until it reaches the upper
                // if it reaches upper, abort region
                lowerRegionStart += SENSOR_SAMPLE_INTERVAL_NS;
                if(upperRegionStart != null && lowerRegionStart >= upperRegionStart) {
                    // took too long, abort region (way longer than a normal step)
                    lowerRegionStart = null;
                    upperRegionStart = null;
                    upperRegionEnd = null;
                }
            }

            // as long as we have enough short-term variance, try to dynamically adapt
            // thresholds using the long-term buffer
            if(currentVariance > VARIANCE_THRESHOLD) {
                double[] newThresholds = ltBuffer.percentiles(new int[]{25, 75});
                // adapt using learn-rate
                lowerThreshold = lowerThreshold + THRESHOLD_LEARN_RATE * (newThresholds[0] - lowerThreshold);
                upperThreshold = upperThreshold + THRESHOLD_LEARN_RATE * (newThresholds[1] - upperThreshold);
                // apply fixed bounds
                lowerThreshold = Math.min(lowerThreshold, MIN_LOWER_THRESHOLD);
                upperThreshold = Math.max(upperThreshold, MIN_UPPER_THRESHOLD);
            }

            prevAccelV = accelV;
        }
    }

    // ###########
    // # EVENTS
    // ###########
    Vec3 lastAccel = new Vec3();
    Vec3 lastGravity = new Vec3();

    @Override
    public void onSensorChanged(SensorEvent event) {
        if(event.sensor == this.accelerometerSensor) {
            lastAccel.set(event.values[0], event.values[1], event.values[2]);
        } else if(event.sensor == this.gravitySensor) {
            lastGravity.set(event.values[0], event.values[1], event.values[2]);
            stepDetector.update(event.timestamp, lastAccel, lastGravity);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    @Override
    public void onResume(Activity act) {
        this.stepDetector = new DoubleHysteresisStepDetector();
        this.sensorManager.registerListener(this, accelerometerSensor, SensorManager.SENSOR_DELAY_FASTEST);
        this.sensorManager.registerListener(this, gravitySensor, SensorManager.SENSOR_DELAY_FASTEST);
        recordingStartTimestamp = sensorDataInterface.getStartTimestamp();
    }

    @Override
    public void onPause(Activity act) {
        // detach from all events
        sensorManager.unregisterListener(this);
    }
}