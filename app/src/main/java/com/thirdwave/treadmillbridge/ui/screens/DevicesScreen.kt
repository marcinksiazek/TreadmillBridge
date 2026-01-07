package com.thirdwave.treadmillbridge.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.thirdwave.treadmillbridge.data.model.ConnectionState
import com.thirdwave.treadmillbridge.data.model.DiscoveredDevice
import com.thirdwave.treadmillbridge.data.model.DiscoveryState
import com.thirdwave.treadmillbridge.data.model.HrDiscoveryState
import com.thirdwave.treadmillbridge.data.model.HrMonitorMetrics
import com.thirdwave.treadmillbridge.data.model.TargetSettingFeatures
import com.thirdwave.treadmillbridge.data.model.TreadmillFeatures
import com.thirdwave.treadmillbridge.data.model.TreadmillMetrics
import com.thirdwave.treadmillbridge.ui.components.DeviceListItem
import com.thirdwave.treadmillbridge.ui.state.TreadmillUiState
import com.thirdwave.treadmillbridge.ui.theme.TreadmillBridgeTheme

/**
 * Devices screen with three sections:
 * 1) HR Monitor (single connection)
 * 2) Treadmill (single connection + feature chips)
 * 3) GATT Server management (start/stop + connected clients list)
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DevicesScreen(
    uiState: TreadmillUiState,
    // Treadmill callbacks
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onConnectToDevice: (String) -> Unit,
    onDisconnectTreadmill: () -> Unit,
    onStartGattServer: () -> Unit,
    onStopGattServer: () -> Unit,
    // HR Monitor callbacks
    onStartHrScan: () -> Unit,
    onStopHrScan: () -> Unit,
    onConnectToHrDevice: (String) -> Unit,
    onDisconnectHrMonitor: () -> Unit,
    // Optional override parameters for preview/testing
    connectedTreadmill: DiscoveredDevice? = uiState.connectedTreadmillDevice,
    treadmillRunState: String? = null,
    gattConnectedClients: List<String> = emptyList(),
    onDisconnectGattClient: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .padding(12.dp)
            .verticalScroll(scrollState)
    ) {
        // ===== HR Monitor Section =====
        Text(text = "HR Monitor", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(8.dp))

        val hrConnected = uiState.hrConnectionState is ConnectionState.Connected
        val hrScanning = uiState.hrDiscoveryState.isScanning

        when {
            hrConnected -> {
                // Show connected HR card
                val connectedHr = uiState.connectedHrDevice
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = connectedHr?.name ?: (uiState.hrConnectionState as? ConnectionState.Connected)?.deviceName ?: "Unknown HR",
                            style = MaterialTheme.typography.titleMedium
                        )
                        connectedHr?.let {
                            Text(
                                text = it.address,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            connectedHr?.let {
                                Text(
                                    text = "Signal: ${it.rssi} dBm",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            val hrValue = uiState.hrMetrics.heartRateBpm.takeIf { it > 0 }
                            Text(
                                text = "HR: ${hrValue ?: "--"} bpm",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Battery: ${uiState.hrMetrics.batteryPercent ?: "--"}%",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedButton(onClick = onDisconnectHrMonitor) {
                            Text("Disconnect")
                        }
                    }
                }
            }

            hrScanning -> {
                // Show scanning state with progress and discovered devices
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Scanning for HR monitors...",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(onClick = onStopHrScan, modifier = Modifier.fillMaxWidth()) {
                    Text("Stop Scanning")
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Show discovered HR devices
                val hrDevices = uiState.hrDiscoveryState.discoveredDevices
                if (hrDevices.isNotEmpty()) {
                    Text(
                        text = "Discovered HR Monitors:",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    hrDevices.forEach { device ->
                        DeviceListItem(
                            device = device,
                            onConnect = { onConnectToHrDevice(device.address) }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }

            else -> {
                // Show connect button when not connected and not scanning
                val hrDevices = uiState.hrDiscoveryState.discoveredDevices
                if (hrDevices.isEmpty()) {
                    Text(
                        text = "No HR monitors discovered.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = onStartHrScan, modifier = Modifier.fillMaxWidth()) {
                        Text("Connect HR Monitor")
                    }
                } else {
                    // Show previously discovered devices
                    Text(
                        text = "Available HR Monitors:",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    hrDevices.forEach { device ->
                        DeviceListItem(
                            device = device,
                            onConnect = { onConnectToHrDevice(device.address) }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(onClick = onStartHrScan, modifier = Modifier.fillMaxWidth()) {
                        Text("Scan Again")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(16.dp))

        // ===== Treadmill Section =====
        Text(text = "Treadmill", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(8.dp))

        val treadmillConnected = uiState.connectionState is ConnectionState.Connected
        val treadmillScanning = uiState.discoveryState.isScanning

        when {
            treadmillConnected && connectedTreadmill != null -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = connectedTreadmill.name ?: "Unknown",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = connectedTreadmill.address,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Signal: ${connectedTreadmill.rssi} dBm",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "State: ${treadmillRunState ?: "Stopped"}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Machine feature chips from FTMS Feature characteristic
                        val featureLabels = uiState.treadmillFeatures?.supportedFeatureLabels ?: emptyList()
                        if (featureLabels.isNotEmpty()) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                featureLabels.forEach { feature ->
                                    AssistChip(
                                        onClick = { },
                                        label = { Text(feature, style = MaterialTheme.typography.labelSmall) },
                                        enabled = false,
                                        colors = AssistChipDefaults.assistChipColors()
                                    )
                                }
                            }
                        }

                        // Target setting feature chips
                        val targetLabels = uiState.targetSettingFeatures?.supportedFeatureLabels ?: emptyList()
                        if (targetLabels.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                targetLabels.forEach { feature ->
                                    AssistChip(
                                        onClick = { },
                                        label = { Text(feature, style = MaterialTheme.typography.labelSmall) },
                                        enabled = false,
                                        colors = AssistChipDefaults.assistChipColors(
                                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                            labelColor = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedButton(onClick = onDisconnectTreadmill) {
                            Text("Disconnect")
                        }
                    }
                }
            }

            treadmillScanning -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Scanning for treadmills...",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(onClick = onStopScan, modifier = Modifier.fillMaxWidth()) {
                    Text("Stop Scanning")
                }

                Spacer(modifier = Modifier.height(8.dp))

                val treadmillDevices = uiState.discoveryState.discoveredDevices
                if (treadmillDevices.isNotEmpty()) {
                    Text(
                        text = "Discovered Treadmills:",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    treadmillDevices.forEach { device ->
                        DeviceListItem(
                            device = device,
                            onConnect = { onConnectToDevice(device.address) }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }

            else -> {
                val treadmillDevices = uiState.discoveryState.discoveredDevices
                if (treadmillDevices.isEmpty()) {
                    Text(
                        text = "No treadmills discovered.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = onStartScan, modifier = Modifier.fillMaxWidth()) {
                        Text("Connect Treadmill")
                    }
                } else {
                    Text(
                        text = "Available Treadmills:",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    treadmillDevices.forEach { device ->
                        DeviceListItem(
                            device = device,
                            onConnect = { onConnectToDevice(device.address) }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(onClick = onStartScan, modifier = Modifier.fillMaxWidth()) {
                        Text("Scan Again")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(16.dp))

        // ===== GATT Server Section =====
        Text(text = "GATT Server", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(8.dp))

        if (!uiState.gattServerState.isRunning) {
            Button(onClick = onStartGattServer, modifier = Modifier.fillMaxWidth()) {
                Text("Start GATT Server")
            }
        } else {
            OutlinedButton(onClick = onStopGattServer, modifier = Modifier.fillMaxWidth()) {
                Text("Stop GATT Server")
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (gattConnectedClients.isEmpty()) {
                Text(
                    text = "No connected clients",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = "Connected Clients:",
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(modifier = Modifier.height(4.dp))
                gattConnectedClients.forEach { client ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = client, style = MaterialTheme.typography.bodyMedium)
                        IconButton(onClick = { onDisconnectGattClient(client) }) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Disconnect"
                            )
                        }
                    }
                }
            }
        }

        // Bottom padding for scroll
        Spacer(modifier = Modifier.height(100.dp))
    }
}

// ===== Previews =====

@OptIn(ExperimentalLayoutApi::class)
@Preview(showBackground = true)
@Composable
fun DevicesScreenPreview() {
    val mockHr = DiscoveredDevice(
        device = null,
        name = "Polar H10",
        address = "11:22:33:44:55:66",
        rssi = -48
    )
    val mockTreadmill = DiscoveredDevice(
        device = null,
        name = "NordicTrack T8.5S",
        address = "AA:BB:CC:DD:EE:01",
        rssi = -65
    )
    val mockFeatures = TreadmillFeatures(
        averageSpeedSupported = true,
        cadenceSupported = true,
        totalDistanceSupported = true,
        inclinationSupported = true,
        paceSupported = true,
        elapsedTimeSupported = true
    )
    val mockTargetFeatures = TargetSettingFeatures(
        speedTargetSettingSupported = true,
        inclinationTargetSettingSupported = true,
        heartRateTargetSettingSupported = true,
        targetedDistanceSupported = true
    )

    val mockState = TreadmillUiState(
        metrics = TreadmillMetrics(speedKph = 10.0f, inclinePercent = 2.0f, cadence = 140),
        treadmillFeatures = mockFeatures,
        targetSettingFeatures = mockTargetFeatures,
        connectionState = ConnectionState.Connected(deviceName = "NordicTrack T8.5S"),
        discoveryState = DiscoveryState(isScanning = false, discoveredDevices = listOf(mockTreadmill)),
        hrMetrics = HrMonitorMetrics(heartRateBpm = 145, batteryPercent = 88),
        hrConnectionState = ConnectionState.Connected(deviceName = "Polar H10"),
        hrDiscoveryState = HrDiscoveryState(isScanning = false, discoveredDevices = listOf(mockHr)),
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
            onStartHrScan = {},
            onStopHrScan = {},
            onConnectToHrDevice = {},
            onDisconnectHrMonitor = {},
            connectedTreadmill = mockTreadmill,
            treadmillRunState = "Running",
            gattConnectedClients = listOf("Phone A", "Tablet B")
        )
    }
}

@Preview(name = "Devices - Scanning", showBackground = true)
@Composable
fun DevicesScreenScanningPreview() {
    val mockDevices = listOf(
        DiscoveredDevice(device = null, name = "Polar H10", address = "11:22:33:44:55:66", rssi = -48),
        DiscoveredDevice(device = null, name = "Garmin HRM", address = "AA:BB:CC:DD:EE:FF", rssi = -55)
    )

    val mockState = TreadmillUiState(
        connectionState = ConnectionState.Disconnected,
        discoveryState = DiscoveryState(isScanning = true, discoveredDevices = emptyList()),
        hrConnectionState = ConnectionState.Disconnected,
        hrDiscoveryState = HrDiscoveryState(isScanning = true, discoveredDevices = mockDevices),
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
            onStartHrScan = {},
            onStopHrScan = {},
            onConnectToHrDevice = {},
            onDisconnectHrMonitor = {}
        )
    }
}
