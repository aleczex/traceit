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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.aletchec.lokalizator.ui.theme.LokalizatorTheme

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

@Composable
fun TrackingScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var ranges by remember { mutableStateOf("08:00-16:00") }
    var isServiceRunning by remember { mutableStateOf(false) }

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
    ) { permissions ->
        val granted = permissions.values.all { it }
        if (granted) {
            // Background location needs separate request on Android 10+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // We should ideally show a rationale here
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(text = "GPS Lokalizator", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = ranges,
            onValueChange = { ranges = it },
            label = { Text("Time Ranges (e.g. 08:00-16:00, 20:00-22:00)") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                val allGranted = permissionsToRequest.all {
                    ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
                }

                if (allGranted) {
                    val intent = Intent(context, LocationService::class.java).apply {
                        putExtra("ranges", ranges)
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
            modifier = Modifier.fillMaxWidth(),
            enabled = !isServiceRunning
        ) {
            Text("Start Tracking")
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = {
                context.stopService(Intent(context, LocationService::class.java))
                isServiceRunning = false
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = isServiceRunning
        ) {
            Text("Stop Tracking")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = if (isServiceRunning) "Status: Running" else "Status: Stopped",
            style = MaterialTheme.typography.bodyLarge
        )

        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = "Note: Background location permission must be set to 'Allow all the time' in app settings for tracking when screen is off.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
    }
}
