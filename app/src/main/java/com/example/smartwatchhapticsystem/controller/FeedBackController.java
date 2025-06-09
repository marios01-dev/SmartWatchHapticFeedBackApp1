package com.example.smartwatchhapticsystem.controller;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
public class FeedBackController implements SensorEventListener {


    private Context context;
    private SensorManager sensorManager;
    private Sensor heartRateSensor;
    private Vibrator vibrator;
    private boolean isMonitoring = false;
    private OnHeartRateUpdateListener heartRateListener;

    public FeedBackController(Context context) {
        this.context = context;
        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
    }

    /**
     * Triggers a vibration pattern based on sun azimuth feedback logic.
     * Converts input parameters into a waveform vibration using Android's VibrationEffect API.
     * Only runs if the device supports vibration and all parameters are valid.
     *
     * @param intensity A multiplier applied to the duration of each pulse (acts like a strength factor)
     * @param pulses    The number of vibration pulses to send
     * @param duration  Duration of each vibration pulse (in ms, multiplied by intensity)
     * @param interval  Delay between vibration pulses (in ms)
     */
    public void triggerVibrationForSunAzimuth(int intensity, int pulses, int duration, int interval) {
        Log.d("FeedBackController", "triggerVibrationForSunAzimuth called with " +
                "Intensity=" + intensity + ", Pulses=" + pulses +
                ", Duration=" + duration + ", Interval=" + interval);

        // Step 1: Validate parameters – all values must be positive
        if (pulses > 0 && intensity > 0 && duration > 0) {

            // Step 2: Ensure the device has a vibrator and it's available
            if (vibrator != null && vibrator.hasVibrator()) {

                // Step 3: Create a vibration pattern: [delay, vibration, pause, vibration, pause...]
                // Pattern length is (pulses * 2 + 1):
                // - First element is 0 (start immediately)
                // - Odd indices = vibration durations
                // - Even indices = intervals between vibrations
                long[] vibrationPattern = new long[pulses * 2 + 1];
                vibrationPattern[0] = 0; // No initial delay before first vibration

                for (int i = 1; i <= pulses * 2; i++) {
                    // Even indices (i % 2 == 0): interval/pause duration
                    // Odd indices (i % 2 == 1): vibration duration (scaled by intensity)
                    vibrationPattern[i] = (i % 2 == 0) ? interval : (long) duration * intensity;
                }

                // Step 4: Trigger the vibration waveform with no repeat (-1)
                vibrator.vibrate(VibrationEffect.createWaveform(vibrationPattern, -1));

                Log.d("FeedBackController", "✅ Sun Azimuth Vibration triggered.");
            } else {
                // Device does not support vibration hardware
                Log.e("FeedBackController", "❌ Device does not support vibration.");
            }
        } else {
            // One or more parameters are invalid (non-positive)
            Log.e("FeedBackController", "❌ Invalid parameters for Sun Azimuth vibration.");
        }
    }

    /**
     * Triggers a vibration pattern in response to heart rate-related feedback.
     * The pattern consists of repeated pulses, each followed by a pause.
     *
     * @param intensity Intensity level requested (not used in pattern directly, unlike in sun azimuth method)
     * @param pulses    Number of vibration pulses to deliver
     * @param duration  Duration (in ms) of each pulse
     * @param interval  Duration (in ms) of pause between pulses
     */
    public void triggerHeartRateVibration(int intensity, int pulses, int duration, int interval) {
        Log.d("FeedBackController", "triggerHeartRateVibration called with intensity: "
                + intensity + ", pulses: " + pulses + ", duration: " + duration + ", interval: " + interval);

        // Step 1: Validate that all necessary values are positive
        if (pulses > 0 && intensity > 0 && duration > 0) {

            // Step 2: Ensure the device has a vibrator
            if (vibrator != null && vibrator.hasVibrator()) {

                // Step 3: Build the vibration pattern
                // Pattern format: [delayBeforeStart, vibration, pause, vibration, pause, ...]
                // For N pulses → pattern length = N * 2 + 1
                long[] vibrationPattern = new long[pulses * 2 + 1];
                vibrationPattern[0] = 0; // No delay before the first pulse

                for (int i = 1; i <= pulses * 2; i++) {
                    // Odd indices → vibration duration
                    // Even indices → interval between pulses
                    vibrationPattern[i] = (i % 2 == 0) ? interval : duration;
                }

                // Step 4: Trigger the vibration pattern (no repeat: -1)
                vibrator.vibrate(VibrationEffect.createWaveform(vibrationPattern, -1));

                Log.d("FeedBackController", "✅ Custom Vibration triggered.");
            } else {
                // Vibrator service unavailable or not supported
                Log.e("FeedBackController", "❌ Device does not support vibration.");
            }

        } else {
            // Invalid parameters (e.g., zero or negative values)
            Log.e("FeedBackController", "Invalid parameters for custom vibration.");
        }
    }


