package de.fhws.indoor.libsmartphonesensors.io;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.SystemClock;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class RecordingManager {

    private final File rootPath;
    private final String fileProviderAuthority;

    private RecordingSession currentSession = null;

    public RecordingManager(File rootPath, String fileProviderAuthority) {
        this.rootPath = rootPath;
        this.fileProviderAuthority = fileProviderAuthority;
        rootPath.mkdirs();
    }

    public File getRootPath() { return rootPath; }

    public void shareLast(Activity activity) {
        RecordingSession lastSession = getCurrentSession();
        if(lastSession.isOpen()) { throw new IllegalStateException("Can't share a currently running session!"); }
        File lastSessionFile = lastSession.getFile();
        if(!lastSessionFile.exists()) { return; }
        Uri path = FileProvider.getUriForFile(activity, fileProviderAuthority, lastSessionFile);
        Intent i = new Intent(Intent.ACTION_SEND);
        i.putExtra(Intent.EXTRA_TEXT, "Share Recording");
        i.putExtra(Intent.EXTRA_STREAM, path);
        i.setType("text/csv");
        List<ResolveInfo> resInfoList = activity.getPackageManager().queryIntentActivities(i, PackageManager.MATCH_DEFAULT_ONLY);
        for (ResolveInfo resolveInfo : resInfoList) {
            String packageName = resolveInfo.activityInfo.packageName;
            activity.grantUriPermission(packageName, path, Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        }
        activity.startActivity(Intent.createChooser(i, "Share Recording"));
    }

    public File getNewest() {
        List<File> recordings = getList();
        long newestTs = 0;
        File newestRecording = null;
        for(File recording : recordings) {
            if(recording.lastModified() > newestTs) {
                newestTs = recording.lastModified();
                newestRecording = recording;
            }
        }
        return newestRecording;
    }

    public List<File> getList() {
        ArrayList<File> recordings = new ArrayList<>();
        File[] directoryListing = rootPath.listFiles();
        if(directoryListing != null) {
            for (File recordingFile : rootPath.listFiles()) {
                if(recordingFile.isFile() && recordingFile.getPath().endsWith(".csv")) {
                    recordings.add(recordingFile);
                }
            }
        }
        return recordings;
    }

    public RecordingSession startNewSession() throws FileNotFoundException {
        Long startTs = SystemClock.elapsedRealtimeNanos();
        return startNewNamedSession(startTs.toString(), startTs);
    }

    public RecordingSession startNewNamedSession(String name, long startTs) throws FileNotFoundException {
        if(currentSession != null && currentSession.isOpen()) {
            throw new IllegalStateException("A recording session is already running. Clean that up first");
        }
        File file = new File(rootPath, name + ".csv");
        currentSession = RecordingSession.create(startTs, file);
        return currentSession;
    }

    public RecordingSession startNewNamedSession(String name) throws FileNotFoundException {
        return startNewNamedSession(name, SystemClock.elapsedRealtimeNanos());
    }

    public RecordingSession getCurrentSession() { return currentSession; }

}
