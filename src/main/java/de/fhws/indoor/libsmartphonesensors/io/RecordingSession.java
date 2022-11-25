package de.fhws.indoor.libsmartphonesensors.io;

import android.os.SystemClock;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class RecordingSession {

    private static final String REMARK_HEADER = "\n\n\n# ================ REMARK ================\n";

    private UUID recordingId;
    private long startTs;
    private File file;
    private OutputStream fileStream;
    private BufferedOutputStream bufferedOutputStream;
    private HashMap<String, AuxillaryStream> auxillaryStreams = new HashMap<>();

    private static class AuxillaryStream {
        public File file;
        public OutputStream fileStream;
        public AuxillaryStream(File file,  OutputStream fileStream) {
            this.file = file;
            this.fileStream = fileStream;
        }
    }

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

    public OutputStream openAuxiliaryChannel(String id) throws IOException {
        if(auxillaryStreams.containsKey(id)) {
            throw new UnsupportedOperationException("An auxiliary channel with this id was already opened.");
        }
        File auxiliaryChannelFile = new File(file.getAbsolutePath() + "." + id);
        FileOutputStream auxiliaryChannelStream = new FileOutputStream(auxiliaryChannelFile);
        AuxillaryStream auxillaryStream = new AuxillaryStream(auxiliaryChannelFile, auxiliaryChannelStream);
        auxillaryStreams.put(id, auxillaryStream);
        return auxiliaryChannelStream;
    }

    public OutputStream stream() { return bufferedOutputStream; }

    public void close() {
        try {
            bufferedOutputStream.flush();
            bufferedOutputStream.close();
            bufferedOutputStream = null;

            fileStream.flush();
            fileStream.close();
            fileStream = null;

            for(Map.Entry auxItem : auxillaryStreams.entrySet()) {
                AuxillaryStream auxillaryStream = (AuxillaryStream)auxItem.getValue();
                auxillaryStream.fileStream.close();
                auxillaryStream.fileStream = null;
            }
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
        for(Map.Entry auxItem : auxillaryStreams.entrySet()) {
            ((AuxillaryStream)auxItem.getValue()).file.delete();
        }
    }

}
