package com.example.clonelab.camera.frame

import android.view.Surface

interface FrameSource {

    fun start(surfaces: List<Surface>, targetFps: Int)

    fun stop()

    fun isRunning(): Boolean

    fun pause()

    fun resume()

    fun addSurface(surface: Surface)

    fun removeSurface(surface: Surface)

    fun setFrameListener(listener: FrameListener?)

    interface FrameListener {
        fun onFrameDelivered(frameIndex: Long, timestampNs: Long, measuredFps: Float)
    }
}
