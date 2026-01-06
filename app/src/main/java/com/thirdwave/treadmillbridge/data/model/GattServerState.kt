package com.thirdwave.treadmillbridge.data.model

/**
 * Sealed class representing GATT server lifecycle state.
 */
sealed class GattServerState {
    data object Stopped : GattServerState()
    data object Starting : GattServerState()
    data object Running : GattServerState()
    data class ClientConnected(val clientName: String) : GattServerState()
    
    /**
     * Convenience property to check if server is running.
     */
    val isRunning: Boolean
        get() = this is Running || this is ClientConnected
}
