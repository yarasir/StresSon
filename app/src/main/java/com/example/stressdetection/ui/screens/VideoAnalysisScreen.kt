package com.example.stressdetection.ui.screens

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.view.ViewGroup
import android.widget.VideoView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.stressdetection.analyzer.StressImageAnalyzer
import com.example.stressdetection.model.StressLevel
import com.example.stressdetection.model.VideoFaceResult
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun VideoAnalysisScreen(videoUri: Uri, onBack: () -> Unit) {
    val context = LocalContext.current
    val analyzer = remember { StressImageAnalyzer(context) }
    var detectedFaces by remember { mutableStateOf<List<VideoFaceResult>>(emptyList()) }
    var videoView: VideoView? by remember { mutableStateOf(null) }
    var videoWidth by remember { mutableStateOf(0) }
    var videoHeight by remember { mutableStateOf(0) }
    var viewWidth by remember { mutableStateOf(0) }
    var viewHeight by remember { mutableStateOf(0) }

    val faceDetector = remember {
        val opts = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .enableTracking()
            .setMinFaceSize(0.12f) // Biraz daha küçük yüzleri de tespit et (0.15 -> 0.12)
            .build()
        FaceDetection.getClient(opts)
    }

    LaunchedEffect(videoView) {
        // ✅ VideoView'un hazır olmasını bekle
        if (videoView == null) return@LaunchedEffect
        
        // ✅ Retriever'ı bir kere oluştur, loop boyunca reuse et
        val retriever = MediaMetadataRetriever().apply {
            try {
                setDataSource(context, videoUri)
                android.util.Log.d("VideoAnalysis", "✅ Retriever oluşturuldu")
            } catch (e: Exception) {
                android.util.Log.e("VideoAnalysis", "❌ Retriever setDataSource hatası: ${e.message}", e)
            }
        }
        
        // ✅ Video başlamadan önce kısa bir bekleme
        delay(300) // Video başlaması için bekle
        
        try {
            // ✅ İlk frame'i hemen analiz et
            var isFirstFrame = true
            
            while (isActive && videoView != null && videoView!!.isPlaying) {
                if (!isFirstFrame) {
                    delay(200) // Her 0.2 saniyede bir analiz (daha hızlı tespit)
                } else {
                    isFirstFrame = false
                }
                try {
                    val timeUs = videoView!!.currentPosition * 1000L
                    val frame = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)

                    frame?.let { bitmap ->
                        // ✅ KRİTİK: Bitmap boyutları ML Kit koordinatlarının referansıdır
                        // Video boyutlarını bitmap'ten al (ML Kit bu boyutlara göre koordinat veriyor)
                        val bitmapWidth = bitmap.width
                        val bitmapHeight = bitmap.height
                        
                        // Video boyutlarını güncelle (bitmap boyutlarına göre)
                        if (videoWidth == 0 || videoHeight == 0 || 
                            videoWidth != bitmapWidth || videoHeight != bitmapHeight) {
                            videoWidth = bitmapWidth
                            videoHeight = bitmapHeight
                            android.util.Log.d("VideoAnalysis", "📐 Bitmap boyutları (ML Kit referansı): ${bitmapWidth}x${bitmapHeight}")
                        }
                        
                        val inputImage = InputImage.fromBitmap(bitmap, 0)
                        faceDetector.process(inputImage)
                            .addOnSuccessListener { faces ->
                                // ✅ BOŞ LİSTE KONTROLÜ: Eğer yüz yoksa boş liste döndür
                                if (faces.isEmpty()) {
                                    detectedFaces = emptyList()
                                    return@addOnSuccessListener
                                }

                                // ✅ EN BÜYÜK YÜZE ODAKLAN (alanı en büyük olan)
                                val sortedFaces = faces.sortedByDescending { 
                                    it.boundingBox.width() * it.boundingBox.height() 
                                }
                                
                                val results = mutableListOf<VideoFaceResult>()
                                for (face in sortedFaces) {
                                    try {
                                        // ✅ Bounding box koordinatlarını bitmap boyutlarına göre normalize et
                                        val normalizedBox = android.graphics.Rect(
                                            face.boundingBox.left.coerceIn(0, bitmapWidth),
                                            face.boundingBox.top.coerceIn(0, bitmapHeight),
                                            face.boundingBox.right.coerceIn(0, bitmapWidth),
                                            face.boundingBox.bottom.coerceIn(0, bitmapHeight)
                                        )
                                        
                                        val faceBitmap = Bitmap.createBitmap(
                                            bitmap,
                                            normalizedBox.left,
                                            normalizedBox.top,
                                            normalizedBox.width().coerceAtMost(bitmap.width - normalizedBox.left),
                                            normalizedBox.height().coerceAtMost(bitmap.height - normalizedBox.top)
                                        )
                                        val inference = analyzer.runInference(faceBitmap)
                                        results.add(
                                            VideoFaceResult(
                                                normalizedBox, // Normalize edilmiş bounding box
                                                inference.first,
                                                inference.second
                                            )
                                        )
                                        android.util.Log.d("VideoAnalysis", "👤 Yüz: box=(${normalizedBox.left},${normalizedBox.top},${normalizedBox.right},${normalizedBox.bottom}), " +
                                                "bitmap=${bitmapWidth}x${bitmapHeight}")
                                    } catch (e: Exception) {
                                        android.util.Log.e("VideoAnalysis", "Yüz işleme hatası: ${e.message}")
                                    }
                                }
                                detectedFaces = results
                                android.util.Log.d("VideoAnalysis", "✅ ${results.size} yüz işlendi (en büyük yüz öncelikli)")
                            }
                            .addOnFailureListener { e ->
                                android.util.Log.e("VideoAnalysis", "Yüz tespiti hatası: ${e.message}")
                                detectedFaces = emptyList()
                            }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("VideoAnalysis", "Frame analiz hatası: ${e.message}")
                }
            }
        } finally {
            // ✅ Retriever'ı düzgün kapat
            try {
                retriever.release()
                android.util.Log.d("VideoAnalysis", "✅ Retriever release edildi")
            } catch (e: Exception) {
                android.util.Log.e("VideoAnalysis", "❌ Retriever release hatası: ${e.message}", e)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { ctx ->
                android.widget.VideoView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    setVideoURI(videoUri)
                    setOnPreparedListener { mp ->
                        mp.isLooping = true
                        videoWidth = mp.videoWidth
                        videoHeight = mp.videoHeight
                        android.util.Log.d("VideoAnalysis", "Video hazır: ${mp.videoWidth}x${mp.videoHeight}")
                        android.util.Log.d("VideoAnalysis", "Video aspect ratio: ${videoWidth.toFloat() / videoHeight.toFloat()}")
                        start()
                    }
                    addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                        viewWidth = width
                        viewHeight = height
                        android.util.Log.d("VideoAnalysis", "View boyutları: ${width}x${height}")
                        android.util.Log.d("VideoAnalysis", "View aspect ratio: ${width.toFloat() / height.toFloat()}")
                    }
                    videoView = this
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Yüz overlay - ✅ KOORDINAT DÖNÜŞÜMÜ: Bitmap koordinatlarını Canvas koordinatlarına çevir
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (videoWidth > 0 && videoHeight > 0 && viewWidth > 0 && viewHeight > 0) {
                // ✅ VideoView CENTER_CROP gibi davranır - aspect ratio korunarak scale
                // Bitmap boyutları (videoWidth x videoHeight) -> Canvas boyutları (size.width x size.height)
                val bitmapAspect = videoWidth.toFloat() / videoHeight.toFloat()
                val canvasAspect = size.width / size.height
                
                val scaleX: Float
                val scaleY: Float
                val offsetX: Float
                val offsetY: Float
                
                if (bitmapAspect > canvasAspect) {
                    // Bitmap daha geniş - yüksekliğe göre scale (CENTER_CROP)
                    // Canvas yüksekliği tamamen doldurulur, genişlik ortalanır
                    scaleY = size.height / videoHeight.toFloat()
                    scaleX = scaleY // Aspect ratio korunur
                    offsetX = (size.width - videoWidth * scaleX) / 2f
                    offsetY = 0f
                } else {
                    // Bitmap daha yüksek - genişliğe göre scale (CENTER_CROP)
                    // Canvas genişliği tamamen doldurulur, yükseklik ortalanır
                    scaleX = size.width / videoWidth.toFloat()
                    scaleY = scaleX // Aspect ratio korunur
                    offsetX = 0f
                    offsetY = (size.height - videoHeight * scaleY) / 2f
                }
                
                detectedFaces.forEach { face ->
                    // ✅ Bitmap koordinatlarını Canvas koordinatlarına dönüştür
                    val left = face.boundingBox.left * scaleX + offsetX
                    val top = face.boundingBox.top * scaleY + offsetY
                    val right = face.boundingBox.right * scaleX + offsetX
                    val bottom = face.boundingBox.bottom * scaleY + offsetY
                    val width = right - left
                    val height = bottom - top
                    
                    // ✅ Debug log (sadece ilk yüz için)
                    if (detectedFaces.indexOf(face) == 0) {
                        android.util.Log.d("VideoAnalysis", "🎯 Yüz çerçevesi: " +
                                "Bitmap box=(${face.boundingBox.left},${face.boundingBox.top},${face.boundingBox.right},${face.boundingBox.bottom}), " +
                                "Canvas box=(${left.toInt()},${top.toInt()},${right.toInt()},${bottom.toInt()}), " +
                                "Scale=(${String.format("%.3f", scaleX)},${String.format("%.3f", scaleY)}), " +
                                "Offset=(${offsetX.toInt()},${offsetY.toInt()})")
                    }
                    
                    drawRect(
                        color = when (face.stressLevel) {
                            StressLevel.HIGH -> Color.Red
                            StressLevel.MEDIUM -> Color.Yellow
                            StressLevel.LOW -> Color.Green
                        },
                        topLeft = Offset(left, top),
                        size = Size(width, height),
                        style = Stroke(width = 5f)
                    )
                }
            }
        }

        // Yüz bilgileri - ✅ KOORDINAT DÖNÜŞÜMÜ (Canvas ile aynı mantık)
        val density = LocalDensity.current
        if (videoWidth > 0 && videoHeight > 0 && viewWidth > 0 && viewHeight > 0) {
            // ✅ Canvas ile aynı hesaplama (tutarlılık için)
            val bitmapAspect = videoWidth.toFloat() / videoHeight.toFloat()
            val viewAspect = viewWidth.toFloat() / viewHeight.toFloat()
            
            val scaleX: Float
            val scaleY: Float
            val offsetX: Float
            val offsetY: Float
            
            if (bitmapAspect > viewAspect) {
                // Bitmap daha geniş - yüksekliğe göre scale
                scaleY = viewHeight.toFloat() / videoHeight.toFloat()
                scaleX = scaleY
                offsetX = (viewWidth - videoWidth * scaleX) / 2f
                offsetY = 0f
            } else {
                // Bitmap daha yüksek - genişliğe göre scale
                scaleX = viewWidth.toFloat() / videoWidth.toFloat()
                scaleY = scaleX
                offsetX = 0f
                offsetY = (viewHeight - videoHeight * scaleY) / 2f
            }
            
            detectedFaces.forEach { face ->
                Box(
                    modifier = Modifier
                        .offset(
                            x = with(density) { (face.boundingBox.left * scaleX + offsetX).toFloat().toDp() },
                            y = with(density) { ((face.boundingBox.top - 60) * scaleY + offsetY).toFloat().toDp() }
                        )
                ) {
                    Column(
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.8f))
                            .padding(8.dp)
                    ) {
                        Text(
                            text = "Duygu: ${face.dominantEmotion}",
                            color = Color.Yellow,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "Stres: ${when (face.stressLevel) {
                                StressLevel.LOW -> "Düşük"
                                StressLevel.MEDIUM -> "Orta"
                                StressLevel.HIGH -> "Yüksek"
                            }}",
                            color = when (face.stressLevel) {
                                StressLevel.LOW -> Color(0xFF4CAF50)
                                StressLevel.MEDIUM -> Color(0xFFFF9800)
                                StressLevel.HIGH -> Color(0xFFF44336)
                            },
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        Button(onClick = onBack, modifier = Modifier.padding(16.dp)) {
            Text("Geri")
        }
    }
}

