package com.crazyfluff.shellfstudy.feature.dashboard

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.crazyfluff.shellfstudy.core.data.model.StudyStreak
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class StudyStreakCardTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun showsEncouragement_whenNoStreakYet() {
        composeTestRule.setContent {
            StudyStreakCard(streak = StudyStreak(0, 0, isActiveToday = false, activeDaysLast7 = List(7) { false }))
        }

        composeTestRule.onNodeWithText("Complete a review to start your streak!").assertIsDisplayed()
    }

    @Test
    fun showsStreakCountAndWeekRow_whenActive() {
        composeTestRule.setContent {
            StudyStreakCard(
                streak = StudyStreak(
                    currentStreakDays = 5,
                    longestStreakDays = 10,
                    isActiveToday = true,
                    activeDaysLast7 = listOf(true, true, true, true, true, true, true)
                )
            )
        }

        composeTestRule.onNodeWithTag(StudyStreakTestTags.STREAK_COUNT).assertIsDisplayed()
        composeTestRule.onNodeWithText("5 day streak").assertIsDisplayed()
        composeTestRule.onNodeWithTag(StudyStreakTestTags.WEEK_ROW).assertIsDisplayed()
    }

    @Test
    fun showsEncouragement_whenNullBeforeFirstLoad() {
        composeTestRule.setContent { StudyStreakCard(streak = null) }

        composeTestRule.onNodeWithText("Complete a review to start your streak!").assertIsDisplayed()
    }
}
