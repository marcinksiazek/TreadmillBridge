package com.thirdwave.treadmillbridge.data.model

import com.thirdwave.treadmillbridge.utils.UnitConversions

/**
 * Real-time metrics from connected treadmill.
 * Immutable data class for thread-safe state sharing.
 */
data class TreadmillMetrics(
    val speedKph: Float = 0f,
    val inclinePercent: Float = 0f,
    val cadence: Int = 0,
    val timestamp: Long = System.currentTimeMillis()
) {
    /**
     * Pace formatted as MM:SS string (min/km).
     * Returns null if speed is zero or invalid.
     */
    val paceString: String?
        get() = UnitConversions.speedToPaceString(speedKph)
}
