package com.example

import com.example.clonelab.camera.frame.PatternFrameSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FrameSourceCadenceTest {

    @Test
    fun testFrameSourceLifecycle() {
        val patternSource = PatternFrameSource("COLOR_BARS")
        assertFalse(patternSource.isRunning())

        // Lifecycle testing
        patternSource.start(emptyList(), 30)
        assertTrue(patternSource.isRunning())

        patternSource.pause()
        assertTrue(patternSource.isRunning())

        patternSource.resume()
        assertTrue(patternSource.isRunning())

        patternSource.stop()
        assertFalse(patternSource.isRunning())
    }

    @Test
    fun testTargetCadenceCalculations() {
        val targetFps = 30
        val expectedIntervalNs = 1_000_000_000L / targetFps
        val expectedIntervalMs = 1000L / targetFps

        assertEquals(33_333_333L, expectedIntervalNs)
        assertEquals(33L, expectedIntervalMs)

        // 60 FPS
        val targetFps60 = 60
        assertEquals(16_666_666L, 1_000_000_000L / targetFps60)
        assertEquals(16L, 1000L / targetFps60)
    }
}
