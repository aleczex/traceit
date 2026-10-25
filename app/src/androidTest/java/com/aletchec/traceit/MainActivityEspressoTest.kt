package com.aletchec.traceit

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityEspressoTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

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
}
