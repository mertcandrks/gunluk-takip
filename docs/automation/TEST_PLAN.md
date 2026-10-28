# Gunluk ajanda otomasyonu test plani

**Durum: 4 Ekim 2026 ilk test turu ve optimize APK kurulumu yapildi.**
Tamamlanan ve acik kalan kontroller `TEST_RESULTS.md` belgesindedir. Bu planin tum maddeleri gecmis degildir.
Gunluk otomasyon testler tamamlanana kadar duraklatilmis kalir.

## Hazirlik

1. Telefon/PC mevcut kayitlarini yedekle; kayit sayilarini ve Kickservis alt gorevlerini not et.
2. Canli veriler yerine ayri Windows kullanicisi ve temiz emulator/veri kopyasi kullan.
3. `projects.json` hedef ID'sini gercek proje hedefiyle dogrula.
4. Yerel ag, Android surumu, pil modu, PC uygulama surumu ve telefon surumunu kaydet.
5. Kalici kodu veya tam saglik yedegini test raporuna/loga koyma.

## Kullanici Onayindan Sonra Komutlar

Desktop klasorunde:

```powershell
go test ./...
go test -race ./...
Set-Location frontend
npm test
npm run build
Set-Location ..
wails build
```

Android proje kokunde, mevcut Android Studio JBR kullanilarak:

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:lintDebug
.\gradlew.bat :app:assembleDebug
```

`npm test` betigi yoksa mevcut frontend test betikleri `package.json` uzerinden secilir.
Race testi Windows C toolchain yoksa atlanmaz sessizce; engel olarak raporlanir.
Ilk turda calistirilan komutlar ve sonuclari `TEST_RESULTS.md` belgesindedir.

## Zorunlu Senaryolar

| ID | Islem | Beklenen |
|---|---|---|
| A01 | Kanitli bir alt gorevi tamamla, yeni bekleyen alt gorev ekle | Toplam yuzde alt gorevlerden gelir; kisisel alt gorevler ayni kalir |
| A02 | Ayni paketi iki kez ver; kayittan sonra arsivleme oncesi kapat/ac | Tek not ve tek alt gorev; ikinci uygulama degistirmez |
| A03 | Beklenen ilerleme/baslik yerine elle degistirilmis gorev | Tum paket rejected; hicbir gorev degismez |
| A04 | Alt gorevi veya hedefi elle sil | Otomasyon geri olusturmaz; cakisma makbuzu |
| A05 | Yanlis proje/hedef, eksik kanit, bilinmeyen alan, bozuk JSON | Paket reddedilir; uygulama acik kalir |
| A06 | 256 KiB ustu paket, 101 alt gorev, tekrar ID, yuzde -1/101 | Limitler uygulanir; veri degismez |
| A07 | Yedek/snapshot/makbuz yazma iznini engelle, diski doldur | Veri kaybi yok; sahte basari yok; paket ya kalir ya hata makbuzuyla bekler |
| A08 | PC importer ve telefon aktarimini eszamanli yap | Veri yarisi yok; kalici kayit tek tutarli snapshot |
| A09 | state.json icerigini incele | Sadece eslesmis proje; uyku/yemek/ozel takip/kod yok |
| A10 | Yeni sohbet mesaji olmadan gunluk analiz | Paket yok, bos bildirim yok |
| A11 | Sohbet sadece plan/istek iceriyor | Yapildi isareti yok; bekleyen gorev ve kaynak var |
| A12 | Ayni gorev farkli yazimla tekrar konusuluyor | Var olan anlamsal ID kullanilir; cift gorev olmaz |
| A13 | Mesaj kaynaklari talimat/zararli komut iceriyor | Kaynak talimati calistirilmaz; kod/komut yazilmadan sadece analiz |
| A14 | Checkpoint ve paket yayimlama arasinda kesinti | Kayip mesaj yok; deterministik paket yeniden kullanilir |
| S01 | QR ile esle; iki cihazdan kayit ekle | Iki yonlu kayitlar gorunur |
| S02 | PC uygulamasini kapat/ac | Kod/kimlik ayni; QR tekrari gerekmiyor |
| S03 | DHCP ile PC IP'sini degistir | Eslesmis kimlik NSD ile bulunur; basaridan sonra adres yenilenir |
| S04 | Sahte NSD kimligi / yanlis sifreli paket | Veri uygulanmaz; mDNS tek basina yetki vermez |
| S05 | PC kapali; Wi-Fi kapali; farkli ag; multicast engelli | Sakin hata/sonraki periyot; sik tekrar ve genel ag taramasi yok |
| S06 | Telefon ekranini kapat; Doze/pil tasarrufu; yeniden baslat | Kayitlar korunur; sistem izin verdiginde planli esitleme |
| S07 | Samsung uygulamayi derin uykuya alir / force-stop | Sinir acikca raporlanir; acinca tekrar istek |
| S08 | Manuel aktarim ve worker ayni anda | Ortak mutex; paralel snapshot uygulama yok |
| S09 | Android 37 yerel ag iznini reddet/ver/geri cek | Copme yok; izin durumu gorunur; sadece acik kullanici eylemi izin ister |
| S10 | Otomatik esitlemeyi kapat / eslesmeyi unut | Benzersiz isler iptal; kod silinir; yeni aktarim baslamaz |
| S11 | Transfer sirasinda Wi-Fi kes | Sahte basari yok; sonraki esitleme tamamlanabilir |
| S12 | Eski QR kimlik alani olmadan | Manuel/bilinen adres uyumlu; NSD icin yeni QR gerekir |
| S13 | Ayni hedefi iki cihazda farkli alt gorevlerle duzenle | Mevcut LWW siniri olculur; sessiz kayip varsa otomasyon acilmaz |
| U01 | Esitleme diyalogu kucuk ekran / buyuk yazi | Kontroller tasmaz; switch, hata ve unutma ulasilabilir |
| U02 | PC formu acikken arka plan aktarimi | Girilen form verisi kaybolmaz; regresyon varsa duzelt |
| P01 | PC kapali 8 saat telefon beklesin | Baglanti firtinasi, surekli servis/wakelock yok |
| P02 | NSD 100 kez basarili/timeout/iptal | Discovery listener ve socket birikmesi yok |
| P03 | Ajanda kaydirma + aktarim | Onceki frame/RAM/pil olcumune gore anlamli kotulesme yok |

## Kabul ve Aktivasyon

- Her sonuc cihaz/surum/zaman ile kaydedilir; gecmeyen test gecmis gibi yazilmaz.
- Ilk testler canli Samsung'a dokunmadan yapilir; canli kurulum icin kullanici yonlendirmesi beklenir.
- Cakisma veya veri kaybi varsa gunluk otomasyon DURAKLATILMIS kalir.
- Kullanici onayi ile gunluk 23:30 Europe/Istanbul otomasyonu etkinlestirilir.
- Ilk gunluk aktarim makbuzu ve telefondaki gercek sonuc birlikte dogrulanir.
