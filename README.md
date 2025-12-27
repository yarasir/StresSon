# 🧠 Stres Tespiti Uygulaması (Stress Detection App)

<div align="center">

![Android](https://img.shields.io/badge/Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/kotlin-%237F52FF.svg?style=for-the-badge&logo=kotlin&logoColor=white)
![TensorFlow](https://img.shields.io/badge/TensorFlow-FF6F00?style=for-the-badge&logo=TensorFlow&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Compose-4285F4?style=for-the-badge&logo=jetpack-compose&logoColor=white)

**Yüz ifadelerinden stres seviyesini tespit eden Android uygulaması**

[Özellikler](#-özellikler) • [Kurulum](#-kurulum) • [Mimari](#-mimari) • [Algoritma](#-stres-ölçüm-algoritması) • [Kullanım](#-kullanım)

</div>

---

## 📋 İçindekiler

- [Genel Bakış](#-genel-bakış)
- [Özellikler](#-özellikler)
- [Teknolojiler](#-teknolojiler)
- [Kurulum](#-kurulum)
- [Proje Yapısı](#-proje-yapısı)
- [Mimari](#-mimari)
- [Stres Ölçüm Algoritması](#-stres-ölçüm-algoritması)
- [Class Detayları](#-class-detayları)
- [Kullanım](#-kullanım)
- [Model Bilgileri](#-model-bilgileri)
- [Geliştirme Notları](#-geliştirme-notları)

---

## 🎯 Genel Bakış

Bu uygulama, **yüz ifadelerini analiz ederek stres seviyesini tespit eden** bir Android uygulamasıdır. Uygulama, **TensorFlow Lite** ile eğitilmiş bir **EfficientNetB0** modeli kullanarak 7 farklı duyguyu (Surprise, Fear, Disgust, Happiness, Sadness, Anger, Neutral) tespit eder ve bu duyguları analiz ederek stres seviyesini hesaplar.

### Temel Özellikler
- 📷 **Canlı kamera analizi**: Gerçek zamanlı yüz tespiti ve stres ölçümü
- 🎬 **Video analizi**: Kaydedilmiş videolardan stres analizi
- 👥 **Çoklu yüz tespiti**: Aynı anda birden fazla yüzü tespit edebilir
- 📊 **Detaylı duygu analizi**: 7 farklı duygu için olasılık skorları
- 🎨 **Modern UI**: Jetpack Compose ile geliştirilmiş kullanıcı arayüzü

---

## ✨ Özellikler

### 🔴 Ana Özellikler
- ✅ **Gerçek zamanlı stres tespiti** - Canlı kamera feed'inden anlık analiz
- ✅ **Video analizi** - Kaydedilmiş videolardan stres seviyesi tespiti
- ✅ **Çoklu yüz desteği** - Aynı anda birden fazla yüzü işleyebilir
- ✅ **Temporal smoothing** - Frame'ler arası tutarlılık için yumuşatma
- ✅ **Duygu redistribüsyonu** - Model çıktılarını optimize eden algoritma
- ✅ **Happiness boost** - Küçük mutluluk değerlerini artırma
- ✅ **Koordinat dönüşümü** - Video ve kamera için doğru yüz konumlandırma

### 🎨 Kullanıcı Arayüzü
- Modern Material Design 3
- Renk kodlu stres göstergeleri (Yeşil: Düşük, Sarı: Orta, Kırmızı: Yüksek)
- Gerçek zamanlı yüz çerçeveleri
- Duygu ve stres bilgisi gösterimi

---

## 🛠 Teknolojiler

### Core Technologies
- **Kotlin** - Programlama dili
- **Jetpack Compose** - Modern UI framework
- **Android SDK 26+** - Minimum Android 8.0 (Oreo)

### Machine Learning
- **TensorFlow Lite 2.16.1** - Model inference engine
- **TensorFlow Lite Flex Ops** - Select TF Ops desteği
- **EfficientNetB0** - Eğitilmiş duygu tanıma modeli
- **ML Kit Face Detection** - Yüz tespiti için Google ML Kit

### Camera & Media
- **CameraX 1.3.1** - Modern kamera API'si
- **MediaMetadataRetriever** - Video frame extraction

### Architecture
- **Clean Architecture** - Organize edilmiş kod yapısı
- **MVVM Pattern** - Model-View-ViewModel yaklaşımı
- **Single Responsibility** - Her class tek bir sorumluluğa sahip

---

## 📦 Kurulum

### Gereksinimler
- Android Studio Hedgehog (2023.1.1) veya üzeri
- Android SDK 26+
- Gradle 8.3+
- Kotlin 1.9.22+

### Adımlar

1. **Repository'yi klonlayın:**
```bash
git clone https://github.com/yarasir/StresSon.git
cd StresSon
```

2. **Android Studio'da açın:**
   - File → Open → Proje klasörünü seçin
   - Gradle sync'in tamamlanmasını bekleyin

3. **Model dosyasını kontrol edin:**
   - `app/src/main/assets/model.tflite` dosyasının mevcut olduğundan emin olun

4. **Uygulamayı çalıştırın:**
   - Bir Android cihaz veya emülatör bağlayın
   - Run butonuna tıklayın veya `Shift+F10`

### Emülatör Ayarları
Eğer emülatör kullanıyorsanız, kamera için:
1. AVD Manager → Emülatörünüzü seçin → Edit
2. Show Advanced Settings → Camera
3. Front/Back Camera için 'Webcam0' seçin
4. Emülatörü yeniden başlatın

---

## 📁 Proje Yapısı

```
app/src/main/java/com/example/stressdetection/
├── MainActivity.kt                    # Ana Activity (Navigation)
├── model/
│   └── StressLevel.kt                  # Enum ve Data Classes
├── analyzer/
│   └── StressImageAnalyzer.kt         # Stres analiz motoru
├── utils/
│   └── ImageProxyExtensions.kt        # Extension functions
└── ui/
    ├── screens/
    │   ├── MainMenuScreen.kt           # Ana menü ekranı
    │   ├── StressDetectionScreen.kt    # Kamera analiz ekranı
    │   └── VideoAnalysisScreen.kt     # Video analiz ekranı
    └── components/
        └── StressLevelIndicator.kt     # UI component'leri
```

---

## 🏗 Mimari

### Genel Mimari

```
┌─────────────────────────────────────────┐
│         MainActivity                    │
│  (Navigation & Lifecycle Management)    │
└──────────────┬──────────────────────────┘
               │
       ┌───────┴────────┐
       │                 │
┌──────▼──────┐  ┌──────▼──────────┐
│ UI Screens  │  │  Analyzer       │
│             │  │                 │
│ - MainMenu  │  │ - Face Detection│
│ - Camera    │  │ - Inference     │
│ - Video     │  │ - Stress Calc   │
└─────────────┘  └─────────────────┘
                       │
              ┌────────┴────────┐
              │                 │
      ┌───────▼──────┐  ┌───────▼──────┐
      │ ML Kit       │  │ TensorFlow    │
      │ Face Detect  │  │ Lite Model    │
      └──────────────┘  └───────────────┘
```

### Veri Akışı

1. **Kamera/Video** → Frame yakalama
2. **ML Kit** → Yüz tespiti
3. **Yüz Crop** → Yüz bölgesini kesme
4. **TensorFlow Lite** → Duygu analizi (7 sınıf)
5. **Post-processing** → Smoothing, redistribution, boost
6. **Stres Hesaplama** → Formül ile stres skoru
7. **UI Güncelleme** → Sonuçları gösterme

---

## 🧮 Stres Ölçüm Algoritması

### 1. Model Çıktısı (7 Duygu)

Model, her frame için 7 duygu için olasılık değerleri döndürür:

| Index | Duygu | Açıklama |
|-------|-------|----------|
| 0 | Surprise | Şaşkınlık |
| 1 | Fear | Korku |
| 2 | Disgust | İğrenme |
| 3 | Happiness | Mutluluk |
| 4 | Sadness | Üzüntü |
| 5 | Anger | Öfke |
| 6 | Neutral | Nötr |

### 2. Preprocessing

#### Temporal Smoothing (Frame Yumuşatma)
Frame'ler arası tutarlılık için **Exponential Moving Average (EMA)** kullanılır:

```kotlin
smoothedProbs[i] = α * currentProbs[i] + (1-α) * previousProbs[i]
```

- **α (smoothingAlpha)**: 0.65
- **Anlamı**: Yeni frame %65, eski frame %35 ağırlığında

#### Neutral Redistribution
Model bazen Anger'ı Neutral olarak sınıflandırır. Bu sorunu çözmek için:

**Koşul**: `Anger > 5%` VE `Neutral > 8%`

**İşlem**: Neutral'ın %45'i Anger'a transfer edilir

```kotlin
if (pAnger > 0.05f && pNeutral > 0.08f) {
    val transfer = pNeutral * 0.45f
    pAnger += transfer
    pNeutral -= transfer
}
```

#### Happiness Boost
Model küçük mutluluk değerlerini düşük tahmin eder. **Karekök yöntemi** ile artırılır:

```kotlin
adjHappiness = √(pHappiness)
```

**Örnekler**:
- %4 mutluluk → %20'ye yükselir
- %1 mutluluk → %10'a yükselir
- %25 mutluluk → %50'ye yükselir

### 3. Stres Skoru Hesaplama

#### Negatif Yük (Stres Artırıcı Duygular)

```kotlin
negativeLoad = (Fear × 2.0) + 
               (Anger × 1.5) + 
               (Disgust × 0.8) + 
               (Sadness × 0.3) + 
               (Surprise × 0.2)
```

**Katsayıların Mantığı**:
- **Fear (2.0)**: En yüksek katsayı - korku stresi en çok artırır
- **Anger (1.5)**: Yüksek katsayı - öfke stres göstergesidir
- **Disgust (0.8)**: Orta katsayı
- **Sadness (0.3)**: Düşük katsayı - patlama yapmasın diye
- **Surprise (0.2)**: En düşük katsayı

#### Pozitif Yük (Stres Azaltıcı Duygular)

```kotlin
positiveLoad = (adjHappiness × 0.5) + (Neutral × 0.15)
```

**Katsayıların Mantığı**:
- **Happiness (0.5)**: Boost edilmiş mutluluk, katsayısı düşük (suni artırıldığı için)
- **Neutral (0.15)**: Çok düşük katsayı (maskelenmeyi önlemek için)

#### Net Skor

```kotlin
rawScore = negativeLoad - positiveLoad
// 0.0 ile 1.0 arasında sınırlandırılır
```

### 4. Stres Seviyesi Belirleme

```kotlin
when {
    rawScore > 0.38f -> StressLevel.HIGH    // Yüksek Stres
    rawScore > 0.15f -> StressLevel.MEDIUM  // Orta Stres
    else             -> StressLevel.LOW     // Düşük Stres
}
```

**Eşik Değerleri**:
- **HIGH**: > 0.38 (Yüksek stres)
- **MEDIUM**: 0.15 - 0.38 (Orta stres)
- **LOW**: < 0.15 (Düşük stres)

---

## 📚 Class Detayları

### 1. `MainActivity`

**Konum**: `MainActivity.kt`

**Sorumluluk**: 
- Uygulama lifecycle yönetimi
- Screen navigation
- Permission yönetimi
- Video picker launcher

**Özellikler**:
- 3 farklı ekran arasında geçiş (MainMenu, Camera, Video)
- Kamera izni yönetimi
- Video seçme işlevi

### 2. `StressImageAnalyzer`

**Konum**: `analyzer/StressImageAnalyzer.kt`

**Sorumluluk**: 
- TensorFlow Lite model yükleme
- Yüz tespiti koordinasyonu
- Duygu analizi inference
- Stres skoru hesaplama

**Önemli Metodlar**:

#### `init`
- Model dosyasını assets'ten yükler
- FlexDelegate ekler (Select TF Ops için)
- Interpreter oluşturur
- Input buffer hazırlar

#### `analyze(imageProxy: ImageProxy)`
- ImageProxy'yi Bitmap'e çevirir
- Rotation düzeltmesi yapar
- ML Kit ile yüz tespiti yapar
- Her yüz için inference çalıştırır
- Sonuçları callback'e gönderir

#### `runInference(bitmap: Bitmap): Pair<StressLevel, String>`
- Bitmap'i 224x224'e scale eder
- Pixel değerlerini 0-255 aralığında hazırlar (normalize ETMEZ!)
- TensorFlow Lite ile inference yapar
- Softmax uygular (gerekirse)
- Temporal smoothing yapar
- Neutral redistribution yapar
- Happiness boost yapar
- Stres skorunu hesaplar
- Dominant emotion'ı bulur

**Önemli Değişkenler**:
- `MODEL_FILE_NAME`: "model.tflite"
- `inputImageSize`: 224 (EfficientNetB0 input boyutu)
- `smoothingAlpha`: 0.65 (EMA katsayısı)
- `smoothedProbs`: Frame'ler arası yumuşatılmış olasılıklar

### 3. `MainMenuScreen`

**Konum**: `ui/screens/MainMenuScreen.kt`

**Sorumluluk**: 
- Ana menü ekranı
- Video seçme butonu
- Kamera açma butonu

**UI Özellikleri**:
- Koyu tema (0xFF1E1E1E)
- 2 ana buton (Video, Kamera)
- Material Design 3

### 4. `StressDetectionScreen`

**Konum**: `ui/screens/StressDetectionScreen.kt`

**Sorumluluk**: 
- Kamera izni yönetimi
- CameraPreview composable'ını barındırır
- Stres seviyesi göstergesi

**Özellikler**:
- İzin durumunu periyodik kontrol eder (500ms)
- İzin verilince otomatik kamera açar
- Geri butonu

### 5. `CameraPreview`

**Konum**: `ui/screens/StressDetectionScreen.kt` (içinde)

**Sorumluluk**: 
- CameraX ile kamera başlatma
- Preview gösterimi
- Yüz çerçeveleri çizme
- Duygu bilgisi gösterme

**Özellikler**:
- Ön kamera öncelikli (fallback: arka kamera)
- Tüm yüzleri tespit eder ve gösterir
- Koordinat dönüşümü (ImageProxy → Canvas)
- Gerçek zamanlı analiz (150ms throttle)

### 6. `VideoAnalysisScreen`

**Konum**: `ui/screens/VideoAnalysisScreen.kt`

**Sorumluluk**: 
- Video oynatma
- Frame extraction (MediaMetadataRetriever)
- Video'dan yüz tespiti
- Koordinat dönüşümü (Bitmap → Canvas)

**Özellikler**:
- 200ms delay ile frame analizi
- İlk frame hemen analiz edilir
- En büyük yüze odaklanır (alan bazlı)
- CENTER_CROP mantığı ile koordinat dönüşümü
- Video loop desteği

**Koordinat Dönüşümü**:
```kotlin
// Aspect ratio korunarak scale
if (bitmapAspect > canvasAspect) {
    // Bitmap daha geniş - yüksekliğe göre scale
    scaleY = size.height / videoHeight
    scaleX = scaleY
    offsetX = (size.width - videoWidth * scaleX) / 2f
} else {
    // Bitmap daha yüksek - genişliğe göre scale
    scaleX = size.width / videoWidth
    scaleY = scaleX
    offsetY = (size.height - videoHeight * scaleY) / 2f
}
```

### 7. `StressLevelIndicatorSimple`

**Konum**: `ui/components/StressLevelIndicator.kt`

**Sorumluluk**: 
- Stres seviyesini görsel olarak gösterme
- Renk kodlu kart gösterimi

**Renkler**:
- 🟢 **Yeşil**: Düşük Stres
- 🟡 **Sarı**: Orta Stres
- 🔴 **Kırmızı**: Yüksek Stres
- ⚪ **Gri**: Analiz Ediliyor

### 8. Model Classes

**Konum**: `model/StressLevel.kt`

#### `StressLevel` (Enum)
```kotlin
enum class StressLevel(val value: Int) {
    LOW(0),      // Düşük stres
    MEDIUM(1),   // Orta stres
    HIGH(2)      // Yüksek stres
}
```

#### `FaceDetectionResult`
```kotlin
data class FaceDetectionResult(
    val boundingBox: Rect?,           // Yüz çerçevesi
    val stressLevel: StressLevel,     // Stres seviyesi
    val dominantEmotion: String,     // "Happiness 45%" gibi
    val imageWidth: Int,              // Görüntü genişliği
    val imageHeight: Int             // Görüntü yüksekliği
)
```

#### `VideoFaceResult`
```kotlin
data class VideoFaceResult(
    val boundingBox: Rect,           // Yüz çerçevesi
    val stressLevel: StressLevel,     // Stres seviyesi
    val dominantEmotion: String      // Dominant duygu
)
```

### 9. `ImageProxyExtensions`

**Konum**: `utils/ImageProxyExtensions.kt`

**Sorumluluk**: 
- ImageProxy'yi Bitmap'e çevirme
- YUV420_888 formatını NV21'ye çevirme

**Kullanım**:
```kotlin
val bitmap = imageProxy.toBitmap()
```

---

## 🎬 Kullanım

### Canlı Kamera Analizi

1. Uygulamayı açın
2. "📷 Kamerayla Ölçün" butonuna tıklayın
3. Kamera izni verin (ilk kullanımda)
4. Yüzünüzü kameraya gösterin
5. Gerçek zamanlı stres seviyesi görüntülenir

**Özellikler**:
- Tüm yüzler tespit edilir
- Her yüz için ayrı analiz yapılır
- Frame'ler arası yumuşatma ile stabil sonuçlar
- 150ms throttle ile performans optimizasyonu

### Video Analizi

1. Uygulamayı açın
2. "Video Seçin" butonuna tıklayın
3. Bir video dosyası seçin
4. Video otomatik oynatılır ve analiz edilir
5. Yüz çerçeveleri ve stres seviyeleri gösterilir

**Özellikler**:
- 200ms delay ile frame analizi
- En büyük yüze odaklanır
- Video loop desteği
- Doğru koordinat dönüşümü

---

## 🤖 Model Bilgileri

### Model Özellikleri

- **Model Tipi**: EfficientNetB0
- **Input Boyutu**: 224x224x3 (RGB)
- **Output**: 7 sınıf (duygu olasılıkları)
- **Format**: TensorFlow Lite (.tflite)
- **Ops**: Select TF Ops (Flex Ops) gerekli
- **Boyut**: ~15-20 MB (tahmini)

### Model Eğitimi

Model, **RAF-DB (Real-world Affective Faces Database)** veri seti üzerinde eğitilmiştir:
- 7 duygu sınıfı
- Class weight ile dengelenmiş
- Strong augmentation uygulanmış
- EfficientNetB0 backbone

### Model Dosyası

**Konum**: `app/src/main/assets/model.tflite`

**Not**: Model dosyası repository'de bulunmalıdır. Eğer yoksa, TensorFlow 2.16.1 ile dönüştürülmüş model dosyasını assets klasörüne ekleyin.

### Model Dönüştürme Scriptleri

Proje kök dizininde model dönüştürme scriptleri bulunur:
- `convert_model_tf216.py` - TensorFlow 2.16 ile dönüştürme
- `convert_model_tf217.py` - TensorFlow 2.17 ile dönüştürme
- `convert_model_tf218_final.py` - TensorFlow 2.18 ile dönüştürme
- `convert_model_final_solution.py` - Final çözüm
- `fix_tensorflow_colab.py` - Colab için TensorFlow kurulumu

---

## 🔬 Teknik Detaylar

### Preprocessing

#### Pixel Normalizasyonu
**ÖNEMLİ**: Model **0-255 aralığında RAW PIXEL** değerleri bekler!

```kotlin
// ❌ YANLIŞ (normalize edilmiş)
val normalizedR = (r / 127.5f) - 1f  // [-1, 1]

// ✅ DOĞRU (raw pixel)
inputBuffer.putFloat(r)  // [0, 255] aralığında
```

**Neden?**: Model kendi içinde normalize ediyor. Biz normalize edersek, model bunu "simsiyah görüntü" olarak algılar ve en baskın sınıf olan Sadness'ı basar.

#### Softmax Uygulaması
Model çıktısı logits mi yoksa probabilities mi kontrol edilir:

```kotlin
val isLogits = rawOutput.sum() < 0.9f || 
               rawOutput.sum() > 1.1f || 
               rawOutput.any { it < -10f || it > 10f }

val probs = if (isLogits) softmax(rawOutput) else rawOutput
```

### Performance Optimizasyonları

1. **Frame Throttling**: 150ms minimum delay (kamera)
2. **Backpressure Strategy**: KEEP_ONLY_LATEST
3. **Single Thread Executor**: Analyzer için ayrı thread
4. **Bitmap Reuse**: Her frame için yeni Bitmap oluşturulmaz
5. **Temporal Smoothing**: Frame'ler arası tutarlılık

### Memory Management

- **MappedByteBuffer**: Model dosyası memory-mapped olarak yüklenir
- **ByteBuffer**: Input/Output için direct buffer kullanılır
- **ImageProxy.close()**: Mutlaka kapatılır (memory leak önleme)
- **MediaMetadataRetriever.release()**: Video analizinde düzgün kapatılır

---

## 📊 Algoritma Detayları

### Stres Skoru Hesaplama Örneği

**Varsayalım ki model şu olasılıkları döndürdü:**
- Surprise: 5%
- Fear: 8%
- Disgust: 2%
- Happiness: 3%
- Sadness: 15%
- Anger: 12%
- Neutral: 55%

**1. Temporal Smoothing (EMA)**
```
smoothed = 0.65 * current + 0.35 * previous
```

**2. Neutral Redistribution**
```
Anger (12%) > 5% ✅
Neutral (55%) > 8% ✅
Transfer = 55% * 45% = 24.75%
Anger: 12% + 24.75% = 36.75%
Neutral: 55% - 24.75% = 30.25%
```

**3. Happiness Boost**
```
adjHappiness = √(3%) = √0.03 = 0.173 = 17.3%
```

**4. Stres Skoru Hesaplama**
```
negativeLoad = (8% × 2.0) + (36.75% × 1.5) + (2% × 0.8) + (15% × 0.3) + (5% × 0.2)
             = 0.16 + 0.551 + 0.016 + 0.045 + 0.01
             = 0.792

positiveLoad = (17.3% × 0.5) + (30.25% × 0.15)
             = 0.0865 + 0.0454
             = 0.1319

rawScore = 0.792 - 0.1319 = 0.6601
```

**5. Stres Seviyesi**
```
0.6601 > 0.38 → StressLevel.HIGH 🔴
```

---

## 🐛 Bilinen Sorunlar ve Çözümler

### 1. Emülatörde Kamera Çalışmıyor

**Sorun**: Emülatörde kamera açılmıyor

**Çözüm**:
1. AVD Manager → Emülatörünüzü seçin → Edit
2. Show Advanced Settings → Camera
3. Front/Back Camera için 'Webcam0' seçin
4. Emülatörü yeniden başlatın

### 2. Model Yüklenemiyor

**Sorun**: "Model yüklenemedi" hatası

**Çözüm**:
- `app/src/main/assets/model.tflite` dosyasının mevcut olduğundan emin olun
- Dosya boyutunu kontrol edin (boş olmamalı)
- Flex Ops dependency'sinin yüklü olduğundan emin olun

### 3. Yüz Tespit Edilmiyor

**Sorun**: Kamera açılıyor ama yüz tespit edilmiyor

**Çözüm**:
- İyi aydınlatma kullanın
- Yüzün tamamen görünür olduğundan emin olun
- ML Kit Face Detection modülünün indirildiğinden emin olun (ilk kullanımda otomatik)

### 4. Video Analizinde Koordinatlar Yanlış

**Sorun**: Video'da yüz çerçeveleri yanlış konumda

**Çözüm**: 
- Video aspect ratio'su ile view aspect ratio'su farklı olabilir
- CENTER_CROP mantığı ile dönüşüm yapılıyor, bu normal
- Logcat'te koordinat log'larını kontrol edin

---

## 🔧 Geliştirme Notları

### Kod Organizasyonu

Proje **Clean Architecture** prensiplerine göre organize edilmiştir:

- **Model**: Data classes ve enum'lar
- **Analyzer**: Business logic (stres hesaplama)
- **UI/Screens**: Presentation layer
- **Utils**: Helper functions

### Best Practices

1. **Single Responsibility**: Her class tek bir sorumluluğa sahip
2. **Separation of Concerns**: UI, business logic ve data ayrı
3. **DRY Principle**: Tekrarlayan kod yok
4. **Error Handling**: Try-catch blokları ile hata yönetimi
5. **Logging**: Detaylı log'lar ile debug kolaylığı

### Performance İpuçları

- Frame throttling ile CPU kullanımını azaltma
- Bitmap reuse ile memory kullanımını optimize etme
- Single thread executor ile thread yönetimi
- Temporal smoothing ile gereksiz hesaplamaları azaltma

---

## 📝 Versiyon Geçmişi

### v10.0 (Mevcut)
- ✅ Kodları class'lara ayırma (refactoring)
- ✅ Çoklu yüz desteği
- ✅ Video analizi koordinat dönüşümü düzeltmesi
- ✅ Temporal smoothing iyileştirmesi
- ✅ Happiness boost algoritması
- ✅ Neutral redistribution

### Önceki Versiyonlar
- v9.x: İlk çalışan versiyon
- v8.x: Model optimizasyonları
- v7.x: Stres formülü iyileştirmeleri

---

## 🤝 Katkıda Bulunma

1. Fork edin
2. Feature branch oluşturun (`git checkout -b feature/amazing-feature`)
3. Commit edin (`git commit -m 'Add amazing feature'`)
4. Push edin (`git push origin feature/amazing-feature`)
5. Pull Request açın

---

## 📄 Lisans

Bu proje açık kaynaklıdır. Kendi sorumluluğunuzda kullanabilirsiniz.

---

## 👤 Yazar

**yarasir**
- GitHub: [@yarasir](https://github.com/yarasir)
- Repository: [StresSon](https://github.com/yarasir/StresSon)

---

## 🙏 Teşekkürler

- **TensorFlow** - Machine learning framework
- **Google ML Kit** - Face detection
- **Jetpack Compose** - Modern UI framework
- **CameraX** - Kamera API'si

---

## 📞 İletişim

Sorularınız için GitHub Issues kullanabilirsiniz.

---

<div align="center">

**⭐ Bu projeyi beğendiyseniz yıldız vermeyi unutmayın! ⭐**

Made with ❤️ using Kotlin & TensorFlow Lite

</div>
