package com.aletchec.lokalizator

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import kotlinx.coroutines.delay
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.aletchec.lokalizator.ui.theme.LokalizatorTheme
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LokalizatorTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    TrackingScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackingScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scheduler = remember { TrackingScheduler() }
    val gpxManager = remember { GpxManager(context) }
    val ranges = remember { mutableStateListOf(TrackingScheduler.TimeRange(8, 0, 16, 0)) }
    
    var isServiceRunning by remember { mutableStateOf(value = false) }
    var showTimePicker by remember { mutableStateOf(value = false) }
    var pickingStartTime by remember { mutableStateOf(value = true) }
    var currentRangeIndex by remember { mutableIntStateOf(-1) }
    
    var selectedTab by remember { mutableIntStateOf(0) }
    var trackPoints by remember { mutableStateOf(emptyList<GpxManager.TrackPoint>()) }

    val permissionsToRequest = mutableListOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    ).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    // Periodically fetch logged points from the GPX file
    LaunchedEffect(isServiceRunning) {
        while (true) {
            val file = gpxManager.getGpxFile()
            trackPoints = gpxManager.getTrackPoints(file)
            delay(5000)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(text = "GPS Lokalizator", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(8.dp))

        Text(text = "Tracking Schedule", style = MaterialTheme.typography.titleMedium)
        
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 120.dp)
        ) {
            items(ranges) { range ->
                ScheduleItem(
                    range = range,
                    onDelete = { ranges.remove(range) },
                    onEditStart = {
                        currentRangeIndex = ranges.indexOf(range)
                        pickingStartTime = true
                        showTimePicker = true
                    }
                ) {
                    currentRangeIndex = ranges.indexOf(range)
                    pickingStartTime = false
                    showTimePicker = true
                }
            }
        }

        OutlinedButton(
            onClick = {
                ranges.add(TrackingScheduler.TimeRange(9, 0, 17, 0))
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Add Range")
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = {
                    val allGranted = permissionsToRequest.all {
                        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
                    }

                    if (allGranted) {
                        val intent = Intent(context, LocationService::class.java).apply {
                            putExtra("ranges", scheduler.serializeRanges(ranges))
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            context.startForegroundService(intent)
                        } else {
                            context.startService(intent)
                        }
                        isServiceRunning = true
                    } else {
                        launcher.launch(permissionsToRequest.toTypedArray())
                    }
                },
                modifier = Modifier.weight(1f),
                enabled = !isServiceRunning
            ) {
                Text("Start Tracking")
            }

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = {
                    context.stopService(Intent(context, LocationService::class.java))
                    isServiceRunning = false
                },
                modifier = Modifier.weight(1f),
                enabled = isServiceRunning
            ) {
                Text("Stop Tracking")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isServiceRunning) "Status: Running" else "Status: Stopped",
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.weight(1f))
            TextButton(onClick = {
                val file = gpxManager.getGpxFile()
                trackPoints = gpxManager.getTrackPoints(file)
            }) {
                Text("Refresh Data")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Bottom half tabbed views
        TabRow(selectedTabIndex = selectedTab, modifier = Modifier.fillMaxWidth()) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Map View") }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Points List (${trackPoints.size})") }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            if (selectedTab == 0) {
                TrackMap(points = trackPoints, modifier = Modifier.fillMaxSize())
            } else {
                TrackPointsList(points = trackPoints, modifier = Modifier.fillMaxSize())
            }
        }
    }

    if (showTimePicker) {
        val range = ranges[currentRangeIndex]
        val initialHour = if (pickingStartTime) range.startHour else range.endHour
        val initialMinute = if (pickingStartTime) range.startMinute else range.endMinute

        TimePickerDialog(
            initialHour = initialHour,
            initialMinute = initialMinute,
            onDismiss = { showTimePicker = false },
            onConfirm = { hour, minute ->
                val oldRange = ranges[currentRangeIndex]
                ranges[currentRangeIndex] = if (pickingStartTime) {
                    oldRange.copy(startHour = hour, startMinute = minute)
                } else {
                    oldRange.copy(endHour = hour, endMinute = minute)
                }
                showTimePicker = false
            }
        )
    }
}

