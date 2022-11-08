package de.fhws.indoor.libsmartphonesensors.loggers;

import android.content.Context;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Live (ordered) logger.
 * <p>
 *     This logger contains an internal caching structure that regularly sorts the contained entries
 *     and commits the oldest ones to the logfile using the correct order.
 *     This buffer determines the amount of items to store based on the time-window they represent,
 *     instead of a hard-coded amount of entries.
 * </p>
 * @author Markus Ebner
 */
public final class TimedOrderedLogger extends Logger {

    // If the entries in the ReorderBuffer represent at least a timespan of REORDER_TIMEFRAME_UPPER_NS,
    // a commit is scheduled, that sorts the buffer and commits all items older than REORDER_TIMEFRAME_LOWER_NS.
    private static final long REORDER_TIMEFRAME_UPPER_NS = 10L * 1000 * 1000 * 1000;
    private static final long REORDER_TIMEFRAME_LOWER_NS = 7L * 1000 * 1000 * 1000;

    // members
    private ReorderBuffer reorderBuffer;

    public TimedOrderedLogger(Context context) {
        super(context);
    }

    @Override
    protected final void onStart() {
        reorderBuffer = new ReorderBuffer((commitSlice) -> {
            OutputStream outputStream = recordingSession.stream();
            for(LogEntry entry : commitSlice) {
//                if(oldestEntryTimestamp > entry.timestamp) {
//                    throw new MyException("Order Issue!");
//                } else {
//                    oldestEntryTimestamp = entry.timestamp;
//                }
                try {
                    outputStream.write(entry.csv.getBytes());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    @Override
    protected void onStop() {
        synchronized (reorderBuffer) {
            reorderBuffer.flush();
        }
        try {
            recordingSession.stream().flush();
        } catch (final Exception e) {
            throw new LoggerException("error while writing log-file", e);
        }
    }

    @Override
    protected void log(LogEntry logEntry) {
        synchronized (reorderBuffer) {
            reorderBuffer.add(logEntry);
        }
    }

    @Override
    public long getEntriesCached() {
        synchronized (reorderBuffer) {
            return reorderBuffer.size();
        }
    }

    @Override
    public float getCacheLevel() {
        synchronized (reorderBuffer) {
            return ((float)reorderBuffer.timespan() / (float)REORDER_TIMEFRAME_UPPER_NS);
        }
    }

    private interface ReorderBufferCommitListener {
        void onCommit(List<LogEntry> commitSlice);
    }

    private static class ReorderBuffer {
        private long oldestTs = Long.MAX_VALUE;
        private long newestTs = Long.MIN_VALUE;
        private ArrayList<LogEntry> reorderBuffer = new ArrayList<>();
        private ReorderBufferCommitListener listener;

        public ReorderBuffer(ReorderBufferCommitListener listener) {
            this.listener = listener;
        }

        public void add(LogEntry logEntry) {
            if(logEntry.timestamp < oldestTs) { oldestTs = logEntry.timestamp; }
            if(logEntry.timestamp > newestTs) { newestTs = logEntry.timestamp; }
            reorderBuffer.add(logEntry);
            if((newestTs - oldestTs) > REORDER_TIMEFRAME_UPPER_NS) { // commit required
                Collections.sort(reorderBuffer);
                long commitEndTs = (newestTs - REORDER_TIMEFRAME_LOWER_NS);
                int commitEndIdx = 0;
                //TODO: binary search
                for(; commitEndIdx < reorderBuffer.size() && reorderBuffer.get(commitEndIdx).timestamp <= commitEndTs; ++commitEndIdx) {}

                List<LogEntry> commit = reorderBuffer.subList(0, commitEndIdx);
                this.listener.onCommit(commit);
                // remove committed elements from reorderBuffer
                commit.clear();

                if(reorderBuffer.size() > 0) {
                    oldestTs = reorderBuffer.get(0).timestamp;
                    newestTs = reorderBuffer.get(reorderBuffer.size() - 1).timestamp;
                } else {
                    oldestTs = newestTs;
                }
            }
        }

        public void flush() {
            Collections.sort(reorderBuffer);
            this.listener.onCommit(reorderBuffer);
            reorderBuffer.clear();
            newestTs = 0;
            oldestTs = 0;
        }

        public int size() {
            return reorderBuffer.size();
        }

        public long timespan() {
            return (newestTs - oldestTs);
        }

    }
}
