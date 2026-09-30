package com.example.clonelab.camera

import android.content.Context
import android.content.ContextWrapper
import android.hardware.camera2.CameraManager
import com.example.clonelab.data.ClonedApp
import com.example.clonelab.security.KycDenylistPolicy

class ContainerContextWrapper(
    base: Context,
    val clonedApp: ClonedApp
) : ContextWrapper(base) {

    private val TAG = "ContainerContextWrapper"

    val fakeCameraManager: FakeCameraManager by lazy {
        FakeCameraManager(baseContext, clonedApp)
    }

    override fun getSystemService(name: String): Any? {
        if (name == Context.CAMERA_SERVICE) {
            // Guardrail 1: Refuse to attach for KYC, Banking, or Identity verification apps
            if (KycDenylistPolicy.isDenied(clonedApp.packageName)) {
                val reason = KycDenylistPolicy.getDenialReason(clonedApp.packageName)
                CameraRedirectLog.security(TAG, reason)
                // Refuse redirection, pass through to real system camera
                return super.getSystemService(name)
            }

            // Guardrail 2: If cameraSourceUri is null or redirection disabled, pass through 100% unaffected
            if (clonedApp.cameraSourceUri == null || !clonedApp.cameraRedirectEnabled) {
                CameraRedirectLog.info(
                    TAG,
                    "Passthrough: cameraSourceUri is null or disabled for '${clonedApp.packageName}' -> Using real camera"
                )
                return super.getSystemService(name)
            }

            // Active Redirection: substitute with fake CameraManager
            CameraRedirectLog.hook(
                TAG,
                "Intercepted getSystemService(CAMERA_SERVICE) for '${clonedApp.packageName}' -> Returning FakeCameraManager"
            )
            return fakeCameraManager
        }

        return super.getSystemService(name)
    }
}
