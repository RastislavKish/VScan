package com.rastislavkish.vscan.core

import androidx.camera.core.CameraSelector

import kotlinx.serialization.*

@Serializable
enum class UsedCamera {
    BACK,
    FRONT;

    val selector: CameraSelector
    get() = when (this) {
        UsedCamera.BACK -> CameraSelector.DEFAULT_BACK_CAMERA
        UsedCamera.FRONT -> CameraSelector.DEFAULT_FRONT_CAMERA
        }
    }
