package com.aletchec.lokalizator

import java.util.Calendar

class TrackingScheduler {

    data class TimeRange(val startHour: Int, val startMinute: Int, val endHour: Int, val endMinute: Int)

    fun isTrackingAllowed(ranges: List<TimeRange>): Boolean {
        if (ranges.isEmpty()) return true

        val now = Calendar.getInstance()
        val currentHour = now.get(Calendar.HOUR_OF_DAY)
        val currentMinute = now.get(Calendar.MINUTE)
        val currentTimeInMinutes = currentHour * 60 + currentMinute

        for (range in ranges) {
            val startInMinutes = range.startHour * 60 + range.startMinute
            val endInMinutes = range.endHour * 60 + range.endMinute

            if (startInMinutes <= endInMinutes) {
                if (currentTimeInMinutes in startInMinutes..endInMinutes) return true
            } else {
                // Range spans across midnight
                if (currentTimeInMinutes >= startInMinutes || currentTimeInMinutes <= endInMinutes) return true
            }
        }
        return false
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
}
