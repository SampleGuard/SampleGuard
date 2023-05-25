package com.example.sampleguard;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Toast;
import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.GridLabelRenderer;

import static android.hardware.SensorManager.SENSOR_DELAY_NORMAL;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final String[] REQUIRED_SDK_PERMISSIONS = new String[] {
            Manifest.permission.FOREGROUND_SERVICE, Manifest.permission.WRITE_EXTERNAL_STORAGE };
    private final static int REQUEST_CODE_ASK_PERMISSIONS = 5947;

    public static final String CHANNEL_ID = "camDetectServiceChannel";
    public static final String SENSOR_CHANNEL_ID = "sensorUpdateChannel";
    private EditText editTextInput;
    private EditText sampleRateText;
    private boolean serviceRunning = false;
    private IMUManager mImuManager;
    String outputDir = "";
    String mTestName = "";
    public static String inertialFile = "";
    private boolean runInBackground = false;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        checkPermissions();

        createNotificationChannel();
    }


    @Override
    protected void onResume() {
        super.onResume();
        stopService();
    }

    public void onCheckboxClicked(View view) {
        // Is the view now checked?
        runInBackground = ((CheckBox) view).isChecked();
    }

    public void goToProfileScreen(View v) {
        startActivity(new Intent(MainActivity.this, ProfilingActivity.class));
    }

    public void goToGraph(View v) {
        startActivity(new Intent(MainActivity.this, GraphActivity.class));
    }

    public void startServiceBtn(View v) {

        // stop anyway no matter if started
        startService();

    }

    public void startService() {
        String input = "logs";
        mTestName = input;
        String sampleRate = "5";
        outputDir = renewOutputDir();
        inertialFile = outputDir + File.separator + "sensor.csv";
        if (runInBackground) {
            Intent serviceIntent = new Intent(this, ExampleService.class);
            serviceIntent.putExtra("inputExtra", input);
            serviceIntent.putExtra("sampleRate", sampleRate);
            ContextCompat.startForegroundService(this, serviceIntent);
            finish();
        } else {
            if (sampleRate.trim().equals("")) { // if just whitespace, make sample rate default
                IMUManager.mSensorRate = SENSOR_DELAY_NORMAL;
            } else {
                IMUManager.mSensorRate = (int) Math.round(1 / (Double.parseDouble(sampleRate) / Math.pow(10, 6)));
            }
            if (mImuManager == null) {
                mImuManager = new IMUManager(this);
                mImuManager.register();
                mImuManager.startRecording(inertialFile);
            }
        }
    }

    public void stopServiceBtn(View v) {
        // stopService();
        if (runInBackground) {
            stopService();
        } else {
            mImuManager.stopRecording();
            mImuManager.unregister();
            mImuManager = null;
        }
    }

    public void generateGraphs(View v) {
        GraphView accel = (GraphView) findViewById(R.id.accelGraph);
        GraphView mag = (GraphView) findViewById(R.id.magGraph);
        GraphView gyro = (GraphView) findViewById(R.id.gyroGraph);
        GraphView[] graphs = {accel, mag, gyro};
        for (GraphView graph : graphs) {
            graph.getViewport().setXAxisBoundsManual(true);
            graph.getViewport().setMinX(0);
            graph.getViewport().setScrollable(true);
            GridLabelRenderer gridLabel = graph.getGridLabelRenderer();
            gridLabel.setHorizontalAxisTitle("Time (s)");
            gridLabel.setVerticalAxisTitle("Sample Rate (Hz)");
        }
        accel.setTitle("accl");
        mag.setTitle("magnetic");
        gyro.setTitle("gyro");
        ExampleService.plotSensorData(accel, "accl", inertialFile);
        ExampleService.plotSensorData(gyro, "gyro", inertialFile);
        ExampleService.plotSensorData(mag, "mag", inertialFile);
    }

    public void stopService() {
        Intent serviceIntent = new Intent(this, ExampleService.class);
        stopService(serviceIntent);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Cam Detect Service Channel",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            NotificationChannel sensorChannel = new NotificationChannel(
                    SENSOR_CHANNEL_ID,
                    "Sensor Update Channel",
                    NotificationManager.IMPORTANCE_HIGH
            );

            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(serviceChannel);
        manager.createNotificationChannel(sensorChannel);
        }
    }




    /**
     * Checks the dynamically-controlled permissions and requests missing permissions from end user.
     * see https://developer.here.com/documentation/android-starter/dev_guide/topics/request-android-permissions.html
     */
    protected void checkPermissions() {
        final List<String> missingPermissions = new ArrayList<String>();
        // check all required dynamic permissions
        for (final String permission : REQUIRED_SDK_PERMISSIONS) {
            final int result = ContextCompat.checkSelfPermission(this, permission);
            if (result != PackageManager.PERMISSION_GRANTED) {
                missingPermissions.add(permission);
            }
        }
        if (!missingPermissions.isEmpty()) {
            // request all missing permissions
            final String[] permissions = missingPermissions
                    .toArray(new String[missingPermissions.size()]);
            ActivityCompat.requestPermissions(this, permissions, REQUEST_CODE_ASK_PERMISSIONS);
        } else {
            final int[] grantResults = new int[REQUIRED_SDK_PERMISSIONS.length];
            Arrays.fill(grantResults, PackageManager.PERMISSION_GRANTED);
            onRequestPermissionsResult(REQUEST_CODE_ASK_PERMISSIONS, REQUIRED_SDK_PERMISSIONS,
                    grantResults);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[],
                                           @NonNull int[] grantResults) {
        switch (requestCode) {
            case REQUEST_CODE_ASK_PERMISSIONS:
                for (int index = permissions.length - 1; index >= 0; --index) {
                    if (grantResults[index] != PackageManager.PERMISSION_GRANTED) {
                        // exit the app if one permission is not granted
                        Toast.makeText(this, "Required permission '" + permissions[index]
                                + "' not granted, exiting", Toast.LENGTH_LONG).show();
                        finish();
                        return;
                    }
                }
        }
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



