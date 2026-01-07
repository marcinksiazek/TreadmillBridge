package com.thirdwave.treadmillbridge.data.model

/**
 * Sealed class representing the treadmill running state.
 * Derived from Machine Status notifications (0x2ADA).
 *
 * SAFETY: Running state is ONLY set when Machine Status 0x02 (StartedOrResumed) is received.
 * This ensures speed commands are never sent unless the belt is actually moving.
 */
sealed class TreadmillRunningState {
    /** Treadmill is stopped (belt not moving) */
    data object Stopped : TreadmillRunningState()

    /** Treadmill is running (belt moving) - set ONLY from Machine Status 0x02 */
    data object Running : TreadmillRunningState()

    /** Treadmill is paused (belt stopped, can resume) */
    data object Paused : TreadmillRunningState()

    /** Unknown state (e.g., just connected, before any status received) */
    data object Unknown : TreadmillRunningState()
}
