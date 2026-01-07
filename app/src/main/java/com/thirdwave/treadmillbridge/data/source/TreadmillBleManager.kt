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
import com.thirdwave.treadmillbridge.data.model.DiscoveredDevice
import com.thirdwave.treadmillbridge.ble.FTMSFeatureData
import com.thirdwave.treadmillbridge.ble.FTMSTreadmillData
import com.thirdwave.treadmillbridge.ble.ParsedFTMSFeatures
import dagger.hilt.android.qualifiers.ApplicationContext
import no.nordicsemi.android.ble.BleManager
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Nordic BLE manager wrapper for treadmill connection.
 * Handles scanning, connecting, and characteristic notifications.
 * 
 * Uses Nordic BLE library for robust connection management with:
 * - Automatic retries
 * - Request queuing
 * - Better error handling
 */
@Singleton
@SuppressLint("MissingPermission")
class TreadmillBleManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bluetoothAdapter: BluetoothAdapter?
) {
    private val TAG = "TreadmillBleManager"
    
    // Callbacks (set by BluetoothDataSource)
    var onDeviceDiscovered: ((DiscoveredDevice) -> Unit)? = null
    var onConnectionStateChanged: ((Boolean, String?) -> Unit)? = null
    var onMetricsReceived: ((FTMSTreadmillData) -> Unit)? = null
    var onFeaturesReceived: ((ParsedFTMSFeatures) -> Unit)? = null
    
    private var nordicManager: NordicTreadmillManager? = null
    private var scanner: BluetoothLeScanner? = null
    private var connectedDeviceName: String? = null
    
    companion object {
        val FTMS_SERVICE_UUID: UUID = UUID.fromString("00001826-0000-1000-8000-00805f9b34fb")
        val FTMS_TREADMILL_DATA_UUID: UUID = UUID.fromString("00002ACD-0000-1000-8000-00805f9b34fb")
        val FTMS_FEATURE_UUID: UUID = UUID.fromString("00002ACC-0000-1000-8000-00805f9b34fb")
    }
    
    // Permission helper
    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * Nordic BLE Manager inner class for treadmill connection.
     * Handles connection, service discovery, and characteristic notifications.
     */
    private inner class NordicTreadmillManager(context: Context) : BleManager(context) {
        private var treadmillDataCharacteristic: BluetoothGattCharacteristic? = null
        private var featureCharacteristic: BluetoothGattCharacteristic? = null
        
        override fun getMinLogPriority(): Int = Log.VERBOSE
        
        override fun log(priority: Int, message: String) {
            Log.println(priority, TAG, message)
        }
        
        override fun getGattCallback(): BleManagerGattCallback {
            return object : BleManagerGattCallback() {
                override fun isRequiredServiceSupported(gatt: BluetoothGatt): Boolean {
                    val service = gatt.getService(FTMS_SERVICE_UUID)
                    if (service == null) {
                        Log.w(TAG, "FTMS service not found")
                        return false
                    }

                    treadmillDataCharacteristic = service.getCharacteristic(FTMS_TREADMILL_DATA_UUID)
                    if (treadmillDataCharacteristic == null) {
                        Log.w(TAG, "Treadmill Data characteristic not found")
                        return false
                    }

                    val properties = treadmillDataCharacteristic?.properties ?: 0
                    val hasNotify = (properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0
                    if (!hasNotify) {
                        Log.w(TAG, "Treadmill Data characteristic does not support notifications")
                        return false
                    }

                    // Feature characteristic is optional but useful
                    featureCharacteristic = service.getCharacteristic(FTMS_FEATURE_UUID)
                    if (featureCharacteristic == null) {
                        Log.w(TAG, "FTMS Feature characteristic not found (optional)")
                    }

                    return true
                }
                
                override fun initialize() {
                    // Read feature characteristic once after connection
                    featureCharacteristic?.let { char ->
                        readCharacteristic(char).with { _, data ->
                            val features = FTMSFeatureData.parse(data.value ?: ByteArray(0))
                            if (features != null) {
                                onFeaturesReceived?.invoke(features)
                            } else {
                                Log.w(TAG, "Failed to parse FTMS features")
                            }
                        }.enqueue()
                    }

                    // Subscribe to treadmill data notifications
                    treadmillDataCharacteristic?.let { char ->
                        setNotificationCallback(char).with { _, data ->
                            val ftmsData = FTMSTreadmillData.parse(data.value ?: ByteArray(0))
                            if (ftmsData != null) {
                                onMetricsReceived?.invoke(ftmsData)
                            } else {
                                Log.w(TAG, "Failed to parse FTMS treadmill data")
                            }
                        }

                        enableNotifications(char).enqueue()
                    }
                }
                
                override fun onServicesInvalidated() {
                    treadmillDataCharacteristic = null
                    featureCharacteristic = null
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
            
            Log.d(TAG, "Scan result: ${device.address} name=$deviceName")
            
            // If advertisement contains FTMS service, notify listener
            val records = result.scanRecord
            val uuids = records?.serviceUuids?.map { it.uuid }
            if (uuids?.contains(FTMS_SERVICE_UUID) == true) {
                Log.i(TAG, "Found FTMS device: ${device.address}")
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
            Log.w(TAG, "Scan failed: $errorCode")
        }
    }
    
    fun startScan() {
        if (!hasPermission(android.Manifest.permission.BLUETOOTH_SCAN)) {
            Log.w(TAG, "Missing BLUETOOTH_SCAN permission — cannot start scan")
            return
        }
        
        scanner = bluetoothAdapter?.bluetoothLeScanner
        if (scanner == null) {
            Log.w(TAG, "BluetoothLeScanner not available")
            return
        }
        
        val filters = listOf(
            ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(FTMS_SERVICE_UUID))
                .build()
        )
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        
        scanner?.startScan(filters, settings, scanCallback)
        Log.i(TAG, "Started scan for FTMS devices")
    }
    
    fun stopScan() {
        if (!hasPermission(android.Manifest.permission.BLUETOOTH_SCAN)) {
            return
        }
        scanner?.stopScan(scanCallback)
        Log.i(TAG, "Stopped scan")
    }
    
    /**
     * Connect to a specific treadmill device by address.
     */
    fun connectToDevice(deviceAddress: String) {
        if (!hasPermission(android.Manifest.permission.BLUETOOTH_CONNECT)) {
            Log.w(TAG, "Missing BLUETOOTH_CONNECT permission — cannot connect")
            onConnectionStateChanged?.invoke(false, null)
            return
        }
        
        val device = bluetoothAdapter?.getRemoteDevice(deviceAddress)
        if (device == null) {
            Log.w(TAG, "Device not found: $deviceAddress")
            onConnectionStateChanged?.invoke(false, null)
            return
        }
        
        // Stop scanning when connecting
        stopScan()
        
        Log.i(TAG, "Connecting to treadmill ${device.address} using Nordic BLE")
        
        // Create Nordic BLE manager and connect
        nordicManager = NordicTreadmillManager(context).apply {
            setConnectionObserver(object : no.nordicsemi.android.ble.observer.ConnectionObserver {
                override fun onDeviceConnecting(device: BluetoothDevice) {
                    Log.i(TAG, "Nordic BLE: Connecting to ${device.address}")
                }
                
                override fun onDeviceConnected(device: BluetoothDevice) {
                    Log.i(TAG, "Nordic BLE: Connected to ${device.address}")
                    connectedDeviceName = if (hasPermission(android.Manifest.permission.BLUETOOTH_CONNECT)) {
                        device.name ?: device.address
                    } else {
                        device.address
                    }
                }
                
                override fun onDeviceFailedToConnect(device: BluetoothDevice, reason: Int) {
                    Log.w(TAG, "Nordic BLE: Failed to connect to ${device.address}, reason: $reason")
                    connectedDeviceName = null
                    onConnectionStateChanged?.invoke(false, null)
                }
                
                override fun onDeviceReady(device: BluetoothDevice) {
                    Log.i(TAG, "Nordic BLE: Device ${device.address} is ready")
                    onConnectionStateChanged?.invoke(true, connectedDeviceName)
                }
                
                override fun onDeviceDisconnecting(device: BluetoothDevice) {
                    Log.i(TAG, "Nordic BLE: Disconnecting from ${device.address}")
                }
                
                override fun onDeviceDisconnected(device: BluetoothDevice, reason: Int) {
                    Log.i(TAG, "Nordic BLE: Disconnected from ${device.address}, reason: $reason")
                    connectedDeviceName = null
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
     * Disconnect from the treadmill.
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
        stopScan()
    }
}
