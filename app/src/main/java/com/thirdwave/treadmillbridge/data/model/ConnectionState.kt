package com.thirdwave.treadmillbridge.data.model

/**
 * Sealed class representing treadmill connection states.
 * Type-safe state representation for UI.
 */
sealed class ConnectionState {
    data object Disconnected : ConnectionState()
    data object Connecting : ConnectionState()
    data class Connected(val deviceName: String) : ConnectionState()
    data class Failed(val reason: String) : ConnectionState()
    
    /**
     * Convenience property to check if currently connected.
     */
    val isConnected: Boolean
        get() = this is Connected
}
