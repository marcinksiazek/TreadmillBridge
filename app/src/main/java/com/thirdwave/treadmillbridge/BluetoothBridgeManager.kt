package com.thirdwave.treadmillbridge

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.ParcelUuid
import android.util.Log
import androidx.core.content.ContextCompat
import com.thirdwave.treadmillbridge.ftms.FTMSTreadmillData
import no.nordicsemi.android.ble.BleManager
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*

/**
 * BluetoothBridgeManager
 * - Scans for a treadmill implementing the Fitness Machine Service (FTMS)
 * - Connects to treadmill using Nordic BLE library for robust connection management
 * - Hosts a local BluetoothGattServer (native Android) exposing FTMS so other centrals (e.g. watch) can connect
 *
 * Architecture:
 * - Nordic BLE library: Used for scanning, connecting to treadmill, and subscribing to characteristics
 *   Provides automatic retries, request queuing, and better error handling
 * - Native Android APIs: Used for GATT server implementation to broadcast data to connected devices
 */
/**
 * Represents a discovered treadmill device
 */
data class DiscoveredDevice(
    val device: BluetoothDevice?,
    val name: String?,
    val address: String,
    val rssi: Int
)

@SuppressLint("MissingPermission")
class BluetoothBridgeManager(private val context: Context) {
    private val TAG = "BTBridgeManager"

    // Permission helper
    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    private val bluetoothManager: BluetoothManager? by lazy {
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    }
    private val bluetoothAdapter: BluetoothAdapter? by lazy { bluetoothManager?.adapter }
    private var bluetoothLeScanner: BluetoothLeScanner? = null

    // Nordic BLE manager for treadmill connection
    private var treadmillManager: TreadmillBleManager? = null

    // GATT server to expose FTMS (native Android)
    private var gattServer: BluetoothGattServer? = null

    // Callback for device discovery
    var onDeviceDiscovered: ((DiscoveredDevice) -> Unit)? = null

    // Callback for connection state changes
    var onConnectionStateChanged: ((Boolean, String?) -> Unit)? = null

    // Callback for GATT server state changes
    var onGattServerStateChanged: ((Boolean) -> Unit)? = null

    // Callback for GATT server client connections
    var onGattClientConnected: ((String) -> Unit)? = null
    var onGattClientDisconnected: (() -> Unit)? = null

    // Track GATT server state
    var isGattServerRunning: Boolean = false
        private set

    // Track connected GATT clients
    var connectedGattClientName: String? = null
        private set

    // Current treadmill values (shared state)
    var speedKph: Float = 0f
        private set
    var inclinePercent: Float = 0f
        private set
    var cadence: Int = 0
        private set

    // Track connected device
    var connectedDeviceName: String? = null
        private set

    // FTMS UUIDs (standard)
    companion object {
        val FTMS_SERVICE_UUID: UUID = UUID.fromString("00001826-0000-1000-8000-00805f9b34fb")
        val FTMS_TREADMILL_DATA_UUID: UUID = UUID.fromString("00002ACD-0000-1000-8000-00805f9b34fb")
        val CLIENT_CHARACTERISTIC_CONFIG_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }

    /**
     * Nordic BLE Manager for treadmill connection
     * Handles connection, service discovery, and characteristic notifications
     */
    private inner class TreadmillBleManager(context: Context) : BleManager(context) {
        private var treadmillDataCharacteristic: BluetoothGattCharacteristic? = null

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

                    return true
                }

                override fun initialize() {
                    // Subscribe to treadmill data notifications
                    treadmillDataCharacteristic?.let { char ->
                        setNotificationCallback(char).with { _, data ->
                            parseTreadmillData(data.value ?: ByteArray(0))
                        }

                        enableNotifications(char)
                            .enqueue()
                    }
                }

