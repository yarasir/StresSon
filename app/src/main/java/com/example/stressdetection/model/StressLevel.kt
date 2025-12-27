package com.example.stressdetection.model

import android.graphics.Rect

enum class StressLevel(val value: Int) {
    LOW(0), MEDIUM(1), HIGH(2)
}

data class FaceDetectionResult(
    val boundingBox: Rect?,
    val stressLevel: StressLevel,
    val dominantEmotion: String = "Neutral",
    val imageWidth: Int = 0,
    val imageHeight: Int = 0
)

data class VideoFaceResult(
    val boundingBox: Rect,
    val stressLevel: StressLevel,
    val dominantEmotion: String
)

