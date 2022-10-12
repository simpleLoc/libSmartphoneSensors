package de.fhws.indoor.libsmartphonesensors.loggers;

import android.widget.Toast;

public class LoggerException extends RuntimeException {
    public LoggerException(final String err, final Throwable t) {
        super(err, t);
    }

    public LoggerException(final String err) {
        super(err);
    }
}
