package com.example.stressdetection.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.stressdetection.model.StressLevel

@Composable
fun FaceOverlay(rect: android.graphics.Rect, level: StressLevel, emotion: String, modifier: Modifier) {
    Canvas(modifier = modifier) {
        val color = when (level) {
            StressLevel.HIGH -> Color.Red
            StressLevel.MEDIUM -> Color.Yellow
            StressLevel.LOW -> Color.Green
        }
        drawRect(
            color = color,
            topLeft = Offset(rect.left.toFloat(), rect.top.toFloat()),
            size = Size(rect.width().toFloat(), rect.height().toFloat()),
            style = Stroke(width = 8f)
        )
    }
}

@Composable
fun StressLevelIndicatorSimple(level: StressLevel?, modifier: Modifier = Modifier) {
    val (text, color) = when (level) {
        StressLevel.HIGH -> "YÜKSEK STRES" to Color.Red
        StressLevel.MEDIUM -> "ORTA STRES" to Color.Yellow
        StressLevel.LOW -> "DÜŞÜK STRES" to Color.Green
        null -> "Analiz Ediliyor..." to Color.Gray
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = color)
    ) {
        Box(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Text(text, style = MaterialTheme.typography.headlineMedium, color = Color.Black)
        }
    }
}

