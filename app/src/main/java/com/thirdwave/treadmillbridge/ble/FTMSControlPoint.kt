package com.thirdwave.treadmillbridge.ble

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * FTMS Control Point opcodes per FTMS spec v1.0.
 */
object FTMSControlPointOpcode {
    const val REQUEST_CONTROL = 0x00
    const val RESET = 0x01
    const val SET_TARGET_SPEED = 0x02
    const val SET_TARGET_INCLINATION = 0x03
    const val START_OR_RESUME = 0x07
    const val STOP_OR_PAUSE = 0x08
    const val SET_TARGETED_DISTANCE = 0x0C
    const val SET_TARGETED_TRAINING_TIME = 0x0D
    const val RESPONSE_CODE = 0x80
}

/**
 * FTMS Control Point result codes.
 */
enum class FTMSControlPointResult(val code: Int) {
    SUCCESS(0x01),
    NOT_SUPPORTED(0x02),
    INVALID_PARAMETER(0x03),
    OPERATION_FAILED(0x04),
    CONTROL_NOT_PERMITTED(0x05);

    companion object {
        fun fromCode(code: Int): FTMSControlPointResult? =
            entries.find { it.code == code }
    }

    val humanReadableMessage: String
        get() = when (this) {
            SUCCESS -> "Command successful"
            NOT_SUPPORTED -> "Operation not supported"
            INVALID_PARAMETER -> "Invalid parameter"
            OPERATION_FAILED -> "Operation failed"
            CONTROL_NOT_PERMITTED -> "Control not permitted - request control first"
        }
}

/**
 * Sealed class representing control point responses.
 */
sealed class FTMSControlPointResponse {
    abstract val requestedOpcode: Int
    abstract val resultCode: FTMSControlPointResult
    abstract val isSuccess: Boolean
    abstract val humanReadableMessage: String

    data class Success(
        override val requestedOpcode: Int
    ) : FTMSControlPointResponse() {
        override val resultCode = FTMSControlPointResult.SUCCESS
        override val isSuccess = true
        override val humanReadableMessage = "Command successful"
    }

    data class Error(
        override val requestedOpcode: Int,
        override val resultCode: FTMSControlPointResult
    ) : FTMSControlPointResponse() {
        override val isSuccess = false
        override val humanReadableMessage: String
            get() {
                val operation = when (requestedOpcode) {
                    FTMSControlPointOpcode.REQUEST_CONTROL -> "Request control"
                    FTMSControlPointOpcode.RESET -> "Reset"
                    FTMSControlPointOpcode.SET_TARGET_SPEED -> "Set speed"
                    FTMSControlPointOpcode.SET_TARGET_INCLINATION -> "Set incline"
                    FTMSControlPointOpcode.START_OR_RESUME -> "Start"
                    FTMSControlPointOpcode.STOP_OR_PAUSE -> "Stop/Pause"
                    FTMSControlPointOpcode.SET_TARGETED_DISTANCE -> "Set distance"
                    FTMSControlPointOpcode.SET_TARGETED_TRAINING_TIME -> "Set time"
                    else -> "Command"
                }
                return "$operation failed: ${resultCode.humanReadableMessage}"
            }
    }

    companion object {
        /**
         * Parse Control Point indication response.
         * Format: [0x80, request_opcode, result_code, optional_params...]
         */
        fun parse(data: ByteArray): FTMSControlPointResponse? {
            if (data.size < 3) return null

            val responseCode = data[0].toInt() and 0xFF
            if (responseCode != FTMSControlPointOpcode.RESPONSE_CODE) return null

            val requestedOpcode = data[1].toInt() and 0xFF
            val resultCode = FTMSControlPointResult.fromCode(data[2].toInt() and 0xFF)
                ?: return null

            return if (resultCode == FTMSControlPointResult.SUCCESS) {
                Success(requestedOpcode)
            } else {
                Error(requestedOpcode, resultCode)
            }
        }
    }
}

/**
 * Builder for FTMS Control Point commands.
 */
object FTMSControlPointCommand {

    /** Request control of the fitness machine (must be called first) */
    fun requestControl(): ByteArray = byteArrayOf(FTMSControlPointOpcode.REQUEST_CONTROL.toByte())

    /** Reset the fitness machine */
    fun reset(): ByteArray = byteArrayOf(FTMSControlPointOpcode.RESET.toByte())

    /**
     * Set target speed.
     * @param speedKmh Speed in km/h (resolution: 0.01 km/h)
     */
    fun setTargetSpeed(speedKmh: Float): ByteArray {
        val speedRaw = (speedKmh * 100).toInt().coerceIn(0, 65535)
        return ByteBuffer.allocate(3)
            .order(ByteOrder.LITTLE_ENDIAN)
            .put(FTMSControlPointOpcode.SET_TARGET_SPEED.toByte())
            .putShort(speedRaw.toShort())
            .array()
    }

    /**
     * Set target inclination.
     * @param inclinePercent Incline in percent (resolution: 0.1%)
     */
    fun setTargetInclination(inclinePercent: Float): ByteArray {
        val inclineRaw = (inclinePercent * 10).toInt().coerceIn(-32768, 32767)
        return ByteBuffer.allocate(3)
            .order(ByteOrder.LITTLE_ENDIAN)
            .put(FTMSControlPointOpcode.SET_TARGET_INCLINATION.toByte())
            .putShort(inclineRaw.toShort())
            .array()
    }

    /** Start or resume the workout */
    fun startOrResume(): ByteArray = byteArrayOf(FTMSControlPointOpcode.START_OR_RESUME.toByte())

    /**
     * Stop or pause the workout.
     * @param pause true = pause, false = stop
     */
    fun stopOrPause(pause: Boolean): ByteArray {
        return byteArrayOf(
            FTMSControlPointOpcode.STOP_OR_PAUSE.toByte(),
            if (pause) 0x01 else 0x00
        )
    }

    /**
     * Set targeted distance.
     * @param distanceMeters Distance in meters (UINT24, max 16777215)
     */
    fun setTargetedDistance(distanceMeters: Int): ByteArray {
        val distance = distanceMeters.coerceIn(0, 16777215)
        return byteArrayOf(
            FTMSControlPointOpcode.SET_TARGETED_DISTANCE.toByte(),
            (distance and 0xFF).toByte(),
            ((distance shr 8) and 0xFF).toByte(),
            ((distance shr 16) and 0xFF).toByte()
        )
    }

    /**
     * Set targeted training time.
     * @param timeSeconds Time in seconds (UINT16, max 65535)
     */
    fun setTargetedTrainingTime(timeSeconds: Int): ByteArray {
        val time = timeSeconds.coerceIn(0, 65535)
        return ByteBuffer.allocate(3)
            .order(ByteOrder.LITTLE_ENDIAN)
            .put(FTMSControlPointOpcode.SET_TARGETED_TRAINING_TIME.toByte())
            .putShort(time.toShort())
            .array()
    }
}
