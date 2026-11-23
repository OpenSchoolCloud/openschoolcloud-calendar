# Release v2.1.0 — opruimen + Play/F-Droid Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** PR #1 gemerged, inloggen optioneel, app opgeruimd, ondertekende AAB + F-Droid-metadata klaar voor de Play closed test (kritiek pad: 12 testers × 14 dagen).

**Architecture:** Alles op branch `feature/osc-onboarding-identity-cloud`; pas aan het eind merge naar `main` + tag `v2.1.0`. Geen nieuwe dependencies. Signing alleen als `keystore.properties` bestaat, anders unsigned (F-Droid signt zelf).

**Tech Stack:** Kotlin 1.9.21, Compose BOM 2024.12.01, Hilt, Room, AGP 8.13.2, Gradle 8.13. Website: Next.js (`/mnt/nvme1tb/projects/openschoolcloud-frontend-website`).

**Spec:** `docs/superpowers/specs/2026-11-18-release-v2-cleanup-design.md`

Werkmap app: `/mnt/nvme1tb/projects/openschoolcloud-calendar/android`. Afkorting `PKG` = `app/src/main/java/nl/openschoolcloud/calendar`, `TPKG` = `app/src/test/java/nl/openschoolcloud/calendar`.

## Global Constraints

- AGPL-header op elke `.kt` (ook tests); kopieer de header van een bestaand bestand.
- Geen em-dashes in NL user-facing tekst. Strings alleen in `app/src/main/res/values/strings.xml`.
- `ENABLE_OSC_CLOUD` blijft `false`.
- Geen nieuwe dependencies. Geen signing-sleutel/wachtwoord in git.
- Nooit force-pushen. Origin's commit `857ca5a` moet na Task 1 in de historie zitten.
- Na elke task groen: `./gradlew testDebugUnitTest assembleDebug lintDebug`.
- Versie: `versionCode = 14`, `versionName = "2.1.0"`.
- Feedback-adres: `feedback@openschoolcloud.nl` (bestaat). Verwijder-URL: `https://openschoolcloud.nl/account-verwijderen`. Privacy-URL: `https://openschoolcloud.nl/privacy`.

## Review Focus

1. Nicks toestel is al ingelogd met een gezin: na update start de app direct op de kalender (geen Welcome) — test in Task 2.
2. Gebruiker kiest "Zonder account verder", sluit de app tijdens FamilySetup en opent opnieuw: hij komt op FamilySetup, niet opnieuw op Welcome — test in Task 2.
3. Uitloggen/account verwijderen terwijl er geen OSC-login is (alleen Nextcloud): de verwijder-knop is onzichtbaar en Nextcloud-accounts blijven staan — test in Task 3.
4. Release-build met R8 crasht niet bij Welcome/OIDC en CalDAV-sync (ProGuard-regels): handmatige rooktest op de release-APK in Task 5.
5. Schermen zonder `Scaffold` (Welcome, RecoveryCodes, FamilySetup, SyncChoice, Splash, Login) vallen niet onder statusbalk/navigatiebalk op targetSdk 36 — screenshot-check in Task 4.

---

### Task 1: Origin/main mergen in de feature-branch

**Files:**
- Modify (conflict): `PKG/presentation/screens/calendar/CalendarViewModel.kt`
- Auto-merge (controleren): `PKG/data/remote/caldav/CalDavClient.kt`, `PKG/data/repository/AccountRepositoryImpl.kt`, `PKG/data/repository/CalendarRepositoryImpl.kt`

**Interfaces:** geen nieuwe.

