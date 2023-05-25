package com.example.sampleguard;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.HandlerThread;
import static android.hardware.SensorManager.SENSOR_DELAY_NORMAL;
import android.os.Process;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.util.Log;
import static com.example.sampleguard.MainActivity.SENSOR_CHANNEL_ID;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;

//import timber.log.Timber;
public class IMUManager implements SensorEventListener {
    private static final String TAG = "IMUManager";
    private int notifId = 70;
    public static int mSensorRate = SENSOR_DELAY_NORMAL;  // us
    private double prevUpdateTime = 0;
    public static String ImuHeader = "Timestamp[nanosec],sensor_x,sensor_y,sensor_z,Unix time[nanosec]\n";
    private double previousTime = -1;
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
    private Sensor mMagSensor;
    private Sensor mGyroSensor;
    private Sensor mAcclSensor;
    private Sensor mLightSensor;
    private Sensor mProxSensor;
    private Sensor mDummy;

    private volatile boolean mRecordingInertialData = false;
    private BufferedWriter mMagWriter = null;
    private BufferedWriter mGyroWriter = null;
    private BufferedWriter mAcclWriter = null;
    private BufferedWriter mLightWriter = null;
    private BufferedWriter mProxWriter = null;

    private HandlerThread mSensorThread;

    private SensorManager.DynamicSensorCallback mDynamicSensorCallback;
    private Context cntxt;
    private NotificationManagerCompat notificationManager;
    public IMUManager(Context fgservice) {
        cntxt = fgservice;
        notificationManager = NotificationManagerCompat.from(fgservice);
        mSensorManager = (SensorManager) fgservice.getSystemService(Context.SENSOR_SERVICE);


        // USELESS
        mDynamicSensorCallback = new SensorManager.DynamicSensorCallback() {
            @Override
            public void onDynamicSensorConnected(Sensor sensor) {
                if (sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                    mDummy = sensor;
                    Log.d("wtf", "Dynamic Sensor Connected");
                }
            }

            @Override
            public void onDynamicSensorDisconnected(Sensor sensor) {
                if (sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                    mDummy = sensor;
                    Log.d("wtf", "Dynamic Sensor Disconnected");
                }
                //Sensor disconnected
//                mSensorManager.unregisterListener(SensorDriverService.this);
            }
        };

        mSensorManager.registerDynamicSensorCallback(mDynamicSensorCallback);



        mMagSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        mGyroSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        mAcclSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mLightSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
        mProxSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);


    }

    public void startRecording(String captureResultFile) {


        try {
            mMagWriter = new BufferedWriter(
                    new FileWriter(captureResultFile.replace("sensor", "mag"), false));
            mMagWriter.write(ImuHeader);

            mGyroWriter = new BufferedWriter(
                    new FileWriter(captureResultFile.replace("sensor", "gyro"), false));
            mGyroWriter.write(ImuHeader);

            mAcclWriter = new BufferedWriter(
                    new FileWriter(captureResultFile.replace("sensor", "accl"), false));
            mAcclWriter.write(ImuHeader);

            mLightWriter = new BufferedWriter(
                    new FileWriter(captureResultFile.replace("sensor", "light"), false));
            mLightWriter.write(ImuHeader);

            mProxWriter = new BufferedWriter(
                    new FileWriter(captureResultFile.replace("sensor", "prox"), false));
            mProxWriter.write(ImuHeader);

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
                mMagWriter.flush();
                mMagWriter.close();
                mGyroWriter.flush();
                mGyroWriter.close();
                mAcclWriter.flush();
                mAcclWriter.close();
                mLightWriter.flush();
                mLightWriter.close();
                mProxWriter.flush();
                mProxWriter.close();
            } catch (IOException err) {
//                Timber.e(err, "IOException in closing inertial data writer");
            }
            mMagWriter = null;
            mGyroWriter = null;
            mAcclWriter = null;
            mLightWriter = null;
            mProxWriter = null;
        }



        mSensorManager.unregisterDynamicSensorCallback(mDynamicSensorCallback);
    }



    @Override
    public final void onSensorChanged(SensorEvent event) {
        long unixTime = System.currentTimeMillis();
        double currentTime = -1;
        if (previousTime == -1) {
            previousTime = event.timestamp;
        } else {
            currentTime = event.timestamp;
        }
        String sens = "";
        if (mRecordingInertialData) {
            SensorPacket sp = new SensorPacket(event.timestamp, unixTime, event.values);
            switch (event.sensor.getType()) {
                case Sensor.TYPE_MAGNETIC_FIELD:
                    sens = "Magnetometer";
                    try {
                        mMagWriter.write(sp.toString() + "\n");
                    } catch (IOException ioe) {
                        //                    Timber.e(ioe);
                    }
                    break;
                case Sensor.TYPE_GYROSCOPE:
                    sens = "Gyroscope";
                    try {
                        mGyroWriter.write(sp.toString() + "\n");
                    } catch (IOException ioe) {
                        //                    Timber.e(ioe);
                    }
                    break;
                case Sensor.TYPE_ACCELEROMETER:
                    sens = "Accelerometer";
                    try {
                        mAcclWriter.write(sp.toString() + "\n");
                    } catch (IOException ioe) {
                        //                    Timber.e(ioe);
                    }
                    break;
                case Sensor.TYPE_LIGHT:
                    sens = "Light sensor";
                    try {
                        mLightWriter.write(sp.toString() + "\n");
                    } catch (IOException ioe) {
                        //                    Timber.e(ioe);
                    }
                    break;
                case Sensor.TYPE_PROXIMITY:
                    sens = "Proximity sensor";
                    try {
                        mProxWriter.write(sp.toString() + "\n");
                    } catch (IOException ioe) {
                        //                    Timber.e(ioe);
                    }
                    break;
            }
        }
        if (currentTime != -1) {
            double samplingRate = (Math.pow(10, 9)/(currentTime- previousTime));
            double THRESHOLD = 10;// make this user customizable later
            if (samplingRate > 10 && (prevUpdateTime == 0 || (currentTime - prevUpdateTime)/(Math.pow(10,9)) >= 3)) {
                prevUpdateTime = currentTime;
                NotificationCompat.Builder builder = new NotificationCompat.Builder(cntxt, SENSOR_CHANNEL_ID)
                        .setSmallIcon(cntxt.getApplicationInfo().icon)
                        .setContentTitle("Sensor Usage Detected")
                        .setContentText(sens + " usage detected.")
                        .setStyle(new NotificationCompat.BigTextStyle()
                                .bigText(sens + " usage detected."))
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setAutoCancel(true);
                notificationManager.notify(notifId, builder.build());
                notifId += 1;
            }
            previousTime = currentTime;
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
                this, mMagSensor, mSensorRate, sensorHandler);
        mSensorManager.registerListener(
                this, mGyroSensor, mSensorRate, sensorHandler);
        mSensorManager.registerListener(
                this, mAcclSensor, mSensorRate, sensorHandler);
        mSensorManager.registerListener(
                this, mLightSensor, mSensorRate, sensorHandler);
        mSensorManager.registerListener(
                this, mProxSensor, mSensorRate, sensorHandler);
    }

    /**
     * This will unregister all IMU listeners
     */
    public void unregister() {
        mSensorManager.unregisterListener(this, mMagSensor);
        mSensorManager.unregisterListener(this, mGyroSensor);
        mSensorManager.unregisterListener(this, mAcclSensor);
        mSensorManager.unregisterListener(this, mLightSensor);
        mSensorManager.unregisterListener(this, mProxSensor);
        mSensorManager.unregisterListener(this);
        mSensorThread.quitSafely();
        stopRecording();
    }

    public void exit() {
        stopRecording();
        unregister();
    }


}
