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

    private data class CacheEntry(
        val lastModified: Long,
        val length: Long,
        val statistics: TrackStatistics
    )

    private val statsCache = java.util.concurrent.ConcurrentHashMap<String, CacheEntry>()

    fun invalidateCache(file: File) {
        statsCache.remove(file.absolutePath)
    }

    fun clearCache() {
        statsCache.clear()
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
        invalidateCache(file)
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
        if (!file.exists()) return TrackStatistics(0L, 0f)

        val lastMod = file.lastModified()
        val len = file.length()
        val cached = statsCache[file.absolutePath]
        if (cached != null && cached.lastModified == lastMod && cached.length == len) {
            return cached.statistics
        }

        val points = getTrackPoints(file).filter { !it.isIgnored }
        if (points.isEmpty()) {
            val emptyStats = TrackStatistics(0L, 0f)
            statsCache[file.absolutePath] = CacheEntry(lastMod, len, emptyStats)
            return emptyStats
        }

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

        val computedStats = TrackStatistics(durationMillis, totalDistance)
        statsCache[file.absolutePath] = CacheEntry(lastMod, len, computedStats)
        return computedStats
    }

    fun formatDuration(durationMillis: Long): String {
        val seconds = durationMillis / 1000
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        return when {
            hours > 0 -> context.getString(R.string.duration_hours_minutes, hours, minutes)
            minutes > 0 -> context.getString(R.string.duration_minutes_seconds, minutes, secs)
            else -> context.getString(R.string.duration_seconds, secs)
        }
    }

    fun formatDistance(distanceMeters: Float): String {
        return if (distanceMeters >= 1000f) {
            context.getString(R.string.distance_km, distanceMeters / 1000f)
        } else {
            context.getString(R.string.distance_m, distanceMeters)
        }
    }

    fun formatPointTime(timeStr: String): String {
        try {
            val date = dateFormat.parse(timeStr) ?: return timeStr.replace("T", " ").replace("Z", "")
            val localFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
                timeZone = TimeZone.getDefault()
            }
            return localFormat.format(date)
        } catch (e: Exception) {
            return timeStr.replace("T", " ").replace("Z", "")
        }
    }

    fun saveTrackPoints(file: File, points: List<TrackPoint>) {
        val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<gpx version=\"1.1\" creator=\"TraceIt\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n")
        sb.append("  <trk>\n")
        sb.append("    <name>Track $dateStr</name>\n")
        sb.append("    <trkseg>\n")
        for (pt in points) {
            sb.append("      <trkpt lat=\"${pt.latitude}\" lon=\"${pt.longitude}\">\n")
            sb.append("        <ele>${pt.elevation}</ele>\n")
            sb.append("        <time>${pt.time}</time>\n")
            if (pt.isIgnored) {
                sb.append("        <desc>ignored</desc>\n")
            }
            sb.append("      </trkpt>\n")
        }
        sb.append("    </trkseg>\n")
        sb.append("  </trk>\n")
        sb.append("</gpx>\n")

        file.writeText(sb.toString())
        invalidateCache(file)
    }
}
