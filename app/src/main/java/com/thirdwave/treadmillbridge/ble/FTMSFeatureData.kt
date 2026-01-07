package com.thirdwave.treadmillbridge.ble

import android.util.Log
import com.thirdwave.treadmillbridge.data.model.TargetSettingFeatures
import com.thirdwave.treadmillbridge.data.model.TreadmillFeatures
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Result of parsing FTMS Feature characteristic containing both feature sets.
 */
data class ParsedFTMSFeatures(
    val machineFeatures: TreadmillFeatures,
    val targetSettingFeatures: TargetSettingFeatures?
)

/**
 * Parser for FTMS Fitness Machine Feature characteristic (0x2ACC).
 *
 * Characteristic format:
 * - Bytes 0-3: Fitness Machine Features (uint32) - bits 0-15 are treadmill features
 * - Bytes 4-7: Target Setting Features (uint32) - bits 0-13 are target settings
 */
object FTMSFeatureData {
    private const val TAG = "FTMSFeatureParser"

    // Fitness Machine Feature bits (0-15)
    private const val FEATURE_AVERAGE_SPEED = 0x0001
    private const val FEATURE_CADENCE = 0x0002
    private const val FEATURE_TOTAL_DISTANCE = 0x0004
    private const val FEATURE_INCLINATION = 0x0008
    private const val FEATURE_ELEVATION_GAIN = 0x0010
    private const val FEATURE_PACE = 0x0020
    private const val FEATURE_STEP_COUNT = 0x0040
    private const val FEATURE_RESISTANCE_LEVEL = 0x0080
    private const val FEATURE_STRIDE_COUNT = 0x0100
    private const val FEATURE_EXPENDED_ENERGY = 0x0200
    private const val FEATURE_HEART_RATE_MEASUREMENT = 0x0400
    private const val FEATURE_METABOLIC_EQUIVALENT = 0x0800
    private const val FEATURE_ELAPSED_TIME = 0x1000
    private const val FEATURE_REMAINING_TIME = 0x2000
    private const val FEATURE_POWER_MEASUREMENT = 0x4000
    private const val FEATURE_FORCE_ON_BELT_AND_POWER_OUTPUT = 0x8000

    // Target Setting Feature bits (0-13)
    private const val TARGET_SPEED_SETTING = 0x0001
    private const val TARGET_INCLINATION_SETTING = 0x0002
    private const val TARGET_RESISTANCE_SETTING = 0x0004
    private const val TARGET_POWER_SETTING = 0x0008
    private const val TARGET_HEART_RATE_SETTING = 0x0010
    private const val TARGET_EXPENDED_ENERGY = 0x0020
    private const val TARGET_STEP_NUMBER = 0x0040
    private const val TARGET_STRIDE_NUMBER = 0x0080
    private const val TARGET_DISTANCE = 0x0100
    private const val TARGET_TRAINING_TIME = 0x0200
    private const val TARGET_TIME_IN_TWO_HR_ZONES = 0x0400
    private const val TARGET_TIME_IN_THREE_HR_ZONES = 0x0800
    private const val TARGET_TIME_IN_FIVE_HR_ZONES = 0x1000
    private const val TARGET_INDOOR_BIKE_SIMULATION = 0x2000

