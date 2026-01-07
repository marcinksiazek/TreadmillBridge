package com.thirdwave.treadmillbridge.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thirdwave.treadmillbridge.ble.InclineRange
import com.thirdwave.treadmillbridge.ble.SpeedRange
import com.thirdwave.treadmillbridge.data.model.ConnectionState
import com.thirdwave.treadmillbridge.data.model.ControlPointResponseMessage
import com.thirdwave.treadmillbridge.data.model.DiscoveryState
import com.thirdwave.treadmillbridge.data.model.GattServerState
import com.thirdwave.treadmillbridge.data.model.HrDiscoveryState
import com.thirdwave.treadmillbridge.data.model.HrMonitorMetrics
import com.thirdwave.treadmillbridge.data.model.MachineStatusMessage
import com.thirdwave.treadmillbridge.data.model.TargetSettingFeatures
import com.thirdwave.treadmillbridge.data.model.TreadmillFeatures
import com.thirdwave.treadmillbridge.data.model.TreadmillMetrics
import com.thirdwave.treadmillbridge.data.repository.HrMonitorRepository
import com.thirdwave.treadmillbridge.data.repository.TreadmillRepository
import com.thirdwave.treadmillbridge.ui.state.TreadmillUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
            repository.features,
            repository.targetSettingFeatures,
            repository.connectionState,
            repository.gattServerState
        ) { metrics, features, targetFeatures, connection, gattServer ->
            TreadmillCoreState(metrics, features, targetFeatures, connection, gattServer)
        },
        combine(
            repository.discoveryState,
            repository.machineStatusMessage,
            repository.controlPointResponse,
            repository.speedRange,
            repository.inclineRange
        ) { discovery, statusMessage, controlResponse, speedRange, inclineRange ->
            DiscoveryAndStatusState(discovery, statusMessage, controlResponse, speedRange, inclineRange)
        },
        combine(
            hrRepository.hrMetrics,
            hrRepository.hrConnectionState,
            hrRepository.hrDiscoveryState
        ) { hrMetrics, hrConnection, hrDiscovery ->
            HrState(hrMetrics, hrConnection, hrDiscovery)
        }
    ) { core, discoveryAndStatus, hr ->
        TreadmillUiState(
            metrics = core.metrics,
            treadmillFeatures = core.features,
            targetSettingFeatures = core.targetSettingFeatures,
            connectionState = core.connectionState,
            gattServerState = core.gattServerState,
            discoveryState = discoveryAndStatus.discoveryState,
            machineStatusMessage = discoveryAndStatus.statusMessage,
            controlPointResponse = discoveryAndStatus.controlResponse,
            speedRange = discoveryAndStatus.speedRange,
            inclineRange = discoveryAndStatus.inclineRange,
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
    private data class TreadmillCoreState(
        val metrics: TreadmillMetrics,
        val features: TreadmillFeatures?,
        val targetSettingFeatures: TargetSettingFeatures?,
        val connectionState: ConnectionState,
        val gattServerState: GattServerState
    )

    private data class DiscoveryAndStatusState(
        val discoveryState: DiscoveryState,
        val statusMessage: MachineStatusMessage?,
        val controlResponse: ControlPointResponseMessage?,
        val speedRange: SpeedRange,
        val inclineRange: InclineRange
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

    // Control Point commands
    private companion object {
        const val DEBOUNCE_DELAY_MS = 300L
    }

    private var speedDebounceJob: Job? = null
    private var inclineDebounceJob: Job? = null

    fun onRequestControl() {
        viewModelScope.launch {
            repository.requestControl()
        }
    }

    fun onResetMachine() {
        viewModelScope.launch {
            repository.resetMachine()
        }
    }

    fun onSetTargetSpeed(speedKmh: Float) {
        speedDebounceJob?.cancel()
        speedDebounceJob = viewModelScope.launch {
            delay(DEBOUNCE_DELAY_MS)
            repository.setTargetSpeed(speedKmh)
        }
    }

    fun onSetTargetInclination(inclinePercent: Float) {
        inclineDebounceJob?.cancel()
        inclineDebounceJob = viewModelScope.launch {
            delay(DEBOUNCE_DELAY_MS)
            repository.setTargetInclination(inclinePercent)
        }
    }

    fun onStartOrResume() {
        viewModelScope.launch {
            repository.startOrResume()
        }
    }

    fun onStopMachine() {
        viewModelScope.launch {
            repository.stopMachine()
        }
    }

    fun onPauseMachine() {
        viewModelScope.launch {
            repository.pauseMachine()
        }
    }

    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch {
            repository.cleanup()
        }
    }
}
