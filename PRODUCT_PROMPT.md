# Uyku ve Beslenme Takip Uygulamasi - Uygulama Promptu

Android Studio icin Kotlin ile, tamamen Turkce ve internetsiz calisan bir "Gunluk Takip" uygulamasi gelistir.
Uygulama bugunku uyku ve beslenme aliskanliklarini hizli sekilde kaydetmeli, kullaniciyi uzun formlarla yormamali
ve ileride su, egzersiz, ilac ve ruh hali takibi eklenebilecek bicimde bolumlere ayrilmalidir.

## Teknik temel

- Minimum Android 8.0 (API 26), hedef SDK 36 ve Kotlin kullan.
- Android Studio disinda ek bir arac gerektirme.
- Tum verileri cihazda sakla; internet, hesap ve reklam kullanma.
- Ekranlari ve veri erisimini ayri siniflarda tut. Tek bir Activity yalnizca bolumler arasi gecisi yonetsin.
- Uygulama kapansa veya telefon yeniden baslasa bile aktif uyku ve aktif ogun devam etsin.
- Bozuk yerel veri uygulamayi cokertmesin; guvenli varsayilan degerlere donsun.

## Ana gezinme

- Ust baslikta "Gunluk Takip" yazsin.
- Altinda iki belirgin sekme bulunsun: "Uyku" ve "Beslenme".
- Secilen sekme renk ve vurgu ile belli olsun; ekran degisimi aninda gerceklessin.

## Uyku bolumu

- Canli saat ve tarih goster.
- "Uykuya Basla" ve "Uyandim" eylemleri sun.
- Aktif uykunun suresini saniye saniye goster.
- Son uyku, ortalama uyku, son yedi gun toplami ve son 30 kaydi goster.
- Tum uyku kayitlarini onay isteyerek temizleyebil.

## Beslenme bolumu

- Bugunun tarihini ve ozetini goster.
- Bugunku ana ogun sayisi, atistirma sayisi ve son iki yeme kaydi arasindaki sureyi hesapla.
- Ogun turleri: Kahvalti, Ogle Yemegi, Aksam Yemegi, Atistirma ve Icecek.
- "Ogun Baslat" eyleminde tur sectir ve baslangic saatini kaydet.
- Aktif ogunde gecen sureyi canli goster.
- Aktif ogune tek dokunusla hazir secenekler eklenebilsin:
  - 1 yumurta, ekmek, peynir, zeytin, pizza, corba, salata, tavuk, pilav, meyve, yogurt, cay, kahve, su.
- "Baska Ekle" ile kullanici serbest metin yazabilsin ve miktar belirtebilsin.
- Ayni yiyecegin tekrar eklenmesi kaybolmasin; her ekleme ayri madde olarak gorunsun.
- "Ogunu Bitir" bitis saatini ve toplam sureyi kaydetsin.
- Cay, kahve, meyve gibi kisa kayitlar icin "Hizli Kayit" sun; tur ve icerik secilerek baslangic/biyis gerektirmeden kaydedilebilsin.
- Kayitlari gunlere gore, en yeniden eskiye listele.
- Ayni turdeki kayitlari gun icinde numaralandir: "Kahvalti 1", "Atistirma 2" gibi.
- Her kayitta tarih, baslangic-bitis saati, sure, onceki kayittan gecen sure ve yenilen/icilen maddeler gorunsun.
- Tek bir kayit silinebilsin; tum beslenme gecmisi ayrica onayla temizlenebilsin.

## Deneyim ve tasarim

- Koyu, sakin fakat tek renk olmayan bir palet kullan: lacivert zemin, yesil beslenme vurgusu, mor uyku vurgusu,
  turuncu ikincil vurgu ve acik okunakli metinler.
- Dokunma alanlari buyuk, metinler telefonda tasma yapmayacak sekilde olsun.
- Aktif ogun yokken yiyecek ekleme dugmeleri devre disi kalmasin; dokunuldugunda once ogun baslatilmasi gerektigini acikla.
- Silme gibi geri donusu zor eylemlerde onay iste.
- Bos liste durumlarinda kisa ve faydali bir mesaj goster.

## Teslim ve dogrulama

- Eksiksiz, derlenebilir Android Studio projesi teslim et.
- Debug APK olustur.
- Emulatorde uyku baslat/bitir, ogun baslat, hazir ve ozel yiyecek ekle, ogun bitir, hizli kayit ve silme
  akislariyla temel davranisi test et.
