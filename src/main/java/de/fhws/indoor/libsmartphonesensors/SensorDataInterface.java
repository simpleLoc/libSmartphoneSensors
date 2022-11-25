package de.fhws.indoor.libsmartphonesensors;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Interface used to handle the communication channel between sensor and the
 * consumer of sensor data (in other words: the SensorDataInterface provider).
 */
public interface SensorDataInterface {

    /**
     * Called by Sensor to notify SensorDataInterface provider about new timestamped event
     * in the SensorReadout csv format.
     * @param timestamp Event timestamp
     * @param id Event identifier (see SensorType)
     * @param csv csv data to append
     */
    void onData(final long timestamp, final SensorType id, final String csv);

    /**
     * Request an auxiliary data channel from the SensorDataInterface provider.
     * @param id Identifying string for this auxiliary channel.
     * @return A writeable OutputStream that the requesting sensor can write
     * data at will. It's the provider's responsibility to close this channel!
     */
    OutputStream requestAuxiliaryChannel(String id) throws UnsupportedOperationException, IOException;

}
