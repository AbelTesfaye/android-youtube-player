package com.pierfrancescosoffritti.androidyoutubeplayer.core

import android.app.Activity
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.filters.LargeTest
import androidx.test.internal.runner.junit4.statement.UiThreadStatement.runOnUiThread
import androidx.test.rule.ActivityTestRule
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView
import com.pierfrancescosoffritti.androidyoutubeplayer.core.testActivity.TestActivity
import com.pierfrancescosoffritti.androidyoutubeplayer.test.R
import org.hamcrest.CoreMatchers.allOf
import org.hamcrest.CoreMatchers.not
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@LargeTest
class CustomUITest {

    private lateinit var testActivity: Activity
    private lateinit var youTubePlayerView: YouTubePlayerView

    @Rule
    @JvmField
    val activityRule = ActivityTestRule(TestActivity::class.java)

    @Before
    fun setup() {
        testActivity = activityRule.activity
        youTubePlayerView = testActivity.findViewById(R.id.youtube_player_view_native_ui)
    }

    @Test
    fun testCustomUiIsSet() {
        // Prepare

        // Act
        runOnUiThread {
            youTubePlayerView.inflateCustomPlayerUi(R.layout.custom_layout)
        }

        // Assert
        onView(allOf(isDescendantOfA(withId(R.id.youtube_player_view_native_ui)), withId(R.id.ayp_default_native_ui_layout))).check(doesNotExist()) // checks if the default native ui is removed

        onView(withId(R.id.custom_linear_layout)).check(matches(not(doesNotExist()))) // checks if custom_layout was inflated
    }
}