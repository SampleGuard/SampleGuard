package com.example.sampleguard;

import android.Manifest;
import android.content.pm.PackageManager;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;
import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.GridLabelRenderer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class GraphActivity extends AppCompatActivity {

    private static final String[] REQUIRED_SDK_PERMISSIONS = new String[] {
            Manifest.permission.FOREGROUND_SERVICE, Manifest.permission.WRITE_EXTERNAL_STORAGE };
    private final static int REQUEST_CODE_ASK_PERMISSIONS = 5947;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_graph);
        checkPermissions();

    }


    @Override
    protected void onResume() {
        super.onResume();
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
        ExampleService.plotSensorData(accel, "accl", MainActivity.inertialFile);
        ExampleService.plotSensorData(gyro, "gyro", MainActivity.inertialFile);
        ExampleService.plotSensorData(mag, "mag", MainActivity.inertialFile);
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

}



