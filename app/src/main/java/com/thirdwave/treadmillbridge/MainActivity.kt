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
import com.thirdwave.treadmillbridge.ui.theme.TreadmillBridgeTheme
import com.thirdwave.treadmillbridge.utils.UnitConversions

class MainActivity : ComponentActivity() {
    private var bridgeManager: BluetoothBridgeManager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        bridgeManager = BluetoothBridgeManager(this)

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
                TreadmillBridgeApp(bridgeManager!!, permissionsGranted = permissionsGrantedState.value)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        bridgeManager?.stop()
        bridgeManager = null
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
                    treadmillConnected = false,
                    treadmillName = null,
                    gattServerRunning = false,
                    gattClientConnected = null,
                    modifier = Modifier.align(androidx.compose.ui.Alignment.BottomCenter)
                )
            }
        }
    }
}

@Composable
fun TreadmillBridgeApp(bridgeManager: BluetoothBridgeManager, permissionsGranted: Boolean) {
    var currentDestination by rememberSaveable { mutableStateOf(AppDestinations.HOME) }
    var discoveredDevices by remember { mutableStateOf<List<DiscoveredDevice>>(emptyList()) }
    var connectedDeviceName by remember { mutableStateOf<String?>(null) }
    var isConnected by remember { mutableStateOf(false) }
    var isScanning by remember { mutableStateOf(false) }
    var isGattServerRunning by remember { mutableStateOf(false) }
    var gattClientConnectedName by remember { mutableStateOf<String?>(null) }

    val manager = remember { bridgeManager }

    // Set up callbacks
    LaunchedEffect(manager) {
        manager.onDeviceDiscovered = { device ->
            // Add to list if not already present
            if (discoveredDevices.none { it.address == device.address }) {
                discoveredDevices = discoveredDevices + device
            }
        }

        manager.onConnectionStateChanged = { connected, deviceName ->
            isConnected = connected
            connectedDeviceName = deviceName
            if (connected) {
                // Clear device list and stop scanning when connected
                discoveredDevices = emptyList()
                isScanning = false
            }
        }

        manager.onGattServerStateChanged = { running ->
            isGattServerRunning = running
        }

        manager.onGattClientConnected = { clientName ->
            gattClientConnectedName = clientName
        }

        manager.onGattClientDisconnected = {
            gattClientConnectedName = null
        }

        // Initialize state from manager
        isGattServerRunning = manager.isGattServerRunning
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
                Column(
                    modifier = Modifier
                        .padding(innerPadding)
                        .padding(16.dp)
                        .padding(bottom = 120.dp) // Make room for status panel
                ) {
                    Text(text = "Speed: ${manager.speedKph} kph", style = MaterialTheme.typography.bodyLarge)

                    // Display pace in min/km format using conversion utility
                    val paceString = UnitConversions.speedToPaceString(manager.speedKph)
                    Text(
                        text = paceString?.let { "Pace: $it min/km" } ?: "Pace: -- min/km",
                        style = MaterialTheme.typography.bodyLarge
                    )

                    Text(text = "Incline: ${manager.inclinePercent} %", style = MaterialTheme.typography.bodyLarge)
                    Text(text = "Cadence: ${manager.cadence}", style = MaterialTheme.typography.bodyLarge)

                    Spacer(modifier = Modifier.height(16.dp))

                    // GATT Server buttons
                    if (!isGattServerRunning) {
                        Button(
                            onClick = { manager.startLocalGattServer() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Start GATT Server")
                        }
                    } else {
                        Button(
                            onClick = { manager.stopLocalGattServer() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Stop GATT Server")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Treadmill connection buttons
                    if (!isConnected) {
                        Button(
                            onClick = {
                                discoveredDevices = emptyList()
                                manager.startScan()
                                isScanning = true
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Connect Treadmill")
                        }
                    } else {
                        Button(
                            onClick = {
                                manager.disconnectTreadmill()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Disconnect Treadmill")
                        }
                    }

                    // Stop Scanning button (only when scanning)
                    if (isScanning && !isConnected) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                manager.stopScan()
                                isScanning = false
                                discoveredDevices = emptyList()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Stop Scanning")
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Device list (only when scanning and not connected)
                    if (isScanning && !isConnected && discoveredDevices.isNotEmpty()) {
                        Text(
                            text = "Discovered Devices:",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        LazyColumn {
                            items(discoveredDevices) { device ->
                                DeviceListItem(
                                    device = device,
                                    onConnect = {
                                        manager.connectToDevice(device.address)
                                        isScanning = false
                                    }
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                    }
                }

                // Status panel at bottom
                StatusPanel(
                    treadmillConnected = isConnected,
                    treadmillName = connectedDeviceName,
                    gattServerRunning = isGattServerRunning,
                    gattClientConnected = gattClientConnectedName,
                    modifier = Modifier.align(androidx.compose.ui.Alignment.BottomCenter)
                )
            }
        }
    }
}

@Composable
fun StatusPanel(
    treadmillConnected: Boolean,
    treadmillName: String?,
    gattServerRunning: Boolean,
    gattClientConnected: String?,
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

            // Treadmill connection status
            Text(
                text = if (treadmillConnected) {
                    "Treadmill: Connected to $treadmillName"
                } else {
                    "Treadmill: Not connected"
                },
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(4.dp))

            // GATT Server status
            Text(
                text = if (gattServerRunning) {
                    "GATT Server: Started"
                } else {
                    "GATT Server: Not running"
                },
                style = MaterialTheme.typography.bodyMedium
            )

            // GATT Client connection status
            if (gattClientConnected != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "GATT Client Connected: $gattClientConnected",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
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