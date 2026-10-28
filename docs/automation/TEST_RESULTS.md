# Test sonuclari - 4 Ekim 2026

## Gecen otomatik kontroller

- `gradlew.bat test`: 11 JUnit raporunda toplam 39 test, 0 hata/basarisizlik.
- `gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleOptimized`: BUILD SUCCESSFUL.
- `go test -v ./...`: ana pakette 16, syncmodel paketinde 7 test; toplam 23 test gecti.
- `go test -race ./...`: gecti; race detector hatasi bildirmedi.
- `npm test`: 4 frontend testi gecti.
- `npm run build`: frontend production paketi olusturuldu.
- `wails build -skipbindings -s`: PC executable olusturuldu.

Toplam otomatik test: 66. Bu sayi cihazdaki manuel/uc-tan-uca testleri kapsamaz.

## Duzeltilen sorun

Ilk lint calistirmasi CameraX `ImageProxy.image` kullanimindaki eksik opt-in nedeniyle
1 hata ile durdu. QR tarayicisina AndroidX opt-in bildirimi eklendi; ikinci lint gecti.
Surum 5.3 / versionCode 29 olarak guncellendi. Mevcut bagimlilik/deprecation uyarilari kaldi;
bu test turunda toplu bagimlilik guncellemesi yapilmadi.

## Yeni regresyon kapsami

- Gelen kutusu paketi kalici snapshot'a yaziliyor; once yedek aliniyor ve islenmis paket arsivleniyor.
- Elle degismis alt gorevle cakisan paket reddediliyor; snapshot degismiyor.
- Ayni paket iki kez uygulandiginda cift not/alt gorev olusmuyor.
- Elle girilmis, pakette belirtilmeyen alt gorev korunuyor.
- Eksik kanit, tekrar alt gorev ID ve JSON sonrasi ilave icerik reddediliyor.
- Kalici eslesme kimligi yeniden okununca ayni kaliyor; bozuk kimlik reddediliyor.
- QR kalici kimlik alani Android ve frontend testlerinde dogrulandi; eski QR uyumlulugu korundu.

Dosya sistemi testleri gecici kullanici konfigurasyon klasorunde yapildi; canli ajandaya sahte gorev eklenmedi.

## Emulator

- Medium_Phone / emulator-5554: debug APK, ardindan optimize 5.3 APK kuruldu.
- Optimize uygulama acildi; uyku ekrani ekran goruntusunden incelendi.
- Uygulama force-stop sonrasi tekrar acildi; mevcut aktif uyku takibi ekranda korundu.
- Istatistikler ve PC esitleme diyalogu semantik UI secicileriyle acildi.
- Android 37 yerel ag izin istegi gorundu; Allow secildi ve izin `granted=true` dogrulandi.
- Bu kontroller tam QR-kamera veya uc-tan-uca PC senkron testi degildir.

## Samsung

- ADB ile canli SM-G990B2 baglantisi dogrulandi; guncel kablosuz endpoint kesfedildi.
- `adb install -r` ile optimize APK mevcut uygulamanin uzerine kuruldu: Success.
- Paket bilgisi: versionCode 29, versionName 5.3, targetSdk 37.
- Uygulama baslatma sonucu Status ok; cold start TotalTime 627 ms.
- Uygulama sureci kurulum sonrasi goruldu; bu denemeye ait uygulama crash kaydi bulunmadi.
- Veri silme, uninstall veya veritabani temizleme yapilmadi.
- Telefon ekraninda uygulama sabit kalmadigi icin yeni eslesme ve gercek PC-telefon senkronu tamamlanmadi.

PC'nin eski snapshot'i kurulumdan once `artifacts/verification-20261004-181034/pc-before.gts`
altina yedeklendi. Bu dosya guncel telefon verisinin tam yedegi DEGILDIR.
Eski telefon paketi debuggable olmadigi icin run-as ile uygulama ici dosyalar okunamadi.

## Acik kalan kontroller

- Gercek Samsung ile ilk kalici QR eslesmesi, WorkManager aktarimi ve iki yonlu sonuc.
- PC IP degisimi / multicast engeli / ag kopmasi / yanlis kod.
- Ayni hedefin iki cihazda eszamanli duzenlenmesi ve mevcut LWW siniri.
- Tam kamera QR okuma, unutma/iptal, buyuk yazi ve kucuk ekran kontrolleri.
- Uzun sureli Doze, Samsung arka plan kisitlari, RAM/pil ve kaydirma olcumleri.
- Gunluk Codex analizinin gercek sohbetten paket uretmesi ve telefonda gorunmesi.

