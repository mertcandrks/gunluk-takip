# Yeni Feature Kontrol Listesi

## 1. Sinir

- Tek bir kullanici amacini ve ona ait ekranlari kapsa.
- Baska feature modulunden sinif import etme.
- Paylasilan model veya UI gercekten ortaksa `:core` icine al.

## 2. Veri

- Domain modelini `:core` icinde tanimla.
- Entity ve DAO'yu `:data` icinde olustur.
- UI'nin kullanacagi repository interface'ini `:data` modulunun public API'si yap.
- Yazma operasyonlarini transaction ve suspend fonksiyonlarla uygula.
- Schema degisirse migration ve schema ciktisi ekle.

## 3. Feature

- `FeatureUiState`: Ekranin tek durum modeli.
- `FeatureViewModel`: Repository Flow'larini StateFlow'a cevirir ve eylemleri yonetir.
- `FeatureRoute`: ViewModel olusturur, state toplar ve sistem etkilerini yonetir.
- `FeatureScreen`: State ve callback alan, veri katmanini bilmeyen Compose UI.

## 4. App Baglantisi

- Repository implementasyonunu `AppContainer` icinde bagla.
- Hedefi `DailyTrackerApp` navigation grafigine ekle.
- Alt menude yalniz ana kullanim alanlarini goster; ikincil ekranlari feature icinde gezindir.

## 5. Dogrulama

- Domain hesaplari icin unit test yaz.
- DAO migration ve transaction davranisini test et.
- Process death, ekran dondurme, bos/yukleniyor/hata durumlarini kontrol et.
- Debug APK'yi emulatorde kurup ana kullanici akislarini calistir.
