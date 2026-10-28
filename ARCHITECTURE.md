# Uygulama Mimarisi

Bu proje, yeni takip alanlari ve raporlama ozellikleri eklenirken mevcut ozelliklerin birbirine dolanmamasi icin cok modullu kurulmustur.

## Modul Haritasi

```text
:app
  |-- :feature:sleep ------|
  |-- :feature:nutrition --|--> :data --> Room
  |                         |--> :core
  |-- :data ----------------|
  `-- :core
```

- `:app`: Application, Activity, ana Navigation grafigi ve dependency composition root.
- `:core`: Saf modeller, tarih/saat araclari, Material 3 tema ve ortak Compose bilesenleri.
- `:data`: Room entity/DAO katmani, repository API'leri, offline implementasyonlar ve eski veri aktarimi.
- `:feature:sleep`: Uyku route, ekran, editor, UI state ve ViewModel.
- `:feature:nutrition`: Beslenme route, ekran, analiz, UI state ve ViewModel.
- `:feature:stats`: Salt okunur donem ozetleri ve hedef ilerlemesi.
- `:feature:ideas`: Ajandadan bagimsiz, tarih atanmayan fikir notlari; Room ideas tablosu ve IdeasRepository.
- `:feature:shopping`: Kisa/uzun vadeli alinacaklar, alindi durumu ve duzenleme; ayri shopping_items tablosu. JSON disa aktarimina dahil, PC senkronuna dahil degil.
- Yemek ve su ekranlari ana menuden kaldirildi; eski tablolar ve verileri korunur. Ideas kayitlari JSON disa aktarimina dahildir; mevcut PC senkron protokolune dahil degildir.
- `:feature:settings`: Uyku hedefi, hatirlaticilar, CSV/JSON disa aktarma; cihaz baglantisi ve tanilama icin app callback'leri.

## Bagimlilik Kurallari

1. Feature modulleri birbirine baglanmaz.
2. UI, DAO veya Room entity kullanmaz; yalniz repository API ve core model gorur.
3. Data modulu feature kodu bilmez.
4. `:app` is kurali tutmaz; modulleri ve navigation hedeflerini birlestirir.
5. Modul ici implementasyonlar varsayilan olarak `internal` tutulur.

## MCP Erisimi

- `mcp-bridge/`: Bagimsiz Go modulu; resmi MCP Go SDK v1.8.0 ile stdio sunucusu.
- `mcp-bridge/contract.json`: Go ve Android'in paylastigi tek arac/schema katalogu;
  Gradle bunu uygulama asset'lerine kopyalar.
- `app/mcp`: Shell UID, anahtar ve scope kontrolu yapan ContentProvider ile
  kapali baslayan erisim/eslestirme arayuzu. Telefonda HTTP sunucusu veya polling yoktur.
- `data/McpRepository`: Mevcut repository'lere domain cagrilari; kayit hash'iyle
  optimistic conflict kontrolu, sayfalama ve hassas veri ayrimi.
- `mcp_receipts`: Mutation ile ayni Room transaction'inda saklanan requestId/hash/sonuc;
  baglanti kesintisinden sonraki tekrarlar cift kayit uretmez.
- Erisim anahtari PC'de kullaniciya ozel ACL'li dosyada, telefonda app-private
  tercihlerde tutulur; cloud backup ve device transfer'den haric tutulur.
- V1 sinirlari ve kurulum: [MCP Bridge](mcp-bridge/README.md).

## Veri Stratejisi

- Room uygulamanin tek dogruluk kaynagidir.
- Aktif uyku ve aktif ogun ayri tablolarda saklanir.
- Oturum bitirme, gecmise ekleme ve aktif kaydi kaldirma tek Room transaction icinde yapilir.
- Gecmis veriler sinirsiz saklanir ve LazyColumn ile tamami goruntulenir; 30 kayitlik gorunum siniri yoktur.
- Gorev onceligi (`agenda_entries.priority`) cihazda kalir; senkron protokolu v8'e kadar PC tarafina tasinmaz ve senkron yazimi yerel degeri korur.
- Alisveris listesi olusturulma tarihine gore gunluk gruplanir; `createdAtMillis` gruplama anahtaridir, kayitlar silinmez.
- `SharedPreferences` kullanan 2.x surum verileri ilk acilista Room'a idempotent olarak aktarilir.
- Veri tabani semasi `data/schemas` altinda surumlenir. Her schema degisikliginde Room migration yazilmalidir.

## UI Durum Akisi

```text
Room Flow -> Repository Flow -> ViewModel StateFlow -> Route -> Stateless Screen
User Action -> ViewModel -> Repository -> Room transaction -> Flow update
```

Composable ekranlar veri yazmaz. Kullanici eylemleri ViewModel'e gider; ekran Room'dan geri donen yeni state ile guncellenir.

## Sonraki Buyume Alanlari

- `:feature:dashboard`: Gunluk ozet ve hizli aksiyonlar.
- `:feature:reports`: Haftalik/aylik trendler ve grafikler.
- `:feature:water`, `:feature:activity`, `:feature:mood`: Yeni takip alanlari.
- Ayarlarin olasi genislemeleri: tema, ice aktarma ve yedekleme.
- `:sync`: Istege bagli hesap ve bulut senkronizasyonu; offline repository API'lerini bozmadan eklenebilir.
- `:notifications`: WorkManager ve alarm tabanli hatirlaticilar.

Yeni bir alan eklenirken [FEATURE_TEMPLATE.md](docs/FEATURE_TEMPLATE.md) kontrol listesi izlenir.
