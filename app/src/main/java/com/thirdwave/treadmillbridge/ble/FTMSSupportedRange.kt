package com.thirdwave.treadmillbridge.ble

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * FTMS Supported Speed Range characteristic (0x2AD4).
 * Units: km/h with 0.01 resolution.
 * Format: [min: UINT16, max: UINT16, increment: UINT16]
 */
data class SpeedRange(
    val minKmh: Float = DEFAULT_MIN,
    val maxKmh: Float = DEFAULT_MAX,
    val stepKmh: Float = DEFAULT_STEP
) {
    companion object {
        const val DEFAULT_MIN = 0f
        const val DEFAULT_MAX = 20f
        const val DEFAULT_STEP = 0.1f

        private const val RESOLUTION = 0.01f

        fun default(): SpeedRange = SpeedRange()

        /**
         * Parse Supported Speed Range characteristic data.
         * Returns null if data is invalid.
         */
        fun parse(data: ByteArray): SpeedRange? {
            if (data.size < 6) return null

            val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)

            val minRaw = buffer.short.toInt() and 0xFFFF
            val maxRaw = buffer.short.toInt() and 0xFFFF
            val stepRaw = buffer.short.toInt() and 0xFFFF

            val minKmh = minRaw * RESOLUTION
            val maxKmh = maxRaw * RESOLUTION
            val stepKmh = stepRaw * RESOLUTION

            // Validate ranges
            if (maxKmh <= minKmh || stepKmh <= 0) return null

            return SpeedRange(minKmh, maxKmh, stepKmh)
        }
    }
}

/**
 * FTMS Supported Inclination Range characteristic (0x2AD5).
 * Units: % with 0.1 resolution (signed).
 * Format: [min: SINT16, max: SINT16, increment: UINT16]
 */
data class InclineRange(
    val minPercent: Float = DEFAULT_MIN,
    val maxPercent: Float = DEFAULT_MAX,
    val stepPercent: Float = DEFAULT_STEP
) {
    companion object {
        const val DEFAULT_MIN = 0f
        const val DEFAULT_MAX = 10f
        const val DEFAULT_STEP = 0.5f

        private const val RESOLUTION = 0.1f

        fun default(): InclineRange = InclineRange()

        /**
         * Parse Supported Inclination Range characteristic data.
         * Returns null if data is invalid.
         */
        fun parse(data: ByteArray): InclineRange? {
            if (data.size < 6) return null

            val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)

            // Min and max are signed (SINT16)
            val minRaw = buffer.short.toInt()
            val maxRaw = buffer.short.toInt()
            // Increment is unsigned (UINT16)
            val stepRaw = buffer.short.toInt() and 0xFFFF

            val minPercent = minRaw * RESOLUTION
            val maxPercent = maxRaw * RESOLUTION
            val stepPercent = stepRaw * RESOLUTION

            // Validate ranges
            if (maxPercent <= minPercent || stepPercent <= 0) return null

            return InclineRange(minPercent, maxPercent, stepPercent)
        }
    }
}
