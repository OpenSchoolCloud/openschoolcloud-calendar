# OSC Calendar — Onboarding, Identiteit, Gezin & Verdienmodel

**Datum:** 2026-09-27
**Status:** Ontwerp goedgekeurd, klaar voor implementatieplan
**Scope van deze spec:** Subsysteem **A** (app-laag) + **B** (identity-backend), inclusief het economische model en de contractgrens voor **C** (betaalde sync-backend). C wordt in dezelfde werkstroom gebouwd zodat OSC-sync meteen live kan, maar krijgt vanwege de omvang een **eigen implementatieplan/spec**.

---

## 1. Probleem & intentie

OSC Calendar is nu een privacy-first, lokaal/CalDAV-gerichte Android-app. De onboarding bestaat uit drie losse stukken (3-slide intro → ModeSelection → Nextcloud-login) en kent geen echte accountlaag, geen gezinsconcept en geen MFA.

Doelen:
1. **Onboarding-herziening** tot één samenhangende trechter.
2. **Gezin aanhaken:** één ouder-accounthouder met kind-profielen eronder.
3. **Accountlaag:** e-mail + wachtwoord met **verplichte TOTP-MFA** voor iedereen.
4. **Sync-keuze** met een betaalde optie voor wie geen eigen cloud heeft.
5. **Verdienmodel** dat adoptie niet in de weg staat.

Niet-doelen (nu): B2B school-/klasabonnementen; de technische sync-backend C; een EUDI-wallet-integratie (wel als naad meegenomen).

## 2. Belangrijke, vastgelegde beslissingen

| # | Beslissing | Reden |
|---|---|---|
| D1 | Eén **ouder** is accounthouder; kinderen zijn **profielen** zonder eigen login | AVG-vriendelijk voor minderjarigen; laagste complexiteit |
| D2 | **Account-first** voor iedereen: e-mail + wachtwoord + verplichte TOTP-MFA | Uniforme onboarding, aansluit op "mensen doen dit obv e-mail+wachtwoord" |
| D3 | **Authentik (self-hosted, bestaand)** als OIDC-provider | Geen eigen wachtwoordopslag/MFA-code bouwen = minder risico + soeverein |
| D4 | Kind-profielen + hun data zijn **lokaal-first**; syncen alleen bij betaalde/eigen cloud, en dan naar *die* cloud — **nooit** naar de IdP | Minimale dataverantwoordelijkheid; geen minderjarigen-PII op de IdP |
| D5 | Login via **AppAuth + Custom Tab** (OIDC Authorization Code + PKCE) | OAuth 2.1-standaard; wachtwoord/MFA nooit in de app |
| D6 | Betaalde laag = **gehoste gezins-sync**, geprijsd **per gezin** | App zelf blijft gratis; je verkoopt gemak, niet functionaliteit |
| D7 | Trial = **30 dagen, geen kaart vooraf, prompt ~dag 25**, daarna **zachte degradatie naar lokaal** (niets wordt verwijderd) | Maximale adoptie zonder oneindig gratis hosting |

**Risico-erkenning:** door D3 wordt de eigenaar (OSC/Aldewereld Consultancy) verwerkingsverantwoordelijke voor ouder-identiteiten (e-mail, wachtwoord-hash, TOTP-secret). D3 + D4 begrenzen dit: geen eigen auth-code, geen kind-PII op de IdP.

## 3. Onboarding-flow (subsysteem A, UX)

```
Splash
 └─ Welkom (1 scherm: privacy + "voor het hele gezin")
     └─ [Account maken] | [Inloggen]
         └─ Custom Tab → Authentik
              • Account maken: e-mail + wachtwoord
              • Verplichte MFA-enrollment (TOTP, QR) — geen skip
              • Herstelcodes 1x tonen
         └─ terug in app met tokens
              └─ Gezin opzetten: gezinsnaam + eerste kind-profiel(en)
                   (naam + kleur/avatar, geen verplichte PII)
                   └─ Sync-keuze (3 kaarten):
                        1) Alleen dit apparaat (gratis)
                        2) Eigen Nextcloud koppelen (CalDAV, gratis)
                        3) OSC-sync — betaald (trial)
                        └─ Kalender (met profiel-switcher)
```

De bestaande 3-slide onboarding + ModeSelection + Nextcloud-login worden hierin opgenomen. Het huidige server/username/password-formulier blijft **alleen** achter "Eigen Nextcloud koppelen" (Kaart 2); CalDAV heeft zijn eigen app-password los van de OSC-identiteit.

## 4. Datamodel (subsysteem A)