    /**
     * Parse FTMS Fitness Machine Feature characteristic value.
     *
     * @param data Raw characteristic data bytes (minimum 4 bytes, 8 bytes for full parsing)
     * @return Parsed features or null if parsing fails
     */
    fun parse(data: ByteArray): ParsedFTMSFeatures? {
        if (data.isEmpty()) {
            Log.w(TAG, "Empty feature data")
            return null
        }

        if (data.size < 4) {
            Log.w(TAG, "Feature data too short: ${data.size} bytes (minimum 4 bytes required)")
            return null
        }

        return try {
            val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)

            // Read first 4 bytes as uint32, but we only care about bits 0-15
            val machineFeatureBits = buffer.int and 0xFFFF
            Log.d(TAG, "Machine Features: 0x${machineFeatureBits.toString(16).padStart(4, '0')}")

            val machineFeatures = TreadmillFeatures(
                averageSpeedSupported = (machineFeatureBits and FEATURE_AVERAGE_SPEED) != 0,
                cadenceSupported = (machineFeatureBits and FEATURE_CADENCE) != 0,
                totalDistanceSupported = (machineFeatureBits and FEATURE_TOTAL_DISTANCE) != 0,
                inclinationSupported = (machineFeatureBits and FEATURE_INCLINATION) != 0,
                elevationGainSupported = (machineFeatureBits and FEATURE_ELEVATION_GAIN) != 0,
                paceSupported = (machineFeatureBits and FEATURE_PACE) != 0,
                stepCountSupported = (machineFeatureBits and FEATURE_STEP_COUNT) != 0,
                resistanceLevelSupported = (machineFeatureBits and FEATURE_RESISTANCE_LEVEL) != 0,
                strideCountSupported = (machineFeatureBits and FEATURE_STRIDE_COUNT) != 0,
                expendedEnergySupported = (machineFeatureBits and FEATURE_EXPENDED_ENERGY) != 0,
                heartRateMeasurementSupported = (machineFeatureBits and FEATURE_HEART_RATE_MEASUREMENT) != 0,
                metabolicEquivalentSupported = (machineFeatureBits and FEATURE_METABOLIC_EQUIVALENT) != 0,
                elapsedTimeSupported = (machineFeatureBits and FEATURE_ELAPSED_TIME) != 0,
                remainingTimeSupported = (machineFeatureBits and FEATURE_REMAINING_TIME) != 0,
                powerMeasurementSupported = (machineFeatureBits and FEATURE_POWER_MEASUREMENT) != 0,
                forceOnBeltAndPowerOutputSupported = (machineFeatureBits and FEATURE_FORCE_ON_BELT_AND_POWER_OUTPUT) != 0
            )
            Log.i(TAG, "Parsed machine features: ${machineFeatures.supportedFeatureLabels}")

            // Parse Target Setting Features if we have enough data (bytes 4-7)
            val targetSettingFeatures = if (data.size >= 8) {
                val targetFeatureBits = buffer.int and 0x3FFF // bits 0-13
                Log.d(TAG, "Target Setting Features: 0x${targetFeatureBits.toString(16).padStart(4, '0')}")

                TargetSettingFeatures(
                    speedTargetSettingSupported = (targetFeatureBits and TARGET_SPEED_SETTING) != 0,
                    inclinationTargetSettingSupported = (targetFeatureBits and TARGET_INCLINATION_SETTING) != 0,
                    resistanceTargetSettingSupported = (targetFeatureBits and TARGET_RESISTANCE_SETTING) != 0,
                    powerTargetSettingSupported = (targetFeatureBits and TARGET_POWER_SETTING) != 0,
                    heartRateTargetSettingSupported = (targetFeatureBits and TARGET_HEART_RATE_SETTING) != 0,
                    targetedExpendedEnergySupported = (targetFeatureBits and TARGET_EXPENDED_ENERGY) != 0,
                    targetedStepNumberSupported = (targetFeatureBits and TARGET_STEP_NUMBER) != 0,
                    targetedStrideNumberSupported = (targetFeatureBits and TARGET_STRIDE_NUMBER) != 0,
                    targetedDistanceSupported = (targetFeatureBits and TARGET_DISTANCE) != 0,
                    targetedTrainingTimeSupported = (targetFeatureBits and TARGET_TRAINING_TIME) != 0,
                    targetedTimeInTwoHrZonesSupported = (targetFeatureBits and TARGET_TIME_IN_TWO_HR_ZONES) != 0,
                    targetedTimeInThreeHrZonesSupported = (targetFeatureBits and TARGET_TIME_IN_THREE_HR_ZONES) != 0,
                    targetedTimeInFiveHrZonesSupported = (targetFeatureBits and TARGET_TIME_IN_FIVE_HR_ZONES) != 0,
                    indoorBikeSimulationSupported = (targetFeatureBits and TARGET_INDOOR_BIKE_SIMULATION) != 0
                ).also {
                    Log.i(TAG, "Parsed target setting features: ${it.supportedFeatureLabels}")
                }
            } else {
                Log.w(TAG, "Not enough data for target setting features (only ${data.size} bytes)")
                null
            }

            ParsedFTMSFeatures(machineFeatures, targetSettingFeatures)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse feature data", e)
            null
        }
    }
}
