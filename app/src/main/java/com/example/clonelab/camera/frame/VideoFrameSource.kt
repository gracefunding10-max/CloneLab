package com.example.clonelab.camera.frame

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.view.Surface
import com.example.clonelab.camera.CameraRedirectLog
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

class VideoFrameSource(
    private val context: Context,
    private val videoUri: Uri? = null
) : FrameSource {

    private val TAG = "VideoFrameSource"
    private val surfaces = CopyOnWriteArrayList<Surface>()

    private var workerThread: HandlerThread? = null
    private var workerHandler: Handler? = null

    private val isRunningFlag = AtomicBoolean(false)
    private val isPausedFlag = AtomicBoolean(false)

    private var targetFps = 30
    private var frameIntervalNs = 1_000_000_000L / 30

    private var mediaExtractor: MediaExtractor? = null
    private var mediaCodec: MediaCodec? = null

    private var frameCounter = 0L
    private var lastFpsCalculationTime = 0L
    private var frameCountInWindow = 0
    private var currentFps = 0f
    private var frameListener: FrameSource.FrameListener? = null

    // Fallback pattern if video URI is invalid or cannot be decoded
    private var fallbackPatternSource: PatternFrameSource? = null

    override fun start(surfaces: List<Surface>, targetFps: Int) {
        if (isRunningFlag.get()) {
            stop()
        }

        this.surfaces.clear()
        this.surfaces.addAll(surfaces)
        this.targetFps = targetFps.coerceIn(10, 60)
        this.frameIntervalNs = 1_000_000_000L / this.targetFps

        val thread = HandlerThread("CloneLab-VideoSourceThread")
        thread.start()
        workerThread = thread
        val handler = Handler(thread.looper)
        workerHandler = handler

        isRunningFlag.set(true)
        isPausedFlag.set(false)
        frameCounter = 0L
        frameCountInWindow = 0
        lastFpsCalculationTime = SystemClock.elapsedRealtime()

        CameraRedirectLog.info(
            TAG,
            "Starting VideoFrameSource with ${surfaces.size} surface(s) at $targetFps FPS"
        )

        handler.post {
            tryInitMediaCodec()
        }
    }

    private fun tryInitMediaCodec() {
        if (videoUri == null || surfaces.isEmpty()) {
            startFallbackPattern("No video URI provided")
            return
        }

        try {
            val extractor = MediaExtractor()
            extractor.setDataSource(context, videoUri, null)
            mediaExtractor = extractor

            var trackIndex = -1
            var videoFormat: MediaFormat? = null

            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("video/")) {
                    trackIndex = i
                    videoFormat = format
                    break
                }
            }

            if (trackIndex < 0 || videoFormat == null) {
                startFallbackPattern("No video track found in URI")
                return
            }

            extractor.selectTrack(trackIndex)
            val mime = videoFormat.getString(MediaFormat.KEY_MIME)!!
            val codec = MediaCodec.createDecoderByType(mime)
            mediaCodec = codec

            val primarySurface = surfaces.firstOrNull { it.isValid }
            if (primarySurface == null) {
                startFallbackPattern("No valid output surface for video decode")
                return
            }

            // Zero-copy path: configure MediaCodec with target surface directly!
            codec.configure(videoFormat, primarySurface, null, 0)
            codec.start()

            CameraRedirectLog.info(
                TAG,
                "MediaCodec initialized ($mime) zero-copy to surface. Looping enabled."
            )

            // Start decode loop
            workerHandler?.post(decodeLoopRunnable)

        } catch (e: Exception) {
            CameraRedirectLog.error(TAG, "MediaCodec init failed: ${e.message}. Falling back to pattern.")
            startFallbackPattern("Decoder init exception: ${e.message}")
        }
    }

    private fun startFallbackPattern(reason: String) {
        CameraRedirectLog.warn(TAG, "Falling back to animated pattern. Reason: $reason")
        val pattern = PatternFrameSource("CALIBRATION_GRID")
        fallbackPatternSource = pattern
        pattern.setFrameListener(object : FrameSource.FrameListener {
            override fun onFrameDelivered(frameIndex: Long, timestampNs: Long, measuredFps: Float) {
                frameCounter = frameIndex
                currentFps = measuredFps
                frameListener?.onFrameDelivered(frameIndex, timestampNs, measuredFps)
            }
        })
        pattern.start(surfaces.toList(), targetFps)
    }

    private val decodeLoopRunnable = object : Runnable {
        private val bufferInfo = MediaCodec.BufferInfo()
        private var isEos = false

        override fun run() {
            if (!isRunningFlag.get()) return

            val startNano = System.nanoTime()

            if (!isPausedFlag.get() && mediaCodec != null && mediaExtractor != null) {
                decodeNextFrame(bufferInfo)
            }

            val elapsedNano = System.nanoTime() - startNano
            val delayNs = frameIntervalNs - elapsedNano
            val delayMs = (delayNs / 1_000_000L).coerceAtLeast(0)

            workerHandler?.postDelayed(this, delayMs)
        }

        private fun decodeNextFrame(info: MediaCodec.BufferInfo) {
            val codec = mediaCodec ?: return
            val extractor = mediaExtractor ?: return

            try {
                // Feed input buffer
                if (!isEos) {
                    val inIndex = codec.dequeueInputBuffer(10_000L)
                    if (inIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inIndex)
                        if (inputBuffer != null) {
                            val sampleSize = extractor.readSampleData(inputBuffer, 0)
                            if (sampleSize < 0) {
                                // End of stream reached: loop video back to start
                                extractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                                CameraRedirectLog.info(TAG, "Video reached EOS -> Looping from start")
                            } else {
                                codec.queueInputBuffer(
                                    inIndex,
                                    0,
                                    sampleSize,
                                    extractor.sampleTime,
                                    0
                                )
                                extractor.advance()
                            }
                        }
                    }
                }

                // Drain output buffer
                val outIndex = codec.dequeueOutputBuffer(info, 10_000L)
                if (outIndex >= 0) {
                    // Render to output surface
                    codec.releaseOutputBuffer(outIndex, true)

                    frameCounter++
                    val nowMs = SystemClock.elapsedRealtime()
                    frameCountInWindow++
                    val elapsed = nowMs - lastFpsCalculationTime
                    if (elapsed >= 1000L) {
                        currentFps = (frameCountInWindow * 1000f) / elapsed
                        frameCountInWindow = 0
                        lastFpsCalculationTime = nowMs
                        CameraRedirectLog.cadence(
                            TAG,
                            "Video playback cadence: ${"%.1f".format(currentFps)} FPS (target: $targetFps)"
                        )
                    }

                    frameListener?.onFrameDelivered(frameCounter, System.nanoTime(), currentFps)
                }
            } catch (e: Exception) {
                CameraRedirectLog.warn(TAG, "Video decode frame exception: ${e.message}")
            }
        }
    }

    override fun stop() {
        isRunningFlag.set(false)
        fallbackPatternSource?.stop()
        fallbackPatternSource = null

        workerHandler?.removeCallbacks(decodeLoopRunnable)

        try {
            mediaCodec?.stop()
            mediaCodec?.release()
        } catch (_: Exception) {}
        mediaCodec = null

        try {
            mediaExtractor?.release()
        } catch (_: Exception) {}
        mediaExtractor = null

        workerThread?.quitSafely()
        workerThread = null
        workerHandler = null
        surfaces.clear()

        CameraRedirectLog.info(TAG, "Stopped VideoFrameSource")
    }

    override fun isRunning(): Boolean = isRunningFlag.get()

    override fun pause() {
        isPausedFlag.set(true)
        fallbackPatternSource?.pause()
        CameraRedirectLog.info(TAG, "Paused VideoFrameSource")
    }

    override fun resume() {
        isPausedFlag.set(false)
        fallbackPatternSource?.resume()
        lastFpsCalculationTime = SystemClock.elapsedRealtime()
        frameCountInWindow = 0
        CameraRedirectLog.info(TAG, "Resumed VideoFrameSource")
    }

    override fun addSurface(surface: Surface) {
        if (!surfaces.contains(surface)) {
            surfaces.add(surface)
            fallbackPatternSource?.addSurface(surface)
        }
    }

    override fun removeSurface(surface: Surface) {
        surfaces.remove(surface)
        fallbackPatternSource?.removeSurface(surface)
    }

    override fun setFrameListener(listener: FrameSource.FrameListener?) {
        this.frameListener = listener
    }
}
