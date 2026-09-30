package com.example.clonelab.camera

import android.content.Context
import com.example.clonelab.data.ClonedApp
import com.example.clonelab.security.KycDenylistPolicy
import java.util.concurrent.ConcurrentHashMap

object CameraRedirectModule {

    private val TAG = "CameraRedirectModule"
    private val activeContextWrappers = ConcurrentHashMap<String, ContainerContextWrapper>()

    fun wrapContext(baseContext: Context, clonedApp: ClonedApp): Context {
        if (KycDenylistPolicy.isDenied(clonedApp.packageName)) {
            CameraRedirectLog.security(
                TAG,
                "Refusing to attach CameraRedirectModule to ${clonedApp.packageName} (KYC/Banking Denylist Gate)"
            )
            return baseContext
        }

        val wrapper = ContainerContextWrapper(baseContext, clonedApp)
        activeContextWrappers[clonedApp.packageName] = wrapper

        CameraRedirectLog.hook(
            TAG,
            "Wrapped container context for '${clonedApp.packageName}' (Redirect enabled: ${clonedApp.cameraRedirectEnabled})"
        )
        return wrapper
    }

    fun getWrapper(packageName: String): ContainerContextWrapper? {
        return activeContextWrappers[packageName]
    }

    fun unregister(packageName: String) {
        val wrapper = activeContextWrappers.remove(packageName)
        wrapper?.fakeCameraManager?.closeActiveDevice()
    }

    fun clearAll() {
        for (w in activeContextWrappers.values) {
            w.fakeCameraManager.closeActiveDevice()
        }
        activeContextWrappers.clear()
    }
}
