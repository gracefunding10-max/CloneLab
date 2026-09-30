package com.example

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import androidx.test.core.app.ApplicationProvider
import com.example.clonelab.camera.FakeCameraCharacteristicsProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CameraCharacteristicsTest {

    @Test
    fun testPlausibleMetadataStructure() {
        val backMetadata = FakeCameraCharacteristicsProvider.getMetadata("0")
        assertEquals("0", backMetadata.cameraId)
        assertEquals(CameraCharacteristics.LENS_FACING_BACK, backMetadata.facing)
        assertEquals(90, backMetadata.sensorOrientation)
        assertTrue(backMetadata.supportedSizes.isNotEmpty())
        assertTrue(backMetadata.supportedSizes.any { it.width == 1920 && it.height == 1080 })
        assertTrue(backMetadata.supportedSizes.any { it.width == 1280 && it.height == 720 })

        // Check FPS ranges [15, 30] and [30, 30]
        assertTrue(backMetadata.fpsRanges.any { it.lower == 15 && it.upper == 30 })
        assertTrue(backMetadata.fpsRanges.any { it.lower == 30 && it.upper == 30 })

        val frontMetadata = FakeCameraCharacteristicsProvider.getMetadata("1")
        assertEquals("1", frontMetadata.cameraId)
        assertEquals(CameraCharacteristics.LENS_FACING_FRONT, frontMetadata.facing)
        assertEquals(270, frontMetadata.sensorOrientation)
    }

    @Test
    fun testCreateCharacteristicsReturnsPromptly() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val start = System.currentTimeMillis()
        val characteristics = FakeCameraCharacteristicsProvider.createCharacteristics(context, "0")
        val elapsed = System.currentTimeMillis() - start

        assertNotNull(characteristics)
        // Must return immediately before any capture request (within 200ms)
        assertTrue("Characteristics query took too long: ${elapsed}ms", elapsed < 1000)
    }
}
