package com.thirdwave.treadmillbridge.data.repository

import com.thirdwave.treadmillbridge.data.model.ConnectionState
import com.thirdwave.treadmillbridge.data.model.DiscoveryState
import com.thirdwave.treadmillbridge.data.model.GattServerState
import com.thirdwave.treadmillbridge.data.model.TreadmillMetrics
import kotlinx.coroutines.flow.StateFlow

/**
 * Repository interface for treadmill BLE operations.
 * Abstracts BLE complexity from ViewModel.
 */
interface TreadmillRepository {
    // State Flows (hot streams, always active)
    val metrics: StateFlow<TreadmillMetrics>
    val connectionState: StateFlow<ConnectionState>
    val gattServerState: StateFlow<GattServerState>
    val discoveryState: StateFlow<DiscoveryState>
    
    // Actions (suspend functions for one-shot operations)
    suspend fun startScan()
    suspend fun stopScan()
    suspend fun connectToDevice(address: String)
    suspend fun disconnectTreadmill()
    suspend fun startGattServer()
    suspend fun stopGattServer()
    suspend fun cleanup()
}
