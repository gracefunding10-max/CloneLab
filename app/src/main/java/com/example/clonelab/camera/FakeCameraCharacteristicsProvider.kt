package com.example.clonelab.camera

import android.content.Context
import android.graphics.Rect
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.util.Range
import android.util.Size
import java.lang.reflect.Constructor
import java.lang.reflect.Method

data class FakeCameraMetadata(
    val cameraId: String,
    val facing: Int, // CameraCharacteristics.LENS_FACING_BACK (0) or FRONT (1)
    val sensorOrientation: Int,
    val hardwareLevel: Int,
    val supportedSizes: List<Size>,
    val fpsRanges: List<Range<Int>>,
    val maxDigitalZoom: Float = 4.0f
)

object FakeCameraCharacteristicsProvider {

    private val TAG = "FakeCharacteristics"

    val DEFAULT_BACK_METADATA = FakeCameraMetadata(
        cameraId = "0",
        facing = CameraCharacteristics.LENS_FACING_BACK,
        sensorOrientation = 90,
        hardwareLevel = CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL,
        supportedSizes = listOf(
            Size(1920, 1080),
            Size(1280, 720),
            Size(640, 480),
            Size(320, 240)
        ),
        fpsRanges = listOf(
            Range(15, 30),
            Range(30, 30)
        )
    )

    val DEFAULT_FRONT_METADATA = FakeCameraMetadata(
        cameraId = "1",
        facing = CameraCharacteristics.LENS_FACING_FRONT,
        sensorOrientation = 270,
        hardwareLevel = CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL,
        supportedSizes = listOf(
            Size(1920, 1080),
            Size(1280, 720),
            Size(640, 480)
        ),
        fpsRanges = listOf(
            Range(15, 30),
            Range(30, 30)
        )
    )

    fun getMetadata(cameraId: String): FakeCameraMetadata {
        return if (cameraId == "1") DEFAULT_FRONT_METADATA else DEFAULT_BACK_METADATA
    }

    fun createCharacteristics(context: Context, cameraId: String): CameraCharacteristics {
        CameraRedirectLog.hook(TAG, "Querying CameraCharacteristics for camera ID '$cameraId'")

        // Method 1: Try constructing CameraCharacteristics via CameraMetadataNative reflection
        try {
            val nativeClass = Class.forName("android.hardware.camera2.impl.CameraMetadataNative")
            val nativeCons = nativeClass.getDeclaredConstructor()
            nativeCons.isAccessible = true
            val nativeInstance = nativeCons.newInstance()

            val meta = getMetadata(cameraId)

            // Populate essential keys if set method exists
            try {
                val setMethod = findSetMethod(nativeClass)
                if (setMethod != null) {
                    setMetadataKey(setMethod, nativeInstance, CameraCharacteristics.LENS_FACING, meta.facing)
                    setMetadataKey(setMethod, nativeInstance, CameraCharacteristics.SENSOR_ORIENTATION, meta.sensorOrientation)
                    setMetadataKey(setMethod, nativeInstance, CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL, meta.hardwareLevel)
                    setMetadataKey(
                        setMethod,
                        nativeInstance,
                        CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES,
                        meta.fpsRanges.toTypedArray()
                    )
                    setMetadataKey(
                        setMethod,
                        nativeInstance,
                        CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE,
                        Size(1920, 1080)
                    )
                    setMetadataKey(
                        setMethod,
                        nativeInstance,
                        CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE,
                        Rect(0, 0, 1920, 1080)
                    )
                }
            } catch (t: Throwable) {
                CameraRedirectLog.warn(TAG, "Failed setting individual native keys: ${t.message}")
            }

            val charClass = CameraCharacteristics::class.java
            val charCons = charClass.getDeclaredConstructor(nativeClass)
            charCons.isAccessible = true
            val characteristics = charCons.newInstance(nativeInstance) as CameraCharacteristics
            CameraRedirectLog.info(TAG, "Successfully constructed synthetic CameraCharacteristics for ID $cameraId")
            return characteristics
        } catch (e: Exception) {
            CameraRedirectLog.warn(TAG, "CameraMetadataNative reflection unavailable: ${e.message}")
        }

        // Method 2: Fallback to real CameraManager characteristics if present on host
        try {
            val realManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            if (realManager != null) {
                val list = realManager.cameraIdList
                if (list.isNotEmpty()) {
                    val fallbackId = if (list.contains(cameraId)) cameraId else list[0]
                    return realManager.getCameraCharacteristics(fallbackId)
                }
            }
        } catch (e: Exception) {
            CameraRedirectLog.warn(TAG, "Host CameraManager fallback unavailable: ${e.message}")
        }

        // Method 3: Unsafe constructor allocation or empty mock
        return try {
            val charClass = CameraCharacteristics::class.java
            val constructors = charClass.declaredConstructors
            for (c in constructors) {
                c.isAccessible = true
                if (c.parameterTypes.isEmpty()) {
                    return c.newInstance() as CameraCharacteristics
                }
            }
            throw IllegalStateException("Cannot instantiate CameraCharacteristics")
        } catch (e: Exception) {
            throw RuntimeException("Failed to instantiate fake CameraCharacteristics for camera $cameraId", e)
        }
    }

    private fun findSetMethod(nativeClass: Class<*>): Method? {
        for (m in nativeClass.declaredMethods) {
            if (m.name == "set" && m.parameterTypes.size == 2) {
                m.isAccessible = true
                return m
            }
        }
        return null
    }

    private fun setMetadataKey(method: Method, target: Any, key: Any, value: Any) {
        try {
            method.invoke(target, key, value)
        } catch (_: Exception) {}
    }
}
