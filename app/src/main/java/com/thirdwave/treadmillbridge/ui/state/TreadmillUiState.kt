package com.thirdwave.treadmillbridge.ui.state

import com.thirdwave.treadmillbridge.data.model.ConnectionState
import com.thirdwave.treadmillbridge.data.model.DiscoveredDevice
import com.thirdwave.treadmillbridge.data.model.DiscoveryState
import com.thirdwave.treadmillbridge.data.model.GattServerState
import com.thirdwave.treadmillbridge.data.model.HrDiscoveryState
import com.thirdwave.treadmillbridge.data.model.HrMonitorMetrics
import com.thirdwave.treadmillbridge.data.model.TargetSettingFeatures
import com.thirdwave.treadmillbridge.data.model.TreadmillFeatures
import com.thirdwave.treadmillbridge.data.model.TreadmillMetrics

/**
 * Root UI state combining all app state.
 * Single source of truth for Compose UI.
 */
data class TreadmillUiState(
    // Treadmill state
    val metrics: TreadmillMetrics = TreadmillMetrics(),
    val treadmillFeatures: TreadmillFeatures? = null,
    val targetSettingFeatures: TargetSettingFeatures? = null,
    val connectionState: ConnectionState = ConnectionState.Disconnected,
    val gattServerState: GattServerState = GattServerState.Stopped,
    val discoveryState: DiscoveryState = DiscoveryState(),
    val permissionsGranted: Boolean = false,

    // HR Monitor state
    val hrMetrics: HrMonitorMetrics = HrMonitorMetrics(),
    val hrConnectionState: ConnectionState = ConnectionState.Disconnected,
    val hrDiscoveryState: HrDiscoveryState = HrDiscoveryState()
) {
    /**
     * Get the connected HR device info if available.
     * Matches by device name from connection state to discovery list.
     */
    val connectedHrDevice: DiscoveredDevice?
        get() = (hrConnectionState as? ConnectionState.Connected)?.let { connected ->
            hrDiscoveryState.discoveredDevices.find {
                it.name == connected.deviceName || it.address == connected.deviceName
            }
        }

    /**
     * Get the connected treadmill device info if available.
     */
    val connectedTreadmillDevice: DiscoveredDevice?
        get() = (connectionState as? ConnectionState.Connected)?.let { connected ->
            discoveryState.discoveredDevices.find {
                it.name == connected.deviceName || it.address == connected.deviceName
            }
        }
}
