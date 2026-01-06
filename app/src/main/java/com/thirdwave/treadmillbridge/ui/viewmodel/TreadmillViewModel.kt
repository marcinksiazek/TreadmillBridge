package com.thirdwave.treadmillbridge.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thirdwave.treadmillbridge.data.repository.TreadmillRepository
import com.thirdwave.treadmillbridge.ui.state.TreadmillUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for treadmill UI.
 * Exposes single StateFlow of UI state to Compose.
 * Handles user actions via intent methods.
 */
@HiltViewModel
class TreadmillViewModel @Inject constructor(
    private val repository: TreadmillRepository
) : ViewModel() {
    
    // Combine all repository flows into single UI state
    val uiState: StateFlow<TreadmillUiState> = combine(
        repository.metrics,
        repository.connectionState,
        repository.gattServerState,
        repository.discoveryState
    ) { metrics, connection, gattServer, discovery ->
        TreadmillUiState(
            metrics = metrics,
            connectionState = connection,
            gattServerState = gattServer,
            discoveryState = discovery,
            permissionsGranted = true // TODO: Add permission state management
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = TreadmillUiState()
    )
    
    // User actions (intents)
    fun onStartScan() {
        viewModelScope.launch {
            repository.startScan()
        }
    }
    
    fun onStopScan() {
        viewModelScope.launch {
            repository.stopScan()
        }
    }
    
    fun onConnectToDevice(address: String) {
        viewModelScope.launch {
            repository.stopScan() // Auto-stop scan on connect
            repository.connectToDevice(address)
        }
    }
    
    fun onDisconnectTreadmill() {
        viewModelScope.launch {
            repository.disconnectTreadmill()
        }
    }
    
    fun onStartGattServer() {
        viewModelScope.launch {
            repository.startGattServer()
        }
    }
    
    fun onStopGattServer() {
        viewModelScope.launch {
            repository.stopGattServer()
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch {
            repository.cleanup()
        }
    }
}
