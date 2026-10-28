# Günlük Takip / Daily Tracker

[Türkçe](#türkçe) | [English](#english)

![Kotlin](https://img.shields.io/badge/Kotlin-Jetpack%20Compose-7F52FF?logo=kotlin&logoColor=white)
![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white)
![Offline](https://img.shields.io/badge/Offline-first-2ea44f)

## Ekran görüntüleri / Screenshots

| Android (Uyku / Sleep) | Masaüstü / Desktop |
| --- | --- |
| ![Android sleep screen](docs/screenshots/sleep-tracking.png) | ![Desktop agenda](docs/screenshots/agenda-desktop.png) |

## Türkçe

**Günlük Takip**, uyku düzenini, görevlerini, alışkanlıklarını, fikirlerini ve alışveriş listelerini tek yerden yönetebileceğin, internetsiz çalışan bir Android uygulamasıdır.

### Özellikler

- **Uyku takibi:** Tek dokunuşla başlat/bitir, süreleri ve geçmişi gör.
- **Ajanda:** Görevler, alt görevler, notlar, hedefler ve hatırlatıcılar.
- **Görev önceliği:** Sağa veya sola kaydırarak önceliği değiştir.
- **Alışkanlıklar:** Günlük tekrarları ve tamamlanan günleri takip et.
- **Fikir ve alışveriş listeleri:** Ayrı bölümlerde kayıt tut.
- **İstatistikler:** Uyku ve hedef ilerlemesini incele.
- **İsteğe bağlı bağlantılar:** Go/Wails masaüstü uygulamasıyla yerel ağda eşitleme ve yerel MCP köprüsü.

### Kurulum

Projeyi Android Studio ile açıp aşağıdaki komutla APK oluşturabilirsin:

```powershell
.\gradlew.bat :app:assembleOptimized
```

Çıktı: `app/build/outputs/apk/optimized/app-optimized.apk` (debug anahtarıyla imzalanır). Masaüstü kodu `desktop-wails/`, isteğe bağlı MCP köprüsü `mcp-bridge/` klasöründedir.

### Gizlilik

Üyelik, reklam, takip sistemi veya zorunlu bulut sunucusu yoktur. Veriler cihazda saklanır. Yerel ağ eşitlemesi ve MCP erişimi isteğe bağlıdır. Yayımlanan ekran görüntülerinden kişisel bilgiler ve metadata kaldırılmıştır.

## English

**Daily Tracker** is an offline-first Android app for managing sleep, tasks, habits, ideas and shopping lists in one place.

### Features

- **Sleep tracking:** Start/stop with one tap; view sleep duration and history.
- **Agenda:** Tasks, subtasks, notes, goals and reminders.
- **Task priority:** Swipe left or right to change priority.
- **Habits:** Track daily repetitions and completed days.
- **Ideas and shopping:** Keep separate lists for each.
- **Statistics:** Review sleep summaries and goal progress.
- **Optional integrations:** Local-network sync with the Go/Wails desktop companion and a local MCP bridge.

### Build

Open the project in Android Studio, then build the APK with:

```powershell
.\gradlew.bat :app:assembleOptimized
```

Output: `app/build/outputs/apk/optimized/app-optimized.apk` (signed with a debug key). Desktop sources are in `desktop-wails/`; the optional MCP bridge is in `mcp-bridge/`.

### Privacy

No account, ads, analytics or mandatory cloud server. Data stays on your device. LAN sync and MCP access are opt-in. Published screenshots have been reviewed to remove personal information and metadata.

---

**Teknolojiler / Stack:** Kotlin, Jetpack Compose, Material 3, Room, Go, Wails.

**Durum / Status:** Aktif geliştirme aşamasında / Under active development.
