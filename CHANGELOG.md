# Changelog — OpenSchoolCloud Calendar

Alle noemenswaardige wijzigingen aan dit project worden gedocumenteerd in dit bestand.

Het formaat is gebaseerd op [Keep a Changelog](https://keepachangelog.com/nl/1.0.0/),
en dit project volgt [Semantic Versioning](https://semver.org/lang/nl/).

---

## [1.1.0] - 2026-02-16

### Sprint 5: Multiculturele Feestdagenkalender

#### Toegevoegd
- **Feestdagenkalenders**: 7 subscribable kalenders met culturele feestdagen
  - Nederlandse feestdagen (Koningsdag, Bevrijdingsdag, Sinterklaas, etc.)
  - Islamitische feestdagen (Suikerfeest, Offerfeest, Ramadan, Mawlid)
  - Christelijke feestdagen (Pasen, Kerst, Hemelvaart, Pinksteren)
  - Joodse feestdagen (Pesach, Jom Kippur, Chanoeka, Rosh Hashana)
  - Hindoestaanse feestdagen (Diwali, Holi/Phagwa, Navratri)
  - Chinese/Aziatische feestdagen (Chinees Nieuwjaar, Maanfestival)
  - Internationale dagen (Kinderrechtendag, Werelddocentendag, etc.)
- **Gekleurde stippen** op de weekweergave per feestdagencategorie
- **Feestdagen-chips** in het dagdetailpaneel boven reguliere afspraken
- **Detail bottom sheet**: Kindvriendelijke uitleg per feestdag met gespreksstarter voor de klas
- **Ontdekscherm**: Per-categorie aan/uit schakelaar in Instellingen > Feestdagen
- **Privacy**: Alle feestdagenkeuzes zijn lokaal opgeslagen, nooit gesynchroniseerd

#### Technisch
- Room database migratie v1 naar v2: `holiday_calendars` + `holiday_events` tabellen
- JSON seed data in assets voor 48+ feestdagen met datums 2025-2030
- Anonymous Gregorian Easter algorithm voor christelijke feestdagen
- Precomputed dates voor islamitische, joodse, hindoestaanse en Chinese kalenders
- `HolidayRepository` met automatische seeding bij eerste start
- Moshi JSON parsing voor seed data
- Versie: 1.1.0 (versionCode 3)

---

## [1.0.0] - 2026-02-16

### Sprint 4: Play Store Ready

#### Toegevoegd
- **Branded splash screen**: Fade-animatie met OSC-logo en tagline
- **Onboarding**: 3 slides voor nieuwe gebruikers (privacy, CalDAV, features)
- **ACRA crashrapportage**: Privacy-first crashrapportage via e-mail (geen Firebase/Google)
- **Promo card**: Subtiele kaart in Instellingen voor OpenSchoolCloud.nl hosting (wegklikbaar)
- **Play Store metadata**: Beschrijvingen in Nederlands en Engels (Fastlane/Triple-T conventie)

#### Technisch
- ACRA `acra-mail` + `acra-dialog` (v5.11.4): gebruiker kiest zelf of crashrapport wordt verstuurd
- ProGuard-regels uitgebreid: ACRA, Moshi, ZXing, WorkManager, SourceFile/LineNumberTable
- `AppPreferences`: `onboardingCompleted` en `promoDismissed` vlaggen
- `compose-foundation` expliciet opgenomen voor HorizontalPager
- Navigatie: Splash -> Onboarding -> Login -> Calendar flow
- Versie: 1.0.0 (versionCode 2)

---

## [0.1.0-alpha] - 2025-01-18

### Sprint 2.5: Huisstijl & Build Fix

#### Toegevoegd (Android)
- **Gradle Wrapper**: Gradle 8.5 wrapper gegenereerd voor reproduceerbare builds
- **Brand Colors**: OSC huisstijl geimplementeerd volgens Huisstijlgids v1.0
  - OSC Blauw (#3B9FD9) als primary color
  - Donker Blauw (#2B7FB9) voor headings
  - Licht Blauw (#E8F4FB) voor achtergronden
  - Success/Error/Warning kleuren
- **Typography**: Open Sans font family setup met Material3 type scale
- **Nederlandse UI**: 250+ strings vertaald naar Nederlands
  - Educatie-vriendelijke terminologie ("afspraken" ipv "events")
  - Tagline: "Jullie school, jullie data. In Nederland."
- **Adaptive Icons**: Cloud-shaped launcher icons voor Android 8+
- **Splash Screen**: SplashScreen API met OSC Blauw achtergrond

#### Technisch
- `androidx.core:core-splashscreen:1.0.1` toegevoegd
- Material3 light/dark color schemes
- .gitignore voor Android project

---

## [0.0.2-alpha] - 2025-01-17

### Sprint 2: Core Calendar

#### Toegevoegd (Android)
- **Room Database**: Complete schema voor events, calendars, accounts
  - `EventEntity`, `CalendarEntity`, `AccountEntity`
  - DAOs met CRUD operaties
  - Type converters voor LocalDateTime, ZonedDateTime
- **CalDAV Sync Engine**: CTag-based differential sync
  - `SyncManager` voor sync orchestratie
  - `CalendarSyncWorker` voor background sync (WorkManager)
  - 15-minuten sync interval
- **Calendar Views**:
  - Week view met 7-kolommen grid
  - Day view met uur-voor-uur weergave
  - Month view (basis grid met navigatie)
  - Pull-to-refresh voor handmatige sync
- **Calendar Selection**: Selecteer welke calendars zichtbaar zijn

#### Technisch
- WorkManager integratie met Hilt
- `BootReceiver` voor sync na herstart
- Room database versie 1

---

## [0.0.1-alpha] - 2025-01-15

### Sprint 1: Foundation

#### Toegevoegd (Android)
- **Project Setup**:
  - Kotlin 1.9 met Jetpack Compose
  - Hilt voor dependency injection
  - Clean Architecture (data/domain/presentation)
- **CalDAV Discovery**:
  - Well-known endpoint detection (`/.well-known/caldav`)
  - Principal URL discovery via PROPFIND
  - Calendar home set discovery
  - Calendar collection listing
- **CalDAV XML Parser**: RFC 4791 compliant XML parsing
- **iCalendar Parser**: ical4j integratie voor event parsing
- **Account Storage**: EncryptedSharedPreferences voor credentials
- **Login UI**: 3-velden onboarding (URL, username, app-wachtwoord)
- **Navigation**: Jetpack Navigation Compose met routes

#### Technisch
- MinSdk 26 (Android 8.0)
- TargetSdk 34 (Android 14)
- OkHttp 4.12 voor HTTP
- Moshi voor JSON serialization

#### Tests
- `CalDavXmlParserTest`: 25+ unit tests
- `ICalParserTest`: Event parsing tests
- `JsonSerializerTest`: Serialization tests
- ~73 unit tests totaal

---

## Nog Te Doen

### Sprint 6: Leerling-Agenda & Reflectie
- [ ] Leeragenda-modus bij afspraken aanmaken
- [ ] Reflectie-prompt na afloop van afspraken
- [ ] Weekoverzicht met stemmingen

---

## Legenda

- **Toegevoegd**: Nieuwe features
- **Gewijzigd**: Wijzigingen in bestaande functionaliteit
- **Verwijderd**: Verwijderde features
- **Opgelost**: Bug fixes
- **Beveiligd**: Security gerelateerde wijzigingen
- **Technisch**: Interne wijzigingen zonder gebruikersimpact
