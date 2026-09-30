package com.example.clonelab.camera

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class NdkWarning(
    val packageName: String,
    val timestamp: Long = System.currentTimeMillis(),
    val message: String
)

object NdkCameraFallbackHandler {

    private val _lastNdkWarning = MutableStateFlow<NdkWarning?>(null)
    val lastNdkWarning: StateFlow<NdkWarning?> = _lastNdkWarning.asStateFlow()

    fun reportNdkAttempt(packageName: String, detail: String = "App attempted native camera access (libcamera2ndk.so)") {
        val warning = NdkWarning(
            packageName = packageName,
            message = "NDK Camera Fallback Warning: Contained app '$packageName' requested camera access via NDK ($detail). " +
                "Native camera interception is out-of-scope for v1. CloneLab has prevented a container crash."
        )
        _lastNdkWarning.value = warning
        CameraRedirectLog.ndk("NDK-Fallback", warning.message)
    }

    fun dismissWarning() {
        _lastNdkWarning.value = null
    }
}
