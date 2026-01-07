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
import com.thirdwave.treadmillbridge.data.source.BluetoothDataSource
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of TreadmillRepository.
 * Coordinates BluetoothDataSource and exposes Flows to ViewModel.
 */
@Singleton
class TreadmillRepositoryImpl @Inject constructor(
    private val bluetoothDataSource: BluetoothDataSource
) : TreadmillRepository {

    // Expose data source Flows directly
    override val metrics: StateFlow<TreadmillMetrics> =
        bluetoothDataSource.treadmillMetrics

    override val features: StateFlow<TreadmillFeatures?> =
        bluetoothDataSource.treadmillFeatures

    override val targetSettingFeatures: StateFlow<TargetSettingFeatures?> =
        bluetoothDataSource.targetSettingFeatures

    override val connectionState: StateFlow<ConnectionState> =
        bluetoothDataSource.connectionState

    override val gattServerState: StateFlow<GattServerState> =
        bluetoothDataSource.gattServerState

    override val discoveryState: StateFlow<DiscoveryState> =
        bluetoothDataSource.discoveryState

    override val machineStatusMessage: StateFlow<MachineStatusMessage?> =
        bluetoothDataSource.machineStatusMessage

    override val controlPointResponse: StateFlow<ControlPointResponseMessage?> =
        bluetoothDataSource.controlPointResponse

    override val speedRange: StateFlow<SpeedRange> =
        bluetoothDataSource.speedRange

    override val inclineRange: StateFlow<InclineRange> =
        bluetoothDataSource.inclineRange

    // Delegate actions to data source
    override suspend fun startScan() {
        bluetoothDataSource.startScan()
    }

    override suspend fun stopScan() {
        bluetoothDataSource.stopScan()
    }

    override suspend fun connectToDevice(address: String) {
        bluetoothDataSource.connectToDevice(address)
    }

    override suspend fun disconnectTreadmill() {
        bluetoothDataSource.disconnectTreadmill()
    }

    override suspend fun startGattServer() {
        bluetoothDataSource.startGattServer()
    }

    override suspend fun stopGattServer() {
        bluetoothDataSource.stopGattServer()
    }

    override suspend fun cleanup() {
        bluetoothDataSource.cleanup()
    }

    // Control Point commands
    override suspend fun requestControl(): Boolean =
        bluetoothDataSource.requestControl()

    override suspend fun resetMachine(): Boolean =
        bluetoothDataSource.resetMachine()

    override suspend fun setTargetSpeed(speedKmh: Float): Boolean =
        bluetoothDataSource.setTargetSpeed(speedKmh)

    override suspend fun setTargetInclination(inclinePercent: Float): Boolean =
        bluetoothDataSource.setTargetInclination(inclinePercent)

    override suspend fun startOrResume(): Boolean =
        bluetoothDataSource.startOrResume()

    override suspend fun stopMachine(): Boolean =
        bluetoothDataSource.stopMachine()

    override suspend fun pauseMachine(): Boolean =
        bluetoothDataSource.pauseMachine()

    override suspend fun setTargetedDistance(distanceMeters: Int): Boolean =
        bluetoothDataSource.setTargetedDistance(distanceMeters)

    override suspend fun setTargetedTrainingTime(timeSeconds: Int): Boolean =
        bluetoothDataSource.setTargetedTrainingTime(timeSeconds)
}
