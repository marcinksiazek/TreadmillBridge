package com.thirdwave.treadmillbridge.ble

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * FTMS Machine Status characteristic (0x2ADA) parser.
 * Handles opcodes 0x00-0x12 with optional parameters per FTMS spec.
 */
sealed class FTMSMachineStatus {
    abstract val humanReadableMessage: String

    // 0x00: Reset
    object Reset : FTMSMachineStatus() {
        override val humanReadableMessage = "Machine reset"
    }

    // 0x01: Stopped or Paused
    sealed class StoppedOrPaused : FTMSMachineStatus() {
        object StoppedByUser : StoppedOrPaused() {
            override val humanReadableMessage = "Stopped by user"
        }
        object PausedByUser : StoppedOrPaused() {
            override val humanReadableMessage = "Paused by user"
        }
        object StoppedBySafetyKey : StoppedOrPaused() {
            override val humanReadableMessage = "Emergency stop - safety key removed"
        }
        object StoppedByError : StoppedOrPaused() {
            override val humanReadableMessage = "Stopped due to error"
        }
        object StoppedByEmergencyStop : StoppedOrPaused() {
            override val humanReadableMessage = "Emergency stop activated"
        }
        data class StoppedUnknownReason(val reason: Int) : StoppedOrPaused() {
            override val humanReadableMessage = "Stopped (reason code: $reason)"
        }
    }

    // 0x02: Started or Resumed
    object StartedOrResumed : FTMSMachineStatus() {
        override val humanReadableMessage = "Started by user"
    }

    // 0x03: Target Speed Changed
    data class TargetSpeedChanged(val speedKmh: Float) : FTMSMachineStatus() {
        override val humanReadableMessage = "Target speed changed to %.1f km/h".format(speedKmh)
    }

    // 0x04: Target Incline Changed
    data class TargetInclineChanged(val inclinePercent: Float) : FTMSMachineStatus() {
        override val humanReadableMessage = "Target incline changed to %.1f%%".format(inclinePercent)
    }

    // 0x05: Target Resistance Changed
    data class TargetResistanceChanged(val resistanceLevel: Int) : FTMSMachineStatus() {
        override val humanReadableMessage = "Target resistance changed to $resistanceLevel"
    }

    // 0x06: Target Power Changed
    data class TargetPowerChanged(val powerWatts: Int) : FTMSMachineStatus() {
        override val humanReadableMessage = "Target power changed to $powerWatts W"
    }

    // 0x07: Target Heart Rate Changed
    data class TargetHeartRateChanged(val heartRateBpm: Int) : FTMSMachineStatus() {
        override val humanReadableMessage = "Target heart rate changed to $heartRateBpm bpm"
    }

    // 0x08: Targeted Expended Energy Changed
    data class TargetedEnergyChanged(val energyKcal: Int) : FTMSMachineStatus() {
        override val humanReadableMessage = "Target energy changed to $energyKcal kcal"
    }

    // 0x09: Targeted Steps Changed
    data class TargetedStepsChanged(val steps: Int) : FTMSMachineStatus() {
        override val humanReadableMessage = "Target steps changed to $steps"
    }

    // 0x0A: Targeted Strides Changed
    data class TargetedStridesChanged(val strides: Int) : FTMSMachineStatus() {
        override val humanReadableMessage = "Target strides changed to $strides"
    }

    // 0x0B: Targeted Distance Changed
    data class TargetedDistanceChanged(val distanceMeters: Long) : FTMSMachineStatus() {
        override val humanReadableMessage = "Target distance changed to %.2f km".format(distanceMeters / 1000.0)
    }

    // 0x0C: Targeted Training Time Changed
    data class TargetedTrainingTimeChanged(val timeSeconds: Int) : FTMSMachineStatus() {
        override val humanReadableMessage: String
            get() {
                val minutes = timeSeconds / 60
                val seconds = timeSeconds % 60
                return "Target time changed to $minutes min $seconds sec"
            }
    }

    // 0x0D: Targeted HR Zone Time Changed
    data class TargetedHrZoneTimeChanged(val zone: Int, val timeSeconds: Int) : FTMSMachineStatus() {
        override val humanReadableMessage: String
            get() {
                val minutes = timeSeconds / 60
                val seconds = timeSeconds % 60
                return "Target HR zone $zone: $minutes min $seconds sec"
            }
    }

