# Release v2 — opruimen + Play Store + F-Droid

Datum: 2026-11-18 · Besluit: Nick (in chat)

## Intent

De originele scope (Plans A/B/C, gezins-sync dark) opruimen, strak trekken en releasen op Play Store en F-Droid. Daarna feedback van echte gebruikers gebruiken om het product te verbeteren. Geen nieuwe functies vóór de release.

## Doelen

1. PR #1 (`feature/osc-onboarding-identity-cloud`) gemerged naar `main`, inclusief origin's commit `857ca5a` ("Empty CTag at discovery forces full sync on first run"). Nooit force-pushen.
2. Opruimen en tighten-up: dode code, ongebruikte resources/imports/dependencies, lint-waarschuwingen, de open Minors uit de reviews die app-kant zijn (o.a. naamgeving `OscCloudProvider` vs `OscCloudSyncProvider`), debug-logging die niet in release hoort (o.a. de PROPFIND-diagnostiek uit `857ca5a` achter `BuildConfig.DEBUG`).
3. Release-build die Play Store accepteert: ondertekende AAB, targetSdk op het huidige Play-minimum.
4. F-Droid-klaar: build uit broncode zonder proprietary deps, fastlane-metadata in de structuur die F-Droid leest, merge request naar fdroiddata voorbereid.

## Niet-doelen

- Huiswerk/taken leraar→kind (geparkeerd, zie AGENTS.md).
- Gezins-sync aanzetten (`ENABLE_OSC_CLOUD` blijft `false`) en de C-go-live-gates.
- iOS (eigen project na Android-feedback; `ios/` is een lege stub).
- Herontwerp van schermen of architectuur.

## Anti-doelen (falen ook als het technisch werkt)

- Gedrag veranderen dat gebruikers merken, behalve bugfixes.
- Nieuwe dependencies toevoegen.
- Een signing-sleutel of wachtwoord in git.
- Origin's werk verliezen bij de merge.

## Beperkingen

- AGPL-header op elke `.kt` (ook tests). Geen em-dashes in NL user-facing tekst.
- Unit-suite + `assembleDebug` + `assembleRelease`/`bundleRelease` + `lint` moeten groen zijn na elke stap.
- `sudo` kan niet via Claude; Nick draait die zelf.
- Play-account bestaat (Nick); store-console-acties (app aanmaken, data-safety-formulier, testers) doet Nick, Claude levert de teksten/bestanden.

## Wat besloten is

- Kanalen: Play Store + F-Droid. Apple later.
- Gezins-sync dark bij release.
- Refactor = opruimen/tighten, geen features.

- **Inloggen bij OpenSchoolCloud wordt optioneel** (lage drempel). App start zoals 1.x: lokaal of eigen Nextcloud direct bruikbaar; het OSC-account is een keuze (bv. "Inloggen met OpenSchoolCloud" op het welkomscherm + in Instellingen). Upsell van betaalde functies later is uitdrukkelijk niet uitgesloten, dus de OIDC-laag blijft intact, alleen niet verplicht. Gedragswijziging t.o.v. de huidige branch is hiermee goedgekeurd.
- Omdat de app accounts kan aanmaken: account-verwijderen-pad in de app (Instellingen) + een web-URL voor verwijderen (Play-eis). Werk hiervoor is goedgekeurd.
- **"Mis je iets? Geef het door"**: goed vindbare ingang (kalender-menu + Instellingen) naar het bestaande feedbackscherm (`screens/feedback`, mailto `feedback@openschoolcloud.nl`). Geen backend, geen tracking.
- Niemand buiten Nick heeft een eerdere build: nieuwe upload-sleutel, geen upgradepad vanaf 1.x/2.0.0 nodig (Nick herinstalleert).

## Calls van Claude (te checken)

- Feedback blijft mailto (geen eigen endpoint): past bij privacy-belofte en "geen backend erbij". Mailbox `feedback@openschoolcloud.nl` moet bestaan op Hostinger (MX staat daar), check door Nick.
- Accountverwijdering: in de app een knop die de web-URL opent (Authentik user-settings / een verwijderverzoek per mail naar privacy@openschoolcloud.nl) plus lokaal uitloggen + tokens wissen. Geen eigen verwijder-API bouwen zolang er geen sync-data is.
- Play-review-demo is niet nodig zolang inloggen optioneel is.

- Versie: `2.1.0`, versionCode `14` (2.0.0/13 is al gebouwd geweest in maart).
- Upload-keystore wordt nieuw aangemaakt buiten de repo (`~/.config/osc-signing/`), signing via `keystore.properties` (gitignored) of env vars; zonder keystore valt release terug op unsigned zodat F-Droid (die zelf signt) blijft bouwen. Play App Signing aan.
- targetSdk omhoog naar het actuele Play-minimum (verwacht 35, check bij uitvoering); als dat een AGP/Kotlin/Compose-upgrade afdwingt, is dat een eigen taak met eigen test-run.
- `metadata/` → `fastlane/metadata/android/{nl-NL,en-US}/` (F-Droid- en Play-conform), met `changelogs/14.txt`.
- Privacyverklaring: tekst geleverd als bestand; hosten op openschoolcloud.nl doet een aparte stap in de website-repo.

- F-Droid signt met eigen sleutel (standaard, geen reproducible-builds-traject). Gevolg: Play- en F-Droid-installs kunnen niet over elkaar heen updaten; vermelden in store-tekst.
- "Klaar" voor dit plan = ondertekende AAB + store-teksten/privacyverklaring/data-safety-antwoorden aangeleverd, Play closed/interne test klaar om te starten, F-Droid merge request ingediend. Testers werven, review-feedback en productie-rollout vallen erbuiten.
- targetSdk-upgrade (waarschijnlijk 36) inclusief edge-to-edge-insets is de grootste losse taak; visuele verschuivingen door insets gelden als bugfix.

## Aan de bouwer

Volgorde van opruimtaken, welke lint-regels je strenger zet, en hoe de merge-conflicten precies worden opgelost (wel: beide fixes behouden, tests bewijzen het).

## Bekende valkuil Play

Persoonlijke developer-accounts aangemaakt na nov 2023 moeten eerst een closed test draaien met ≥12 testers gedurende 14 dagen voordat productie mag. Check in de console welk type account het is; plan dat in.
