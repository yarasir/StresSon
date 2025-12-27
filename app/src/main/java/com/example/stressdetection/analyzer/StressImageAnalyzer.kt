package com.example.stressdetection.analyzer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Rect
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.example.stressdetection.model.FaceDetectionResult
import com.example.stressdetection.model.StressLevel
import com.example.stressdetection.utils.toBitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.Interpreter.Options
import org.tensorflow.lite.flex.FlexDelegate
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.exp

class StressImageAnalyzer(
    private val context: Context,
    private val onResult: (List<FaceDetectionResult>) -> Unit = {}
) : ImageAnalysis.Analyzer {

    private val MODEL_FILE_NAME = "model.tflite"

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
    
    // ✅ Temporal smoothing: Duygu değerlerini yumuşatmak için (frame'ler arası tutarlılık)
    private var smoothedProbs: FloatArray? = null
    private val smoothingAlpha = 0.65f  // 0.65 = Yeni frame %65, eski frame %35 (daha stabil)

    init {
        android.util.Log.d("StressAnalyzer", "🔵 StressImageAnalyzer init başladı...")
        try {
            android.util.Log.d("StressAnalyzer", "📦 Model dosyası yükleniyor: $MODEL_FILE_NAME")
            val model = loadModelFile(MODEL_FILE_NAME)
            android.util.Log.d("StressAnalyzer", "✅ Model dosyası yüklendi, interpreter oluşturuluyor...")
            
            // ✅ FLEX DELEGATE EKLE: model.tflite için gerekli
            android.util.Log.d("StressAnalyzer", "🔧 FlexDelegate ekleniyor...")
            val options = Options()
            try {
                options.addDelegate(FlexDelegate())
                android.util.Log.d("StressAnalyzer", "✅ FlexDelegate eklendi")
            } catch (e: Exception) {
                android.util.Log.w("StressAnalyzer", "⚠️ FlexDelegate eklenemedi, devam ediliyor: ${e.message}")
            }
            
            android.util.Log.d("StressAnalyzer", "🔧 Interpreter oluşturuluyor...")
            interpreter = Interpreter(model, options)
            android.util.Log.d("StressAnalyzer", "✅ Interpreter oluşturuldu")
            
            inputBuffer = ByteBuffer.allocateDirect(4 * inputImageSize * inputImageSize * 3).apply {
                order(ByteOrder.nativeOrder())
            }
            android.util.Log.d("StressAnalyzer", "✅ Input buffer oluşturuldu")
            android.util.Log.d("StressAnalyzer", "✅✅✅ Model başarıyla yüklendi: $MODEL_FILE_NAME (Flex Ops aktif)")
        } catch (e: Exception) {
            android.util.Log.e("StressAnalyzer", "❌❌❌ Model yüklenemedi: ${e.message}")
            android.util.Log.e("StressAnalyzer", "❌ Hata detayı: ${e.stackTraceToString()}")
            e.printStackTrace()
            interpreter = null
        }
    }

    private fun loadModelFile(path: String): MappedByteBuffer {
        try {
            android.util.Log.d("StressAnalyzer", "Model dosyası yükleniyor: $path")
            val fd = context.assets.openFd(path)
            val model = FileInputStream(fd.fileDescriptor).channel.map(
                FileChannel.MapMode.READ_ONLY,
                fd.startOffset,
                fd.declaredLength
            )
            android.util.Log.d("StressAnalyzer", "Model dosyası başarıyla yüklendi (${model.capacity()} bytes)")
            return model
        } catch (e: Exception) {
            android.util.Log.e("StressAnalyzer", "Model dosyası yüklenirken hata: ${e.message}", e)
            throw e
        }
    }

    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        if (interpreter == null) {
            android.util.Log.w("StressAnalyzer", "⚠️ Interpreter null, analiz atlanıyor")
            imageProxy.close()
            return
        }

        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            android.util.Log.w("StressAnalyzer", "⚠️ MediaImage null, analiz atlanıyor")
            imageProxy.close()
            return
        }

        // 1. ML Kit için: Video analizi gibi Bitmap kullan (daha iyi sonuçlar için)
        // Video analizinde InputImage.fromBitmap kullanılıyor ve Anger/Happiness daha iyi çalışıyor
        // Canlı kamera analizinde de aynı yaklaşımı kullanıyoruz
        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        val bitmap = imageProxy.toBitmap()
        val rotatedBitmap = if (rotationDegrees != 0) {
            rotateBitmap(bitmap, rotationDegrees)
        } else {
            bitmap
        }
        val inputImage = InputImage.fromBitmap(rotatedBitmap, 0) // Video analizi gibi

        val now = System.currentTimeMillis()
        if (now - lastAnalyzeTime < 150 || isProcessing) {
            imageProxy.close()
            return
        }

        isProcessing = true
        lastAnalyzeTime = now

        // NOT: Bitmap çevrimini SADECE yüz bulunduktan sonra yapacağız.
        // Çünkü her kareyi Bitmap'e çevirmek CPU'yu öldürür.

        android.util.Log.d("StressAnalyzer", "🔍 Yüz tespiti başlatılıyor (Bitmap ile, video analizi gibi)...")
        faceDetector.process(inputImage)
            .addOnSuccessListener { faces ->
                android.util.Log.d("StressAnalyzer", "👤 ${faces.size} yüz tespit edildi")
                if (faces.isEmpty()) {
                    android.util.Log.d("StressAnalyzer", "❌ Yüz bulunamadı")
                    onResult(emptyList())
                    return@addOnSuccessListener
                }
                
                // ✅ TÜM YÜZLERİ İŞLE
                val results = mutableListOf<FaceDetectionResult>()
                for (face in faces) {
                    try {
                        // ✅ Video analizi gibi: Bitmap zaten hazır, direkt kullan
                        android.util.Log.d("StressAnalyzer", "✅ Yüz işleniyor: ${face.boundingBox}")
                        
                        // ML Kit koordinatları rotatedBitmap'e göre doğrudur (video analizi gibi)
                        // ✅ Video analizi gibi: Padding yok, direkt crop (daha iyi sonuçlar için)
                        val faceBitmap = cropFace(rotatedBitmap, face.boundingBox)
                        
                        android.util.Log.d("StressAnalyzer", "🔍 Bitmap boyutları: rotated=${rotatedBitmap.width}x${rotatedBitmap.height}, face=${faceBitmap.width}x${faceBitmap.height}")

                        val inferenceResult = runInference(faceBitmap)
                        android.util.Log.d("StressAnalyzer", "📊 Sonuç: ${inferenceResult.first} - ${inferenceResult.second}")
                        results.add(
                            FaceDetectionResult(
                                face.boundingBox,
                                inferenceResult.first,
                                inferenceResult.second,
                                rotatedBitmap.width,
                                rotatedBitmap.height
                            )
                        )
                    } catch (e: Exception) {
                        android.util.Log.e("StressAnalyzer", "❌ Yüz işleme hatası: ${e.message}", e)
                    }
                }
                onResult(results)
            }
            .addOnFailureListener { e ->
                val errorMsg = e.message ?: "Bilinmeyen hata"
                android.util.Log.e("StressAnalyzer", "❌ Yüz tespiti hatası: $errorMsg", e)
                
                // ML Kit modül indirme hatası için özel mesaj
                if (errorMsg.contains("Waiting for the face module") || 
                    errorMsg.contains("face module to be downloaded")) {
                    android.util.Log.w("StressAnalyzer", "💡 ML Kit Face Detection modülü indiriliyor...")
                    android.util.Log.w("StressAnalyzer", "💡 Lütfen birkaç saniye bekleyin ve tekrar deneyin")
                    android.util.Log.w("StressAnalyzer", "💡 İnternet bağlantınızın olduğundan emin olun")
                }
            }
            .addOnCompleteListener {
                isProcessing = false
                // KRİTİK: ImageProxy'yi mutlaka kapatmalıyız, yoksa kamera donar.
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
            
            // ✅ Temporal Smoothing: Frame'ler arası tutarlılık için exponential moving average
            if (smoothedProbs == null) {
                // İlk frame: direkt kullan
                smoothedProbs = probs.clone()
            } else {
                // Sonraki frame'ler: EMA ile yumuşat
                for (i in probs.indices) {
                    smoothedProbs!![i] = smoothingAlpha * probs[i] + (1f - smoothingAlpha) * smoothedProbs!![i]
                }
            }
            
            // Smoothing sonrası değerleri kullan (daha stabil)
            val finalProbs = smoothedProbs!!
            
            // ✅ KRİTİK: Colab kodundaki class order:
            // 0: surprise, 1: fear, 2: disgust, 3: happiness, 4: sadness, 5: anger, 6: neutral
            var pSurprise = finalProbs[0]  // Colab: 0
            var pFear = finalProbs[1]      // Colab: 1
            var pDisgust = finalProbs[2]   // Colab: 2
            var pHappiness = finalProbs[3] // Colab: 3
            var pSadness = finalProbs[4]    // Colab: 4
            var pAnger = finalProbs[5]     // Colab: 5
            var pNeutral = finalProbs[6]   // Colab: 6
            
            // ✅ Neutral redistribution: Sadece Anger için (Sadness redistribution kaldırıldı)
            // Model bazen Anger'ı Neutral olarak sınıflandırıyor, bu yüzden "düzeltiyoruz"
            // Anger > 5% ise ve Neutral > 8% ise → Neutral'dan Anger'a transfer
            val angerThreshold = 0.05f   // Anger > 5% ise Neutral'dan al (daha agresif)
            val neutralThreshold = 0.08f  // Neutral > 8% olmalı (10%'dan düşürüldü - daha kolay tetiklenir)
            val transferRatio = 0.45f     // Neutral'ın %45'ini transfer et (daha fazla transfer)
            
            if (pAnger > angerThreshold && pNeutral > neutralThreshold) {
                // Anger yüksek ve Neutral da yeterliyse → Neutral'dan Anger'a transfer
                val transfer = pNeutral * transferRatio
                val oldAnger = pAnger
                val oldNeutral = pNeutral
                pAnger += transfer
                pNeutral -= transfer
                android.util.Log.d("StressAnalyzer", "🔄 Neutral → Anger: ${String.format("%.2f", transfer*100)}% " +
                        "(Anger: ${String.format("%.1f", oldAnger*100)}% → ${String.format("%.1f", pAnger*100)}%, " +
                        "Neutral: ${String.format("%.1f", oldNeutral*100)}% → ${String.format("%.1f", pNeutral*100)}%)")
            } else {
                // Debug: Neden redistribution yapılmadı?
                if (pAnger <= angerThreshold) {
                    android.util.Log.d("StressAnalyzer", "⏭️ Redistribution atlandı: Anger=${String.format("%.1f", pAnger*100)}% <= ${angerThreshold*100}%")
                }
                if (pNeutral <= neutralThreshold) {
                    android.util.Log.d("StressAnalyzer", "⏭️ Redistribution atlandı: Neutral=${String.format("%.1f", pNeutral*100)}% <= ${neutralThreshold*100}%")
                }
            }

            // ✅ DEBUG: Tüm emotion olasılıklarını logla (TEK SATIRDA - TÜM 7 DUYGU)
            // RAW (smoothing öncesi) ve SMOOTHED (smoothing sonrası) değerleri göster
            android.util.Log.d("StressAnalyzer", "📊 RAW 7 Duygu: Surprise=${String.format("%.1f%%", probs[0]*100)}, Fear=${String.format("%.1f%%", probs[1]*100)}, Disgust=${String.format("%.1f%%", probs[2]*100)}, Happiness=${String.format("%.1f%%", probs[3]*100)}, Sadness=${String.format("%.1f%%", probs[4]*100)}, Anger=${String.format("%.1f%%", probs[5]*100)}, Neutral=${String.format("%.1f%%", probs[6]*100)}")
            android.util.Log.d("StressAnalyzer", "📊 SMOOTHED 7 Duygu: Surprise=${String.format("%.1f%%", pSurprise*100)}, Fear=${String.format("%.1f%%", pFear*100)}, Disgust=${String.format("%.1f%%", pDisgust*100)}, Happiness=${String.format("%.1f%%", pHappiness*100)}, Sadness=${String.format("%.1f%%", pSadness*100)}, Anger=${String.format("%.1f%%", pAnger*100)}, Neutral=${String.format("%.1f%%", pNeutral*100)}")

            // =================================================================================
            // 🔧 STRES HASSASİYET AYARI v6 (Mutluluk Boost + Anger Freni)
            // =================================================================================
            
            // 1. Happiness Boost: KAREKÖK YÖNTEMİ (Square Root)
            // Modelin cimri davrandığı küçük mutlulukları parlatır.
            // Örnek: %4 --> %20 olur. %1 --> %10 olur.
            var adjHappiness = kotlin.math.sqrt(pHappiness.toDouble()).toFloat()
            if (adjHappiness > 1.0f) adjHappiness = 1.0f
            
            // ✅ Anger frenleme kaldırıldı - direkt pAnger kullanılıyor
            // Önceki: adjAnger ile frenleniyordu (mutluluk varsa %70 azaltılıyordu)
            // Şimdi: Direkt pAnger kullanılıyor (frenleme yok)
            
            // ✅ LOGLAMA
            android.util.Log.d("StressAnalyzer", "🔧 v7 AYAR: RealHap=${String.format("%.2f", pHappiness)} -> BoostHap=${String.format("%.2f", adjHappiness)}")
            android.util.Log.d("StressAnalyzer", "   RAW Anger=${String.format("%.2f", pAnger)} (frenleme YOK)")

            // =================================================================================
            // 🧮 STRES FORMÜLÜ (Mevcut memnuniyetini bozmadan)
            // =================================================================================
            
            // 3. Stres Yükü (Negative Load)
            // ✅ Fear katsayısı 1.0'dan 2.0'a çıkarıldı (Fear çok düşük, daha agresif boost)
            // Anger katsayısı 1.5'te (zaten yüksek)
            // Sadness katsayısı 0.5'ten 0.3'e düşürüldü (patlama yapmasın diye)
            // Surprise artık stresi artırıyor (pozitif yükten çıkarıldı, negatif yüke eklendi)
            val negativeLoad = (pFear * 2.0f) + (pAnger * 1.5f) + (pDisgust * 0.8f) + (pSadness * 0.3f) + (pSurprise * 0.2f)
            
            // 4. Rahatlama Yükü (Positive Load)
            // DİKKAT: adjHappiness'i suni olarak artırdığımız için, buradaki katsayısını 
            // 1.0'dan 0.5'e düşürdük. Böylece stres skoru "gereğinden fazla" düşmeyecek.
            // Surprise artık pozitif yükten çıkarıldı (stresi artırsın diye)
            // ✅ Neutral katsayısı 0.4'ten 0.15'e düşürüldü (Neutral patlaması yapıyordu, Anger/Fear maskeliyordu)
            val positiveLoad = (adjHappiness * 0.5f) + (pNeutral * 0.15f)
            
            // Net Skor
            var rawScore = negativeLoad - positiveLoad
            
            // Sınırlandırma (Clamping)
            if (rawScore < 0f) rawScore = 0f
            if (rawScore > 1f) rawScore = 1f
            
            // ✅ DEBUG: Stres skoru detayı
            android.util.Log.d("StressAnalyzer", "🔍 Stres skoru detayı (v10 - Neutral redistribution eklendi):")
            android.util.Log.d("StressAnalyzer", "   🔴 Neg: ${String.format("%.3f", negativeLoad)} (Fear×2.0 + Anger×1.5 + Disgust×0.8 + Sadness×0.3 + Surprise×0.2)")
            android.util.Log.d("StressAnalyzer", "   🟢 Poz: ${String.format("%.3f", positiveLoad)} (Happiness×0.5 + Neutral×0.15)")
            android.util.Log.d("StressAnalyzer", "   🧮 Net Skor: ${String.format("%.3f", rawScore)}")

            // 5. Eşik Değerleri (Hafifçe ayarlandı - HIGH eşiği 0.45'ten 0.38'e düşürüldü)
            // Böylece 0.38-0.45 arası skorlar da HIGH olarak işaretlenir
            val stressLevel = when {
                rawScore > 0.38f -> StressLevel.HIGH  // Önceki: 0.45f
                rawScore > 0.15f -> StressLevel.MEDIUM
                else -> StressLevel.LOW
            }
            
            android.util.Log.d("StressAnalyzer", "🎯 Stres seviyesi: $stressLevel (score=${String.format("%.3f", rawScore)})")

            // ✅ DOMINANT EMOTION GÜNCELLEME
            // Ekranda görünen duygu için de "boostlanmış" mutluluğu ve redistribution sonrası değerleri kullanalım
            val displayProbs = probs.clone()
            displayProbs[3] = adjHappiness // Mutluluğu güncelledik (index 3 = Happiness)
            displayProbs[4] = pSadness     // Sadness redistribution sonrası (index 4 = Sadness)
            displayProbs[5] = pAnger       // Anger redistribution sonrası (index 5 = Anger)
            displayProbs[6] = pNeutral     // Neutral redistribution sonrası (index 6 = Neutral)
            
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
        // ✅ Video analizi gibi: Padding yok, direkt crop (daha iyi sonuçlar için)
        // Padding eklemek mutluluğu azaltıyor, bu yüzden padding kullanmıyoruz
        val left = box.left.coerceAtLeast(0)
        val top = box.top.coerceAtLeast(0)
        val width = box.width().coerceAtMost(bitmap.width - left)
        val height = box.height().coerceAtMost(bitmap.height - top)
        
        android.util.Log.d("StressAnalyzer", "🔍 Yüz kesimi: box=(${box.left},${box.top},${box.right},${box.bottom}), " +
                "cropped=($left,$top,${left+width},${top+height}), padding=YOK (video analizi gibi)")
        
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

