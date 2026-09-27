# Changelog — OpenSchoolCloud Calendar

Alle noemenswaardige wijzigingen aan dit project worden gedocumenteerd in dit bestand.

Het formaat is gebaseerd op [Keep a Changelog](https://keepachangelog.com/nl/1.0.0/),
en dit project volgt [Semantic Versioning](https://semver.org/lang/nl/).

---

## [2.0.0] - 2026-09-27

### Plan A: Identity Layer, Gezinsmodel & Gated OSC-Sync

#### Toegevoegd
- **Account-first onboarding**: Nieuw onboarding-traject via Authentik OIDC (AppAuth)
  - Inloggen op schoolaccount of Nextcloud (AppAuth Custom Tab)
  - Herstelopties: herstelbewijs en recovery-codes
  - Account-status in Splash screen bij app-herstart
- **Verplichte MFA**: MFA-afwending in de IdP-flow (Authentik)
  - MFA-verificatie verplicht voor account-eers onboarding
  - Recovery-codes centraal beheerd in Authentik
- **Gezinsmodel met profiel-switcher**: Multi-profiel ondersteuning voor ouders/verzorgers
  - Profiel-switcher in NavigationRail en TopAppBar
  - Profiel aanmaken: naam + kleur
  - Per-profiel agenda en instellingen
  - Kindvriendelijke identiteit per profiel
- **Sync-keuze met feature flag**: Lokale sync vs. betaalde OSC Cloud
  - Sync-keuzescherm in onboarding (LOCAL, OWN_NEXTCLOUD, OSC_CLOUD)
  - ENABLE_OSC_CLOUD buildConfigField voor gefaseerde uitrol
  - Local-first default (SyncScope.LOCAL)
  - Geflagde OSC-sync-kaart in instellingen

#### Technisch
- AppAuth integratie (OpenID Connect) via `net.openid:appauth:0.11.1`
- AuthManager + TokenStore met EncryptedSharedPreferences
- FamilyProfile entity en ProfileRepository (Room)
- SyncScope enum (LOCAL, OWN_NEXTCLOUD, OSC_CLOUD)
- BuildConfig OIDC configuratie (issuer, client_id, redirect_scheme)
- Adaptive splash-route beslissing: `decideStartRoute(isLoggedIn, familyExists, syncChosen)`
- Profiel-switcher composable in NavigationRail/TopAppBar
- Recovery-code gate voor account-eers flow
- Versie: 2.0.0 (versionCode 13)

---

## [1.4.0] - 2026-02-17

### Sprint 8: UX Polish, Feedback & Theme

#### Toegevoegd
- **Overflow menu**: TopAppBar opgeruimd met DropdownMenu
  - Alleen "Vandaag" als primaire actie zichtbaar
  - Synchroniseren, Weekvoortgang, Weekoverzicht, Afspraak-links, Feedback en Instellingen in overflow (⋮)
  - Sync-indicator inline zichtbaar tijdens synchronisatie
- **Feedback systeem**: In-app feedback via e-mail
  - Categorieselectie: Bug, Idee/Verzoek, Gebruikservaring, Overig
  - Gestructureerd e-mailformat via Android ACTION_SENDTO intent
  - Privacy-vriendelijk: alleen door gebruiker ingevulde tekst wordt verstuurd
- **Dark mode verbeterd**: Blauw-getint donker thema
  - Achtergronden #1A1A2E en #252540 (was grijs #121212)
  - Alle hardcoded kleuren vervangen door MaterialTheme tokens
  - ThemeMode parameter (systeem/licht/donker) doorverbonden vanuit voorkeuren

#### Technisch
- `CalendarTopBar` refactor: 6 IconButtons → 1 DropdownMenu
- `OpenSchoolCloudCalendarTheme` accepteert `themeMode: String` i.p.v. `darkTheme: Boolean`
- `MainActivity` leest `appPreferences.themeMode` en geeft door aan theme
- Hardcoded Color() waarden verwijderd uit SplashScreen, OnboardingScreen, EventDetailScreen, SettingsScreen, HolidayDetailSheet
- `FeedbackScreen` + `FeedbackViewModel` met `@HiltViewModel`
- `Route.Feedback` in NavGraph
- Versie: 1.4.0 (versionCode 6)

---

## [1.3.0] - 2026-02-17

### Sprint 7: Week Vooruit Planner

#### Toegevoegd
- **Weekplanning**: Begeleide 3-stappen planning voor leerlingen
  - Stap 1: Taken toevoegen (maximaal 10, minimaal 1)
  - Stap 2: Taken toewijzen aan dagen via dagchips
  - Stap 3: Bevestigen en opslaan
- **Taken in agenda**: Taken verschijnen als licht-gekleurde chips met checkbox
  - Tertiary kleur met lage alpha voor visueel onderscheid van reguliere afspraken
  - Doorgestreepte titel bij afgeronde taken
  - Tik op taak om af te vinken/ongedaan te maken
- **Weekvoortgang**: Voortgangsscherm met circulaire voortgangsindicator
  - Toon voltooid/totaal taakoverzicht
  - Weeknavigatie (vorige/volgende)
  - Takenlijst met checkboxes
  - Knop naar weekplanning
- **Planningsstreak**: Bijhouden van opeenvolgende weken plannen
  - "Je plant al X weken op rij!" motivatiebericht
- **Planningsherinnering**: Wekelijkse notificatie op configureerbare dag/tijd
  - Apart notificatiekanaal (standaard prioriteit)
  - WorkManager-gebaseerd (dagelijkse controle)
- **Instellingen**: Weekplanning-sectie met:
  - Planningsherinneringen aan/uit
  - Configureerbare planningsdag

#### Technisch
- Room database migratie v3 naar v4: `eventType` en `taskCompleted` kolommen op `events`
- `eventType` als String ("STANDARD" / "TASK") voor CalDAV X-OSC-EVENT-TYPE compatibiliteit
- Taken als all-day Events: hergebruik bestaande CRUD en sync-infrastructuur
- Task-specifieke DAO queries: `getTasksInRange`, `getCompletedTaskCountInRange`, `getTaskCountInRange`
- `toggleTaskCompleted` in EventRepository met automatische `syncStatus = PENDING_UPDATE`
- `PlanningReminderWorker` met dagelijkse PeriodicWorkRequest
- Planning voorkeuren in AppPreferences: dag, tijd, streak, lastWeekYear
- Streak-tracking via `year * 100 + weekNumber` encoding
- Privacy: Taken synchroniseren als reguliere CalDAV all-day events
- Versie: 1.3.0 (versionCode 5)

---

## [1.2.0] - 2026-02-16

### Sprint 6: Leerling-Agenda & Reflectie

#### Toegevoegd
- **Leeragenda-modus**: Optionele toggle bij het aanmaken/bewerken van afspraken
  - Gestructureerde velden: "Wat ga ik doen?" en "Wat heb ik nodig?"
  - Automatische beschrijving voor CalDAV-compatibiliteit
- **Reflectie na afloop**: Lichte reflectie-prompt wanneer een leeragenda-afspraak eindigt
  - Stemming kiezen (1-5): 😫 Moeilijk, 😕 Niet zo goed, 😐 Oké, 🙂 Goed, 🤩 Super!
  - Optionele tekstvelden: "Wat ging goed?" en "Wat kan beter?"
  - Bottom sheet vanuit afspraakdetails
- **Weekoverzicht**: Visueel overzicht van reflecties per week
  - 7-kolommen tijdlijn met stemmingsemoji's per dag
  - Samenvattingskaart met gemiddelde stemming en aanmoediging
  - Week-navigatie (vorige/volgende week)
- **Reflectienotificaties**: Zachte herinnering wanneer een leeragenda-afspraak eindigt
  - Apart notificatiekanaal (standaard prioriteit)
  - Instelbaar via Instellingen > Leeragenda & Reflectie
- **Leeragenda-weergave in afspraakdetails**: Badge, leerdoel en leerbehoeften kaarten
- **Reflectiesamenvatting**: Stemming en teksten zichtbaar in afspraakdetails na invullen

#### Technisch
- Room database migratie v2 naar v3: `reflection_entries` tabel + 3 kolommen op `events`
- `ReflectionEntity` met FK naar `events(uid)` CASCADE + indices
- `ReflectionRepository` en `ReflectionRepositoryImpl` met Flow-gebaseerde observatie
- `MoodSelector` herbruikbaar composable component
- `ReflectionPromptSheet` als ModalBottomSheet
- `WeekReviewViewModel` met combine() voor reflecties + leeragenda-afspraken
- Notificatiekanaal `reflections` met `IMPORTANCE_DEFAULT`
- AlarmManager-planning op `dtEnd` voor reflectieherinneringen
- Privacy: Alle reflectiedata lokaal opgeslagen, nooit gesynchroniseerd naar CalDAV
- Pedagogisch kader: Zimmerman's Self-Regulated Learning model
- Versie: 1.2.0 (versionCode 4)

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

### Sprint 9: Seizoenskaarten & Ontdek-Feed
- [ ] Contextuele ontdekkaarten per seizoen en schooljaar
- [ ] Natuur, schooljaar-momenten, wereldoriëntatie, fun facts
- [ ] Afwijsbaar, niet-storend in dagweergave

---

## Legenda

- **Toegevoegd**: Nieuwe features
- **Gewijzigd**: Wijzigingen in bestaande functionaliteit
- **Verwijderd**: Verwijderde features
- **Opgelost**: Bug fixes
- **Beveiligd**: Security gerelateerde wijzigingen
- **Technisch**: Interne wijzigingen zonder gebruikersimpact
