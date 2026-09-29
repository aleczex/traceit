package com.aletchec.traceit

import android.content.Context
import java.util.Calendar
import java.util.Locale

class TrackingScheduler {

    data class TimeRange(val startHour: Int, val startMinute: Int, val endHour: Int, val endMinute: Int)

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
        return ranges.joinToString(", ") {
            val start = String.format(Locale.US, "%02d:%02d", it.startHour, it.startMinute)
            val end = String.format(Locale.US, "%02d:%02d", it.endHour, it.endMinute)
            "$start-$end"
        }
    }

    fun parseRanges(input: String): List<TimeRange> {
        // Expected format: "08:00-16:00, 20:00-22:00"
        return try {
            input.split(",").mapNotNull { rangeStr ->
                val parts = rangeStr.trim().split("-")
                if (parts.size == 2) {
                    val start = parts[0].trim().split(":")
                    val end = parts[1].trim().split(":")
                    if (start.size == 2 && end.size == 2) {
                        TimeRange(
                            start[0].toInt(), start[1].toInt(),
                            end[0].toInt(), end[1].toInt()
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
            // Default initial schedule if none exists
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

    fun getDepartureDistanceThresholdMeters(context: Context): Int {
        val sharedPrefs = context.getSharedPreferences("tracking_prefs", Context.MODE_PRIVATE)
        return sharedPrefs.getInt("departure_distance_threshold_meters", 50)
    }

    fun saveDepartureDistanceThresholdMeters(context: Context, meters: Int) {
        val sharedPrefs = context.getSharedPreferences("tracking_prefs", Context.MODE_PRIVATE)
        sharedPrefs.edit().putInt("departure_distance_threshold_meters", meters).apply()
    }
}