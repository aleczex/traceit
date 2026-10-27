package com.aletchec.traceit

import android.Manifest
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityEspressoTest {

    @get:Rule
    val grantPermissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.POST_NOTIFICATIONS,
        Manifest.permission.ACCESS_BACKGROUND_LOCATION
    )

    @get:Rule
    val composeTestRule: AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity> =
        createAndroidComposeRule<MainActivity>()

    @Before
    fun setUp() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        context.getSharedPreferences("tracking_prefs", android.content.Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun activityLaunches() {
        composeTestRule.onNodeWithTag("menu_button").assertIsDisplayed()
        waitForMapToLoad()
        takeScreenshot("main_screen.png")
    }

    @Test
    fun menuButtonIsDisplayed() {
        val menuText = composeTestRule.activity.getString(R.string.menu)
        composeTestRule.onNodeWithContentDescription(menuText).assertIsDisplayed()
        takeScreenshot("menu_button.png")
    }

    @Test
    fun menuContainsSettingsAction() {
        val menuText = composeTestRule.activity.getString(R.string.menu)
        val optionsText = composeTestRule.activity.getString(R.string.options)
        composeTestRule.onNodeWithContentDescription(menuText).performClick()
        composeTestRule.onNodeWithText(optionsText).assertIsDisplayed()
        takeScreenshot("options_dialog.png")
    }

    @Test
    fun menuContainsAboutAction() {
        val menuText = composeTestRule.activity.getString(R.string.menu)
        val aboutText = composeTestRule.activity.getString(R.string.about)
        composeTestRule.onNodeWithContentDescription(menuText).performClick()
        composeTestRule.onNodeWithText(aboutText).assertIsDisplayed()
        composeTestRule.onNodeWithText(aboutText).performClick()
        composeTestRule.waitForIdle()
        takeScreenshot("about_dialog.png")
    }

    @Test
    fun addNewScheduleTest() {
        try {
            val nodes = composeTestRule.onAllNodesWithTag("app_title").fetchSemanticsNodes()
            if (nodes.isNotEmpty()) {
                composeTestRule.onNodeWithTag("app_title").performTouchInput { swipeUp() }
            }
        } catch (_: Exception) {}

        composeTestRule.onAllNodesWithTag("schedule_item").assertCountEquals(1)
        composeTestRule.onNodeWithTag("add_schedule_button").performClick()
        composeTestRule.onAllNodesWithTag("schedule_item").assertCountEquals(2)
        takeScreenshot("track_screen.png")
    }

    private fun takeScreenshot(fileName: String) {
        try {
            val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
            val bitmap = instrumentation.uiAutomation.takeScreenshot()
            if (bitmap != null) {
                val dir = java.io.File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES), "screenshots")
                if (!dir.exists()) dir.mkdirs()
                val file = java.io.File(dir, fileName)
                java.io.FileOutputStream(file).use { out ->
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
                }
                bitmap.recycle()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun waitForMapToLoad(timeoutMillis: Long = 5000) {
        composeTestRule.waitUntil(timeoutMillis) {
            try {
                composeTestRule.onNodeWithTag("track_map").assertIsDisplayed()
                true
            } catch (_: Exception) {
                false
            }
        }
        composeTestRule.waitForIdle()
        Thread.sleep(1500)
        composeTestRule.waitForIdle()
    }
}
