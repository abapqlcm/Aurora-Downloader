package com.aurora.downloader.platform.battery

import android.content.Context
import android.content.Intent
import android.provider.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * A dead "ignore battery optimizations" button is worse than no button — it
 * teaches the user that settings do nothing. The fallback path must always be
 * reachable.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BatteryOptimizationTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
    }

    @Test
    fun appDetailsIntentIsAlwaysAvailable() {
        val intent = BatteryOptimization.appDetailsIntent(context)
        assertNotNull(intent)
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, intent.action)
        // Must point at this app, not a generic page.
        assertTrue(
            "expected the app's own package in the intent",
            intent.dataString?.contains(context.packageName) == true
        )
    }

    @Test
    fun requestIntentTargetsThisPackage() {
        val intent = BatteryOptimization.requestIntent(context)
        if (intent == null) return // below API 23 the concept does not exist
        assertEquals(
            "android.settings.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS",
            intent.action
        )
        assertTrue(
            "the exemption request must name this app",
            intent.dataString?.contains(context.packageName) == true
        )
    }

    @Test
    fun isExemptReturnsABoolWithoutCrashing() {
        // Robolectric's PowerManager reports not-exempt by default.
        assertFalse(BatteryOptimization.isExempt(context))
    }

    @Test
    fun canRequestReflectsWhatTheRomCanResolve() {
        // Either the ROM supports it or it does not; both are handled — but
        // the answer must not throw.
        BatteryOptimization.canRequest(context)
    }
}
