#!/bin/bash

# ============================================================
# Colab'da TensorFlow 2.14.0 ile Model Dönüştürme Scripti
# FULLY_CONNECTED version 12 sorununu çözer
# ============================================================

# 1. Miniconda indir (Küçük Python Yöneticisi)
wget https://repo.anaconda.com/miniconda/Miniconda3-latest-Linux-x86_64.sh -O miniconda.sh
bash miniconda.sh -b -p /content/miniconda

# 2. Python 3.10 ortamı oluştur
/content/miniconda/bin/conda create -n old_tf_env python=3.10 -y

# 3. TensorFlow 2.14.0 yükle (Android uyumlu sürüm)
source /content/miniconda/bin/activate old_tf_env
pip install tensorflow==2.14.0

# 4. Dönüştürme işlemini yapacak Python kodunu yaz
cat <<'EOF' > convert_script.py
import tensorflow as tf
import os

# Model dosyası yolu (Google Drive'dan)
base_dir = "/content/drive/MyDrive/rafdb_emotions_efficientnetb0_30x30_classweight_v2_strong_aug_outputs"
MODEL_PATH = f"{base_dir}/rafdb_emotions_efficientnetb0_30x30_classweight_v2_strong_aug.keras"

# Eğer .keras dosyası yoksa .h5'ı dene
if not os.path.exists(MODEL_PATH):
    MODEL_PATH = f"{base_dir}/rafdb_emotions_efficientnetb0_30x30_classweight_v2_strong_aug.h5"

try:
    # Model dosyasını kontrol et
    if not os.path.exists(MODEL_PATH):
        print(f"❌ Model dosyası bulunamadı: {MODEL_PATH}")
        print("💡 Dosya yolunu kontrol edin")
        exit(1)
    
    print(f"✅ Model dosyası bulundu: {MODEL_PATH}")
    
    # Eski model dosyasını yükle
    print(f"⏳ Model yükleniyor (TF version: {tf.__version__})...")
    model = tf.keras.models.load_model(MODEL_PATH)
    print("✅ Model yüklendi")
    
    # Dönüştürücü ayarları
    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    
    # SADECE ESKİ OPERATÖRLERİ KULLAN (Version 12 hatasını çözen kısım)
    # SELECT_TF_OPS kullanmıyoruz, sadece TFLITE_BUILTINS
    converter.target_spec.supported_ops = [tf.lite.OpsSet.TFLITE_BUILTINS]
    converter._experimental_lower_tensor_list_ops = False
    
    # Dönüştür
    print("🔄 TFLite'e dönüştürülüyor...")
    tflite_model = converter.convert()
    
    # Kaydet
    output_path = "/content/final_stress_model_flex_tf214.tflite"
    with open(output_path, 'wb') as f:
        f.write(tflite_model)
    
    size_mb = os.path.getsize(output_path) / (1024 * 1024)
    print(f"✅✅✅ BAŞARILI! '{output_path}' oluşturuldu.")
    print(f"📦 Dosya boyutu: {size_mb:.2f} MB")
    
    # Drive'a kopyala
    drive_path = os.path.join(base_dir, "final_stress_model_flex_tf214.tflite")
    import shutil
    shutil.copy2(output_path, drive_path)
    print(f"💾 Drive'a kopyalandı: {drive_path}")
    
except Exception as e:
    print(f"❌ HATA: {e}")
    import traceback
    traceback.print_exc()
EOF

# 5. Oluşturduğumuz kodu eski TensorFlow ile çalıştır
python convert_script.py

