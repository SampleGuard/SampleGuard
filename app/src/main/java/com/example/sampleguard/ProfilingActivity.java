package com.example.sampleguard;

import static android.hardware.SensorManager.SENSOR_DELAY_NORMAL;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Toast;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.GridLabelRenderer;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;
import java.util.List;

import static android.hardware.SensorManager.SENSOR_DELAY_FASTEST;
import static android.hardware.SensorManager.SENSOR_DELAY_GAME;
import static android.hardware.SensorManager.SENSOR_DELAY_UI;


public class ProfilingActivity extends AppCompatActivity {

    private static final String[] REQUIRED_SDK_PERMISSIONS = new String[] {
            Manifest.permission.FOREGROUND_SERVICE, Manifest.permission.WRITE_EXTERNAL_STORAGE };
    private final static int REQUEST_CODE_ASK_PERMISSIONS = 5947;

    public static final String CHANNEL_ID = "camDetectServiceChannel";
    private EditText editTextInput;
    private EditText sampleRateText;
    private int rateIndex = 0;
    private long startTime;
    private boolean serviceRunning = false;
    private Timer timer;
    private IMUManager mImuManager;
    String outputDir = "";
    private EditText timeText;
    String mTestName = "";
    static String inertialFile = "";
    private boolean runInBackground = false;
    private int[] rates = {1, 10, 100, SENSOR_DELAY_NORMAL, SENSOR_DELAY_FASTEST,
            SENSOR_DELAY_GAME, SENSOR_DELAY_UI};
    private int[][] ids = {{R.id.accelGraph_1Hz, R.id.magGraph_1Hz, R.id.gyroGraph_1Hz},
            {R.id.accelGraph_10Hz, R.id.magGraph_10Hz, R.id.gyroGraph_10Hz},
            {R.id.accelGraph_100Hz, R.id.magGraph_100Hz, R.id.gyroGraph_100Hz},
            {R.id.accelGraph_normal, R.id.magGraph_normal, R.id.gyroGraph_normal},
            {R.id.accelGraph_fastest, R.id.magGraph_fastest, R.id.gyroGraph_fastest},
            {R.id.accelGraph_game, R.id.magGraph_game, R.id.gyroGraph_game},
            {R.id.accelGraph_ui, R.id.magGraph_ui, R.id.gyroGraph_ui}};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profiling);
        editTextInput = findViewById(R.id.edit_text_input);
        timeText = findViewById(R.id.edit_timing);
        timer = new Timer();
        checkPermissions();

        createNotificationChannel();
    }


    @Override
    protected void onResume() {
        super.onResume();
        //stopService();
    }

    public void onCheckboxClicked(View view) {
        // Is the view now checked?
        runInBackground = ((CheckBox) view).isChecked();
    }

    public void goToMainScreen(View v) {
        startActivity(new Intent(ProfilingActivity.this, MainActivity.class));
    }

    public void startProfilingBtn(View v) {
        rateIndex = 0;
        // stop anyway no matter if started
        startProfiling();
    }

    public void startProfiling() {
        runInBackground = false;
        String input = editTextInput.getText().toString();
        int timeDelay = Integer.parseInt(timeText.getText().toString());
        mTestName = input;
        outputDir = renewOutputDir();
        inertialFile = outputDir + File.separator + "sensor.csv";
        double rate = (double) rates[rateIndex];
        if (rateIndex > 3) {
            IMUManager.mSensorRate = rates[rateIndex];
        } else {
            int sampleRate = (int) Math.round(1 / (rate / Math.pow(10, 6)));
            IMUManager.mSensorRate = sampleRate;
        }
        timer.schedule(new timeExecution(), timeDelay * 1000);
        if (runInBackground) {
            Intent serviceIntent = new Intent(this, ExampleService.class);
            serviceIntent.putExtra("inputExtra", input);
            serviceIntent.putExtra("sampleRate", "10");
            ContextCompat.startForegroundService(this, serviceIntent);
            finish();
        } else {
            if (mImuManager == null) {
                mImuManager = new IMUManager(this);
                mImuManager.register();
                mImuManager.startRecording(inertialFile);
            }
        }
    }

    class timeExecution extends TimerTask {
        public void run() {
            System.out.println("in run");
            System.out.println("Timer just done");
            stopService();
        }
    }

    public void stopService() {
        // stopService();
        System.out.println("in stopservice");
        if (runInBackground) {
            stopBgService();
        } else {
            mImuManager.stopRecording();
            mImuManager.unregister();
            mImuManager = null;
        }
        generateGraphs();
    }

    public GraphView[] getGraphsFromIndex() {
        GraphView accel = (GraphView) findViewById(ids[rateIndex][0]);
        GraphView mag = (GraphView) findViewById(ids[rateIndex][1]);
        GraphView gyro = (GraphView) findViewById(ids[rateIndex][2]);
        GraphView[] graphs = {accel, mag, gyro};
        return graphs;
    }

    public void generateGraphs() {
        GraphView[] graphs = getGraphsFromIndex();
        for (GraphView graph : graphs) {
            graph.getViewport().setXAxisBoundsManual(true);
            graph.getViewport().setMinX(0);
            graph.getViewport().setScrollable(true);
            GridLabelRenderer gridLabel = graph.getGridLabelRenderer();
            gridLabel.setHorizontalAxisTitle("Time (s)");
            gridLabel.setVerticalAxisTitle("Sample Rate (Hz)");
        }
        graphs[0].setTitle("accl");
        graphs[1].setTitle("magnetic");
        graphs[2].setTitle("gyro");
        ExampleService.plotSensorData(graphs[0], "accl", inertialFile);
        ExampleService.plotSensorData(graphs[1], "gyro", inertialFile);
        ExampleService.plotSensorData(graphs[2], "mag", inertialFile);
        checkIfFinished();
    }
    // If profiling isnt wholly finished, starts profiling for the next rate
    public void checkIfFinished() {
        rateIndex++;
        if (rateIndex < rates.length) {startProfiling();}
    }

    public void stopBgService() {
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

            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(serviceChannel);
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



