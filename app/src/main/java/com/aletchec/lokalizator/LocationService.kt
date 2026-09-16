package com.aletchec.lokalizator

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionRequest
import com.google.android.gms.location.DetectedActivity
import android.content.BroadcastReceiver
import android.app.NotificationManager
import android.app.NotificationChannel
import android.content.pm.ServiceInfo
import android.hardware.TriggerEvent
import android.hardware.TriggerEventListener

class LocationService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var gpxManager: GpxManager
    private lateinit var scheduler: TrackingScheduler
    private lateinit var sensorManager: SensorManager
    private var significantMotionSensor: Sensor? = null
    private lateinit var activityReceiver: BroadcastReceiver
    private lateinit var motionListener: TriggerEventListener

    private enum class State {
        IDLE,
        WAITING_FOR_MOTION,
        TRACKING
    }

    private var currentState = State.IDLE

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        gpxManager = GpxManager(this)
        scheduler = TrackingScheduler()
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        significantMotionSensor = sensorManager.getDefaultSensor(Sensor.TYPE_SIGNIFICANT_MOTION)

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    handleLocationUpdate(location)
                }
            }
        }
        setupReceivers()
        setupMotionListener()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundService()
        evaluateState()
        return START_STICKY
    }

    private fun setupMotionListener() {
        motionListener = object : TriggerEventListener() {
            override fun onTrigger(event: TriggerEvent?) {
                if (currentState == State.WAITING_FOR_MOTION) {
                    startTracking()
                }
                // Re-arm the sensor
                startMotionSensor()
            }
        }
    }

    private fun setupReceivers() {
        activityReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == "com.lokalizator.ACTIVITY_TRANSITION") {
                    if (com.google.android.gms.location.ActivityTransitionResult.hasResult(intent)) {
                        val result = com.google.android.gms.location.ActivityTransitionResult.extractResult(intent)!!
                        for (event in result.transitionEvents) {
                            if (event.activityType == DetectedActivity.STILL && event.transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER) {
                                if (currentState == State.TRACKING) {
                                    stopTrackingAndListenForMotion()
                                }
                            } else if (event.activityType != DetectedActivity.STILL && event.transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER) {
                                if (currentState != State.TRACKING) {
                                    startTracking()
                                }
                            }
                        }
                    }
                }
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(activityReceiver, IntentFilter("com.lokalizator.ACTIVITY_TRANSITION"), RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(activityReceiver, IntentFilter("com.lokalizator.ACTIVITY_TRANSITION"))
        }
    }


    private fun evaluateState() {
        val currentRanges = scheduler.loadSchedules(this)
        if (scheduler.getActiveRange(currentRanges) != null) {
            startTracking()
        } else {
            stopTrackingAndListenForMotion()
        }
    }
    
    private fun startTracking() {
        if (currentState == State.TRACKING) return
        currentState = State.TRACKING
        updateNotification("Tracking active.")
        startLocationUpdates()
        startActivityTransitionUpdates()
        stopMotionSensor()
    }

    private fun stopTrackingAndListenForMotion() {
        if (currentState == State.WAITING_FOR_MOTION || currentState == State.IDLE) return
        currentState = State.WAITING_FOR_MOTION
        updateNotification("Tracking paused. Waiting for movement.")
        stopLocationUpdates()
        stopActivityTransitionUpdates()
        startMotionSensor()
    }

    private fun handleLocationUpdate(location: Location) {
        val activeRange = scheduler.getActiveRange(scheduler.loadSchedules(this))
        if (activeRange != null) {
            val file = gpxManager.getGpxFile(activeRange)
            gpxManager.appendLocation(file, location)
            updateNotification("Tracking active. Last point: ${location.latitude}, ${location.longitude}")
        } else {
            evaluateState()
        }
    }

    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000)
            .setMinUpdateIntervalMillis(5000)
            .build()
        try {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
        } catch (unlikely: SecurityException) {
            stopSelf()
        }
    }

    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    private fun startMotionSensor() {
        significantMotionSensor?.let {
            sensorManager.requestTriggerSensor(motionListener, it)
        }
    }

    private fun stopMotionSensor() {
        significantMotionSensor?.let {
            sensorManager.cancelTriggerSensor(motionListener, it)
        }
    }

    private fun startActivityTransitionUpdates() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        val transitions = mutableListOf<ActivityTransition>()
        transitions.add(ActivityTransition.Builder().setActivityType(DetectedActivity.STILL).setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER).build())
        transitions.add(ActivityTransition.Builder().setActivityType(DetectedActivity.STILL).setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_EXIT).build())

        val request = ActivityTransitionRequest(transitions)
        val intent = Intent("com.lokalizator.ACTIVITY_TRANSITION")
        val pendingIntent = PendingIntent.getBroadcast(this, 1, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val task = ActivityRecognition.getClient(this).requestActivityTransitionUpdates(request, pendingIntent)
        task.addOnFailureListener {
            // Handle error
        }
    }

    private fun stopActivityTransitionUpdates() {
        val intent = Intent("com.lokalizator.ACTIVITY_TRANSITION")
        val pendingIntent = PendingIntent.getBroadcast(this, 1, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        ActivityRecognition.getClient(this).removeActivityTransitionUpdates(pendingIntent)
    }


    private fun startForegroundService() {
        val channelId = "location_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Location Tracking",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
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
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, "location_channel")
            .setContentTitle("Lokalizator")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(content: String) {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(1, createNotification(content))
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        stopLocationUpdates()
        stopMotionSensor()
        stopActivityTransitionUpdates()
        unregisterReceiver(activityReceiver)
    }
}