package com.example.clonelab.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cloned_apps")
data class ClonedApp(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val packageName: String,
    val appName: String,
    val iconUri: String? = null,
    // cameraSourceUri: null means "pass through to real camera" (default, unmodified behavior)
    val cameraSourceUri: String? = null,
    val sourceType: CameraSourceType = CameraSourceType.GENERATED_PATTERN,
    val cameraRedirectEnabled: Boolean = false,
    val targetFps: Int = 30,
    val isKycBlocked: Boolean = false,
    val customPatternName: String = "COLOR_BARS", // COLOR_BARS, FACE_TARGET, CALIBRATION_GRID
    val createdAt: Long = System.currentTimeMillis()
)
