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
import com.thirdwave.treadmillbridge.data.model.ControlTargets
import com.thirdwave.treadmillbridge.data.model.TargetSettingFeatures
import com.thirdwave.treadmillbridge.data.model.TreadmillControlState
import com.thirdwave.treadmillbridge.data.model.TreadmillFeatures
import com.thirdwave.treadmillbridge.data.model.TreadmillMetrics
import com.thirdwave.treadmillbridge.data.model.TreadmillRunningState
import com.thirdwave.treadmillbridge.data.repository.HrMonitorRepository
import com.thirdwave.treadmillbridge.data.repository.TreadmillRepository
import com.thirdwave.treadmillbridge.ui.state.TreadmillUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
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

    private companion object {
        const val DEBOUNCE_DELAY_MS = 300L
        const val HEARTBEAT_INTERVAL_MS = 3000L
    }

    // Heartbeat job
    private var heartbeatJob: Job? = null

    // Track current target values for heartbeat
    private var currentTargetSpeed = 0f
    private var currentTargetIncline = 0f

    // Control targets for UI state
    private val _controlTargets = MutableStateFlow(ControlTargets())

    init {
        // Monitor running state to start/stop heartbeat
        viewModelScope.launch {
            repository.runningState.collect { state ->
                when (state) {
                    is TreadmillRunningState.Running -> {
                        // If speed is 0 when starting, set to first step > 0
                        if (currentTargetSpeed == 0f &&
                            repository.controlState.value == TreadmillControlState.Controlling) {
                            val speedStep = repository.speedRange.value.stepKmh
                            currentTargetSpeed = speedStep
                            _controlTargets.value = _controlTargets.value.copy(targetSpeedKmh = speedStep)
                            repository.setTargetSpeed(speedStep)
                        }
                        // Start heartbeat if we have control and speed > 0
                        if (repository.controlState.value == TreadmillControlState.Controlling &&
                            currentTargetSpeed > 0f) {
                            startHeartbeat()
                        }
                    }
                    is TreadmillRunningState.Paused,
                    is TreadmillRunningState.Stopped,
                    is TreadmillRunningState.Unknown -> {
                        // SAFETY: Stop heartbeat immediately when not running
                        stopHeartbeat()
                    }
                }
            }
        }

        // Monitor control state to reset on control loss
        viewModelScope.launch {
            repository.controlState.collect { state ->
                when (state) {
                    is TreadmillControlState.NotControlling -> {
                        stopHeartbeat()
                        // Reset targets when control is lost
                        currentTargetSpeed = 0f
                        currentTargetIncline = 0f
                        _controlTargets.value = ControlTargets()
                    }
                    is TreadmillControlState.Controlling -> {
                        // Control granted, but don't start heartbeat yet
                        // Wait for Running state (0x02) before sending speed commands
                    }
                }
            }
        }

        // Monitor connection state to stop heartbeat on disconnect
        viewModelScope.launch {
            repository.connectionState.collect { state ->
                if (state !is ConnectionState.Connected) {
                    // SAFETY: Stop heartbeat immediately on any disconnection
                    stopHeartbeat()
                }
            }
        }
    }

    /**
     * Check if heartbeat should be active.
     * SAFETY: Only send speed commands when belt is actually running.
     */
    private fun shouldSendHeartbeat(): Boolean {
        return repository.controlState.value == TreadmillControlState.Controlling &&
                repository.runningState.value == TreadmillRunningState.Running &&
                currentTargetSpeed > 0f
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = viewModelScope.launch {
            while (isActive) {
                delay(HEARTBEAT_INTERVAL_MS)
                // SAFETY: Double-check conditions before sending
                if (shouldSendHeartbeat()) {
                    repository.setTargetSpeed(currentTargetSpeed)
                }
            }
        }
    }

    private fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

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
        },
        combine(
            repository.controlState,
            repository.runningState,
            _controlTargets
        ) { controlState, runningState, targets ->
            ControlState(controlState, runningState, targets)
        }
    ) { core, discoveryAndStatus, hr, control ->
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
            hrDiscoveryState = hr.hrDiscoveryState,
            controlState = control.controlState,
            runningState = control.runningState,
            controlTargets = control.targets
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

    private data class ControlState(
        val controlState: TreadmillControlState,
        val runningState: TreadmillRunningState,
        val targets: ControlTargets
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
            currentTargetSpeed = speedKmh
            _controlTargets.value = _controlTargets.value.copy(targetSpeedKmh = speedKmh)

            // SAFETY: Only send speed command if belt is actually running
            if (repository.runningState.value == TreadmillRunningState.Running) {
                repository.setTargetSpeed(speedKmh)

                // Restart heartbeat if speed > 0
                if (speedKmh > 0f) {
                    startHeartbeat()
                } else {
                    stopHeartbeat()
                }
            }
        }
    }

    fun onSetTargetInclination(inclinePercent: Float) {
        inclineDebounceJob?.cancel()
        inclineDebounceJob = viewModelScope.launch {
            delay(DEBOUNCE_DELAY_MS)
            currentTargetIncline = inclinePercent
            _controlTargets.value = _controlTargets.value.copy(targetInclinePercent = inclinePercent)

            // SAFETY: Only send incline command if belt is actually running
            if (repository.runningState.value == TreadmillRunningState.Running) {
                repository.setTargetInclination(inclinePercent)
            }
        }
    }

    fun onStartOrResume() {
        viewModelScope.launch {
            // Send startOrResume command
            // SAFETY: Do NOT set button to green yet (wait for 0x02)
            // SAFETY: Do NOT enable sliders yet (wait for 0x02)
            // SAFETY: Do NOT send speed yet (wait for 0x02)
            repository.startOrResume()
        }
    }

    fun onStopMachine() {
        // SAFETY: Stop heartbeat IMMEDIATELY (before coroutine dispatch)
        stopHeartbeat()
        // Reset targets
        currentTargetSpeed = 0f
        currentTargetIncline = 0f
        _controlTargets.value = ControlTargets()
        // Send stop command
        viewModelScope.launch {
            repository.stopMachine()
        }
    }

    fun onPauseMachine() {
        // SAFETY: Stop heartbeat IMMEDIATELY (before coroutine dispatch)
        stopHeartbeat()
        // SAFETY: Do NOT reset targets (retain for resume)
        // Send pause command
        viewModelScope.launch {
            repository.pauseMachine()
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopHeartbeat()
        viewModelScope.launch {
            repository.cleanup()
        }
    }
}
