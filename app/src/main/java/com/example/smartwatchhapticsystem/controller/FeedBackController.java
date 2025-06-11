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
     * Uses Android's VibrationEffect API with direct intensity (amplitude) mapping.
     *
     * @param intensity Amplitude of vibration (1–255); 0 = silent
     * @param pulses    Number of vibration pulses
     * @param duration  Duration of each pulse in milliseconds
     * @param interval  Duration of pause between pulses in milliseconds
     */
    public void triggerVibrationForSunAzimuth(int intensity, int pulses, int duration, int interval) {
        Log.d("FeedBackController", "triggerVibrationForSunAzimuth called with " +
                "Intensity=" + intensity + ", Pulses=" + pulses +
                ", Duration=" + duration + ", Interval=" + interval);

        // Validate inputs
        if (pulses > 0 && intensity > 0 && intensity <= 255 && duration > 0) {
            if (vibrator != null && vibrator.hasVibrator()) {
                // Create vibration pattern and amplitude array
                long[] vibrationPattern = new long[pulses * 2 + 1];
                int[] amplitudes = new int[pulses * 2 + 1];

                vibrationPattern[0] = 0;   // Start immediately
                amplitudes[0] = 0;         // No amplitude for initial delay

                for (int i = 1; i <= pulses * 2; i++) {
                    if (i % 2 == 1) {
                        // Vibration pulse
                        vibrationPattern[i] = duration;
                        amplitudes[i] = intensity;
                    } else {
                        // Pause between pulses
                        vibrationPattern[i] = interval;
                        amplitudes[i] = 0;
                    }
                }

                // Trigger the vibration waveform
                VibrationEffect effect = VibrationEffect.createWaveform(vibrationPattern, amplitudes, -1);
                vibrator.vibrate(effect);

                Log.d("FeedBackController", "✅ Sun Azimuth Vibration triggered.");
            } else {
                Log.e("FeedBackController", "❌ Device does not support vibration.");
            }
        } else {
            Log.e("FeedBackController", "❌ Invalid parameters for vibration.");
        }
    }

    /**
     * Triggers a vibration pattern in response to heart rate-related feedback.
     * Uses amplitude scaling to reflect intensity per pulse.
     *
     * @param intensity Intensity level (1–255) representing vibration strength
     * @param pulses    Number of vibration pulses to deliver
     * @param duration  Duration (in ms) of each pulse
     * @param interval  Duration (in ms) of pause between pulses
     */
    public void triggerHeartRateVibration(int intensity, int pulses, int duration, int interval) {
        Log.d("FeedBackController", "triggerHeartRateVibration called with intensity: "
                + intensity + ", pulses: " + pulses + ", duration: " + duration + ", interval: " + interval);

        if (pulses > 0 && intensity > 0 && duration > 0) {

            if (vibrator != null && vibrator.hasVibrator()) {

                // 1. Build vibration pattern: [0, duration, interval, duration, interval, ...]
                long[] vibrationPattern = new long[pulses * 2 + 1];
                vibrationPattern[0] = 0; // no initial delay

                // 2. Build amplitude pattern: [0, intensity, 0, intensity, 0, ...]
                int[] amplitudes = new int[pulses * 2 + 1];
                amplitudes[0] = 0; // no amplitude during initial delay

                for (int i = 1; i <= pulses * 2; i++) {
                    if (i % 2 == 0) {
                        vibrationPattern[i] = interval;   // pauses
                        amplitudes[i] = 0;
                    } else {
                        vibrationPattern[i] = duration;   // vibrations
                        amplitudes[i] = intensity;        // apply intensity
                    }
                }

                vibrator.vibrate(VibrationEffect.createWaveform(vibrationPattern, amplitudes, -1));

                Log.d("FeedBackController", "✅ Heart rate vibration triggered.");
            } else {
                Log.e("FeedBackController", "❌ Device does not support vibration.");
            }

        } else {
            Log.e("FeedBackController", "❌ Invalid parameters for heart rate vibration.");
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