Nieuw/gewijzigd (Room; migraties verplicht):

```
FamilyEntity   id, name, ownerAccountId, createdAt
ProfileEntity  id, familyId, displayName, color, avatar,
               isOwnerProfile: Boolean,
               syncScope: LOCAL | OWN_NEXTCLOUD | OSC_CLOUD   (default LOCAL)
AccountEntity (uitgebreid)
               id (= oscSub, OIDC subject), email, authProvider,
               mfaEnrolled: Boolean,
               displayName?,
               serverUrl?/username?/principalUrl?/calendarHomeSet?  (optioneel; alleen bij Nextcloud-sync)
EventEntity    + profileId  (migratie: bestaande events → owner-profiel)
```

Regels:
- Eén ingelogde ouder = één `FamilyEntity` (owner).
- Kind-profielen dragen **minimale data** (naam + kleur/avatar), geen e-mail/geboortedatum verplicht.
- `syncScope` per profiel bepaalt of/waarheen events syncen. Default `LOCAL`.
- Kalender toont een **profiel-switcher**: owner kan filteren op "hele gezin" of één kind.
- Migratie: `events` krijgt `profile_id TEXT` met verwijzing naar het owner-profiel voor bestaande rijen.

## 5. Identity-backend (subsysteem B) + app-integratie

**Authentik-kant:**
- OAuth2/OIDC Provider + Application "OSC Calendar". Redirect-URI `nl.openschoolcloud.calendar://oidc`. Scopes `openid profile email offline_access`.
- **Enrollment-flow:** self-service registratie (e-mail + wachtwoord) met **niet-optionele TOTP-stage**.
- **Authentication-flow** met TOTP; **recovery-flow** (wachtwoord vergeten via e-maillink); static recovery-codes.
- MFA wordt **in de Authentik-flow** afgedwongen, niet in de app → onomzeilbaar.
- Aanbeveling: **aparte OSC-brand/tenant** binnen Authentik voor scheiding van blast-radius (implementatiedetail, niet blokkerend).
- PII op de IdP: uitsluitend e-mail, wachtwoord-hash, TOTP-secret. **Geen kind-data.**
- Draait op eigen EU-soevereine infra.

**App-kant:**
- Lib: **AppAuth-Android** (net.openid.appauth), Authorization Code + PKCE via Custom Tab.
- Nieuwe `AuthRepository`: `login()`, `logout()`, `getValidAccessToken()` (auto-refresh).
- Token-opslag in **EncryptedSharedPreferences** met lazy-init + fallback (conform bekende MasterKey-valkuil).
- `oscSub` (OIDC `sub`) is de stabiele sleutel van `AccountEntity`.
- Issuer/discovery-URL + `client_id` als **BuildConfig-velden** (test/prod-IdP wisselbaar), niet hardcoded.
- Twee gescheiden credential-soorten: OSC-identiteit (Authentik) en optioneel Nextcloud app-password (CalDAV). Strikt gescheiden opgeslagen en gebruikt.

## 6. MFA & herstel

- **Enrollment** verplicht bij account maken (TOTP, QR + handmatige sleutel) in de Authentik-flow; geen "later doen".
- **Herstelcodes** worden 1x getoond; na terugkeer in de app een kort scherm "Bewaar je herstelcodes" met kopieer/opslaan + expliciete bevestiging "Ik heb ze bewaard".
- **Kwijt-scenario's:**
  - Wachtwoord vergeten → Authentik recovery via e-maillink.
  - Authenticator kwijt + recovery-code → login met code, daarna her-enrollen.
  - Beide kwijt → **handmatige, gelogde admin-reset** door de beheerder in Authentik na identiteitscheck (geen self-service). Operationele procedure; hoort bij subsysteem B-runbook.
- **Sessieduur:** refresh-token ~30 dagen, access-token kort (config in Authentik; aanbeveling).
- **Kind-profielen** raken MFA niet (geen credentials). Toekomst-optie (buiten scope): lokale pincode/profiel-lock.

## 7. Sync-keuze, verdienmodel & naad naar subsysteem C

### 7.1 Persona's (gerangschikt op waarde voor de *betaalde* laag)
1. **Niet-technische ouder, meerdere kinderen/apparaten** — kan/wil geen eigen Nextcloud. **Betalende kern.** Waarde = gemak + privacy-belofte. Prijsgevoelig.
2. **Privacy-ouder met eigen Nextcloud** — gratis (Kaart 2); betaalt niet maar is **ambassadeur**. Niet forceren.
3. **Lokaal-only minimalist** — gratis, lage supportlast; funnel-top.
4. **School/leraar** — latente B2B-waarde (klasabonnement); **buiten scope**, later.

