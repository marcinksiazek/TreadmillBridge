package com.thirdwave.treadmillbridge.ftms

import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Represents parsed FTMS Treadmill Data according to GATT FTMS specification.
 * Characteristic UUID: 0x2ACD
 *
 * Based on Fitness Machine Service v1.0 specification.
 */
data class FTMSTreadmillData(
    // Always present
    val instantaneousSpeedKmh: Float,

    // Optional fields based on flags
    val averageSpeedKmh: Float? = null,
    val totalDistanceMeters: Int? = null,
    val inclinationPercent: Float? = null,
    val rampAngleDegrees: Float? = null,
    val elevationGainMeters: Float? = null,
    val instantaneousPaceMinPerKm: Float? = null,
    val averagePaceMinPerKm: Float? = null,
    val totalEnergyKCal: Int? = null,
    val energyPerHourKCal: Int? = null,
    val energyPerMinuteKCal: Int? = null,
    val heartRateBpm: Int? = null,
    val metabolicEquivalent: Float? = null,
    val elapsedTimeSeconds: Int? = null,
    val remainingTimeSeconds: Int? = null,
    val forceOnBeltNewton: Int? = null,
    val powerOutputWatt: Int? = null
) {
    companion object {
        private const val TAG = "FTMSParser"

        // Flag bits for Treadmill Data characteristic
        private const val FLAG_MORE_DATA = 0x0001
        private const val FLAG_AVERAGE_SPEED = 0x0002
        private const val FLAG_TOTAL_DISTANCE = 0x0004
        private const val FLAG_INCLINATION_RAMP = 0x0008
        private const val FLAG_ELEVATION_GAIN = 0x0010
        private const val FLAG_INSTANTANEOUS_PACE = 0x0020
        private const val FLAG_AVERAGE_PACE = 0x0040
        private const val FLAG_EXPENDED_ENERGY = 0x0080
        private const val FLAG_HEART_RATE = 0x0100
        private const val FLAG_METABOLIC_EQUIVALENT = 0x0200
        private const val FLAG_ELAPSED_TIME = 0x0400
        private const val FLAG_REMAINING_TIME = 0x0800
        private const val FLAG_FORCE_POWER = 0x1000

        /**
         * Parse FTMS Treadmill Data characteristic value.
         *
         * Format according to FTMS spec:
         * - Flags (uint16) - 2 bytes
         * - Instantaneous Speed (uint16) - 2 bytes, resolution 0.01 km/h (ALWAYS PRESENT)
         * - Optional fields based on flags
         *
         * @param data Raw characteristic data bytes
         * @return Parsed FTMSTreadmillData or null if parsing fails
         */
        fun parse(data: ByteArray): FTMSTreadmillData? {
            if (data.isEmpty()) {
                Log.w(TAG, "Empty treadmill data")
                return null
            }

            if (data.size < 4) {
                Log.w(TAG, "Treadmill data too short: ${data.size} bytes (minimum 4 bytes required)")
                return null
            }

            val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)

            try {
                // Parse flags
                val flags = buffer.short.toInt() and 0xFFFF
                Log.d(TAG, "Flags: 0x${flags.toString(16).padStart(4, '0')}")

                // Parse instantaneous speed (always present)
                val speedRaw = buffer.short.toInt() and 0xFFFF
                val instantaneousSpeedKmh = speedRaw * 0.01f
                Log.d(TAG, "Instantaneous Speed: $instantaneousSpeedKmh km/h")

                var averageSpeed: Float? = null
                var totalDistance: Int? = null
                var inclination: Float? = null
                var rampAngle: Float? = null
                var elevationGain: Float? = null
                var instantaneousPace: Float? = null
                var averagePace: Float? = null
                var totalEnergy: Int? = null
                var energyPerHour: Int? = null
                var energyPerMinute: Int? = null
                var heartRate: Int? = null
                var metabolicEq: Float? = null
                var elapsedTime: Int? = null
                var remainingTime: Int? = null
                var forceOnBelt: Int? = null
                var powerOutput: Int? = null

                // Parse optional fields based on flags
                if (flags and FLAG_AVERAGE_SPEED != 0) {
                    if (buffer.remaining() >= 2) {
                        val avgSpeedRaw = buffer.short.toInt() and 0xFFFF
                        averageSpeed = avgSpeedRaw * 0.01f
                        Log.d(TAG, "Average Speed: $averageSpeed km/h")
                    } else {
                        Log.w(TAG, "Not enough data for average speed")
                    }
                }

                if (flags and FLAG_TOTAL_DISTANCE != 0) {
                    if (buffer.remaining() >= 3) {
                        // uint24 - 3 bytes
                        val byte1 = buffer.get().toInt() and 0xFF
                        val byte2 = buffer.get().toInt() and 0xFF
                        val byte3 = buffer.get().toInt() and 0xFF
                        totalDistance = byte1 or (byte2 shl 8) or (byte3 shl 16)
                        Log.d(TAG, "Total Distance: $totalDistance meters")
                    } else {
                        Log.w(TAG, "Not enough data for total distance")
                    }
                }

                if (flags and FLAG_INCLINATION_RAMP != 0) {
                    if (buffer.remaining() >= 4) {
                        // Inclination (sint16) - resolution 0.1%
                        val inclinationRaw = buffer.short.toInt()
                        inclination = inclinationRaw * 0.1f

                        // Ramp Angle (sint16) - resolution 0.1 degree
                        val rampAngleRaw = buffer.short.toInt()
                        rampAngle = rampAngleRaw * 0.1f
                        Log.d(TAG, "Inclination: $inclination%, Ramp Angle: $rampAngle°")
                    } else {
                        Log.w(TAG, "Not enough data for inclination/ramp")
                    }
                }

                if (flags and FLAG_ELEVATION_GAIN != 0) {
                    if (buffer.remaining() >= 2) {
                        val elevationRaw = buffer.short.toInt() and 0xFFFF
                        elevationGain = elevationRaw * 0.1f
                        Log.d(TAG, "Elevation Gain: $elevationGain meters")
                    } else {
                        Log.w(TAG, "Not enough data for elevation gain")
                    }
                }

                if (flags and FLAG_INSTANTANEOUS_PACE != 0) {
                    if (buffer.remaining() >= 1) {
                        val paceRaw = buffer.get().toInt() and 0xFF
                        instantaneousPace = paceRaw * 1.0f // km/min
                        Log.d(TAG, "Instantaneous Pace: $instantaneousPace km/min")
                    } else {
                        Log.w(TAG, "Not enough data for instantaneous pace")
                    }
                }

                if (flags and FLAG_AVERAGE_PACE != 0) {
                    if (buffer.remaining() >= 1) {
                        val avgPaceRaw = buffer.get().toInt() and 0xFF
                        averagePace = avgPaceRaw * 1.0f // km/min
                        Log.d(TAG, "Average Pace: $averagePace km/min")
                    } else {
                        Log.w(TAG, "Not enough data for average pace")
                    }
                }

                if (flags and FLAG_EXPENDED_ENERGY != 0) {
                    if (buffer.remaining() >= 5) {
                        totalEnergy = buffer.short.toInt() and 0xFFFF
                        energyPerHour = buffer.short.toInt() and 0xFFFF
                        energyPerMinute = buffer.get().toInt() and 0xFF
                        Log.d(TAG, "Energy - Total: $totalEnergy kCal, Per Hour: $energyPerHour kCal, Per Minute: $energyPerMinute kCal")
                    } else {
                        Log.w(TAG, "Not enough data for expended energy")
                    }
                }

                if (flags and FLAG_HEART_RATE != 0) {
                    if (buffer.remaining() >= 1) {
                        heartRate = buffer.get().toInt() and 0xFF
                        Log.d(TAG, "Heart Rate: $heartRate bpm")
                    } else {
                        Log.w(TAG, "Not enough data for heart rate")
                    }
                }

                if (flags and FLAG_METABOLIC_EQUIVALENT != 0) {
                    if (buffer.remaining() >= 1) {
                        val metRaw = buffer.get().toInt() and 0xFF
                        metabolicEq = metRaw * 0.1f
                        Log.d(TAG, "Metabolic Equivalent: $metabolicEq MET")
                    } else {
                        Log.w(TAG, "Not enough data for metabolic equivalent")
                    }
                }

                if (flags and FLAG_ELAPSED_TIME != 0) {
                    if (buffer.remaining() >= 2) {
                        elapsedTime = buffer.short.toInt() and 0xFFFF
                        Log.d(TAG, "Elapsed Time: $elapsedTime seconds")
                    } else {
                        Log.w(TAG, "Not enough data for elapsed time")
                    }
                }

                if (flags and FLAG_REMAINING_TIME != 0) {
                    if (buffer.remaining() >= 2) {
                        remainingTime = buffer.short.toInt() and 0xFFFF
                        Log.d(TAG, "Remaining Time: $remainingTime seconds")
                    } else {
                        Log.w(TAG, "Not enough data for remaining time")
                    }
                }

                if (flags and FLAG_FORCE_POWER != 0) {
                    if (buffer.remaining() >= 4) {
                        forceOnBelt = buffer.short.toInt()
                        powerOutput = buffer.short.toInt()
                        Log.d(TAG, "Force: $forceOnBelt N, Power: $powerOutput W")
                    } else {
                        Log.w(TAG, "Not enough data for force/power")
                    }
                }

                return FTMSTreadmillData(
                    instantaneousSpeedKmh = instantaneousSpeedKmh,
                    averageSpeedKmh = averageSpeed,
                    totalDistanceMeters = totalDistance,
                    inclinationPercent = inclination,
                    rampAngleDegrees = rampAngle,
                    elevationGainMeters = elevationGain,
                    instantaneousPaceMinPerKm = instantaneousPace,
                    averagePaceMinPerKm = averagePace,
                    totalEnergyKCal = totalEnergy,
                    energyPerHourKCal = energyPerHour,
                    energyPerMinuteKCal = energyPerMinute,
                    heartRateBpm = heartRate,
                    metabolicEquivalent = metabolicEq,
                    elapsedTimeSeconds = elapsedTime,
                    remainingTimeSeconds = remainingTime,
                    forceOnBeltNewton = forceOnBelt,
                    powerOutputWatt = powerOutput
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse treadmill data", e)
                return null
            }
        }

        /**
         * Build FTMS Treadmill Data characteristic value for GATT server.
         *
         * @param data The treadmill data to encode
         * @return Encoded byte array
         */
        fun build(data: FTMSTreadmillData): ByteArray {
            val buffer = ByteBuffer.allocate(64).order(ByteOrder.LITTLE_ENDIAN)
            var flags = 0

            // Calculate flags based on present fields
            if (data.averageSpeedKmh != null) flags = flags or FLAG_AVERAGE_SPEED
            if (data.totalDistanceMeters != null) flags = flags or FLAG_TOTAL_DISTANCE
            if (data.inclinationPercent != null || data.rampAngleDegrees != null) flags = flags or FLAG_INCLINATION_RAMP
            if (data.elevationGainMeters != null) flags = flags or FLAG_ELEVATION_GAIN
            if (data.instantaneousPaceMinPerKm != null) flags = flags or FLAG_INSTANTANEOUS_PACE
            if (data.averagePaceMinPerKm != null) flags = flags or FLAG_AVERAGE_PACE
            if (data.totalEnergyKCal != null) flags = flags or FLAG_EXPENDED_ENERGY
            if (data.heartRateBpm != null) flags = flags or FLAG_HEART_RATE
            if (data.metabolicEquivalent != null) flags = flags or FLAG_METABOLIC_EQUIVALENT
            if (data.elapsedTimeSeconds != null) flags = flags or FLAG_ELAPSED_TIME
            if (data.remainingTimeSeconds != null) flags = flags or FLAG_REMAINING_TIME
            if (data.forceOnBeltNewton != null || data.powerOutputWatt != null) flags = flags or FLAG_FORCE_POWER

            // Write flags
            buffer.putShort(flags.toShort())

            // Write instantaneous speed (always present)
            val speedRaw = (data.instantaneousSpeedKmh / 0.01f).toInt().toShort()
            buffer.putShort(speedRaw)

            // Write optional fields
            data.averageSpeedKmh?.let {
                buffer.putShort((it / 0.01f).toInt().toShort())
            }

            data.totalDistanceMeters?.let { dist ->
                buffer.put((dist and 0xFF).toByte())
                buffer.put(((dist shr 8) and 0xFF).toByte())
                buffer.put(((dist shr 16) and 0xFF).toByte())
            }

            if (data.inclinationPercent != null || data.rampAngleDegrees != null) {
                buffer.putShort(((data.inclinationPercent ?: 0f) / 0.1f).toInt().toShort())
                buffer.putShort(((data.rampAngleDegrees ?: 0f) / 0.1f).toInt().toShort())
            }

            data.elevationGainMeters?.let {
                buffer.putShort((it / 0.1f).toInt().toShort())
            }

            data.instantaneousPaceMinPerKm?.let {
                buffer.put(it.toInt().toByte())
            }

            data.averagePaceMinPerKm?.let {
                buffer.put(it.toInt().toByte())
            }

            if (data.totalEnergyKCal != null) {
                buffer.putShort(data.totalEnergyKCal.toShort())
                buffer.putShort((data.energyPerHourKCal ?: 0).toShort())
                buffer.put((data.energyPerMinuteKCal ?: 0).toByte())
            }

            data.heartRateBpm?.let {
                buffer.put(it.toByte())
            }

            data.metabolicEquivalent?.let {
                buffer.put((it / 0.1f).toInt().toByte())
            }

            data.elapsedTimeSeconds?.let {
                buffer.putShort(it.toShort())
            }

            data.remainingTimeSeconds?.let {
                buffer.putShort(it.toShort())
            }

            if (data.forceOnBeltNewton != null || data.powerOutputWatt != null) {
                buffer.putShort((data.forceOnBeltNewton ?: 0).toShort())
                buffer.putShort((data.powerOutputWatt ?: 0).toShort())
            }

            return buffer.array().copyOf(buffer.position())
        }
    }
}