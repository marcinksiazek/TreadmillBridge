package com.thirdwave.treadmillbridge.data.repository

import com.thirdwave.treadmillbridge.ble.InclineRange
import com.thirdwave.treadmillbridge.ble.SpeedRange
import com.thirdwave.treadmillbridge.data.model.ConnectionState
import com.thirdwave.treadmillbridge.data.model.ControlPointResponseMessage
import com.thirdwave.treadmillbridge.data.model.DiscoveryState
import com.thirdwave.treadmillbridge.data.model.GattServerState
import com.thirdwave.treadmillbridge.data.model.MachineStatusMessage
import com.thirdwave.treadmillbridge.data.model.TargetSettingFeatures
import com.thirdwave.treadmillbridge.data.model.TreadmillFeatures
import com.thirdwave.treadmillbridge.data.model.TreadmillMetrics
import kotlinx.coroutines.flow.StateFlow

/**
 * Repository interface for treadmill BLE operations.
 * Abstracts BLE complexity from ViewModel.
 */
interface TreadmillRepository {
    // State Flows (hot streams, always active)
    val metrics: StateFlow<TreadmillMetrics>
    val features: StateFlow<TreadmillFeatures?>
    val targetSettingFeatures: StateFlow<TargetSettingFeatures?>
    val connectionState: StateFlow<ConnectionState>
    val gattServerState: StateFlow<GattServerState>
    val discoveryState: StateFlow<DiscoveryState>
    val machineStatusMessage: StateFlow<MachineStatusMessage?>
    val controlPointResponse: StateFlow<ControlPointResponseMessage?>
    val speedRange: StateFlow<SpeedRange>
    val inclineRange: StateFlow<InclineRange>

    // Actions (suspend functions for one-shot operations)
    suspend fun startScan()
    suspend fun stopScan()
    suspend fun connectToDevice(address: String)
    suspend fun disconnectTreadmill()
    suspend fun startGattServer()
    suspend fun stopGattServer()
    suspend fun cleanup()

    // Control Point commands
    suspend fun requestControl(): Boolean
    suspend fun resetMachine(): Boolean
    suspend fun setTargetSpeed(speedKmh: Float): Boolean
    suspend fun setTargetInclination(inclinePercent: Float): Boolean
    suspend fun startOrResume(): Boolean
    suspend fun stopMachine(): Boolean
    suspend fun pauseMachine(): Boolean
    suspend fun setTargetedDistance(distanceMeters: Int): Boolean
    suspend fun setTargetedTrainingTime(timeSeconds: Int): Boolean
}
