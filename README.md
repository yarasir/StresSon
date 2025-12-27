# StressDetectionApp

Android uygulaması ile yüz ifadelerinden stres seviyesi tespiti yapan uygulama.

## Özellikler

- **Canlı Kamera Analizi**: Gerçek zamanlı yüz tespiti ve stres seviyesi analizi
- **Video Analizi**: Kaydedilmiş videolardan stres seviyesi analizi
- **Duygu Tespiti**: 7 farklı duygu sınıfı (Surprise, Fear, Disgust, Happiness, Sadness, Anger, Neutral)
- **Stres Seviyesi**: Düşük, Orta ve Yüksek stres seviyeleri

## Teknik Detaylar

### Happiness Boost Özelliği

- **Karekök Yöntemi**: Modelin düşük tahmin ettiği mutluluk değerlerini artırır
  - Örnek: %4 mutluluk → %20'ye yükselir
  - Örnek: %1 mutluluk → %10'a yükselir
- **Stres Formülü**: Boost edilmiş happiness, stres formülünde 0.6 katsayısı ile kullanılır
- **Her İki Modda Aktif**: Hem kamera hem video analizinde çalışır

### Anger Boost Özelliği

- **Neutral Redistribution**: Model bazen Anger'ı Neutral olarak sınıflandırır, bu sorunu çözmek için:
  - Anger > %5 ve Neutral > %8 ise → Neutral'ın %45'i Anger'a transfer edilir
  - **Her İki Modda Aktif**: Hem kamera hem video analizinde çalışır

- **Akıllı Kontroller**:
  - **Happiness Kontrolü**: Happiness > %10 ise anger boost devre dışı (gülerken anger yükselmesin)
  - **Yüksek Anger Kontrolü**: Anger > %25 ise boost yapılmaz (zaten yeterince yüksek)

### Stres Seviyesi Eşikleri

- **HIGH Stres**: Skor > 0.43
- **MEDIUM Stres**: Skor 0.17 - 0.43 arası
- **LOW Stres**: Skor < 0.17

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
