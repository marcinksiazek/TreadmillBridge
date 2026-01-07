package com.thirdwave.treadmillbridge.ui.state

import com.thirdwave.treadmillbridge.ble.InclineRange
import com.thirdwave.treadmillbridge.ble.SpeedRange
import com.thirdwave.treadmillbridge.data.model.ConnectionState
import com.thirdwave.treadmillbridge.data.model.ControlPointResponseMessage
import com.thirdwave.treadmillbridge.data.model.DiscoveredDevice
import com.thirdwave.treadmillbridge.data.model.DiscoveryState
import com.thirdwave.treadmillbridge.data.model.GattServerState
import com.thirdwave.treadmillbridge.data.model.HrDiscoveryState
import com.thirdwave.treadmillbridge.data.model.HrMonitorMetrics
import com.thirdwave.treadmillbridge.data.model.MachineStatusMessage
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
    val machineStatusMessage: MachineStatusMessage? = null,
    val controlPointResponse: ControlPointResponseMessage? = null,
    val speedRange: SpeedRange = SpeedRange(),
    val inclineRange: InclineRange = InclineRange(),
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

    /**
     * Whether the treadmill is connected.
     */
    val isConnected: Boolean
        get() = connectionState is ConnectionState.Connected

    /**
     * Whether speed target setting is supported by the connected treadmill.
     */
    val supportsSpeedControl: Boolean
        get() = targetSettingFeatures?.speedTargetSettingSupported == true

    /**
     * Whether incline target setting is supported by the connected treadmill.
     */
    val supportsInclineControl: Boolean
        get() = targetSettingFeatures?.inclinationTargetSettingSupported == true
}
