package com.example.clonelab.camera

import android.content.Context
import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.SurfaceHolder
import com.example.clonelab.camera.frame.FrameSource
import com.example.clonelab.camera.frame.PatternFrameSource
import java.util.concurrent.atomic.AtomicBoolean

class FakeCamera private constructor(
    private val cameraId: Int,
    private var frameSource: FrameSource
) {

    private val TAG = "FakeCamera-Legacy"
    private var previewSurface: Surface? = null
    private var surfaceTexture: SurfaceTexture? = null
    private var previewCallback: PreviewCallback? = null
    private val isPreviewing = AtomicBoolean(false)
    private var isReleased = false

    interface PreviewCallback {
        fun onPreviewFrame(data: ByteArray, camera: FakeCamera)
    }

    data class Size(val width: Int, val height: Int)

    class Parameters {
        var previewWidth: Int = 1280
        var previewHeight: Int = 720
        var focusMode: String = "auto"
        var flashMode: String = "off"

        fun getPreviewSize(): Size = Size(previewWidth, previewHeight)
        fun setPreviewSize(w: Int, h: Int) {
            previewWidth = w
            previewHeight = h
        }

        fun getSupportedPreviewSizes(): List<Size> = listOf(
            Size(1920, 1080),
            Size(1280, 720),
            Size(640, 480)
        )
    }

    private val parameters = Parameters()

    companion object {
        fun open(cameraId: Int = 0, frameSource: FrameSource? = null): FakeCamera {
            CameraRedirectLog.hook("FakeCamera-Legacy", "Legacy Camera.open(id=$cameraId) called")
            val source = frameSource ?: PatternFrameSource("COLOR_BARS")
            return FakeCamera(cameraId, source)
        }
    }

    fun setPreviewDisplay(holder: SurfaceHolder) {
        checkNotReleased()
        CameraRedirectLog.hook(TAG, "setPreviewDisplay(holder)")
        this.previewSurface = holder.surface
    }

    fun setPreviewTexture(texture: SurfaceTexture) {
        checkNotReleased()
        CameraRedirectLog.hook(TAG, "setPreviewTexture(surfaceTexture)")
        this.surfaceTexture = texture
        this.previewSurface = Surface(texture)
    }

    fun setPreviewCallback(callback: PreviewCallback?) {
        checkNotReleased()
        this.previewCallback = callback
    }

    fun startPreview() {
        checkNotReleased()
        if (isPreviewing.compareAndSet(false, true)) {
            CameraRedirectLog.hook(TAG, "startPreview() started")
            val surface = previewSurface
            if (surface != null && surface.isValid) {
                frameSource.start(listOf(surface), 30)
            } else {
                CameraRedirectLog.warn(TAG, "startPreview called before valid surface was attached")
            }
        }
    }

    fun stopPreview() {
        checkNotReleased()
        if (isPreviewing.compareAndSet(true, false)) {
            CameraRedirectLog.hook(TAG, "stopPreview() stopped")
            frameSource.pause()
        }
    }

    fun release() {
        if (!isReleased) {
            isReleased = true
            isPreviewing.set(false)
            CameraRedirectLog.hook(TAG, "release() called on legacy camera")
            frameSource.stop()
            previewSurface?.release()
            previewSurface = null
        }
    }

    fun getParameters(): Parameters = parameters

    fun setParameters(params: Parameters) {
        // Apply parameters
    }

    private fun checkNotReleased() {
        if (isReleased) {
            throw RuntimeException("Legacy FakeCamera is already released")
        }
    }
}
