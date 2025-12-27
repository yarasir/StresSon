"""
Masasüstü Bilgisayarınızda Model Dönüştürme Scripti
TensorFlow 2.16.1 veya 2.17.0 ile dönüştürür
TensorFlow Lite 2.16.1 ile uyumlu olacak
"""

import tensorflow as tf
import os
import sys

print("=" * 60)
print("TensorFlow Lite Model Dönüştürücü")
print("=" * 60)
print(f"TensorFlow version: {tf.__version__}")

# Model dosyası yolunu kendinize göre ayarlayın
MODEL_PATH = input("\n📁 Model dosyasının tam yolunu girin (.keras veya .h5): ").strip()

# Tırnak işaretlerini kaldır
MODEL_PATH = MODEL_PATH.strip('"').strip("'")

if not os.path.exists(MODEL_PATH):
    print(f"\n❌ Model dosyası bulunamadı: {MODEL_PATH}")
    print("💡 Dosya yolunu kontrol edin")
    sys.exit(1)

print(f"\n✅ Model dosyası bulundu: {MODEL_PATH}")

# Modeli yükle
print("\n📦 Model yükleniyor...")
try:
    model = tf.keras.models.load_model(MODEL_PATH)
    print("✅ Model yüklendi")
except Exception as e:
    print(f"❌ Model yüklenemedi: {e}")
    sys.exit(1)

# Model bilgilerini göster
print(f"\n📊 Model bilgileri:")
print(f"   - Input shape: {model.input_shape}")
print(f"   - Output shape: {model.output_shape}")
print(f"   - Toplam parametre: {model.count_params():,}")

# TFLite'e dönüştür
print("\n🔄 TFLite'e dönüştürülüyor...")
try:
    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    converter.target_spec.supported_ops = [
        tf.lite.OpsSet.TFLITE_BUILTINS,
        tf.lite.OpsSet.SELECT_TF_OPS,
    ]
    converter.optimizations = [tf.lite.Optimize.DEFAULT]
    
    tflite_model = converter.convert()
    print("✅ Dönüştürme başarılı")
except Exception as e:
    print(f"❌ Dönüştürme hatası: {e}")
    sys.exit(1)

# Kaydet
output_dir = os.path.dirname(MODEL_PATH) or "."
output_path = os.path.join(output_dir, "final_stress_model_flex_desktop.tflite")

with open(output_path, "wb") as f:
    f.write(tflite_model)

size_mb = os.path.getsize(output_path) / (1024 * 1024)
print(f"\n✅✅✅ TFLite model oluşturuldu!")
print(f"📦 Dosya: {output_path}")
print(f"📦 Boyut: {size_mb:.2f} MB")

print("\n" + "=" * 60)
print("💡 Sonraki adımlar:")
print("=" * 60)
print(f"1. Bu dosyayı Android Studio'ya kopyalayın:")
print(f"   cp '{output_path}' ~/StressDetectionApp-/app/src/main/assets/final_stress_model_flex.tflite")
print("\n2. Android Studio'da:")
print("   - Build → Clean Project")
print("   - Build → Rebuild Project")
print("   - Uygulamayı çalıştırın")
print("=" * 60)

