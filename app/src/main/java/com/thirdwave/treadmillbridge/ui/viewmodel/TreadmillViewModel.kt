package com.thirdwave.treadmillbridge.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thirdwave.treadmillbridge.data.model.ConnectionState
import com.thirdwave.treadmillbridge.data.model.DiscoveryState
import com.thirdwave.treadmillbridge.data.model.GattServerState
import com.thirdwave.treadmillbridge.data.model.HrDiscoveryState
import com.thirdwave.treadmillbridge.data.model.HrMonitorMetrics
import com.thirdwave.treadmillbridge.data.model.TreadmillMetrics
import com.thirdwave.treadmillbridge.data.repository.HrMonitorRepository
import com.thirdwave.treadmillbridge.data.repository.TreadmillRepository
import com.thirdwave.treadmillbridge.ui.state.TreadmillUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for treadmill UI.
 * Exposes single StateFlow of UI state to Compose.
 * Handles user actions via intent methods.
 */
@HiltViewModel
class TreadmillViewModel @Inject constructor(
    private val repository: TreadmillRepository,
    private val hrRepository: HrMonitorRepository
) : ViewModel() {

    // Combine all repository flows into single UI state
    // Using nested combines since Kotlin's combine only supports up to 5 flows directly
    val uiState: StateFlow<TreadmillUiState> = combine(
        combine(
            repository.metrics,
            repository.connectionState,
            repository.gattServerState,
            repository.discoveryState
        ) { metrics, connection, gattServer, discovery ->
            TreadmillState(metrics, connection, gattServer, discovery)
        },
        combine(
            hrRepository.hrMetrics,
            hrRepository.hrConnectionState,
            hrRepository.hrDiscoveryState
        ) { hrMetrics, hrConnection, hrDiscovery ->
            HrState(hrMetrics, hrConnection, hrDiscovery)
        }
    ) { treadmill, hr ->
        TreadmillUiState(
            metrics = treadmill.metrics,
            connectionState = treadmill.connectionState,
            gattServerState = treadmill.gattServerState,
            discoveryState = treadmill.discoveryState,
            permissionsGranted = true,
            hrMetrics = hr.hrMetrics,
            hrConnectionState = hr.hrConnectionState,
            hrDiscoveryState = hr.hrDiscoveryState
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = TreadmillUiState()
    )

    // Helper data classes for nested combine
    private data class TreadmillState(
        val metrics: TreadmillMetrics,
        val connectionState: ConnectionState,
        val gattServerState: GattServerState,
        val discoveryState: DiscoveryState
    )

    private data class HrState(
        val hrMetrics: HrMonitorMetrics,
        val hrConnectionState: ConnectionState,
        val hrDiscoveryState: HrDiscoveryState
    )

    // Treadmill user actions
    fun onStartScan() {
        viewModelScope.launch {
            repository.startScan()
        }
    }

    fun onStopScan() {
        viewModelScope.launch {
            repository.stopScan()
        }
    }

    fun onConnectToDevice(address: String) {
        viewModelScope.launch {
            repository.stopScan()
            repository.connectToDevice(address)
        }
    }

    fun onDisconnectTreadmill() {
        viewModelScope.launch {
            repository.disconnectTreadmill()
        }
    }

    fun onStartGattServer() {
        viewModelScope.launch {
            repository.startGattServer()
        }
    }

    fun onStopGattServer() {
        viewModelScope.launch {
            repository.stopGattServer()
        }
    }

    // HR Monitor user actions
    fun onStartHrScan() {
        viewModelScope.launch {
            hrRepository.startScan()
        }
    }

    fun onStopHrScan() {
        viewModelScope.launch {
            hrRepository.stopScan()
        }
    }

    fun onConnectToHrDevice(address: String) {
        viewModelScope.launch {
            hrRepository.stopScan()
            hrRepository.connectToDevice(address)
        }
    }

    fun onDisconnectHrMonitor() {
        viewModelScope.launch {
            hrRepository.disconnect()
        }
    }

    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch {
            repository.cleanup()
        }
    }
}
