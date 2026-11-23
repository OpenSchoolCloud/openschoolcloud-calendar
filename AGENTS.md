# AGENTS.md — OSC Calendar (werkgeheugen)

Privacy-first Android-kalender (Kotlin/Compose/Hilt/Room, CalDAV→Nextcloud). Lees ook `CLAUDE.md` (bouwregels) en `.superpowers/sdd/progress.md` (volledige taak/fix-historie + go-live-gates). Specs/plannen in `docs/superpowers/`.

## Stand per 2026-11-23: v2.1.0 gereleased naar main (Play + F-Droid in voorbereiding)

- PR #1 gemerged naar `main`, tag `v2.1.0`. Inloggen bij OSC is optioneel, gezins-sync blijft dark.
- **Release-ondertekening:** upload-sleutel staat BUITEN de repo in `~/.config/osc-signing/` (`osc-upload.jks` + `keystore.properties`, chmod 600). Cert SHA-256 `03:40:79:B6:51:42:D0:14:B2:F3:D8:77:55:86:D3:DD:C3:94:C0:ED:B7:F3:AD:5C:E0:E4:B6:72:30:D7:99:4E`. Kwijt = nieuwe upload-sleutel aanvragen bij Play. Zonder bestand bouwt release unsigned (F-Droid) met een waarschuwing.
- Gradle draaien met `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64` (JDK 25 breekt de build).
- Store-stappen voor Nick: `docs/release/play-console-checklist.md` (closed test ≥12 testers × 14 dagen, persoonlijk account), `docs/release/play-data-safety.md`, F-Droid-recept `docs/release/fdroid/`.
- Privacy- en verwijderpagina staan in de website-repo `openschoolcloud-frontend-website` (`/juridisch/privacy`, `/account-verwijderen`).

## Stand per 2026-10-07 (go-live gepauzeerd)

Drie plannen via subagent-driven dev (elke taak TDD + review; brede eind-reviews vingen 9 echte bugs, o.a. Postgres-brekende datetime, Mollie-webhook-retry-storm, CalDAV-username-401, login-404). `assembleDebug` groen; unit-suite groen (de pre-existing ICalParserTest-fail is gefixt: RFC 5545 nul-duur).

- **Plan A** — app-laag: account-first onboarding (Splash → Welkom/OIDC via AppAuth Custom Tab → herstelcodes → gezin+profielen → sync-keuze → kalender + profiel-switcher). Room `MIGRATION_6_7` (Family/Profile/SyncScope, Event.profileId). Encrypted OIDC-token-store los van CalDAV-creds.
- **Plan B** — Authentik-identiteit (`identity/authentik/`): blueprint + runbook. **LIVE op de VPS** (zie hieronder).
- **Plan C** — betaalde gezins-sync. App-kant hier (`OscCloudProvider`, discovery-token-refresh, WorkManager soft-degrade), **dark achter `ENABLE_OSC_CLOUD=false`**. Backend = aparte repo `osc-cloud-sync` (zie die repo's AGENTS.md), **draait al op de VPS**.

## Git / hoe verder

- PR #1 is op 2026-11-23 gemerged naar `main` (origin's "Empty CTag"-fix `857ca5a` zit erin). **Nooit force-pushen.**
- Nieuwe fixes: feature-branch vanaf `main` → PR.

## Vallen (niet-evident)

- **App-login moet OIDC-discovery gebruiken** (`AuthManager.fetchFromIssuer`), NOOIT endpoints hardcoden: authorize/token zijn globaal (`…/application/o/authorize/`), jwks is per-app (`…/o/osc-calendar/jwks/`). Hardcoden = login-404.
- OIDC-contract (byte-exact, matcht Authentik-blueprint + C-backend): issuer `https://id.openschoolcloud.nl/application/o/osc-calendar/`, client_id `osc-calendar`, public+PKCE, redirect `nl.openschoolcloud.calendar://oidc`, scopes openid/profile/email/offline_access.
- AGPL-header op **elke** `.kt` (ook tests). Geen em-dashes in user-facing tekst (NL). Compose BOM 2024.12.01 (geen PullToRefreshBox/HorizontalDivider).

## Go-live-gates (vóór ENABLE_OSC_CLOUD aan)

Jaar-−20% wiren · checkout/member-change-UI · NC-disable-cron bij trial-verloop · JWKS-rotatie-refresh · live bevestigen dat Authentik access-token `aud=osc-calendar` draagt. VPS-details + de B/C-runbooks: `identity/authentik/RUNBOOK.md` en de osc-cloud-sync-repo.

## Geparkeerd idee (2026-11-18): huiswerk/taken leraar → kind

Eerst originele scope releasen, feedback ophalen, dan pas dit. Idee: leraar/coach/mentor/ouder geeft todo's met deadline; kind noteert huiswerk zelf aan eind van de les; ouder kijkt mee om te begeleiden. Leraar via web én app. Scholen melden zich apart aan via openschoolcloud.nl.
Blokkade om eerst op te lossen: kind heeft nu GEEN eigen account (credentialloos profiel op ouder-toestel, zie Models.kt), terwijl dit idee een eigen kind-toestel + sync vereist. Bouwt voort op bestaande `screens/planning` + `screens/reflection`. Scholen/B2B staan in de gezinsspec van 27-09 expliciet als niet-doel.
