package com.thirdwave.treadmillbridge.ble

import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Parser for Heart Rate Measurement characteristic (UUID: 0x2A37).
 * Based on Bluetooth SIG Heart Rate Profile specification.
 */
data class HeartRateData(
    val heartRateBpm: Int,
    val sensorContactDetected: Boolean,
    val sensorContactSupported: Boolean,
    val energyExpendedKJ: Int? = null,
    val rrIntervalsMs: List<Int>? = null
) {
    companion object {
        private const val TAG = "HeartRateData"

        // Flag bits
        private const val FLAG_HR_FORMAT_UINT16 = 0x01
        private const val FLAG_SENSOR_CONTACT_SUPPORTED = 0x04
        private const val FLAG_SENSOR_CONTACT_DETECTED = 0x02
        private const val FLAG_ENERGY_EXPENDED = 0x08
        private const val FLAG_RR_INTERVALS = 0x10

        /**
         * Parse Heart Rate Measurement characteristic value.
         *
         * Format:
         * - Flags (uint8) - 1 byte
         * - Heart Rate (uint8 or uint16 based on flag) - 1 or 2 bytes
         * - Energy Expended (uint16, optional) - 2 bytes
         * - RR-Intervals (uint16[], optional) - remaining bytes
         *
         * @param data Raw characteristic data bytes
         * @return Parsed HeartRateData or null if parsing fails
         */
        fun parse(data: ByteArray): HeartRateData? {
            if (data.isEmpty()) {
                Log.w(TAG, "Empty heart rate data")
                return null
            }

            val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)

            try {
                val flags = buffer.get().toInt() and 0xFF

                val hrFormat16Bit = (flags and FLAG_HR_FORMAT_UINT16) != 0
                val sensorContactSupported = (flags and FLAG_SENSOR_CONTACT_SUPPORTED) != 0
                val sensorContactDetected = (flags and FLAG_SENSOR_CONTACT_DETECTED) != 0
                val hasEnergyExpended = (flags and FLAG_ENERGY_EXPENDED) != 0
                val hasRrIntervals = (flags and FLAG_RR_INTERVALS) != 0

                // Parse heart rate
                val heartRate = if (hrFormat16Bit) {
                    if (buffer.remaining() < 2) {
                        Log.w(TAG, "Not enough data for 16-bit HR")
                        return null
                    }
                    buffer.short.toInt() and 0xFFFF
                } else {
                    if (buffer.remaining() < 1) {
                        Log.w(TAG, "Not enough data for 8-bit HR")
                        return null
                    }
                    buffer.get().toInt() and 0xFF
                }

                Log.d(TAG, "Heart Rate: $heartRate bpm, Contact: $sensorContactDetected")

                // Parse energy expended (optional)
                val energy = if (hasEnergyExpended && buffer.remaining() >= 2) {
                    val value = buffer.short.toInt() and 0xFFFF
                    Log.d(TAG, "Energy Expended: $value kJ")
                    value
                } else null

                // Parse RR intervals (optional, remaining bytes)
                val rrIntervals = if (hasRrIntervals && buffer.remaining() >= 2) {
                    val intervals = mutableListOf<Int>()
                    while (buffer.remaining() >= 2) {
                        // RR interval is in 1/1024 seconds, convert to ms
                        val raw = buffer.short.toInt() and 0xFFFF
                        val intervalMs = (raw * 1000) / 1024
                        intervals.add(intervalMs)
                    }
                    Log.d(TAG, "RR Intervals: $intervals ms")
                    intervals.takeIf { it.isNotEmpty() }
                } else null

                return HeartRateData(
                    heartRateBpm = heartRate,
                    sensorContactDetected = sensorContactDetected,
                    sensorContactSupported = sensorContactSupported,
                    energyExpendedKJ = energy,
                    rrIntervalsMs = rrIntervals
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse heart rate data", e)
                return null
            }
        }
    }
}
