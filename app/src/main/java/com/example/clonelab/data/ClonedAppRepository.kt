package com.example.clonelab.data

import kotlinx.coroutines.flow.Flow

class ClonedAppRepository(private val dao: ClonedAppDao) {

    val allClones: Flow<List<ClonedApp>> = dao.getAllClones()

    fun getCloneById(id: Long): Flow<ClonedApp?> = dao.getCloneById(id)

    suspend fun getCloneByPackage(packageName: String): ClonedApp? = dao.getCloneByPackage(packageName)

    suspend fun insert(clone: ClonedApp): Long = dao.insert(clone)

    suspend fun update(clone: ClonedApp) = dao.update(clone)

    suspend fun delete(clone: ClonedApp) = dao.delete(clone)

    suspend fun deleteById(id: Long) = dao.deleteById(id)

    suspend fun setRedirectEnabled(id: Long, enabled: Boolean) = dao.setRedirectEnabled(id, enabled)

    suspend fun updateCameraSource(id: Long, uri: String?, sourceType: CameraSourceType) =
        dao.updateCameraSource(id, uri, sourceType)

    suspend fun seedDefaultsIfEmpty() {
        if (dao.count() == 0) {
            val defaults = listOf(
                ClonedApp(
                    packageName = "com.mock.qrscanner",
                    appName = "FastQR Scanner",
                    cameraSourceUri = "pattern://face_target",
                    sourceType = CameraSourceType.GENERATED_PATTERN,
                    cameraRedirectEnabled = true,
                    targetFps = 30,
                    isKycBlocked = false,
                    customPatternName = "FACE_TARGET"
                ),
                ClonedApp(
                    packageName = "com.mock.socialsnap",
                    appName = "Social Snap Clone",
                    cameraSourceUri = "pattern://color_bars",
                    sourceType = CameraSourceType.GENERATED_PATTERN,
                    cameraRedirectEnabled = true,
                    targetFps = 30,
                    isKycBlocked = false,
                    customPatternName = "COLOR_BARS"
                ),
                ClonedApp(
                    packageName = "com.mock.retrophoto",
                    appName = "Retro Vintage Cam",
                    cameraSourceUri = null, // null = Real Camera passthrough
                    sourceType = CameraSourceType.IMAGE,
                    cameraRedirectEnabled = false,
                    targetFps = 24,
                    isKycBlocked = false,
                    customPatternName = "CALIBRATION_GRID"
                ),
                ClonedApp(
                    packageName = "com.chase.sig.android",
                    appName = "Chase Mobile Banking [KYC]",
                    cameraSourceUri = null,
                    sourceType = CameraSourceType.IMAGE,
                    cameraRedirectEnabled = false,
                    targetFps = 30,
                    isKycBlocked = true,
                    customPatternName = "COLOR_BARS"
                )
            )
            dao.insertAll(defaults)
        }
    }
}
