package de.fhws.indoor.libsmartphonesensors.loggers;

import android.app.Activity;
import android.content.Context;
import android.os.SystemClock;

import androidx.annotation.NonNull;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import de.fhws.indoor.libsmartphonesensors.SensorType;
import de.fhws.indoor.libsmartphonesensors.io.RecordingSession;

/**
 * Base-Class for all Logger implementations.
 * <p>
 *     This class defines the public interface of all logging implementations.
 * </p>
 * @author Markus Ebner
 */
public abstract class Logger {

    public static final long BEGINNING_TS = -1;

    // work data
    protected Context context;
    protected RecordingSession recordingSession;

    //statistics
    private AtomicLong statEntryCnt = new AtomicLong(0);
    private AtomicLong statSizeTotal = new AtomicLong(0);
    private AtomicBoolean isRunning = new AtomicBoolean(false);

    public Logger(Context context) {
        this.context = context;
    }


    public final void start(RecordingSession recordingSession, FileMetadata metadata) {
        this.recordingSession = recordingSession;
        statEntryCnt.set(0);
        statSizeTotal.set(0);
        isRunning.set(true);
        onStart();

        // commit metadata
        addCSV(SensorType.FILE_METADATA, BEGINNING_TS, metadata.toCsv());
        addCSV(SensorType.RECORDING_ID, BEGINNING_TS, recordingSession.getRecordingId().toString());
    }
    protected abstract void onStart();

    public final void stop() {
        isRunning.set(false);
        onStop();
    }
    protected abstract void onStop();

    protected abstract void log(LogEntry logEntry);

    public final long getStartTS() {
        return recordingSession.getStartTs();
    }
    public final long getSizeTotal() { return statSizeTotal.get(); }
    public final long getEventCnt() { return statEntryCnt.get(); }
    public abstract long getEntriesCached();
    public abstract float getCacheLevel();

    public final String getName() {
        if(recordingSession != null && recordingSession.isOpen()) {
            return recordingSession.getName();
        }
        return "-";
    }

    /** add a new CSV entry for the given sensor number to the internal buffer */
    public final void addCSV(final SensorType sensorNr, final long timestamp, final String csv) {
        if(isRunning.get() == false) { return; }
        final long relTS = (timestamp == Logger.BEGINNING_TS) ? 0 : (timestamp - getStartTS());
        if (relTS >= 0) { // drop pre startTS logs (at the beginning, sensors sometimes deliver old values)
            String line = String.format("%d;%d;%s\n", relTS, sensorNr.id(), csv);
            log(new LogEntry(relTS, line));
            statEntryCnt.incrementAndGet();
            statSizeTotal.addAndGet(line.length());
        }
    }

    public static class LogEntry implements Comparable<LogEntry> {
        public long timestamp;
        public String csv;

        public LogEntry(long timestamp, String csv) {
            this.timestamp = timestamp;
            this.csv = csv;
        }

        @Override
        public int compareTo(@NonNull LogEntry another) {
            return Long.compare(timestamp, another.timestamp);
        }
    }

    public static class FileMetadata {
        private String person;
        private String comment;
        private Date dateTime;

        public FileMetadata(String person, String comment) {
            this(person, comment, new Date(System.currentTimeMillis()));
        }
        public FileMetadata(String person, String comment, Date dateTime) {
            this.person = person;
            this.comment = comment;
            this.dateTime = dateTime;
        }

        protected String toCsv() {
            //TODO: refactor this hack as soon as minAPILevel is raised:
            // - above 24 -> SimpleDateFormatter with "yyyy-MM-dd'T'HH:mm:ss.SSSX" instead of 'Z' append hack
            // - above 26 -> Use of the Instant class, Instant.now()
            SimpleDateFormat dateTimeFormatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
            dateTimeFormatter.setTimeZone(TimeZone.getTimeZone("UTC"));
            String utcDateTime = dateTimeFormatter.format(dateTime);
            return utcDateTime + ";" + person + ";" + comment;
        }
    }

}
