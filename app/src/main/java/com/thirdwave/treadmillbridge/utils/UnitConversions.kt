package com.thirdwave.treadmillbridge.utils

/**
 * Utility functions for unit conversions commonly used in fitness applications.
 */
object UnitConversions {
    /**
     * Convert speed from kilometers per hour to pace in minutes per kilometer.
     *
     * @param speedKmh Speed in km/h
     * @return Pace in minutes per km, or null if speed is zero or negative
     */
    fun kmhToPaceMinPerKm(speedKmh: Float): Float? {
        return if (speedKmh > 0) {
            60f / speedKmh
        } else {
            null
        }
    }

    /**
     * Convert pace from minutes per kilometer to speed in kilometers per hour.
     *
     * @param paceMinPerKm Pace in minutes per km
     * @return Speed in km/h, or null if pace is zero or negative
     */
    fun paceMinPerKmToKmh(paceMinPerKm: Float): Float? {
        return if (paceMinPerKm > 0) {
            60f / paceMinPerKm
        } else {
            null
        }
    }

    /**
     * Format pace in minutes per kilometer as MM:SS string.
     *
     * @param paceMinPerKm Pace in minutes per km
     * @return Formatted string like "4:48" or null if pace is null
     */
    fun formatPace(paceMinPerKm: Float?): String? {
        if (paceMinPerKm == null || paceMinPerKm <= 0) return null

        val minutes = paceMinPerKm.toInt()
        val seconds = ((paceMinPerKm - minutes) * 60).toInt()
        return String.format("%d:%02d", minutes, seconds)
    }

    /**
     * Convert speed from km/h to pace and format as MM:SS string.
     *
     * @param speedKmh Speed in km/h
     * @return Formatted pace string like "4:48" or null if speed is zero or negative
     */
    fun speedToPaceString(speedKmh: Float): String? {
        val pace = kmhToPaceMinPerKm(speedKmh)
        return formatPace(pace)
    }

    /**
     * Convert kilometers per hour to meters per second.
     *
     * @param kmh Speed in km/h
     * @return Speed in m/s
     */
    fun kmhToMs(kmh: Float): Float {
        return kmh / 3.6f
    }

    /**
     * Convert meters per second to kilometers per hour.
     *
     * @param ms Speed in m/s
     * @return Speed in km/h
     */
    fun msToKmh(ms: Float): Float {
        return ms * 3.6f
    }

    /**
     * Convert kilometers to miles.
     *
     * @param km Distance in kilometers
     * @return Distance in miles
     */
    fun kmToMiles(km: Float): Float {
        return km * 0.621371f
    }

    /**
     * Convert miles to kilometers.
     *
     * @param miles Distance in miles
     * @return Distance in kilometers
     */
    fun milesToKm(miles: Float): Float {
        return miles / 0.621371f
    }

    /**
     * Convert meters to feet.
     *
     * @param meters Distance in meters
     * @return Distance in feet
     */
    fun metersToFeet(meters: Float): Float {
        return meters * 3.28084f
    }

    /**
     * Convert feet to meters.
     *
     * @param feet Distance in feet
     * @return Distance in meters
     */
    fun feetToMeters(feet: Float): Float {
        return feet / 3.28084f
    }

    /**
     * Format time in seconds as HH:MM:SS or MM:SS string.
     *
     * @param seconds Time in seconds
     * @return Formatted time string
     */
    fun formatTime(seconds: Int): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60

        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, secs)
        } else {
            String.format("%d:%02d", minutes, secs)
        }
    }
}