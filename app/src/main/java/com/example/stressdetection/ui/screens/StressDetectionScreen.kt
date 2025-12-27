@file:OptIn(androidx.camera.core.ExperimentalGetImage::class)

package com.example.stressdetection.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.stressdetection.analyzer.StressImageAnalyzer
import com.example.stressdetection.model.FaceDetectionResult
import com.example.stressdetection.model.StressLevel
import com.example.stressdetection.ui.components.StressLevelIndicatorSimple
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

@Composable
fun StressDetectionScreen(requestPermission: (String) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    var stressLevel by remember { mutableStateOf<StressLevel?>(null) }
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    // İzin durumunu kontrol et
    LaunchedEffect(Unit) {
        val currentPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        hasPermission = currentPermission
        if (!hasPermission) {
            android.util.Log.d("StressDetection", "Kamera izni isteniyor...")
            requestPermission(Manifest.permission.CAMERA)
        } else {
            android.util.Log.d("StressDetection", "✅ Kamera izni mevcut")
        }
    }
    
    // İzin durumunu periyodik olarak kontrol et
    LaunchedEffect(Unit) {
        while (true) {
            delay(500) // Her 500ms'de bir kontrol et
            val currentPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
            if (currentPermission != hasPermission) {
                hasPermission = currentPermission
                android.util.Log.d("StressDetection", "İzin durumu güncellendi: $hasPermission")
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Box(modifier = Modifier.weight(1f)) {
            if (hasPermission) {
                CameraPreview(
                    onFaceDetected = { result -> 
                        stressLevel = result.stressLevel
                        // Logcat'te duygu durumunu göster
                        android.util.Log.d("StressDetection", "🎭 Duygu: ${result.dominantEmotion}, Stres: ${result.stressLevel}")
                    }
                )
            } else {
                Text(
                    "Kamera izni gerekli",
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            Button(
                onClick = onBack,
                modifier = Modifier.padding(16.dp).align(Alignment.TopStart)
            ) {
                Text("Geri")
            }
        }
        StressLevelIndicatorSimple(stressLevel, modifier = Modifier.padding(16.dp))
    }
}

@Composable
fun CameraPreview(onFaceDetected: (FaceDetectionResult) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }
    val analyzerExecutor = remember { Executors.newSingleThreadExecutor() }
    var faceResults by remember { mutableStateOf<List<FaceDetectionResult>>(emptyList()) }

    LaunchedEffect(Unit) {
        try {
            android.util.Log.d("CameraPreview", "📷 Kamera başlatılıyor...")
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            
            // Kamera provider'ı arka thread'de bekle
            val cameraProvider = withContext(Dispatchers.IO) {
                cameraProviderFuture.get()
            }
            
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

            val analyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                .build()
                .also {
                    it.setAnalyzer(
                        analyzerExecutor,
                        StressImageAnalyzer(context) { results ->
                            faceResults = results
                            // İlk yüzü callback'e gönder (stres seviyesi için)
                            if (results.isNotEmpty()) {
                                onFaceDetected(results[0])
                            }
                        }
                    )
                }

            cameraProvider.unbindAll()
            
            // Emülatörde lens facing bilgisi olmayabilir, bu yüzden önce DEFAULT kamera dene
            val cameraSelector = try {
                android.util.Log.d("CameraPreview", "📷 Ön kamera deneniyor...")
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    preview,
                    analyzer
                )
                android.util.Log.d("CameraPreview", "✅ Ön kamera başarıyla bağlandı")
                CameraSelector.DEFAULT_FRONT_CAMERA
            } catch (e: Exception) {
                android.util.Log.w("CameraPreview", "Ön kamera bulunamadı: ${e.message}")
                try {
                    android.util.Log.d("CameraPreview", "📷 Arka kamera deneniyor...")
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analyzer
                    )
                    android.util.Log.d("CameraPreview", "✅ Arka kamera başarıyla bağlandı")
                    CameraSelector.DEFAULT_BACK_CAMERA
                } catch (e2: Exception) {
                    android.util.Log.w("CameraPreview", "Arka kamera bulunamadı: ${e2.message}")
                    // Emülatör sorunu: Lens facing bilgisi yok
                    android.util.Log.e("CameraPreview", "❌ Emülatörde kamera kullanılamıyor")
                    android.util.Log.e("CameraPreview", "💡 ÇÖZÜM ADIMLARI:")
                    android.util.Log.e("CameraPreview", "   1. Android Studio'da AVD Manager'ı açın")
                    android.util.Log.e("CameraPreview", "   2. Emülatörünüzü seçin ve 'Edit' (kalem ikonu) tıklayın")
                    android.util.Log.e("CameraPreview", "   3. 'Show Advanced Settings' butonuna tıklayın")
                    android.util.Log.e("CameraPreview", "   4. 'Camera' bölümüne gidin")
                    android.util.Log.e("CameraPreview", "   5. 'Front Camera' ve 'Back Camera' için 'Webcam0' veya 'VirtualScene' seçin")
                    android.util.Log.e("CameraPreview", "   6. 'Finish' tıklayın ve emülatörü yeniden başlatın")
                    android.util.Log.e("CameraPreview", "")
                    android.util.Log.e("CameraPreview", "   VEYA:")
                    android.util.Log.e("CameraPreview", "   - Video analizi özelliğini kullanın (kamera yerine)")
                    android.util.Log.e("CameraPreview", "   - Farklı bir emülatör deneyin (Pixel 5, Pixel 6)")
                    
                    throw IllegalStateException(
                        "Emülatörde kamera kullanılamıyor.\n\n" +
                        "ÇÖZÜM:\n" +
                        "1. AVD Manager → Emülatörünüzü seçin → Edit\n" +
                        "2. Show Advanced Settings → Camera\n" +
                        "3. Front/Back Camera için 'Webcam0' seçin\n" +
                        "4. Emülatörü yeniden başlatın\n\n" +
                        "VEYA Video analizi özelliğini kullanın."
                    )
                }
            }
        } catch (exc: Exception) {
            android.util.Log.e("CameraPreview", "❌ Kamera başlatma hatası: ${exc.message}", exc)
            android.util.Log.e("CameraPreview", "Hata detayı: ${exc.stackTraceToString()}")
            exc.printStackTrace()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

        // Yüz çerçevesi ve duygu durumu gösterimi (TÜM YÜZLER)
        faceResults.forEach { result ->
            result.boundingBox?.let { rect ->
                val density = LocalDensity.current
                
                // Gerçek ImageProxy boyutlarını kullan (result'tan geliyor)
                val imgWidth = if (result.imageWidth > 0) result.imageWidth.toFloat() else 640f
                val imgHeight = if (result.imageHeight > 0) result.imageHeight.toFloat() else 480f
                
                // Çerçeve çiz (Video analizi gibi: Canvas içinde scale hesapla)
                Canvas(modifier = Modifier.fillMaxSize()) {
                    if (imgWidth > 0 && imgHeight > 0) {
                        // Video analizi gibi: Canvas boyutunu kullan
                        val scaleX = size.width / imgWidth
                        val scaleY = size.height / imgHeight
                        
                        val left = rect.left * scaleX
                        val top = rect.top * scaleY
                        val width = rect.width() * scaleX
                        val height = rect.height() * scaleY
                        
                        android.util.Log.d("CameraPreview", "🔍 Çerçeve çiziliyor: rect=(${rect.left},${rect.top},${rect.right},${rect.bottom}), " +
                                "canvas=${size.width.toInt()}x${size.height.toInt()}, scale=(${String.format("%.2f", scaleX)},${String.format("%.2f", scaleY)}), " +
                                "transformed=(${left.toInt()},${top.toInt()},${width.toInt()},${height.toInt()})")
                        
                        val color = when (result.stressLevel) {
                            StressLevel.HIGH -> Color.Red
                            StressLevel.MEDIUM -> Color.Yellow
                            StressLevel.LOW -> Color.Green
                        }
                        drawRect(
                            color = color,
                            topLeft = Offset(left, top),
                            size = Size(width, height),
                            style = Stroke(width = 5f)
                        )
                    }
                }
                
                // Duygu durumu metni (Video analizi gibi)
                if (imgWidth > 0 && imgHeight > 0) {
                    val previewWidth = previewView.width.toFloat()
                    val previewHeight = previewView.height.toFloat()
                    val scaleX = previewWidth / imgWidth
                    val scaleY = previewHeight / imgHeight
                    
                    Box(
                        modifier = Modifier
                            .offset(
                                x = with(density) { (rect.left * scaleX).toFloat().toDp() },
                                y = with(density) { ((rect.top - 60) * scaleY).toFloat().toDp() }
                            )
                    ) {
                        Column(
                            modifier = Modifier
                                .background(Color.Black.copy(alpha = 0.8f))
                                .padding(8.dp)
                        ) {
                            Text(
                                text = "Duygu: ${result.dominantEmotion}",
                                color = Color.Yellow,
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = "Stres: ${when (result.stressLevel) {
                                    StressLevel.LOW -> "Düşük"
                                    StressLevel.MEDIUM -> "Orta"
                                    StressLevel.HIGH -> "Yüksek"
                                }}",
                                color = when (result.stressLevel) {
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
        }
    }
}

