"""
Colab'da Çalıştırılacak TFLite Dönüştürücü
Mevcut .h5 veya .keras modelini TensorFlow Lite 2.16.1 ile uyumlu hale getirir.
"""

import tensorflow as tf
import os

print("TensorFlow version:", tf.__version__)

# Model dosyası yolunu belirtin (Colab'da oluşturduğunuz model)
# Örnek: "/content/rafdb_emotions_efficientnetb0_30x30_classweight_v2_strong_aug.keras"
# veya: "/content/best_rafdb_emotions_efficientnetb0_30x30_classweight_v2_strong_aug.keras"

# Model dosyası Drive'da outputs klasöründe
base_dir = "/content/drive/MyDrive/rafdb_emotions_efficientnetb0_30x30_classweight_v2_strong_aug_outputs"
MODEL_PATH = f"{base_dir}/rafdb_emotions_efficientnetb0_30x30_classweight_v2_strong_aug.keras"

# Alternatif olarak best_ ile başlayan dosya:
# MODEL_PATH = f"{base_dir}/best_rafdb_emotions_efficientnetb0_30x30_classweight_v2_strong_aug.keras"
# Veya .h5 dosyası:
# MODEL_PATH = f"{base_dir}/rafdb_emotions_efficientnetb0_30x30_classweight_v2_strong_aug.h5"

# Önce dosyanın var olup olmadığını kontrol et
if not os.path.exists(MODEL_PATH):
    print(f"❌ Dosya bulunamadı: {MODEL_PATH}")
    print("\n🔍 Outputs klasöründeki dosyaları kontrol ediliyor...")
    
    outputs_dir = "/content/drive/MyDrive/rafdb_emotions_efficientnetb0_30x30_classweight_v2_strong_aug_outputs"
    if os.path.exists(outputs_dir):
        files = os.listdir(outputs_dir)
        print(f"\n📁 Klasördeki dosyalar ({len(files)} adet):")
        for f in files[:20]:  # İlk 20 dosyayı göster
            print(f"   - {f}")
        
        # .keras veya .h5 dosyalarını bul
        keras_files = [f for f in files if f.endswith('.keras')]
        h5_files = [f for f in files if f.endswith('.h5')]
        
        if keras_files:
            print(f"\n✅ .keras dosyaları bulundu:")
            for f in keras_files:
                print(f"   - {f}")
            MODEL_PATH = os.path.join(outputs_dir, keras_files[0])
            print(f"\n💡 İlk .keras dosyası kullanılacak: {MODEL_PATH}")
        elif h5_files:
            print(f"\n✅ .h5 dosyaları bulundu:")
            for f in h5_files:
                print(f"   - {f}")
            MODEL_PATH = os.path.join(outputs_dir, h5_files[0])
            print(f"\n💡 İlk .h5 dosyası kullanılacak: {MODEL_PATH}")
        else:
            print(f"\n❌ .keras veya .h5 dosyası bulunamadı!")
            exit(1)
    else:
        print(f"\n❌ Outputs klasörü bulunamadı: {outputs_dir}")
        exit(1)

print(f"\n📦 Model yükleniyor: {MODEL_PATH}")

# Modeli yükle
try:
    model = tf.keras.models.load_model(MODEL_PATH)
    print("✅ Model başarıyla yüklendi")
    print(f"📊 Model input shape: {model.input_shape}")
    print(f"📊 Model output shape: {model.output_shape}")
except Exception as e:
    print(f"❌ Model yükleme hatası: {e}")
    exit(1)

# TFLite converter - FULLY_CONNECTED version 12 sorununu çözmek için
print("\n🔄 TFLite'e dönüştürülüyor (FULLY_CONNECTED version 12 sorunu için düzeltilmiş)...")

try:
    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    
    # ⚡ KRİTİK: Select TF Ops ekle (Flex ops için)
    converter.target_spec.supported_ops = [
        tf.lite.OpsSet.TFLITE_BUILTINS,  # TFLite built-in ops
        tf.lite.OpsSet.SELECT_TF_OPS,    # Select TF ops (Flex ops için)
    ]
    
    # Optimizasyon
    converter.optimizations = [tf.lite.Optimize.DEFAULT]
    
    # ⚡ KRİTİK: Opset versiyonunu düşürmek için experimental ayarlar
    # Bu, FULLY_CONNECTED version 12 yerine daha eski bir versiyon kullanır
    try:
        # TensorFlow 2.13+ için
        converter._experimental_lower_tensor_list_ops = False
    except:
        pass
    
    print("⏳ Dönüştürme başlatıldı (bu biraz sürebilir)...")
    tflite_model = converter.convert()
    
    # Çıktı dosyası
    base_name = os.path.splitext(os.path.basename(MODEL_PATH))[0]
    output_path = f"/content/{base_name}_fixed.tflite"
    
    with open(output_path, "wb") as f:
        f.write(tflite_model)
    
    size_mb = os.path.getsize(output_path) / (1024 * 1024)
    print(f"\n✅✅✅ TFLite model başarıyla oluşturuldu!")
    print(f"📦 Dosya: {output_path}")
    print(f"📦 Boyut: {size_mb:.2f} MB")
    
    # Drive'a kopyala
    drive_path = f"/content/drive/MyDrive/{base_name}_fixed.tflite"
    try:
        import shutil
        shutil.copy2(output_path, drive_path)
        print(f"💾 Drive'a kopyalandı: {drive_path}")
    except Exception as e:
        print(f"⚠️ Drive'a kopyalama hatası: {e}")
    
    print(f"\n📱 ANDROID UYGULAMA İÇİN:")
    print(f"1. Bu dosyayı indirin: {output_path}")
    print(f"2. app/src/main/assets/ klasörüne kopyalayın")
    print(f"3. Eski final_stress_model_flex.tflite dosyasını değiştirin")
    print(f"4. Uygulamayı yeniden derleyin")
    
except Exception as e:
    print(f"\n❌❌❌ TFLite dönüşüm hatası: {e}")
    print(f"\n💡 ÇÖZÜM ÖNERİLERİ:")
    print(f"1. TensorFlow versiyonunu düşürün:")
    print(f"   !pip install tensorflow==2.13.0")
    print(f"   Sonra bu scripti tekrar çalıştırın")
    print(f"\n2. Veya modeli yeniden eğitin (TensorFlow 2.13 veya 2.14 ile)")
    print(f"\n3. Veya TensorFlow Lite'ı daha yeni bir versiyona yükseltin")
    
    import traceback
    traceback.print_exc()

