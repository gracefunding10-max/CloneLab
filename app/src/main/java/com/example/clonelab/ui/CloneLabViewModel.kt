package com.example.clonelab.ui

import android.app.Application
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CaptureRequest
import android.os.Handler
import android.os.Looper
import android.view.Surface
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.clonelab.camera.CameraRedirectLog
import com.example.clonelab.camera.FakeCamera
import com.example.clonelab.camera.FakeCameraCharacteristicsProvider
import android.hardware.camera2.FakeCameraDevice
import com.example.clonelab.camera.FakeCameraManager
import com.example.clonelab.camera.NdkCameraFallbackHandler
import com.example.clonelab.camera.frame.FrameSource
import com.example.clonelab.camera.frame.FrameSourceFactory
import com.example.clonelab.data.CameraSourceType
import com.example.clonelab.data.CloneLabDatabase
import com.example.clonelab.data.ClonedApp
import com.example.clonelab.data.ClonedAppRepository
import com.example.clonelab.security.KycDenylistPolicy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class SandboxApiMode {
    CAMERA2,
    LEGACY_CAMERA,
    CAMERAX_SIM
}

class CloneLabViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: ClonedAppRepository

    val allClones: StateFlow<List<ClonedApp>>
    val logsFlow = CameraRedirectLog.logsFlow
    val ndkWarning = NdkCameraFallbackHandler.lastNdkWarning

    private val _selectedClone = MutableStateFlow<ClonedApp?>(null)
    val selectedClone: StateFlow<ClonedApp?> = _selectedClone.asStateFlow()

    // Sandbox state
    private val _sandboxApiMode = MutableStateFlow(SandboxApiMode.CAMERA2)
    val sandboxApiMode: StateFlow<SandboxApiMode> = _sandboxApiMode.asStateFlow()

    private val _sandboxIsStreaming = MutableStateFlow(false)
    val sandboxIsStreaming: StateFlow<Boolean> = _sandboxIsStreaming.asStateFlow()

    private val _sandboxIsPaused = MutableStateFlow(false)
    val sandboxIsPaused: StateFlow<Boolean> = _sandboxIsPaused.asStateFlow()

    private val _sandboxFps = MutableStateFlow(0f)
    val sandboxFps: StateFlow<Float> = _sandboxFps.asStateFlow()

    private val _sandboxFrameCount = MutableStateFlow(0L)
    val sandboxFrameCount: StateFlow<Long> = _sandboxFrameCount.asStateFlow()

    private val _activeCameraId = MutableStateFlow("0")
    val activeCameraId: StateFlow<String> = _activeCameraId.asStateFlow()

    private val _securityAlert = MutableStateFlow<String?>(null)
    val securityAlert: StateFlow<String?> = _securityAlert.asStateFlow()

    private val _snapshotCount = MutableStateFlow(0)
    val snapshotCount: StateFlow<Int> = _snapshotCount.asStateFlow()

    // Active Sandbox handles
    private var activeFakeManager: FakeCameraManager? = null
    private var activeDevice: FakeCameraDevice? = null
    private var activeSession: CameraCaptureSession? = null
    private var activeFrameSource: FrameSource? = null
    private var activeLegacyCamera: FakeCamera? = null
    private var activeSandboxSurface: Surface? = null

    init {
        val db = CloneLabDatabase.getInstance(application)
        repository = ClonedAppRepository(db.clonedAppDao())
        allClones = repository.allClones.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

        viewModelScope.launch {
            repository.seedDefaultsIfEmpty()
        }
    }

    fun selectClone(clone: ClonedApp?) {
        _selectedClone.value = clone
    }

    fun toggleRedirect(clone: ClonedApp, enabled: Boolean) {
        if (enabled && KycDenylistPolicy.isDenied(clone.packageName)) {
            _securityAlert.value = KycDenylistPolicy.getDenialReason(clone.packageName)
            CameraRedirectLog.security("SecurityGate", _securityAlert.value!!)
            return
        }
        viewModelScope.launch {
            repository.setRedirectEnabled(clone.id, enabled)
            if (_selectedClone.value?.id == clone.id) {
                _selectedClone.value = clone.copy(cameraRedirectEnabled = enabled)
            }
        }
    }

    fun updateCameraSource(
        clone: ClonedApp,
        uri: String?,
        sourceType: CameraSourceType,
        patternName: String = "COLOR_BARS"
    ) {
        viewModelScope.launch {
            val updated = clone.copy(
                cameraSourceUri = uri,
                sourceType = sourceType,
                customPatternName = patternName
            )
            repository.update(updated)
            if (_selectedClone.value?.id == clone.id) {
                _selectedClone.value = updated
            }
            CameraRedirectLog.info(
                "Config",
                "Updated camera source for '${clone.appName}': $sourceType ($uri)"
            )
        }
    }

    fun updateFps(clone: ClonedApp, fps: Int) {
        viewModelScope.launch {
            val updated = clone.copy(targetFps = fps)
            repository.update(updated)
            if (_selectedClone.value?.id == clone.id) {
                _selectedClone.value = updated
            }
        }
    }

    fun addCustomClone(name: String, packageName: String) {
        viewModelScope.launch {
            val isKyc = KycDenylistPolicy.isDenied(packageName)
            val newApp = ClonedApp(
                packageName = packageName,
                appName = name,
                cameraSourceUri = if (isKyc) null else "pattern://color_bars",
                sourceType = CameraSourceType.GENERATED_PATTERN,
                cameraRedirectEnabled = !isKyc,
                targetFps = 30,
                isKycBlocked = isKyc,
                customPatternName = "COLOR_BARS"
            )
            repository.insert(newApp)
            CameraRedirectLog.info("CloneLab", "Created new clone container: $name ($packageName)")
        }
    }

    fun deleteClone(clone: ClonedApp) {
        viewModelScope.launch {
            repository.delete(clone)
            if (_selectedClone.value?.id == clone.id) {
                _selectedClone.value = null
            }
        }
    }

    fun setSandboxApiMode(mode: SandboxApiMode) {
        _sandboxApiMode.value = mode
        if (_sandboxIsStreaming.value && activeSandboxSurface != null) {
            stopSandbox()
            startSandboxStreaming(activeSandboxSurface!!)
        }
    }

    fun startSandboxStreaming(surface: Surface) {
        activeSandboxSurface = surface
        val clone = _selectedClone.value ?: allClones.value.firstOrNull() ?: return

        // Gate check
        if (KycDenylistPolicy.isDenied(clone.packageName)) {
            _securityAlert.value = KycDenylistPolicy.getDenialReason(clone.packageName)
            CameraRedirectLog.security("Sandbox", _securityAlert.value!!)
            return
        }

        stopSandbox()

        CameraRedirectLog.hook(
            "Sandbox",
            "Target App '${clone.appName}' opening camera via ${_sandboxApiMode.value}..."
        )

        val frameSource = FrameSourceFactory.create(getApplication(), clone)
        activeFrameSource = frameSource

        frameSource.setFrameListener(object : FrameSource.FrameListener {
            override fun onFrameDelivered(frameIndex: Long, timestampNs: Long, measuredFps: Float) {
                _sandboxFrameCount.value = frameIndex
                _sandboxFps.value = measuredFps
            }
        })

        when (_sandboxApiMode.value) {
            SandboxApiMode.CAMERA2, SandboxApiMode.CAMERAX_SIM -> {
                val fakeManager = FakeCameraManager(getApplication(), clone, frameSource)
                activeFakeManager = fakeManager

                fakeManager.openCamera(
                    _activeCameraId.value,
                    object : CameraDevice.StateCallback() {
                        override fun onOpened(camera: CameraDevice) {
                            activeDevice = camera as? FakeCameraDevice
                            camera.createCaptureSession(
                                mutableListOf(surface),
                                object : CameraCaptureSession.StateCallback() {
                                    override fun onConfigured(session: CameraCaptureSession) {
                                        activeSession = session
                                        try {
                                            val builder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                                            session.setRepeatingRequest(builder.build(), null, Handler(Looper.getMainLooper()))
                                            _sandboxIsStreaming.value = true
                                            _sandboxIsPaused.value = false
                                        } catch (e: Exception) {
                                            // Direct fallback to start frames directly
                                            frameSource.start(listOf(surface), clone.targetFps)
                                            _sandboxIsStreaming.value = true
                                            _sandboxIsPaused.value = false
                                        }
                                    }

                                    override fun onConfigureFailed(session: CameraCaptureSession) {
                                        CameraRedirectLog.error("Sandbox", "CaptureSession configuration failed")
                                    }
                                },
                                Handler(Looper.getMainLooper())
                            )
                        }

                        override fun onDisconnected(camera: CameraDevice) {
                            stopSandbox()
                        }

                        override fun onError(camera: CameraDevice, error: Int) {
                            CameraRedirectLog.error("Sandbox", "CameraDevice error code: $error")
                        }
                    },
                    Handler(Looper.getMainLooper())
                )
            }

            SandboxApiMode.LEGACY_CAMERA -> {
                val cam = FakeCamera.open(_activeCameraId.value.toIntOrNull() ?: 0, frameSource)
                activeLegacyCamera = cam
                cam.setPreviewDisplay(object : android.view.SurfaceHolder {
                    override fun addCallback(callback: android.view.SurfaceHolder.Callback?) {}
                    override fun removeCallback(callback: android.view.SurfaceHolder.Callback?) {}
                    override fun isCreating(): Boolean = false
                    override fun setType(type: Int) {}
                    override fun setFixedSize(width: Int, height: Int) {}
                    override fun setSizeFromLayout() {}
                    override fun setFormat(format: Int) {}
                    override fun setKeepScreenOn(screenOn: Boolean) {}
                    override fun lockCanvas(): android.graphics.Canvas? = null
                    override fun lockCanvas(dirty: android.graphics.Rect?): android.graphics.Canvas? = null
                    override fun lockHardwareCanvas(): android.graphics.Canvas? = null
                    override fun unlockCanvasAndPost(canvas: android.graphics.Canvas?) {}
                    override fun getSurfaceFrame(): android.graphics.Rect = android.graphics.Rect(0, 0, 1280, 720)
                    override fun getSurface(): Surface = surface
                })
                cam.startPreview()
                _sandboxIsStreaming.value = true
                _sandboxIsPaused.value = false
            }
        }
    }

    fun stopSandbox() {
        try {
            activeSession?.close()
        } catch (_: Exception) {}
        activeSession = null

        try {
            activeDevice?.close()
        } catch (_: Exception) {}
        activeDevice = null

        try {
            activeLegacyCamera?.release()
        } catch (_: Exception) {}
        activeLegacyCamera = null

        try {
            activeFrameSource?.stop()
        } catch (_: Exception) {}
        activeFrameSource = null

        _sandboxIsStreaming.value = false
        _sandboxIsPaused.value = false
        _sandboxFps.value = 0f
    }

    fun togglePauseResume() {
        val paused = _sandboxIsPaused.value
        if (paused) {
            activeFrameSource?.resume()
            _sandboxIsPaused.value = false
            CameraRedirectLog.info("Sandbox", "Simulated onResume(): Video/Image frame loop resumed")
        } else {
            activeFrameSource?.pause()
            _sandboxIsPaused.value = true
            CameraRedirectLog.info("Sandbox", "Simulated onPause(): Contained app backgrounded")
        }
    }

    fun switchCameraFacing() {
        val nextId = if (_activeCameraId.value == "0") "1" else "0"
        _activeCameraId.value = nextId
        CameraRedirectLog.info("Sandbox", "Switched camera facing to ID $nextId (${if (nextId == "0") "BACK" else "FRONT"})")
        val surface = activeSandboxSurface
        if (surface != null && _sandboxIsStreaming.value) {
            stopSandbox()
            startSandboxStreaming(surface)
        }
    }

    fun takeSnapshot() {
        _snapshotCount.value++
        CameraRedirectLog.hook(
            "Sandbox",
            "Single capture snapshot #${_snapshotCount.value} triggered successfully on ${activeSandboxSurface}"
        )
    }

    fun simulateNdkAccess(packageName: String) {
        NdkCameraFallbackHandler.reportNdkAttempt(
            packageName,
            "App called ACameraManager_create() via libcamera2ndk.so"
        )
    }

    fun dismissNdkWarning() {
        NdkCameraFallbackHandler.dismissWarning()
    }

    fun dismissSecurityAlert() {
        _securityAlert.value = null
    }

    fun clearLogs() {
        CameraRedirectLog.clear()
    }

    override fun onCleared() {
        super.onCleared()
        stopSandbox()
    }
}
