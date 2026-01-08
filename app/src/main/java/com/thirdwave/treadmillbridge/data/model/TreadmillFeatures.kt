package com.thirdwave.treadmillbridge.data.model

/**
 * Represents supported features from FTMS Fitness Machine Feature characteristic (0x2ACC).
 * Contains bits 0-15 of the Fitness Machine Features field.
 */
data class TreadmillFeatures(
    val averageSpeedSupported: Boolean = false,
    val cadenceSupported: Boolean = false,
    val totalDistanceSupported: Boolean = false,
    val inclinationSupported: Boolean = false,
    val elevationGainSupported: Boolean = false,
    val paceSupported: Boolean = false,
    val stepCountSupported: Boolean = false,
    val resistanceLevelSupported: Boolean = false,
    val strideCountSupported: Boolean = false,
    val expendedEnergySupported: Boolean = false,
    val heartRateMeasurementSupported: Boolean = false,
    val metabolicEquivalentSupported: Boolean = false,
    val elapsedTimeSupported: Boolean = false,
    val remainingTimeSupported: Boolean = false,
    val powerMeasurementSupported: Boolean = false,
    val forceOnBeltAndPowerOutputSupported: Boolean = false,
    val userDataRetentionSupported: Boolean = false
) {
    /**
     * List of human-readable labels for supported features.
     * Used for displaying feature chips in the UI.
     */
    val supportedFeatureLabels: List<String>
        get() = buildList {
            if (averageSpeedSupported) add("Avg Speed")
            if (cadenceSupported) add("Cadence")
            if (totalDistanceSupported) add("Distance")
            if (inclinationSupported) add("Incline")
            if (elevationGainSupported) add("Elevation")
            if (paceSupported) add("Pace")
            if (stepCountSupported) add("Steps")
            if (resistanceLevelSupported) add("Resistance")
            if (strideCountSupported) add("Stride")
            if (expendedEnergySupported) add("Energy")
            if (heartRateMeasurementSupported) add("Heart Rate")
            if (metabolicEquivalentSupported) add("Metabolic Equivalent")
            if (elapsedTimeSupported) add("Elapsed Time")
            if (remainingTimeSupported) add("Remaining Time")
            if (powerMeasurementSupported) add("Power")
            if (forceOnBeltAndPowerOutputSupported) add("Force on Belt")
            if (userDataRetentionSupported) add("User Data")
        }
}