Het prijsmodel richt zich op persona 1 en prijst **per gezin** (niet per kind/profiel).

### 7.2 Verdienmodel
De app is **gratis en volledig** (lokaal + eigen Nextcloud); de betaalde laag is **gehoste gezins-sync**.

Het **WinRAR-model** (ongelimiteerd gratis, nooit afdwingen) past hier niet: gehoste sync heeft echte marginale hostingkosten per gezin, dus "hopen dat men betaalt" subsidieert freeloaders eindeloos. In plaats daarvan: **trial die zacht degradeert**.

| Laag | Prijs (indicatie; exacte bedragen = eigenaarsbesluit) | Wat |
|---|---|---|
| Lokaal | €0 eeuwig | Alles op het apparaat |
| Eigen Nextcloud | €0 eeuwig | CalDAV-koppeling |
| OSC-sync | **€1 per gezinslid / maand** | Gehoste multi-device gezins-sync, geen eigen server |
| OSC-sync jaarplan | **€1/lid/mnd − 20% bij jaarlijkse vooruitbetaling** | Zelfde, goedkoper + minder churn/transactiekosten |

**Prijs per lid (eigenaarsbesluit, "lekker makkelijk"):** €1 per actief profiel per maand. Gevolg: het abonnement is **gekoppeld aan het aantal actieve profielen** en beweegt mee als de ouder een profiel toevoegt/verwijdert. C moet dus prorata/quantity-updates op het Mollie-abonnement doen bij profielwijziging (niet een vast bedrag). Trade-off t.o.v. per-gezin: grotere gezinnen betalen meer — bewust geaccepteerd voor eenvoud en transparantie.

**Trial (D7):** 30 dagen, **geen kaart vooraf**. Rond **dag 25** een vriendelijke betaalprompt (Mollie-mandaat). Betaalt men niet → **sync pauzeert, data blijft lokaal, niets verwijderd**. Betalen heractiveert direct. Jaarplan (−20%) drukt churn + transactiekosten.

**Betaalprovider:** Mollie **Subscriptions** (eerste betaling = mandaat, daarna recurring).
- Test-key (placeholder, later vervangen): `test_QkEj…D7x`.
- **Live-key nooit in app/repo/chat**; uitsluitend server-side env var (subsysteem C).

### 7.3 Naad naar subsysteem C (nu bouwen: de naad, niet C)
- `SyncProvider`-interface met impls `LocalSyncProvider`, `NextcloudCalDavProvider`, en een **stub** `OscCloudProvider` die C later invult.
- Feature-flag `ENABLE_OSC_CLOUD` (default `false`): veiligheidsschakelaar. Kaart 3 toont "Binnenkort" tot C gebouwd + getest is; daarna vlag aan → live zonder nieuwe app-release.
- **Besluit:** C wordt in dezelfde werkstroom meegebouwd zodat OSC-sync **meteen live kan** zodra hij getest is (dus géén blijvende placeholder). C krijgt wel een eigen implementatieplan/spec vanwege de omvang.
- **Contractgrens die C moet leveren:** geprovisioneerd Nextcloud-account per klant; CalDAV-endpoint + app-password teruggeven aan de app; koppelen aan `oscSub`; abonnement-/trial-status opvraagbaar zodat de app zacht kan degraderen; **Mollie-abonnement met quantity = aantal actieve profielen**, prorata bij wijziging; webhook voor betaal-/mandaatstatus.

## 8. Privacy & compliance (blijft gelden)
- Geen Google Play Services / Firebase / analytics.
- Geen kind-PII op de IdP; kind-data lokaal tenzij ouder bewust cloud-sync kiest.
- Upsell blijft subtiel (geen popups): sync-keuze + rustige Settings-kaart.
- CalDAV-standaard met `X-OSC-`-prefix voor custom properties.

## 9. EUDI-wallet (toekomst, alleen naad)
Omdat login via OIDC/Authentik loopt, is een latere EUDI-wallet (eIDAS 2.0) toe te voegen als **extra provider** in Authentik, zonder app-herarchitectuur. DigiD is voor een privaat product niet begaanbaar en valt af.

## 10. Open punten voor het implementatieplan
- Exacte prijsbedragen en jaarkorting (eigenaarsbesluit).
- Authentik: aparte tenant/brand ja/nee (B-detail).
- Room-migratiepad voor bestaande installs (accounts zonder `oscSub`): eenmalige "koppel je nieuwe OSC-account"-migratiestap of clean re-onboard.
- Precieze token-levensduren.
```
