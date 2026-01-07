package com.thirdwave.treadmillbridge.data.model

/**
 * Holds target values set by the app when controlling the treadmill.
 * These values are retained during pause but reset on stop or control loss.
 */
data class ControlTargets(
    val targetSpeedKmh: Float = 0f,
    val targetInclinePercent: Float = 0f
)
