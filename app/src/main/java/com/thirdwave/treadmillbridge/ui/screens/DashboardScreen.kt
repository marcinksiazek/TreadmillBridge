package com.thirdwave.treadmillbridge.ui.screens

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.thirdwave.treadmillbridge.ui.state.TreadmillUiState
import com.thirdwave.treadmillbridge.ui.theme.TreadmillBridgeTheme
import com.thirdwave.treadmillbridge.data.model.TreadmillMetrics
import java.util.Locale

@Composable
fun DashboardScreen(
    uiState: TreadmillUiState,
    modifier: Modifier = Modifier
) {
    // Determine columns based on orientation: portrait -> 2, landscape -> 4
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val columns = if (isLandscape) 4 else 2

    // Prepare metrics data as a list for the grid
    val metrics = listOf(
        MetricItem("Speed", String.format(Locale.getDefault(), "%.1f", uiState.metrics.speedKph), "km/h"),
        MetricItem("Pace", uiState.metrics.paceString ?: "--", "min/km"),
        MetricItem("Incline", String.format(Locale.getDefault(), "%.1f", uiState.metrics.inclinePercent), "%"),
        MetricItem("Cadence", uiState.metrics.cadence.toString(), "spm"),
        MetricItem("Heart Rate", if (uiState.hrMetrics.heartRateBpm > 0) uiState.hrMetrics.heartRateBpm.toString() else "--", "bpm")
    )

    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = modifier
            .fillMaxSize()
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = {
            items(metrics) { item ->
                MetricCard(
                    name = item.name,
                    value = item.value,
                    unit = item.unit,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    )
}

private data class MetricItem(val name: String, val value: String, val unit: String)

@Composable
private fun MetricCard(name: String, value: String, unit: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors()
    ) {
        Column(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Value and unit aligned
            Column(horizontalAlignment = Alignment.Start) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = unit,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Preview(name = "Dashboard - Landscape", showBackground = true, widthDp = 800, heightDp = 360)
@Composable
fun DashboardScreenPreview_Landscape() {
    val mockState = TreadmillUiState(
        metrics = TreadmillMetrics(speedKph = 15.2f, inclinePercent = 0.0f, cadence = 160),
        hrMetrics = com.thirdwave.treadmillbridge.data.model.HrMonitorMetrics(heartRateBpm = 145),
        permissionsGranted = true
    )

    TreadmillBridgeTheme {
        DashboardScreen(uiState = mockState, modifier = Modifier.fillMaxSize())
    }
}

@Preview(name = "Dashboard - Portrait", showBackground = true, widthDp = 360, heightDp = 800)
@Composable
fun DashboardScreenPreview_Portrait() {
    val mockState = TreadmillUiState(
        metrics = TreadmillMetrics(speedKph = 9.8f, inclinePercent = 1.5f, cadence = 140),
        hrMetrics = com.thirdwave.treadmillbridge.data.model.HrMonitorMetrics(heartRateBpm = 132),
        permissionsGranted = true
    )

    TreadmillBridgeTheme {
        DashboardScreen(uiState = mockState, modifier = Modifier.fillMaxSize())
    }
}


