"""
Colab'da TensorFlow 2.18.0 ile Model Dönüştürme
TensorFlow Lite 2.18.1 ile uyumlu olacak
"""

print("""
# ============================================================
# COLAB'DA BU KODU ÇALIŞTIRIN
# ============================================================

# TensorFlow 2.18.0'ı yükle (TensorFlow Lite 2.18.1 ile uyumlu)
!pip install tensorflow==2.18.0

# Runtime'ı yeniden başlatın: Runtime → Restart runtime

# ============================================================
# RUNTIME YENİDEN BAŞLATTIKTAN SONRA BU KODU ÇALIŞTIRIN
# ============================================================
import tensorflow as tf
import os

print("TensorFlow version:", tf.__version__)
# 2.18.0 olmalı ✅

# Model dosyası
base_dir = "/content/drive/MyDrive/rafdb_emotions_efficientnetb0_30x30_classweight_v2_strong_aug_outputs"
MODEL_PATH = f"{base_dir}/rafdb_emotions_efficientnetb0_30x30_classweight_v2_strong_aug.keras"

# Modeli yükle
model = tf.keras.models.load_model(MODEL_PATH)
print("✅ Model yüklendi")

# TFLite'e dönüştür
print("\\n🔄 TFLite'e dönüştürülüyor (TensorFlow 2.18.0 ile)...")
converter = tf.lite.TFLiteConverter.from_keras_model(model)
converter.target_spec.supported_ops = [
    tf.lite.OpsSet.TFLITE_BUILTINS,
    tf.lite.OpsSet.SELECT_TF_OPS,
]
converter.optimizations = [tf.lite.Optimize.DEFAULT]

tflite_model = converter.convert()

# Kaydet
output_path = "/content/final_stress_model_flex_tf218.tflite"
with open(output_path, "wb") as f:
    f.write(tflite_model)

size_mb = os.path.getsize(output_path) / (1024 * 1024)
print(f"\\n✅✅✅ TFLite model oluşturuldu!")
print(f"📦 Dosya: {output_path}")
print(f"📦 Boyut: {size_mb:.2f} MB")

# Drive'a kopyala
drive_path = os.path.join(base_dir, "final_stress_model_flex_tf218.tflite")
import shutil
shutil.copy2(output_path, drive_path)
print(f"💾 Drive'a kopyalandı: {drive_path}")
""")

