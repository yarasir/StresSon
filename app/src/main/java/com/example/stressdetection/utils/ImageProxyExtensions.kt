package com.example.stressdetection.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy

@androidx.annotation.OptIn(ExperimentalGetImage::class)
fun ImageProxy.toBitmap(): Bitmap {
    val image = image ?: throw IllegalStateException("Image is null")
    val yBuffer = image.planes[0].buffer // Y
    val uBuffer = image.planes[1].buffer // U
    val vBuffer = image.planes[2].buffer // V

    val ySize = yBuffer.remaining()
    val uSize = uBuffer.remaining()
    val vSize = vBuffer.remaining()

    val nv21 = ByteArray(ySize + uSize + vSize)
    // U ve V buffer verilerini NV21 formatına uygun şekilde birleştir
    yBuffer.get(nv21, 0, ySize)
    vBuffer.get(nv21, ySize, vSize)
    uBuffer.get(nv21, ySize + vSize, uSize)

    val yuvImage = android.graphics.YuvImage(
        nv21,
        android.graphics.ImageFormat.NV21,
        image.width,
        image.height,
        null
    )

    val out = java.io.ByteArrayOutputStream()
    yuvImage.compressToJpeg(
        android.graphics.Rect(0, 0, image.width, image.height),
        100,
        out
    )
    val imageBytes = out.toByteArray()
    return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
}

