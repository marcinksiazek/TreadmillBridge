package com.thirdwave.treadmillbridge.ble

import android.util.Log

/**
 * Parser for Battery Level characteristic (UUID: 0x2A19).
 * Simple single-byte percentage value (0-100).
 */
object BatteryData {
    private const val TAG = "BatteryData"

    /**
     * Parse Battery Level characteristic value.
     *
     * @param data Raw characteristic data bytes
     * @return Battery level percentage (0-100) or null if parsing fails
     */
    fun parse(data: ByteArray): Int? {
        if (data.isEmpty()) {
            Log.w(TAG, "Empty battery data")
            return null
        }
        val level = data[0].toInt() and 0xFF
        Log.d(TAG, "Battery Level: $level%")
        return level
    }
}
