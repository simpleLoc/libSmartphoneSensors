package de.fhws.indoor.libsmartphonesensors.sensors;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.AudioTimestamp;
import android.media.MediaRecorder;
import android.os.Build;
import android.util.Log;

import androidx.annotation.RequiresApi;

import java.io.IOException;
import java.io.OutputStream;

import de.fhws.indoor.libsmartphonesensors.ASensor;
import de.fhws.indoor.libsmartphonesensors.SensorDataInterface;
import de.fhws.indoor.libsmartphonesensors.SensorType;

@RequiresApi(api = Build.VERSION_CODES.N)
public class Microphone extends ASensor {

    private AudioRecord audioRecord = null;
    private Thread pullThread = null;

    public Microphone(SensorDataInterface sensorDataInterface) {
        super(sensorDataInterface);
    }

    @SuppressLint("MissingPermission")
    @Override
    public void onResume(Activity act) {
        final OutputStream audioOutputStream;
        try {
            audioOutputStream = sensorDataInterface.requestAuxiliaryChannel("mic");
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        // configure audio capture
        AudioFormat audioFormat = new AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .build();
        audioRecord = new AudioRecord.Builder()
                .setAudioFormat(audioFormat)
                .setAudioSource(MediaRecorder.AudioSource.UNPROCESSED)
                .build();

        audioRecord.startRecording();
        pullThread = new Thread(() -> {
            AudioTimestamp timeBase = null;
            final byte[] buffer = new byte[audioRecord.getBufferSizeInFrames() * (16 / 8)];
            while(true) {
                int framesRead = audioRecord.read(buffer, 0, buffer.length);
                if(framesRead <= 0) {
                    Log.d("Microphone", "Read returned: " + framesRead);
                    break;
                }
                if(timeBase == null) {
                    timeBase = new AudioTimestamp();
                    if(audioRecord.getTimestamp(timeBase, AudioTimestamp.TIMEBASE_BOOTTIME) != AudioRecord.SUCCESS) {
                        throw new RuntimeException("Failed to retrieve sample timestamp");
                    }
                    long bufferStartTs = timeBase.nanoTime - (timeBase.framePosition * 1000000000 / audioRecord.getSampleRate() / audioRecord.getChannelCount());
                    bufferStartTs = Math.max(0, bufferStartTs);
                    String micMetadata = audioRecord.getChannelCount() + ";" + audioRecord.getSampleRate() + ";s16le";
                    sensorDataInterface.onData(bufferStartTs, SensorType.MICROPHONE_METADATA, micMetadata);
                }
                try {
                    audioOutputStream.write(buffer);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            Log.d("MicrophonePull", "exiting...");
        });
        pullThread.setDaemon(true);
        pullThread.setName("MicrophonePull");
        pullThread.start();
    }

    @Override
    public void onPause(Activity act) {
        if(audioRecord != null) {
            audioRecord.stop();
            try {
                pullThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            pullThread = null;
            audioRecord.release();
            audioRecord = null;
        }
    }
}
