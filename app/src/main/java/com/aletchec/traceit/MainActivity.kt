package com.aletchec.traceit

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.graphics.Bitmap
import android.os.Bundle
import android.view.MotionEvent
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.rounded.HistoryEdu
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.aletchec.traceit.ui.component.AboutAppDialog
import com.aletchec.traceit.ui.component.OptionsDialog
import com.aletchec.traceit.ui.theme.ThemeMode
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

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("tracking_prefs", MODE_PRIVATE)
        val langTag = prefs.getString("app_language", "") ?: ""
        if (langTag.isNotEmpty()) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(langTag))
        }

        // osmdroid configuration
        Configuration.getInstance().load(this, getSharedPreferences("osmdroid", MODE_PRIVATE))
        Configuration.getInstance().userAgentValue = packageName

        // Initialize Mobile Ads SDK
        MobileAds.initialize(this) {}

        // UMP Consent Request
        val params = ConsentRequestParameters.Builder().build()
        val consentInformation = UserMessagingPlatform.getConsentInformation(this)
        consentInformation.requestConsentInfoUpdate(
            this,
            params,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(this) { _ -> }
            },
            { _ -> }
        )

        enableEdgeToEdge()
        setContent {
            val themeStr = prefs.getString("theme_mode", "SYSTEM") ?: "SYSTEM"
            var currentThemeMode by remember {
                mutableStateOf(
                    try { ThemeMode.valueOf(themeStr) } catch (e: Exception) { ThemeMode.SYSTEM }
                )
            }
            var currentLanguage by remember { mutableStateOf(langTag) }

            TraceItTheme(themeMode = currentThemeMode) {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    TrackingScreen(
                        currentThemeMode = currentThemeMode,
                        onThemeSelected = { newMode ->
                            currentThemeMode = newMode
                            prefs.edit().putString("theme_mode", newMode.name).apply()
                        },
                        currentLanguage = currentLanguage,
                        onLanguageSelected = { newTag ->
                            currentLanguage = newTag
                            prefs.edit().putString("app_language", newTag).apply()
                            val localeList = if (newTag.isEmpty()) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(newTag)
                            AppCompatDelegate.setApplicationLocales(localeList)
                            recreate()
                        },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

private fun Activity.showPrivacyOptions() {
    val consentInformation = UserMessagingPlatform.getConsentInformation(this)
    if (consentInformation.privacyOptionsRequirementStatus == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED) {
        UserMessagingPlatform.showPrivacyOptionsForm(this) { formError ->
            if (formError != null) {
                Toast.makeText(this, formError.message, Toast.LENGTH_SHORT).show()
            }
        }
    } else {
        Toast.makeText(this, getString(R.string.privacy_form_not_available), Toast.LENGTH_SHORT).show()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackingScreen(
    currentThemeMode: ThemeMode,
    onThemeSelected: (ThemeMode) -> Unit,
    currentLanguage: String,
    onLanguageSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
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

    var showMenuDropdown by remember { mutableStateOf(false) }
    var showOptionsDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }

    val onPrivacyClick: () -> Unit = {
        val activity = context as? Activity
        activity?.showPrivacyOptions()
    }

    val onLicensesClick: () -> Unit = {
        try {
            context.startActivity(Intent(context, com.google.android.gms.oss.licenses.OssLicensesMenuActivity::class.java))
        } catch (e: Exception) {
            Toast.makeText(context, e.message ?: "Failed to open licenses", Toast.LENGTH_SHORT).show()
        }
    }

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
                    Toast.makeText(context, R.string.track_saved_successfully, Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, context.getString(R.string.failed_to_save_track, e.message ?: ""), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    var imageFileToExport by remember { mutableStateOf<java.io.File?>(null) }
    val createImageDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("image/png")
    ) { uri ->
        uri?.let { destinationUri ->
            imageFileToExport?.let { srcFile ->
                try {
                    context.contentResolver.openOutputStream(destinationUri)?.use { outputStream ->
                        srcFile.inputStream().use { inputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                    Toast.makeText(context, R.string.export_success, Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, context.getString(R.string.export_failed, e.message ?: ""), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    var pdfFileToExport by remember { mutableStateOf<java.io.File?>(null) }
    val createPdfDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        uri?.let { destinationUri ->
            pdfFileToExport?.let { srcFile ->
                try {
                    context.contentResolver.openOutputStream(destinationUri)?.use { outputStream ->
                        srcFile.inputStream().use { inputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                    Toast.makeText(context, R.string.export_success, Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, context.getString(R.string.export_failed, e.message ?: ""), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val importGpxLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { sourceUri ->
            try {
                var fileName = "imported_${System.currentTimeMillis()}.gpx"
                context.contentResolver.query(sourceUri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (cursor.moveToFirst() && nameIndex != -1) {
                        val displayName = cursor.getString(nameIndex)
                        if (!displayName.isNullOrBlank()) {
                            fileName = if (displayName.endsWith(".gpx", ignoreCase = true)) {
                                displayName
                            } else {
                                "$displayName.gpx"
                            }
                        }
                    }
                }

                val targetDir = context.getExternalFilesDir(null)
                val destFile = java.io.File(targetDir, fileName)
                context.contentResolver.openInputStream(sourceUri)?.use { inputStream ->
                    destFile.outputStream().use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                Toast.makeText(context, R.string.gpx_imported_successfully, Toast.LENGTH_SHORT).show()
                selectedFile = destFile
            } catch (e: Exception) {
                Toast.makeText(context, context.getString(R.string.failed_to_import_gpx, e.message ?: ""), Toast.LENGTH_LONG).show()
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
                    text = stringResource(R.string.app_name),
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
                        text = if (isTrackingNow) stringResource(R.string.tracking_active) else stringResource(R.string.outside_schedule),
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
                        text = { Text(stringResource(R.string.tab_schedules), maxLines = 1, softWrap = false) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = {
                            selectedTab = 1
                            scope.launch { scaffoldState.bottomSheetState.expand() }
                        },
                        text = { Text(stringResource(R.string.tab_points), maxLines = 1, softWrap = false) }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = {
                            selectedTab = 2
                            scope.launch { scaffoldState.bottomSheetState.expand() }
                        },
                        text = { Text(stringResource(R.string.tab_saved), maxLines = 1, softWrap = false) }
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
                                    Text(text = stringResource(R.string.tracking_schedule), style = MaterialTheme.typography.titleMedium)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    IconButton(onClick = {
                                        ranges.add(TrackingScheduler.TimeRange(9, 0, 17, 0, name = context.getString(R.string.schedule_default_name)))
                                        scheduler.saveSchedules(context, ranges)
                                    }) {
                                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_schedule))
                                    }
                                    Spacer(modifier = Modifier.weight(1f))
                                    TextButton(onClick = {
                                        val file = selectedFile ?: run {
                                            val activeRange = scheduler.getActiveRange(scheduler.loadSchedules(context))
                                            gpxManager.getGpxFile(activeRange)
                                        }
                                        trackPoints = gpxManager.getTrackPoints(file)
                                    }) {
                                        Text(stringResource(R.string.refresh))
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
                                            },
                                            onSetStartRadius = {
                                                editingRadiusScheduleIndex = index
                                                editingRadiusTarget = PickTarget.START
                                                showRadiusDialog = true
                                            },
                                            onSetEndRadius = {
                                                editingRadiusScheduleIndex = index
                                                editingRadiusTarget = PickTarget.END
                                                showRadiusDialog = true
                                            }
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    text = if (selectedFile != null) {
                                        stringResource(R.string.viewing_track, selectedFile?.name?.replace("track_", "")?.replace(".gpx", "") ?: "")
                                    } else {
                                        val activeRange = scheduler.getActiveRange(ranges)
                                        if (activeRange != null) {
                                            stringResource(R.string.active_schedule_format, activeRange.startHour, activeRange.startMinute, activeRange.endHour, activeRange.endMinute)
                                        } else {
                                            stringResource(R.string.no_active_schedule)
                                        }
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                if (selectedFile != null) {
                                    TextButton(onClick = { selectedFile = null }) {
                                        Text(stringResource(R.string.show_current_active_track))
                                    }
                                }
                            }
                        }
                        1 -> TrackPointsList(points = trackPoints, gpxManager = gpxManager, modifier = Modifier.fillMaxSize())
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

            var isMapMoving by remember { mutableStateOf(false) }

            TrackMap(
                points = trackPoints,
                ranges = ranges,
                pickingScheduleIndex = pickingScheduleIndex,
                onPointSelectedOnMap = { point ->
                    if (pickingScheduleIndex in ranges.indices) {
                        val targetIndex = pickingScheduleIndex
                        val currentRange = ranges[targetIndex]
                        if (pickTarget == PickTarget.START) {
                            if (currentRange.endLat != null && currentRange.endLon != null && currentRange.endRadiusMeters > 0) {
                                val results = FloatArray(1)
                                android.location.Location.distanceBetween(
                                    point.latitude, point.longitude,
                                    currentRange.endLat, currentRange.endLon,
                                    results
                                )
                                if (results[0] <= currentRange.endRadiusMeters) {
                                    Toast.makeText(context, R.string.start_point_cannot_be_within_end, Toast.LENGTH_SHORT).show()
                                    editingSettingsScheduleIndex = targetIndex
                                    showSettingsDialog = true
                                    pickingScheduleIndex = -1
                                    return@TrackMap
                                }
                            }
                            val defaultRadius = if (currentRange.startRadiusMeters > 0) currentRange.startRadiusMeters else 50
                            ranges[targetIndex] = currentRange.copy(
                                startLat = point.latitude,
                                startLon = point.longitude,
                                startRadiusMeters = defaultRadius
                            )
                            scheduler.saveSchedules(context, ranges)
                            Toast.makeText(context, R.string.start_point_set, Toast.LENGTH_SHORT).show()
                        } else {
                            if (currentRange.startLat != null && currentRange.startLon != null && currentRange.startRadiusMeters > 0) {
                                val results = FloatArray(1)
                                android.location.Location.distanceBetween(
                                    point.latitude, point.longitude,
                                    currentRange.startLat, currentRange.startLon,
                                    results
                                )
                                if (results[0] <= currentRange.startRadiusMeters) {
                                    Toast.makeText(context, R.string.end_point_cannot_be_within_start, Toast.LENGTH_SHORT).show()
                                    editingSettingsScheduleIndex = targetIndex
                                    showSettingsDialog = true
                                    pickingScheduleIndex = -1
                                    return@TrackMap
                                }
                            }
                            val defaultRadius = if (currentRange.endRadiusMeters > 0) currentRange.endRadiusMeters else 50
                            ranges[targetIndex] = currentRange.copy(
                                endLat = point.latitude,
                                endLon = point.longitude,
                                endRadiusMeters = defaultRadius
                            )
                            scheduler.saveSchedules(context, ranges)
                            Toast.makeText(context, R.string.end_point_set, Toast.LENGTH_SHORT).show()
                        }
                        editingSettingsScheduleIndex = targetIndex
                        showSettingsDialog = true
                    }
                    pickingScheduleIndex = -1
                },
                onMapReady = { map ->
                    mapViewInstance = map
                },
                onMapMovingChanged = { moving ->
                    isMapMoving = moving
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
                    Toast.makeText(context, R.string.location_permission_required, Toast.LENGTH_SHORT).show()
                }
            }

            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 16.dp),
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
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.zoom_in))
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
                    Icon(Icons.Default.Remove, contentDescription = stringResource(R.string.zoom_out))
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
                    Icon(Icons.Default.MyLocation, contentDescription = stringResource(R.string.show_current_gps_location))
                }
            }

            // Top-right Hamburger Menu Button
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 16.dp, end = 16.dp)
            ) {
                FloatingActionButton(
                    onClick = { showMenuDropdown = true },
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(Icons.Default.Menu, contentDescription = stringResource(R.string.menu))
                }

                DropdownMenu(
                    expanded = showMenuDropdown,
                    onDismissRequest = { showMenuDropdown = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.options)) },
                        leadingIcon = {
                            Icon(Icons.Default.Settings, contentDescription = null)
                        },
                        onClick = {
                            showMenuDropdown = false
                            showOptionsDialog = true
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.privacy_options)) },
                        leadingIcon = {
                            Icon(Icons.Rounded.PrivacyTip, contentDescription = null)
                        },
                        onClick = {
                            showMenuDropdown = false
                            onPrivacyClick()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.open_source_licenses)) },
                        leadingIcon = {
                            Icon(Icons.Rounded.HistoryEdu, contentDescription = null)
                        },
                        onClick = {
                            showMenuDropdown = false
                            onLicensesClick()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.about)) },
                        leadingIcon = {
                            Icon(Icons.Default.Info, contentDescription = null)
                        },
                        onClick = {
                            showMenuDropdown = false
                            showAboutDialog = true
                        }
                    )
                }
            }

            val validTrackPoints = remember(trackPoints) { trackPoints.filter { !it.isIgnored } }
            if (validTrackPoints.isNotEmpty() && !isMapMoving) {
                val currentFile = remember(selectedFile, ranges) {
                    selectedFile ?: gpxManager.getGpxFile(scheduler.getActiveRange(ranges))
                }
                val stats = remember(currentFile, trackPoints) {
                    gpxManager.getTrackStatistics(currentFile)
                }
                val startTimeStr = validTrackPoints.firstOrNull()?.time?.let { gpxManager.formatPointTime(it) } ?: "-"
                val endTimeStr = validTrackPoints.lastOrNull()?.time?.let { gpxManager.formatPointTime(it) } ?: "-"
                val distanceStr = gpxManager.formatDistance(stats.distanceMeters)
                val durationStr = gpxManager.formatDuration(stats.durationMillis)

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, top = 16.dp, end = 72.dp, bottom = 16.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
                        .padding(12.dp)
                ) {
                    Column {
                        var showExportMenu by remember { mutableStateOf(false) }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (selectedFile != null) stringResource(R.string.loaded_track, selectedFile?.name?.replace("track_", "")?.replace(".gpx", "") ?: "") else stringResource(R.string.active_track),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f)
                            )
                            if (selectedFile != null) {
                                TextButton(onClick = { selectedFile = null }) {
                                    Text(stringResource(R.string.show_current))
                                }
                            }
                            Box {
                                IconButton(onClick = { showExportMenu = true }) {
                                    Icon(Icons.Default.Print, contentDescription = stringResource(R.string.export_map))
                                }
                                DropdownMenu(
                                    expanded = showExportMenu,
                                    onDismissRequest = { showExportMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.export_as_image)) },
                                        onClick = {
                                            showExportMenu = false
                                            mapViewInstance?.let { map ->
                                                val fileName = currentFile.name
                                                val bmp = MapExportUtils.generateAnnotatedMapBitmap(
                                                    context, map, fileName, distanceStr, durationStr, startTimeStr, endTimeStr
                                                )
                                                if (bmp != null) {
                                                    val file = java.io.File(context.cacheDir, "track_map_${System.currentTimeMillis()}.png")
                                                    java.io.FileOutputStream(file).use { out ->
                                                        bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
                                                    }
                                                    imageFileToExport = file
                                                    createImageDocLauncher.launch(fileName.replace(".gpx", ".png"))
                                                } else {
                                                    Toast.makeText(context, R.string.export_failed, Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.export_as_pdf)) },
                                        onClick = {
                                            showExportMenu = false
                                            mapViewInstance?.let { map ->
                                                val fileName = currentFile.name
                                                val pdfFile = MapExportUtils.generatePdf(
                                                    context, map, fileName, distanceStr, durationStr, startTimeStr, endTimeStr
                                                )
                                                if (pdfFile != null) {
                                                    pdfFileToExport = pdfFile
                                                    createPdfDocLauncher.launch(fileName.replace(".gpx", ".pdf"))
                                                } else {
                                                    Toast.makeText(context, R.string.export_failed, Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.print_map)) },
                                        onClick = {
                                            showExportMenu = false
                                            mapViewInstance?.let { map ->
                                                val fileName = currentFile.name
                                                val pdfFile = MapExportUtils.generatePdf(
                                                    context, map, fileName, distanceStr, durationStr, startTimeStr, endTimeStr
                                                )
                                                if (pdfFile != null) {
                                                    MapExportUtils.printDocument(context, pdfFile, fileName)
                                                } else {
                                                    Toast.makeText(context, R.string.export_failed, Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = stringResource(R.string.distance_label, distanceStr), style = MaterialTheme.typography.bodySmall)
                            Text(text = stringResource(R.string.total_time_label, durationStr), style = MaterialTheme.typography.bodySmall)
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = stringResource(R.string.start_time_label, startTimeStr), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                            Text(text = stringResource(R.string.end_time_label, endTimeStr), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                        }
                    }
                }
            }

            if (pickingScheduleIndex != -1) {
                val targetText = if (pickTarget == PickTarget.START) stringResource(R.string.point_start_upper) else stringResource(R.string.point_end_upper)
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
                            text = stringResource(R.string.tap_map_to_set_point, targetText, pickingScheduleIndex + 1),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = {
                            if (pickingScheduleIndex in ranges.indices) {
                                editingSettingsScheduleIndex = pickingScheduleIndex
                                showSettingsDialog = true
                            }
                            pickingScheduleIndex = -1
                        }) {
                            Text(stringResource(R.string.cancel))
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
                val targetIndex = editingSettingsScheduleIndex
                editingSettingsScheduleIndex = -1
                showSettingsDialog = false
                pickingScheduleIndex = targetIndex
                pickTarget = PickTarget.START
                scope.launch { scaffoldState.bottomSheetState.partialExpand() }
            },
            onPickEnd = {
                val targetIndex = editingSettingsScheduleIndex
                editingSettingsScheduleIndex = -1
                showSettingsDialog = false
                pickingScheduleIndex = targetIndex
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
            title = { Text(stringResource(R.string.select_days_of_week)) },
            text = {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        OutlinedButton(onClick = {
                            selectedDays = setOf(Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY)
                        }) {
                            Text(stringResource(R.string.days_mo_su))
                        }
                        OutlinedButton(onClick = {
                            selectedDays = setOf(Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY)
                        }) {
                            Text(stringResource(R.string.days_mo_fri))
                        }
                        OutlinedButton(onClick = {
                            selectedDays = setOf(Calendar.SUNDAY, Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY)
                        }) {
                            Text(stringResource(R.string.days_su_sa))
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    val dayEntries = listOf(
                        Calendar.MONDAY to stringResource(R.string.day_monday),
                        Calendar.TUESDAY to stringResource(R.string.day_tuesday),
                        Calendar.WEDNESDAY to stringResource(R.string.day_wednesday),
                        Calendar.THURSDAY to stringResource(R.string.day_thursday),
                        Calendar.FRIDAY to stringResource(R.string.day_friday),
                        Calendar.SATURDAY to stringResource(R.string.day_saturday),
                        Calendar.SUNDAY to stringResource(R.string.day_sunday)
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
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDaysDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showIntervalDialog && editingIntervalScheduleIndex in ranges.indices) {
        val currentRange = ranges[editingIntervalScheduleIndex]
        var intervalInput by remember { mutableStateOf(currentRange.gpsIntervalSeconds.toString()) }

        AlertDialog(
            onDismissRequest = { showIntervalDialog = false },
            title = { Text(stringResource(R.string.check_interval)) },
            text = {
                Column {
                    Text(stringResource(R.string.check_interval_prompt))
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = intervalInput,
                        onValueChange = { intervalInput = it },
                        label = { Text(stringResource(R.string.seconds)) },
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
                        Toast.makeText(context, R.string.error_positive_number, Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showIntervalDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showRadiusDialog && editingRadiusScheduleIndex in ranges.indices) {
        val currentRange = ranges[editingRadiusScheduleIndex]
        val initialRadius = if (editingRadiusTarget == PickTarget.START) currentRange.startRadiusMeters else currentRange.endRadiusMeters
        var radiusInput by remember { mutableStateOf(if (initialRadius > 0) initialRadius.toString() else "50") }

        val titleText = if (editingRadiusTarget == PickTarget.START) stringResource(R.string.start_radius_title) else stringResource(R.string.end_radius_title)
        val actionText = if (editingRadiusTarget == PickTarget.START) stringResource(R.string.radius_departure) else stringResource(R.string.radius_arrival)

        AlertDialog(
            onDismissRequest = { showRadiusDialog = false },
            title = { Text(titleText) },
            text = {
                Column {
                    Text(stringResource(R.string.radius_prompt, actionText))
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = radiusInput,
                        onValueChange = { radiusInput = it },
                        label = { Text(stringResource(R.string.radius_meters_label)) },
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
                        Toast.makeText(context, R.string.error_valid_radius, Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRadiusDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showOptionsDialog) {
        OptionsDialog(
            currentThemeMode = currentThemeMode,
            onThemeSelected = onThemeSelected,
            currentLanguage = currentLanguage,
            onLanguageSelected = onLanguageSelected,
            onImportGpx = {
                importGpxLauncher.launch(arrayOf("application/gpx+xml", "text/xml", "application/octet-stream", "*/*"))
            },
            onDismiss = { showOptionsDialog = false }
        )
    }

    if (showAboutDialog) {
        AboutAppDialog(
            onDismiss = { showAboutDialog = false }
        )
    }
}

fun formatDays(days: Set<Int>, context: Context): String {
    val allDays = setOf(Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY)
    val workDays = setOf(Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY)
    val suSaDays = setOf(Calendar.SUNDAY, Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY)

    return when {
        days == allDays || days == suSaDays -> context.getString(R.string.days_mo_su)
        days == workDays -> context.getString(R.string.days_mo_fri)
        days.size == 7 -> context.getString(R.string.days_su_sa)
        else -> {
            val dayNames = mapOf(
                Calendar.MONDAY to context.getString(R.string.day_mon_short),
                Calendar.TUESDAY to context.getString(R.string.day_tue_short),
                Calendar.WEDNESDAY to context.getString(R.string.day_wed_short),
                Calendar.THURSDAY to context.getString(R.string.day_thu_short),
                Calendar.FRIDAY to context.getString(R.string.day_fri_short),
                Calendar.SATURDAY to context.getString(R.string.day_sat_short),
                Calendar.SUNDAY to context.getString(R.string.day_sun_short)
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
    onTogglePickEnd: () -> Unit,
    onSetStartRadius: (() -> Unit)? = null,
    onSetEndRadius: (() -> Unit)? = null
) {
    val context = LocalContext.current
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
                    text = range.name.ifBlank { stringResource(R.string.schedule_default_name) },
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
                    text = formatDays(range.daysOfWeek, context),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.interval_seconds_format, range.gpsIntervalSeconds),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val hasStart = range.startLat != null && range.startLon != null
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            if (isPickingStart) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                            else Color.Transparent
                        )
                        .clickable { onTogglePickStart() }
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = stringResource(R.string.start_point_desc),
                        tint = if (hasStart) Color(0xFF2E7D32) else Color.Gray,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = if (hasStart) stringResource(R.string.start_radius_format, range.startRadiusMeters) else stringResource(R.string.start_not_set),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (hasStart || isPickingStart) Color(0xFF2E7D32) else Color.Gray
                    )
                }

                if (hasStart && onSetStartRadius != null) {
                    Spacer(modifier = Modifier.width(2.dp))
                    TextButton(
                        onClick = onSetStartRadius,
                        modifier = Modifier.height(24.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                    ) {
                        Text(stringResource(R.string.radius), style = MaterialTheme.typography.labelSmall)
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                val hasEnd = range.endLat != null && range.endLon != null
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            if (isPickingEnd) MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
                            else Color.Transparent
                        )
                        .clickable { onTogglePickEnd() }
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = stringResource(R.string.end_point_desc),
                        tint = if (hasEnd) Color(0xFFC62828) else Color.Gray,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = if (hasEnd) stringResource(R.string.end_radius_format, range.endRadiusMeters) else stringResource(R.string.end_not_set),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (hasEnd || isPickingEnd) Color(0xFFC62828) else Color.Gray
                    )
                }

                if (hasEnd && onSetEndRadius != null) {
                    Spacer(modifier = Modifier.width(2.dp))
                    TextButton(
                        onClick = onSetEndRadius,
                        modifier = Modifier.height(24.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                    ) {
                        Text(stringResource(R.string.radius), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        IconButton(onClick = onOpenSettings) {
            Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.schedule_settings_desc))
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
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.schedule_settings_title)) },
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
                        label = { Text(stringResource(R.string.schedule_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Text(stringResource(R.string.time_and_duration), style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(onClick = onEditStart, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.start_time_btn, String.format(Locale.US, "%02d:%02d", range.startHour, range.startMinute)))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedButton(onClick = onEditEnd, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.end_time_btn, String.format(Locale.US, "%02d:%02d", range.endHour, range.endMinute)))
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(onClick = onEditDays, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.days_btn, formatDays(range.daysOfWeek, context)))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedButton(onClick = onEditInterval, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.interval_btn, range.gpsIntervalSeconds))
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Text(stringResource(R.string.start_point_title), style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val startText = if (range.startLat != null && range.startLon != null) {
                            String.format(Locale.US, "%.4f, %.4f (%dm)", range.startLat, range.startLon, range.startRadiusMeters)
                        } else {
                            stringResource(R.string.not_set)
                        }
                        Text(
                            text = startText,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onPickStart) {
                            Text(stringResource(R.string.pick))
                        }
                        if (range.startLat != null && range.startLon != null) {
                            TextButton(onClick = onSetStartRadius) {
                                Text(stringResource(R.string.radius))
                            }
                            IconButton(onClick = onClearStart) {
                                Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.clear_start), modifier = Modifier.size(20.dp))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(stringResource(R.string.end_point_title), style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val endText = if (range.endLat != null && range.endLon != null) {
                            String.format(Locale.US, "%.4f, %.4f (%dm)", range.endLat, range.endLon, range.endRadiusMeters)
                        } else {
                            stringResource(R.string.not_set)
                        }
                        Text(
                            text = endText,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onPickEnd) {
                            Text(stringResource(R.string.pick))
                        }
                        if (range.endLat != null && range.endLon != null) {
                            TextButton(onClick = onSetEndRadius) {
                                Text(stringResource(R.string.radius))
                            }
                            IconButton(onClick = onClearEnd) {
                                Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.clear_end), modifier = Modifier.size(20.dp))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = onDelete,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete_schedule), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.delete_schedule))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        }
    )
}

fun isDownloadUnlocked(context: Context): Boolean {
    val prefs = context.getSharedPreferences("tracking_prefs", Context.MODE_PRIVATE)
    val unlockUntil = prefs.getLong("download_unlocked_until", 0L)
    return System.currentTimeMillis() < unlockUntil
}

fun getDownloadUnlockRemainingTime(context: Context): String {
    val prefs = context.getSharedPreferences("tracking_prefs", Context.MODE_PRIVATE)
    val unlockUntil = prefs.getLong("download_unlocked_until", 0L)
    val diff = unlockUntil - System.currentTimeMillis()
    if (diff <= 0) return "0h 0m"
    val hours = diff / (3600 * 1000)
    val minutes = (diff % (3600 * 1000)) / (60 * 1000)
    return String.format(Locale.US, "%dh %02dm", hours, minutes)
}

fun setDownloadUnlockedForOneDay(context: Context) {
    val prefs = context.getSharedPreferences("tracking_prefs", Context.MODE_PRIVATE)
    val oneDayMillis = 24 * 60 * 60 * 1000L
    val unlockUntil = System.currentTimeMillis() + oneDayMillis
    prefs.edit().putLong("download_unlocked_until", unlockUntil).apply()
}

fun loadRewardedAd(context: Context, onAdLoaded: (RewardedAd?) -> Unit) {
    val adRequest = AdRequest.Builder().build()
    RewardedAd.load(
        context,
        BuildConfig.REWARDED_AD_UNIT_ID,
        adRequest,
        object : RewardedAdLoadCallback() {
            override fun onAdLoaded(ad: RewardedAd) {
                onAdLoaded(ad)
            }
            override fun onAdFailedToLoad(error: LoadAdError) {
                onAdLoaded(null)
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
    var fileToDownload by remember { mutableStateOf<java.io.File?>(null) }
    var showAdDialog by remember { mutableStateOf(false) }
    var rewardedAd by remember { mutableStateOf<RewardedAd?>(null) }
    var isUnlocked by remember { mutableStateOf(false) }
    var remainingTimeStr by remember { mutableStateOf("") }

    val context = LocalContext.current
    val ranges = remember { scheduler.loadSchedules(context) }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        files = gpxManager.getAllGpxFiles()
        isUnlocked = isDownloadUnlocked(context)
        if (isUnlocked) {
            remainingTimeStr = getDownloadUnlockRemainingTime(context)
        }
        loadRewardedAd(context) { ad ->
            rewardedAd = ad
        }
    }

    LaunchedEffect(isUnlocked) {
        while (true) {
            val unlocked = isDownloadUnlocked(context)
            isUnlocked = unlocked
            if (unlocked) {
                remainingTimeStr = getDownloadUnlockRemainingTime(context)
            }
            delay(1000)
        }
    }

    LaunchedEffect(selectedFile, files) {
        if (selectedFile != null) {
            val index = files.indexOfFirst { it.absolutePath == selectedFile.absolutePath }
            if (index != -1) {
                listState.animateScrollToItem(index)
            }
        }
    }

    Column(modifier = modifier) {
        val bannerBg = if (isUnlocked) Color(0xFFE8F5E9) else Color(0xFFFFF3E0)
        val bannerTextColor = if (isUnlocked) Color(0xFF2E7D32) else Color(0xFFE65100)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(bannerBg)
                .clickable {
                    if (!isUnlocked) {
                        fileToDownload = files.firstOrNull()
                        showAdDialog = true
                    }
                }
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isUnlocked) {
                    stringResource(R.string.download_unlocked_status, remainingTimeStr)
                } else {
                    stringResource(R.string.download_locked_status)
                },
                color = bannerTextColor,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        if (files.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.no_saved_tracks), style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize()
                ) {
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
                                        isActiveFile -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                                        isLoaded -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
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

                                Text(
                                    text = formattedName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = when {
                                        isActiveFile -> MaterialTheme.colorScheme.error
                                        isLoaded -> MaterialTheme.colorScheme.primary
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                val stats = remember(file) { gpxManager.getTrackStatistics(file) }
                                Text(
                                    text = stringResource(R.string.duration_distance_format, gpxManager.formatDuration(stats.durationMillis), gpxManager.formatDistance(stats.distanceMeters)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                Text(
                                    text = stringResource(R.string.file_size_format, file.length() / 1024.0),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }

                            Button(onClick = {
                                onSelectFile(file)
                            }) {
                                Text(stringResource(R.string.load))
                            }

                            Spacer(modifier = Modifier.width(4.dp))

                            IconButton(onClick = {
                                if (isDownloadUnlocked(context)) {
                                    onExportFile(file)
                                } else {
                                    fileToDownload = file
                                    showAdDialog = true
                                }
                            }) {
                                Icon(Icons.Default.SaveAlt, contentDescription = stringResource(R.string.export_track_file))
                            }

                            Spacer(modifier = Modifier.width(4.dp))

                            IconButton(onClick = {
                                fileToDelete = file
                            }) {
                                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete_track_file))
                            }
                        }
                    }
                }

                // Interactive Vertical Scrollbar
                val totalItems = files.size
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
            }
        }
    }

    if (showAdDialog) {
        AlertDialog(
            onDismissRequest = { showAdDialog = false },
            title = { Text(stringResource(R.string.watch_ad_to_download_title)) },
            text = { Text(stringResource(R.string.watch_ad_to_download_message)) },
            confirmButton = {
                TextButton(onClick = {
                    val activity = context as? Activity
                    if (rewardedAd != null && activity != null) {
                        rewardedAd?.show(activity) { _ ->
                            setDownloadUnlockedForOneDay(context)
                            isUnlocked = true
                            remainingTimeStr = getDownloadUnlockRemainingTime(context)
                            Toast.makeText(context, R.string.downloads_unlocked_toast, Toast.LENGTH_SHORT).show()
                            showAdDialog = false
                            fileToDownload?.let { onExportFile(it) }
                            loadRewardedAd(context) { ad -> rewardedAd = ad }
                        }
                    } else {
                        Toast.makeText(context, R.string.ad_not_ready, Toast.LENGTH_SHORT).show()
                        loadRewardedAd(context) { ad ->
                            rewardedAd = ad
                            if (ad != null && activity != null) {
                                ad.show(activity) { _ ->
                                    setDownloadUnlockedForOneDay(context)
                                    isUnlocked = true
                                    remainingTimeStr = getDownloadUnlockRemainingTime(context)
                                    Toast.makeText(context, R.string.downloads_unlocked_toast, Toast.LENGTH_SHORT).show()
                                    showAdDialog = false
                                    fileToDownload?.let { onExportFile(it) }
                                    loadRewardedAd(context) { loaded -> rewardedAd = loaded }
                                }
                            }
                        }
                    }
                }) {
                    Text(stringResource(R.string.watch_ad_btn))
                }
            },
            dismissButton = {
                TextButton(onClick = { showAdDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (fileToDelete != null) {
        val activeRange = scheduler.getActiveRange(ranges)
        val isActiveFile = fileToDelete?.absolutePath == gpxManager.getGpxFile(activeRange).absolutePath

        AlertDialog(
            onDismissRequest = { fileToDelete = null },
            title = { Text(stringResource(R.string.delete_track_title)) },
            text = {
                if (isActiveFile) {
                    Text(stringResource(R.string.delete_active_track_warning))
                } else {
                    Text(stringResource(R.string.delete_track_confirm))
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
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { fileToDelete = null }) {
                    Text(stringResource(R.string.cancel))
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
    onMapMovingChanged: (Boolean) -> Unit = {},
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

                val handler = android.os.Handler(android.os.Looper.getMainLooper())
                val scrollRunnable = Runnable { onMapMovingChanged(false) }

                setOnTouchListener { v, event ->
                    when (event.action) {
                        MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                            handler.removeCallbacks(scrollRunnable)
                            onMapMovingChanged(true)
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            v.performClick()
                            handler.removeCallbacks(scrollRunnable)
                            handler.postDelayed(scrollRunnable, 300)
                        }
                    }
                    false
                }

                addMapListener(object : MapListener {
                    override fun onScroll(event: ScrollEvent?): Boolean {
                        handler.removeCallbacks(scrollRunnable)
                        onMapMovingChanged(true)
                        handler.postDelayed(scrollRunnable, 300)
                        return false
                    }
                    override fun onZoom(event: ZoomEvent?): Boolean {
                        val currentZoom = zoomLevelDouble.toFloat()
                        sharedPrefs.edit().putFloat("map_zoom_level", currentZoom).apply()
                        handler.removeCallbacks(scrollRunnable)
                        onMapMovingChanged(true)
                        handler.postDelayed(scrollRunnable, 300)
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
                    val timeStr = String.format(Locale.US, "%02d:%02d", range.startHour, range.startMinute)
                    val titleStr = context.getString(R.string.schedule_start_marker_title, range.name, timeStr)
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
                    val timeStr = String.format(Locale.US, "%02d:%02d", range.endHour, range.endMinute)
                    val titleStr = context.getString(R.string.schedule_end_marker_title, range.name, timeStr)
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
                    title = context.getString(R.string.start_marker_title)
                    icon = ContextCompat.getDrawable(context, org.osmdroid.library.R.drawable.marker_default)?.mutate()?.apply {
                        setTint(android.graphics.Color.GREEN)
                    }
                }
                map.overlays.add(startMarker)

                val endMarker = Marker(map).apply {
                    position = geoPoints.last()
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    title = context.getString(R.string.latest_position_marker_title)
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
            map.invalidate()
        }
    )
}

@Composable
fun TrackPointsList(points: List<GpxManager.TrackPoint>, gpxManager: GpxManager, modifier: Modifier = Modifier) {
    if (points.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.no_gps_points), style = MaterialTheme.typography.bodyMedium)
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
                                text = stringResource(R.string.time_label, gpxManager.formatPointTime(pt.time)),
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (pt.isIgnored) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
                            )
                            if (pt.isIgnored) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.ignored_label),
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
                            text = stringResource(R.string.lat_lon_label, pt.latitude.toString(), pt.longitude.toString()),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = stringResource(R.string.elevation_label, pt.elevation.toString()),
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
                        Toast.makeText(context, R.string.scroll_to_top, Toast.LENGTH_SHORT).show()
                    }
                },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(Icons.Default.KeyboardArrowUp, contentDescription = stringResource(R.string.scroll_to_top), modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.height(8.dp))
            FloatingActionButton(
                onClick = {
                    coroutineScope.launch {
                        listState.scrollToItem(points.size - 1)
                        Toast.makeText(context, R.string.scroll_to_bottom, Toast.LENGTH_SHORT).show()
                    }
                },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = stringResource(R.string.scroll_to_bottom), modifier = Modifier.size(20.dp))
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
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
        text = {
            TimePicker(state = state)
        }
    )
}

private fun fetchAndCenterLocation(
    context: Context,
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
                    Toast.makeText(context, R.string.centered_on_current_gps, Toast.LENGTH_SHORT).show()
                } else {
                    fusedLocationClient.lastLocation.addOnSuccessListener { lastLoc ->
                        if (lastLoc != null) {
                            val geoPoint = GeoPoint(lastLoc.latitude, lastLoc.longitude)
                            mapView.controller.animateTo(geoPoint)
                            mapView.controller.setZoom(16.0)
                            Toast.makeText(context, R.string.centered_on_last_known_gps, Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, R.string.unable_to_get_gps, Toast.LENGTH_LONG).show()
                        }
                    }.addOnFailureListener {
                        Toast.makeText(context, context.getString(R.string.failed_to_get_location, it.message ?: ""), Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .addOnFailureListener {
                fusedLocationClient.lastLocation.addOnSuccessListener { lastLoc ->
                    if (lastLoc != null) {
                        val geoPoint = GeoPoint(lastLoc.latitude, lastLoc.longitude)
                        mapView.controller.animateTo(geoPoint)
                        mapView.controller.setZoom(16.0)
                        Toast.makeText(context, R.string.centered_on_last_known_gps, Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, R.string.unable_to_get_gps, Toast.LENGTH_LONG).show()
                    }
                }.addOnFailureListener {
                    Toast.makeText(context, context.getString(R.string.failed_to_get_location, it.message ?: ""), Toast.LENGTH_SHORT).show()
                }
            }
    } catch (e: Exception) {
        Toast.makeText(context, context.getString(R.string.error_fetching_location, e.message ?: ""), Toast.LENGTH_SHORT).show()
    }
}
