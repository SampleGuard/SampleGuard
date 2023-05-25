package com.example.sampleguard;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;

//import timber.log.Timber;

public class IMUManager_old implements SensorEventListener {
    private static final String TAG = "IMUManager";

    private int mSensorRate = SensorManager.SENSOR_DELAY_FASTEST;

    public static String ImuHeader = "Timestamp[nanosec],m_x,m_y,m_z," +
            "b_x,b_y,b_z,Unix time[nanosec]\n";

    private class SensorPacket {
        long timestamp; // nanoseconds
        long unixTime; // milliseconds
        float[] values;

        SensorPacket(long time, long unixTimeMillis, float[] vals) {
            timestamp = time;
            unixTime = unixTimeMillis;
            values = vals;
        }

        @Override
        public String toString() {
            String delimiter = ",";
            StringBuilder sb = new StringBuilder();
            sb.append(timestamp);
            for (int index = 0; index < values.length; ++index) {
                sb.append(delimiter + values[index]);
            }
            sb.append(delimiter + unixTime + "000000");
            return sb.toString();
        }
    }

    // Sensor listeners
    private SensorManager mSensorManager;
    private Sensor mMagUncal;

    private volatile boolean mRecordingInertialData = false;
    private BufferedWriter mDataWriter = null;
    private HandlerThread mSensorThread;

    private Deque<SensorPacket> mMagUncalData = new ArrayDeque<>();

    public IMUManager_old(Context fgservice) {
        mSensorManager = (SensorManager) fgservice.getSystemService(Context.SENSOR_SERVICE);
        mMagUncal = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED);
    }

    public void startRecording(String captureResultFile) {
        try {
            mDataWriter = new BufferedWriter(
                    new FileWriter(captureResultFile, false));
            if (mMagUncal == null) {
                String warning = "The device may not have uncalibrated mag!\n";
                mDataWriter.write(warning);
            } else {
                mDataWriter.write(ImuHeader);
            }
            mRecordingInertialData = true;
        } catch (IOException err) {
//            Timber.e(err,"IOException in opening inertial data writer at %s",
//                    captureResultFile);
        }
    }

    public void stopRecording() {
        if (mRecordingInertialData) {
            mRecordingInertialData = false;
            try {
                mDataWriter.flush();
                mDataWriter.close();
            } catch (IOException err) {
//                Timber.e(err, "IOException in closing inertial data writer");
            }
            mDataWriter = null;
        }
    }



    @Override
    public final void onSensorChanged(SensorEvent event) {
        long unixTime = System.currentTimeMillis();
        if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED) {
            SensorPacket sp = new SensorPacket(event.timestamp, unixTime, event.values);
            mMagUncalData.add(sp);
            if (mRecordingInertialData) {
                try {
                    mDataWriter.write(sp.toString() + "\n");
                } catch (IOException ioe) {
//                    Timber.e(ioe);
                }
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    /**
     * This will register all IMU listeners
     * https://stackoverflow.com/questions/3286815/sensoreventlistener-in-separate-thread
     */
    public void register() {
        mSensorThread = new HandlerThread("Sensor thread",
                Process.THREAD_PRIORITY_MORE_FAVORABLE);
        mSensorThread.start();
        // Blocks until looper is prepared, which is fairly quick
        Handler sensorHandler = new Handler(mSensorThread.getLooper());
        mSensorManager.registerListener(
                this, mMagUncal, mSensorRate, sensorHandler);
    }

    /**
     * This will unregister all IMU listeners
     */
    public void unregister() {
        mSensorManager.unregisterListener(this, mMagUncal);
        mSensorManager.unregisterListener(this);
        mSensorThread.quitSafely();
        stopRecording();
    }
}
