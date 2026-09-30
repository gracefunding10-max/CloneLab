package com.example.clonelab.camera.frame

import android.content.Context
import android.net.Uri
import com.example.clonelab.data.CameraSourceType
import com.example.clonelab.data.ClonedApp

object FrameSourceFactory {

    fun create(context: Context, clone: ClonedApp): FrameSource {
        val uriString = clone.cameraSourceUri
        if (uriString != null && uriString.startsWith("pattern://")) {
            val patternType = when {
                uriString.contains("face") -> "FACE_TARGET"
                uriString.contains("calibration") -> "CALIBRATION_GRID"
                else -> "COLOR_BARS"
            }
            return PatternFrameSource(patternType)
        }

        return when (clone.sourceType) {
            CameraSourceType.VIDEO -> {
                val uri = uriString?.let { Uri.parse(it) }
                VideoFrameSource(context, uri)
            }
            CameraSourceType.IMAGE -> {
                val uri = uriString?.let { Uri.parse(it) }
                ImageFrameSource(context, uri)
            }
            CameraSourceType.GENERATED_PATTERN -> {
                PatternFrameSource(clone.customPatternName)
            }
        }
    }
}
