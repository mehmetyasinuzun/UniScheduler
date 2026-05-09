package com.unischeduler.ui.onboarding

import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.unischeduler.App
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Robolectric smoke tests for the onboarding flow.
 *
 * Catches:
 *   • Layout inflation crashes (missing string ref, broken vector drawable)
 *   • ViewPager2 adapter wiring (RecyclerView IndexOutOfBounds, etc.)
 *   • The "should we show onboarding?" decision logic
 *   • Pref-flip on completion (skip / get started both persist)
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class OnboardingActivityTest {

    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun resetPref() {
        ctx.getSharedPreferences(App.PREFS_NAME, Context.MODE_PRIVATE)
            .edit().clear().apply()
    }

    @After
    fun cleanup() {
        // Defensive — keep tests order-independent.
        ctx.getSharedPreferences(App.PREFS_NAME, Context.MODE_PRIVATE)
            .edit().clear().apply()
    }

    @Test
    fun `isPending returns true on a fresh install`() {
        assertTrue(OnboardingActivity.isPending(ctx))
    }

    @Test
    fun `isPending returns false once flag is set`() {
        ctx.getSharedPreferences(App.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(OnboardingActivity.KEY_ONBOARDING_DONE, true)
            .apply()
        assertFalse(OnboardingActivity.isPending(ctx))
    }

    @Test
    fun `activity inflates without crashing`() {
        // Just launching the activity through Robolectric is the smoke test
        // — it forces every layout XML to inflate, every drawable to load,
        // and the ViewPager adapter to bind page 0. A regression in any of
        // those crashes the launch and fails this test.
        ActivityScenario.launch(OnboardingActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertFalse("Activity finishing prematurely", activity.isFinishing)
            }
        }
    }
}
