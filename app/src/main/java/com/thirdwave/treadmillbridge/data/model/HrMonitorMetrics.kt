package com.thirdwave.treadmillbridge.data.model

/**
 * Real-time metrics from connected HR monitor.
 * Immutable data class for thread-safe state sharing.
 */
data class HrMonitorMetrics(
    val heartRateBpm: Int = 0,
    val batteryPercent: Int? = null,
    val sensorContact: Boolean = false,
    val rrIntervals: List<Int>? = null,
    val timestamp: Long = System.currentTimeMillis()
)
