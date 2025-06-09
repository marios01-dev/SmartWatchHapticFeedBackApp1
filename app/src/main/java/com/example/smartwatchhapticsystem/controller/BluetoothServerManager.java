package com.example.smartwatchhapticsystem.controller;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;

import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.os.Process;
import androidx.core.content.ContextCompat;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

public class BluetoothServerManager {
    private static final String TAG = "BluetoothServerManager";
    private static final String SERVICE_NAME = "SmartwatchHapticService";
    private String monitoringType = "";
    private static final UUID APP_UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb"); // SPP UUID
    private String androidId = "UnknownAndroid";
    private String userId = "UnknownUser";
    private String watchId = "UnknownWatchID";

    private final Context context;
    private final FeedBackController feedbackController;
    private BluetoothServerSocket serverSocket;
    private boolean isRunning = false;

    public BluetoothServerManager(Context context, FeedBackController feedbackController) {
        this.context = context;
        this.feedbackController = feedbackController;
    }

    /**
     * Starts a classic Bluetooth SPP (Serial Port Profile) server socket on the current thread.
     * Waits for incoming Bluetooth connections to receive vibration commands.
     * Ensures proper permission and adapter checks, and runs as a blocking loop until stopped.
     */
    public void startServerOnCurrentThread() {
        // Step 1: Set the thread priority to foreground (just below main UI thread)
        // This gives Bluetooth operations higher priority than background tasks
        Process.setThreadPriority(Process.THREAD_PRIORITY_FOREGROUND);

        // Step 2: Get the system Bluetooth adapter
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null) {
            Log.e(TAG, "❌ Device does not support Bluetooth");
            return; // Abort if the device has no Bluetooth hardware
        }

        // Step 3: Make sure Bluetooth is turned on
        if (!adapter.isEnabled()) {
            Log.w(TAG, "⚠️ Bluetooth is OFF. Cannot start server.");
            return; // Important: retry logic (e.g., waiting for Bluetooth to turn on) should be handled elsewhere
        }

