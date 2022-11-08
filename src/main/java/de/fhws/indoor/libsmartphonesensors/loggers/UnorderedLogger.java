package de.fhws.indoor.libsmartphonesensors.loggers;

import android.app.Activity;
import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.ArrayBlockingQueue;

/**
 * Live (unordered) Logger.
 * <p>
 *     This logger takes the incomming events and commits them to the logfile in a background thread.
 *
 *     WARNING: This produces files with non-monotonic increasing timestamps.
 *     Reordering is required on the parser-side, or as post-processing step.
 * </p>
 * @author Frank Ebner
 * @author Markus Ebner
 */
public final class UnorderedLogger extends Logger {

    private static final int LINE_BUFFER_SIZE = 5000;

    private volatile boolean addingStopped = false; // Just to be sure
    private ArrayBlockingQueue<LogEntry> lineBuffer = new ArrayBlockingQueue<>(LINE_BUFFER_SIZE);
    private WriteBackWorker writeBackWorker;

    public UnorderedLogger(Context context) {
        super(context);
    }

    @Override
    protected void onStart() {
        writeBackWorker = new WriteBackWorker();
        writeBackWorker.start();
    }

    @Override
    protected void onStop() {
        addingStopped = true;
        try {
            writeBackWorker.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void log(LogEntry logEntry) {
        lineBuffer.add(logEntry);
    }

    @Override
    public long getEntriesCached() {
        return lineBuffer.size();
    }

    @Override
    public float getCacheLevel() {
        return 1.0f - (float)lineBuffer.remainingCapacity() / (float)LINE_BUFFER_SIZE;
    }

    private class WriteBackWorker extends Thread {

        public WriteBackWorker() {
            setName("WriteBackWorker");
            setPriority(Thread.MIN_PRIORITY);
        }

        @Override
        public void run() {
            try {
                OutputStream outputStream = recordingSession.stream();
                while (true) {
                    LogEntry entry = lineBuffer.poll();
                    if (entry == null) {
                        if (addingStopped) { // Queue empty, recording stopped. exit
                            return;
                        } else { // Currently no line in queue, wait 10 ms
                            Thread.sleep(10);
                        }
                    } else { // print log line
                        outputStream.write(entry.csv.getBytes());
                    }
                }
            } catch(InterruptedException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

}