Gunluk 23:30 otomasyonu DURAKLATILMIS kaldi; bu test turunda etkinlestirilmedi.

## Ek Samsung Senkron Turu - 18:52 Sonrasi

- Yeni aktarimdan once PC snapshot'i ve telefonun kendi snapshot'i ayri ayri yedeklendi.
- Telefon yedegi sifreli protokol uzerinden alindi; cevap olarak degistirilmemis kendi snapshot'i gonderildi.
- Gercek PC uygulamasi telefon verisini aldi; yerel snapshot 18:59:32'de guncellendi.
- WorkManager logunda 18:59:32'de PcSyncWorker SUCCESS goruldu; zaman yerel PC kaydiyla eslesiyor.
- Telefonun aktarim sonrasi snapshot'i tekrar alindi.
- `TestLiveMergedSnapshotRoundTrip`: PC ve telefonun son verileri ilk iki snapshot'in beklenen birlesimiyle eslesti.
- Karsilastirma sadece sayi degil, tarihler/kimlikler/icerikler/alt gorevler/kataloglar/tombstone'lar dahil tum snapshot alanlarini kapsadi.
- Son kayit sayilari: 172 ajanda, 40 uyku, 5 ogun, 5 su. Gercek gorev veya test notu eklenmedi/silinmedi.
- Kickservis hedefi PC'nin secili proje durum dosyasinda gorundu; 27 alt gorev korundu.
- `go test -race ./...` yeni canli test yardimcilari eklendikten sonra yeniden gecti.

Ilk yedek sunucusu, alanlara veri girilirken 120 saniyelik sure sinirina ulasti; yeniden acilan tur gecti.
Yedek sunucusunun kapali oldugu arada manuel denemede ECONNREFUSED goruldu; gercek PC servisi tekrar acildi.
Ilk snapshot karsilastirmasinin katalog dizi siralamasi farkini veri farki saydigi goruldu;
test normalizasyonuna kimlige gore siralama eklendi ve tum alan karsilastirmasi gecti.
Bu olaylar basarili testler gibi sayilmadi.

Yedekler `artifacts/sync-phone-20261004-185235` altinda ozel veri icerir; paylasilmaz.
Canli PC-Samsung bilinen adresle senkron ve uygulama acilisindaki otomatik is artik dogrulandi.
Gercek QR-kamera, DHCP/IP degisimi, uzun sureli ekran kapali/Doze ve eszamanli farkli duzenleme testleri halen aciktir.
Gunluk Codex analiz otomasyonu bu turda da DURAKLATILMIS birakildi.

## Tek Seferlik AI Analizi - 19:12 Sonrasi

- Kullanici istegiyle mevcut Codex sohbetinde ayni analiz/aktarim akisi bir kere calistirildi; scheduler manuel-run araci calistirilmadi.
- Ilk calisma kurali geregi yalnizca 4 Ekim'e ait iki Kickservis turu incelendi; eski gunler yeniden aktarilmadi.
- Tamamlandi kaniti agent'in son yanitlarindan alindi; yeni bir site testi yapildigi iddia edilmedi.
- Hesap/token/parola gibi sohbet girdileri pakete veya checkpoint'e kopyalanmadi.
- Uc tamamlanmis duzeltme ve uc kanitli bekleyen is ayrildi; onceki 27 alt gorevin degerleri degistirilmedi.
- `kickservis-20261004-clips-copy-v1` paketi atomik olarak gelen kutusuna yayimlandi.
- PC 19:12:24'te `processed / Applied` makbuzu uretti; uygulama oncesi yedek importer tarafindan alindi.
- Checkpoint son iki kaynak turunu ve paket kimligini sakladi.
- Telefonun uygulama acilisindaki WorkManager isi 19:13:30'da SUCCESS oldu.
- Gercek Samsung ajanda kartinda `%90 · 30/33 alt görev`, alt gorev ekraninda `%90 · 30/33 tamamlandı` goruldu.
- Bugune ait analiz notu da telefonda gorundu. Alt gorev editoru degisiklik kaydetmeden Vazgec ile kapatildi.
- Gunluk otomasyon PAUSED kaldi; bu tur yalnizca tek seferlik AI analizinin ve aktariminin canli denemesiydi.
