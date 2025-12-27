# StressDetectionApp

Android uygulaması ile yüz ifadelerinden stres seviyesi tespiti yapan uygulama.

## Özellikler

- **Canlı Kamera Analizi**: Gerçek zamanlı yüz tespiti ve stres seviyesi analizi
- **Video Analizi**: Kaydedilmiş videolardan stres seviyesi analizi
- **Duygu Tespiti**: 7 farklı duygu sınıfı (Surprise, Fear, Disgust, Happiness, Sadness, Anger, Neutral)
- **Stres Seviyesi**: Düşük, Orta ve Yüksek stres seviyeleri

## Teknik Detaylar

### Anger Boost Özelliği

- **Kamera Analizi**: Anger boost aktif - Neutral'dan Anger'a olasılık transferi yapılır (daha hassas anger tespiti)
- **Video Analizi**: Anger boost devre dışı - Ham model çıktıları kullanılır (daha objektif analiz)

### Model

- TensorFlow Lite modeli kullanılmaktadır
- EfficientNetB0 tabanlı duygu sınıflandırma modeli
- ML Kit Face Detection ile yüz tespiti

## Kurulum

1. Projeyi klonlayın:
```bash
git clone https://github.com/yarasir/StresSon.git
```

2. Android Studio'da açın

3. Gradle sync yapın

4. Uygulamayı çalıştırın

## Kullanım

1. Ana menüden "Kamera" veya "Video Seç" seçeneğini seçin
2. Kamera için izin verin
3. Yüz tespiti otomatik olarak yapılır ve stres seviyesi gösterilir

## Gereksinimler

- Android 7.0 (API 24) veya üzeri
- Kamera izni
- ML Kit Face Detection modülü (otomatik indirilir)
