package com.aletchec.lokalizator

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
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
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.draw.clip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.aletchec.lokalizator.ui.theme.LokalizatorTheme
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // osmdroid configuration
        Configuration.getInstance().load(this, getSharedPreferences("osmdroid", MODE_PRIVATE))
        Configuration.getInstance().userAgentValue = packageName

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
    val ranges = remember { 
        mutableStateListOf<TrackingScheduler.TimeRange>().apply {
            addAll(scheduler.loadSchedules(context))
        }
    }
    
    var showTimePicker by remember { mutableStateOf(value = false) }
    var pickingStartTime by remember { mutableStateOf(value = true) }
    var currentRangeIndex by remember { mutableIntStateOf(-1) }
    
    var selectedTab by remember { mutableIntStateOf(0) }
    var isTrackingNow by remember { mutableStateOf(false) }
    var trackPoints by remember { mutableStateOf(emptyList<GpxManager.TrackPoint>()) }
    var selectedFile by remember { mutableStateOf<java.io.File?>(null) }
    var fileToExport by remember { mutableStateOf<java.io.File?>(null) }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/gpx+xml")
    ) { uri ->
        uri?.let { destinationUri ->
            fileToExport?.let { srcFile ->
                try {
                    context.contentResolver.openOutputStream(destinationUri)?.use { outputStream ->
                        srcFile.inputStream().use { inputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                    Toast.makeText(context, "Track saved successfully", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Failed to save track: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

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

    var hasRequestedPermissions by remember { mutableStateOf(false) }

    // Periodically fetch logged points and update status
    LaunchedEffect(selectedFile, ranges.size) {
        while (true) {
            val isAllowed = scheduler.isTrackingAllowed(ranges)
            isTrackingNow = isAllowed

            // Auto-start service if not running and tracking is allowed
            val allGranted = permissionsToRequest.all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
            if (isAllowed) {
                if (allGranted) {
                    val intent = Intent(context, LocationService::class.java).apply {
                        putExtra("ranges", scheduler.serializeRanges(ranges))
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(intent)
                    } else {
                        context.startService(intent)
                    }
                } else if (!hasRequestedPermissions) {
                    launcher.launch(permissionsToRequest.toTypedArray())
                    hasRequestedPermissions = true
                }
            }

            val points = withContext(Dispatchers.IO) {
                val file = selectedFile ?: run {
                    val activeRange = scheduler.getActiveRange(ranges)
                    gpxManager.getGpxFile(activeRange)
                }
                gpxManager.getTrackPoints(file)
            }
            trackPoints = points
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
                    onDelete = { 
                        ranges.remove(range)
                        scheduler.saveSchedules(context, ranges)
                    },
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
                scheduler.saveSchedules(context, ranges)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Add Range")
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Tracking Status Indicator
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(
                    if (isTrackingNow) Color(0xFFE8F5E9) else Color(0xFFF5F5F5)
                )
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isTrackingNow) "● TRACKING ACTIVE" else "○ OUTSIDE SCHEDULE",
                color = if (isTrackingNow) Color(0xFF2E7D32) else Color(0xFF757575),
                style = MaterialTheme.typography.titleMedium
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = if (selectedFile != null) {
                        "Viewing: ${selectedFile?.name?.substringAfter("track_")?.substringBefore(".gpx") ?: ""}"
                    } else {
                        val activeRange = scheduler.getActiveRange(ranges)
                        if (activeRange != null) {
                            String.format(Locale.US, "Active Range: %02d:%02d-%02d:%02d", activeRange.startHour, activeRange.startMinute, activeRange.endHour, activeRange.endMinute)
                        } else {
                            "No active schedule"
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            if (selectedFile != null) {
                TextButton(onClick = { selectedFile = null }) {
                    Text("Show Active")
                }
            }
            TextButton(onClick = {
                val file = selectedFile ?: run {
                    val activeRange = scheduler.getActiveRange(scheduler.loadSchedules(context))
                    gpxManager.getGpxFile(activeRange)
                }
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
            Tab(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                text = { Text("Saved Tracks") }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            when (selectedTab) {
                0 -> TrackMap(points = trackPoints, modifier = Modifier.fillMaxSize())
                1 -> TrackPointsList(points = trackPoints, modifier = Modifier.fillMaxSize())
                2 -> SavedTracksList(
                    gpxManager = gpxManager,
                    scheduler = scheduler,
                    onSelectFile = { file ->
                        selectedFile = file
                        selectedTab = 0
                    },
                    onDeleteFile = { file ->
                        if (selectedFile == file) {
                            selectedFile = null
                        }
                    },
                    onExportFile = { file ->
                        fileToExport = file
                        createDocumentLauncher.launch(file.name)
                    },
                    modifier = Modifier.fillMaxSize()
                )
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
                scheduler.saveSchedules(context, ranges)
                showTimePicker = false
            }
        )
    }

    // No longer need showStopTrackingConfirmation dialog
}

@Composable
fun SavedTracksList(
    gpxManager: GpxManager,
    scheduler: TrackingScheduler,
    onSelectFile: (java.io.File) -> Unit,
    onDeleteFile: (java.io.File) -> Unit,
    onExportFile: (java.io.File) -> Unit,
    modifier: Modifier = Modifier
) {
    var files by remember { mutableStateOf(emptyList<java.io.File>()) }
    var fileToDelete by remember { mutableStateOf<java.io.File?>(null) }
    val context = LocalContext.current
    val ranges = remember { scheduler.loadSchedules(context) }
    
    LaunchedEffect(fileToDelete == null) {
        files = gpxManager.getAllGpxFiles()
    }

    if (files.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text("No saved tracks found.", style = MaterialTheme.typography.bodyMedium)
        }
        return
    }

    LazyColumn(modifier = modifier) {
        items(files) { file ->
            val activeRange = scheduler.getActiveRange(ranges)
            val isActiveFile = file.absolutePath == gpxManager.getGpxFile(activeRange).absolutePath
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .background(
                        if (isActiveFile) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        else MaterialTheme.colorScheme.surfaceVariant, 
                        RoundedCornerShape(8.dp)
                    )
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    val name = file.name.replace("track_", "").replace(".gpx", "")
                    val formattedName = try {
                        val parts = name.split("_")
                        if (parts.size == 3) {
                            val dateStr = parts[0] // YYYYMMDD
                            val startStr = parts[1] // HHMM
                            val endStr = parts[2] // HHMM
                            "${dateStr.substring(6, 8)}.${dateStr.substring(4, 6)}.${dateStr.substring(0, 4)} (${startStr.substring(0, 2)}:${startStr.substring(2, 4)} - ${endStr.substring(0, 2)}:${endStr.substring(2, 4)})"
                        } else if (parts.size == 1 && parts[0].length == 8) {
                            val dateStr = parts[0]
                            "${dateStr.substring(6, 8)}.${dateStr.substring(4, 6)}.${dateStr.substring(0, 4)}"
                        } else {
                            file.name
                        }
                    } catch (e: Exception) {
                        file.name
                    }
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = formattedName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isActiveFile) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (isActiveFile) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "ACTIVE",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Size: ${String.format(Locale.US, "%.2f", file.length() / 1024.0)} KB",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                
                Button(onClick = {
                    onSelectFile(file)
                }) {
                    Text("Load")
                }
                
                Spacer(modifier = Modifier.width(4.dp))

                IconButton(onClick = {
                    onExportFile(file)
                }) {
                    Icon(Icons.Default.SaveAlt, contentDescription = "Export track file")
                }
                
                Spacer(modifier = Modifier.width(4.dp))
                
                IconButton(onClick = {
                    fileToDelete = file
                }) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete track file")
                }
            }
        }
    }

    if (fileToDelete != null) {
        val activeRange = scheduler.getActiveRange(ranges)
        val isActiveFile = fileToDelete?.absolutePath == gpxManager.getGpxFile(activeRange).absolutePath

        AlertDialog(
            onDismissRequest = { fileToDelete = null },
            title = { Text("Delete Track?") },
            text = { 
                if (isActiveFile) {
                    Text("This is the track currently being recorded (if active). Deleting it will stop logging for the current schedule until a new point is recorded. Are you sure?")
                } else {
                    Text("Are you sure you want to permanently delete this track file?")
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val file = fileToDelete!!
                        file.delete()
                        onDeleteFile(file)
                        fileToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { fileToDelete = null }) {
                    Text("Cancel")
                }
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

    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("tracking_prefs", android.content.Context.MODE_PRIVATE) }
    var hasCenteredInitially by remember { mutableStateOf(false) }

    AndroidView(
        factory = { ctx ->
            MapView(ctx).apply {
                clipToOutline = true
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.ALWAYS)

                val savedZoom = sharedPrefs.getFloat("map_zoom_level", 16.0f).toDouble()
                controller.setZoom(savedZoom)

                addMapListener(object : MapListener {
                    override fun onScroll(event: ScrollEvent?): Boolean = false
                    override fun onZoom(event: ZoomEvent?): Boolean {
                        val currentZoom = zoomLevelDouble.toFloat()
                        sharedPrefs.edit { putFloat("map_zoom_level", currentZoom) }
                        return false
                    }
                })
            }
        },
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)),
        update = { map ->
            map.overlays.clear()

            if (points.isNotEmpty()) {
                val geoPoints = points.map { GeoPoint(it.latitude, it.longitude) }
                
                // Track line
                val polyline = Polyline().apply {
                    setPoints(geoPoints)
                    outlinePaint.color = android.graphics.Color.BLUE
                    outlinePaint.strokeWidth = 5f
                }
                map.overlays.add(polyline)

                // Start marker
                val startMarker = Marker(map).apply {
                    position = geoPoints.first()
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    title = "Start"
                    icon = ContextCompat.getDrawable(context, org.osmdroid.library.R.drawable.marker_default)?.mutate()?.apply {
                        setTint(android.graphics.Color.GREEN)
                    }
                }
                map.overlays.add(startMarker)

                // End marker
                val endMarker = Marker(map).apply {
                    position = geoPoints.last()
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    title = "Latest Position"
                    icon = ContextCompat.getDrawable(context, org.osmdroid.library.R.drawable.marker_default)?.mutate()?.apply {
                        setTint(android.graphics.Color.RED)
                    }
                }
                map.overlays.add(endMarker)

                if (!hasCenteredInitially) {
                    hasCenteredInitially = true
                    map.post {
                        map.controller.setCenter(geoPoints.last())
                    }
                }
            }
            map.invalidate()
        }
    )
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
