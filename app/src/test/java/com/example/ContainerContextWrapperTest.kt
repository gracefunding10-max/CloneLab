package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.clonelab.camera.ContainerContextWrapper
import com.example.clonelab.camera.FakeCameraManager
import com.example.clonelab.data.CameraSourceType
import com.example.clonelab.data.ClonedApp
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ContainerContextWrapperTest {

    @Test
    fun testNullCameraSourceUriPreservesRealCamera() {
        val baseContext = ApplicationProvider.getApplicationContext<Context>()
        val unredirectedClone = ClonedApp(
            packageName = "com.mock.normalapp",
            appName = "Normal Camera App",
            cameraSourceUri = null, // null means pass through to real camera
            cameraRedirectEnabled = false
        )

        val wrapper = ContainerContextWrapper(baseContext, unredirectedClone)
        val service = wrapper.getSystemService(Context.CAMERA_SERVICE)

        // Must not be FakeCameraManager
        assertTrue("Service should not be FakeCameraManager", service !is FakeCameraManager)
    }

    @Test
    fun testKycPackageIsRefusedAndPreservesRealCamera() {
        val baseContext = ApplicationProvider.getApplicationContext<Context>()
        val kycClone = ClonedApp(
            packageName = "com.chase.sig.android",
            appName = "Chase Banking",
            cameraSourceUri = "pattern://face_target",
            cameraRedirectEnabled = true,
            isKycBlocked = true
        )

        val wrapper = ContainerContextWrapper(baseContext, kycClone)
        val service = wrapper.getSystemService(Context.CAMERA_SERVICE)

        // Must refuse to return FakeCameraManager
        assertTrue("KYC package must never receive FakeCameraManager", service !is FakeCameraManager)
    }

    @Test
    fun testActiveRedirectionSubstitutesFakeCameraManager() {
        val baseContext = ApplicationProvider.getApplicationContext<Context>()
        val redirectedClone = ClonedApp(
            packageName = "com.mock.qrscanner",
            appName = "FastQR Scanner",
            cameraSourceUri = "pattern://color_bars",
            sourceType = CameraSourceType.GENERATED_PATTERN,
            cameraRedirectEnabled = true
        )

        val wrapper = ContainerContextWrapper(baseContext, redirectedClone)
        val service = wrapper.getSystemService(Context.CAMERA_SERVICE)

        assertNotNull(service)
        assertTrue("Active clone must receive FakeCameraManager", service is FakeCameraManager)
    }
}
