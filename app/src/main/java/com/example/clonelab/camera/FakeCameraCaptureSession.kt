package android.hardware.camera2

import android.hardware.camera2.params.OutputConfiguration
import android.os.Handler
import android.os.Looper
import android.view.Surface
import com.example.clonelab.camera.CameraRedirectLog
import com.example.clonelab.camera.frame.FrameSource
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class FakeCameraCaptureSession(
    private val device: FakeCameraDevice,
    private val outputSurfaces: List<Surface>,
    private val frameSource: FrameSource,
    private val targetFps: Int
) : CameraCaptureSession() {

    private val TAG = "FakeCameraSession"
    private val isClosed = AtomicBoolean(false)
    private val sequenceCounter = AtomicInteger(1)
    private var repeatingRequest: CaptureRequest? = null
    private var repeatingCallback: CaptureCallback? = null
    private var repeatingHandler: Handler? = null

    init {
        CameraRedirectLog.hook(
            TAG,
            "CaptureSession created with ${outputSurfaces.size} output Surface(s) (Target FPS: $targetFps)"
        )

        frameSource.setFrameListener(object : FrameSource.FrameListener {
            override fun onFrameDelivered(frameIndex: Long, timestampNs: Long, measuredFps: Float) {
                dispatchFrameCallbacks(frameIndex, timestampNs)
            }
        })
    }

    override fun getDevice(): CameraDevice = device

    override fun prepare(surface: Surface) {
        checkNotClosed()
    }

    override fun finalizeOutputConfigurations(outputConfigs: MutableList<OutputConfiguration>) {
        checkNotClosed()
    }

    override fun setRepeatingRequest(
        request: CaptureRequest,
        listener: CaptureCallback?,
        handler: Handler?
    ): Int {
        checkNotClosed()
        repeatingRequest = request
        repeatingCallback = listener
        repeatingHandler = handler ?: Handler(Looper.getMainLooper())

        val seqId = sequenceCounter.getAndIncrement()
        CameraRedirectLog.hook(
            TAG,
            "setRepeatingRequest: Seq #$seqId. Starting FrameSource rendering to surfaces."
        )

        frameSource.start(outputSurfaces, targetFps)
        return seqId
    }

    override fun setRepeatingBurst(
        requests: MutableList<CaptureRequest>,
        listener: CaptureCallback?,
        handler: Handler?
    ): Int {
        checkNotClosed()
        val primary = requests.firstOrNull() ?: return -1
        return setRepeatingRequest(primary, listener, handler)
    }

    override fun stopRepeating() {
        checkNotClosed()
        CameraRedirectLog.hook(TAG, "stopRepeating called")
        repeatingRequest = null
        repeatingCallback = null
        frameSource.pause()
    }

    override fun abortCaptures() {
        checkNotClosed()
        CameraRedirectLog.hook(TAG, "abortCaptures called")
        frameSource.pause()
    }

    override fun capture(
        request: CaptureRequest,
        listener: CaptureCallback?,
        handler: Handler?
    ): Int {
        checkNotClosed()
        val seqId = sequenceCounter.getAndIncrement()
        CameraRedirectLog.hook(TAG, "capture (single): Seq #$seqId")

        val targetHandler = handler ?: Handler(Looper.getMainLooper())
        targetHandler.post {
            listener?.onCaptureStarted(
                this,
                request,
                System.nanoTime(),
                seqId.toLong()
            )
            createDummyCaptureResult()?.let { result ->
                listener?.onCaptureCompleted(this, request, result)
            }
        }
        return seqId
    }

    override fun captureBurst(
        requests: MutableList<CaptureRequest>,
        listener: CaptureCallback?,
        handler: Handler?
    ): Int {
        checkNotClosed()
        var lastSeq = -1
        for (req in requests) {
            lastSeq = capture(req, listener, handler)
        }
        return lastSeq
    }

    override fun close() {
        if (isClosed.compareAndSet(false, true)) {
            CameraRedirectLog.hook(TAG, "CaptureSession closed. Stopping FrameSource.")
            frameSource.stop()
            repeatingCallback = null
            repeatingRequest = null
        }
    }

    override fun isReprocessable(): Boolean = false

    override fun getInputSurface(): Surface? = null

    private fun dispatchFrameCallbacks(frameIndex: Long, timestampNs: Long) {
        val req = repeatingRequest ?: return
        val cb = repeatingCallback ?: return
        val handler = repeatingHandler ?: return

        handler.post {
            try {
                cb.onCaptureStarted(this, req, timestampNs, frameIndex)
                createDummyCaptureResult()?.let { res ->
                    cb.onCaptureCompleted(this, req, res)
                }
            } catch (e: Exception) {
                CameraRedirectLog.warn(TAG, "Callback invocation warning: ${e.message}")
            }
        }
    }

    private fun createDummyCaptureResult(): TotalCaptureResult? {
        return try {
            val nativeClass = Class.forName("android.hardware.camera2.impl.CameraMetadataNative")
            val nativeCons = nativeClass.getDeclaredConstructor()
            nativeCons.isAccessible = true
            val nativeObj = nativeCons.newInstance()

            val resClass = TotalCaptureResult::class.java
            val constructors = resClass.declaredConstructors
            for (c in constructors) {
                c.isAccessible = true
                if (c.parameterTypes.size == 2 && c.parameterTypes[0] == nativeClass) {
                    return c.newInstance(nativeObj, sequenceCounter.get()) as TotalCaptureResult
                }
                if (c.parameterTypes.isEmpty()) {
                    return c.newInstance() as TotalCaptureResult
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun checkNotClosed() {
        if (isClosed.get()) {
            throw IllegalStateException("CameraCaptureSession is already closed")
        }
    }
}
