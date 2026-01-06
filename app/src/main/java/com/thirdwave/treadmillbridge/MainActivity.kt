package com.thirdwave.treadmillbridge

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf as runtimeMutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.thirdwave.treadmillbridge.data.model.ConnectionState
import com.thirdwave.treadmillbridge.data.model.DiscoveredDevice
import com.thirdwave.treadmillbridge.ui.state.TreadmillUiState
import com.thirdwave.treadmillbridge.ui.theme.TreadmillBridgeTheme
import com.thirdwave.treadmillbridge.ui.viewmodel.TreadmillViewModel
import com.thirdwave.treadmillbridge.utils.UnitConversions
import dagger.hilt.android.AndroidEntryPoint

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
                    onStopGattServer = viewModel::onStopGattServer
                )
            }
        }
    }
}

enum class AppDestinations(
    val label: String,
    val icon: ImageVector,
) {
    HOME("Home", Icons.Default.Home),
    FAVORITES("Favorites", Icons.Default.Favorite),
    PROFILE("Profile", Icons.Default.AccountBox),
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
    var currentDestination by remember { mutableStateOf(AppDestinations.HOME) }
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
                Column(modifier = Modifier.padding(innerPadding).padding(16.dp)) {
                    Text(text = "Speed: 12.5 kph", style = MaterialTheme.typography.bodyLarge)
                    Text(text = "Pace: 4:48 min/km", style = MaterialTheme.typography.bodyLarge)
                    Text(text = "Incline: 5.0 %", style = MaterialTheme.typography.bodyLarge)
                    Text(text = "Cadence: 150", style = MaterialTheme.typography.bodyLarge)

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = { },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Start GATT Server")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = { },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Connect Treadmill")
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Discovered Devices:",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    LazyColumn {
                        items(mockDevices) { device ->
                            DeviceListItem(
                                device = device,
                                onConnect = { }
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }

                // Status panel at bottom
                StatusPanel(
                    connectionState = ConnectionState.Disconnected,
                    gattServerState = com.thirdwave.treadmillbridge.data.model.GattServerState.Stopped,
                    modifier = Modifier.align(androidx.compose.ui.Alignment.BottomCenter)
                )
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
    onStopGattServer: () -> Unit
) {
    var currentDestination by rememberSaveable { mutableStateOf(AppDestinations.HOME) }

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
                Column(
                    modifier = Modifier
                        .padding(innerPadding)
                        .padding(16.dp)
                        .padding(bottom = 120.dp) // Make room for status panel
                ) {
                    Text(text = "Speed: ${uiState.metrics.speedKph} kph", style = MaterialTheme.typography.bodyLarge)

                    // Display pace in min/km format using conversion utility
                    Text(
                        text = uiState.metrics.paceString?.let { "Pace: $it min/km" } ?: "Pace: -- min/km",
                        style = MaterialTheme.typography.bodyLarge
                    )

                    Text(text = "Incline: ${uiState.metrics.inclinePercent} %", style = MaterialTheme.typography.bodyLarge)
                    Text(text = "Cadence: ${uiState.metrics.cadence}", style = MaterialTheme.typography.bodyLarge)

                    Spacer(modifier = Modifier.height(16.dp))

                    // GATT Server buttons
                    if (!uiState.gattServerState.isRunning) {
                        Button(
                            onClick = onStartGattServer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Start GATT Server")
                        }
                    } else {
                        Button(
                            onClick = onStopGattServer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Stop GATT Server")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Treadmill connection buttons using sealed class
                    when (uiState.connectionState) {
                        is ConnectionState.Disconnected -> {
                            Button(
                                onClick = onStartScan,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Connect Treadmill")
                            }
                        }
                        is ConnectionState.Connecting -> {
                            Button(
                                onClick = { },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = false
                            ) {
                                Text("Connecting...")
                            }
                        }
                        is ConnectionState.Connected -> {
                            Button(
                                onClick = onDisconnectTreadmill,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Disconnect Treadmill")
                            }
                        }
                        is ConnectionState.Failed -> {
                            Button(
                                onClick = onStartScan,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Retry Connection")
                            }
                        }
                    }

                    // Stop Scanning button (only when scanning)
                    if (uiState.discoveryState.isScanning && !uiState.connectionState.isConnected) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = onStopScan,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Stop Scanning")
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Device list (only when scanning and not connected)
                    if (uiState.discoveryState.isScanning &&
                        !uiState.connectionState.isConnected &&
                        uiState.discoveryState.discoveredDevices.isNotEmpty()) {
                        Text(
                            text = "Discovered Devices:",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        LazyColumn {
                            items(uiState.discoveryState.discoveredDevices) { device ->
                                DeviceListItem(
                                    device = device,
                                    onConnect = { onConnectToDevice(device.address) }
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                    }
                }

                // Status panel at bottom
                StatusPanel(
                    connectionState = uiState.connectionState,
                    gattServerState = uiState.gattServerState,
                    modifier = Modifier.align(androidx.compose.ui.Alignment.BottomCenter)
                )
            }
        }
    }
}

@Composable
fun StatusPanel(
    connectionState: ConnectionState,
    gattServerState: com.thirdwave.treadmillbridge.data.model.GattServerState,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Bluetooth Status",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Treadmill connection status using sealed class
            val treadmillStatus = when (connectionState) {
                is ConnectionState.Disconnected -> "Not connected"
                is ConnectionState.Connecting -> "Connecting..."
                is ConnectionState.Connected -> "Connected to ${connectionState.deviceName}"
                is ConnectionState.Failed -> "Failed: ${connectionState.reason}"
            }
            Text(
                text = "Treadmill: $treadmillStatus",
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(4.dp))

            // GATT Server status using sealed class
            val gattStatus = when (gattServerState) {
                is com.thirdwave.treadmillbridge.data.model.GattServerState.Stopped -> "Not running"
                is com.thirdwave.treadmillbridge.data.model.GattServerState.Starting -> "Starting..."
                is com.thirdwave.treadmillbridge.data.model.GattServerState.Running -> "Started"
                is com.thirdwave.treadmillbridge.data.model.GattServerState.ClientConnected ->
                    "Started (Client: ${gattServerState.clientName})"
            }
            Text(
                text = "GATT Server: $gattStatus",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
fun DeviceListItem(device: DiscoveredDevice, onConnect: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.name ?: "Unknown Device",
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = device.address,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Signal: ${device.rssi} dBm",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Button(onClick = onConnect) {
                Text("Connect")
            }
        }
    }
}