package com.example.clonelab.camera

import android.content.Context
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.FakeCameraDevice
import android.os.Handler
import android.os.Looper
import com.example.clonelab.camera.frame.FrameSource
import com.example.clonelab.camera.frame.FrameSourceFactory
import com.example.clonelab.data.ClonedApp
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executor

class FakeCameraManager(
    private val context: Context,
    private val targetApp: ClonedApp,
    private var customFrameSource: FrameSource? = null
) {

    private val TAG = "FakeCameraManager"
    private val availabilityCallbacks = CopyOnWriteArrayList<CameraManager.AvailabilityCallback>()
    private var openDevice: FakeCameraDevice? = null

    fun getCameraIdList(): Array<String> {
        CameraRedirectLog.hook(TAG, "getCameraIdList() -> ['0', '1']")
        return arrayOf("0", "1")
    }

    fun getCameraCharacteristics(cameraId: String): CameraCharacteristics {
        CameraRedirectLog.hook(TAG, "getCameraCharacteristics(cameraId=$cameraId)")
        return FakeCameraCharacteristicsProvider.createCharacteristics(context, cameraId)
    }

    fun openCamera(
        cameraId: String,
        callback: CameraDevice.StateCallback,
        handler: Handler?
    ) {
        CameraRedirectLog.hook(TAG, "openCamera(id=$cameraId) called")

        val targetHandler = handler ?: Handler(Looper.getMainLooper())
        val frameSource = customFrameSource ?: FrameSourceFactory.create(context, targetApp)

        val device = FakeCameraDevice(
            context = context,
            cameraId = cameraId,
            frameSource = frameSource,
            targetFps = targetApp.targetFps
        )
        openDevice = device

        targetHandler.post {
            try {
                callback.onOpened(device)
                notifyCameraAvailable(cameraId, false)
            } catch (e: Exception) {
                CameraRedirectLog.error(TAG, "Callback onOpened exception: ${e.message}")
            }
        }
    }

    fun openCamera(
        cameraId: String,
        executor: Executor,
        callback: CameraDevice.StateCallback
    ) {
        CameraRedirectLog.hook(TAG, "openCamera(id=$cameraId, executor) called")
        val frameSource = customFrameSource ?: FrameSourceFactory.create(context, targetApp)

        val device = FakeCameraDevice(
            context = context,
            cameraId = cameraId,
            frameSource = frameSource,
            targetFps = targetApp.targetFps
        )
        openDevice = device

        executor.execute {
            try {
                callback.onOpened(device)
                notifyCameraAvailable(cameraId, false)
            } catch (e: Exception) {
                CameraRedirectLog.error(TAG, "Executor onOpened exception: ${e.message}")
            }
        }
    }

    fun registerAvailabilityCallback(callback: CameraManager.AvailabilityCallback, handler: Handler?) {
        availabilityCallbacks.add(callback)
        val targetHandler = handler ?: Handler(Looper.getMainLooper())
        targetHandler.post {
            callback.onCameraAvailable("0")
            callback.onCameraAvailable("1")
        }
    }

    fun registerAvailabilityCallback(executor: Executor, callback: CameraManager.AvailabilityCallback) {
        availabilityCallbacks.add(callback)
        executor.execute {
            callback.onCameraAvailable("0")
            callback.onCameraAvailable("1")
        }
    }

    fun unregisterAvailabilityCallback(callback: CameraManager.AvailabilityCallback) {
        availabilityCallbacks.remove(callback)
    }

    private fun notifyCameraAvailable(cameraId: String, available: Boolean) {
        val handler = Handler(Looper.getMainLooper())
        for (cb in availabilityCallbacks) {
            handler.post {
                if (available) cb.onCameraAvailable(cameraId) else cb.onCameraUnavailable(cameraId)
            }
        }
    }

    fun closeActiveDevice() {
        openDevice?.close()
        openDevice = null
    }
}
