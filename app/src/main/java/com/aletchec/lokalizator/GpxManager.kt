package com.aletchec.lokalizator

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

    fun getGpxFile(): File {
        val fileName = "track_${SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())}.gpx"
        return File(context.getExternalFilesDir(null), fileName)
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
