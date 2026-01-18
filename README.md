# OpenSchoolCloud Calendar

**De eenvoudige agenda-app voor scholen, bovenop Nextcloud.**

🇳🇱 🇧🇪 🇩🇪 🇫🇷 — *Gebouwd voor Europees onderwijs*

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Android](https://img.shields.io/badge/Platform-Android-green.svg)](#android)
[![iOS](https://img.shields.io/badge/Platform-iOS-lightgrey.svg)](#ios)

---

## Het Probleem

Scholen willen weg van Google Calendar, maar het alternatief (Nextcloud + DAVx⁵) is te technisch:

```
Huidige situatie:
1. Installeer Nextcloud app
2. Installeer DAVx⁵ (wat is dat?)
3. Configureer CalDAV URL
4. Maak app-wachtwoord aan
5. Synchroniseer met system calendar
6. Open een andere agenda-app

→ Resultaat: "Ik gebruik gewoon Google Calendar"
```

## De Oplossing

```
OpenSchoolCloud Calendar:
1. Installeer de app
2. Vul in: URL + gebruikersnaam + app-wachtwoord
3. Klaar — je agenda werkt
```

---

## Features

### MVP
- ✅ One-time onboarding (3 velden, 30 seconden)
- ✅ Dag/week/maand views
- ✅ Events aanmaken en bewerken
- ✅ Uitnodigingen versturen + updates bij wijzigingen
- ✅ Reminders
- ✅ Offline cache
- ✅ Kalenderkleur support

### v1
- ⬜ Meerdere accounts
- ⬜ Zoekfunctie
- ⬜ Herhalende afspraken (volledige edit)
- ⬜ Widgets (Android + iOS)
- ⬜ Contact autocomplete (device + CardDAV)

### v2
- ⬜ Natural language input
- ⬜ Free/busy scheduling assistant
- ⬜ 10-minutengesprekken integratie

Zie [SCOPE.md](SCOPE.md) voor de volledige specificatie.

---

## Platforms

### Android

**Stack:** Kotlin, Jetpack Compose, Room, WorkManager

**Minimum:** Android 8.0 (API 26)

```bash
cd android/
./gradlew assembleDebug
```

### iOS

**Stack:** Swift, SwiftUI, CoreData, BackgroundTasks

**Minimum:** iOS 15.0

```bash
cd ios/
open OpenSchoolCloudCalendar.xcodeproj
# Of via xcodebuild
```

---

## Development

### Prerequisites

**Android:**
- Android Studio Hedgehog (2023.1.1) of nieuwer
- JDK 17

**iOS:**
- Xcode 15+
- macOS Sonoma of nieuwer

### Getting Started

```bash
git clone https://github.com/NickAldewereld/openschoolcloud-calendar.git
cd openschoolcloud-calendar

# Android
cd android/
./gradlew build

# iOS
cd ios/
pod install  # indien CocoaPods dependencies
open OpenSchoolCloudCalendar.xcworkspace
```

### Project Structure

```
openschoolcloud-calendar/
├── android/                    # Android app (Kotlin/Compose)
│   ├── app/
│   │   └── src/main/
│   │       ├── java/nl/openschoolcloud/calendar/
│   │       └── res/
│   └── build.gradle.kts
│
├── ios/                        # iOS app (Swift/SwiftUI)
│   ├── OpenSchoolCloudCalendar/
│   │   ├── App/
│   │   ├── Features/
│   │   ├── Core/
│   │   └── Resources/
│   └── OpenSchoolCloudCalendar.xcodeproj
│
├── shared/                     # Shared documentation & specs
│   ├── caldav/                 # CalDAV protocol documentation
│   └── api/                    # API contracts (indien nodig)
│
├── docs/                       # Project documentation
│   ├── ARCHITECTURE.md
│   └── TESTING.md
│
├── SCOPE.md                    # Feature scope & roadmap
├── CONTRIBUTING.md             # Contribution guidelines
├── LICENSE                     # Apache 2.0
└── README.md
```

---

## CalDAV Implementation Notes

De app communiceert direct met Nextcloud via CalDAV. Geen tussenlaag, geen eigen backend.

**Discovery flow:**
1. User geeft server URL
2. App doet PROPFIND op `/.well-known/caldav` of `/remote.php/dav/`
3. Discover `current-user-principal`
4. Discover `calendar-home-set`
5. List calendars

**Sync strategy:**
- CTag-based differential sync
- Server is single source of truth
- Offline changes queued, sync on reconnect

Zie [shared/caldav/](shared/caldav/) voor protocol details.

---

## Privacy & Security

- **Geen analytics** — geen Firebase, geen tracking
- **Geen telemetrie** — tenzij opt-in
- **Credentials encrypted** — Android Keystore / iOS Keychain
- **Minimale permissies** — Internet, Notifications, Contacts (optioneel)
- **Open source** — audit zelf de code

---

## Contributing

Zie [CONTRIBUTING.md](CONTRIBUTING.md) voor guidelines.

**We zoeken:**
- Android developers (Kotlin/Compose)
- iOS developers (Swift/SwiftUI)
- CalDAV/iCalendar expertise
- Vertalers (DE, FR, ES)
- Testers

---

## License

Apache License 2.0 — zie [LICENSE](LICENSE)

```
Copyright 2025 OpenSchoolCloud / Aldewereld Consultancy
```

---

## Links

- **Website:** [openschoolcloud.nl](https://openschoolcloud.nl)
- **Issues:** [GitHub Issues](https://github.com/NickAldewereld/openschoolcloud-calendar/issues)
- **Contact:** info@openschoolcloud.nl

---

<p align="center">
  <strong>OpenSchoolCloud Calendar</strong><br>
  <em>Jullie school, jullie agenda. In Europa.</em>
</p>