        // Step 4: Check runtime permission (Android 12+ requires BLUETOOTH_CONNECT)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
                        != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "❌ Missing BLUETOOTH_CONNECT permission. Cannot start SPP server.");
            return;
        }

        try {
            // Step 5: Start listening for incoming Bluetooth SPP connections
            serverSocket = adapter.listenUsingRfcommWithServiceRecord(SERVICE_NAME, APP_UUID);
            isRunning = true;
            Log.d(TAG, "📡 Classic Bluetooth server started. Waiting for connections...");

            // Step 6: Enter main server loop (blocks on .accept())
            while (isRunning) {
                Log.d(TAG, "🔄 Bluetooth server still running...");

                try {
                    // Step 7: Accept a new client connection (blocking until a device connects)
                    BluetoothSocket socket = serverSocket.accept();

                    // Step 8: Optionally get the name of the connected device (if permission allows)
                    String deviceName = "Unknown";
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
                                    == PackageManager.PERMISSION_GRANTED) {
                        deviceName = socket.getRemoteDevice().getName();
                    }

                    Log.d(TAG, "✅ Device connected via SPP: " + deviceName);

                    // Step 9: Handle communication with the connected device
                    handleClient(socket);

                } catch (SecurityException se) {
                    // Handle permission error mid-loop (could happen if permission is revoked)
                    Log.e(TAG, "❌ SecurityException: Missing BLUETOOTH_CONNECT permission", se);
                }
            }

        } catch (IOException e) {
            // Handle critical failure when starting the server
            Log.e(TAG, "❌ Failed to start SPP server: " + e.getMessage());
        }
    }


    /**
     * Handles communication with a connected Bluetooth client on a dedicated thread.
     * Interprets incoming commands (e.g., "Monitoring:HeartRate", "Vibrate:...") and responds accordingly.
     *
     * @param socket The Bluetooth socket representing the connection to the client.
     */
    private void handleClient(BluetoothSocket socket) {
        new Thread(() -> {
            // Step 1: Start a "heartbeat" log to confirm thread is alive every 3 seconds
            Handler heartbeatHandler = new Handler(Looper.getMainLooper());
            Runnable heartbeatRunnable = new Runnable() {
                @Override
                public void run() {
                    Log.d("Heartbeat", "✅ Heartbeat thread is still alive");
                    heartbeatHandler.postDelayed(this, 3000);
                }
            };
            heartbeatHandler.post(heartbeatRunnable);

            try (
                    // Step 2: Open input/output streams for Bluetooth socket
                    InputStream input = socket.getInputStream();
                    OutputStream output = socket.getOutputStream()
            ) {
                byte[] buffer = new byte[1024];
                int bytes;
                boolean heartRateStarted = false;

                // Step 3: Read messages from client in a loop
                while ((bytes = input.read(buffer)) != -1) {
                    String message = new String(buffer, 0, bytes).trim();
                    Log.d(TAG, "📥 Received: " + message);

                    // Step 4: Split message into command and payload
                    String[] parts = message.split(":", 2);
                    if (parts.length != 2) {
                        Log.e(TAG, "❌ Invalid message format: " + message);
                        continue;
                    }

                    String command = parts[0];
                    String payload = parts[1];

                    // Step 5: Handle the command
                    switch (command) {
                        case "Monitoring":
                            monitoringType = payload;
                            Log.d(TAG, "📌 Monitoring Type set to: " + monitoringType);

                            if (monitoringType.equalsIgnoreCase("HeartRate") && !heartRateStarted) {
                                heartRateStarted = true;
                                startSendingHeartRate(output, socket); // Begin heart rate streaming
                            }
                            break;

                            // Handle Vibration command
                        case "Vibrate":
                            handleVibrateCommand(payload);
                            break;

                        default:
                            Log.w(TAG, "⚠️ Unknown command: " + command);
                            break;
                    }
                }

            } catch (IOException e) {
                // Handle disconnection or communication failure
                Log.e(TAG, "❌ Error while reading from socket: " + e.getMessage());
            } finally {
                // Step 6: Clean up when the socket is closed
                try {
                    socket.close();
                    Log.d(TAG, "🔌 Socket closed. Stopping heart rate monitoring.");
                    feedbackController.stopHeartRateMonitoring();
                } catch (IOException e) {
                    Log.e(TAG, "❌ Failed to close socket", e);
                }

                // Remove heartbeat logging
                heartbeatHandler.removeCallbacksAndMessages(null);
            }
        }).start();
    }

    /**
     * Parses a "Vibrate" command payload and triggers vibration feedback based on the current monitoring type.
     * The payload is expected to be in the format: "intensity,pulses,duration,interval".
     *
     * Supported monitoring types:
     * - "HeartRate"     → Triggers heart rate-specific vibration feedback
     * - "SunAzimuth"    → Triggers sun azimuth-specific feedback pattern
     *
     * If the monitoring type is unknown or not supported, the command is logged and ignored.
     *
     * @param payload The raw command string received from the client (e.g., "50,3,1000,200")
     */
    private void handleVibrateCommand(String payload) {
        String[] vibrationParams = payload.split(",");

        // Step 1: Ensure the payload contains exactly 4 comma-separated values
        if (vibrationParams.length != 4) {
            Log.e(TAG, "❌ Incorrect number of vibration parameters: " + payload);
            return;
        }

        try {
            // Step 2: Parse all values into integers
            int intensity = Integer.parseInt(vibrationParams[0]);
            int pulses = Integer.parseInt(vibrationParams[1]);
            int duration = Integer.parseInt(vibrationParams[2]);
            int interval = Integer.parseInt(vibrationParams[3]);

            // Step 3: Trigger feedback based on current monitoring type
            switch (monitoringType) {
                case "HeartRate":
                    feedbackController.triggerHeartRateVibration(intensity, pulses, duration, interval);
                    break;

                case "SunAzimuth":
                    feedbackController.triggerVibrationForSunAzimuth(intensity, pulses, duration, interval);
                    break;

                case "MoonAzimuth":
                    feedbackController.triggerVibrationForSunAzimuth(intensity, pulses, duration, interval);
                    break;

                default:
                    Log.w(TAG, "⚠️ Vibration command received, but monitoring type is unknown or unsupported: " + monitoringType);
                    break;
            }

        } catch (NumberFormatException e) {
            // Step 4: Handle case where one or more parameters are not valid integers
            Log.e(TAG, "❌ Invalid numbers in vibration command: " + payload, e);
        }
    }


    /**
     * Starts heart rate monitoring and continuously sends heart rate updates
     * over the provided Bluetooth socket's output stream.
     *
     * Also parses the device names to extract user and watch IDs,
     * falling back gracefully in case of errors or invalid formats.
     */
    private void startSendingHeartRate(OutputStream output, BluetoothSocket socket) {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();

        String watchName = "UnknownWatch";
        String androidName = "UnknownAndroid";

        // Step 1: Safely try to retrieve Bluetooth names, with permission checks
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
                        == PackageManager.PERMISSION_GRANTED) {

                    if (adapter != null && adapter.getName() != null) {
                        watchName = adapter.getName();
                    } else {
                        Log.w(TAG, "⚠️ Bluetooth adapter or name is null");
                    }

                    if (socket != null && socket.getRemoteDevice() != null) {
                        @SuppressLint("MissingPermission")
                        String alias = socket.getRemoteDevice().getAlias();
                        if (alias != null) {
                            androidName = alias;
                        } else {
                            Log.w(TAG, "⚠️ Alias from remote device is null");
                        }
                    } else {
                        Log.w(TAG, "⚠️ BluetoothSocket or remote device is null");
                    }

                } else {
                    Log.w(TAG, "⚠️ Missing BLUETOOTH_CONNECT permission, using fallback names.");
                }
            } else {
                // For Android < 12
                if (adapter != null && adapter.getName() != null) {
                    watchName = adapter.getName();
                }

                if (socket != null && socket.getRemoteDevice() != null) {
                    @SuppressLint("MissingPermission")
                    String alias = null;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        alias = socket.getRemoteDevice().getAlias();
                    }
                    if (alias != null) {
                        androidName = alias;
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "❌ Error while retrieving Bluetooth names", e);
        }

        // Step 2: Log the raw names for debugging
        Log.d(TAG, "📛 Watch Bluetooth Name: " + watchName);
        Log.d(TAG, "📛 Connected Android Device Name: " + androidName);

        // Step 3: Parse Watch name into userId and watchId
        if (watchName != null && watchName.matches("^UserID-\\d+-SmartWatchID-\\d+$")) {
            String[] tokens = watchName.split("-");
            userId = tokens[1];
            watchId = tokens[3];
            Log.d(TAG, "✅ Parsed Watch Name: userId=" + userId + ", watchId=" + watchId);
        } else {
            Log.e(TAG, "❌ Invalid or null watch name format: " + watchName);
            userId = "UnknownUser";
            watchId = "UnknownWatch";
        }

        // Step 4: Parse Android name into androidId
        if (androidName != null && androidName.matches("^Android-\\d+$")) {
            androidId = androidName.split("-")[1];
            Log.d(TAG, "✅ Parsed Android Name: androidId=" + androidId);
        } else {
            Log.e(TAG, "❌ Invalid or null Android name format: " + androidName);
            androidId = "UnknownAndroid";
        }

        // Step 5: Start heart rate monitoring and send data
        feedbackController.startHeartRateMonitoring(hr -> {
            try {
                String message = "MonitoringType:HeartRate," +
                        "Value:" + hr + "," +
                        "UserID:" + userId + "," +
                        "SmartWatchID:" + watchId + "," +
                        "AndroidID:" + androidId + "\n";

                output.write(message.getBytes());
                output.flush();

                Log.d(TAG, "📤 Sent heart rate: " + message.trim());
            } catch (IOException e) {
                Log.e(TAG, "❌ Failed to send heart rate", e);
            } catch (Exception e) {
                Log.e(TAG, "❌ Unexpected error while sending heart rate", e);
            }
        });
    }


    /**
     * Stops the Bluetooth SPP server by closing the server socket and halting the server loop.
     * This method should be called when the service is being shut down or the app is cleaned up.
     */
    public void stopServer() {
        // Step 1: Signal the server loop to exit
        isRunning = false;

        try {
            // Step 2: Close the Bluetooth server socket to release the port and unblock .accept()
            if (serverSocket != null) {
                serverSocket.close();
                Log.d(TAG, "🛑 Bluetooth server stopped.");
            }
        } catch (IOException e) {
            // Step 3: Log any exception that occurs during socket shutdown
            Log.e(TAG, "❌ Failed to stop server", e);
        }
    }



}

