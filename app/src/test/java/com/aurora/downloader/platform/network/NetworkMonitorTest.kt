package com.aurora.downloader.platform.network

import android.content.Context
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * A monitor that crashes or throws NPE inside a callback would take the whole
 * engine down with it. Every probe path must degrade to a state, not an
 * exception.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NetworkMonitorTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
    }

    @Test
    fun currentReturnsAStateEvenWithoutActiveNetwork() {
        // Robolectric has no active network by default.
        val state = NetworkMonitor.current(context)
        assertNotNull(state)
        // And it must report not-online rather than crash.
        assertFalse("expected offline in a no-network environment", state.online)
    }

    @Test
    fun unknownStateIsSafeToConsume() {
        val unknown = NetworkMonitor.State.UNKNOWN
        assertNotNull(unknown)
        assertFalse(unknown.online)
        assertFalse(unknown.unmetered)
    }

    @Test
    fun stateIsDataClassComparable() {
        // distinctUntilChanged depends on equality working.
        val a = NetworkMonitor.State(true, false)
        val b = NetworkMonitor.State(true, false)
        assert(a == b)
        assert(a.hashCode() == b.hashCode())
    }
}
