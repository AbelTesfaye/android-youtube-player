package com.pierfrancescosoffritti.androidyoutubeplayer.core

import android.app.Activity
import android.view.MotionEvent
import android.view.View
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.IdlingPolicies
import androidx.test.espresso.IdlingRegistry
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.MotionEvents
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.idling.CountingIdlingResource
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.filters.LargeTest
import androidx.test.rule.ActivityTestRule
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.YouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView
import com.pierfrancescosoffritti.androidyoutubeplayer.core.testActivity.TestActivity
import com.pierfrancescosoffritti.androidyoutubeplayer.test.R
import org.hamcrest.CoreMatchers.allOf
import org.hamcrest.Matcher
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min


/**
 * This class tests if the fast forward and fast rewind views work as expected.
 *
 * Note:
 * 1. Internet connection is required to perform this test since the player needs to start
 * playing a video before it can perform a fast forward/rewind.
 * 2. Slow internet is going to cause this test to fail because of timeouts
 */
@LargeTest
class YouTubePlayerViewFastForwardRewindTest {

    private lateinit var testActivity: Activity
    private lateinit var youTubePlayerView: YouTubePlayerView
    /**
     * This ViewAction has 2 functionalities:
     * 1. Provides an elegant way to perform 10 clicks
     * 2. Avoids the "90% of the view should be visible" constraint on the built-in click() and doubleClick() methods
     */
    private val click10Times = object : ViewAction {
        override fun getConstraints(): Matcher<View> {
            return ViewMatchers.isEnabled() // No un-necessary constraints
        }

        override fun getDescription(): String {
            return "Click view 10 times"
        }

        override fun perform(uiController: UiController, view: View) {
            // Get view absolute position
            val location = IntArray(2)
            view.getLocationOnScreen(location)

            // The touch coordinates should be at the center of the view
            val coordinates = FloatArray(2)
            coordinates[0] = (location[0] + (view.width / 2)).toFloat()
            coordinates[1] = (location[1] + (view.height / 2)).toFloat()

            val precision = FloatArray(2)
            precision[0] = 1F
            precision[1] = 1F

            val events = ArrayList<MotionEvent>()
            val downEvent = MotionEvents.obtainDownEvent(coordinates, precision)
            val upEvent = MotionEvents.obtainUpEvent(downEvent, coordinates)

            for (i in 1..10) {
                events.add(downEvent)
                events.add(upEvent)
            }
            uiController.injectMotionEventSequence(events)
        }
    }

    @Rule
    @JvmField
    val activityRule = ActivityTestRule(TestActivity::class.java)
    val playerPlayingCountingIdlingResource = CountingIdlingResource("player is playing")

    @Before
    fun setup() {
        testActivity = activityRule.activity
        youTubePlayerView = testActivity.findViewById(R.id.youtube_player_view_native_ui)
        IdlingPolicies.setMasterPolicyTimeout(5, TimeUnit.MINUTES)
        IdlingPolicies.setIdlingResourceTimeout(5, TimeUnit.MINUTES)
        IdlingRegistry.getInstance().register(playerPlayingCountingIdlingResource)
    }

    @Test
    fun testFastForward() {
        // Prepare
        val playerProgressQueue = LinkedList<Float>()
        playerPlayingCountingIdlingResource.increment() // Block espresso view actions till player starts playing

        youTubePlayerView.addYouTubePlayerListener(object : YouTubePlayerListener {
            override fun onCurrentSecond(youTubePlayer: YouTubePlayer, second: Float) {
                playerProgressQueue.add(second)
                if (!playerPlayingCountingIdlingResource.isIdleNow) {
                    playerPlayingCountingIdlingResource.decrement() // If player is playing, then continue running espresso view actions
                }
            }

            override fun onStateChange(youTubePlayer: YouTubePlayer, state: PlayerConstants.PlayerState) {
                // This player could be paused if other players are playing, so play the video in any case
                if (state == PlayerConstants.PlayerState.PAUSED || state == PlayerConstants.PlayerState.UNSTARTED || state == PlayerConstants.PlayerState.VIDEO_CUED)
                    youTubePlayer.play()
            }

            override fun onReady(youTubePlayer: YouTubePlayer) {}
            override fun onPlaybackQualityChange(youTubePlayer: YouTubePlayer, playbackQuality: PlayerConstants.PlaybackQuality) {}
            override fun onPlaybackRateChange(youTubePlayer: YouTubePlayer, playbackRate: PlayerConstants.PlaybackRate) {}
            override fun onError(youTubePlayer: YouTubePlayer, error: PlayerConstants.PlayerError) {}
            override fun onVideoDuration(youTubePlayer: YouTubePlayer, duration: Float) {}
            override fun onVideoLoadedFraction(youTubePlayer: YouTubePlayer, loadedFraction: Float) {}
            override fun onVideoId(youTubePlayer: YouTubePlayer, videoId: String) {}
            override fun onApiChange(youTubePlayer: YouTubePlayer) {}
        })

        onView(withId(R.id.youtube_player_view_native_ui)).perform(scrollTo())

        // Act
        onView(allOf(isDescendantOfA(withId(R.id.youtube_player_view_native_ui)), withId(R.id.fast_forward_layout))).perform(click10Times) // 10 clicks, should fast forward by 90 sec
        Thread.sleep(1000) // Wait 1 sec for the fast forward view to call skipTo()

        // Assert
        playerPlayingCountingIdlingResource.increment()
        onView(withId(R.id.youtube_player_view_native_ui)).perform(scrollTo()) // Used to pause execution while waiting for player to start playing again

        var maxDiffPlayerProgress = -1F
        var prevPlayerProgress = -1F
        // Gets the biggest difference between 2 consecutive player progress values
        while (playerProgressQueue.isNotEmpty()) {
            val currentPlayerProgress = playerProgressQueue.poll()!!
            maxDiffPlayerProgress = max(maxDiffPlayerProgress, (currentPlayerProgress - prevPlayerProgress))
            prevPlayerProgress = currentPlayerProgress
        }

        /**
         * The biggest difference between two consecutive player progress values will be caused by
         * fast forwarding. Hence we can check that to see if fast forward worked as expected.
         */
        assertTrue("Check if fast forward was successful", maxDiffPlayerProgress > 89)
    }

