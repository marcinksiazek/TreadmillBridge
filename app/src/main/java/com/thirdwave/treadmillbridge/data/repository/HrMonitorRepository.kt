package com.thirdwave.treadmillbridge.data.repository

import com.thirdwave.treadmillbridge.data.model.ConnectionState
import com.thirdwave.treadmillbridge.data.model.HrDiscoveryState
import com.thirdwave.treadmillbridge.data.model.HrMonitorMetrics
import kotlinx.coroutines.flow.StateFlow

/**
 * Repository interface for HR monitor BLE operations.
 * Abstracts BLE complexity from ViewModel.
 */
interface HrMonitorRepository {
    // State Flows (hot streams, always active)
    val hrMetrics: StateFlow<HrMonitorMetrics>
    val hrConnectionState: StateFlow<ConnectionState>
    val hrDiscoveryState: StateFlow<HrDiscoveryState>

    // Actions (suspend functions for one-shot operations)
    suspend fun startScan()
    suspend fun stopScan()
    suspend fun connectToDevice(address: String)
    suspend fun disconnect()
}
