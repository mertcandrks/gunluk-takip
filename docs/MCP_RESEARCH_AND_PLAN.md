# Gunluk Takip MCP Arastirmasi ve Uyarlama Plani

Tarih: 2026-10-24

Uygulama notu: Bu arastirmayi izleyen ilk calisan stdio/ADB uygulamasi
`../mcp-bridge/README.md` altinda belgelenmistir. Asagidaki tasarimin tum
gelecek kapsam maddeleri v1'de tamamlanmis degildir; guncel sinirlar README'dedir.

Durum: Kaynak kodu, resmi SDK belgeleri ve guncel upstream lisanslari incelendi.
Bu belge entegrasyon veya kurulum tamamlandi anlamina gelmez. Uygulama kodu,
istemci ayarlari ve telefondaki veriler bu arastirmada degistirilmedi.

## 1. Sonuc

Uygulamaya ozel veri ve islem kontrolu icin hazir bir protokol temeli var:
resmi `modelcontextprotocol/go-sdk`. Ancak Gunluk Takip'in kayit yapisini ve
is kurallarini bilen, dogrudan alip calistirabilecegimiz bir MCP sunucusu bulunmadi.

Onerilen uyarlama:

```text
MCP destekli AI istemcisi
  -> PC: bagimsiz Go MCP executable / stdio
  -> yetkilendirilmis USB veya kablosuz ADB
  -> Android: uygulamaya ozel ContentProvider.call
  -> domain islem katmani
  -> mevcut Repository API'leri
  -> Room / mevcut ayar deposu
  -> mevcut Flow uzerinden UI guncellemesi
```

Bu, bir mimari oneridir. ContentProvider koprusunun tam davranisi Samsung ve
API 26/API 37 emulatorde bir uyumluluk deneyiyle dogrulanmalidir.

Harici backend, hesap, ucretli LLM API'si veya PC arayuzunun acik olmasi gerekmez.
PC'de MCP sureci ve telefonla yetkilendirilmis ADB baglantisi gerekir. ADB yetkisi
genis bir cihaz guven siniridir; uygulama ici yetkilendirme bunun yerine gecmez.

## 2. Hazir Projeler

### Resmi Go SDK: secilen protokol temeli

- Kaynak: https://github.com/modelcontextprotocol/go-sdk
- Kontrol edilen son release: `v1.8.0`; yayin tarihi 2026-09-14.
- Release go.mod: Go `1.25.0` ister.
- `mcp.AddTool` ile tiplerden input/output schema, argument dogrulama ve
  structured result uretebiliyor; stdio sunucusu icin resmi ornek mevcut.
- Mevcut Wails modulu `go 1.23` bildirdiginden ayri `mcp-bridge/go.mod`
  kullanilmali; masaustu uygulamasinin toolchain'i sirf bu nedenle degistirilmemeli.
- Lisans dosyasi MIT -> Apache-2.0 gecisini ve belge lisansini acikliyor.
  Secilen surumun LICENSE/NOTICE gereklilikleri korunmali; tek kelimelik
  GitHub lisans etiketine guvenilmemeli.
- SDK dependency olarak kullanilmali; MCP JSON-RPC/handshake kodu yeniden yazilmamali.

### Resmi Kotlin SDK: ADB'siz HTTP secenegi icin alternatif

- Kaynak: https://github.com/modelcontextprotocol/kotlin-sdk
- Stdio ve Ktor uzerinden Streamable HTTP sunucu ornekleri var.
- Ktor engine bagimliligini otomatik eklemiyor; Android'e uygun engine ve
  release/R8 uyumlulugu ayri dogrulanmali. JVM destegi tek basina Android kaniti degildir.
- Telefonda HTTP dinleyicisi, kimlik dogrulama ve arka plan yasam dongusu gerektirir.
- Su an ADB koprusunden daha fazla bagimlilik ve isletim yukune sahip bir alternatif.

### kaeawc/android-mcp-sdk