    // 0x0E: Indoor Bike Simulation Changed
    data class IndoorBikeSimulationChanged(
        val windSpeed: Float,
        val grade: Float,
        val crr: Float,
        val cw: Float
    ) : FTMSMachineStatus() {
        override val humanReadableMessage = "Simulation: Wind %.2f m/s, Grade %.1f%%, Crr %.4f, Cw %.2f".format(windSpeed, grade, crr, cw)
    }

    // 0x0F: Wheel Circumference Changed
    data class WheelCircumferenceChanged(val circumferenceMm: Int) : FTMSMachineStatus() {
        override val humanReadableMessage = "Wheel circumference changed to $circumferenceMm mm"
    }

    // 0x10: Spin Down Started
    object SpinDownStarted : FTMSMachineStatus() {
        override val humanReadableMessage = "Spin down calibration started"
    }

    // 0x11: Spin Down Completed
    sealed class SpinDownCompleted : FTMSMachineStatus() {
        object Success : SpinDownCompleted() {
            override val humanReadableMessage = "Spin down calibration completed successfully"
        }
        object Failed : SpinDownCompleted() {
            override val humanReadableMessage = "Spin down calibration failed"
        }
        object Aborted : SpinDownCompleted() {
            override val humanReadableMessage = "Spin down calibration aborted"
        }
        data class UnknownResult(val result: Int) : SpinDownCompleted() {
            override val humanReadableMessage = "Spin down calibration completed (result: $result)"
        }
    }

    // 0x12: Control Permission Lost
    sealed class ControlPermissionLost : FTMSMachineStatus() {
        object AnotherClientTookControl : ControlPermissionLost() {
            override val humanReadableMessage = "Control permission lost - another client took control"
        }
        object DeviceReset : ControlPermissionLost() {
            override val humanReadableMessage = "Control permission lost - device reset"
        }
        object BleLinkLost : ControlPermissionLost() {
            override val humanReadableMessage = "Control permission lost - BLE link lost"
        }
        object UserPressedPhysicalStop : ControlPermissionLost() {
            override val humanReadableMessage = "Control permission lost - user pressed physical stop"
        }
        data class UnknownReason(val reason: Int) : ControlPermissionLost() {
            override val humanReadableMessage = "Control permission lost (reason: $reason)"
        }
    }

