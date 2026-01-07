package com.thirdwave.treadmillbridge.data.model

import com.thirdwave.treadmillbridge.ble.FTMSMachineStatus

/**
 * Represents a machine status message from FTMS.
 * Includes timestamp for UI updates (Snackbar display).
 */
data class MachineStatusMessage(
    val status: FTMSMachineStatus,
    val timestamp: Long = System.currentTimeMillis()
)
