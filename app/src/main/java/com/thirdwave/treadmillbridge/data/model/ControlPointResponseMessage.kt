package com.thirdwave.treadmillbridge.data.model

import com.thirdwave.treadmillbridge.ble.FTMSControlPointResponse

/**
 * Represents a control point response from FTMS.
 * Includes timestamp for UI updates (Snackbar display on errors).
 */
data class ControlPointResponseMessage(
    val response: FTMSControlPointResponse,
    val timestamp: Long = System.currentTimeMillis()
)
