package com.aletchec.traceit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class TrackingSchedulerTest {

    @Test
    fun testAddNewScheduleAndSerialization() {
        val scheduler = TrackingScheduler()
        val initialRanges = mutableListOf(
            TrackingScheduler.TimeRange(8, 0, 16, 0, name = "Default Schedule")
        )

        // Add a new schedule
        initialRanges.add(
            TrackingScheduler.TimeRange(9, 0, 17, 0, name = "New Schedule")
        )

        assertEquals(2, initialRanges.size)
        assertEquals("New Schedule", initialRanges[1].name)
        assertEquals(9, initialRanges[1].startHour)
        assertEquals(17, initialRanges[1].endHour)

        // Test serialization and parsing
        val serialized = scheduler.serializeRanges(initialRanges)
        assertNotNull(serialized)

        val parsedRanges = scheduler.parseRanges(serialized)
        assertEquals(2, parsedRanges.size)
        assertEquals("Default Schedule", parsedRanges[0].name)
        assertEquals("New Schedule", parsedRanges[1].name)
        assertEquals(9, parsedRanges[1].startHour)
        assertEquals(17, parsedRanges[1].endHour)
    }

    @Test
    fun testDefaultTimeRangeValues() {
        val range = TrackingScheduler.TimeRange(9, 0, 17, 0, name = "Test")
        assertEquals(5, range.gpsIntervalSeconds)
        assertEquals(7, range.daysOfWeek.size)
        assertEquals("Test", range.name)
    }
}