                override fun onServicesInvalidated() {
                    treadmillDataCharacteristic = null
                }
            }
        }
    }

    // Scan callback
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)
            val device = result.device
            val deviceName = if (hasPermission(android.Manifest.permission.BLUETOOTH_CONNECT)) device.name else null
            Log.d(TAG, "Scan result: ${device.address} name=${deviceName}")

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

    @SuppressLint("MissingPermission")
    fun startScan() {
        if (!hasPermission(android.Manifest.permission.BLUETOOTH_SCAN)) {
            Log.w(TAG, "Missing BLUETOOTH_SCAN permission — cannot start scan")
            return
        }

        bluetoothLeScanner = bluetoothAdapter?.bluetoothLeScanner
        if (bluetoothLeScanner == null) {
            Log.w(TAG, "BluetoothLeScanner not available")
            return
        }

        val filters = listOf(ScanFilter.Builder().setServiceUuid(ParcelUuid(FTMS_SERVICE_UUID)).build())
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        bluetoothLeScanner?.startScan(filters, settings, scanCallback)
        Log.i(TAG, "Started scan for FTMS devices")
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (!hasPermission(android.Manifest.permission.BLUETOOTH_SCAN)) {
            return
        }
        bluetoothLeScanner?.stopScan(scanCallback)
        Log.i(TAG, "Stopped scan")
    }

    /**
     * Connect to a specific treadmill device by address
     */
    @SuppressLint("MissingPermission")
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
        treadmillManager = TreadmillBleManager(context).apply {
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
                    synchronized(this@BluetoothBridgeManager) {
                        speedKph = 0f
                        inclinePercent = 0f
                        cadence = 0
                    }
                    notifyLocalClients()
                    onConnectionStateChanged?.invoke(false, null)
                }
            })

            connect(device)
                .useAutoConnect(false)
                .retry(3, 100)
                .enqueue()
        }
    }

    private fun parseTreadmillData(data: ByteArray) {
        // Parse using full FTMS specification
        val ftmsData = FTMSTreadmillData.parse(data)
        if (ftmsData == null) {
            Log.w(TAG, "Failed to parse FTMS treadmill data")
            return
        }

        synchronized(this) {
            speedKph = ftmsData.instantaneousSpeedKmh
            inclinePercent = ftmsData.inclinationPercent ?: inclinePercent
            // Note: FTMS doesn't have a cadence field in treadmill data by default
            // Some vendors may use pace or other fields
        }

        Log.i(TAG, "Updated treadmill state: speed=${speedKph} kph incline=${inclinePercent}%")

        // Notify connected clients via GATT server characteristic updates
        notifyLocalClients()
    }

    @SuppressLint("MissingPermission")
    fun startLocalGattServer() {
        if (isGattServerRunning) {
            Log.w(TAG, "GATT server already running")
            return
        }

        val mgr = bluetoothManager ?: run {
            Log.w(TAG, "BluetoothManager not available")
            return
        }

        gattServer = mgr.openGattServer(context, gattServerCallback)

        // Build FTMS service with Treadmill Data characteristic
        val ftmsService = BluetoothGattService(FTMS_SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)

        val treadmillDataChar = BluetoothGattCharacteristic(
            FTMS_TREADMILL_DATA_UUID,
            BluetoothGattCharacteristic.PROPERTY_NOTIFY or BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ
        )

        val cccd = BluetoothGattDescriptor(CLIENT_CHARACTERISTIC_CONFIG_UUID, BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE)
        treadmillDataChar.addDescriptor(cccd)

        ftmsService.addCharacteristic(treadmillDataChar)

        gattServer?.addService(ftmsService)
        isGattServerRunning = true
        onGattServerStateChanged?.invoke(true)
        Log.i(TAG, "Local GATT server started with FTMS service")
    }

    @SuppressLint("MissingPermission")
    fun stopLocalGattServer() {
        gattServer?.close()
        gattServer = null
        isGattServerRunning = false
        connectedGattClientName = null
        onGattServerStateChanged?.invoke(false)
        onGattClientDisconnected?.invoke()
        Log.i(TAG, "Local GATT server stopped")
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
                connectedGattClientName = deviceName
                onGattClientConnected?.invoke(deviceName)
                Log.i(TAG, "GATT client connected: $deviceName")
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                connectedGattClientName = null
                onGattClientDisconnected?.invoke()
                Log.i(TAG, "GATT client disconnected")
            }
        }

        override fun onCharacteristicReadRequest(device: BluetoothDevice?, requestId: Int, offset: Int, characteristic: BluetoothGattCharacteristic?) {
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

        override fun onDescriptorWriteRequest(device: BluetoothDevice?, requestId: Int, descriptor: BluetoothGattDescriptor?, preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray?) {
            super.onDescriptorWriteRequest(device, requestId, descriptor, preparedWrite, responseNeeded, offset, value)
            if (descriptor?.uuid == CLIENT_CHARACTERISTIC_CONFIG_UUID) {
                Log.i(TAG, "Client wrote CCCD: ${value?.contentToString()}")
                if (responseNeeded) {
                    if (hasPermission(android.Manifest.permission.BLUETOOTH_CONNECT)) {
                        gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
                    }
                }
            } else {
                if (responseNeeded && hasPermission(android.Manifest.permission.BLUETOOTH_CONNECT)) gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null)
            }
        }
    }

    private fun buildTreadmillDataPayload(): ByteArray {
        // Build FTMS Treadmill Data payload using full specification
        val ftmsData = synchronized(this) {
            FTMSTreadmillData(
                instantaneousSpeedKmh = speedKph,
                inclinationPercent = if (inclinePercent != 0f) inclinePercent else null,
                // Cadence is not a standard FTMS treadmill field, but we keep it in state
                // for compatibility. Real treadmills may provide it in other characteristics.
            )
        }

        return FTMSTreadmillData.build(ftmsData)
    }

    private fun notifyLocalClients() {
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

    @SuppressLint("MissingPermission")
    fun disconnectTreadmill() {
        // Disconnect Nordic BLE manager
        treadmillManager?.disconnect()?.enqueue()
        treadmillManager?.close()
        treadmillManager = null

        synchronized(this) {
            speedKph = 0f
            inclinePercent = 0f
            cadence = 0
        }

        Log.i(TAG, "Treadmill disconnected")
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        stopScan()
        disconnectTreadmill()
        stopLocalGattServer()
    }
}
