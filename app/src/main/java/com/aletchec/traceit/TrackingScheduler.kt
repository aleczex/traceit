package com.aletchec.traceit

import android.content.Context
import java.util.Calendar
import java.util.Locale

class TrackingScheduler {

    data class TimeRange(
        val startHour: Int,
        val startMinute: Int,
        val endHour: Int,
        val endMinute: Int,
        val startLat: Double? = null,
        val startLon: Double? = null,
        val startRadiusMeters: Int = 0,
        val endLat: Double? = null,
        val endLon: Double? = null,
        val endRadiusMeters: Int = 0
    )

    fun isTrackingAllowed(ranges: List<TimeRange>): Boolean {
        return getActiveRange(ranges) != null
    }

    fun getActiveRange(ranges: List<TimeRange>): TimeRange? {
        if (ranges.isEmpty()) return null

        val now = Calendar.getInstance()
        val currentHour = now.get(Calendar.HOUR_OF_DAY)
        val currentMinute = now.get(Calendar.MINUTE)
        val currentTimeInMinutes = currentHour * 60 + currentMinute

        for (range in ranges) {
            val startInMinutes = range.startHour * 60 + range.startMinute
            val endInMinutes = range.endHour * 60 + range.endMinute

            if (startInMinutes <= endInMinutes) {
                if (currentTimeInMinutes in startInMinutes..endInMinutes) return range
            } else {
                // Range spans across midnight
                if (currentTimeInMinutes >= startInMinutes || currentTimeInMinutes <= endInMinutes) return range
            }
        }
        return null
    }

    fun serializeRanges(ranges: List<TimeRange>): String {
        return ranges.joinToString("; ") {
            val start = String.format(Locale.US, "%02d:%02d", it.startHour, it.startMinute)
            val end = String.format(Locale.US, "%02d:%02d", it.endHour, it.endMinute)
            val timeStr = "$start-$end"

            val startStr = if (it.startLat != null && it.startLon != null) {
                "${it.startLat},${it.startLon},${it.startRadiusMeters}"
            } else ""

            val endStr = if (it.endLat != null && it.endLon != null) {
                "${it.endLat},${it.endLon},${it.endRadiusMeters}"
            } else ""

            if (startStr.isNotEmpty() || endStr.isNotEmpty()) {
                "$timeStr@$startStr@$endStr"
            } else {
                timeStr
            }
        }
    }

    fun parseRanges(input: String): List<TimeRange> {
        return try {
            val delimiter = if (input.contains(";")) ";" else ","
            input.split(delimiter).mapNotNull { rangeStr ->
                val str = rangeStr.trim()
                if (str.isEmpty()) return@mapNotNull null

                val partsAt = str.split("@")
                val timePart = partsAt[0].trim()

                val parts = timePart.split("-")
                if (parts.size == 2) {
                    val start = parts[0].trim().split(":")
                    val end = parts[1].trim().split(":")
                    if (start.size == 2 && end.size == 2) {
                        var startLat: Double? = null
                        var startLon: Double? = null
                        var startRadius = 0

                        var endLat: Double? = null
                        var endLon: Double? = null
                        var endRadius = 0

                        val startLocPart = partsAt.getOrNull(1)?.trim()
                        if (!startLocPart.isNullOrEmpty()) {
                            val locParts = startLocPart.split(",")
                            if (locParts.size == 3) {
                                startLat = locParts[0].toDoubleOrNull()
                                startLon = locParts[1].toDoubleOrNull()
                                startRadius = locParts[2].toIntOrNull() ?: 0
                            }
                        }

                        val endLocPart = partsAt.getOrNull(2)?.trim()
                        if (!endLocPart.isNullOrEmpty()) {
                            val locParts = endLocPart.split(",")
                            if (locParts.size == 3) {
                                endLat = locParts[0].toDoubleOrNull()
                                endLon = locParts[1].toDoubleOrNull()
                                endRadius = locParts[2].toIntOrNull() ?: 0
                            }
                        }

                        TimeRange(
                            start[0].toInt(), start[1].toInt(),
                            end[0].toInt(), end[1].toInt(),
                            startLat, startLon, startRadius,
                            endLat, endLon, endRadius
                        )
                    } else null
                } else null
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveSchedules(context: Context, ranges: List<TimeRange>) {
        val sharedPrefs = context.getSharedPreferences("tracking_prefs", Context.MODE_PRIVATE)
        sharedPrefs.edit().putString("saved_ranges", serializeRanges(ranges)).apply()
    }

    fun loadSchedules(context: Context): List<TimeRange> {
        val sharedPrefs = context.getSharedPreferences("tracking_prefs", Context.MODE_PRIVATE)
        val savedStr = sharedPrefs.getString("saved_ranges", null)
        return if (!savedStr.isNullOrBlank()) {
            parseRanges(savedStr)
        } else {
            listOf(TimeRange(8, 0, 16, 0))
        }
    }

    fun getGpsIntervalSeconds(context: Context): Int {
        val sharedPrefs = context.getSharedPreferences("tracking_prefs", Context.MODE_PRIVATE)
        return sharedPrefs.getInt("gps_interval_seconds", 5)
    }

    fun saveGpsIntervalSeconds(context: Context, seconds: Int) {
        val sharedPrefs = context.getSharedPreferences("tracking_prefs", Context.MODE_PRIVATE)
        sharedPrefs.edit().putInt("gps_interval_seconds", seconds).apply()
    }
}