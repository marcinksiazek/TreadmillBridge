package com.thirdwave.treadmillbridge.data.model

import android.bluetooth.BluetoothDevice

/**
 * Represents a discovered BLE device during scanning.
 */
data class DiscoveredDevice(
    val device: BluetoothDevice?,
    val name: String?,
    val address: String,
    val rssi: Int
)
