package com.thirdwave.treadmillbridge.data.source

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.thirdwave.treadmillbridge.data.model.TreadmillMetrics
import com.thirdwave.treadmillbridge.ble.FTMSTreadmillData
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * GATT server manager for broadcasting FTMS data.
 * Uses native Android BluetoothGattServer to expose treadmill data
 * to connected clients (e.g., smartwatches).
 */
@Singleton
@SuppressLint("MissingPermission")
class GattServerManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bluetoothManager: BluetoothManager?
) {
    private val TAG = "GattServerManager"
    
    // Callbacks (set by BluetoothDataSource)
    var onServerStateChanged: ((Boolean) -> Unit)? = null
    var onClientConnected: ((String) -> Unit)? = null
    var onClientDisconnected: (() -> Unit)? = null
    
    private var gattServer: BluetoothGattServer? = null
    private var currentMetrics: TreadmillMetrics = TreadmillMetrics()
    private var connectedClientName: String? = null
    
    companion object {
        val FTMS_SERVICE_UUID: UUID = UUID.fromString("00001826-0000-1000-8000-00805f9b34fb")
        val FTMS_TREADMILL_DATA_UUID: UUID = UUID.fromString("00002ACD-0000-1000-8000-00805f9b34fb")
        val CLIENT_CHARACTERISTIC_CONFIG_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
    
    // Permission helper
    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }
    
    private val gattServerCallback = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: BluetoothDevice?, status: Int, newState: Int) {
            super.onConnectionStateChange(device, status, newState)
            Log.i(TAG, "GATT server connection state change: device=${device?.address} state=$newState")
            
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                val deviceName = if (hasPermission(android.Manifest.permission.BLUETOOTH_CONNECT)) {
                    device?.name ?: device?.address ?: "Unknown"
                } else {
                    device?.address ?: "Unknown"
                }
                connectedClientName = deviceName
                onClientConnected?.invoke(deviceName)
                Log.i(TAG, "GATT client connected: $deviceName")
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                connectedClientName = null
                onClientDisconnected?.invoke()
                Log.i(TAG, "GATT client disconnected")
            }
        }
        
        override fun onCharacteristicReadRequest(
            device: BluetoothDevice?,
            requestId: Int,
            offset: Int,
            characteristic: BluetoothGattCharacteristic?
        ) {
            super.onCharacteristicReadRequest(device, requestId, offset, characteristic)
            if (characteristic?.uuid == FTMS_TREADMILL_DATA_UUID) {
                val value = buildTreadmillDataPayload()
                if (hasPermission(android.Manifest.permission.BLUETOOTH_CONNECT)) {
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, value)
                } else {
                    Log.w(TAG, "Missing BLUETOOTH_CONNECT permission — cannot send read response")
                }
            } else {
                if (hasPermission(android.Manifest.permission.BLUETOOTH_CONNECT)) {
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null)
                }
            }
        }
        
        override fun onDescriptorWriteRequest(
            device: BluetoothDevice?,
            requestId: Int,
            descriptor: BluetoothGattDescriptor?,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?
        ) {
            super.onDescriptorWriteRequest(device, requestId, descriptor, preparedWrite, responseNeeded, offset, value)
            if (descriptor?.uuid == CLIENT_CHARACTERISTIC_CONFIG_UUID) {
                Log.i(TAG, "Client wrote CCCD: ${value?.contentToString()}")
                if (responseNeeded) {
                    if (hasPermission(android.Manifest.permission.BLUETOOTH_CONNECT)) {
                        gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
                    }
                }
            } else {
                if (responseNeeded && hasPermission(android.Manifest.permission.BLUETOOTH_CONNECT)) {
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null)
                }
            }
        }
    }
    
    /**
     * Start the GATT server.
     */
    fun start() {
        if (gattServer != null) {
            Log.w(TAG, "GATT server already running")
            return
        }
        
        val mgr = bluetoothManager ?: run {
            Log.w(TAG, "BluetoothManager not available")
            return
        }
        
        gattServer = mgr.openGattServer(context, gattServerCallback)
        
        // Build FTMS service with Treadmill Data characteristic
        val ftmsService = BluetoothGattService(
            FTMS_SERVICE_UUID,
            BluetoothGattService.SERVICE_TYPE_PRIMARY
        )
        
        val treadmillDataChar = BluetoothGattCharacteristic(
            FTMS_TREADMILL_DATA_UUID,
            BluetoothGattCharacteristic.PROPERTY_NOTIFY or BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ
        )
        
        val cccd = BluetoothGattDescriptor(
            CLIENT_CHARACTERISTIC_CONFIG_UUID,
            BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
        )
        treadmillDataChar.addDescriptor(cccd)
        
        ftmsService.addCharacteristic(treadmillDataChar)
        
        gattServer?.addService(ftmsService)
        onServerStateChanged?.invoke(true)
        Log.i(TAG, "Local GATT server started with FTMS service")
    }
    
    /**
     * Stop the GATT server.
     */
    fun stop() {
        gattServer?.close()
        gattServer = null
        connectedClientName = null
        onServerStateChanged?.invoke(false)
        onClientDisconnected?.invoke()
        Log.i(TAG, "Local GATT server stopped")
    }
    
    /**
     * Update metrics and notify connected clients.
     */
    fun updateMetrics(metrics: TreadmillMetrics) {
        currentMetrics = metrics
        notifyClients()
    }
    
    private fun buildTreadmillDataPayload(): ByteArray {
        // Build FTMS Treadmill Data payload using full specification
        val ftmsData = FTMSTreadmillData(
            instantaneousSpeedKmh = currentMetrics.speedKph,
            inclinationPercent = if (currentMetrics.inclinePercent != 0f) {
                currentMetrics.inclinePercent
            } else null
        )
        
        return FTMSTreadmillData.build(ftmsData)
    }
    
    @Suppress("DEPRECATION")
    private fun notifyClients() {
        val server = gattServer ?: return
        val service = server.getService(FTMS_SERVICE_UUID) ?: return
        val characteristic = service.getCharacteristic(FTMS_TREADMILL_DATA_UUID) ?: return
        characteristic.value = buildTreadmillDataPayload()
        
        if (!hasPermission(android.Manifest.permission.BLUETOOTH_CONNECT)) {
            Log.w(TAG, "Missing BLUETOOTH_CONNECT permission — cannot notify clients")
            return
        }
        
        val connectedDevices = bluetoothManager?.getConnectedDevices(BluetoothProfile.GATT) ?: emptyList()
        for (dev in connectedDevices) {
            server.notifyCharacteristicChanged(dev, characteristic, false)
            Log.d(TAG, "Notified ${dev.address}")
        }
    }
    
    /**
     * Cleanup resources.
     */
    fun cleanup() {
        stop()
    }
}
