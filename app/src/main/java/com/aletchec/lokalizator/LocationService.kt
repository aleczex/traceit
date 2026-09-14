package com.aletchec.lokalizator

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

class LocationService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var gpxManager: GpxManager
    private lateinit var scheduler: TrackingScheduler
    private var timeRanges: List<TrackingScheduler.TimeRange> = emptyList()

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        gpxManager = GpxManager(this)
        scheduler = TrackingScheduler()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    handleLocationUpdate(location)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val rangesStr = intent?.getStringExtra("ranges") ?: ""
        timeRanges = if (rangesStr.isNotBlank()) {
            scheduler.parseRanges(rangesStr)
        } else {
            scheduler.loadSchedules(this)
        }

        startForegroundService()
        startLocationUpdates()

        return START_STICKY
    }

    private fun handleLocationUpdate(location: Location) {
        val currentRanges = scheduler.loadSchedules(this)
        val activeRange = scheduler.getActiveRange(currentRanges)
        if (activeRange != null) {
            val file = gpxManager.getGpxFile(activeRange)
            gpxManager.appendLocation(file, location)
            updateNotification("Tracking active. Last point: ${location.latitude}, ${location.longitude}")
        } else {
            updateNotification("Tracking paused (outside scheduled hours)")
        }
    }

    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000)
            .setMinUpdateIntervalMillis(5000)
            .build()

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        } catch (unlikely: SecurityException) {
            stopSelf()
        }
    }

    private fun startForegroundService() {
        val channelId = "location_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Location Tracking",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val notification = createNotification("Initializing GPS tracking...")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(1, notification)
        }
    }

    private fun createNotification(content: String): Notification {
        return NotificationCompat.Builder(this, "location_channel")
            .setContentTitle("Lokalizator")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(content: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(1, createNotification(content))
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }
}
