package com.thirdwave.treadmillbridge.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.thirdwave.treadmillbridge.ui.state.TreadmillUiState
import com.thirdwave.treadmillbridge.ui.theme.TreadmillBridgeTheme
import java.util.Locale

@Composable
fun ControlScreen(
    uiState: TreadmillUiState,
    onRequestControl: () -> Unit,
    onResetMachine: () -> Unit,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
    onSpeedChange: (Float) -> Unit,
    onInclineChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val isConnected = uiState.isConnected
    val supportsSpeed = uiState.supportsSpeedControl
    val supportsIncline = uiState.supportsInclineControl

    // Local slider state
    var speed by rememberSaveable { mutableFloatStateOf(0f) }
    var incline by rememberSaveable { mutableFloatStateOf(0f) }

    // Get dynamic ranges from connected treadmill (or defaults)
    val speedRange = uiState.speedRange
    val inclineRange = uiState.inclineRange

    // Sync slider state with metrics when first connected or values change significantly
    LaunchedEffect(uiState.metrics.speedKph, isConnected, speedRange) {
        if (isConnected && uiState.metrics.speedKph > 0) {
            speed = uiState.metrics.speedKph.coerceIn(speedRange.minKmh, speedRange.maxKmh)
        }
    }

    LaunchedEffect(uiState.metrics.inclinePercent, isConnected, inclineRange) {
        if (isConnected && uiState.metrics.inclinePercent > 0) {
            incline = uiState.metrics.inclinePercent.coerceIn(inclineRange.minPercent, inclineRange.maxPercent)
        }
    }

    Column(modifier = modifier.padding(16.dp)) {
        Text(text = "Control", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(12.dp))

        // Request Control & Reset buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = onRequestControl,
                enabled = isConnected
            ) {
                Text("Request Control")
            }

            OutlinedButton(
                onClick = onResetMachine,
                enabled = isConnected
            ) {
                Text("Reset")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Start / Pause / Stop buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ElevatedButton(
                onClick = onStart,
                enabled = isConnected
            ) {
                Icon(imageVector = Icons.Filled.PlayArrow, contentDescription = "Start")
            }

            ElevatedButton(
                onClick = onPause,
                enabled = isConnected
            ) {
                Icon(imageVector = Icons.Filled.Pause, contentDescription = "Pause")
            }

            ElevatedButton(
                onClick = onStop,
                enabled = isConnected
            ) {
                Icon(imageVector = Icons.Filled.Stop, contentDescription = "Stop")
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Speed control - using dynamic ranges from treadmill
        val speedSliderRange = speedRange.minKmh..speedRange.maxKmh
        val speedStep = speedRange.stepKmh
        val speedEnabled = isConnected && supportsSpeed
        val speedSteps = ((speedSliderRange.endInclusive - speedSliderRange.start) / speedStep).toInt() - 1

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = {
                    val newSpeed = (speed - speedStep).coerceAtLeast(speedSliderRange.start)
                    speed = newSpeed
                    onSpeedChange(newSpeed)
                },
                enabled = speedEnabled
            ) {
                Icon(imageVector = Icons.Filled.Remove, contentDescription = "Decrease speed")
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (supportsSpeed || !isConnected) "Speed" else "Speed (not supported)",
                    style = MaterialTheme.typography.titleSmall,
                    color = if (speedEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value = speed,
                    onValueChange = { speed = it },
                    onValueChangeFinished = { onSpeedChange(speed) },
                    valueRange = speedSliderRange,
                    steps = speedSteps.coerceAtLeast(0),
                    enabled = speedEnabled
                )
                Text(
                    text = String.format(Locale.getDefault(), "%.1f km/h", speed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(
                onClick = {
                    val newSpeed = (speed + speedStep).coerceAtMost(speedSliderRange.endInclusive)
                    speed = newSpeed
                    onSpeedChange(newSpeed)
                },
                enabled = speedEnabled
            ) {
                Icon(imageVector = Icons.Filled.Add, contentDescription = "Increase speed")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Incline control - using dynamic ranges from treadmill
        val inclineSliderRange = inclineRange.minPercent..inclineRange.maxPercent
        val inclineStep = inclineRange.stepPercent
        val inclineEnabled = isConnected && supportsIncline
        val inclineSteps = ((inclineSliderRange.endInclusive - inclineSliderRange.start) / inclineStep).toInt() - 1

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = {
                    val newIncline = (incline - inclineStep).coerceAtLeast(inclineSliderRange.start)
                    incline = newIncline
                    onInclineChange(newIncline)
                },
                enabled = inclineEnabled
            ) {
                Icon(imageVector = Icons.Filled.Remove, contentDescription = "Decrease incline")
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (supportsIncline || !isConnected) "Incline" else "Incline (not supported)",
                    style = MaterialTheme.typography.titleSmall,
                    color = if (inclineEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value = incline,
                    onValueChange = { incline = it },
                    onValueChangeFinished = { onInclineChange(incline) },
                    valueRange = inclineSliderRange,
                    steps = inclineSteps.coerceAtLeast(0),
                    enabled = inclineEnabled
                )
                Text(
                    text = String.format(Locale.getDefault(), "%.1f %%", incline),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(
                onClick = {
                    val newIncline = (incline + inclineStep).coerceAtMost(inclineSliderRange.endInclusive)
                    incline = newIncline
                    onInclineChange(newIncline)
                },
                enabled = inclineEnabled
            ) {
                Icon(imageVector = Icons.Filled.Add, contentDescription = "Increase incline")
            }
        }

        // Show connection hint if not connected
        if (!isConnected) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Connect to a treadmill to control it",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ControlScreenPreview() {
    TreadmillBridgeTheme {
        ControlScreen(
            uiState = TreadmillUiState(),
            onRequestControl = {},
            onResetMachine = {},
            onStart = {},
            onPause = {},
            onStop = {},
            onSpeedChange = {},
            onInclineChange = {}
        )
    }
}
