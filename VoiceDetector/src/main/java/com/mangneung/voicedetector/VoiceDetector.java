package com.mangneung.voicedetector;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;

import androidx.core.app.ActivityCompat;

/**
 * Detects audio input from the microphone and returns a decibel value.
 * RECORD_AUDIO permission is required.
 */
public class VoiceDetector {
    private Context mContext;

    private AudioRecord audioInput;

    private short[][] inputBuffer = null;
    private int inputBufferWhich = 0;
    private int inputBufferIndex = 0;

    private int inputBlockSize = 0;

    private long sleepTime = 0;

    private Listener inputListener = null;

    private boolean running = false;

    private Thread readerThread = null;


    private short[] audioData;
    private long audioSequence = 0;
    private long audioProcessed = 0;

    private static final float MAX_16_BIT = 32768;
    private static final float FUDGE = 0.6f;

    public VoiceDetector(Context mContext) {
        this.mContext = mContext;
    }

    public int calculateDecibel(short[] sdata, int off, int samples) {
        double sum = 0;
        double sumSquared = 0;

        for (int i = 0; i < samples; i++) {
            final long v = sdata[off + i];
            sum += v;
            sumSquared += v * v;
        }

        double power = (sumSquared - sum * sum / samples) / samples;

        power /= MAX_16_BIT * MAX_16_BIT;

        double result = Math.log10(power) * 10f + FUDGE;
        return (int) result;
    }

    /**
     * Start the detector with default rate and block. The default value
     * of rate is 8000 (Hz), which is a sample rate suitable for most human
     * voices except for sibilants (/s/, /f/). The default value of block
     * is set to 6000 considering the return frequency.
     *
     * @param listener Listener to be notified on each completed read.
     */
    public void startReader(Listener listener) {
        final int rate = 8000;
        final int block = 6000;

        synchronized (this) {
            // Calculate the required I/O buffer size.
            int audioBuf = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT) * 2;

            // Set up the audio input.
            if (ActivityCompat.checkSelfPermission(mContext, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            audioInput = new AudioRecord(MediaRecorder.AudioSource.MIC, rate, AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, audioBuf);
            inputBlockSize = block;
            sleepTime = (long) (1000f / ((float) rate / (float) block));
            inputBuffer = new short[2][inputBlockSize];
            inputBufferWhich = 0;
            inputBufferIndex = 0;
            inputListener = listener;
            running = true;
            readerThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    readerRun();
                }
            }, "Voice Detector");
            readerThread.start();
        }
    }

    public void startReader(int rate, int block, Listener listener) {
        synchronized (this) {
            // Calculate the required I/O buffer size.
            int audioBuf = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT) * 2;

            // Set up the audio input.
            if (ActivityCompat.checkSelfPermission(mContext, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            audioInput = new AudioRecord(MediaRecorder.AudioSource.MIC, rate, AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, audioBuf);
            inputBlockSize = block;
            sleepTime = (long) (1000f / ((float) rate / (float) block));
            inputBuffer = new short[2][inputBlockSize];
            inputBufferWhich = 0;
            inputBufferIndex = 0;
            inputListener = listener;
            running = true;
            readerThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    readerRun();
                }
            }, "Voice Detector");
            readerThread.start();
        }
    }

    public void stopReader() {
        synchronized (this) {
            running = false;
        }
        try {
            if (readerThread != null)
                readerThread.join();
        } catch (InterruptedException ignored) {
        }
        readerThread = null;

        // Kill the audio input.
        synchronized (this) {
            if (audioInput != null) {
                audioInput.release();
                audioInput = null;
            }
        }

    }

    private void readerRun() {
        short[] buffer;
        int index, readSize;

        int timeout = 200;
        try {
            while (timeout > 0 && audioInput.getState() != AudioRecord.STATE_INITIALIZED) {
                Thread.sleep(50);
                timeout -= 50;
            }
        } catch (InterruptedException e) {
        }

        if (audioInput.getState() != AudioRecord.STATE_INITIALIZED) {
            readError(Listener.ERR_INIT_FAILED);
            running = false;
            return;
        }

        try {
            audioInput.startRecording();
            while (running) {
                long stime = System.currentTimeMillis();

                if (!running)
                    break;

                readSize = inputBlockSize;
                int space = inputBlockSize - inputBufferIndex;
                if (readSize > space)
                    readSize = space;
                buffer = inputBuffer[inputBufferWhich];
                index = inputBufferIndex;

                synchronized (buffer) {
                    int nread = audioInput.read(buffer, index, readSize);

                    boolean done = false;
                    if (!running)
                        break;

                    if (nread < 0) {
                        readError(Listener.ERR_READ_FAILED);
                        running = false;
                        break;
                    }
                    int end = inputBufferIndex + nread;
                    if (end >= inputBlockSize) {
                        inputBufferWhich = (inputBufferWhich + 1) % 2;
                        inputBufferIndex = 0;
                        done = true;
                    } else
                        inputBufferIndex = end;

                    if (done) {
                        readDone(buffer);

                        // Because our block size is way smaller than the audio
                        // buffer, we get blocks in bursts, which messes up
                        // the audio analyzer. We don't want to be forced to
                        // wait until the analysis is done, because if
                        // the analysis is slow, lag will build up. Instead
                        // wait, but with a timeout which lets us keep the
                        // input serviced.
                        long etime = System.currentTimeMillis();
                        long sleep = sleepTime - (etime - stime);
                        if (sleep < 5)
                            sleep = 5;
                        try {
                            buffer.wait(sleep);
                        } catch (InterruptedException ignored) {
                        }
                    }
                }
            }
        } finally {
            if (audioInput.getState() == AudioRecord.RECORDSTATE_RECORDING)
                audioInput.stop();
        }
    }

    private void readDone(short[] arrBuffer) {
        synchronized (this) {
            audioData = arrBuffer;
            ++audioSequence;

            short[] arrBuffer2 = null;
            if (audioData != null && audioSequence > audioProcessed) {
                audioProcessed = audioSequence;
                arrBuffer2 = audioData;
            }

            if (arrBuffer2 != null) {
                final int len = arrBuffer2.length;
                inputListener.onReadComplete(calculateDecibel(arrBuffer2, 0, len));
                arrBuffer2.notify();
            }
        }
    }

    private void readError(int code) {
        inputListener.onReadError(code);
    }

    public static abstract class Listener {
        public static final int ERR_OK = 0;
        public static final int ERR_INIT_FAILED = 1;
        public static final int ERR_READ_FAILED = 2;

        public abstract void onReadComplete(int decibel);
        public abstract void onReadError(int error);
    }
}