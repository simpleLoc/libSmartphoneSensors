package de.fhws.indoor.libsmartphonesensors.math;

/**
 * Simple n-dimensional linear signal resampler.
 * @author Markus Ebner
 */
public class LinearResampler {
    // resample config
    private int dimensions;
    private long sampleIntervalNs;
    private Long syncTimestampNs = null;
    // interpolation state
    private long nextSampleTs = 0;
    private Sample prevSample = null;

    public interface SampleCallback {
        void onSample(long timestampNs, float[] sample);
    }

    private static class Sample {
        private long timestampNs = 0;
        private float[] sample = null;

        public Sample(long timestampNs, float[] sample) {
            this.timestampNs = timestampNs;
            this.sample = sample;
        }
    }

    public long getSampleIntervalNs() {
        return sampleIntervalNs;
    }

    /**
     * Create a new linear resampler. Will synchronize on the timestamp of the
     * first received sample.
     * @param dimensions Amount of dimensions the signal has.
     * @param targetHz Target frequency to resample the signal to
     */
    public LinearResampler(int dimensions, float targetHz) {
        setup(dimensions, targetHz, null);
    }
    /**
     * Create a new linear resampler with synchronized timestamp.
     * This resampler will generate samples at multiples of the given syncTimestamp + n*sampleInterval
     * such that the generated samples are running in unison to other resamplers with the same
     * targetHz and syncTimestamp
     * @param dimensions Amount of dimensions the signal has.
     * @param targetHz Target frequency to resample the signal to
     * @param syncTimestampNs Synchronization timestamp. All resulting samples will have
     *                     a timestamp fulfilling syncTimestamp + n*sampleInterval
     */
    public LinearResampler(int dimensions, float targetHz, long syncTimestampNs) {
        setup(dimensions, targetHz, syncTimestampNs);
    }

    private void setup(int dimensions, float targetHz, Long syncTimestampNs) {
        this.dimensions = dimensions;
        this.sampleIntervalNs = (long) ((double) 1000000000 / (double) targetHz);
        if(syncTimestampNs != null) {
            this.syncTimestampNs = (syncTimestampNs % sampleIntervalNs);
        }
    }

    public void pushSample(long timestamp, float[] sample, SampleCallback sampleCallback) {
        Sample newSample = new Sample(timestamp, sample);

        if(prevSample == null) {
            if(syncTimestampNs == null) {
                nextSampleTs = timestamp;
            } else {
                nextSampleTs = syncTimestampNs;
                nextSampleTs += ((timestamp - syncTimestampNs) / sampleIntervalNs) * sampleIntervalNs;
                if(nextSampleTs < timestamp) { nextSampleTs += sampleIntervalNs; }
                if(Math.abs(nextSampleTs - timestamp) > sampleIntervalNs) { throw new Error("BUG!"); }
            }
            prevSample = newSample;
        }

        while(nextSampleTs <= timestamp) {
            double a = 0;
            if(timestamp != prevSample.timestampNs) {
                a = (double) (nextSampleTs - prevSample.timestampNs) / (double) (timestamp - prevSample.timestampNs);
            }
            Sample interpolSample = genSample(prevSample, newSample, a);
            sampleCallback.onSample(interpolSample.timestampNs, interpolSample.sample);
            nextSampleTs += sampleIntervalNs;
        }
        prevSample = newSample;
    }

    private Sample genSample(Sample prevSample, Sample newSample, double a) {
        long interpolTsNs = (long) (prevSample.timestampNs * (1.0 - a) + newSample.timestampNs * a);
        float[] interpolSamples = new float[dimensions];
        for(int i = 0; i < dimensions; ++i) {
            interpolSamples[i] = (float) (prevSample.sample[i] * (1.0 - a) + newSample.sample[i] * a);
        }
        return new Sample(interpolTsNs, interpolSamples);
    }

}
