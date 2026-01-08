package com.thirdwave.treadmillbridge.data.model

import com.thirdwave.treadmillbridge.utils.UnitConversions

/**
 * Real-time metrics from connected treadmill.
 * Immutable data class for thread-safe state sharing.
 */
data class TreadmillMetrics(
    val speedKph: Float? = null,
    val averageSpeedKph: Float? = null,
    val inclinePercent: Float? = null,
    val cadence: Int? = null,
    val totalDistanceMeters: Int? = null,
    val elevationGainMeters: Float? = null,
    val elapsedTimeSeconds: Int? = null,
    val timestamp: Long = System.currentTimeMillis()
) {
    /**
     * Pace formatted as MM:SS string (min/km).
     * Returns null if speed is zero or invalid.
     */
    val paceString: String?
        get() = UnitConversions.speedToPaceString(speedKph ?: 0f)

    /**
     * Total distance formatted in kilometers.
     */
    val totalDistanceKm: Float?
        get() = totalDistanceMeters?.let { it / 1000f }

    /**
     * Elapsed time formatted as HH:MM:SS or MM:SS string.
     */
    val elapsedTimeString: String?
        get() = elapsedTimeSeconds?.let { seconds ->
            val hours = seconds / 3600
            val minutes = (seconds % 3600) / 60
            val secs = seconds % 60
            if (hours > 0) {
                String.format(java.util.Locale.getDefault(), "%d:%02d:%02d", hours, minutes, secs)
            } else {
                String.format(java.util.Locale.getDefault(), "%d:%02d", minutes, secs)
            }
        }
}