- Kaynak: https://github.com/kaeawc/android-mcp-sdk
- Lisans: Apache-2.0.
- Android uygulamasina MCP yerlestirme, Startup ve DI entegrasyonu mevcut.
- README acikca debug-only diyor; release'e dahil edilirse bilerek crash ettigini soyluyor.
- Biz kullanicinin gunluk kullandigi `optimized` APK'yi desteklemek zorundayiz.
  Bu nedenle oldugu gibi kullanmak uygun degil; release kilidini kaldirmak da
  guvenli urun entegrasyonu yapilmis oldugu anlamina gelmez.

### cyclops-top/android-mcp

- Kaynak: https://github.com/cyclops-top/android-mcp
- Uygulama ici Ktor sunucusu ve Room 2/Room 3 adapterleri var.
- README gereksinimleri: minSdk 29, Kotlin 2.3+, Hilt 2.59+.
- Bizim minSdk 26; DI yapimiz Hilt degil. Dogrudan eklemek mimari degisikligi ister.
- Ornek konfigurasyonda bind adresi `0.0.0.0`; bu guvenli varsayilan olarak alinmamali.
- Kontrol edilen repository kokunde LICENSE bulunmadi; GitHub lisans metadatasi da bos.
  Acik kopyalama/degistirme izni dogrulanmadan kodu kopyalanmamali.
- Room `execute_sql` araci urun MCP'sinin veri yazma API'si olarak kullanilmamali.

### Zzechen/client-tools

- Kaynak: https://github.com/Zzechen/client-tools
- Lisans: MIT.
- Ekran agaci, screenshot, layout degistirme, dokunma ve custom HTTP route destegi var.
- README'deki release entegrasyonu noop; asil amaci gelistirme ve UI inceleme.
- Custom route fikri uygun, fakat tum UI/mocking altyapisini tasimak bizim veri
  kontrolumuz icin gereksiz. Gunluk Takip is kurallari yine bizim tarafimizdan yazilir.

### Mobile Next / mobile-mcp

- Kaynak: https://github.com/mobile-next/mobile-mcp
- Lisans: Apache-2.0.
- Android/iOS ekran otomasyonu ve accessibility tabanli kullanim icin uygun.
- Butonlara basabilir; uygulamanin domain modeli, transaction'lari veya kalici
  kayit kimlikleri icin ozel bir API saglamaz.
- Bizdeki Android diagnostics ve Maestro ile buyuk olcude ayni ihtiyaci karsiliyor.
  Veri MCP'sinin yerine veya sirf arac sayisini artirmak icin eklenmemeli.

### DroidRun / Mobilerun Portal

- Guncel kaynak: https://github.com/droidrun/mobilerun-portal
- Yerel API: https://github.com/droidrun/mobilerun-portal/blob/main/docs/local-api.md
- HTTP/WebSocket yaninda ADB ContentProvider tasarimi var; bu, onerilen
  transport deseninin var olan bir ornegidir. Bizim kayit API'miz yine ayri olacaktir.
- Onemli kanit farki: arama onizlemelerinden biri eski MIT lisansini gosterirken,
  kontrol edilen guncel LICENSE dosyasi AGPL-3.0-or-later metni tasiyordu.
- Guncel lisans kosullari degerlendirilmeden kaynak kodu uygulamaya kopyalanmamali.
  Oneri: Android'in kendi ContentProvider API'sini kullan; genel cihaz kontrol
  uygulamasini Gunluk Takip'e gommek yerine yalniz transport desenini referans al.

## 3. Projede Dogrulanan Durum

- Tek dogruluk kaynagi `:data` icindeki Room; UI Repository Flow'larini izliyor.
- Uyku, ajanda, fikirler, alisveris, ozel takip ve ayarlar icin mevcut API'ler var.
- Ajandada sure, alt gorev, tekrar sayaci ve birlestirme artik mevcut.
- `desktop-wails/automation_inbox.go` proje alt gorevlerini batch ile guncelliyor;
  baseline conflict kontrolu, batch kimligi ve kayit mantigi degerli.