    /**
     * Starts continuous heart-rate monitoring using the device’s built-in BODY SENSORS API.
     * Registers this class (implements SensorEventListener) to receive HR updates and
     * forwards each reading to a client-supplied callback.
     *
     * @param listener A callback that will receive heart-rate updates (beats-per-minute).
     */
    public void startHeartRateMonitoring(OnHeartRateUpdateListener listener) {

        /* ── 1. Prevent duplicate registration ───────────────────────────────────── */
        if (isMonitoring) {                                 // Already active?
            Log.d("FeedBackController", "ℹ️ Heart-Rate monitoring already active.");
            return;                                         // Exit early
        }

        /* ── 2️.  Retrieve the heart-rate sensor from the SensorManager ────────────── */
        if (sensorManager != null) {
            heartRateSensor = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE);

            if (heartRateSensor != null) {
                /* ── 3.  Save the client’s callback and register listener ─────────── */
                this.heartRateListener = listener;          // Store callback for later updates
                sensorManager.registerListener(
                        this,                               // Current class implements SensorEventListener
                        heartRateSensor,
                        SensorManager.SENSOR_DELAY_NORMAL   // Sampling rate (~1 Hz, varies by device)
                );

                isMonitoring = true;                        // Mark as active
                Log.d("FeedBackController", "✅ Heart-Rate monitoring started…");
            } else {
                Log.e("FeedBackController", "❌ Heart-rate sensor not available!");
            }
        }
    }



    /**
     * Stops active heart rate monitoring by unregistering the sensor listener.
     * Ensures resources are released and callbacks are cleared to prevent memory leaks.
     */
    public void stopHeartRateMonitoring() {
        // Step 1: Only proceed if monitoring is active and sensorManager is available
        if (isMonitoring && sensorManager != null) {
            // Step 2: Unregister this class from receiving heart rate sensor updates
            sensorManager.unregisterListener(this);

            // Step 3: Reset internal state
            isMonitoring = false;
            heartRateListener = null; // 🧹 Clear the listener to prevent memory leaks or unexpected callbacks

            Log.d("FeedBackController", "⛔ Heart Rate Monitoring Stopped.");
        } else {
            // Step 4: Monitoring wasn't active, nothing to stop
            Log.d("FeedBackController", "ℹ️ Heart Rate Monitoring is not active.");
        }
    }



    /**
     * Called automatically when new sensor data is available.
     * Specifically listens for heart rate sensor updates and forwards the detected value
     * to the registered listener callback.
     *
     * @param event The sensor event containing new heart rate data.
     */
    @Override
    public void onSensorChanged(SensorEvent event) {
        // Step 1: Ensure the event is from the heart rate sensor
        if (event.sensor.getType() == Sensor.TYPE_HEART_RATE) {
            // Step 2: Extract heart rate value (as float → int)
            int heartRate = Math.round(event.values[0]);
            Log.d("FeedBackController", "❤️ Heart Rate Detected: " + heartRate);

            // Step 3: Notify the registered listener (if any)
            if (heartRateListener != null) {
                heartRateListener.onUpdate(heartRate);  // Pass the detected BPM to the app
            }
        }
    }


    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Not needed for heart rate
    }
    public interface OnHeartRateUpdateListener {
        void onUpdate(int heartRate);
    }


}
