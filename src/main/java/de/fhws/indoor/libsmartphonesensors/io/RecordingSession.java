package de.fhws.indoor.libsmartphonesensors.io;

import android.os.SystemClock;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class RecordingSession {

    private long startTs;
    private final File file;
    private OutputStream fileStream;
    private BufferedOutputStream bufferedOutputStream;

    private RecordingSession(long startTs, File file, OutputStream fileStream) {
        this.startTs = startTs;
        this.file = file;
        this.fileStream = fileStream;
        this.bufferedOutputStream = new BufferedOutputStream(fileStream);
    }

    public static RecordingSession create(long startTs, File file) throws FileNotFoundException {
        OutputStream fileStream = new FileOutputStream(file);
        return new RecordingSession(startTs, file, fileStream);
    }

    public String getName() { return file.getName(); }

    public boolean isOpen() { return (fileStream != null); }

    public long getStartTs() { return startTs; }

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

}
