package com.thirdwave.treadmillbridge.data.model

/**
 * State for device discovery/scanning.
 */
data class DiscoveryState(
    val isScanning: Boolean = false,
    val discoveredDevices: List<DiscoveredDevice> = emptyList(),
    val error: String? = null
)