    @Test
    fun testFastRewind() {
        // Prepare
        val playerProgressQueue = LinkedList<Float>()
        playerPlayingCountingIdlingResource.increment() // Block espresso view actions till player starts playing

        youTubePlayerView.addYouTubePlayerListener(object : YouTubePlayerListener {
            override fun onCurrentSecond(youTubePlayer: YouTubePlayer, second: Float) {
                playerProgressQueue.add(second)
                if (second < 100) { // Move player progress to 100 sec
                    youTubePlayer.seekTo(100F)
                } else {
                    if (!playerPlayingCountingIdlingResource.isIdleNow) {
                        playerPlayingCountingIdlingResource.decrement() // If player's progress is >= 100 sec, then continue running espresso view actions
                    }
                }
            }

            override fun onStateChange(youTubePlayer: YouTubePlayer, state: PlayerConstants.PlayerState) {
                // This player could be paused if other players are playing, so play the video in any case                
                if (state == PlayerConstants.PlayerState.PAUSED || state == PlayerConstants.PlayerState.UNSTARTED || state == PlayerConstants.PlayerState.VIDEO_CUED) {
                    youTubePlayer.play()
                }
            }

            override fun onReady(youTubePlayer: YouTubePlayer) {}
            override fun onPlaybackQualityChange(youTubePlayer: YouTubePlayer, playbackQuality: PlayerConstants.PlaybackQuality) {}
            override fun onPlaybackRateChange(youTubePlayer: YouTubePlayer, playbackRate: PlayerConstants.PlaybackRate) {}
            override fun onError(youTubePlayer: YouTubePlayer, error: PlayerConstants.PlayerError) {}
            override fun onVideoDuration(youTubePlayer: YouTubePlayer, duration: Float) {}
            override fun onVideoLoadedFraction(youTubePlayer: YouTubePlayer, loadedFraction: Float) {}
            override fun onVideoId(youTubePlayer: YouTubePlayer, videoId: String) {}
            override fun onApiChange(youTubePlayer: YouTubePlayer) {}
        })

        onView(withId(R.id.youtube_player_view_native_ui)).perform(scrollTo())

        // Act
        onView(allOf(isDescendantOfA(withId(R.id.youtube_player_view_native_ui)), withId(R.id.fast_rewind_layout))).perform(click10Times) // 10 clicks, should fast rewind by 90 sec
        Thread.sleep(1000) // Wait 1 sec for the fast rewind view to call skipTo()

        // Assert
        playerPlayingCountingIdlingResource.increment()
        onView(withId(R.id.youtube_player_view_native_ui)).perform(scrollTo()) // Used to pause execution while waiting for player to start playing again

        var minDiffPlayerProgress = -1F
        var prevPlayerProgress = -1F
        // Gets the smallest difference between 2 consecutive player progress values
        while (playerProgressQueue.isNotEmpty()) {
            val currentPlayerProgress = playerProgressQueue.poll()!!
            minDiffPlayerProgress = min(minDiffPlayerProgress, (currentPlayerProgress - prevPlayerProgress))
            prevPlayerProgress = currentPlayerProgress
        }

        /**
         * The smallest difference between two consecutive player progress values will be caused by
         * fast rewinding. Hence we can check that to see if fast rewind worked as expected.
         */
        assertTrue("Check if fast rewind was successful", minDiffPlayerProgress < -89)
    }
}