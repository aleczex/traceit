package com.aletchec.traceit

import android.content.Context
import android.location.Location
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class GpxManager(private val context: Context) {

    data class TrackPoint(val latitude: Double, val longitude: Double, val elevation: Double, val time: String)

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)

    fun getGpxFile(range: TrackingScheduler.TimeRange? = null): File {
        val dateStr = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
        val fileName = if (range != null) {
            val start = String.format(Locale.US, "%02d%02d", range.startHour, range.startMinute)
            val end = String.format(Locale.US, "%02d%02d", range.endHour, range.endMinute)
            "track_${dateStr}_${start}_${end}.gpx"
        } else {
            "track_${dateStr}.gpx"
        }
        return File(context.getExternalFilesDir(null), fileName)
    }

    fun getAllGpxFiles(): List<File> {
        val dir = context.getExternalFilesDir(null) ?: return emptyList()
        return dir.listFiles { _, name -> name.startsWith("track_") && name.endsWith(".gpx") }
            ?.sortedByDescending { it.name }
            ?.toList() ?: emptyList()
    }

    fun initGpxFile(file: File) {
        if (!file.exists()) {
            val header = """
                <?xml version="1.0" encoding="UTF-8"?>
                <gpx version="1.1" creator="Lokalizator" xmlns="http://www.topografix.com/GPX/1/1">
                  <trk>
                    <name>Track ${SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())}</name>
                    <trkseg>
                    </trkseg>
                  </trk>
                </gpx>
            """.trimIndent()
            file.writeText(header)
        }
    }

    fun appendLocation(file: File, location: Location) {
        if (!file.exists()) {
            initGpxFile(file)
        }

        val content = file.readText()
        val point = """
                      <trkpt lat="${location.latitude}" lon="${location.longitude}">
                        <ele>${location.altitude}</ele>
                        <time>${dateFormat.format(Date(location.time))}</time>
                      </trkpt>
                    </trkseg>
        """.trimIndent()

        val newContent = content.replace("</trkseg>", point)
        file.writeText(newContent)
    }

    fun getTrackPoints(file: File): List<TrackPoint> {
        if (!file.exists()) return emptyList()
        val content = file.readText()
        val points = mutableListOf<TrackPoint>()
        
        // Simple and safe matching for our precise format
        val regex = """<trkpt lat="([^"]+)" lon="([^"]+)">\s*<ele>([^<]+)</ele>\s*<time>([^<]+)</time>""".toRegex()
        regex.findAll(content).forEach { matchResult ->
            val lat = matchResult.groupValues[1].toDoubleOrNull() ?: 0.0
            val lon = matchResult.groupValues[2].toDoubleOrNull() ?: 0.0
            val ele = matchResult.groupValues[3].toDoubleOrNull() ?: 0.0
            val time = matchResult.groupValues[4]
            points.add(TrackPoint(lat, lon, ele, time))
        }
        return points
    }
}
