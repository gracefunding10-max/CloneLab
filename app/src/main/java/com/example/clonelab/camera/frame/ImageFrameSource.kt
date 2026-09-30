package com.example.clonelab.camera.frame

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.view.Surface
import com.example.clonelab.camera.CameraRedirectLog
import java.io.InputStream
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

class ImageFrameSource(
    private val context: Context,
    private val imageUri: Uri? = null,
    private val staticBitmap: Bitmap? = null
) : FrameSource {

    private val TAG = "ImageFrameSource"
    private val surfaces = CopyOnWriteArrayList<Surface>()
    private var handlerThread: HandlerThread? = null
    private var loopHandler: Handler? = null

    private val isRunningFlag = AtomicBoolean(false)
    private val isPausedFlag = AtomicBoolean(false)

    private var targetFps = 30
    private var frameIntervalNs = 1_000_000_000L / 30
    private var frameIntervalMs = 1000L / 30

    private var decodedBitmap: Bitmap? = null
    private var frameListener: FrameSource.FrameListener? = null

    private var frameCounter = 0L
    private var lastFpsCalculationTime = 0L
    private var frameCountInWindow = 0
    private var currentFps = 0f

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    override fun start(surfaces: List<Surface>, targetFps: Int) {
        if (isRunningFlag.get()) {
            stop()
        }

        this.surfaces.clear()
        this.surfaces.addAll(surfaces)
        this.targetFps = targetFps.coerceIn(10, 60)
        this.frameIntervalNs = 1_000_000_000L / this.targetFps
        this.frameIntervalMs = (1000L / this.targetFps).coerceAtLeast(1)

        loadBitmap()

        val thread = HandlerThread("CloneLab-ImageSourceThread")
        thread.start()
        handlerThread = thread
        val handler = Handler(thread.looper)
        loopHandler = handler

        isRunningFlag.set(true)
        isPausedFlag.set(false)
        frameCounter = 0L
        frameCountInWindow = 0
        lastFpsCalculationTime = SystemClock.elapsedRealtime()

        CameraRedirectLog.info(
            TAG,
            "Started ImageFrameSource with ${surfaces.size} surface(s) at $targetFps FPS (interval ${frameIntervalMs}ms)"
        )

        handler.post(frameRunnable)
    }

    private fun loadBitmap() {
        if (decodedBitmap != null && !decodedBitmap!!.isRecycled) {
            return
        }
        if (staticBitmap != null && !staticBitmap.isRecycled) {
            decodedBitmap = staticBitmap
            return
        }

        if (imageUri != null) {
            try {
                var stream: InputStream? = context.contentResolver.openInputStream(imageUri)
                val options = BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                decodedBitmap = BitmapFactory.decodeStream(stream, null, options)
                stream?.close()
                CameraRedirectLog.info(TAG, "Decoded image bitmap: ${decodedBitmap?.width}x${decodedBitmap?.height}")
            } catch (e: Exception) {
                CameraRedirectLog.error(TAG, "Failed to decode image from uri: ${e.message}")
            }
        }

        if (decodedBitmap == null) {
            decodedBitmap = createFallbackBitmap()
        }
    }

    private fun createFallbackBitmap(): Bitmap {
        val width = 1280
        val height = 720
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.parseColor("#0F172A"))

        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#38BDF8")
            textSize = 42f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("CloneLab Camera Feed", width / 2f, height / 2f - 40, p)

        p.color = Color.parseColor("#94A3B8")
        p.textSize = 28f
        canvas.drawText("Static Image Redirection Active (1280x720)", width / 2f, height / 2f + 20, p)
        return bmp
    }

    private val frameRunnable = object : Runnable {
        override fun run() {
            if (!isRunningFlag.get()) return

            val startNano = System.nanoTime()

            if (!isPausedFlag.get()) {
                deliverFrame()
            }

            val elapsedNano = System.nanoTime() - startNano
            val delayNs = frameIntervalNs - elapsedNano
            val delayMs = (delayNs / 1_000_000L).coerceAtLeast(0)

            loopHandler?.postDelayed(this, delayMs)
        }
    }

    private fun deliverFrame() {
        val bmp = decodedBitmap ?: return
        if (bmp.isRecycled) return

        val nowMs = SystemClock.elapsedRealtime()
        frameCountInWindow++
        val windowElapsed = nowMs - lastFpsCalculationTime
        if (windowElapsed >= 1000L) {
            currentFps = (frameCountInWindow * 1000f) / windowElapsed
            frameCountInWindow = 0
            lastFpsCalculationTime = nowMs
            CameraRedirectLog.cadence(
                TAG,
                "Cadence check: delivered FPS = ${"%.1f".format(currentFps)} (target: $targetFps FPS)"
            )
        }

        frameCounter++
        val frameTimestampNs = System.nanoTime()

        for (surface in surfaces) {
            if (!surface.isValid) continue
            var canvas: Canvas? = null
            try {
                canvas = try {
                    surface.lockHardwareCanvas()
                } catch (t: Throwable) {
                    surface.lockCanvas(null)
                }

                if (canvas != null) {
                    drawBitmapToCanvas(canvas, bmp)
                    surface.unlockCanvasAndPost(canvas)
                }
            } catch (e: Exception) {
                // Surface might be releasing or recreating
                CameraRedirectLog.warn(TAG, "Render to surface warning: ${e.message}")
            }
        }

        frameListener?.onFrameDelivered(frameCounter, frameTimestampNs, currentFps)
    }

    private fun drawBitmapToCanvas(canvas: Canvas, bitmap: Bitmap) {
        val canvasWidth = canvas.width.toFloat()
        val canvasHeight = canvas.height.toFloat()

        if (canvasWidth <= 0 || canvasHeight <= 0) return

        canvas.drawColor(Color.BLACK)

        val srcRect = Rect(0, 0, bitmap.width, bitmap.height)
        val srcRatio = bitmap.width.toFloat() / bitmap.height.toFloat()
        val dstRatio = canvasWidth / canvasHeight

        val dstRect: RectF = if (srcRatio > dstRatio) {
            // Bitmap is wider than canvas
            val targetHeight = canvasWidth / srcRatio
            val top = (canvasHeight - targetHeight) / 2f
            RectF(0f, top, canvasWidth, top + targetHeight)
        } else {
            // Bitmap is taller than canvas
            val targetWidth = canvasHeight * srcRatio
            val left = (canvasWidth - targetWidth) / 2f
            RectF(left, 0f, left + targetWidth, canvasHeight)
        }

        canvas.drawBitmap(bitmap, srcRect, dstRect, paint)
    }

    override fun stop() {
        isRunningFlag.set(false)
        loopHandler?.removeCallbacks(frameRunnable)
        handlerThread?.quitSafely()
        handlerThread = null
        loopHandler = null
        surfaces.clear()
        CameraRedirectLog.info(TAG, "Stopped ImageFrameSource")
    }

    override fun isRunning(): Boolean = isRunningFlag.get()

    override fun pause() {
        isPausedFlag.set(true)
        CameraRedirectLog.info(TAG, "Paused ImageFrameSource (app backgrounded)")
    }

    override fun resume() {
        isPausedFlag.set(false)
        lastFpsCalculationTime = SystemClock.elapsedRealtime()
        frameCountInWindow = 0
        CameraRedirectLog.info(TAG, "Resumed ImageFrameSource (app foregrounded)")
    }

    override fun addSurface(surface: Surface) {
        if (!surfaces.contains(surface)) {
            surfaces.add(surface)
            CameraRedirectLog.info(TAG, "Added surface: $surface (total ${surfaces.size})")
        }
    }

    override fun removeSurface(surface: Surface) {
        surfaces.remove(surface)
        CameraRedirectLog.info(TAG, "Removed surface: $surface (remaining ${surfaces.size})")
    }

    override fun setFrameListener(listener: FrameSource.FrameListener?) {
        this.frameListener = listener
    }
}
