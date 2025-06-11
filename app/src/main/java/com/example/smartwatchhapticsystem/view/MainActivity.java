package com.example.smartwatchhapticsystem.view;

import android.Manifest;
import android.companion.AssociationRequest;
import android.companion.CompanionDeviceManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.WindowManager;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import com.example.smartwatchhapticsystem.R;
public class MainActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 101;
    private static final String TAG = "MainActivity";


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //  Request necessary permissions
        checkAndRequestPermissions();
        requestCompanionAssociation();
    }

    /**
     *  Request necessary permissions before starting the server
     */
    private void checkAndRequestPermissions() {
        String[] permissions;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions = new String[]{
                    Manifest.permission.BODY_SENSORS,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.WAKE_LOCK
            };
        } else {
            permissions = new String[]{
                    Manifest.permission.BODY_SENSORS,
                    Manifest.permission.WAKE_LOCK // Required on all versions for keeping service alive
            };
        }

        boolean permissionsGranted = true;

        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                permissionsGranted = false;
                break;
            }
        }

        if (!permissionsGranted) {
            ActivityCompat.requestPermissions(this, permissions, PERMISSION_REQUEST_CODE);
        } else {
            // Permissions already granted, start services
            startServices();
        }
    }


    /**
     *  Start Background Monitoring Service and Bluetooth GATT Server
     */
    private void startServices() {
        startBackgroundService();
    }

    /**
     *  Start Background Monitoring Service
     */
    private void startBackgroundService() {
        Intent serviceIntent = new Intent(this, BackgroundMonitoringService.class);
        startForegroundService(serviceIntent);
        Log.d(TAG, "Background Service Started");
    }

    /**
     *  Handle Permission Request Result
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            if (allGranted) {
                Toast.makeText(this, " Permissions granted.", Toast.LENGTH_SHORT).show();
                startServices();
            } else {
                Toast.makeText(this, " Required permissions denied!", Toast.LENGTH_SHORT).show();
            }
        }
    }



    private void requestCompanionAssociation() {
        CompanionDeviceManager cdm = (CompanionDeviceManager) getSystemService(Context.COMPANION_DEVICE_SERVICE);

        AssociationRequest request;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            request = new AssociationRequest.Builder()
                    .setSingleDevice(true)
                    .setDeviceProfile(AssociationRequest.DEVICE_PROFILE_WATCH) //  For smartwatches
                    .build();
        } else {
            request = new AssociationRequest.Builder()
                    .setSingleDevice(true)
                    .build(); // No profile for < API 31
        }


        cdm.associate(request, new CompanionDeviceManager.Callback() {
            @Override
            public void onDeviceFound(@NonNull IntentSender chooserLauncher) {
                try {
                    startIntentSenderForResult(chooserLauncher, 1234, null, 0, 0, 0);
                } catch (IntentSender.SendIntentException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onFailure(CharSequence error) {
                Log.e(TAG, "❌ CDM association failed: " + error);
            }
        }, null);
    }
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == 1234 && resultCode == RESULT_OK) {
            Log.d(TAG, "✅ Companion device associated successfully");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        );
    }


    /**
     *  Stop Services when the app is destroyed
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, " MainActivity Destroyed, Stopping Services");
        Intent serviceIntent = new Intent(this, BackgroundMonitoringService.class);
        stopService(serviceIntent);
    }
}
