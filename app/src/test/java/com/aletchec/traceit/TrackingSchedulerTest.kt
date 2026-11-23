package com.aletchec.traceit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar

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

    @Test
    fun testDayOfWeekRecognitionNormalSchedule() {
        val scheduler = TrackingScheduler()
        val range = TrackingScheduler.TimeRange(
            startHour = 8, startMinute = 0,
            endHour = 16, endMinute = 0,
            name = "Workday",
            daysOfWeek = setOf(Calendar.MONDAY)
        )

        val calMonday = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            set(Calendar.HOUR_OF_DAY, 10)
            set(Calendar.MINUTE, 0)
        }

        val calTuesday = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, Calendar.TUESDAY)
            set(Calendar.HOUR_OF_DAY, 10)
            set(Calendar.MINUTE, 0)
        }

        val activeOnMonday = scheduler.getActiveRange(listOf(range), calMonday)
        assertNotNull(activeOnMonday)
        assertEquals("Workday", activeOnMonday?.name)

        val activeOnTuesday = scheduler.getActiveRange(listOf(range), calTuesday)
        assertNull(activeOnTuesday)
    }

    @Test
    fun testDayOfWeekRecognitionOvernightSchedule() {
        val scheduler = TrackingScheduler()
        val overnightRange = TrackingScheduler.TimeRange(
            startHour = 22, startMinute = 0,
            endHour = 4, endMinute = 0,
            name = "Night Shift",
            daysOfWeek = setOf(Calendar.MONDAY)
        )

        // Monday at 23:00 (Evening part of Monday night shift)
        val calMondayNight = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 0)
        }

        // Tuesday at 02:00 (Morning part of Monday night shift, continuing into Tuesday)
        val calTuesdayMorning = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, Calendar.TUESDAY)
            set(Calendar.HOUR_OF_DAY, 2)
            set(Calendar.MINUTE, 0)
        }

        // Wednesday at 02:00 (Should NOT be active)
        val calWednesdayMorning = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, Calendar.WEDNESDAY)
            set(Calendar.HOUR_OF_DAY, 2)
            set(Calendar.MINUTE, 0)
        }

        assertNotNull(scheduler.getActiveRange(listOf(overnightRange), calMondayNight))
        assertNotNull(scheduler.getActiveRange(listOf(overnightRange), calTuesdayMorning))
        assertNull(scheduler.getActiveRange(listOf(overnightRange), calWednesdayMorning))
    }

    @Test
    fun testSingleScheduleSerializationAndParsing() {
        val scheduler = TrackingScheduler()
        val singleRange = listOf(
            TrackingScheduler.TimeRange(9, 0, 17, 0, name = "Single Schedule")
        )

        val serialized = scheduler.serializeRanges(singleRange)
        val parsedRanges = scheduler.parseRanges(serialized)

        assertEquals(1, parsedRanges.size)
        assertEquals("Single Schedule", parsedRanges[0].name)
        assertEquals(9, parsedRanges[0].startHour)
        assertEquals(17, parsedRanges[0].endHour)
        assertEquals(7, parsedRanges[0].daysOfWeek.size)
    }

    @Test
    fun testScheduleNameSanitizesSemicolon() {
        val scheduler = TrackingScheduler()
        val ranges = listOf(
            TrackingScheduler.TimeRange(8, 0, 16, 0, name = "Work; Shift")
        )

        val serialized = scheduler.serializeRanges(ranges)
        val parsedRanges = scheduler.parseRanges(serialized)

        assertEquals(1, parsedRanges.size)
        assertEquals("Work  Shift", parsedRanges[0].name)
    }

    @Test
    fun testNewScheduleModifyHoursPreservesDefaultDays() {
        val newRange = TrackingScheduler.TimeRange(9, 0, 17, 0, name = "New")
        assertEquals(7, newRange.daysOfWeek.size)

        // User changes only hours (mimicking copy() in UI)
        val modifiedRange = newRange.copy(startHour = 10, endHour = 18)
        assertEquals(7, modifiedRange.daysOfWeek.size)
        assertEquals(10, modifiedRange.startHour)
        assertEquals(18, modifiedRange.endHour)
    }

    @Test
    fun testCopySchedule() {
        val range = TrackingScheduler.TimeRange(8, 0, 16, 0, name = "Work")
        val copySuffix = " - kopia"
        val duplicated = range.copy(name = range.name + copySuffix)

        assertEquals("Work - kopia", duplicated.name)
        assertEquals(range.startHour, duplicated.startHour)
        assertEquals(range.startMinute, duplicated.startMinute)
        assertEquals(range.endHour, duplicated.endHour)
        assertEquals(range.endMinute, duplicated.endMinute)
        assertEquals(range.gpsIntervalSeconds, duplicated.gpsIntervalSeconds)
        assertEquals(range.daysOfWeek, duplicated.daysOfWeek)
    }
}
