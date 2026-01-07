package com.thirdwave.treadmillbridge.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.thirdwave.treadmillbridge.ui.theme.TreadmillBridgeTheme
import java.util.Locale
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Remove

@Composable
fun ControlScreen(
    modifier: Modifier = Modifier,
    initialSpeed: Float = 0f,
    initialIncline: Float = 0f,
    onStart: () -> Unit = {},
    onPause: () -> Unit = {},
    onStop: () -> Unit = {}
) {
    Column(modifier = modifier.padding(16.dp)) {
        Text(text = "Control", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(12.dp))

        // Combined buttons: Start / Pause / Stop
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ElevatedButton(onClick = { onStart() }) {
                Icon(imageVector = Icons.Filled.PlayArrow, contentDescription = "Start")
            }

            ElevatedButton(onClick = { onPause() }) {
                Icon(imageVector = Icons.Filled.Pause, contentDescription = "Pause")
            }

            ElevatedButton(onClick = { onStop() }) {
                Icon(imageVector = Icons.Filled.Stop, contentDescription = "Stop")
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Speed control
        var speed by rememberSaveable { mutableStateOf(initialSpeed.coerceIn(0f, 20f)) }
        val speedRange = 0f..20f
        val speedStep = 0.5f

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(onClick = { speed = (speed - speedStep).coerceAtLeast(speedRange.start) }) {
                Icon(imageVector = Icons.Filled.Remove, contentDescription = "Decrease speed")
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Speed", style = MaterialTheme.typography.titleSmall)
                Slider(
                    value = speed,
                    onValueChange = { speed = it },
                    valueRange = speedRange,
                    steps = ((speedRange.endInclusive - speedRange.start) / speedStep).toInt() - 1
                )
                Text(text = String.format(Locale.getDefault(), "%.1f km/h", speed), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            IconButton(onClick = { speed = (speed + speedStep).coerceAtMost(speedRange.endInclusive) }) {
                Icon(imageVector = Icons.Filled.Add, contentDescription = "Increase speed")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Incline control
        var incline by rememberSaveable { mutableStateOf(initialIncline.coerceIn(0f, 15f)) }
        val inclineRange = 0f..15f
        val inclineStep = 0.5f

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(onClick = { incline = (incline - inclineStep).coerceAtLeast(inclineRange.start) }) {
                Icon(imageVector = Icons.Filled.Remove, contentDescription = "Decrease incline")
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Incline", style = MaterialTheme.typography.titleSmall)
                Slider(
                    value = incline,
                    onValueChange = { incline = it },
                    valueRange = inclineRange,
                    steps = ((inclineRange.endInclusive - inclineRange.start) / inclineStep).toInt() - 1
                )
                Text(text = String.format(Locale.getDefault(), "%.1f %%", incline), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            IconButton(onClick = { incline = (incline + inclineStep).coerceAtMost(inclineRange.endInclusive) }) {
                Icon(imageVector = Icons.Filled.Add, contentDescription = "Increase incline")
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ControlScreenPreview() {
    TreadmillBridgeTheme {
        ControlScreen()
    }
}
