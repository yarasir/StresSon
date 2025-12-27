"""
TensorFlow Lite Dönüştürücü - FULLY_CONNECTED version 12 sorununu çözmek için
Bu script .h5 veya .keras modelini TensorFlow Lite 2.16.1 ile uyumlu hale getirir.
"""

import tensorflow as tf
import os

print("TensorFlow version:", tf.__version__)

# Model dosyası yolunu belirtin
MODEL_PATH = input("Model dosyası yolunu girin (.h5 veya .keras): ").strip()

if not os.path.exists(MODEL_PATH):
    print(f"❌ Dosya bulunamadı: {MODEL_PATH}")
    exit(1)

print(f"\n📦 Model yükleniyor: {MODEL_PATH}")

# Modeli yükle
try:
    model = tf.keras.models.load_model(MODEL_PATH)
    print("✅ Model başarıyla yüklendi")
except Exception as e:
    print(f"❌ Model yükleme hatası: {e}")
    exit(1)

# TFLite converter
print("\n🔄 TFLite'e dönüştürülüyor...")

try:
    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    
    # ⚡ KRİTİK: Target opset versiyonunu belirt (daha eski versiyon kullan)
    # Bu, FULLY_CONNECTED version 12 yerine daha eski bir versiyon kullanır
    converter.target_spec.supported_ops = [
        tf.lite.OpsSet.TFLITE_BUILTINS,  # TFLite built-in ops
        tf.lite.OpsSet.SELECT_TF_OPS,    # Select TF ops (Flex ops için)
    ]
    
    # ⚡ Opset versiyonunu düşür (version 12 yerine daha eski)
    # Bu, TensorFlow Lite 2.16.1 ile uyumlu olacak
    converter._experimental_lower_tensor_list_ops = False
    
    # Optimizasyon
    converter.optimizations = [tf.lite.Optimize.DEFAULT]
    
    # Dönüştür
    tflite_model = converter.convert()
    
    # Çıktı dosyası
    output_path = MODEL_PATH.replace(".h5", "_fixed.tflite").replace(".keras", "_fixed.tflite")
    
    with open(output_path, "wb") as f:
        f.write(tflite_model)
    
    size_mb = os.path.getsize(output_path) / (1024 * 1024)
    print(f"✅ TFLite model kaydedildi: {output_path}")
    print(f"📦 Dosya boyutu: {size_mb:.2f} MB")
    print(f"\n💡 Bu dosyayı app/src/main/assets/ klasörüne kopyalayın")
    print(f"💡 Eski final_stress_model_flex.tflite dosyasını değiştirin")
    
except Exception as e:
    print(f"❌ TFLite dönüşüm hatası: {e}")
    print(f"\n💡 Alternatif: TensorFlow versiyonunu düşürmeyi deneyin")
    print(f"💡 Veya modeli yeniden eğitin (TensorFlow 2.13 veya 2.14 ile)")

