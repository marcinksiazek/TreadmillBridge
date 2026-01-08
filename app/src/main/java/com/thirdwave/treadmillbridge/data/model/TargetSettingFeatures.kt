package com.thirdwave.treadmillbridge.data.model

/**
 * Represents Target Setting Features from FTMS Fitness Machine Feature characteristic (0x2ACC).
 * Contains bits 0-13 of the Target Setting Features field (bytes 4-7).
 */
data class TargetSettingFeatures(
    val speedTargetSettingSupported: Boolean = false,
    val inclinationTargetSettingSupported: Boolean = false,
    val resistanceTargetSettingSupported: Boolean = false,
    val powerTargetSettingSupported: Boolean = false,
    val heartRateTargetSettingSupported: Boolean = false,
    val targetedExpendedEnergySupported: Boolean = false,
    val targetedStepNumberSupported: Boolean = false,
    val targetedStrideNumberSupported: Boolean = false,
    val targetedDistanceSupported: Boolean = false,
    val targetedTrainingTimeSupported: Boolean = false,
    val targetedTimeInTwoHrZonesSupported: Boolean = false,
    val targetedTimeInThreeHrZonesSupported: Boolean = false,
    val targetedTimeInFiveHrZonesSupported: Boolean = false,
    val indoorBikeSimulationSupported: Boolean = false,
    val wheelCircumferenceConfigurationSupported: Boolean = false,
    val spinDownControlSupported: Boolean = false,
    val targetedCadenceConfigurationSupported: Boolean = false
) {
    /**
     * List of human-readable labels for supported target setting features.
     * Used for displaying feature chips in the UI.
     */
    val supportedFeatureLabels: List<String>
        get() = buildList {
            if (speedTargetSettingSupported) add("Speed Target")
            if (inclinationTargetSettingSupported) add("Incline Target")
            if (resistanceTargetSettingSupported) add("Resistance Target")
            if (powerTargetSettingSupported) add("Power Target")
            if (heartRateTargetSettingSupported) add("HR Target")
            if (targetedExpendedEnergySupported) add("Energy Target")
            if (targetedStepNumberSupported) add("Step Target")
            if (targetedStrideNumberSupported) add("Stride Target")
            if (targetedDistanceSupported) add("Distance Target")
            if (targetedTrainingTimeSupported) add("Time Target")
            if (targetedTimeInTwoHrZonesSupported) add("Time in Z2")
            if (targetedTimeInThreeHrZonesSupported) add("Time in Z3")
            if (targetedTimeInFiveHrZonesSupported) add("Time in Z5")
            if (indoorBikeSimulationSupported) add("Bike Simulation")
            if (wheelCircumferenceConfigurationSupported) add("Wheel Circumference")
            if (spinDownControlSupported) add("Spin Down")
            if (targetedCadenceConfigurationSupported) add("Cadence Target")
        }
}
