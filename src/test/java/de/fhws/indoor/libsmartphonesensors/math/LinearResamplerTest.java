package de.fhws.indoor.libsmartphonesensors.math;

import org.junit.Test;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;

public class LinearResamplerTest {
    @Test
    public void unsyncedResamplingIsCorrect() throws Exception {
        long tsOffset = 991231235;
        LinearResampler resampler = new LinearResampler(3, 2);
        assertEquals(resampler.getSampleIntervalNs(), 500000000);

        List<Long> splTimestamps = new ArrayList<>();
        List<float[]> splValues = new ArrayList<>();

        resampler.pushSample(tsOffset+1, new float[]{1, 1, 1}, (ts, spl) -> {
            splTimestamps.add(ts);
            splValues.add(spl);
        });

        resampler.pushSample(tsOffset+1000000001, new float[]{2, 2, 2}, (ts, spl) -> {
            splTimestamps.add(ts);
            splValues.add(spl);
        });

        assertArrayEquals(new Long[]{ new Long(tsOffset+1), new Long(tsOffset+1+resampler.getSampleIntervalNs()), new Long(tsOffset+1000000001) }, splTimestamps.toArray());
        assertArrayEquals(new float[]{1, 1, 1}, splValues.get(0), (float) 0.00001);
        assertArrayEquals(new float[]{1.5f, 1.5f, 1.5f}, splValues.get(1), (float) 0.00001);
        assertArrayEquals(new float[]{2, 2, 2}, splValues.get(2), (float) 0.00001);
    }

    @Test
    public void syncedResamplingIsCorrect() throws Exception {
        long tsOffset = 99123123531L;
        LinearResampler resampler = new LinearResampler(3, 2, 0);

        List<Long> splTimestamps = new ArrayList<>();
        List<float[]> splValues = new ArrayList<>();

        resampler.pushSample(tsOffset+1, new float[]{1, 1, 1}, (ts, spl) -> {
            splTimestamps.add(ts);
            splValues.add(spl);
        });

        resampler.pushSample(tsOffset+1000000001, new float[]{2, 2, 2}, (ts, spl) -> {
            splTimestamps.add(ts);
            splValues.add(spl);
        });

        // 99123123532 < 1spl
        // 99500000000 < out        a = (100123123532 - 99500000000) / (100123123532 - 99123123532)
        // 100000000000 < out       a = (100123123532 - 100000000000) / (100123123532 - 99123123532)
        // 100123123532 < 2spl
        //

        assertArrayEquals(new Long[]{ new Long(99500000000L), new Long(99500000000L + resampler.getSampleIntervalNs()) }, splTimestamps.toArray());
        assertArrayEquals(new float[]{1.3768765f, 1.3768765f, 1.3768765f}, splValues.get(0), (float) 0.00001);
        assertArrayEquals(new float[]{1.8768765f, 1.8768765f, 1.8768765f}, splValues.get(1), (float) 0.00001);
    }
}