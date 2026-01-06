package com.thirdwave.treadmillbridge.data.source

import android.util.Log
import com.thirdwave.treadmillbridge.data.model.ConnectionState
import com.thirdwave.treadmillbridge.data.model.DiscoveryState
import com.thirdwave.treadmillbridge.data.model.GattServerState
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
    private val gattServerManager: GattServerManager,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val TAG = "BluetoothDataSource"
    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    
    // Mutable state holders (private)
    private val _treadmillMetrics = MutableStateFlow(TreadmillMetrics())
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    private val _gattServerState = MutableStateFlow<GattServerState>(GattServerState.Stopped)
    private val _discoveryState = MutableStateFlow(DiscoveryState())
    
    // Public read-only StateFlows
    val treadmillMetrics: StateFlow<TreadmillMetrics> = _treadmillMetrics.asStateFlow()
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()
    val gattServerState: StateFlow<GattServerState> = _gattServerState.asStateFlow()
    val discoveryState: StateFlow<DiscoveryState> = _discoveryState.asStateFlow()
    
    init {
        // Set up callbacks from BLE managers → StateFlow updates
        setupTreadmillCallbacks()
        setupGattServerCallbacks()
        setupMetricsSync()
    }
    
    private fun setupTreadmillCallbacks() {
        treadmillBleManager.onConnectionStateChanged = { connected, deviceName ->
            scope.launch {
                _connectionState.value = if (connected) {
                    ConnectionState.Connected(deviceName ?: "Unknown")
                } else {
                    // Reset metrics on disconnect
                    _treadmillMetrics.value = TreadmillMetrics()
                    ConnectionState.Disconnected
                }
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
    
    suspend fun cleanup() = withContext(ioDispatcher) {
        treadmillBleManager.cleanup()
        gattServerManager.cleanup()
        Log.i(TAG, "Cleaned up BLE resources")
    }
}
