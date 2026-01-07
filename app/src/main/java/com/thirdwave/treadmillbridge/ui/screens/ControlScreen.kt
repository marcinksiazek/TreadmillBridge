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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.thirdwave.treadmillbridge.data.model.ConnectionState
import com.thirdwave.treadmillbridge.data.model.ControlTargets
import com.thirdwave.treadmillbridge.data.model.TargetSettingFeatures
import com.thirdwave.treadmillbridge.data.model.TreadmillControlState
import com.thirdwave.treadmillbridge.data.model.TreadmillRunningState
import com.thirdwave.treadmillbridge.ui.state.TreadmillUiState
import com.thirdwave.treadmillbridge.ui.theme.TreadmillBridgeTheme
import java.util.Locale

// Button highlight colors
private val PlayGreen = Color(0xFF4CAF50)
private val PauseOrange = Color(0xFFFF9800)
private val StopRed = Color(0xFFF44336)

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
    val hasControl = uiState.hasControl
    val runningState = uiState.runningState
    val supportsSpeed = uiState.supportsSpeedControl
    val supportsIncline = uiState.supportsInclineControl

    // Local slider state - synced with control targets
    var speed by rememberSaveable { mutableFloatStateOf(uiState.controlTargets.targetSpeedKmh) }
    var incline by rememberSaveable { mutableFloatStateOf(uiState.controlTargets.targetInclinePercent) }

    // Get dynamic ranges from connected treadmill (or defaults)
    val speedRange = uiState.speedRange
    val inclineRange = uiState.inclineRange

    // Sync slider state when control targets change (e.g., on reset or control loss)
    LaunchedEffect(uiState.controlTargets) {
        speed = uiState.controlTargets.targetSpeedKmh.coerceIn(speedRange.minKmh, speedRange.maxKmh)
        incline = uiState.controlTargets.targetInclinePercent.coerceIn(inclineRange.minPercent, inclineRange.maxPercent)
    }

    // Reset sliders when control is lost
    LaunchedEffect(hasControl) {
        if (!hasControl) {
            speed = 0f
            incline = 0f
        }
    }

    Column(modifier = modifier.padding(16.dp)) {
        Text(text = "Control", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(12.dp))

        // PROMINENT CONTROL LABEL when app has control
        if (hasControl) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = PlayGreen.copy(alpha = 0.15f)
                )
            ) {
                Text(
                    text = "Application is controlling the treadmill",
                    style = MaterialTheme.typography.titleMedium,
                    color = PlayGreen,
                    modifier = Modifier.padding(16.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Request Control & Reset buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = onRequestControl,
                enabled = uiState.requestControlEnabled
            ) {
                Text("Request Control")
            }

            OutlinedButton(
                onClick = onResetMachine,
                enabled = isConnected && hasControl
            ) {
                Text("Reset")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Start / Pause / Stop buttons with highlighting
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // PLAY button - highlighted GREEN when running
            val playHighlighted = runningState == TreadmillRunningState.Running
            ElevatedButton(
                onClick = onStart,
                enabled = uiState.controlButtonsEnabled,
                colors = ButtonDefaults.elevatedButtonColors(
                    containerColor = if (playHighlighted) PlayGreen else MaterialTheme.colorScheme.surface,
                    contentColor = if (playHighlighted) Color.White else MaterialTheme.colorScheme.onSurface
                )
            ) {
                Icon(imageVector = Icons.Filled.PlayArrow, contentDescription = "Start")
            }

            // PAUSE button - highlighted ORANGE when paused
            val pauseHighlighted = runningState == TreadmillRunningState.Paused
            ElevatedButton(
                onClick = onPause,
                enabled = uiState.controlButtonsEnabled,
                colors = ButtonDefaults.elevatedButtonColors(
                    containerColor = if (pauseHighlighted) PauseOrange else MaterialTheme.colorScheme.surface,
                    contentColor = if (pauseHighlighted) Color.White else MaterialTheme.colorScheme.onSurface
                )
            ) {
                Icon(imageVector = Icons.Filled.Pause, contentDescription = "Pause")
            }

            // STOP button - highlighted RED when stopped/unknown
            val stopHighlighted = runningState == TreadmillRunningState.Stopped ||
                    runningState == TreadmillRunningState.Unknown
            ElevatedButton(
                onClick = onStop,
                enabled = uiState.controlButtonsEnabled,
                colors = ButtonDefaults.elevatedButtonColors(
                    containerColor = if (stopHighlighted) StopRed else MaterialTheme.colorScheme.surface,
                    contentColor = if (stopHighlighted) Color.White else MaterialTheme.colorScheme.onSurface
                )
            ) {
                Icon(imageVector = Icons.Filled.Stop, contentDescription = "Stop")
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Speed control - using dynamic ranges from treadmill
        val speedSliderRange = speedRange.minKmh..speedRange.maxKmh
        val speedStep = speedRange.stepKmh
        // SAFETY: Sliders only enabled when Running (after 0x02 received)
        val speedEnabled = uiState.slidersEnabled && supportsSpeed
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
                val speedLabel = when {
                    !isConnected -> "Speed"
                    !supportsSpeed -> "Speed (not supported)"
                    !hasControl -> "Speed (request control)"
                    runningState == TreadmillRunningState.Paused -> "Speed (paused)"
                    runningState == TreadmillRunningState.Stopped -> "Speed (stopped)"
                    runningState == TreadmillRunningState.Unknown -> "Speed (stopped)"
                    else -> "Speed"
                }
                Text(
                    text = speedLabel,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (speedEnabled) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant
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
        // SAFETY: Sliders only enabled when Running (after 0x02 received)
        val inclineEnabled = uiState.slidersEnabled && supportsIncline
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
                val inclineLabel = when {
                    !isConnected -> "Incline"
                    !supportsIncline -> "Incline (not supported)"
                    !hasControl -> "Incline (request control)"
                    runningState == TreadmillRunningState.Paused -> "Incline (paused)"
                    runningState == TreadmillRunningState.Stopped -> "Incline (stopped)"
                    runningState == TreadmillRunningState.Unknown -> "Incline (stopped)"
                    else -> "Incline"
                }
                Text(
                    text = inclineLabel,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (inclineEnabled) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant
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

        // Connection/control hints
        if (!isConnected) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Connect to a treadmill to control it",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else if (!hasControl) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Request control to enable controls",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Preview(showBackground = true, name = "Disconnected")
@Composable
fun ControlScreenDisconnectedPreview() {
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

@Preview(showBackground = true, name = "Connected - No Control")
@Composable
fun ControlScreenConnectedNoControlPreview() {
    TreadmillBridgeTheme {
        ControlScreen(
            uiState = TreadmillUiState(
                connectionState = ConnectionState.Connected("Treadmill X"),
                controlState = TreadmillControlState.NotControlling,
                runningState = TreadmillRunningState.Unknown,
                targetSettingFeatures = TargetSettingFeatures(
                    speedTargetSettingSupported = true,
                    inclinationTargetSettingSupported = true
                )
            ),
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

@Preview(showBackground = true, name = "Controlling - Running")
@Composable
fun ControlScreenControllingRunningPreview() {
    TreadmillBridgeTheme {
        ControlScreen(
            uiState = TreadmillUiState(
                connectionState = ConnectionState.Connected("Treadmill X"),
                controlState = TreadmillControlState.Controlling,
                runningState = TreadmillRunningState.Running,
                targetSettingFeatures = TargetSettingFeatures(
                    speedTargetSettingSupported = true,
                    inclinationTargetSettingSupported = true
                ),
                controlTargets = ControlTargets(
                    targetSpeedKmh = 8.5f,
                    targetInclinePercent = 2.0f
                )
            ),
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

@Preview(showBackground = true, name = "Controlling - Paused")
@Composable
fun ControlScreenControllingPausedPreview() {
    TreadmillBridgeTheme {
        ControlScreen(
            uiState = TreadmillUiState(
                connectionState = ConnectionState.Connected("Treadmill X"),
                controlState = TreadmillControlState.Controlling,
                runningState = TreadmillRunningState.Paused,
                targetSettingFeatures = TargetSettingFeatures(
                    speedTargetSettingSupported = true,
                    inclinationTargetSettingSupported = true
                ),
                controlTargets = ControlTargets(
                    targetSpeedKmh = 8.5f,
                    targetInclinePercent = 2.0f
                )
            ),
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

@Preview(showBackground = true, name = "Controlling - Stopped")
@Composable
fun ControlScreenControllingStoppedPreview() {
    TreadmillBridgeTheme {
        ControlScreen(
            uiState = TreadmillUiState(
                connectionState = ConnectionState.Connected("Treadmill X"),
                controlState = TreadmillControlState.Controlling,
                runningState = TreadmillRunningState.Stopped,
                targetSettingFeatures = TargetSettingFeatures(
                    speedTargetSettingSupported = true,
                    inclinationTargetSettingSupported = true
                ),
                controlTargets = ControlTargets() // Reset to 0
            ),
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
