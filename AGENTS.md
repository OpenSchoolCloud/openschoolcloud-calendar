# AGENTS.md — OSC Calendar (werkgeheugen)

Privacy-first Android-kalender (Kotlin/Compose/Hilt/Room, CalDAV→Nextcloud). Lees ook `CLAUDE.md` (bouwregels) en `.superpowers/sdd/progress.md` (volledige taak/fix-historie + go-live-gates). Specs/plannen in `docs/superpowers/`.

## Stand per 2026-10-07 (go-live gepauzeerd)

Drie plannen via subagent-driven dev (elke taak TDD + review; brede eind-reviews vingen 9 echte bugs, o.a. Postgres-brekende datetime, Mollie-webhook-retry-storm, CalDAV-username-401, login-404). `assembleDebug` groen; unit-suite groen (de pre-existing ICalParserTest-fail is gefixt: RFC 5545 nul-duur).

- **Plan A** — app-laag: account-first onboarding (Splash → Welkom/OIDC via AppAuth Custom Tab → herstelcodes → gezin+profielen → sync-keuze → kalender + profiel-switcher). Room `MIGRATION_6_7` (Family/Profile/SyncScope, Event.profileId). Encrypted OIDC-token-store los van CalDAV-creds.
- **Plan B** — Authentik-identiteit (`identity/authentik/`): blueprint + runbook. **LIVE op de VPS** (zie hieronder).
- **Plan C** — betaalde gezins-sync. App-kant hier (`OscCloudProvider`, discovery-token-refresh, WorkManager soft-degrade), **dark achter `ENABLE_OSC_CLOUD=false`**. Backend = aparte repo `osc-cloud-sync` (zie die repo's AGENTS.md), **draait al op de VPS**.

## Git / hoe verder

- Al het werk zit op branch **`feature/osc-onboarding-identity-cloud`** → **PR #1** (https://github.com/OpenSchoolCloud/openschoolcloud-calendar/pull/1).
- **Lokale `main` is uiteengelopen van `origin/main`** (origin had een andere "Empty CTag"-fix). Merge-conflict in CalDAV-kernbestanden (`CalDavClient.kt`, `AccountRepositoryImpl.kt`, `CalendarRepositoryImpl.kt`, `CalendarViewModel.kt`) — **op te lossen in de PR, nooit force-pushen** (zou origin's werk wissen).
- Elke nieuwe fix: committen op de feature-branch → push → update PR #1.

## Vallen (niet-evident)

- **App-login moet OIDC-discovery gebruiken** (`AuthManager.fetchFromIssuer`), NOOIT endpoints hardcoden: authorize/token zijn globaal (`…/application/o/authorize/`), jwks is per-app (`…/o/osc-calendar/jwks/`). Hardcoden = login-404.
- OIDC-contract (byte-exact, matcht Authentik-blueprint + C-backend): issuer `https://id.openschoolcloud.nl/application/o/osc-calendar/`, client_id `osc-calendar`, public+PKCE, redirect `nl.openschoolcloud.calendar://oidc`, scopes openid/profile/email/offline_access.
- AGPL-header op **elke** `.kt` (ook tests). Geen em-dashes in user-facing tekst (NL). Compose BOM 2024.12.01 (geen PullToRefreshBox/HorizontalDivider).

## Go-live-gates (vóór ENABLE_OSC_CLOUD aan)

Jaar-−20% wiren · checkout/member-change-UI · NC-disable-cron bij trial-verloop · JWKS-rotatie-refresh · live bevestigen dat Authentik access-token `aud=osc-calendar` draagt. VPS-details + de B/C-runbooks: `identity/authentik/RUNBOOK.md` en de osc-cloud-sync-repo.
