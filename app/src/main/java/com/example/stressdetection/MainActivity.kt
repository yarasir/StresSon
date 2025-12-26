@file:OptIn(
    androidx.camera.core.ExperimentalGetImage::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class
)

package com.example.stressdetection

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Rect
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.widget.VideoView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
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
import com.example.stressdetection.ui.theme.StressDetectionTheme
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.InterpreterOptions
import org.tensorflow.lite.flex.FlexDelegate
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.exp

// =======================
// ENUM & DATA CLASSES
// =======================
enum class StressLevel(val value: Int) {
    LOW(0), MEDIUM(1), HIGH(2)
}

data class FaceDetectionResult(
    val boundingBox: Rect?,
    val stressLevel: StressLevel,
    val dominantEmotion: String = "Neutral"
)

data class VideoFaceResult(
    val boundingBox: Rect,
    val stressLevel: StressLevel,
    val dominantEmotion: String
)

// =======================
// MAIN ACTIVITY
// =======================
class MainActivity : ComponentActivity() {
    private lateinit var cameraExecutor: ExecutorService
    private var onVideoSelected: ((Uri) -> Unit)? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    private val videoPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { videoUri ->
                onVideoSelected?.invoke(videoUri)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cameraExecutor = Executors.newSingleThreadExecutor()

        setContent {
            StressDetectionTheme {
                var showVideoAnalysis by remember { mutableStateOf(false) }
                var showCameraScreen by remember { mutableStateOf(false) }
                var currentVideoUri by remember { mutableStateOf<Uri?>(null) }

                LaunchedEffect(Unit) {
                    onVideoSelected = { uri ->
                        currentVideoUri = uri
                        showVideoAnalysis = true
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    when {
                        showVideoAnalysis && currentVideoUri != null -> {
                            VideoAnalysisScreen(
                                videoUri = currentVideoUri!!,
                                onBack = {
                                    showVideoAnalysis = false
                                    currentVideoUri = null
                                }
                            )
                        }
                        showCameraScreen -> {
                            StressDetectionScreen(
                                requestPermission = { permission ->
                                    requestPermissionLauncher.launch(permission)
                                },
                                onBack = { showCameraScreen = false }
                            )
                        }
                        else -> {
                            MainMenuScreen(
                                onVideoSelect = {
                                    val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                                        type = "video/*"
                                        addCategory(Intent.CATEGORY_OPENABLE)
                                    }
                                    videoPickerLauncher.launch(intent)
                                },
                                onCameraSelect = {
                                    if (ContextCompat.checkSelfPermission(
                                            this@MainActivity,
                                            Manifest.permission.CAMERA
                                        ) == PackageManager.PERMISSION_GRANTED
                                    ) {
                                        showCameraScreen = true
                                    } else {
                                        requestPermissionLauncher.launch(Manifest.permission.CAMERA)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}

// =======================
// SCREENS
// =======================
@Composable
fun MainMenuScreen(onVideoSelect: () -> Unit, onCameraSelect: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1E1E1E))
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Stres Tespiti", style = MaterialTheme.typography.headlineLarge, color = Color.White)
        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = onVideoSelect,
            modifier = Modifier.fillMaxWidth().height(64.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3))
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null)
            Spacer(modifier = Modifier.width(12.dp))
            Text("Video Seçin", style = MaterialTheme.typography.titleMedium)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onCameraSelect,
            modifier = Modifier.fillMaxWidth().height(64.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
        ) {
            Text("📷 Kamerayla Ölçün", style = MaterialTheme.typography.titleMedium)
        }
    }
}

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

    LaunchedEffect(Unit) {
        if (!hasPermission) requestPermission(Manifest.permission.CAMERA)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Box(modifier = Modifier.weight(1f)) {
            if (hasPermission) {
                CameraPreview(
                    onFaceDetected = { result -> stressLevel = result.stressLevel }
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
    var faceResult by remember { mutableStateOf<FaceDetectionResult?>(null) }

    LaunchedEffect(Unit) {
        val cameraProvider = ProcessCameraProvider.getInstance(context).get()
        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }

        val analyzer = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also {
                it.setAnalyzer(
                    analyzerExecutor,
                    StressImageAnalyzer(context) { result ->
                        faceResult = result
                        onFaceDetected(result)
                    }
                )
            }

        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_FRONT_CAMERA,
                preview,
                analyzer
            )
        } catch (exc: Exception) {
            exc.printStackTrace()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

        faceResult?.boundingBox?.let { rect ->
            FaceOverlay(
                rect,
                faceResult!!.stressLevel,
                faceResult!!.dominantEmotion,
                Modifier.fillMaxSize()
            )
        }
    }
}

// =======================
// ANALYZER
// =======================
class StressImageAnalyzer(
    private val context: Context,
    private val onResult: (FaceDetectionResult) -> Unit = {}
) : ImageAnalysis.Analyzer {

    private val MODEL_FILE_NAME = "final_stress_model_flex.tflite"

    private var interpreter: Interpreter? = null
    private var inputBuffer: ByteBuffer? = null
    private val inputImageSize = 224

    private val faceDetector: FaceDetector by lazy {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .enableTracking()
            .setMinFaceSize(0.15f)
            .build()
        FaceDetection.getClient(options)
    }

    private var lastAnalyzeTime = 0L
    @Volatile
    private var isProcessing = false

    init {
        try {
            val model = loadModelFile(MODEL_FILE_NAME)
            
            // ✅ FLEX DELEGATE EKLE: final_stress_model_flex.tflite için gerekli
            val options = InterpreterOptions().apply {
                addDelegate(FlexDelegate())
            }
            
            interpreter = Interpreter(model, options)
            inputBuffer = ByteBuffer.allocateDirect(4 * inputImageSize * inputImageSize * 3).apply {
                order(ByteOrder.nativeOrder())
            }
            android.util.Log.d("StressAnalyzer", "✅ Model yüklendi: $MODEL_FILE_NAME (Flex Ops aktif)")
        } catch (e: Exception) {
            android.util.Log.e("StressAnalyzer", "❌ Model yüklenemedi: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun loadModelFile(path: String): MappedByteBuffer {
        val fd = context.assets.openFd(path)
        return FileInputStream(fd.fileDescriptor).channel.map(
            FileChannel.MapMode.READ_ONLY,
            fd.startOffset,
            fd.declaredLength
        )
    }

    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        if (interpreter == null) {
            imageProxy.close()
            return
        }

        val now = System.currentTimeMillis()
        if (now - lastAnalyzeTime < 150 || isProcessing) {
            imageProxy.close()
            return
        }
        isProcessing = true
        lastAnalyzeTime = now

        try {
            val rotationDegrees = imageProxy.imageInfo.rotationDegrees
            val bitmap = imageProxy.toBitmap()
            val rotatedBitmap = rotateBitmap(bitmap, rotationDegrees)
            val inputImage = InputImage.fromBitmap(rotatedBitmap, 0)

            faceDetector.process(inputImage)
                .addOnSuccessListener { faces ->
                    val face = faces.firstOrNull()
                    if (face != null) {
                        val faceBitmap = cropFace(rotatedBitmap, face.boundingBox)
                        val result = runInference(faceBitmap)
                        onResult(
                            FaceDetectionResult(
                                face.boundingBox,
                                result.first,
                                result.second
                            )
                        )
                    } else {
                        onResult(FaceDetectionResult(null, StressLevel.LOW, "Yüz yok"))
                    }
                }
                .addOnCompleteListener {
                    isProcessing = false
                    imageProxy.close()
                }
        } catch (e: Exception) {
            isProcessing = false
            imageProxy.close()
        }
    }

    fun runInference(bitmap: Bitmap): Pair<StressLevel, String> {
        if (interpreter == null || inputBuffer == null) {
            android.util.Log.e("StressAnalyzer", "❌ Interpreter veya inputBuffer null!")
            return Pair(StressLevel.LOW, "Model yüklenemedi")
        }

        try {
            android.util.Log.d("StressAnalyzer", "🔵 runInference başladı, bitmap: ${bitmap.width}x${bitmap.height}")
            val scaledBitmap = Bitmap.createScaledBitmap(bitmap, inputImageSize, inputImageSize, true)
            inputBuffer!!.rewind()

            val pixels = IntArray(inputImageSize * inputImageSize)
            scaledBitmap.getPixels(pixels, 0, inputImageSize, 0, 0, inputImageSize, inputImageSize)

            // ✅ KRİTİK DÜZELTME: EfficientNetB0 modeli 0-255 aralığında RAW PIXEL değerleri bekliyor!
            // Model kendi içinde normalize ediyor, biz normalize ETMEMELİYİZ!
            // Eğer normalize edersek (ör: 128 -> 0.5), model bunu "simsiyah görüntü" olarak algılar
            // ve dataset'in en baskın sınıfı olan Sadness'ı basar.
            
            var rSum = 0f
            var gSum = 0f
            var bSum = 0f
            
            for (pixel in pixels) {
                val r = ((pixel shr 16) and 0xFF).toFloat()  // [0,255]
                val g = ((pixel shr 8) and 0xFF).toFloat()   // [0,255]
                val b = (pixel and 0xFF).toFloat()            // [0,255]
                
                // ✅ NORMALIZE ETME! Model 0-255 aralığında raw pixel bekliyor
                // ÖNCEKİ YANLIŞ: val normalizedR = (r / 127.5f) - 1f  // [0,255] -> [-1,1]
                // ŞİMDİ DOĞRU: Direkt 0-255 aralığında gönder
                
                rSum += r
                gSum += g
                bSum += b
                
                inputBuffer!!.putFloat(r)  // [0,255] aralığında
                inputBuffer!!.putFloat(g)  // [0,255] aralığında
                inputBuffer!!.putFloat(b)  // [0,255] aralığında
            }
            
            // ✅ DEBUG: Preprocessing istatistikleri
            val pixelCount = pixels.size
            android.util.Log.d("StressAnalyzer", "🔍 Preprocessing: RAW PIXEL [0,255] (normalize YOK!)")
            android.util.Log.d("StressAnalyzer", "   Ortalama: r=${String.format("%.1f", rSum/pixelCount)}, g=${String.format("%.1f", gSum/pixelCount)}, b=${String.format("%.1f", bSum/pixelCount)}")

            inputBuffer!!.rewind()

            // Model çıktısını al
            val outputSize = 7 // 7 emotion sınıfı
            val outputBuffer = ByteBuffer.allocateDirect(4 * outputSize).apply {
                order(ByteOrder.nativeOrder())
            }

            interpreter!!.run(inputBuffer, outputBuffer)

            outputBuffer.rewind()
            val rawOutput = FloatArray(outputSize)
            for (i in 0 until outputSize) {
                rawOutput[i] = outputBuffer.getFloat()
            }

            // ✅ DEBUG: Raw output'u logla
            android.util.Log.d("StressAnalyzer", "🔍 Raw output: [${rawOutput.joinToString(", ") { String.format("%.4f", it) }}]")
            android.util.Log.d("StressAnalyzer", "🔍 Raw output sum: ${rawOutput.sum()}")

            // ✅ Softmax kontrolü: Eğer toplam ~1.0 değilse logits'tir
            val isLogits = rawOutput.sum() < 0.9f || rawOutput.sum() > 1.1f || rawOutput.any { it < -10f || it > 10f }
            val probs = if (isLogits) {
                android.util.Log.d("StressAnalyzer", "✅ Logits tespit edildi, softmax uygulanıyor")
                softmax(rawOutput)
            } else {
                android.util.Log.d("StressAnalyzer", "✅ Zaten probabilities (softmax uygulanmış)")
                rawOutput
            }
            
            android.util.Log.d("StressAnalyzer", "🔍 Final probabilities sum: ${probs.sum()}")

            // ✅ KRİTİK: Colab kodundaki class order:
            // 0: surprise, 1: fear, 2: disgust, 3: happiness, 4: sadness, 5: anger, 6: neutral
            val pSurprise = probs[0]  // Colab: 0
            val pFear = probs[1]      // Colab: 1
            val pDisgust = probs[2]   // Colab: 2
            val pHappiness = probs[3] // Colab: 3
            val pSadness = probs[4]    // Colab: 4
            val pAnger = probs[5]     // Colab: 5
            val pNeutral = probs[6]   // Colab: 6

            // ✅ DEBUG: Tüm emotion olasılıklarını logla (TEK SATIRDA - TÜM 7 DUYGU)
            android.util.Log.d("StressAnalyzer", "📊 Tüm 7 Duygu: Surprise=${String.format("%.1f%%", pSurprise*100)}, Fear=${String.format("%.1f%%", pFear*100)}, Disgust=${String.format("%.1f%%", pDisgust*100)}, Happiness=${String.format("%.1f%%", pHappiness*100)}, Sadness=${String.format("%.1f%%", pSadness*100)}, Anger=${String.format("%.1f%%", pAnger*100)}, Neutral=${String.format("%.1f%%", pNeutral*100)}")

            // =================================================================================
            // 🔧 STRES HASSASİYET AYARI v6 (Mutluluk Boost + Anger Freni)
            // =================================================================================
            
            // 1. Happiness Boost: KAREKÖK YÖNTEMİ (Square Root)
            // Modelin cimri davrandığı küçük mutlulukları parlatır.
            // Örnek: %4 --> %20 olur. %1 --> %10 olur.
            var adjHappiness = kotlin.math.sqrt(pHappiness.toDouble()).toFloat()
            if (adjHappiness > 1.0f) adjHappiness = 1.0f
            
            // 2. Anger Suppression: ÇAPRAZ BASKILAMA (Mutluluk Varsa Sinir Yoktur)
            var adjAnger = pAnger
            
            // KURAL: Eğer Mutluluk (adj) %15'in üzerindeyse, Anger muhtemelen hatadır.
            // Anger'ı %70 oranında yok et (0.3 ile çarp).
            if (adjHappiness > 0.15f) {
                adjAnger = pAnger * 0.3f
            }
            // KURAL 2: Eğer Nötr çok baskınsa (%40+), Anger'ı yine yarıla.
            else if (pNeutral > 0.40f) {
                adjAnger = pAnger * 0.5f
            }
            
            // ✅ LOGLAMA
            android.util.Log.d("StressAnalyzer", "🔧 v6 AYAR: RealHap=${String.format("%.2f", pHappiness)} -> BoostHap=${String.format("%.2f", adjHappiness)}")
            android.util.Log.d("StressAnalyzer", "   RAW Anger=${String.format("%.2f", pAnger)} -> ADJ Anger=${String.format("%.2f", adjAnger)}")

            // =================================================================================
            // 🧮 STRES FORMÜLÜ (Mevcut memnuniyetini bozmadan)
            // =================================================================================
            
            // 3. Stres Yükü (Negative Load)
            // Anger artık "frenlenmiş" (adjAnger) haliyle giriyor, patlama yapamaz.
            val negativeLoad = (pFear * 1.0f) + (adjAnger * 1.0f) + (pDisgust * 0.8f) + (pSadness * 0.5f)
            
            // 4. Rahatlama Yükü (Positive Load)
            // DİKKAT: adjHappiness'i suni olarak artırdığımız için, buradaki katsayısını 
            // 1.0'dan 0.5'e düşürdük. Böylece stres skoru "gereğinden fazla" düşmeyecek.
            // Senin memnun olduğun denge korunacak.
            val positiveLoad = (adjHappiness * 0.5f) + (pNeutral * 0.4f) + (pSurprise * 0.1f)
            
            // Net Skor
            var rawScore = negativeLoad - positiveLoad
            
            // Sınırlandırma (Clamping)
            if (rawScore < 0f) rawScore = 0f
            if (rawScore > 1f) rawScore = 1f
            
            // ✅ DEBUG: Stres skoru detayı
            android.util.Log.d("StressAnalyzer", "🔍 Stres skoru detayı (v6 - Mutluluk Boost):")
            android.util.Log.d("StressAnalyzer", "   🔴 Neg: ${String.format("%.3f", negativeLoad)} (Fear×1.0 + Anger×1.0 + Disgust×0.8 + Sadness×0.5)")
            android.util.Log.d("StressAnalyzer", "   🟢 Poz: ${String.format("%.3f", positiveLoad)} (Happiness×0.5 + Neutral×0.4 + Surprise×0.1)")
            android.util.Log.d("StressAnalyzer", "   🧮 Net Skor: ${String.format("%.3f", rawScore)}")

            // 5. Eşik Değerleri (Aynı kaldı - Memnunsun diye dokunmadık)
            val stressLevel = when {
                rawScore > 0.45f -> StressLevel.HIGH
                rawScore > 0.15f -> StressLevel.MEDIUM
                else -> StressLevel.LOW
            }
            
            android.util.Log.d("StressAnalyzer", "🎯 Stres seviyesi: $stressLevel (score=${String.format("%.3f", rawScore)})")

            // ✅ DOMINANT EMOTION GÜNCELLEME
            // Ekranda görünen duygu için de "boostlanmış" mutluluğu kullanalım ki
            // kullanıcı "Neden stresim düşük ama ekranda Nötr yazıyor?" demesin.
            val displayProbs = probs.clone()
            displayProbs[3] = adjHappiness // Mutluluğu güncelledik (index 3 = Happiness)
            displayProbs[5] = adjAnger     // Kızgınlığı güncelledik (index 5 = Anger)
            
            // Yeni dominant bul
            val emotions = listOf("Surprise", "Fear", "Disgust", "Happiness", "Sadness", "Anger", "Neutral")
            val maxIdx = displayProbs.indices.maxByOrNull { displayProbs[it] } ?: 3
            val dominant = "${emotions[maxIdx]} ${(displayProbs[maxIdx] * 100).toInt()}%"
            
            android.util.Log.d("StressAnalyzer", "🎭 Dominant emotion (boostlanmış): $dominant (index=$maxIdx, prob=${String.format("%.3f", displayProbs[maxIdx])})")

            return Pair(stressLevel, dominant)
        } catch (e: Exception) {
            android.util.Log.e("StressAnalyzer", "Inference hatası: ${e.message}")
            return Pair(StressLevel.LOW, "Hata")
        }
    }

    private fun cropFace(bitmap: Bitmap, box: Rect): Bitmap {
        val left = box.left.coerceAtLeast(0)
        val top = box.top.coerceAtLeast(0)
        val width = box.width().coerceAtMost(bitmap.width - left)
        val height = box.height().coerceAtMost(bitmap.height - top)
        return Bitmap.createBitmap(bitmap, left, top, width, height)
    }

    private fun rotateBitmap(bitmap: Bitmap, degrees: Int): Bitmap {
        if (degrees == 0) return bitmap
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun softmax(logits: FloatArray): FloatArray {
        val max = logits.maxOrNull() ?: 0f
        val expVals = logits.map { exp(it - max) }
        val sum = expVals.sum()
        return expVals.map { (it / sum).toFloat() }.toFloatArray()
    }
}

// =======================
// VIDEO ANALYSIS
// =======================
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
            .setMinFaceSize(0.15f)
            .build()
        FaceDetection.getClient(opts)
    }

    LaunchedEffect(videoView) {
        // ✅ Retriever'ı bir kere oluştur, loop boyunca reuse et
        val retriever = MediaMetadataRetriever().apply {
            try {
                setDataSource(context, videoUri)
                android.util.Log.d("VideoAnalysis", "✅ Retriever oluşturuldu")
            } catch (e: Exception) {
                android.util.Log.e("VideoAnalysis", "❌ Retriever setDataSource hatası: ${e.message}", e)
            }
        }
        
        try {
            while (isActive && videoView != null && videoView!!.isPlaying) {
                delay(500) // Her 0.5 saniyede bir analiz
                try {
                    val timeUs = videoView!!.currentPosition * 1000L
                    val frame = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)

                    frame?.let { bitmap ->
                        // Video boyutlarını bir kere al
                        if (videoWidth == 0 || videoHeight == 0) {
                            videoWidth = bitmap.width
                            videoHeight = bitmap.height
                            android.util.Log.d("VideoAnalysis", "Video boyutları: ${bitmap.width}x${bitmap.height}")
                        }
                        
                        val inputImage = InputImage.fromBitmap(bitmap, 0)
                        faceDetector.process(inputImage)
                            .addOnSuccessListener { faces ->
                                // ✅ BOŞ LİSTE KONTROLÜ: Eğer yüz yoksa boş liste döndür
                                if (faces.isEmpty()) {
                                    detectedFaces = emptyList()
                                    return@addOnSuccessListener
                                }

                                val results = mutableListOf<VideoFaceResult>()
                                for (face in faces) {
                                    try {
                                        val faceBitmap = Bitmap.createBitmap(
                                            bitmap,
                                            face.boundingBox.left.coerceAtLeast(0),
                                            face.boundingBox.top.coerceAtLeast(0),
                                            face.boundingBox.width()
                                                .coerceAtMost(bitmap.width - face.boundingBox.left),
                                            face.boundingBox.height()
                                                .coerceAtMost(bitmap.height - face.boundingBox.top)
                                        )
                                        val inference = analyzer.runInference(faceBitmap)
                                        results.add(
                                            VideoFaceResult(
                                                face.boundingBox,
                                                inference.first,
                                                inference.second
                                            )
                                        )
                                    } catch (e: Exception) {
                                        android.util.Log.e("VideoAnalysis", "Yüz işleme hatası: ${e.message}")
                                    }
                                }
                                detectedFaces = results
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
                VideoView(ctx).apply {
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
                        start()
                    }
                    addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                        viewWidth = width
                        viewHeight = height
                        android.util.Log.d("VideoAnalysis", "View boyutları: ${width}x${height}")
                    }
                    videoView = this
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Yüz overlay - ✅ KOORDINAT DÖNÜŞÜMÜ: Bitmap koordinatlarını view koordinatlarına çevir
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (videoWidth > 0 && videoHeight > 0 && viewWidth > 0 && viewHeight > 0) {
                // Scale hesapla (video boyutları -> view boyutları)
                val scaleX = size.width / videoWidth.toFloat()
                val scaleY = size.height / videoHeight.toFloat()
                
                detectedFaces.forEach { face ->
                    val left = face.boundingBox.left * scaleX
                    val top = face.boundingBox.top * scaleY
                    val width = face.boundingBox.width() * scaleX
                    val height = face.boundingBox.height() * scaleY
                    
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

        // Yüz bilgileri - ✅ KOORDINAT DÖNÜŞÜMÜ
        val density = LocalDensity.current
        if (videoWidth > 0 && videoHeight > 0 && viewWidth > 0 && viewHeight > 0) {
            val scaleX = viewWidth.toFloat() / videoWidth.toFloat()
            val scaleY = viewHeight.toFloat() / videoHeight.toFloat()
            
            detectedFaces.forEach { face ->
                Box(
                    modifier = Modifier
                        .offset(
                            x = with(density) { (face.boundingBox.left * scaleX).toFloat().toDp() },
                            y = with(density) { ((face.boundingBox.top - 60) * scaleY).toFloat().toDp() }
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

// =======================
// UI COMPONENTS
// =======================
@Composable
fun FaceOverlay(rect: Rect, level: StressLevel, emotion: String, modifier: Modifier) {
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

// =======================
// ImageProxy -> Bitmap
// =======================
@androidx.annotation.OptIn(ExperimentalGetImage::class)
fun ImageProxy.toBitmap(): Bitmap {
    val image = image ?: throw IllegalStateException("Image is null")
    val yBuffer = image.planes[0].buffer
    val uBuffer = image.planes[1].buffer
    val vBuffer = image.planes[2].buffer

    val ySize = yBuffer.remaining()
    val uSize = uBuffer.remaining()
    val vSize = vBuffer.remaining()

    val nv21 = ByteArray(ySize + uSize + vSize)
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