- Bu inbox tum uygulama kontrolu degil; ayrica masaustu surecine bagimli.
- `SyncSnapshotRepository` ve PC snapshot protokolu fikir/alisverisi kapsamiyor;
  ajanda sure alanlari Android'de korunuyor ama PC protokolune tasinmiyor.
- Mevcut PC senkronu telefondan PC'ye snapshot gonderme/merge akisi. Telefona
  dogrudan domain komutu gonderen bir uygulama API'si degil.
- Projedeki manifest su anda boyle bir provider tanimlamiyor.
- `TrackerApplication` eski veri aktarimini asenkron baslatiyor. Yeni API,
  bu aktarimla yarisarak erken okuma/yazma yapmamali; readiness beklemeli.

## 4. Erisim Kapsami

MCP kullanici verisi ve uygulama davranisini kontrol eder. Uygulamanin kaynak
kodunu degistirip APK derlemek, veri araclarindan ayri gelistirme araclari ister.
Yeni ekran/ozellik kendiliginden veri MCP'si uzerinden olusmaz.

| Alan | Okuma | Yazma |
| --- | --- | --- |
| Ajanda | Gun/aralik, arama, gorev detayi, alt gorevler | Ekle/toplu ekle, duzenle, tamamla/yeniden ac, yapilmadi, ertele |
| Genel hedef | Hedefler, ilerleme | Gunluk/genel tasima, alt gorev ilerlemesi, birlestirme |
| Gorev suresi | Aktif sayac, kayitli sure | Baslat, duraklat/devam, bitir |
| Hazir gorev | Favoriler, onceki gorevler, kategoriler | Sablon/favori/kategori ekle-duzenle-kaldir |
| Uyku | Aktif oturum, tum gecmis sayfalari, ozet | Baslat/bitir, eksik kayit, baslangic/bitis duzeltme |
| Fikirler | Bekleyen/yapilan fikirler, olusturma/tamamlanma zamani | Ekle, duzenle, tamamla ve ajandaya aktar |
| Alinacaklar | Kisa/uzun vadeli listeler | Ekle, duzenle, alindi durumunu degistir |
| Ayarlar | Uyku hedefi, hatirlatici durumlari | Hedef ve hatirlatici guncelle; alarm etkisini de uygula |
| Istatistik | Gunluk/haftalik/aylik hesaplanan ozetler | Kaynak kayitlari duzenle; hesaplanan sonucu elle bozma |
| Yedek/ice aktarma | Islem onizlemesi, surum | Dosya temelli kontrollu aktarim/geri yukleme |
| Ozel alan | Yalniz ayri izin verilince | Ayri okuma/yazma izni ve acikca yetkilendirilmis islemler |
| Tanilama | Surum, baglanti, sinirli performans bilgisi | Izinli gelistirici tercihleri; sinirsiz cihaz komutu degil |

Yemek/su ana menuden kaldirilmis. Eski veriler silinmez; ana MCP kataloguna
aktif ozellikmis gibi otomatik eklenmez. Istenirse ayri legacy scope ile sunulur.

Ornek arac adlari: `agenda_list`, `agenda_create_batch`, `agenda_update`,
`agenda_parts_update`, `agenda_timer_start`, `agenda_timer_pause`,
`agenda_timer_finish`, `agenda_merge_preview`, `agenda_merge_apply`,
`sleep_list`, `sleep_update`, `ideas_finish`, `shopping_update`, `settings_update`.

Katalog domain'e gore gruplansin; her islem tipli schema ve structured response
versin. Tek bir sinirsiz `execute_sql` veya `execute_shell` araci olmasin.
`app_capabilities` araci surumu, desteklenen islemleri ve verilmis scope'lari bildirir.
Okumalarda tarih filtresi ve pagination kullanilir; tum gecmis sessizce 30 kayitla kesilmez.

## 5. Yetki ve Veri Butunlugu

1. Uygulama icinde MCP erisimi kapali baslar; kullanici acip istemciye scope verir.
   Her siradan kayit icin tekrar onay sormak yerine bu izin kapsaminda calisir.
