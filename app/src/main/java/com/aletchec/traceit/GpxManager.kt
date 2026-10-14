package com.aletchec.traceit

import android.content.Context
import android.location.Location
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class GpxManager(private val context: Context) {

    data class TrackPoint(
        val latitude: Double,
        val longitude: Double,
        val elevation: Double,
        val time: String,
        val isIgnored: Boolean = false
    )

    data class TrackStatistics(
        val durationMillis: Long,
        val distanceMeters: Float
    )

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

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
                <gpx version="1.1" creator="TraceIt" xmlns="http://www.topografix.com/GPX/1/1">
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

        val regex = """<trkpt lat="([^"]+)" lon="([^"]+)">\s*<ele>([^<]+)</ele>\s*<time>([^<]+)</time>(?:\s*<desc>([^<]+)</desc>)?\s*</trkpt>""".toRegex()
        regex.findAll(content).forEach { matchResult ->
            val lat = matchResult.groupValues[1].toDoubleOrNull() ?: 0.0
            val lon = matchResult.groupValues[2].toDoubleOrNull() ?: 0.0
            val ele = matchResult.groupValues[3].toDoubleOrNull() ?: 0.0
            val time = matchResult.groupValues[4]
            val desc = matchResult.groupValues.getOrNull(5)
            val isIgnored = desc == "ignored"
            points.add(TrackPoint(lat, lon, ele, time, isIgnored))
        }
        return points
    }

    fun getTrackStatistics(file: File): TrackStatistics {
        val points = getTrackPoints(file).filter { !it.isIgnored }
        if (points.isEmpty()) return TrackStatistics(0L, 0f)

        var totalDistance = 0f
        for (i in 0 until points.size - 1) {
            val p1 = points[i]
            val p2 = points[i + 1]
            val results = FloatArray(1)
            Location.distanceBetween(p1.latitude, p1.longitude, p2.latitude, p2.longitude, results)
            totalDistance += results[0]
        }

        val durationMillis = try {
            val firstTime = dateFormat.parse(points.first().time)?.time ?: 0L
            val lastTime = dateFormat.parse(points.last().time)?.time ?: 0L
            if (lastTime >= firstTime) lastTime - firstTime else 0L
        } catch (e: Exception) {
            0L
        }

        return TrackStatistics(durationMillis, totalDistance)
    }

    fun formatDuration(durationMillis: Long): String {
        val seconds = durationMillis / 1000
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        return when {
            hours > 0 -> String.format(Locale.US, "%dh %02dm", hours, minutes)
            minutes > 0 -> String.format(Locale.US, "%dm %02ds", minutes, secs)
            else -> String.format(Locale.US, "%ds", secs)
        }
    }

    fun formatDistance(distanceMeters: Float): String {
        return if (distanceMeters >= 1000f) {
            String.format(Locale.US, "%.2f km", distanceMeters / 1000f)
        } else {
            String.format(Locale.US, "%.0f m", distanceMeters)
        }
    }
}