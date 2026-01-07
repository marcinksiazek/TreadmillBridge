package com.thirdwave.treadmillbridge.data.model

/**
 * State for HR monitor device discovery/scanning.
 * Separate from DiscoveryState to allow concurrent treadmill and HR scanning.
 */
data class HrDiscoveryState(
    val isScanning: Boolean = false,
    val discoveredDevices: List<DiscoveredDevice> = emptyList(),
    val error: String? = null
)