2. Provider `call` girisinde Binder caller UID, token, etkinlik ve islem iznini
   kontrol eder. Read-only etiketi veya MCP annotations yetkilendirme degildir.
3. Android ContentProvider.call, isleme ozel read/write iznini bizim yerimize
   kontrol etmez. Sadece exported/provider tanimi yaparak acmak yeterli degildir.
4. Butun islemler mevcut repository/domain kodundan gecmeli. UI tarafinda kalan
   bildirim planlama gibi etkiler ortak islem katmanina alinmali; yalniz DB yazmak eksiktir.
5. Yazmalar `requestId` ve kalici receipt ile idempotent olmali. Baglanti cevap
   gelmeden kopunca ayni istegin tekrari iki gorev veya iki uyku kaydi uretmemeli.
6. Kullanici veya baska AI arada kaydi degistirdiyse revision uyusmazligi donmeli.
   Revision kontrolu ve mutation ayni transaction'da yapilmali; saat damgasi tek basina
   saglam bir optimistic concurrency surumu olarak kabul edilmemeli.
7. Birlestirme, toplu aktarim ve silme once etkilenecek kimlikleri/onizlemeyi dondurur.
   Verilmis izinlere gore apply yapilir. Toplu temizleme varsayilan katalogda kapali;
   geri alinamayan islemler ozel yetki ister. Silme/geri alma mevcut tombstone
   ve senkron kurallariyla birlikte tasarlanir.
8. Gunlukte istemci, arac, requestId, kayit kimlikleri ve sonuc tutulur. Token,
   ozel alan metni ve tum kayit icerikleri loglara dokulmez.
9. Ozel alan izni normal `get_state`, istatistik, export veya arama yoluyla asilamaz.
10. Istek boyutu, cevap boyutu, timeout, sayfa boyutu ve eszamanlilik sinirli olmali.
    ADB komut satiri ve Binder boyut sinirlari icin buyuk dosya/aktarim ayri kanaldan yapilir.
11. Islem ve kayit metinleri talimat degil veridir; bir gorev metni MCP yetkisi veremez.

## 6. Baglanti ve Pil

- Yerel PC istemcileri icin stdio: AI istemcisi exe'yi baslatir; ek TCP portu gerekmez.
- Telefona ADB: mevcut kablosuz eslesme veya USB. Root gerektiren tasarim hedeflenmez;
  optimize build ve fiziksel cihazda erisim uyumlulugu deneyle kanitlanir.
- Birden fazla cihazda otomatik rastgele hedef secilmez; yetkili cihaz kimligi sabitlenir.
- ContentProvider istege cevap verir; surekli telefon HTTP sunucusu, polling ve wake lock
  gerektirmez. Cagri basina uygulama sureci baslatma maliyeti olabilir; olculmelidir.
- Ekrani acmak/dokunmak normal domain cagrisinin parcasi degildir.
- Ekran kilidi, ilk acilistan sonra kimlikli ADB erisimi ve yeniden baslatma sonrasi ilk
  kilit acma durumu ayri test edilir. Android force-stop/OEM davranisi icin garanti verilmez.
- ADB'siz baglanti ileride gerekiyorsa Kotlin SDK + kimlik dogrulamali Streamable HTTP
  ayri transport olur. Origin kontrolu, TLS, istemci izni, Android yasam dongusu ve
  foreground service/pil maliyeti yeniden degerlendirilir; public tunnel otomatik acilmaz.
- Bulut tabanli bir AI istemcisi PC'deki stdio exe'ye kendiliginden ulasamaz.
  MCP destekli yerel istemci/local connector gerekir; uzaktan endpoint ayri karardir.
- MCP koprusu LLM servisi degildir. Veriyi bagladigin AI istemcisine verir; istemcinin
  bulut modeline gondermesi, uygulamanin cevrimdisi olmasindan ayri bir veri akisidir.

## 7. Uygulama Sirasi ve Kabul Kontrolleri