    companion object {
        /**
         * Parse FTMS Machine Status characteristic data.
         * Returns null if data is invalid or opcode is unsupported.
         */
        fun parse(data: ByteArray): FTMSMachineStatus? {
            if (data.isEmpty()) return null

            val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
            val opcode = buffer.get().toInt() and 0xFF

            return when (opcode) {
                0x00 -> Reset

                0x01 -> {
                    // Stopped or Paused - UINT8 reason
                    if (buffer.remaining() >= 1) {
                        when (val reason = buffer.get().toInt() and 0xFF) {
                            0 -> StoppedOrPaused.StoppedByUser
                            1 -> StoppedOrPaused.PausedByUser
                            2 -> StoppedOrPaused.StoppedBySafetyKey
                            3 -> StoppedOrPaused.StoppedByError
                            4 -> StoppedOrPaused.StoppedByEmergencyStop
                            else -> StoppedOrPaused.StoppedUnknownReason(reason)
                        }
                    } else null
                }

                0x02 -> StartedOrResumed

                0x03 -> {
                    // Target Speed Changed - UINT16 (km/h with 0.01 resolution)
                    if (buffer.remaining() >= 2) {
                        val rawSpeed = buffer.short.toInt() and 0xFFFF
                        TargetSpeedChanged(rawSpeed / 100.0f)
                    } else null
                }

                0x04 -> {
                    // Target Incline Changed - SINT16 (percent with 0.1 resolution)
                    if (buffer.remaining() >= 2) {
                        val rawIncline = buffer.short.toInt()
                        TargetInclineChanged(rawIncline / 10.0f)
                    } else null
                }

                0x05 -> {
                    // Target Resistance Changed - SINT16 (level)
                    if (buffer.remaining() >= 2) {
                        val level = buffer.short.toInt()
                        TargetResistanceChanged(level)
                    } else null
                }

                0x06 -> {
                    // Target Power Changed - SINT16 (watts)
                    if (buffer.remaining() >= 2) {
                        val power = buffer.short.toInt()
                        TargetPowerChanged(power)
                    } else null
                }

                0x07 -> {
                    // Target Heart Rate Changed - UINT8 (bpm)
                    if (buffer.remaining() >= 1) {
                        val hr = buffer.get().toInt() and 0xFF
                        TargetHeartRateChanged(hr)
                    } else null
                }

                0x08 -> {
                    // Targeted Expended Energy Changed - UINT16 (kcal)
                    if (buffer.remaining() >= 2) {
                        val energy = buffer.short.toInt() and 0xFFFF
                        TargetedEnergyChanged(energy)
                    } else null
                }

                0x09 -> {
                    // Targeted Steps Changed - UINT16
                    if (buffer.remaining() >= 2) {
                        val steps = buffer.short.toInt() and 0xFFFF
                        TargetedStepsChanged(steps)
                    } else null
                }

                0x0A -> {
                    // Targeted Strides Changed - UINT16
                    if (buffer.remaining() >= 2) {
                        val strides = buffer.short.toInt() and 0xFFFF
                        TargetedStridesChanged(strides)
                    } else null
                }

                0x0B -> {
                    // Targeted Distance Changed - UINT32 (meters)
                    if (buffer.remaining() >= 4) {
                        val distance = buffer.int.toLong() and 0xFFFFFFFFL
                        TargetedDistanceChanged(distance)
                    } else null
                }

                0x0C -> {
                    // Targeted Training Time Changed - UINT16 (seconds)
                    if (buffer.remaining() >= 2) {
                        val time = buffer.short.toInt() and 0xFFFF
                        TargetedTrainingTimeChanged(time)
                    } else null
                }

                0x0D -> {
                    // Targeted HR Zone Time Changed - UINT8 zone + UINT16 seconds
                    if (buffer.remaining() >= 3) {
                        val zone = buffer.get().toInt() and 0xFF
                        val time = buffer.short.toInt() and 0xFFFF
                        TargetedHrZoneTimeChanged(zone, time)
                    } else null
                }

                0x0E -> {
                    // Indoor Bike Simulation Changed - 4 × SINT16
                    // Wind Speed (m/s with 0.001 resolution)
                    // Grade (percent with 0.01 resolution)
                    // Crr (coefficient with 0.0001 resolution)
                    // Cw (kg/m with 0.01 resolution)
                    if (buffer.remaining() >= 8) {
                        val windSpeed = buffer.short.toInt() / 1000.0f
                        val grade = buffer.short.toInt() / 100.0f
                        val crr = buffer.short.toInt() / 10000.0f
                        val cw = buffer.short.toInt() / 100.0f
                        IndoorBikeSimulationChanged(windSpeed, grade, crr, cw)
                    } else null
                }

                0x0F -> {
                    // Wheel Circumference Changed - UINT16 (mm)
                    if (buffer.remaining() >= 2) {
                        val circumference = buffer.short.toInt() and 0xFFFF
                        WheelCircumferenceChanged(circumference)
                    } else null
                }

                0x10 -> SpinDownStarted

                0x11 -> {
                    // Spin Down Completed - UINT8 result
                    if (buffer.remaining() >= 1) {
                        when (val result = buffer.get().toInt() and 0xFF) {
                            0 -> SpinDownCompleted.Success
                            1 -> SpinDownCompleted.Failed
                            2 -> SpinDownCompleted.Aborted
                            else -> SpinDownCompleted.UnknownResult(result)
                        }
                    } else null
                }

                0x12 -> {
                    // Control Permission Lost - UINT8 reason
                    if (buffer.remaining() >= 1) {
                        when (val reason = buffer.get().toInt() and 0xFF) {
                            0 -> ControlPermissionLost.AnotherClientTookControl
                            1 -> ControlPermissionLost.DeviceReset
                            2 -> ControlPermissionLost.BleLinkLost
                            3 -> ControlPermissionLost.UserPressedPhysicalStop
                            else -> ControlPermissionLost.UnknownReason(reason)
                        }
                    } else null
                }

                else -> null // Unsupported opcode
            }
        }
    }
}
