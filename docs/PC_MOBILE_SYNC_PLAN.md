# PC ve Android Ozellik Esitligi ve Iki Yonlu Senkron

## Bulgular

- desktop-wails/app.go yalnizca durum okuma ve telefondan snapshot alma sunuyor.
- desktop-wails/frontend/src/main.js ekranlari salt okunur; uyku dugmeleri disabled.
- PcSyncClient.kt snapshot gonderip ACK okuyor. Telefondaki veritabanina PC degisikliklerini uygulayan akis yok.
- SyncSnapshotRepository.kt yalnizca snapshot uretiyor. Mevcut protokol cihazlar arasi sabit kimlik, silme kaydi ve cakisma gecmisi tasimiyor.
- Snapshot; yiyecek kataloglari, hazir menuler, gorev sablonlari ve tum hedef/ayarlar icin yeterli degil.

## Onay Gerektiren Kapsam

Bu degisiklik Android veritabanini, aktarim protokolunu ve PC uygulamasinin veri katmanini birlikte etkiliyor. Eski veriler korunacak; eski istemcilerin yeni veriyi ezmesine izin verilmeyecek. Go/Wails ve Kotlin korunacak.

## Onerilen Degisiklikler

### Veri ve Senkron Katmani

- Her senkronize kayda cihazlardan bagimsiz UUID ve revizyon ekle. Yerel sayisal kimlikleri cihazlar arasi kimlik olarak kullanma.
- Android Room icin veri koruyan migration; PC icin kalici, transaction destekli yerel veri deposu ve eski last-sync.gts dosyasindan tek seferlik aktarim.
- Kayit degisikligi ve gonderim kuyrugu ayni transaction icinde yazilsin. Basarili kalici kayittan sonra ACK verilsin.
- Silmeleri tombstone olarak aktar; tekrar gonderimleri islem kimligiyle tek kez uygula. Kismi aktarimda kuyrugu kaybetme.
- Cakismalari yalnizca cihaz saatine gore cozme. Ortak taban revizyonu ile eszamanli degisiklikleri tespit et; iki kopyayi da saklayip kullaniciya secim sun. Duzenleme-silme cakismalari da dahil.
- Aktif uyku ve ogun oturumlari ortak kimlik tasisin. Iki cihazda ayni anda baslatma/bitirme icin acik cakisma kurali uygula.
- Yeni protokolde karsilikli yetenek/surum kontrolu, sifreli aktarim, kalici eslestirme ve eslestirmeyi kaldirma olsun. Kisa kod yalnizca ilk eslestirme icin kullanilsin; kalici cihaz anahtarlari platformun guvenli deposunda tutulsun.
- Ortak test vektorleriyle Java ve Go protokol uyumlulugunu dogrula.

### PC Ozellikleri

- Uyku: baslat/bitir, gecmis kayit ekle, tarih ve saat duzelt, sil, hedef ve mobildeki dongu hesaplayicisi.
- Beslenme: ogun baslat/bitir, unutulan ogun ekle, yiyecek/kategori/miktar, ozel yiyecek ve hazir menu yonetimi.
- Su: yarim bardak dahil miktar secimi, hedef, tarihli ekleme, duzenleme ve silme.
- Ajanda: gun secimi, not ve gorev ekleme/duzenleme, tamamlandi/yapilmadi, genel hedefler, hazir gorevler, tekrar ve seri, mevcut nottan gorev cikarma ve import davranisinin karsiligi.
- Ozel alan: mevcut gizlilik davranisini koru; otomatik ve manuel kayit yonetimi. Hassas kayitlari kullanici gorunur bicimde izin vermeden otomatik eslestirmeye dahil etme.
- Istatistik: haftalik/aylik filtre, hedef ilerlemesi; arama ve CSV/JSON disa aktarma.
- Hatirlatici tanimlarini senkronize et; yerel bildirim izinleri ve cihaz kilidi gibi cihaza ozel ayarlari kopyalama. Iki cihazda cift bildirim davranisini ayarlanabilir yap.
- Tek buyuk main.js yerine ekran, form, veri erisimi ve senkron durumu modullerine ayir. Mobilin terimlerini, renklerini ve is akisini koru; masaustunde uygun genislikleri kullan.

### Otomatik Yansima

- Harici sunucu, bulut hesabi veya API anahtari yok; cihazlar dogrudan yerel agda haberlesir.
- Eslesme sonrasinda baglanti varken degisiklikleri otomatik aktar, bekleyen degisiklik sayisi ve son basarili senkronu goster.
- PC kapaliyken veya ag erisimi yokken iki uygulama bagimsiz calissin. Tekrar baglaninca kuyruklar birlestirilsin.
- Android arka plan kisitlari nedeniyle uygulama kapaliyken anlik aktarim garanti edilmez. On planda baglanti ve yeniden acilista senkron saglanir; arka plan politikasi ayrica test edilir.

## Dogrulama

- Iki tarafta olusturma, duzenleme, silme; ayni kaydi cevrimdisiyken iki tarafta degistirme.
- Kesilen aktarim, tekrar gonderim, ACK kaybi, uygulama kapanmasi, yeniden baglanti ve farkli cihaz saatleri.
- Silinen kaydin geri gelmemesi ve aktif oturumlarin cogalmamasi.
- Eski Android DB ve PC snapshot yedeklerinden veri koruyan gecis testleri.
- Go birim ve race testleri; sync-protocol ve Android data/feature testleri; PC frontend build ve Android assembleOptimized.
- PC arayuzunde gercek ekleme/duzenleme/silme akislari, emulator ile iki yonlu baglanti ve mumkunse Samsung-PC uctan uca testleri.
- Son teslim: uyumlu APK ve Windows EXE birlikte; yapilmayan fiziksel cihaz testleri acikca belirtilir.

## Uygulama Sirasi

1. Veri kimlikleri, migration, kalici kuyruk ve birlestirme testleri.
2. Iki yonlu protokol, eslestirme ve yeniden baglanti.
3. PC ozellik modulleri ve mobil veri kapsamiyla esitleme.
4. Arayuz, uctan uca testler ve birlikte surumleme.

## Uygulama Durumu

Snapshot protokolu v3, Room v10 migration, silme tombstone'lari, sifreli iki yonlu aktarim ve temel PC CRUD akislarina gecildi. Ilk surum kullanici tarafindan baslatilan yerel ag senkronunu tamamliyor. Kalici cihaz anahtari, otomatik arka plan senkronu ve nedensel cakisma ekrani sonraki asama olarak acik tutuldu; canli v3 birlestirmesi su anda kayit guncelleme zamanini kullanir.
