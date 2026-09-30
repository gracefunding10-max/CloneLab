package android.hardware.camera2

import android.content.Context
import android.hardware.camera2.params.InputConfiguration
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.os.Handler
import android.os.Looper
import android.view.Surface
import com.example.clonelab.camera.CameraRedirectLog
import com.example.clonelab.camera.frame.FrameSource
import java.util.concurrent.atomic.AtomicBoolean

class FakeCameraDevice(
    private val context: Context,
    private val cameraId: String,
    private val frameSource: FrameSource,
    private val targetFps: Int
) : CameraDevice() {

    private val TAG = "FakeCameraDevice"
    private val isClosed = AtomicBoolean(false)
    private var activeSession: FakeCameraCaptureSession? = null

    override fun getId(): String = cameraId

    override fun createCaptureSession(
        outputs: MutableList<Surface>,
        callback: CameraCaptureSession.StateCallback,
        handler: Handler?
    ) {
        checkNotClosed()
        CameraRedirectLog.hook(
            TAG,
            "createCaptureSession requested on Camera $cameraId with ${outputs.size} Surface(s)"
        )

        validateSurfaces(outputs)

        val targetHandler = handler ?: Handler(Looper.getMainLooper())
        val session = FakeCameraCaptureSession(
            device = this,
            outputSurfaces = outputs.toList(),
            frameSource = frameSource,
            targetFps = targetFps
        )
        activeSession = session

        targetHandler.post {
            callback.onConfigured(session)
        }
    }

    override fun createCaptureSessionByOutputConfigurations(
        outputConfigurations: MutableList<OutputConfiguration>,
        callback: CameraCaptureSession.StateCallback,
        handler: Handler?
    ) {
        val surfaces = mutableListOf<Surface>()
        for (config in outputConfigurations) {
            config.surface?.let { surfaces.add(it) }
        }
        createCaptureSession(surfaces, callback, handler)
    }

    override fun createCaptureSession(config: SessionConfiguration) {
        val surfaces = mutableListOf<Surface>()
        for (out in config.outputConfigurations) {
            out.surface?.let { surfaces.add(it) }
        }
        val executor = config.executor
        val callback = config.stateCallback

        val session = FakeCameraCaptureSession(
            device = this,
            outputSurfaces = surfaces,
            frameSource = frameSource,
            targetFps = targetFps
        )
        activeSession = session

        executor.execute {
            callback.onConfigured(session)
        }
    }

    override fun createConstrainedHighSpeedCaptureSession(
        outputs: MutableList<Surface>,
        callback: CameraCaptureSession.StateCallback,
        handler: Handler?
    ) {
        createCaptureSession(outputs, callback, handler)
    }

    override fun createReprocessableCaptureSession(
        inputConfig: InputConfiguration,
        outputs: MutableList<Surface>,
        callback: CameraCaptureSession.StateCallback,
        handler: Handler?
    ) {
        createCaptureSession(outputs, callback, handler)
    }

    override fun createReprocessableCaptureSessionByConfigurations(
        inputConfig: InputConfiguration,
        outputs: MutableList<OutputConfiguration>,
        callback: CameraCaptureSession.StateCallback,
        handler: Handler?
    ) {
        createCaptureSessionByOutputConfigurations(outputs, callback, handler)
    }

    override fun createReprocessCaptureRequest(inputResult: TotalCaptureResult): CaptureRequest.Builder {
        return createCaptureRequest(TEMPLATE_PREVIEW)
    }

    override fun createCaptureRequest(templateType: Int): CaptureRequest.Builder {
        checkNotClosed()
        CameraRedirectLog.hook(TAG, "createCaptureRequest template=$templateType")

        try {
            val nativeClass = Class.forName("android.hardware.camera2.impl.CameraMetadataNative")
            val nativeCons = nativeClass.getDeclaredConstructor()
            nativeCons.isAccessible = true
            val nativeObj = nativeCons.newInstance()

            val builderClass = CaptureRequest.Builder::class.java
            val constructors = builderClass.declaredConstructors
            for (c in constructors) {
                c.isAccessible = true
                if (c.parameterTypes.size >= 2 && c.parameterTypes[0] == nativeClass) {
                    return c.newInstance(nativeObj, false, -1, cameraId, null) as CaptureRequest.Builder
                }
            }
        } catch (e: Exception) {
            CameraRedirectLog.warn(TAG, "CaptureRequest.Builder native reflection failed: ${e.message}")
        }

        val constructors = CaptureRequest.Builder::class.java.declaredConstructors
        for (c in constructors) {
            c.isAccessible = true
            try {
                val args = arrayOfNulls<Any>(c.parameterTypes.size)
                return c.newInstance(*args) as CaptureRequest.Builder
            } catch (_: Exception) {}
        }

        throw CameraAccessException(
            CameraAccessException.CAMERA_ERROR,
            "Failed to allocate CaptureRequest.Builder"
        )
    }

    private fun validateSurfaces(surfaces: List<Surface>) {
        if (surfaces.isEmpty()) {
            throw IllegalArgumentException("Cannot create capture session with 0 surfaces")
        }
        for (s in surfaces) {
            if (!s.isValid) {
                CameraRedirectLog.warn(TAG, "Supplied Surface is not valid yet, will await surface readiness")
            }
        }
    }

    override fun close() {
        if (isClosed.compareAndSet(false, true)) {
            CameraRedirectLog.hook(TAG, "CameraDevice $cameraId closed.")
            activeSession?.close()
            activeSession = null
        }
    }

    private fun checkNotClosed() {
        if (isClosed.get()) {
            throw IllegalStateException("CameraDevice $cameraId is already closed")
        }
    }
}
