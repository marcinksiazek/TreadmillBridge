package com.thirdwave.treadmillbridge.data.source

import android.util.Log
import com.thirdwave.treadmillbridge.ble.FTMSControlPointCommand
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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Data source for BLE operations.
 * Converts Nordic BLE callbacks to Kotlin Flow.
 * Thread-safe using coroutines and MutableStateFlow.
 */
@Singleton
class BluetoothDataSource @Inject constructor(
    private val treadmillBleManager: TreadmillBleManager,
    private val hrMonitorBleManager: HrMonitorBleManager,
    private val gattServerManager: GattServerManager,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val TAG = "BluetoothDataSource"
    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    
    // Mutable state holders (private) - Treadmill
    private val _treadmillMetrics = MutableStateFlow(TreadmillMetrics())
    private val _treadmillFeatures = MutableStateFlow<TreadmillFeatures?>(null)
    private val _targetSettingFeatures = MutableStateFlow<TargetSettingFeatures?>(null)
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    private val _gattServerState = MutableStateFlow<GattServerState>(GattServerState.Stopped)
    private val _discoveryState = MutableStateFlow(DiscoveryState())
    private val _machineStatusMessage = MutableStateFlow<MachineStatusMessage?>(null)
    private val _controlPointResponse = MutableStateFlow<ControlPointResponseMessage?>(null)
    private val _speedRange = MutableStateFlow(SpeedRange.default())
    private val _inclineRange = MutableStateFlow(InclineRange.default())

    // Mutable state holders (private) - HR Monitor
    private val _hrMetrics = MutableStateFlow(HrMonitorMetrics())
    private val _hrConnectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    private val _hrDiscoveryState = MutableStateFlow(HrDiscoveryState())

    // Public read-only StateFlows - Treadmill
    val treadmillMetrics: StateFlow<TreadmillMetrics> = _treadmillMetrics.asStateFlow()
    val treadmillFeatures: StateFlow<TreadmillFeatures?> = _treadmillFeatures.asStateFlow()
    val targetSettingFeatures: StateFlow<TargetSettingFeatures?> = _targetSettingFeatures.asStateFlow()
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()
    val gattServerState: StateFlow<GattServerState> = _gattServerState.asStateFlow()
    val discoveryState: StateFlow<DiscoveryState> = _discoveryState.asStateFlow()
    val machineStatusMessage: StateFlow<MachineStatusMessage?> = _machineStatusMessage.asStateFlow()
    val controlPointResponse: StateFlow<ControlPointResponseMessage?> = _controlPointResponse.asStateFlow()
    val speedRange: StateFlow<SpeedRange> = _speedRange.asStateFlow()
    val inclineRange: StateFlow<InclineRange> = _inclineRange.asStateFlow()

    // Public read-only StateFlows - HR Monitor
    val hrMetrics: StateFlow<HrMonitorMetrics> = _hrMetrics.asStateFlow()
    val hrConnectionState: StateFlow<ConnectionState> = _hrConnectionState.asStateFlow()
    val hrDiscoveryState: StateFlow<HrDiscoveryState> = _hrDiscoveryState.asStateFlow()
    
    init {
        // Set up callbacks from BLE managers → StateFlow updates
        setupTreadmillCallbacks()
        setupHrMonitorCallbacks()
        setupGattServerCallbacks()
        setupMetricsSync()
    }
    
    private fun setupTreadmillCallbacks() {
        treadmillBleManager.onConnectionStateChanged = { connected, deviceName ->
            scope.launch {
                _connectionState.value = if (connected) {
                    ConnectionState.Connected(deviceName ?: "Unknown")
                } else {
                    // Reset metrics, features, and status on disconnect
                    _treadmillMetrics.value = TreadmillMetrics()
                    _treadmillFeatures.value = null
                    _targetSettingFeatures.value = null
                    _machineStatusMessage.value = null
                    _controlPointResponse.value = null
                    _speedRange.value = SpeedRange.default()
                    _inclineRange.value = InclineRange.default()
                    ConnectionState.Disconnected
                }
            }
        }

        treadmillBleManager.onFeaturesReceived = { parsedFeatures ->
            scope.launch {
                _treadmillFeatures.value = parsedFeatures.machineFeatures
                _targetSettingFeatures.value = parsedFeatures.targetSettingFeatures
                Log.d(TAG, "Machine features received: ${parsedFeatures.machineFeatures.supportedFeatureLabels}")
                parsedFeatures.targetSettingFeatures?.let {
                    Log.d(TAG, "Target setting features received: ${it.supportedFeatureLabels}")
                }
            }
        }

        treadmillBleManager.onMachineStatusReceived = { status ->
            scope.launch {
                _machineStatusMessage.value = MachineStatusMessage(status)
                Log.d(TAG, "Machine status: ${status.humanReadableMessage}")
            }
        }

        treadmillBleManager.onControlPointResponse = { response ->
            scope.launch {
                _controlPointResponse.value = ControlPointResponseMessage(response)
                Log.d(TAG, "Control point response: ${response.humanReadableMessage}")
            }
        }

        treadmillBleManager.onSpeedRangeReceived = { range ->
            scope.launch {
                _speedRange.value = range
                Log.d(TAG, "Speed range: ${range.minKmh}-${range.maxKmh} km/h, step ${range.stepKmh}")
            }
        }

        treadmillBleManager.onInclineRangeReceived = { range ->
            scope.launch {
                _inclineRange.value = range
                Log.d(TAG, "Incline range: ${range.minPercent}-${range.maxPercent}%, step ${range.stepPercent}")
            }
        }

        treadmillBleManager.onMetricsReceived = { ftmsData ->
            scope.launch {
                _treadmillMetrics.value = TreadmillMetrics(
                    speedKph = ftmsData.instantaneousSpeedKmh,
                    inclinePercent = ftmsData.inclinationPercent ?: 0f,
                    cadence = 0 // FTMS doesn't have cadence by default
                )
                Log.d(TAG, "Metrics updated: speed=${ftmsData.instantaneousSpeedKmh} kph")
            }
        }
        
        treadmillBleManager.onDeviceDiscovered = { device ->
            scope.launch {
                val current = _discoveryState.value
                if (current.discoveredDevices.none { it.address == device.address }) {
                    _discoveryState.value = current.copy(
                        discoveredDevices = current.discoveredDevices + device
                    )
                    Log.d(TAG, "Device discovered: ${device.name} (${device.address})")
                }
            }
        }
    }

    private fun setupHrMonitorCallbacks() {
        hrMonitorBleManager.onConnectionStateChanged = { connected, deviceName ->
            scope.launch {
                _hrConnectionState.value = if (connected) {
                    ConnectionState.Connected(deviceName ?: "Unknown HR")
                } else {
                    // Reset metrics on disconnect
                    _hrMetrics.value = HrMonitorMetrics()
                    ConnectionState.Disconnected
                }
            }
        }

        hrMonitorBleManager.onHeartRateReceived = { hrData ->
            scope.launch {
                _hrMetrics.value = _hrMetrics.value.copy(
                    heartRateBpm = hrData.heartRateBpm,
                    sensorContact = hrData.sensorContactDetected,
                    rrIntervals = hrData.rrIntervalsMs
                )
                Log.d(TAG, "HR updated: ${hrData.heartRateBpm} bpm")
            }
        }

        hrMonitorBleManager.onBatteryLevelReceived = { level ->
            scope.launch {
                _hrMetrics.value = _hrMetrics.value.copy(batteryPercent = level)
                Log.d(TAG, "HR battery: $level%")
            }
        }

        hrMonitorBleManager.onDeviceDiscovered = { device ->
            scope.launch {
                val current = _hrDiscoveryState.value
                if (current.discoveredDevices.none { it.address == device.address }) {
                    _hrDiscoveryState.value = current.copy(
                        discoveredDevices = current.discoveredDevices + device
                    )
                    Log.d(TAG, "HR device discovered: ${device.name} (${device.address})")
                }
            }
        }
    }

    private fun setupGattServerCallbacks() {
        gattServerManager.onServerStateChanged = { running ->
            scope.launch {
                _gattServerState.value = if (running) {
                    GattServerState.Running
                } else {
                    GattServerState.Stopped
                }
                Log.d(TAG, "GATT server state: ${if (running) "Running" else "Stopped"}")
            }
        }
        
        gattServerManager.onClientConnected = { clientName ->
            scope.launch {
                _gattServerState.value = GattServerState.ClientConnected(clientName)
                Log.d(TAG, "GATT client connected: $clientName")
            }
        }
        
        gattServerManager.onClientDisconnected = {
            scope.launch {
                _gattServerState.value = GattServerState.Running
                Log.d(TAG, "GATT client disconnected")
            }
        }
    }
    
    private fun setupMetricsSync() {
        // Update GATT server when metrics change
        scope.launch {
            _treadmillMetrics.collect { metrics ->
                gattServerManager.updateMetrics(metrics)
            }
        }
    }
    
    suspend fun startScan() = withContext(ioDispatcher) {
        _discoveryState.value = DiscoveryState(isScanning = true, discoveredDevices = emptyList())
        treadmillBleManager.startScan()
        Log.i(TAG, "Started BLE scan")
    }
    
    suspend fun stopScan() = withContext(ioDispatcher) {
        treadmillBleManager.stopScan()
        _discoveryState.value = _discoveryState.value.copy(isScanning = false)
        Log.i(TAG, "Stopped BLE scan")
    }
    
    suspend fun connectToDevice(address: String) = withContext(ioDispatcher) {
        _connectionState.value = ConnectionState.Connecting
        treadmillBleManager.connectToDevice(address)
        // State update happens via callback
        Log.i(TAG, "Connecting to device: $address")
    }
    
    suspend fun disconnectTreadmill() = withContext(ioDispatcher) {
        treadmillBleManager.disconnect()
        _treadmillMetrics.value = TreadmillMetrics() // Reset metrics
        _treadmillFeatures.value = null // Reset features
        _targetSettingFeatures.value = null // Reset target setting features
        _machineStatusMessage.value = null // Reset status message
        _controlPointResponse.value = null // Reset control point response
        _speedRange.value = SpeedRange.default() // Reset to default speed range
        _inclineRange.value = InclineRange.default() // Reset to default incline range
        Log.i(TAG, "Disconnected from treadmill")
    }
    
    suspend fun startGattServer() = withContext(ioDispatcher) {
        _gattServerState.value = GattServerState.Starting
        gattServerManager.start()
        Log.i(TAG, "Starting GATT server")
    }
    
    suspend fun stopGattServer() = withContext(ioDispatcher) {
        gattServerManager.stop()
        Log.i(TAG, "Stopped GATT server")
    }

    // HR Monitor actions
    suspend fun startHrScan() = withContext(ioDispatcher) {
        _hrDiscoveryState.value = HrDiscoveryState(isScanning = true, discoveredDevices = emptyList())
        hrMonitorBleManager.startScan()
        Log.i(TAG, "Started HR scan")
    }

    suspend fun stopHrScan() = withContext(ioDispatcher) {
        hrMonitorBleManager.stopScan()
        _hrDiscoveryState.value = _hrDiscoveryState.value.copy(isScanning = false)
        Log.i(TAG, "Stopped HR scan")
    }

    suspend fun connectToHrMonitor(address: String) = withContext(ioDispatcher) {
        _hrConnectionState.value = ConnectionState.Connecting
        hrMonitorBleManager.connectToDevice(address)
        Log.i(TAG, "Connecting to HR monitor: $address")
    }

    suspend fun disconnectHrMonitor() = withContext(ioDispatcher) {
        hrMonitorBleManager.disconnect()
        _hrMetrics.value = HrMonitorMetrics()
        Log.i(TAG, "Disconnected from HR monitor")
    }

    // Control Point commands
    suspend fun requestControl(): Boolean = withContext(ioDispatcher) {
        val result = treadmillBleManager.writeControlPoint(FTMSControlPointCommand.requestControl())
        Log.i(TAG, "Request control: ${if (result) "sent" else "failed"}")
        result
    }

    suspend fun resetMachine(): Boolean = withContext(ioDispatcher) {
        val result = treadmillBleManager.writeControlPoint(FTMSControlPointCommand.reset())
        Log.i(TAG, "Reset: ${if (result) "sent" else "failed"}")
        result
    }

    suspend fun setTargetSpeed(speedKmh: Float): Boolean = withContext(ioDispatcher) {
        val result = treadmillBleManager.writeControlPoint(FTMSControlPointCommand.setTargetSpeed(speedKmh))
        Log.i(TAG, "Set target speed $speedKmh km/h: ${if (result) "sent" else "failed"}")
        result
    }

    suspend fun setTargetInclination(inclinePercent: Float): Boolean = withContext(ioDispatcher) {
        val result = treadmillBleManager.writeControlPoint(FTMSControlPointCommand.setTargetInclination(inclinePercent))
        Log.i(TAG, "Set target incline $inclinePercent%: ${if (result) "sent" else "failed"}")
        result
    }

    suspend fun startOrResume(): Boolean = withContext(ioDispatcher) {
        val result = treadmillBleManager.writeControlPoint(FTMSControlPointCommand.startOrResume())
        Log.i(TAG, "Start/Resume: ${if (result) "sent" else "failed"}")
        result
    }

    suspend fun stopMachine(): Boolean = withContext(ioDispatcher) {
        val result = treadmillBleManager.writeControlPoint(FTMSControlPointCommand.stopOrPause(pause = false))
        Log.i(TAG, "Stop: ${if (result) "sent" else "failed"}")
        result
    }

    suspend fun pauseMachine(): Boolean = withContext(ioDispatcher) {
        val result = treadmillBleManager.writeControlPoint(FTMSControlPointCommand.stopOrPause(pause = true))
        Log.i(TAG, "Pause: ${if (result) "sent" else "failed"}")
        result
    }

    suspend fun setTargetedDistance(distanceMeters: Int): Boolean = withContext(ioDispatcher) {
        val result = treadmillBleManager.writeControlPoint(FTMSControlPointCommand.setTargetedDistance(distanceMeters))
        Log.i(TAG, "Set targeted distance $distanceMeters m: ${if (result) "sent" else "failed"}")
        result
    }

    suspend fun setTargetedTrainingTime(timeSeconds: Int): Boolean = withContext(ioDispatcher) {
        val result = treadmillBleManager.writeControlPoint(FTMSControlPointCommand.setTargetedTrainingTime(timeSeconds))
        Log.i(TAG, "Set targeted time $timeSeconds s: ${if (result) "sent" else "failed"}")
        result
    }

    suspend fun cleanup() = withContext(ioDispatcher) {
        treadmillBleManager.cleanup()
        hrMonitorBleManager.cleanup()
        gattServerManager.cleanup()
        Log.i(TAG, "Cleaned up BLE resources")
    }
}
