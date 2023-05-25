package com.example.sampleguard;


import static android.hardware.SensorManager.SENSOR_DELAY_NORMAL;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.hardware.camera2.CameraManager;
import android.os.Environment;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;
import com.opencsv.CSVReader;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.FileReader;
import java.io.FileOutputStream;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import static com.example.sampleguard.MainActivity.CHANNEL_ID;


public class ExampleService extends Service {

    private IMUManager mImuManager;
    private Context serviceContext;

    String mTestName = "";
    String sampleRateText = "";
    String outputDir = "";
    static String inertialFile = "";


    private CameraManager cameraManager;
    private CameraManager.AvailabilityCallback cameraCallback;



    @Override
    public void onCreate() {
        super.onCreate();

    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {


        mTestName = intent.getStringExtra("inputExtra");
        sampleRateText = intent.getStringExtra("sampleRate");

        if (sampleRateText.trim().equals("")) { // if just whitespace, make sample rate default
            IMUManager.mSensorRate = SENSOR_DELAY_NORMAL;
        } else {
            IMUManager.mSensorRate = (int) Math.round(1 / (Double.parseDouble(sampleRateText) / Math.pow(10, 6)));
        }

        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this,
                0, notificationIntent, 0);

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Cam Detect")
                .setContentText(mTestName)
                .setSmallIcon(R.drawable.ic_launcher_background)
                .setContentIntent(pendingIntent)
                .build();
        startForeground(1, notification);

        //do heavy work on a background thread
        //stopSelf();



        outputDir = renewOutputDir();
        inertialFile = outputDir + File.separator + "sensor.csv";

        serviceContext = this;


//        // start recording sensor data after a while
//        final Handler handler = new Handler();
//        handler.postDelayed(new Runnable() {
//            @Override
//            public void run() {
//                // Do something after 5s = 5000ms
//                if (mImuManager == null) {
//                    mImuManager = new IMUManager(serviceContext);
//                    mImuManager.register();
//                    mImuManager.startRecording(inertialFile);
//                }
//            }
//        }, 10000);

        // or start recording sensor data immediately
        if (mImuManager == null) {
            mImuManager = new IMUManager(this);
            mImuManager.register();
            mImuManager.startRecording(inertialFile);
        }



//        // camera not used
//        cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
//        cameraManager.registerAvailabilityCallback(getCameraCallback(), null);


        return START_NOT_STICKY;
    }

//
//    // Camera service callbacks
//    // camera not used
//    private CameraManager.AvailabilityCallback getCameraCallback() {
//        cameraCallback = new CameraManager.AvailabilityCallback() {
//            @Override
//            public void onCameraAvailable(String cameraId) {
//                super.onCameraAvailable(cameraId);
//
//
//            }
//
//            @Override
//            public void onCameraUnavailable(String cameraId) {
//                super.onCameraUnavailable(cameraId);
//
//
//            }
//        };
//        return cameraCallback;
//    }

    @Override
    public void onDestroy() {

        super.onDestroy();


        mImuManager.stopRecording();
        mImuManager.unregister();
        mImuManager = null;

//        plotSensorData("accl");


    }

    public static void plotSensorData(GraphView graph, String sensorType, String inertialFile){
        LineGraphSeries<DataPoint> series = new LineGraphSeries<DataPoint>();
        CSVReader reader = null;
        graph.removeAllSeries();
        List<String[]> myEntries = null;
        try {
            reader = new CSVReader(new FileReader(inertialFile.replace("sensor", sensorType)));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        try {
            myEntries = reader.readAll();
            double initTime = Double.parseDouble(myEntries.get(1)[0]);
            double x = 0;
            for (int i = 3; i < myEntries.size(); i++) {
                double currTime = Double.parseDouble(myEntries.get(i)[0]);
                double prevTime = Double.parseDouble(myEntries.get(i-1)[0]);
                x = (currTime - initTime) / (Math.pow(10, 9));
                double y = (Math.pow(10, 9)/(currTime - prevTime));
                series.appendData(new DataPoint(x, y), true, 1000000);
            }
            graph.getViewport().setMaxX(x+5); //automatically scales the graph to show all data
            graph.addSeries(series);
            Bitmap bmp = graph.takeSnapshot();
            FileOutputStream out = new FileOutputStream(inertialFile.replace("sensor", sensorType).replace(".csv", ".png"));
            bmp.compress(Bitmap.CompressFormat.JPEG, 100, out);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }



    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }


    protected String renewOutputDir() {
        if (!mTestName.isEmpty()){
            mTestName += "_";
        }
        SimpleDateFormat dateFormat =
                new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss");
        String folderName = mTestName + dateFormat.format(new Date());
        String dir1 = getFilesDir().getAbsolutePath();
        String dir2 = Environment.getExternalStorageDirectory().
                getAbsolutePath() + File.separator + "CamDetect";

        String dir3 = getExternalFilesDir(
                Environment.getDataDirectory().getAbsolutePath()).getAbsolutePath();
        String outputDir = dir2 + File.separator + folderName;
        (new File(outputDir)).mkdirs();
        return outputDir;
    }
    
    
}