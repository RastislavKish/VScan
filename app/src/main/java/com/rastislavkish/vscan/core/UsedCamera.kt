package com.rastislavkish.vscan.core

import androidx.camera.core.CameraSelector

import kotlinx.serialization.*

@Serializable
enum class UsedCamera {
    BACK_CAMERA,
    FRONT_CAMERA;

    val selector: CameraSelector
    get() = when (this) {
        UsedCamera.BACK_CAMERA -> CameraSelector.DEFAULT_BACK_CAMERA
        UsedCamera.FRONT_CAMERA -> CameraSelector.DEFAULT_FRONT_CAMERA
        }
    }
