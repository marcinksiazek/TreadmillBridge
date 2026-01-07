package com.thirdwave.treadmillbridge.data.model

/**
 * Sealed class representing the treadmill control state.
 * Tracks whether the app has control permission over the treadmill.
 */
sealed class TreadmillControlState {
    /** App does not have control permission */
    data object NotControlling : TreadmillControlState()

    /** App has control permission granted by the treadmill */
    data object Controlling : TreadmillControlState()

    val hasControl: Boolean
        get() = this is Controlling
}