### T0: Transport deneyi

Sadece emulator ve sentetik veri ile: Go SDK initialize/tools/list, Android ping,
capabilities ve versiyon okuma. UID/token reddi, app kapaliyken cagri, optimized/R8,
Turkce metin/quote/newline, birden fazla cihaz ve kopan ADB kontrol edilir.
Basarili olmadan domain kodunu bu transporta baglamamak gerekir.

### T1: Salt okunur tam katalog

Ajanda, uyku, fikirler, alisveris, ayar ve istatistik okumalarini repository'lere bagla.
Sayfalama ve yetki filtrelerini test et. Ozel/legacy alanlar ayri scope'ta kalsin.

### T2: Kayit islemleri

Mevcut butun aktif domain islemlerini tipli MCP araclarina bagla. Yeni feature'larin
ileride kayit olabilecegi kucuk bir domain kayit noktasi kullan; mevcut repository'leri
MCP'ye bagimli yapma. Revision/receipt/transaction kurallariyla yazmalari dogrula.

### T3: Guclu islemler ve kullanici kontrolu

Birlestirme/aktarim/silme onizlemesi, izin ekrani, istemci iptali, sinirli audit log,
uygun yedek ve geri alma. Bildirim planlama, fikirden ajandaya gecis ve surelerin
durmasi UI ile MCP arasinda ayni sonucu vermeli.

### T4: Uctan uca teslim

- Resmi MCP istemcisiyle initialize, tool discovery ve structured error testleri.
- MCP ile ekle/duzenle -> UI'da gor; UI ile degistir -> MCP'de gor.
- Duplicate request, paralel istemciler ve conflict sonucunda kayit kaybi olmamasi.
- Room migration ve yedekten geri donme testi; uyku gecmisinin korunmasi.
- Ekran kapali/process death/ilk kilit acma sonrasi davranis.
- Su anki optimized build'e gore startup, kaydirma, RAM ve bosta pil etkisi olcumu.
- En son Samsung'a `adb install -r`; gercek verilerle test kaydi olusturmadan smoke kontrol.

## 8. Kaynaklar ve Kanit Seviyesi

Birincil belgeler:

- https://github.com/modelcontextprotocol/go-sdk/releases/tag/v1.8.0
- https://github.com/modelcontextprotocol/go-sdk/blob/v1.8.0/go.mod
- https://github.com/modelcontextprotocol/go-sdk/blob/v1.8.0/LICENSE
- https://github.com/modelcontextprotocol/kotlin-sdk
- https://developer.android.com/reference/android/content/ContentProvider
- https://developer.android.com/guide/topics/providers/content-provider-creating
- https://modelcontextprotocol.io/specification/draft/server/tools
- https://modelcontextprotocol.io/specification/draft/basic/transports/streamable-http

Kontrol edilen upstream main commit'leri:

| Repository | Commit |
| --- | --- |
| modelcontextprotocol/go-sdk | 2e00768cde3f1ba31d898ea1f179e206d1ad27ef |
| modelcontextprotocol/kotlin-sdk | a3909eff09f5f31cc05439a3cb3a8dcdcd4abbc7 |
| kaeawc/android-mcp-sdk | f89b2e404382ae221457c038c041c13d7be567a4 |
| cyclops-top/android-mcp | 86f4ae8c831b4be511347c564553e3ba18a60abe |
| Zzechen/client-tools | c077d245056ffb950b856f795f836f87b8505c43 |
| droidrun/mobilerun-portal | da4e384d10f9d6bfa26f8f0c7b653f7eae34caaf |
| mobile-next/mobile-mcp | c659f8ee34873a832adea7daca9c69172d887e70 |

Repository main sayfalari ve draft protokol zamanla degisebilir. Uygulamada release
surumu pinlenmeli, ilgili LICENSE/NOTICE korunmali ve istemci protokol uyumu test edilmeli.
Arastirma kaynak incelemesidir; aday paketler bu turda derlenmedi veya telefona kurulmadilar.
