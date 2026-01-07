package com.thirdwave.treadmillbridge.data.repository

import com.thirdwave.treadmillbridge.data.model.ConnectionState
import com.thirdwave.treadmillbridge.data.model.HrDiscoveryState
import com.thirdwave.treadmillbridge.data.model.HrMonitorMetrics
import com.thirdwave.treadmillbridge.data.source.BluetoothDataSource
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of HR monitor repository.
 * Delegates to BluetoothDataSource for actual BLE operations.
 */
@Singleton
class HrMonitorRepositoryImpl @Inject constructor(
    private val bluetoothDataSource: BluetoothDataSource
) : HrMonitorRepository {

    override val hrMetrics: StateFlow<HrMonitorMetrics> =
        bluetoothDataSource.hrMetrics

    override val hrConnectionState: StateFlow<ConnectionState> =
        bluetoothDataSource.hrConnectionState

    override val hrDiscoveryState: StateFlow<HrDiscoveryState> =
        bluetoothDataSource.hrDiscoveryState

    override suspend fun startScan() {
        bluetoothDataSource.startHrScan()
    }

    override suspend fun stopScan() {
        bluetoothDataSource.stopHrScan()
    }

    override suspend fun connectToDevice(address: String) {
        bluetoothDataSource.connectToHrMonitor(address)
    }

    override suspend fun disconnect() {
        bluetoothDataSource.disconnectHrMonitor()
    }
}
