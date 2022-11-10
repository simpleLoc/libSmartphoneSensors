package de.fhws.indoor.libsmartphonesensors.io;

import android.os.SystemClock;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.UUID;

public class RecordingSession {

    private static final String REMARK_HEADER = "\n\n\n# ================ REMARK ================\n";

    private UUID recordingId;
    private long startTs;
    private File file;
    private OutputStream fileStream;
    private BufferedOutputStream bufferedOutputStream;

    private RecordingSession(long startTs, File file, OutputStream fileStream) {
        recordingId = UUID.randomUUID();
        this.startTs = startTs;
        this.file = file;
        this.fileStream = fileStream;
        this.bufferedOutputStream = new BufferedOutputStream(fileStream);
    }

    public static RecordingSession create(long startTs, File file) throws FileNotFoundException {
        OutputStream fileStream = new FileOutputStream(file);
        return new RecordingSession(startTs, file, fileStream);
    }

    public UUID getRecordingId() { return recordingId; }

    public String getName() { return file.getName(); }

    public boolean isOpen() { return (fileStream != null); }

    public long getStartTs() { return startTs; }

    public File getFile() { return file; }

    public OutputStream stream() { return bufferedOutputStream; }

    public void close() {
        try {
            bufferedOutputStream.flush();
            bufferedOutputStream.close();
            bufferedOutputStream = null;

            fileStream.flush();
            fileStream.close();
            fileStream = null;
        } catch (IOException e) { e.printStackTrace(); }
    }

    /**
     * Adds the given (multi-line) remark to the recording file, then closes the session.
     * This appends every line of the remark with a "# " comment designator at the bottom of the recording file.
     * @param remark Remark to add at the bottom of the file
     */
    public void closeWithRemark(String remark) throws IOException {
        String[] remarkLines = remark.split("\\r?\\n");
        StringBuilder remarkBuilder = new StringBuilder(REMARK_HEADER.length() + remark.length() + remarkLines.length * 3);
        remarkBuilder.append(REMARK_HEADER);
        for(String remarkLine : remarkLines) {
            remarkBuilder.append("# ");
            remarkBuilder.append(remarkLine);
            remarkBuilder.append("\n");
        }
        bufferedOutputStream.write(remarkBuilder.toString().getBytes());
        close();
    }

    /**
     * Aborts this RecordingSession.
     * This closes and deletes the file
     */
    public void abort() {
        close();
        file.delete();
    }

}
