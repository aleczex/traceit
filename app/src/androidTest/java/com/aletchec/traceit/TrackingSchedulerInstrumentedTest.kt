package com.aletchec.traceit

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TrackingSchedulerInstrumentedTest {

    private lateinit var context: Context
    private lateinit var scheduler: TrackingScheduler

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        scheduler = TrackingScheduler()
        // Clear shared prefs
        context.getSharedPreferences("tracking_prefs", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun testAddNewScheduleAndPersistence() {
        val initialRanges = mutableListOf(
            TrackingScheduler.TimeRange(8, 0, 16, 0, name = "Default Schedule")
        )

        // Add a new schedule
        initialRanges.add(
            TrackingScheduler.TimeRange(9, 0, 17, 0, name = "New Schedule")
        )

        scheduler.saveSchedules(context, initialRanges)

        val loadedRanges = scheduler.loadSchedules(context)
        assertEquals(2, loadedRanges.size)
        assertEquals("Default Schedule", loadedRanges[0].name)
        assertEquals("New Schedule", loadedRanges[1].name)
        assertEquals(9, loadedRanges[1].startHour)
        assertEquals(17, loadedRanges[1].endHour)
    }
}