- [ ] **Step 1:** `git fetch origin && git merge origin/main` op de feature-branch. Verwacht: 1 conflict in `CalendarViewModel.kt`.
- [ ] **Step 2:** Los op door beide te behouden: origin's auto-`syncAll()` in `init{}` alleen als niet standalone, plus de branch-logica (profiel-filter/OSC-degrade). Bij overlap wint de branch-structuur, origin's gedrag wordt erin opgenomen.
- [ ] **Step 3:** Controleer in de auto-gemergde repositories dat nieuw ontdekte kalenders `ctag = null, syncToken = null` krijgen (origin's fix) en dat de CalDAV-username-401-fix van de branch er nog in zit.
- [ ] **Step 4:** Run `./gradlew testDebugUnitTest assembleDebug`. Verwacht: BUILD SUCCESSFUL, alle tests groen.
- [ ] **Step 5:** Commit (merge-commit) en `git push origin feature/osc-onboarding-identity-cloud`. Check `gh pr view 1 --json mergeable` → `MERGEABLE`.

### Task 2: Inloggen optioneel

**Files:**
- Modify: `PKG/presentation/navigation/NavGraph.kt` (`decideStartRoute`, Welcome- en Settings-composables)
- Modify: `PKG/presentation/screens/splash/SplashViewModel.kt`
- Modify: `PKG/data/local/AppPreferences.kt` (nieuw veld)
- Modify: `PKG/presentation/screens/welcome/WelcomeScreen.kt`, `WelcomeViewModel.kt`
- Modify: `PKG/presentation/screens/settings/SettingsScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Test: `TPKG/presentation/navigation/StartDestinationTest.kt`

**Interfaces:**
- Produces: `fun decideStartRoute(isLoggedIn: Boolean, welcomeDone: Boolean, familyExists: Boolean, syncChosen: Boolean): String`
- Produces: `AppPreferences.hasPassedWelcome: Boolean` (default `false`)
- Produces: `WelcomeScreen(onAuthenticated: () -> Unit, onContinueWithoutAccount: () -> Unit)`

- [ ] **Step 1: Pas de tests aan (failing)** in `StartDestinationTest.kt`; bestaande 4 krijgen `welcomeDone = false`, plus:

```kotlin
@Test fun `skipped welcome without family goes to family setup`() =
    assertEquals(Route.FamilySetup.route, decideStartRoute(isLoggedIn = false, welcomeDone = true, familyExists = false, syncChosen = false))

@Test fun `skipped welcome fully set up goes to calendar`() =
    assertEquals(Route.Calendar.route, decideStartRoute(isLoggedIn = false, welcomeDone = true, familyExists = true, syncChosen = true))

@Test fun `logged in user ignores welcome flag`() =
    assertEquals(Route.Calendar.route, decideStartRoute(isLoggedIn = true, welcomeDone = false, familyExists = true, syncChosen = true))
```

- [ ] **Step 2:** Run `./gradlew testDebugUnitTest --tests '*StartDestinationTest'` → FAIL (compileerfout, signatuur).
- [ ] **Step 3:** Implementeer: eerste tak wordt `!isLoggedIn && !welcomeDone -> Route.Welcome.route`. `SplashViewModel` geeft `appPreferences.hasPassedWelcome` door. Voeg `hasPassedWelcome` toe aan `AppPreferences` in dezelfde stijl als `hasCompletedModeSelection`.
- [ ] **Step 4:** Run dezelfde test → PASS.
- [ ] **Step 5:** Welcome: derde knop `TextButton` onder "Inloggen" met string `welcome_continue_without_account` = `Zonder account verder`, plus onder de knoppen een kleine regel `welcome_account_optional` = `Een account is niet nodig. Je kunt later altijd inloggen via Instellingen.` Klik → `WelcomeViewModel.continueWithoutAccount()` zet `hasPassedWelcome = true`, daarna navigeert NavGraph naar `Route.FamilySetup` met `popUpTo(Route.Welcome) { inclusive = true }`.
- [ ] **Step 6:** Settings: als `!authManager.isLoggedIn` een item `settings_osc_login` = `Inloggen met OpenSchoolCloud` → navigeert naar `Route.Welcome`. Na succesvolle login vanuit Settings terug naar Settings (niet naar RecoveryCodes-flow als er al een gezin is; RecoveryCodes alleen bij nieuw account, bestaand gedrag).
- [ ] **Step 7:** Run `./gradlew testDebugUnitTest assembleDebug lintDebug` → groen. Handmatig op emulator: verse installatie → Zonder account verder → FamilySetup → SyncChoice Lokaal → kalender; app herstarten → kalender.
- [ ] **Step 8:** Commit `feat: OSC-account optioneel bij start (Zonder account verder)`.

### Task 3: Account verwijderen + "Mis je iets?"

**Files:**
- Modify: `PKG/presentation/screens/settings/SettingsScreen.kt`, `SettingsViewModel.kt`
- Modify: `PKG/presentation/screens/calendar/CalendarScreen.kt` (alleen label via string)
- Modify: `PKG/presentation/navigation/NavGraph.kt` (Settings → Feedback)
- Modify: `app/src/main/res/values/strings.xml`
- Test: `TPKG/presentation/settings/SettingsAccountDeletionTest.kt` (nieuw)

**Interfaces:**
- Consumes: `AuthManager.isLoggedIn`, `AuthManager.logout()` (bestaand)
- Produces: `SettingsViewModel.deleteOscAccount(): String` — logt OSC-identiteit uit (wist OIDC-tokens, raakt Nextcloud-accounts NIET) en geeft de verwijder-URL terug die het scherm opent met `Intent.ACTION_VIEW`.
- Produces: `SettingsUiState.isOscLoggedIn: Boolean`
- Produces: const `ACCOUNT_DELETE_URL = "https://openschoolcloud.nl/account-verwijderen"` in `SettingsViewModel.kt` (companion).

- [ ] **Step 1: Failing test** (fake/mock `AuthManager` zoals in `AuthManagerStateTest`/bestaande VM-tests):

```kotlin
@Test fun `delete osc account logs out and returns delete url`() {
    val url = viewModel.deleteOscAccount()
    assertEquals("https://openschoolcloud.nl/account-verwijderen", url)
    verify { authManager.logout() }
    verify(exactly = 0) { accountRepository.removeAccount(any()) }
}

@Test fun `not logged in hides deletion`() {
    every { authManager.isLoggedIn } returns false
    assertFalse(viewModel.uiState.value.isOscLoggedIn)
}
```

- [ ] **Step 2:** Run → FAIL.
- [ ] **Step 3:** Implementeer `deleteOscAccount()` en `isOscLoggedIn`. Scherm: item `settings_delete_osc_account` = `OpenSchoolCloud-account verwijderen`, alleen zichtbaar bij `isOscLoggedIn`, met bevestigingsdialoog (Compose `AlertDialog`, geen browserdialoog): titel `delete_account_title` = `Account verwijderen?`, tekst `delete_account_body` = `Je wordt uitgelogd en we openen de pagina waar je je OpenSchoolCloud-account definitief verwijdert. Je agenda op dit toestel blijft staan.`, knoppen `delete_account_confirm` = `Verwijderen`, `delete_account_cancel` = `Annuleren`.
- [ ] **Step 4:** Run → PASS.
- [ ] **Step 5:** Feedback: wijzig `menu_feedback` naar `Mis je iets? Geef het door` en voeg in Settings een item met dezelfde string toe dat naar `Route.Feedback` navigeert.
- [ ] **Step 6:** `./gradlew testDebugUnitTest assembleDebug lintDebug` → groen. Commit `feat: OSC-account verwijderen + Mis je iets? in menu en Instellingen`.

### Task 4: Opruimen + targetSdk 36

**Files:**
- Delete (na verificatie dat ze nergens gerouteerd/gebruikt worden): `PKG/presentation/screens/onboarding/OnboardingScreen.kt` (+ VM als aanwezig), `PKG/presentation/screens/modeselection/ModeSelectionScreen.kt`, `ModeSelectionViewModel.kt`; `Route.QrCode` als er geen `composable` voor bestaat; strings die daardoor ongebruikt raken.
- Rename: `OscCloudProvider` / `OscCloudSyncProvider` → één duidelijke naam per rol (kies op basis van inhoud; houd testnamen in lijn).
- Modify: `app/proguard-rules.pro`, `app/build.gradle.kts`
- Modify: schermen zonder `Scaffold` uit Review Focus 5 (alleen insets).

**Interfaces:** geen nieuwe publieke; rename moet in alle call-sites en tests.

- [ ] **Step 1:** `grep` per kandidaat bevestigt "geen referenties buiten zichzelf"; verwijder. `hasCompletedModeSelection` blijft (wordt door SyncChoice gebruikt — verifieer).
- [ ] **Step 2:** Rename uitvoeren. Run `./gradlew testDebugUnitTest` → groen.
- [ ] **Step 3:** Release-logging strippen met één regel in `proguard-rules.pro`:

```
-assumenosideeffects class android.util.Log { public static int v(...); public static int d(...); public static int i(...); }
```

- [ ] **Step 4:** `compileSdk = 36`, `targetSdk = 36`, `versionCode = 14`, `versionName = "2.1.0"`. (Check eerst de actuele Play-eis op support.google.com/googleplay/android-developer/answer/11926878; als die 35 is, toch 36 nemen: AGP 8.13 ondersteunt het.)
- [ ] **Step 5:** `./gradlew lintDebug`: alle errors en de warnings `UnusedResources`, `UnusedImport`/`unused` en `ObsoleteSdkInt` oplossen. Overige warnings mogen blijven, maar noteer het aantal voor/na in de commit-message.
- [ ] **Step 6:** Insets: elk scherm uit Review Focus 5 krijgt op zijn root `Modifier.safeDrawingPadding()` (of `windowInsetsPadding(WindowInsets.safeDrawing)`) als het nu tekst onder de systeembalken heeft. Check op emulator API 36 met screenshots van elk scherm.
- [ ] **Step 7:** `./gradlew testDebugUnitTest assembleDebug lintDebug assembleRelease` → groen. Commit `chore: opruimen dode onboarding-schermen, naamgeving sync-provider, targetSdk 36, release-logging strippen`.

### Task 5: Signing + release-build

**Files:**
- Modify: `app/build.gradle.kts` (signingConfig), `.gitignore`
- Create (buiten repo): `~/.config/osc-signing/upload.jks`, `~/.config/osc-signing/keystore.properties` (chmod 600)

**Interfaces:**
- Produces: `keystore.properties`-sleutels `storeFile`, `storePassword`, `keyAlias`, `keyPassword`; pad via Gradle-property `oscKeystoreProps` of default `~/.config/osc-signing/keystore.properties`.

- [ ] **Step 1:** Maak de upload-sleutel: `keytool -genkeypair -v -keystore ~/.config/osc-signing/upload.jks -alias osc-upload -keyalg RSA -keysize 4096 -validity 10000 -dname "CN=OpenSchoolCloud, O=Aldewereld Consultancy, C=NL"` met een willekeurig wachtwoord (`openssl rand -base64 24`), weggeschreven in `keystore.properties`. Meld Nick waar het staat en dat hij er een back-up van moet maken (verlies = geen updates).
- [ ] **Step 2:** In `build.gradle.kts`: als het properties-bestand bestaat → `signingConfigs.create("release")` en `release { signingConfig = ... }`; anders ongewijzigd (unsigned). Voeg `keystore.properties` en `*.jks` toe aan `.gitignore`.
- [ ] **Step 3:** Run `./gradlew bundleRelease assembleRelease`. Verwacht: `app/build/outputs/bundle/release/app-release.aab` en een signed `app-release.apk`; `apksigner verify --print-certs` toont CN=OpenSchoolCloud.
- [ ] **Step 4: Rooktest release-APK** (Review Focus 4) op emulator: start, Zonder account verder, FamilySetup, Lokaal, afspraak maken; daarna Welcome → Inloggen opent Custom Tab naar id.openschoolcloud.nl; Nextcloud verbinden + sync. Geen crash in `adb logcat`.
- [ ] **Step 5:** Commit `build: release signing via extern keystore.properties`.

### Task 6: Store-, privacy- en F-Droid-materiaal

**Files:**
- Move: `metadata/{nl-NL,en-US}/*` → `fastlane/metadata/android/{nl-NL,en-US}/` (git mv)
- Create: `fastlane/metadata/android/{nl-NL,en-US}/changelogs/14.txt`, `.../title.txt`, `.../images/phoneScreenshots/1..4.png`, `.../images/icon.png` (512×512)
- Create: `docs/release/play-data-safety.md`, `docs/release/play-console-checklist.md`, `docs/release/fdroid/nl.openschoolcloud.calendar.yml`
- Website-repo: `src/app/privacy/page.tsx` (of bestaande privacypagina bijwerken) en `src/app/account-verwijderen/page.tsx` — volg de bestaande paginastructuur daar.

- [ ] **Step 1:** Metadata verplaatsen; voeg in beide full_descriptions een regel toe dat Play- en F-Droid-versies niet over elkaar heen updaten. Changelog 14 (NL): starten zonder account, inloggen optioneel, account verwijderen, Mis je iets?, opgeruimd en sneller.
- [ ] **Step 2:** Screenshots van de release-build op emulator (kalender-week, dag, weekplanning, nieuwe afspraak), 1080×1920 of 1080×2400.
- [ ] **Step 3:** `play-data-safety.md`: antwoorden per Play-vraag. Feiten: geen analytics/ads/tracking; optioneel account (e-mail, naam) bij id.openschoolcloud.nl (EU-VPS) via OIDC; agendadata lokaal of op eigen Nextcloud, niet door ons verzameld; versleuteld in transit; verwijderen via de verwijder-URL.
- [ ] **Step 4:** Privacy- en verwijderpagina op de website (NL), inhoud consistent met Step 3; verwijderpagina: mail naar privacy@openschoolcloud.nl vanaf het accountadres, verwerking binnen 30 dagen, wat er verwijderd wordt (Authentik-account) en wat niet (lokale data op het toestel, eigen Nextcloud). Lokaal `npm run build` groen. Deploy laten volgen de deploy-route van die repo (lees zijn README/CLAUDE.md; bij twijfel Nick vragen).
- [ ] **Step 5:** `fdroid/nl.openschoolcloud.calendar.yml`: Categories `Time`, License `AGPL-3.0-or-later`, SourceCode/IssueTracker = GitHub-repo, Builds-entry `versionName: 2.1.0`, `versionCode: 14`, `commit: v2.1.0`, `subdir: android/app`, `gradle: [yes]`, `AutoUpdateMode: Version`, `UpdateCheckMode: Tags`. Valideer offline met `fdroid lint` als `fdroidserver` beschikbaar is (anders overslaan en melden).
- [ ] **Step 6:** `play-console-checklist.md`: stappen voor Nick in volgorde (app aanmaken `nl.openschoolcloud.calendar`, Play App Signing aan, upload AAB naar closed test, 12+ testers via e-maillijst/Google-groep, data-safety invullen, privacy-URL, content rating, doelgroep: niet primair kinderen jonger dan 13 — ouderapp; check met Nick). Commit `docs: store- en F-Droid-materiaal v2.1.0`.

### Task 7: Merge naar main + tag

- [ ] **Step 1:** Volledige check op de branch: `./gradlew clean testDebugUnitTest lintDebug assembleDebug bundleRelease` → groen.
- [ ] **Step 2:** Update `AGENTS.md` (stand per release) en `CHANGELOG.md` (2.1.0). Commit, push, `gh pr merge 1 --merge` (geen squash: historie behouden).
- [ ] **Step 3:** `git checkout main && git pull`, `git tag -a v2.1.0 -m "v2.1.0"`, `git push origin v2.1.0`. Verifieer dat de tag-commit `857ca5a` als voorouder heeft: `git merge-base --is-ancestor 857ca5a v2.1.0`.
- [ ] **Step 4:** Rapport aan Nick: pad naar AAB, keystore-locatie (+ back-up-advies), checklist Play, F-Droid-yml klaar voor zijn merge request op gitlab.com/fdroid/fdroiddata.
