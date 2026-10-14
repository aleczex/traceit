package com.aletchec.traceit

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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Settings
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.aletchec.traceit.ui.theme.TraceItTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline
import java.util.Calendar
import java.util.Locale

enum class PickTarget { START, END }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // osmdroid configuration
        Configuration.getInstance().load(this, getSharedPreferences("osmdroid", MODE_PRIVATE))
        Configuration.getInstance().userAgentValue = packageName

        enableEdgeToEdge()
        setContent {
            TraceItTheme {
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

    val scope = rememberCoroutineScope()
    val scaffoldState = rememberBottomSheetScaffoldState(
        bottomSheetState = rememberStandardBottomSheetState(
            initialValue = SheetValue.PartiallyExpanded
        )
    )

    var showTimePicker by remember { mutableStateOf(false) }
    var pickingStartTime by remember { mutableStateOf(true) }
    var currentRangeIndex by remember { mutableIntStateOf(-1) }

    var pickingScheduleIndex by remember { mutableIntStateOf(-1) }
    var pickTarget by remember { mutableStateOf(PickTarget.START) }

    // Schedule Settings Dialog State
    var showSettingsDialog by remember { mutableStateOf(false) }
    var editingSettingsScheduleIndex by remember { mutableIntStateOf(-1) }

    var showDaysDialog by remember { mutableStateOf(false) }
    var editingDaysScheduleIndex by remember { mutableIntStateOf(-1) }

    var showIntervalDialog by remember { mutableStateOf(false) }
    var editingIntervalScheduleIndex by remember { mutableIntStateOf(-1) }

    var showRadiusDialog by remember { mutableStateOf(false) }
    var editingRadiusScheduleIndex by remember { mutableIntStateOf(-1) }
    var editingRadiusTarget by remember { mutableStateOf(PickTarget.START) }

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

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetPeekHeight = 180.dp,
        sheetContent = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.85f)
                    .padding(horizontal = 16.dp)
            ) {
                Text(
                    text = "TraceIt",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (isTrackingNow) Color(0xFFE8F5E9) else Color(0xFFF5F5F5)
                        )
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isTrackingNow) "● TRACKING ACTIVE" else "○ OUTSIDE SCHEDULE",
                        color = if (isTrackingNow) Color(0xFF2E7D32) else Color(0xFF757575),
                        style = MaterialTheme.typography.titleSmall
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                TabRow(selectedTabIndex = selectedTab, modifier = Modifier.fillMaxWidth()) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = {
                            selectedTab = 0
                        },
                        text = { Text("Schedules") }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = {
                            selectedTab = 1
                            scope.launch { scaffoldState.bottomSheetState.expand() }
                        },
                        text = { Text("Points") }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = {
                            selectedTab = 2
                            scope.launch { scaffoldState.bottomSheetState.expand() }
                        },
                        text = { Text("Saved") }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    when (selectedTab) {
                        0 -> {
                            Column(modifier = Modifier.fillMaxSize()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(text = "Tracking Schedule", style = MaterialTheme.typography.titleMedium)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    IconButton(onClick = {
                                        ranges.add(TrackingScheduler.TimeRange(9, 0, 17, 0))
                                        scheduler.saveSchedules(context, ranges)
                                    }) {
                                        Icon(Icons.Default.Add, contentDescription = "Add Schedule")
                                    }
                                    Spacer(modifier = Modifier.weight(1f))
                                    TextButton(onClick = {
                                        val file = selectedFile ?: run {
                                            val activeRange = scheduler.getActiveRange(scheduler.loadSchedules(context))
                                            gpxManager.getGpxFile(activeRange)
                                        }
                                        trackPoints = gpxManager.getTrackPoints(file)
                                    }) {
                                        Text("Refresh")
                                    }
                                }

                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                ) {
                                    itemsIndexed(ranges) { index, range ->
                                        ScheduleItem(
                                            range = range,
                                            isPickingStart = pickingScheduleIndex == index && pickTarget == PickTarget.START,
                                            isPickingEnd = pickingScheduleIndex == index && pickTarget == PickTarget.END,
                                            onOpenSettings = {
                                                editingSettingsScheduleIndex = index
                                                showSettingsDialog = true
                                            },
                                            onTogglePickStart = {
                                                if (pickingScheduleIndex == index && pickTarget == PickTarget.START) {
                                                    pickingScheduleIndex = -1
                                                } else {
                                                    pickingScheduleIndex = index
                                                    pickTarget = PickTarget.START
                                                    scope.launch { scaffoldState.bottomSheetState.partialExpand() }
                                                }
                                            },
                                            onTogglePickEnd = {
                                                if (pickingScheduleIndex == index && pickTarget == PickTarget.END) {
                                                    pickingScheduleIndex = -1
                                                } else {
                                                    pickingScheduleIndex = index
                                                    pickTarget = PickTarget.END
                                                    scope.launch { scaffoldState.bottomSheetState.partialExpand() }
                                                }
                                            }
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    text = if (selectedFile != null) {
                                        "Viewing: ${selectedFile?.name?.replace("track_", "")?.replace(".gpx", "")}"
                                    } else {
                                        val activeRange = scheduler.getActiveRange(ranges)
                                        if (activeRange != null) {
                                            String.format(Locale.US, "Active: %02d:%02d-%02d:%02d", activeRange.startHour, activeRange.startMinute, activeRange.endHour, activeRange.endMinute)
                                        } else {
                                            "No active schedule"
                                        }
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                if (selectedFile != null) {
                                    TextButton(onClick = { selectedFile = null }) {
                                        Text("Show Current Active Track")
                                    }
                                }
                            }
                        }
                        1 -> TrackPointsList(points = trackPoints, modifier = Modifier.fillMaxSize())
                        2 -> SavedTracksList(
                            gpxManager = gpxManager,
                            scheduler = scheduler,
                            selectedFile = selectedFile,
                            onSelectFile = { file ->
                                selectedFile = file
                                scope.launch { scaffoldState.bottomSheetState.partialExpand() }
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
                Spacer(modifier = Modifier.height(32.dp))
            }
        },
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            var mapViewInstance by remember { mutableStateOf<MapView?>(null) }
            val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

            TrackMap(
                points = trackPoints,
                ranges = ranges,
                pickingScheduleIndex = pickingScheduleIndex,
                onPointSelectedOnMap = { point ->
                    if (pickingScheduleIndex in ranges.indices) {
                        val currentRange = ranges[pickingScheduleIndex]
                        if (pickTarget == PickTarget.START) {
                            if (currentRange.endLat != null && currentRange.endLon != null && currentRange.endRadiusMeters > 0) {
                                val results = FloatArray(1)
                                android.location.Location.distanceBetween(
                                    point.latitude, point.longitude,
                                    currentRange.endLat, currentRange.endLon,
                                    results
                                )
                                if (results[0] <= currentRange.endRadiusMeters) {
                                    Toast.makeText(context, "Start point cannot be within end point range", Toast.LENGTH_SHORT).show()
                                    pickingScheduleIndex = -1
                                    return@TrackMap
                                }
                            }
                            val defaultRadius = if (currentRange.startRadiusMeters > 0) currentRange.startRadiusMeters else 50
                            ranges[pickingScheduleIndex] = currentRange.copy(
                                startLat = point.latitude,
                                startLon = point.longitude,
                                startRadiusMeters = defaultRadius
                            )
                            scheduler.saveSchedules(context, ranges)
                            Toast.makeText(context, "Start point set for schedule!", Toast.LENGTH_SHORT).show()
                        } else {
                            if (currentRange.startLat != null && currentRange.startLon != null && currentRange.startRadiusMeters > 0) {
                                val results = FloatArray(1)
                                android.location.Location.distanceBetween(
                                    point.latitude, point.longitude,
                                    currentRange.startLat, currentRange.startLon,
                                    results
                                )
                                if (results[0] <= currentRange.startRadiusMeters) {
                                    Toast.makeText(context, "End point cannot be within start point range", Toast.LENGTH_SHORT).show()
                                    pickingScheduleIndex = -1
                                    return@TrackMap
                                }
                            }
                            val defaultRadius = if (currentRange.endRadiusMeters > 0) currentRange.endRadiusMeters else 50
                            ranges[pickingScheduleIndex] = currentRange.copy(
                                endLat = point.latitude,
                                endLon = point.longitude,
                                endRadiusMeters = defaultRadius
                            )
                            scheduler.saveSchedules(context, ranges)
                            Toast.makeText(context, "End point set for schedule!", Toast.LENGTH_SHORT).show()
                        }
                    }
                    pickingScheduleIndex = -1
                },
                onMapReady = { map ->
                    mapViewInstance = map
                },
                modifier = Modifier.fillMaxSize()
            )

            // Map controls row (Zoom In, Zoom Out, Crosshair) in bottom right of map
            val locationLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { permissions ->
                val allGranted = permissions.values.all { it }
                if (allGranted && mapViewInstance != null) {
                    fetchAndCenterLocation(context, fusedLocationClient, mapViewInstance!!)
                } else {
                    Toast.makeText(context, "Location permission required to show current position", Toast.LENGTH_SHORT).show()
                }
            }

            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 100.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Zoom In (+)
                FloatingActionButton(
                    onClick = {
                        mapViewInstance?.controller?.zoomIn()
                    },
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Zoom In")
                }

                // Zoom Out (-)
                FloatingActionButton(
                    onClick = {
                        mapViewInstance?.controller?.zoomOut()
                    },
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(Icons.Default.Remove, contentDescription = "Zoom Out")
                }

                // Crosshair (My Location)
                FloatingActionButton(
                    onClick = {
                        val allGranted = permissionsToRequest.all {
                            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
                        }
                        if (allGranted) {
                            if (mapViewInstance != null) {
                                fetchAndCenterLocation(context, fusedLocationClient, mapViewInstance!!)
                            }
                        } else {
                            locationLauncher.launch(permissionsToRequest.toTypedArray())
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(Icons.Default.MyLocation, contentDescription = "Show Current GPS Location")
                }
            }

            val validTrackPoints = remember(trackPoints) { trackPoints.filter { !it.isIgnored } }
            if (validTrackPoints.isNotEmpty()) {
                val currentFile = remember(selectedFile, ranges) {
                    selectedFile ?: gpxManager.getGpxFile(scheduler.getActiveRange(ranges))
                }
                val stats = remember(currentFile, trackPoints) {
                    gpxManager.getTrackStatistics(currentFile)
                }
                val startTimeStr = validTrackPoints.firstOrNull()?.time?.replace("T", " ")?.replace("Z", "") ?: "-"
                val endTimeStr = validTrackPoints.lastOrNull()?.time?.replace("T", " ")?.replace("Z", "") ?: "-"
                val distanceStr = gpxManager.formatDistance(stats.distanceMeters)
                val durationStr = gpxManager.formatDuration(stats.durationMillis)

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
                        .padding(12.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (selectedFile != null) "Loaded: ${selectedFile?.name?.replace("track_", "")?.replace(".gpx", "")}" else "Active Track",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f)
                            )
                            if (selectedFile != null) {
                                TextButton(onClick = { selectedFile = null }) {
                                    Text("Show Current")
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween
                        ) {
                            Text(text = "Distance: $distanceStr", style = MaterialTheme.typography.bodySmall)
                            Text(text = "Total Time: $durationStr", style = MaterialTheme.typography.bodySmall)
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween
                        ) {
                            Text(text = "Start: $startTimeStr", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                            Text(text = "End: $endTimeStr", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                        }
                    }
                }
            }

            if (pickingScheduleIndex != -1) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Tap map to set ${if (pickTarget == PickTarget.START) "START" else "END"} point for Schedule #${pickingScheduleIndex + 1}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { pickingScheduleIndex = -1 }) {
                            Text("Cancel")
                        }
                    }
                }
            }
        }
    }

    // Schedule Settings Dialog
    if (showSettingsDialog && editingSettingsScheduleIndex in ranges.indices) {
        val range = ranges[editingSettingsScheduleIndex]
        ScheduleSettingsDialog(
            range = range,
            onDismiss = { showSettingsDialog = false },
            onUpdate = { updatedRange ->
                ranges[editingSettingsScheduleIndex] = updatedRange
                scheduler.saveSchedules(context, ranges)
            },
            onDelete = {
                ranges.removeAt(editingSettingsScheduleIndex)
                if (pickingScheduleIndex == editingSettingsScheduleIndex) pickingScheduleIndex = -1
                scheduler.saveSchedules(context, ranges)
                showSettingsDialog = false
            },
            onPickStart = {
                editingSettingsScheduleIndex = -1
                showSettingsDialog = false
                pickingScheduleIndex = editingSettingsScheduleIndex
                pickTarget = PickTarget.START
                scope.launch { scaffoldState.bottomSheetState.partialExpand() }
            },
            onPickEnd = {
                editingSettingsScheduleIndex = -1
                showSettingsDialog = false
                pickingScheduleIndex = editingSettingsScheduleIndex
                pickTarget = PickTarget.END
                scope.launch { scaffoldState.bottomSheetState.partialExpand() }
            },
            onEditStart = {
                currentRangeIndex = editingSettingsScheduleIndex
                pickingStartTime = true
                showTimePicker = true
            },
            onEditEnd = {
                currentRangeIndex = editingSettingsScheduleIndex
                pickingStartTime = false
                showTimePicker = true
            },
            onEditDays = {
                editingDaysScheduleIndex = editingSettingsScheduleIndex
                showDaysDialog = true
            },
            onEditInterval = {
                editingIntervalScheduleIndex = editingSettingsScheduleIndex
                showIntervalDialog = true
            },
            onSetStartRadius = {
                editingRadiusScheduleIndex = editingSettingsScheduleIndex
                editingRadiusTarget = PickTarget.START
                showRadiusDialog = true
            },
            onSetEndRadius = {
                editingRadiusScheduleIndex = editingSettingsScheduleIndex
                editingRadiusTarget = PickTarget.END
                showRadiusDialog = true
            },
            onClearStart = {
                ranges[editingSettingsScheduleIndex] = range.copy(startLat = null, startLon = null, startRadiusMeters = 0)
                scheduler.saveSchedules(context, ranges)
            },
            onClearEnd = {
                ranges[editingSettingsScheduleIndex] = range.copy(endLat = null, endLon = null, endRadiusMeters = 0)
                scheduler.saveSchedules(context, ranges)
            }
        )
    }

    if (showTimePicker && currentRangeIndex in ranges.indices) {
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

    if (showDaysDialog && editingDaysScheduleIndex in ranges.indices) {
        val currentRange = ranges[editingDaysScheduleIndex]
        var selectedDays by remember { mutableStateOf(currentRange.daysOfWeek) }

        AlertDialog(
            onDismissRequest = { showDaysDialog = false },
            title = { Text("Select Days of Week") },
            text = {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween
                    ) {
                        OutlinedButton(onClick = {
                            selectedDays = setOf(Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY)
                        }) {
                            Text("Mo-Su")
                        }
                        OutlinedButton(onClick = {
                            selectedDays = setOf(Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY)
                        }) {
                            Text("Mo-Fri")
                        }
                        OutlinedButton(onClick = {
                            selectedDays = setOf(Calendar.SUNDAY, Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY)
                        }) {
                            Text("Su-Sa")
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    val dayEntries = listOf(
                        Calendar.MONDAY to "Monday",
                        Calendar.TUESDAY to "Tuesday",
                        Calendar.WEDNESDAY to "Wednesday",
                        Calendar.THURSDAY to "Thursday",
                        Calendar.FRIDAY to "Friday",
                        Calendar.SATURDAY to "Saturday",
                        Calendar.SUNDAY to "Sunday"
                    )

                    dayEntries.forEach { (dayConstant, dayName) ->
                        val isSelected = selectedDays.contains(dayConstant)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                    else Color.Transparent,
                                    RoundedCornerShape(4.dp)
                                )
                                .padding(8.dp)
                                .clickable {
                                    selectedDays = if (isSelected) {
                                        if (selectedDays.size > 1) selectedDays - dayConstant else selectedDays
                                    } else {
                                        selectedDays + dayConstant
                                    }
                                },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = dayName,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            if (isSelected) {
                                Text("✓", color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    ranges[editingDaysScheduleIndex] = currentRange.copy(daysOfWeek = selectedDays)
                    scheduler.saveSchedules(context, ranges)
                    showDaysDialog = false
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDaysDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showIntervalDialog && editingIntervalScheduleIndex in ranges.indices) {
        val currentRange = ranges[editingIntervalScheduleIndex]
        var intervalInput by remember { mutableStateOf(currentRange.gpsIntervalSeconds.toString()) }

        AlertDialog(
            onDismissRequest = { showIntervalDialog = false },
            title = { Text("Check Interval") },
            text = {
                Column {
                    Text("Enter how often GPS position should be checked for this schedule (in seconds):")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = intervalInput,
                        onValueChange = { intervalInput = it },
                        label = { Text("Seconds") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val sec = intervalInput.toIntOrNull()
                    if (sec != null && sec > 0) {
                        ranges[editingIntervalScheduleIndex] = currentRange.copy(gpsIntervalSeconds = sec)
                        scheduler.saveSchedules(context, ranges)
                        showIntervalDialog = false
                    } else {
                        Toast.makeText(context, "Please enter a valid positive number", Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showIntervalDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showRadiusDialog && editingRadiusScheduleIndex in ranges.indices) {
        val currentRange = ranges[editingRadiusScheduleIndex]
        val initialRadius = if (editingRadiusTarget == PickTarget.START) currentRange.startRadiusMeters else currentRange.endRadiusMeters
        var radiusInput by remember { mutableStateOf(if (initialRadius > 0) initialRadius.toString() else "50") }

        AlertDialog(
            onDismissRequest = { showRadiusDialog = false },
            title = { Text("${if (editingRadiusTarget == PickTarget.START) "Start" else "End"} Radius") },
            text = {
                Column {
                    Text("Enter ${if (editingRadiusTarget == PickTarget.START) "departure" else "arrival"} radius in meters (0 to disable):")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = radiusInput,
                        onValueChange = { radiusInput = it },
                        label = { Text("Radius (meters)") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val radius = radiusInput.toIntOrNull()
                    if (radius != null && radius >= 0) {
                        ranges[editingRadiusScheduleIndex] = if (editingRadiusTarget == PickTarget.START) {
                            currentRange.copy(startRadiusMeters = radius)
                        } else {
                            currentRange.copy(endRadiusMeters = radius)
                        }
                        scheduler.saveSchedules(context, ranges)
                        showRadiusDialog = false
                    } else {
                        Toast.makeText(context, "Please enter a valid number (0 or greater)", Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRadiusDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

fun formatDays(days: Set<Int>): String {
    val allDays = setOf(Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY)
    val workDays = setOf(Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY)
    val suSaDays = setOf(Calendar.SUNDAY, Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY)

    return when {
        days == allDays || days == suSaDays -> "Mo-Su"
        days == workDays -> "Mo-Fri"
        days.size == 7 -> "Su-Sa"
        else -> {
            val dayNames = mapOf(
                Calendar.MONDAY to "Mon",
                Calendar.TUESDAY to "Tue",
                Calendar.WEDNESDAY to "Wed",
                Calendar.THURSDAY to "Thu",
                Calendar.FRIDAY to "Fri",
                Calendar.SATURDAY to "Sat",
                Calendar.SUNDAY to "Sun"
            )
            val sortedDays = listOf(Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY)
            sortedDays.filter { days.contains(it) }.mapNotNull { dayNames[it] }.joinToString(", ")
        }
    }
}

@Composable
fun ScheduleItem(
    range: TrackingScheduler.TimeRange,
    isPickingStart: Boolean,
    isPickingEnd: Boolean,
    onOpenSettings: () -> Unit,
    onTogglePickStart: () -> Unit,
    onTogglePickEnd: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(
                if (isPickingStart || isPickingEnd) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                else MaterialTheme.colorScheme.surfaceVariant,
                RoundedCornerShape(8.dp)
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = range.name.ifBlank { "Schedule" },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = String.format(Locale.US, "%02d:%02d - %02d:%02d", range.startHour, range.startMinute, range.endHour, range.endMinute),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = formatDays(range.daysOfWeek),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${range.gpsIntervalSeconds}s interval",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val hasStart = range.startLat != null && range.startLon != null
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = "Start Point",
                    tint = if (hasStart) Color(0xFF2E7D32) else Color.Gray,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(2.dp))
                Text(
                    text = if (hasStart) "Start (${range.startRadiusMeters}m)" else "Start: -",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (hasStart) Color(0xFF2E7D32) else Color.Gray
                )
                Spacer(modifier = Modifier.width(12.dp))
                val hasEnd = range.endLat != null && range.endLon != null
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = "End Point",
                    tint = if (hasEnd) Color(0xFFC62828) else Color.Gray,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(2.dp))
                Text(
                    text = if (hasEnd) "End (${range.endRadiusMeters}m)" else "End: -",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (hasEnd) Color(0xFFC62828) else Color.Gray
                )
            }
        }

        IconButton(onClick = onOpenSettings) {
            Icon(Icons.Default.Settings, contentDescription = "Schedule Settings")
        }
    }
}

@Composable
fun ScheduleSettingsDialog(
    range: TrackingScheduler.TimeRange,
    onDismiss: () -> Unit,
    onUpdate: (TrackingScheduler.TimeRange) -> Unit,
    onDelete: () -> Unit,
    onPickStart: () -> Unit,
    onPickEnd: () -> Unit,
    onEditStart: () -> Unit,
    onEditEnd: () -> Unit,
    onEditDays: () -> Unit,
    onEditInterval: () -> Unit,
    onSetStartRadius: () -> Unit,
    onSetEndRadius: () -> Unit,
    onClearStart: () -> Unit,
    onClearEnd: () -> Unit
) {
    var nameInput by remember { mutableStateOf(range.name) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Schedule Settings") },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 450.dp)
            ) {
                item {
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = {
                            nameInput = it
                            onUpdate(range.copy(name = it))
                        },
                        label = { Text("Schedule Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Text("Time & Duration", style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(onClick = onEditStart, modifier = Modifier.weight(1f)) {
                            Text("Start: ${String.format(Locale.US, "%02d:%02d", range.startHour, range.startMinute)}")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedButton(onClick = onEditEnd, modifier = Modifier.weight(1f)) {
                            Text("End: ${String.format(Locale.US, "%02d:%02d", range.endHour, range.endMinute)}")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(onClick = onEditDays, modifier = Modifier.weight(1f)) {
                            Text("Days: ${formatDays(range.daysOfWeek)}")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedButton(onClick = onEditInterval, modifier = Modifier.weight(1f)) {
                            Text("Interval: ${range.gpsIntervalSeconds}s")
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Start Point", style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val startText = if (range.startLat != null && range.startLon != null) {
                            String.format(Locale.US, "%.4f, %.4f (%dm)", range.startLat, range.startLon, range.startRadiusMeters)
                        } else {
                            "Not set"
                        }
                        Text(
                            text = startText,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onPickStart) {
                            Text("Pick")
                        }
                        if (range.startLat != null && range.startLon != null) {
                            TextButton(onClick = onSetStartRadius) {
                                Text("Radius")
                            }
                            IconButton(onClick = onClearStart) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear Start", modifier = Modifier.size(20.dp))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text("End Point", style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val endText = if (range.endLat != null && range.endLon != null) {
                            String.format(Locale.US, "%.4f, %.4f (%dm)", range.endLat, range.endLon, range.endRadiusMeters)
                        } else {
                            "Not set"
                        }
                        Text(
                            text = endText,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onPickEnd) {
                            Text("Pick")
                        }
                        if (range.endLat != null && range.endLon != null) {
                            TextButton(onClick = onSetEndRadius) {
                                Text("Radius")
                            }
                            IconButton(onClick = onClearEnd) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear End", modifier = Modifier.size(20.dp))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = onDelete,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Delete Schedule")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
fun SavedTracksList(
    gpxManager: GpxManager,
    scheduler: TrackingScheduler,
    selectedFile: java.io.File?,
    onSelectFile: (java.io.File) -> Unit,
    onDeleteFile: (java.io.File) -> Unit,
    onExportFile: (java.io.File) -> Unit,
    modifier: Modifier = Modifier
) {
    var files by remember { mutableStateOf(emptyList<java.io.File>()) }
    var fileToDelete by remember { mutableStateOf<java.io.File?>(null) }
    val context = LocalContext.current
    val ranges = remember { scheduler.loadSchedules(context) }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()

    LaunchedEffect(fileToDelete == null) {
        files = gpxManager.getAllGpxFiles()
    }

    LaunchedEffect(selectedFile, files) {
        if (selectedFile != null) {
            val index = files.indexOfFirst { it.absolutePath == selectedFile.absolutePath }
            if (index != -1) {
                listState.animateScrollToItem(index)
            }
        }
    }

    if (files.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text("No saved tracks found.", style = MaterialTheme.typography.bodyMedium)
        }
        return
    }

    LazyColumn(state = listState, modifier = modifier) {
        items(files) { file ->
            val activeRange = scheduler.getActiveRange(ranges)
            val activeFile = gpxManager.getGpxFile(activeRange)
            val isLoaded = file == selectedFile
            val isActiveFile = file.absolutePath == activeFile.absolutePath

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .background(
                        when {
                            isLoaded -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                            isActiveFile -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        },
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
                            val dateStr = parts[0]
                            val startStr = parts[1]
                            val endStr = parts[2]
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
                            color = if (isLoaded || isActiveFile) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
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
                    val stats = remember(file) { gpxManager.getTrackStatistics(file) }
                    Text(
                        text = "Duration: ${gpxManager.formatDuration(stats.durationMillis)} | Distance: ${gpxManager.formatDistance(stats.distanceMeters)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
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

fun createNumberedMarker(
    context: android.content.Context,
    map: MapView,
    position: GeoPoint,
    title: String,
    tintColor: Int,
    label: String
): Marker {
    val size = 120
    val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)

    val baseDrawable = ContextCompat.getDrawable(context, org.osmdroid.library.R.drawable.marker_default)?.mutate()?.apply {
        setTint(tintColor)
    }
    baseDrawable?.setBounds(0, 0, size, size)
    baseDrawable?.draw(canvas)

    val paint = android.graphics.Paint().apply {
        color = android.graphics.Color.WHITE
        textSize = when {
            label.length <= 3 -> 36f
            label.length <= 6 -> 28f
            label.length <= 10 -> 20f
            else -> 16f
        }
        isAntiAlias = true
        textAlign = android.graphics.Paint.Align.CENTER
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    canvas.drawText(label, size / 2f, size / 2.5f, paint)

    val drawable = android.graphics.drawable.BitmapDrawable(context.resources, bitmap)

    return Marker(map).apply {
        this.position = position
        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        this.title = title
        icon = drawable
    }
}

@Composable
fun TrackMap(
    points: List<GpxManager.TrackPoint>,
    ranges: List<TrackingScheduler.TimeRange>,
    pickingScheduleIndex: Int,
    onPointSelectedOnMap: (GeoPoint) -> Unit,
    onMapReady: (MapView) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("tracking_prefs", android.content.Context.MODE_PRIVATE) }
    var hasCenteredInitially by remember { mutableStateOf(false) }

    AndroidView(
        factory = { ctx ->
            MapView(ctx).apply {
                clipToOutline = true
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)

                val savedZoom = sharedPrefs.getFloat("map_zoom_level", 16.0f).toDouble()
                controller.setZoom(savedZoom)

                addMapListener(object : MapListener {
                    override fun onScroll(event: ScrollEvent?): Boolean = false
                    override fun onZoom(event: ZoomEvent?): Boolean {
                        val currentZoom = zoomLevelDouble.toFloat()
                        sharedPrefs.edit().putFloat("map_zoom_level", currentZoom).apply()
                        return false
                    }
                })
                onMapReady(this)
            }
        },
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)),
        update = { map ->
            map.overlays.clear()

            for ((index, range) in ranges.withIndex()) {
                if (pickingScheduleIndex != -1 && index != pickingScheduleIndex) {
                    continue
                }
                // Start Point
                if (range.startLat != null && range.startLon != null) {
                    val center = GeoPoint(range.startLat, range.startLon)
                    val titleStr = "${range.name} - Start Point (${String.format(Locale.US, "%02d:%02d", range.startHour, range.startMinute)})"
                    val startMarker = createNumberedMarker(context, map, center, titleStr, android.graphics.Color.GREEN, range.name)
                    map.overlays.add(startMarker)

                    if (range.startRadiusMeters > 0) {
                        val circle = Polygon().apply {
                            setPoints(Polygon.pointsAsCircle(center, range.startRadiusMeters.toDouble()))
                            fillPaint.color = android.graphics.Color.argb(40, 0, 255, 0)
                            outlinePaint.color = android.graphics.Color.GREEN
                            outlinePaint.strokeWidth = 3f
                        }
                        map.overlays.add(circle)
                    }
                }

                // End Point
                if (range.endLat != null && range.endLon != null) {
                    val center = GeoPoint(range.endLat, range.endLon)
                    val titleStr = "${range.name} - End Point (${String.format(Locale.US, "%02d:%02d", range.endHour, range.endMinute)})"
                    val endMarker = createNumberedMarker(context, map, center, titleStr, android.graphics.Color.RED, range.name)
                    map.overlays.add(endMarker)

                    if (range.endRadiusMeters > 0) {
                        val circle = Polygon().apply {
                            setPoints(Polygon.pointsAsCircle(center, range.endRadiusMeters.toDouble()))
                            fillPaint.color = android.graphics.Color.argb(40, 255, 0, 0)
                            outlinePaint.color = android.graphics.Color.RED
                            outlinePaint.strokeWidth = 3f
                        }
                        map.overlays.add(circle)
                    }
                }
            }

            val validPoints = points.filter { !it.isIgnored }
            if (validPoints.isNotEmpty()) {
                val geoPoints = validPoints.map { GeoPoint(it.latitude, it.longitude) }

                val polyline = Polyline().apply {
                    setPoints(geoPoints)
                    outlinePaint.color = android.graphics.Color.BLUE
                    outlinePaint.strokeWidth = 5f
                }
                map.overlays.add(polyline)

                val startMarker = Marker(map).apply {
                    position = geoPoints.first()
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    title = "Start"
                    icon = ContextCompat.getDrawable(context, org.osmdroid.library.R.drawable.marker_default)?.mutate()?.apply {
                        setTint(android.graphics.Color.GREEN)
                    }
                }
                map.overlays.add(startMarker)

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

            val eventsReceiver = object : MapEventsReceiver {
                override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                    if (pickingScheduleIndex != -1 && p != null) {
                        onPointSelectedOnMap(p)
                        return true
                    }
                    return false
                }

                override fun longPressHelper(p: GeoPoint?): Boolean {
                    if (pickingScheduleIndex != -1 && p != null) {
                        onPointSelectedOnMap(p)
                        return true
                    }
                    return false
                }
            }
            map.overlays.add(MapEventsOverlay(eventsReceiver))
            if (validPoints.isNotEmpty()) {
                val geoPoints = validPoints.map { GeoPoint(it.latitude, it.longitude) }

                val polyline = Polyline().apply {
                    setPoints(geoPoints)
                    outlinePaint.color = android.graphics.Color.BLUE
                    outlinePaint.strokeWidth = 5f
                }
                map.overlays.add(polyline)

                val startMarker = Marker(map).apply {
                    position = geoPoints.first()
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    title = "Start"
                    icon = ContextCompat.getDrawable(context, org.osmdroid.library.R.drawable.marker_default)?.mutate()?.apply {
                        setTint(android.graphics.Color.GREEN)
                    }
                }
                map.overlays.add(startMarker)

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

    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val reversedPoints = remember(points) { points.reversed() }

    Box(modifier = modifier) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize()
        ) {
            itemsIndexed(
                items = reversedPoints,
                key = { index, pt -> "${index}_${pt.time}_${pt.latitude}_${pt.longitude}" }
            ) { _, pt ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .background(
                            if (pt.isIgnored) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                            else MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(8.dp)
                        )
                        .padding(12.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Time: ${pt.time.replace("T", " ").replace("Z", "")}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (pt.isIgnored) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
                            )
                            if (pt.isIgnored) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "ignored",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier
                                        .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
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

        // Interactive Vertical Scrollbar
        val totalItems = points.size
        if (totalItems > 0) {
            val firstVisibleItemIndex = listState.firstVisibleItemIndex
            val visibleItemCount = listState.layoutInfo.visibleItemsInfo.size
            val scrollProgress = if (totalItems > visibleItemCount && totalItems > 1) {
                firstVisibleItemIndex.toFloat() / (totalItems - visibleItemCount).toFloat()
            } else {
                0f
            }.coerceIn(0f, 1f)

            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(24.dp)
                    .fillMaxHeight()
                    .padding(vertical = 8.dp)
                    .pointerInput(totalItems, visibleItemCount) {
                        detectVerticalDragGestures(
                            onDragStart = { offset ->
                                val trackHeight = size.height.toFloat()
                                if (trackHeight > 0f) {
                                    val tapFraction = (offset.y / trackHeight).coerceIn(0f, 1f)
                                    val targetIndex = (tapFraction * (totalItems - visibleItemCount)).toInt().coerceIn(0, totalItems - 1)
                                    coroutineScope.launch {
                                        listState.scrollToItem(targetIndex)
                                    }
                                }
                            },
                            onVerticalDrag = { change, _ ->
                                change.consume()
                                val trackHeight = size.height.toFloat()
                                if (trackHeight > 0f) {
                                    val dragFraction = (change.position.y / trackHeight).coerceIn(0f, 1f)
                                    val targetIndex = (dragFraction * (totalItems - visibleItemCount)).toInt().coerceIn(0, totalItems - 1)
                                    coroutineScope.launch {
                                        listState.scrollToItem(targetIndex)
                                    }
                                }
                            }
                        )
                    }
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .width(4.dp)
                        .fillMaxHeight()
                        .background(Color.Gray.copy(alpha = 0.2f), RoundedCornerShape(2.dp))
                )

                val thumbHeightFraction = (visibleItemCount.toFloat() / totalItems.toFloat()).coerceIn(0.15f, 1f)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(thumbHeightFraction)
                        .align(androidx.compose.ui.BiasAlignment(0f, scrollProgress * 2f - 1f))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.8f), RoundedCornerShape(2.dp))
                )
            }
        }

        // Top and Bottom Buttons
        val context = LocalContext.current
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            horizontalAlignment = Alignment.End
        ) {
            FloatingActionButton(
                onClick = {
                    coroutineScope.launch {
                        listState.scrollToItem(0)
                        Toast.makeText(context, "Scroll to Top", Toast.LENGTH_SHORT).show()
                    }
                },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Scroll to Top", modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.height(8.dp))
            FloatingActionButton(
                onClick = {
                    coroutineScope.launch {
                        listState.scrollToItem(points.size - 1)
                        Toast.makeText(context, "Scroll to Bottom", Toast.LENGTH_SHORT).show()
                    }
                },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Scroll to Bottom", modifier = Modifier.size(20.dp))
            }
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

private fun fetchAndCenterLocation(
    context: android.content.Context,
    fusedLocationClient: FusedLocationProviderClient,
    mapView: MapView
) {
    try {
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { location ->
                if (location != null) {
                    val geoPoint = GeoPoint(location.latitude, location.longitude)
                    mapView.controller.animateTo(geoPoint)
                    mapView.controller.setZoom(16.0)
                    Toast.makeText(context, "Centered on current GPS position", Toast.LENGTH_SHORT).show()
                } else {
                    fusedLocationClient.lastLocation.addOnSuccessListener { lastLoc ->
                        if (lastLoc != null) {
                            val geoPoint = GeoPoint(lastLoc.latitude, lastLoc.longitude)
                            mapView.controller.animateTo(geoPoint)
                            mapView.controller.setZoom(16.0)
                            Toast.makeText(context, "Centered on last known GPS position", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Unable to get current GPS position. Make sure GPS is enabled.", Toast.LENGTH_LONG).show()
                        }
                    }.addOnFailureListener {
                        Toast.makeText(context, "Failed to get location: ${it.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .addOnFailureListener {
                fusedLocationClient.lastLocation.addOnSuccessListener { lastLoc ->
                    if (lastLoc != null) {
                        val geoPoint = GeoPoint(lastLoc.latitude, lastLoc.longitude)
                        mapView.controller.animateTo(geoPoint)
                        mapView.controller.setZoom(16.0)
                        Toast.makeText(context, "Centered on last known GPS position", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Unable to get current GPS position. Make sure GPS is enabled.", Toast.LENGTH_LONG).show()
                    }
                }.addOnFailureListener {
                    Toast.makeText(context, "Failed to get location: ${it.message}", Toast.LENGTH_SHORT).show()
                }
            }
    } catch (e: Exception) {
        Toast.makeText(context, "Error fetching location: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