@Composable
fun TrackMap(points: List<GpxManager.TrackPoint>, modifier: Modifier = Modifier) {
    if (points.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text("No GPS points recorded yet today.", style = MaterialTheme.typography.bodyMedium)
        }
        return
    }

    val minLat = points.minOf { it.latitude }
    val maxLat = points.maxOf { it.latitude }
    val minLon = points.minOf { it.longitude }
    val maxLon = points.maxOf { it.longitude }

    val latRange = maxLat - minLat
    val lonRange = maxLon - minLon

    Canvas(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
            .padding(16.dp)
    ) {
        val padding = 16.dp.toPx()
        val width = size.width - 2 * padding
        val height = size.height - 2 * padding

        if (points.size == 1 || (latRange == 0.0 && lonRange == 0.0)) {
            drawCircle(
                color = Color.Red,
                radius = 12f,
                center = androidx.compose.ui.geometry.Offset(size.width / 2, size.height / 2)
            )
        } else {
            val path = Path()
            points.forEachIndexed { index, pt ->
                val x = padding + ((pt.longitude - minLon) / lonRange * width).toFloat()
                val y = padding + (height - ((pt.latitude - minLat) / latRange * height)).toFloat()

                if (index == 0) {
                    path.moveTo(x, y)
                } else {
                    path.lineTo(x, y)
                }
            }

            drawPath(
                path = path,
                color = Color(0xFF1E88E5),
                style = Stroke(width = 6f)
            )

            // Draw start point (Green)
            val startX = padding + ((points.first().longitude - minLon) / lonRange * width).toFloat()
            val startY = padding + (height - ((points.first().latitude - minLat) / latRange * height)).toFloat()
            drawCircle(color = Color.Green, radius = 10f, center = androidx.compose.ui.geometry.Offset(startX, startY))

            // Draw end point (Red)
            val endX = padding + ((points.last().longitude - minLon) / lonRange * width).toFloat()
            val endY = padding + (height - ((points.last().latitude - minLat) / latRange * height)).toFloat()
            drawCircle(color = Color.Red, radius = 10f, center = androidx.compose.ui.geometry.Offset(endX, endY))
        }
    }
}

@Composable
fun TrackPointsList(points: List<GpxManager.TrackPoint>, modifier: Modifier = Modifier) {
    if (points.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text("No GPS points recorded yet today.", style = MaterialTheme.typography.bodyMedium)
        }
        return
    }

    LazyColumn(modifier = modifier) {
        items(points.reversed()) { pt ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                    .padding(12.dp)
            ) {
                Column {
                    Text(
                        text = "Time: ${pt.time.replace("T", " ").replace("Z", "")}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Lat: ${pt.latitude}, Lon: ${pt.longitude}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "Elevation: ${pt.elevation} m",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }
    }
}

@Composable
fun ScheduleItem(
    range: TrackingScheduler.TimeRange,
    onDelete: () -> Unit,
    onEditStart: () -> Unit,
    onEditEnd: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = onEditStart) {
            Text(String.format(Locale.US, "%02d:%02d", range.startHour, range.startMinute))
        }
        Text("-")
        TextButton(onClick = onEditEnd) {
            Text(String.format(Locale.US, "%02d:%02d", range.endHour, range.endMinute))
        }
        Spacer(modifier = Modifier.weight(1f))
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = "Delete range")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerDialog(
    initialHour: Int,
    initialMinute: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit
) {
    val state = rememberTimePickerState(initialHour, initialMinute, is24Hour = true)

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour, state.minute) }) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        text = {
            TimePicker(state = state)
        }
    )
}
