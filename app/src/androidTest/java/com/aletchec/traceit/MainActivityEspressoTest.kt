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
    }

    @Test
    fun menuButtonIsDisplayed() {
        val menuText = composeTestRule.activity.getString(R.string.menu)
        composeTestRule.onNodeWithContentDescription(menuText).assertIsDisplayed()
    }

    @Test
    fun menuContainsSettingsAction() {
        val menuText = composeTestRule.activity.getString(R.string.menu)
        val optionsText = composeTestRule.activity.getString(R.string.options)
        composeTestRule.onNodeWithContentDescription(menuText).performClick()
        composeTestRule.onNodeWithText(optionsText).assertIsDisplayed()
    }

    @Test
    fun menuContainsAboutAction() {
        val menuText = composeTestRule.activity.getString(R.string.menu)
        val aboutText = composeTestRule.activity.getString(R.string.about)
        composeTestRule.onNodeWithContentDescription(menuText).performClick()
        composeTestRule.onNodeWithText(aboutText).assertIsDisplayed()
    }

    @Test
    fun addNewScheduleTest() {
        try {
            composeTestRule.onNodeWithTag("app_title").performTouchInput { swipeUp() }
        } catch (_: Exception) {}

        composeTestRule.onAllNodesWithTag("schedule_item").assertCountEquals(1)
        composeTestRule.onNodeWithTag("add_schedule_button").performClick()
        composeTestRule.onAllNodesWithTag("schedule_item").assertCountEquals(2)
    }
}
