package com.example.smartwatchhapticsystem.view;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.util.Log;
import android.os.Process;
import androidx.core.app.NotificationCompat;
import com.example.smartwatchhapticsystem.R;
import com.example.smartwatchhapticsystem.controller.BluetoothServerManager;
import com.example.smartwatchhapticsystem.controller.FeedBackController;
import android.os.PowerManager;

public class BackgroundMonitoringService extends Service {
    private static final String CHANNEL_ID = "MonitoringChannel";
    private HandlerThread bluetoothThread;
    private Handler bluetoothHandler;
    private static final String TAG = "BackgroundService";
    private PowerManager.WakeLock wakeLock;
    private FeedBackController feedbackController;
    private BluetoothServerManager bluetoothServerManager;

    /**
     * Called when the background service is first created.
     * Sets up notification, wake lock, controller dependencies, and starts the Bluetooth server
     * on a dedicated background thread using a HandlerThread.
     */
    @Override
    public void onCreate() {
        super.onCreate();

        Log.d(TAG, "🚀 Background Service Created");

        // Step 1: Create a notification channel and start foreground service
        // This is mandatory for background services on Android 8+ to stay alive
        createNotificationChannel();

        // Step 2: Acquire a wake lock to prevent the CPU from sleeping while the service runs
        acquireWakeLock();

        // Step 3: Initialize helper classes that manage feedback and Bluetooth server logic
        feedbackController = new FeedBackController(this);                  // Manages vibration feedback
        bluetoothServerManager = new BluetoothServerManager(this, feedbackController); // Handles incoming Bluetooth commands

        // Step 4: Create and start a background thread dedicated to running the Bluetooth server
        // HandlerThread gives you a Looper-backed thread for async operations
        bluetoothThread = new HandlerThread("BluetoothServerThread", Process.THREAD_PRIORITY_FOREGROUND);
        bluetoothThread.start();

        // Step 5: Attach a Handler to the thread’s Looper so we can post work to it
        bluetoothHandler = new Handler(bluetoothThread.getLooper());

        // Step 6: Post a task to run the server on that background thread (non-blocking)
        bluetoothHandler.post(() -> bluetoothServerManager.startServerOnCurrentThread());

        Log.d(TAG, "✅ Bluetooth server thread started via HandlerThread");
    }


    /**
     * Called when the service is started using startService() or startForegroundService().
     * Used to define service restart behavior and optionally handle intent data.
     *
     * @param intent  The intent passed to startService (can carry extra data).
     * @param flags   Additional flags about how the system started the service.
     * @param startId A unique ID for this start request.
     * @return START_STICKY to indicate that the system should restart the service if it's killed.
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "🟢 Background Service Started");

        // START_STICKY means:
        // → If the service is killed by the system (e.g., due to memory pressure),
        // → Android will try to recreate it after resources are available
        // → However, the original Intent will be null in the restart
        return START_STICKY;
    }

    /**
     * Called when the service is being destroyed (manually stopped or killed by the system).
     * Performs cleanup operations to release resources and stop background processes.
     */
    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "🛑 Background Service Destroyed");

        // Step 1: Stop the Bluetooth server to release the socket and thread
        if (bluetoothServerManager != null) {
            bluetoothServerManager.stopServer();
        }

        // Step 2: Stop any ongoing heart rate monitoring or vibration logic
        feedbackController.stopHeartRateMonitoring();

        // Step 3: Release the CPU wake lock to allow the device to sleep again
        releaseWakeLock();
    }


    /**
     * Called when the user removes the app from the recent tasks list (Recents screen).
     * This can be used to handle cleanup or gracefully stop the service,
     * but in this implementation we simply log the event.
     *
     * @param rootIntent The intent that was used to start the task (not used here).
     */
    @Override
    public void onTaskRemoved(Intent rootIntent) {
        Log.w(TAG, "⚠️ Service task removed (maybe screen off?)");
    }

    /**
     * Acquires a partial wake lock to keep the CPU running even if the screen turns off.
     * This is important for long-running background services like Bluetooth or sensor monitoring.
     *
     * We suppress the warning because we're intentionally holding the lock indefinitely
     * until the service is stopped.
     */
    @SuppressLint("WakelockTimeout")
    private void acquireWakeLock() {
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);

        // Create a partial wake lock (CPU stays on, screen and other components can sleep)
        wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "SmartwatchHapticSystem::WakeLockTag" // Custom tag for debugging
        );

        // We don't want Android to count references automatically; we manage this manually
        wakeLock.setReferenceCounted(false);

        // Acquire the wake lock (no timeout — released manually in onDestroy())
        wakeLock.acquire();
    }


    /**
     * Releases the wake lock if it was acquired and is still held.
     * Always call this during service shutdown to avoid battery drain or system warnings.
     */
    private void releaseWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
    }

    /**
     * This service does not support binding, so return null.
     * This means the service is only started/stopped via startService / stopService
     * and not bound to any client components.
     *
     * @param intent The intent used to bind to the service (unused here).
     * @return null to indicate binding is not supported.
     */
    @Override
    public IBinder onBind(Intent intent) {
        return null; // No binding needed for this service
    }


    /**
     * Create a notification channel for the foreground service
     */
    /**
     * Creates a foreground notification channel (required for Android 8+) and starts
     * a persistent notification so that the service can continue running in the background.
     * This is essential for Bluetooth monitoring and vibration control via a foreground service.
     */
    private void createNotificationChannel() {
        // Step 1: Define a notification channel with a unique ID and low importance (no sound)
        NotificationChannel serviceChannel = new NotificationChannel(
                CHANNEL_ID,                                 // Unique channel ID
                "Smartwatch Haptic System",                 // Channel name (user-visible in settings)
                NotificationManager.IMPORTANCE_LOW          // Low importance: no sound, but shows in tray
        );

        // Step 2: Get the system NotificationManager to register the channel
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.createNotificationChannel(serviceChannel); // Register the channel (only once)
        }

        // Step 3: Build a persistent notification tied to the channel
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Smartwatch Haptic System")                   // Main title
                .setContentText("Monitoring Bluetooth connection & vibrations") // Subtext
                .setSmallIcon(R.drawable.ic_launcher_foreground)               // Icon shown in status bar
                .build();

        // Step 4: Start the service in the foreground with the notification (required to keep it alive)
        startForeground(1, notification);  // ID must be unique within your app
    }

}
