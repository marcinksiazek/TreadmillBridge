package com.thirdwave.treadmillbridge

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.DevicesOther
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.thirdwave.treadmillbridge.data.model.ConnectionState
import com.thirdwave.treadmillbridge.data.model.DiscoveredDevice
import com.thirdwave.treadmillbridge.data.model.HrDiscoveryState
import com.thirdwave.treadmillbridge.data.model.HrMonitorMetrics
import com.thirdwave.treadmillbridge.data.model.TreadmillMetrics
import com.thirdwave.treadmillbridge.ui.components.StatusPanel
import com.thirdwave.treadmillbridge.ui.screens.ControlScreen
import com.thirdwave.treadmillbridge.ui.screens.DashboardScreen
import com.thirdwave.treadmillbridge.ui.screens.DevicesScreen
import com.thirdwave.treadmillbridge.ui.state.TreadmillUiState
import com.thirdwave.treadmillbridge.ui.theme.TreadmillBridgeTheme
import com.thirdwave.treadmillbridge.ui.viewmodel.TreadmillViewModel
import dagger.hilt.android.AndroidEntryPoint
import androidx.compose.runtime.mutableStateOf as runtimeMutableStateOf

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Required permissions for Android 12+
        val requiredPermissions = arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.ACCESS_FINE_LOCATION
        )

        // Check if already granted
        val initiallyGranted = requiredPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

        // Observed state for Compose
        val permissionsGrantedState = runtimeMutableStateOf(initiallyGranted)

        // Register launcher for RequestMultiplePermissions
        val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            val granted = results.values.all { it }
            permissionsGrantedState.value = granted
        }

        // Request missing permissions
        if (!initiallyGranted) {
            val missing = requiredPermissions.filter {
                ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }.toTypedArray()
            if (missing.isNotEmpty()) {
                permissionLauncher.launch(missing)
            }
        }

        setContent {
            TreadmillBridgeTheme {
                val viewModel: TreadmillViewModel = viewModel()
                val uiState by viewModel.uiState.collectAsState()

                TreadmillBridgeApp(
                    uiState = uiState,
                    onStartScan = viewModel::onStartScan,
                    onStopScan = viewModel::onStopScan,
                    onConnectToDevice = viewModel::onConnectToDevice,
                    onDisconnectTreadmill = viewModel::onDisconnectTreadmill,
                    onStartGattServer = viewModel::onStartGattServer,
                    onStopGattServer = viewModel::onStopGattServer,
                    onStartHrScan = viewModel::onStartHrScan,
                    onStopHrScan = viewModel::onStopHrScan,
                    onConnectToHrDevice = viewModel::onConnectToHrDevice,
                    onDisconnectHrMonitor = viewModel::onDisconnectHrMonitor,
                    onRequestControl = viewModel::onRequestControl,
                    onResetMachine = viewModel::onResetMachine,
                    onStartOrResume = viewModel::onStartOrResume,
                    onPauseMachine = viewModel::onPauseMachine,
                    onStopMachine = viewModel::onStopMachine,
                    onSetTargetSpeed = viewModel::onSetTargetSpeed,
                    onSetTargetInclination = viewModel::onSetTargetInclination
                )
            }
        }
    }
}

enum class AppDestinations(
    val label: String,
    val icon: ImageVector,
) {
    DASHBOARD("Dashboard", Icons.Filled.Dashboard),
    CONTROL("Control", Icons.Filled.Favorite),
    DEVICES("Devices", Icons.Filled.DevicesOther),
}

@Preview(showBackground = true)
@Composable
fun TreadmillBridgeAppPreview() {
    TreadmillBridgeTheme {
        PreviewTreadmillBridgeApp()
    }
}

