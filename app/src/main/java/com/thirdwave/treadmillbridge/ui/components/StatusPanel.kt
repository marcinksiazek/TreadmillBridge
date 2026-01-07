package com.thirdwave.treadmillbridge.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.thirdwave.treadmillbridge.data.model.ConnectionState
import com.thirdwave.treadmillbridge.data.model.GattServerState

@Composable
fun StatusPanel(
    connectionState: ConnectionState,
    gattServerState: GattServerState,
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

            val gattStatus = when (gattServerState) {
                is GattServerState.Stopped -> "Not running"
                is GattServerState.Starting -> "Starting..."
                is GattServerState.Running -> "Started"
                is GattServerState.ClientConnected -> "Started (Client: ${gattServerState.clientName})"
            }
            Text(
                text = "GATT Server: $gattStatus",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun StatusPanelPreview() {
    // Show multiple variations stacked
    Column {
        StatusPanel(
            connectionState = ConnectionState.Disconnected,
            gattServerState = GattServerState.Stopped
        )

        StatusPanel(
            connectionState = ConnectionState.Connecting,
            gattServerState = GattServerState.Starting
        )

        StatusPanel(
            connectionState = ConnectionState.Connected(deviceName = "Treadmill X"),
            gattServerState = GattServerState.Running
        )

        StatusPanel(
            connectionState = ConnectionState.Failed(reason = "Timeout"),
            gattServerState = GattServerState.ClientConnected(clientName = "Phone A")
        )
    }
}
