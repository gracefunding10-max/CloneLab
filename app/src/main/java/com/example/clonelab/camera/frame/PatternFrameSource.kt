package com.example.clonelab.camera.frame

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.view.Surface
import com.example.clonelab.camera.CameraRedirectLog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

class PatternFrameSource(
    val patternType: String = "COLOR_BARS"
) : FrameSource {

    private val TAG = "PatternFrameSource"
    private val surfaces = CopyOnWriteArrayList<Surface>()
    private var handlerThread: HandlerThread? = null
    private var loopHandler: Handler? = null

    private val isRunningFlag = AtomicBoolean(false)
    private val isPausedFlag = AtomicBoolean(false)

    private var targetFps = 30
    private var frameIntervalNs = 1_000_000_000L / 30
    private var frameIntervalMs = 1000L / 30

    private var frameCounter = 0L
    private var lastFpsCalculationTime = 0L
    private var frameCountInWindow = 0
    private var currentFps = 0f

    private var frameListener: FrameSource.FrameListener? = null
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    // Animation states
    private var ballX = 100f
    private var ballY = 100f
    private var ballVx = 8f
    private var ballVy = 6f

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 36f
        isFakeBoldText = true
    }

    override fun start(surfaces: List<Surface>, targetFps: Int) {
        if (isRunningFlag.get()) {
            stop()
        }

        this.surfaces.clear()
        this.surfaces.addAll(surfaces)
        this.targetFps = targetFps.coerceIn(10, 60)
        this.frameIntervalNs = 1_000_000_000L / this.targetFps
        this.frameIntervalMs = (1000L / this.targetFps).coerceAtLeast(1)

        val thread = HandlerThread("CloneLab-PatternSourceThread")
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
            "Started PatternFrameSource ($patternType) on ${surfaces.size} surface(s) at $targetFps FPS"
        )

        handler.post(frameRunnable)
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
        val nowMs = SystemClock.elapsedRealtime()
        frameCountInWindow++
        val windowElapsed = nowMs - lastFpsCalculationTime
        if (windowElapsed >= 1000L) {
            currentFps = (frameCountInWindow * 1000f) / windowElapsed
            frameCountInWindow = 0
            lastFpsCalculationTime = nowMs
            CameraRedirectLog.cadence(
                TAG,
                "[$patternType] Cadence: ${"%.1f".format(currentFps)} FPS (Target: $targetFps FPS)"
            )
        }

        frameCounter++
        val frameTimestampNs = System.nanoTime()

        for (surface in surfaces) {
            if (!surface.isValid) continue
            try {
                val canvas = try {
                    surface.lockHardwareCanvas()
                } catch (t: Throwable) {
                    surface.lockCanvas(null)
                }

                if (canvas != null) {
                    when (patternType) {
                        "FACE_TARGET" -> drawFaceTargetPattern(canvas)
                        "CALIBRATION_GRID" -> drawCalibrationGrid(canvas)
                        else -> drawColorBarsPattern(canvas)
                    }
                    surface.unlockCanvasAndPost(canvas)
                }
            } catch (e: Exception) {
                CameraRedirectLog.warn(TAG, "Frame render warning: ${e.message}")
            }
        }

        frameListener?.onFrameDelivered(frameCounter, frameTimestampNs, currentFps)
    }

    private fun drawColorBarsPattern(canvas: Canvas) {
        val w = canvas.width.toFloat()
        val h = canvas.height.toFloat()
        if (w <= 0 || h <= 0) return

        val barColors = intArrayOf(
            Color.rgb(192, 192, 192), // Gray
            Color.rgb(192, 192, 0),   // Yellow
            Color.rgb(0, 192, 192),   // Cyan
            Color.rgb(0, 192, 0),     // Green
            Color.rgb(192, 0, 192),   // Magenta
            Color.rgb(192, 0, 0),     // Red
            Color.rgb(0, 0, 192)      // Blue
        )

        val barWidth = w / barColors.size
        val topBarHeight = h * 0.72f

        for (i in barColors.indices) {
            paint.color = barColors[i]
            canvas.drawRect(i * barWidth, 0f, (i + 1) * barWidth, topBarHeight, paint)
        }

        // Lower portion - dark / signal bars
        paint.color = Color.BLACK
        canvas.drawRect(0f, topBarHeight, w, h, paint)

        // Lower boxes
        val bottomSectionWidth = w / 4f
        paint.color = Color.rgb(0, 33, 71) // Navy
        canvas.drawRect(0f, topBarHeight, bottomSectionWidth, h, paint)

        paint.color = Color.WHITE
        canvas.drawRect(bottomSectionWidth, topBarHeight, bottomSectionWidth * 2, h, paint)

        paint.color = Color.rgb(50, 0, 106) // Deep purple
        canvas.drawRect(bottomSectionWidth * 2, topBarHeight, bottomSectionWidth * 3, h, paint)

        paint.color = Color.rgb(19, 19, 19) // Near black
        canvas.drawRect(bottomSectionWidth * 3, topBarHeight, w, h, paint)

        // Overlay Telemetry HUD
        paint.color = Color.argb(190, 0, 0, 0)
        canvas.drawRoundRect(RectF(24f, 24f, w - 24f, 180f), 16f, 16f, paint)

        textPaint.color = Color.CYAN
        textPaint.textSize = 34f
        canvas.drawText("CLONELAB CAMERA FEED [SYNTHETIC SMPTE]", 44f, 70f, textPaint)

        textPaint.color = Color.WHITE
        textPaint.textSize = 28f
        val timecode = timeFormat.format(Date())
        canvas.drawText("TC: $timecode | Frame #$frameCounter | Target: ${targetFps}fps", 44f, 114f, textPaint)

        textPaint.color = Color.YELLOW
        canvas.drawText("Current Cadence: ${"%.1f".format(currentFps)} FPS | Res: ${w.toInt()}x${h.toInt()}", 44f, 154f, textPaint)

        // Blinking indicator
        paint.color = if (frameCounter % 20 < 10) Color.GREEN else Color.RED
        canvas.drawCircle(w - 60f, 60f, 16f, paint)
    }

    private fun drawFaceTargetPattern(canvas: Canvas) {
        val w = canvas.width.toFloat()
        val h = canvas.height.toFloat()
        if (w <= 0 || h <= 0) return

        // Background
        canvas.drawColor(Color.parseColor("#1E293B"))

        val cx = w / 2f
        val cy = h / 2f

        // Head silhouette / oval
        paint.color = Color.parseColor("#FED7AA") // Skin tone
        canvas.drawOval(RectF(cx - 160f, cy - 240f, cx + 160f, cy + 180f), paint)

        // Hair
        paint.color = Color.parseColor("#451A03")
        canvas.drawArc(RectF(cx - 170f, cy - 270f, cx + 170f, cy - 50f), 180f, 180f, true, paint)

        // Eyes
        paint.color = Color.WHITE
        canvas.drawOval(RectF(cx - 90f, cy - 60f, cx - 30f, cy - 20f), paint)
        canvas.drawOval(RectF(cx + 30f, cy - 60f, cx + 90f, cy - 20f), paint)

        paint.color = Color.parseColor("#0284C7") // Blue pupils
        canvas.drawCircle(cx - 60f, cy - 40f, 14f, paint)
        canvas.drawCircle(cx + 60f, cy - 40f, 14f, paint)

        // Nose
        paint.color = Color.parseColor("#FB923C")
        canvas.drawOval(RectF(cx - 14f, cy - 10f, cx + 14f, cy + 40f), paint)

        // Smile
        paint.color = Color.parseColor("#991B1B")
        paint.strokeWidth = 6f
        paint.style = Paint.Style.STROKE
        canvas.drawArc(RectF(cx - 60f, cy + 60f, cx + 60f, cy + 120f), 20f, 140f, false, paint)
        paint.style = Paint.Style.FILL

        // Face Detection Bounding Box Overlay (Simulating AI Vision)
        paint.color = Color.GREEN
        paint.strokeWidth = 4f
        paint.style = Paint.Style.STROKE
        val boxRect = RectF(cx - 200f, cy - 280f, cx + 200f, cy + 220f)
        canvas.drawRect(boxRect, paint)

        // Corner reticles
        val cornerLen = 30f
        paint.strokeWidth = 8f
        // Top-left
        canvas.drawLine(boxRect.left, boxRect.top, boxRect.left + cornerLen, boxRect.top, paint)
        canvas.drawLine(boxRect.left, boxRect.top, boxRect.left, boxRect.top + cornerLen, paint)
        // Top-right
        canvas.drawLine(boxRect.right, boxRect.top, boxRect.right - cornerLen, boxRect.top, paint)
        canvas.drawLine(boxRect.right, boxRect.top, boxRect.right, boxRect.top + cornerLen, paint)
        // Bottom-left
        canvas.drawLine(boxRect.left, boxRect.bottom, boxRect.left + cornerLen, boxRect.bottom, paint)
        canvas.drawLine(boxRect.left, boxRect.bottom, boxRect.left, boxRect.bottom - cornerLen, paint)
        // Bottom-right
        canvas.drawLine(boxRect.right, boxRect.bottom, boxRect.right - cornerLen, boxRect.bottom, paint)
        canvas.drawLine(boxRect.right, boxRect.bottom, boxRect.right, boxRect.bottom - cornerLen, paint)
        paint.style = Paint.Style.FILL

        // Target Tag
        textPaint.color = Color.GREEN
        textPaint.textSize = 26f
        canvas.drawText("FACE DETECTED (CONF: 99.4%)", boxRect.left, boxRect.top - 12f, textPaint)

        // Info Banner
        paint.color = Color.argb(180, 15, 23, 42)
        canvas.drawRect(0f, h - 100f, w, h, paint)
        textPaint.color = Color.WHITE
        textPaint.textSize = 26f
        canvas.drawText("CloneLab AI Test Subject | Fps: ${"%.1f".format(currentFps)} | Frame: #$frameCounter", 24f, h - 50f, textPaint)
    }

    private fun drawCalibrationGrid(canvas: Canvas) {
        val w = canvas.width.toFloat()
        val h = canvas.height.toFloat()
        if (w <= 0 || h <= 0) return

        canvas.drawColor(Color.parseColor("#090D16"))

        // Grid lines
        paint.color = Color.parseColor("#1E293B")
        paint.strokeWidth = 2f
        val step = 60f
        var x = 0f
        while (x < w) {
            canvas.drawLine(x, 0f, x, h, paint)
            x += step
        }
        var y = 0f
        while (y < h) {
            canvas.drawLine(0f, y, w, y, paint)
            y += step
        }

        // Center crosshair
        paint.color = Color.CYAN
        paint.strokeWidth = 3f
        canvas.drawLine(w / 2f - 40f, h / 2f, w / 2f + 40f, h / 2f, paint)
        canvas.drawLine(w / 2f, h / 2f - 40f, w / 2f, h / 2f + 40f, paint)

        // Animate bouncing ball to demonstrate smooth cadence
        ballX += ballVx
        ballY += ballVy
        val radius = 35f
        if (ballX - radius < 0 || ballX + radius > w) {
            ballVx = -ballVx
            ballX = ballX.coerceIn(radius, w - radius)
        }
        if (ballY - radius < 0 || ballY + radius > h) {
            ballVy = -ballVy
            ballY = ballY.coerceIn(radius, h - radius)
        }

        paint.color = Color.parseColor("#F43F5E") // Bright pink ball
        canvas.drawCircle(ballX, ballY, radius, paint)

        // Trailing glow
        paint.color = Color.argb(80, 244, 63, 94)
        canvas.drawCircle(ballX - ballVx * 2, ballY - ballVy * 2, radius * 0.7f, paint)

        // Cadence telemetry
        paint.color = Color.argb(200, 15, 23, 42)
        canvas.drawRoundRect(RectF(30f, 30f, 440f, 160f), 12f, 12f, paint)
        textPaint.color = Color.WHITE
        textPaint.textSize = 28f
        canvas.drawText("Cadence Benchmark Grid", 46f, 75f, textPaint)
        textPaint.color = Color.CYAN
        canvas.drawText("FPS: ${"%.1f".format(currentFps)} / $targetFps", 46f, 115f, textPaint)
        textPaint.color = Color.YELLOW
        canvas.drawText("Frame: #$frameCounter", 46f, 145f, textPaint)
    }

    override fun stop() {
        isRunningFlag.set(false)
        loopHandler?.removeCallbacks(frameRunnable)
        handlerThread?.quitSafely()
        handlerThread = null
        loopHandler = null
        surfaces.clear()
        CameraRedirectLog.info(TAG, "Stopped PatternFrameSource ($patternType)")
    }

    override fun isRunning(): Boolean = isRunningFlag.get()

    override fun pause() {
        isPausedFlag.set(true)
        CameraRedirectLog.info(TAG, "Paused PatternFrameSource")
    }

    override fun resume() {
        isPausedFlag.set(false)
        lastFpsCalculationTime = SystemClock.elapsedRealtime()
        frameCountInWindow = 0
        CameraRedirectLog.info(TAG, "Resumed PatternFrameSource")
    }

    override fun addSurface(surface: Surface) {
        if (!surfaces.contains(surface)) {
            surfaces.add(surface)
            CameraRedirectLog.info(TAG, "Added surface to pattern: $surface (total ${surfaces.size})")
        }
    }

    override fun removeSurface(surface: Surface) {
        surfaces.remove(surface)
        CameraRedirectLog.info(TAG, "Removed surface from pattern: $surface (remaining ${surfaces.size})")
    }

    override fun setFrameListener(listener: FrameSource.FrameListener?) {
        this.frameListener = listener
    }
}