@Composable
private fun PreviewTreadmillBridgeApp() {
    var currentDestination by remember { mutableStateOf(AppDestinations.DASHBOARD) }
    val mockDevices = remember {
        listOf(
            DiscoveredDevice(
                device = null,
                name = "NordicTrack T8.5S",
                address = "AA:BB:CC:DD:EE:01",
                rssi = -65
            ),
            DiscoveredDevice(
                device = null,
                name = "ProForm Pro 2000",
                address = "AA:BB:CC:DD:EE:02",
                rssi = -72
            ),
            DiscoveredDevice(
                device = null,
                name = "Sole F80",
                address = "AA:BB:CC:DD:EE:03",
                rssi = -80
            )
        )
    }

    // simple mock ui state for preview
    val mockUiState = remember {
        TreadmillUiState(
            metrics = TreadmillMetrics(speedKph = 12.5f, inclinePercent = 5.0f, cadence = 150),
            connectionState = ConnectionState.Disconnected,
            gattServerState = com.thirdwave.treadmillbridge.data.model.GattServerState.Stopped,
            discoveryState = com.thirdwave.treadmillbridge.data.model.DiscoveryState(isScanning = true, discoveredDevices = mockDevices),
            permissionsGranted = true
        )
    }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            AppDestinations.entries.forEach {
                item(
                    icon = {
                        Icon(
                            it.icon,
                            contentDescription = it.label
                        )
                    },
                    label = { Text(it.label) },
                    selected = it == currentDestination,
                    onClick = { currentDestination = it }
                )
            }
        }
    ) {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            Box(modifier = Modifier.fillMaxSize()) {
                // Render content depending on selected destination
                when (currentDestination) {
                    AppDestinations.DASHBOARD -> DashboardScreen(
                        uiState = mockUiState,
                        modifier = Modifier.padding(innerPadding).padding(16.dp)
                    )

                    AppDestinations.CONTROL -> ControlScreen(
                        uiState = mockUiState,
                        onRequestControl = {},
                        onResetMachine = {},
                        onStart = {},
                        onPause = {},
                        onStop = {},
                        onSpeedChange = {},
                        onInclineChange = {},
                        modifier = Modifier.padding(innerPadding).padding(16.dp)
                    )

                    AppDestinations.DEVICES -> DevicesScreen(
                        uiState = mockUiState,
                        onStartScan = {},
                        onStopScan = {},
                        onConnectToDevice = {},
                        onDisconnectTreadmill = {},
                        onStartGattServer = {},
                        onStopGattServer = {},
                        onStartHrScan = {},
                        onStopHrScan = {},
                        onConnectToHrDevice = {},
                        onDisconnectHrMonitor = {},
                        modifier = Modifier.padding(innerPadding).padding(16.dp))
                }
            }
        }
    }
}

@Composable
fun TreadmillBridgeApp(
    uiState: TreadmillUiState,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onConnectToDevice: (String) -> Unit,
    onDisconnectTreadmill: () -> Unit,
    onStartGattServer: () -> Unit,
    onStopGattServer: () -> Unit,
    onStartHrScan: () -> Unit,
    onStopHrScan: () -> Unit,
    onConnectToHrDevice: (String) -> Unit,
    onDisconnectHrMonitor: () -> Unit,
    onRequestControl: () -> Unit,
    onResetMachine: () -> Unit,
    onStartOrResume: () -> Unit,
    onPauseMachine: () -> Unit,
    onStopMachine: () -> Unit,
    onSetTargetSpeed: (Float) -> Unit,
    onSetTargetInclination: (Float) -> Unit
) {
    var currentDestination by rememberSaveable { mutableStateOf(AppDestinations.DASHBOARD) }
    val snackbarHostState = remember { SnackbarHostState() }

    // Show Snackbar when machine status message changes
    LaunchedEffect(uiState.machineStatusMessage) {
        uiState.machineStatusMessage?.let { statusMessage ->
            snackbarHostState.showSnackbar(
                message = statusMessage.status.humanReadableMessage,
                duration = SnackbarDuration.Short
            )
        }
    }

    // Show Snackbar on control point errors (not success)
    LaunchedEffect(uiState.controlPointResponse) {
        uiState.controlPointResponse?.let { response ->
            if (!response.response.isSuccess) {
                snackbarHostState.showSnackbar(
                    message = response.response.humanReadableMessage,
                    duration = SnackbarDuration.Short
                )
            }
        }
    }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            AppDestinations.entries.forEach {
                item(
                    icon = {
                        Icon(
                            it.icon,
                            contentDescription = it.label
                        )
                    },
                    label = { Text(it.label) },
                    selected = it == currentDestination,
                    onClick = { currentDestination = it }
                )
            }
        }
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { innerPadding ->
            Box(modifier = Modifier.fillMaxSize()) {
                // Render content depending on selected destination
                when (currentDestination) {
                    AppDestinations.DASHBOARD -> DashboardScreen(
                        uiState = uiState,
                        modifier = Modifier
                            .padding(innerPadding)
                            .padding(16.dp)
                            .padding(bottom = 120.dp) // Make room for status panel
                    )

                    AppDestinations.CONTROL -> ControlScreen(
                        uiState = uiState,
                        onRequestControl = onRequestControl,
                        onResetMachine = onResetMachine,
                        onStart = onStartOrResume,
                        onPause = onPauseMachine,
                        onStop = onStopMachine,
                        onSpeedChange = onSetTargetSpeed,
                        onInclineChange = onSetTargetInclination,
                        modifier = Modifier.padding(innerPadding).padding(16.dp)
                    )

                    AppDestinations.DEVICES -> DevicesScreen(
                        uiState = uiState,
                        onStartScan = onStartScan,
                        onStopScan = onStopScan,
                        onConnectToDevice = onConnectToDevice,
                        onDisconnectTreadmill = onDisconnectTreadmill,
                        onStartGattServer = onStartGattServer,
                        onStopGattServer = onStopGattServer,
                        onStartHrScan = onStartHrScan,
                        onStopHrScan = onStopHrScan,
                        onConnectToHrDevice = onConnectToHrDevice,
                        onDisconnectHrMonitor = onDisconnectHrMonitor,
                        modifier = Modifier.padding(innerPadding).padding(16.dp))
                }
            }
        }
    }
}
