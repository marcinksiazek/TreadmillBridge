package com.thirdwave.treadmillbridge.domain.model

/**
 * BLE operation errors for error handling.
 * Sealed class hierarchy for type-safe error representation.
 */
sealed class BleError : Exception() {
    data class PermissionDenied(val permission: String) : BleError()
    data object BluetoothDisabled : BleError()
    data object DeviceNotFound : BleError()
    data class ConnectionFailed(val reason: String) : BleError()
    data class ServiceNotSupported(val service: String) : BleError()
}
