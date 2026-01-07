package com.thirdwave.treadmillbridge.data.source

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.ParcelUuid
import android.util.Log
import androidx.core.content.ContextCompat
import com.thirdwave.treadmillbridge.ble.BatteryData
import com.thirdwave.treadmillbridge.ble.HeartRateData
import com.thirdwave.treadmillbridge.data.model.DiscoveredDevice
import dagger.hilt.android.qualifiers.ApplicationContext
import no.nordicsemi.android.ble.BleManager
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Nordic BLE manager wrapper for HR monitor connection.
 * Handles scanning, connecting, and characteristic notifications for Heart Rate monitors.
 *
 * Uses Nordic BLE library for robust connection management with:
 * - Automatic retries
 * - Request queuing
 * - Better error handling
 */
@Singleton
@SuppressLint("MissingPermission")
class HrMonitorBleManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bluetoothAdapter: BluetoothAdapter?
) {
    private val TAG = "HrMonitorBleManager"

    // Callbacks (set by BluetoothDataSource)
    var onDeviceDiscovered: ((DiscoveredDevice) -> Unit)? = null
    var onConnectionStateChanged: ((Boolean, String?) -> Unit)? = null
    var onHeartRateReceived: ((HeartRateData) -> Unit)? = null
    var onBatteryLevelReceived: ((Int) -> Unit)? = null

    private var nordicManager: NordicHrManager? = null
    private var scanner: BluetoothLeScanner? = null
    private var connectedDeviceName: String? = null
    private var connectedDeviceAddress: String? = null
    private var lastRssi: Int = 0

    companion object {
        val HEART_RATE_SERVICE_UUID: UUID = UUID.fromString("0000180D-0000-1000-8000-00805f9b34fb")
        val HEART_RATE_MEASUREMENT_UUID: UUID = UUID.fromString("00002A37-0000-1000-8000-00805f9b34fb")
        val BATTERY_SERVICE_UUID: UUID = UUID.fromString("0000180F-0000-1000-8000-00805f9b34fb")
        val BATTERY_LEVEL_UUID: UUID = UUID.fromString("00002A19-0000-1000-8000-00805f9b34fb")
    }

    // Permission helper
    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Nordic BLE Manager inner class for HR monitor connection.
     * Handles connection, service discovery, and characteristic notifications.
     */
    private inner class NordicHrManager(context: Context) : BleManager(context) {
        private var heartRateCharacteristic: BluetoothGattCharacteristic? = null
        private var batteryCharacteristic: BluetoothGattCharacteristic? = null

        override fun getMinLogPriority(): Int = Log.VERBOSE

        override fun log(priority: Int, message: String) {
            Log.println(priority, TAG, message)
        }

        override fun getGattCallback(): BleManagerGattCallback {
            return object : BleManagerGattCallback() {
                override fun isRequiredServiceSupported(gatt: BluetoothGatt): Boolean {
                    val hrService = gatt.getService(HEART_RATE_SERVICE_UUID)
                    if (hrService == null) {
                        Log.w(TAG, "Heart Rate service not found")
                        return false
                    }

                    heartRateCharacteristic = hrService.getCharacteristic(HEART_RATE_MEASUREMENT_UUID)
                    if (heartRateCharacteristic == null) {
                        Log.w(TAG, "Heart Rate Measurement characteristic not found")
                        return false
                    }

                    val properties = heartRateCharacteristic?.properties ?: 0
                    val hasNotify = (properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0
                    if (!hasNotify) {
                        Log.w(TAG, "Heart Rate Measurement characteristic does not support notifications")
                        return false
                    }

                    // Battery is optional - try to find it
                    val batteryService = gatt.getService(BATTERY_SERVICE_UUID)
                    batteryCharacteristic = batteryService?.getCharacteristic(BATTERY_LEVEL_UUID)
                    if (batteryCharacteristic != null) {
                        Log.i(TAG, "Battery service found")
                    } else {
                        Log.i(TAG, "Battery service not available on this device")
                    }

                    return true
                }

                override fun initialize() {
                    // Subscribe to HR notifications
                    heartRateCharacteristic?.let { char ->
                        setNotificationCallback(char).with { _, data ->
                            val hrData = HeartRateData.parse(data.value ?: ByteArray(0))
                            if (hrData != null) {
                                onHeartRateReceived?.invoke(hrData)
                            } else {
                                Log.w(TAG, "Failed to parse heart rate data")
                            }
                        }
                        enableNotifications(char).enqueue()
                    }

                    // Read battery level and optionally subscribe
                    batteryCharacteristic?.let { char ->
                        // Initial read
                        readCharacteristic(char).with { _, data ->
                            BatteryData.parse(data.value ?: ByteArray(0))?.let { level ->
                                onBatteryLevelReceived?.invoke(level)
                            }
                        }.enqueue()

                        // Subscribe if notifications supported
                        val properties = char.properties
                        if ((properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) {
                            setNotificationCallback(char).with { _, data ->
                                BatteryData.parse(data.value ?: ByteArray(0))?.let { level ->
                                    onBatteryLevelReceived?.invoke(level)
                                }
                            }
                            enableNotifications(char).enqueue()
                        }
                    }
                }

                override fun onServicesInvalidated() {
                    heartRateCharacteristic = null
                    batteryCharacteristic = null
                }
            }
        }
    }

    // Scan callback
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)
            val device = result.device
            val deviceName = if (hasPermission(android.Manifest.permission.BLUETOOTH_CONNECT)) {
                device.name
            } else null

            Log.d(TAG, "HR Scan result: ${device.address} name=$deviceName")

            // If advertisement contains HR service, notify listener
            val records = result.scanRecord
            val uuids = records?.serviceUuids?.map { it.uuid }
            if (uuids?.contains(HEART_RATE_SERVICE_UUID) == true) {
                Log.i(TAG, "Found HR device: ${device.address}")
                val discoveredDevice = DiscoveredDevice(
                    device = device,
                    name = deviceName,
                    address = device.address,
                    rssi = result.rssi
                )
                onDeviceDiscovered?.invoke(discoveredDevice)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            Log.w(TAG, "HR scan failed: $errorCode")
        }
    }

    fun startScan() {
        if (!hasPermission(android.Manifest.permission.BLUETOOTH_SCAN)) {
            Log.w(TAG, "Missing BLUETOOTH_SCAN permission - cannot start HR scan")
            return
        }

        scanner = bluetoothAdapter?.bluetoothLeScanner
        if (scanner == null) {
            Log.w(TAG, "BluetoothLeScanner not available")
            return
        }

        val filters = listOf(
            ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(HEART_RATE_SERVICE_UUID))
                .build()
        )
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        scanner?.startScan(filters, settings, scanCallback)
        Log.i(TAG, "Started scan for HR devices")
    }

    fun stopScan() {
        if (!hasPermission(android.Manifest.permission.BLUETOOTH_SCAN)) {
            return
        }
        scanner?.stopScan(scanCallback)
        Log.i(TAG, "Stopped HR scan")
    }

    /**
     * Connect to a specific HR monitor device by address.
     */
    fun connectToDevice(deviceAddress: String) {
        if (!hasPermission(android.Manifest.permission.BLUETOOTH_CONNECT)) {
            Log.w(TAG, "Missing BLUETOOTH_CONNECT permission - cannot connect")
            onConnectionStateChanged?.invoke(false, null)
            return
        }

        val device = bluetoothAdapter?.getRemoteDevice(deviceAddress)
        if (device == null) {
            Log.w(TAG, "HR Device not found: $deviceAddress")
            onConnectionStateChanged?.invoke(false, null)
            return
        }

        // Stop scanning when connecting
        stopScan()

        Log.i(TAG, "Connecting to HR monitor ${device.address} using Nordic BLE")

        connectedDeviceAddress = deviceAddress

        // Create Nordic BLE manager and connect
        nordicManager = NordicHrManager(context).apply {
            setConnectionObserver(object : no.nordicsemi.android.ble.observer.ConnectionObserver {
                override fun onDeviceConnecting(device: BluetoothDevice) {
                    Log.i(TAG, "Nordic BLE: Connecting to HR ${device.address}")
                }

                override fun onDeviceConnected(device: BluetoothDevice) {
                    Log.i(TAG, "Nordic BLE: Connected to HR ${device.address}")
                    connectedDeviceName = if (hasPermission(android.Manifest.permission.BLUETOOTH_CONNECT)) {
                        device.name ?: device.address
                    } else {
                        device.address
                    }
                }

                override fun onDeviceFailedToConnect(device: BluetoothDevice, reason: Int) {
                    Log.w(TAG, "Nordic BLE: Failed to connect to HR ${device.address}, reason: $reason")
                    connectedDeviceName = null
                    connectedDeviceAddress = null
                    onConnectionStateChanged?.invoke(false, null)
                }

                override fun onDeviceReady(device: BluetoothDevice) {
                    Log.i(TAG, "Nordic BLE: HR Device ${device.address} is ready")
                    onConnectionStateChanged?.invoke(true, connectedDeviceName)
                }

                override fun onDeviceDisconnecting(device: BluetoothDevice) {
                    Log.i(TAG, "Nordic BLE: Disconnecting from HR ${device.address}")
                }

                override fun onDeviceDisconnected(device: BluetoothDevice, reason: Int) {
                    Log.i(TAG, "Nordic BLE: Disconnected from HR ${device.address}, reason: $reason")
                    connectedDeviceName = null
                    connectedDeviceAddress = null
                    onConnectionStateChanged?.invoke(false, null)
                }
            })

            connect(device)
                .useAutoConnect(false)
                .retry(3, 100)
                .enqueue()
        }
    }

    /**
     * Disconnect from the HR monitor.
     */
    fun disconnect() {
        nordicManager?.disconnect()?.enqueue()
    }

    /**
     * Cleanup resources.
     */
    fun cleanup() {
        nordicManager?.close()
        nordicManager = null
        connectedDeviceName = null
        connectedDeviceAddress = null
        stopScan()
    }

    /**
     * Get the connected device address.
     */
    fun getConnectedDeviceAddress(): String? = connectedDeviceAddress
}
