package com.thirdwave.treadmillbridge.ui.state

import com.thirdwave.treadmillbridge.data.model.ConnectionState
import com.thirdwave.treadmillbridge.data.model.DiscoveryState
import com.thirdwave.treadmillbridge.data.model.GattServerState
import com.thirdwave.treadmillbridge.data.model.TreadmillMetrics

/**
 * Root UI state combining all app state.
 * Single source of truth for Compose UI.
 */
data class TreadmillUiState(
    val metrics: TreadmillMetrics = TreadmillMetrics(),
    val connectionState: ConnectionState = ConnectionState.Disconnected,
    val gattServerState: GattServerState = GattServerState.Stopped,
    val discoveryState: DiscoveryState = DiscoveryState(),
    val permissionsGranted: Boolean = false
)
