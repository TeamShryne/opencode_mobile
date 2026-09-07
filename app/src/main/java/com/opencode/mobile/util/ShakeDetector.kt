package com.opencode.mobile.util

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt

/**
 * Simple shake detector: fires [onShake] when the device is shaken.
 * Threshold ~2.7g, requires 3 shakes within the slop window to avoid false positives.
 */
class ShakeDetector(
    private val onShake: () -> Unit
) : SensorEventListener {

    private var lastShakeTime = 0L
    private var shakeCount = 0

    override fun onSensorChanged(event: SensorEvent) {
        val x = event.values[0] / SensorManager.GRAVITY_EARTH
        val y = event.values[1] / SensorManager.GRAVITY_EARTH
        val z = event.values[2] / SensorManager.GRAVITY_EARTH
        val gForce = sqrt((x * x + y * y + z * z).toDouble()).toFloat()
        if (gForce > SHAKE_THRESHOLD_GRAVITY) {
            val now = System.currentTimeMillis()
            if (lastShakeTime + SHAKE_SLOP_MS > now) return
            if (lastShakeTime + SHAKE_RESET_MS < now) shakeCount = 0
            lastShakeTime = now
            shakeCount++
            if (shakeCount >= SHAKE_COUNT) {
                shakeCount = 0
                onShake()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    fun register(manager: SensorManager) {
        manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let { sensor ->
            manager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
        }
    }

    fun unregister(manager: SensorManager) {
        manager.unregisterListener(this)
    }

    companion object {
        private const val SHAKE_THRESHOLD_GRAVITY = 2.7f
        private const val SHAKE_SLOP_MS = 500L
        private const val SHAKE_RESET_MS = 3000L
        private const val SHAKE_COUNT = 2
    }
}
