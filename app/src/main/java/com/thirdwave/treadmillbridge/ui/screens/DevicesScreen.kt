package com.thirdwave.treadmillbridge.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import com.thirdwave.treadmillbridge.data.model.DiscoveredDevice
import com.thirdwave.treadmillbridge.data.model.DiscoveryState
import com.thirdwave.treadmillbridge.data.model.TreadmillMetrics
import com.thirdwave.treadmillbridge.ui.components.DeviceListItem
import com.thirdwave.treadmillbridge.ui.state.TreadmillUiState
import com.thirdwave.treadmillbridge.ui.theme.TreadmillBridgeTheme

/**
 * Redesigned Devices screen with three sections:
 * 1) HR Monitor (single connection)
 * 2) Treadmill (single connection + feature chips)
 * 3) GATT Server management (start/stop + connected clients list)
 *
 * The composable accepts optional parameters so callers can pass in current connected devices / states.
 */
@Composable
fun DevicesScreen(
    uiState: TreadmillUiState,
    // callbacks for scanning/connecting (existing)
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onConnectToDevice: (String) -> Unit,
    onDisconnectTreadmill: () -> Unit,
    onStartGattServer: () -> Unit,
    onStopGattServer: () -> Unit,

    // optional higher-level state for connected devices (can be provided by ViewModel)
    connectedHr: DiscoveredDevice? = null,
    hrHeartRate: Int? = null,
    hrBatteryPercent: Int? = null,

    connectedTreadmill: DiscoveredDevice? = null,
    treadmillRunState: String? = null, // "Stopped" | "Paused" | "Running"
    treadmillFeatures: List<String> = listOf("Average Speed", "Cadence", "Incline Control"),

    // GATT server connected clients
    gattConnectedClients: List<String> = emptyList(), // client names or addresses
    onDisconnectGattClient: (String) -> Unit = {},

    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(12.dp)) {
        // HR Monitor section
        Text(text = "HR Monitor", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(8.dp))

        if (connectedHr != null) {
            Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(2.dp)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(text = connectedHr.name ?: "Unknown", style = MaterialTheme.typography.titleMedium)
                    Text(text = connectedHr.address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "Signal: ${connectedHr.rssi} dBm", style = MaterialTheme.typography.bodyMedium)
                        Text(text = "HR: ${hrHeartRate ?: "--"}", style = MaterialTheme.typography.bodyMedium)
                        Text(text = "Battery: ${hrBatteryPercent ?: "--"}%", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        } else {
            // Show available HR devices to connect (filter by name heuristic optional)
            val hrCandidates = uiState.discoveryState.discoveredDevices
            if (hrCandidates.isEmpty()) {
                Text(text = "No HR monitors discovered.", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = onStartScan) { Text("Start Scan") }
            } else {
                LazyColumn {
                    items(hrCandidates) { device ->
                        // For HR list we simply reuse DeviceListItem with connect action
                        DeviceListItem(device = device, onConnect = { onConnectToDevice(device.address) })
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(16.dp))

        // Treadmill section
        Text(text = "Treadmill", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(8.dp))

        if (connectedTreadmill != null) {
            Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(2.dp)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(text = connectedTreadmill.name ?: "Unknown", style = MaterialTheme.typography.titleMedium)
                    Text(text = connectedTreadmill.address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "Signal: ${connectedTreadmill.rssi} dBm", style = MaterialTheme.typography.bodyMedium)
                        Text(text = "State: ${treadmillRunState ?: "Stopped"}", style = MaterialTheme.typography.bodyMedium)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Feature chips
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        treadmillFeatures.forEach { feature ->
                            AssistChip(
                                onClick = { /* feature action - placeholder */ },
                                label = { Text(feature) },
                                enabled = false,
                                colors = AssistChipDefaults.assistChipColors()
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(onClick = onDisconnectTreadmill) { Text("Disconnect") }
                }
            }
        } else {
            // Show list of discovered (candidate) treadmills
            val treadmillCandidates = uiState.discoveryState.discoveredDevices
            if (treadmillCandidates.isEmpty()) {
                Text(text = "No treadmills discovered.", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = onStartScan) { Text("Start Scan") }
            } else {
                LazyColumn {
                    items(treadmillCandidates) { device ->
                        DeviceListItem(device = device, onConnect = { onConnectToDevice(device.address) })
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(16.dp))

        // GATT Server management
        Text(text = "GATT Server", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(8.dp))

        // Start/Stop server depending on state
        if (!uiState.gattServerState.isRunning) {
            Button(onClick = onStartGattServer, modifier = Modifier.fillMaxWidth()) { Text("Start GATT Server") }
        } else {
            Button(onClick = onStopGattServer, modifier = Modifier.fillMaxWidth()) { Text("Stop GATT Server") }

            Spacer(modifier = Modifier.height(12.dp))
            // Show connected clients with disconnect buttons
            if (gattConnectedClients.isEmpty()) {
                Text(text = "No connected clients", style = MaterialTheme.typography.bodyMedium)
            } else {
                LazyColumn {
                    items(gattConnectedClients) { client ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(text = client, style = MaterialTheme.typography.bodyMedium)
                            IconButton(onClick = { onDisconnectGattClient(client) }) {
                                Icon(imageVector = Icons.Filled.PlayArrow, contentDescription = "Disconnect")
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

// ===== Previews with mock connected devices and clients =====
@Preview(showBackground = true)
@Composable
fun DevicesScreenPreview() {
    val mockDevices = listOf(
        DiscoveredDevice(device = null, name = "NordicTrack T8.5S", address = "AA:BB:CC:DD:EE:01", rssi = -65),
        DiscoveredDevice(device = null, name = "ProForm Pro 2000", address = "AA:BB:CC:DD:EE:02", rssi = -72),
        DiscoveredDevice(device = null, name = "Sole F80", address = "AA:BB:CC:DD:EE:03", rssi = -80)
    )

    val mockHr = DiscoveredDevice(device = null, name = "Polar H10", address = "11:22:33:44:55:66", rssi = -48)
    val mockTreadmill = DiscoveredDevice(device = null, name = "NordicTrack T8.5S", address = "AA:BB:CC:DD:EE:01", rssi = -65)

    val mockState = TreadmillUiState(
        metrics = TreadmillMetrics(speedKph = 10.0f, inclinePercent = 2.0f, cadence = 140),
        discoveryState = DiscoveryState(isScanning = true, discoveredDevices = mockDevices),
        permissionsGranted = true
    )

    TreadmillBridgeTheme {
        DevicesScreen(
            uiState = mockState,
            onStartScan = {},
            onStopScan = {},
            onConnectToDevice = {},
            onDisconnectTreadmill = {},
            onStartGattServer = {},
            onStopGattServer = {},
            connectedHr = mockHr,
            hrHeartRate = 72,
            hrBatteryPercent = 88,
            connectedTreadmill = mockTreadmill,
            treadmillRunState = "Running",
            treadmillFeatures = listOf("Average Speed", "Cadence", "Incline Control"),
            gattConnectedClients = listOf("Phone A", "Tablet B"),
            onDisconnectGattClient = {}
        )
    }
}

@Preview(name = "Devices - Portrait", showBackground = true, widthDp = 360, heightDp = 800)
@Composable
fun DevicesScreenPreview_Portrait() {
    val mockDevices = listOf(
        DiscoveredDevice(device = null, name = "NordicTrack T8.5S", address = "AA:BB:CC:DD:EE:01", rssi = -65),
        DiscoveredDevice(device = null, name = "ProForm Pro 2000", address = "AA:BB:CC:DD:EE:02", rssi = -72)
    )

    val mockState = TreadmillUiState(
        metrics = TreadmillMetrics(speedKph = 8.5f, inclinePercent = 1.0f, cadence = 130),
        discoveryState = DiscoveryState(isScanning = true, discoveredDevices = mockDevices),
        permissionsGranted = true
    )

    TreadmillBridgeTheme {
        DevicesScreen(
            uiState = mockState,
            onStartScan = {},
            onStopScan = {},
            onConnectToDevice = {},
            onDisconnectTreadmill = {},
            onStartGattServer = {},
            onStopGattServer = {},
            connectedHr = null,
            connectedTreadmill = mockDevices.firstOrNull(),
            treadmillRunState = "Stopped",
            gattConnectedClients = emptyList()
        )
    }
}

@Preview(name = "Devices - Landscape", showBackground = true, widthDp = 800, heightDp = 360)
@Composable
fun DevicesScreenPreview_Landscape() {
    val mockDevices = listOf(
        DiscoveredDevice(device = null, name = "Sole F80", address = "AA:BB:CC:DD:EE:03", rssi = -80),
        DiscoveredDevice(device = null, name = "Horizon T101", address = "AA:BB:CC:DD:EE:04", rssi = -70),
        DiscoveredDevice(device = null, name = "NordicTrack X22i", address = "AA:BB:CC:DD:EE:05", rssi = -60)
    )

    val mockState = TreadmillUiState(
        metrics = TreadmillMetrics(speedKph = 16.0f, inclinePercent = 0.0f, cadence = 160),
        discoveryState = DiscoveryState(isScanning = true, discoveredDevices = mockDevices),
        permissionsGranted = true
    )

    TreadmillBridgeTheme {
        DevicesScreen(
            uiState = mockState,
            onStartScan = {},
            onStopScan = {},
            onConnectToDevice = {},
            onDisconnectTreadmill = {},
            onStartGattServer = {},
            onStopGattServer = {},
            connectedHr = null,
            connectedTreadmill = null,
            gattConnectedClients = listOf("Phone A")
        )
    }
}
