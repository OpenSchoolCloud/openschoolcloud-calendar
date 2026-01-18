# Scope — OpenSchoolCloud Calendar (Android + iOS)

## 0. Ontwerpprincipes

| Principe | Betekenis |
|----------|-----------|
| **One-time setup** | 1 scherm, 3 velden, klaar |
| **Default veilig** | App-wachtwoord, HTTPS, minimale permissies |
| **Platform-native** | Gedrag aansluitend bij Android/iOS agenda's |
| **CalDAV-first** | Nextcloud is bron; app is client |

---

## 1. Onboarding & Login (MVP+)

### 1.1 Eerste scherm (one-time setup)

**UI**

| Veld | Type | Notities |
|------|------|----------|
| Nextcloud URL | Text | Auto-normalisatie naar `https://…` |
| Gebruikersnaam | Text | Email of username |
| App-wachtwoord | Password | Met "Maak app-wachtwoord" link/QR-instructie |

**Flow**

1. "Verbind" → auto-discover CalDAV endpoints
2. Toon lijst "Agenda's gevonden"
3. Toggle per agenda: Sync aan/uit
4. Eén knop: Gereed

**UX-vereisten**

- URL-validatie + waarschuwing bij HTTP / self-signed
- "Test verbinding" implicit (bij verbind-knop)
- Error states: verkeerde credentials, 2FA, server unreachable, DNS issues

### 1.2 Accountbeheer (v1)

- Meerdere accounts (docent + privé)
- Account switcher
- "Reset / logout" + data wipe (cache)

---

## 2. Kernfuncties Agenda (MVP)

### 2.1 Views (platformconform)

| View | Prioriteit | Notities |
|------|------------|----------|
| Dag | Default | Standaard bij openen |
| Week | Hoog | Meest gebruikt in werkcontext |
| Maand | Medium | Overzicht |
| "Vandaag"-knop | Hoog | Altijd zichtbaar |
| Zoek | v1 | Titel/locatie/omschrijving |

### 2.2 Afsprakenlijst

- Tijdlijn met kleurblokjes (kalenderkleur)
- All-day events
- Herhalende afspraken (weergave + basis edit) — v1

---

## 3. Event Aanmaken/Bewerken (MVP)

### 3.1 Event editor velden

**Minimaal (MVP):**

| Veld | Type | Required | Notities |
|------|------|----------|----------|
| Titel | Text | ✓ | |
| Datum/tijd start | DateTime | ✓ | |
| Datum/tijd eind | DateTime | | |
| All-day toggle | Boolean | | |
| Tijdzone | Select | | Default: device timezone |
| Locatie | Text | | + optioneel map deep link |
| Omschrijving | Text | | |
| Kalender | Select | ✓ | Drop-down |

**Kleur:**
- Primair: kleur per kalender (native gedrag)
- Extra: event-kleur override indien server ondersteunt (fallback: label in notes)

**Uitnodigen (MVP):**
- Genodigden (email invoer + contact picker)
- Organisator (read-only: account)
- RSVP-statussen tonen (Accepted/Declined/Tentative) — v1

**Notificaties:**
- Reminder presets: 0 / 5 / 10 / 30 min / 1 uur / 1 dag
- Meerdere reminders — v1
- "Reistijd" (iOS-achtig) — optioneel later

### 3.2 Update & Resend Logic (essentieel)

Bij wijzigen van een afspraak met genodigden:

```
Pop-up:
├── "Wijziging sturen naar genodigden?" (default: Ja)
└── "Alleen genodigden met veranderingen informeren" (optioneel)
```

- Bij tijd/datum/locatie/omschrijving wijziging → nieuw iTIP invite/update sturen via server (CalDAV scheduling)
- Bij annuleren → cancellation sturen

### 3.3 Conflict & Offline Gedrag

**Offline aanmaken:**
- Opslaan als "Pending"
- Sync zodra online

**Conflict (server-versie wijkt af):**
- "Serverversie behouden" (default)
- "Mijn versie overschrijven" (advanced)

---

## 4. Calendars & Sharing (v1)

- Lijst met agenda's + kleur beheren
- Read-only agenda's (school/roosters) duidelijk labelen
- Support voor gedeelde agenda's en resources (ruimtes) indien Nextcloud dit aanbiedt

---

## 5. Contacts (v1)

**Contact picker:**
- Lokaal toestel (Android Contacts / iOS Contacts)
- Optioneel Nextcloud Contacts (CardDAV) als bron

**Autocomplete:**
- Op eerder gebruikte genodigden

---

## 6. Integraties (v1+)

### 6.1 Deep Links & Intents

- Open address in Maps
- Deel event (ICS-export of share link)
- Add from share (tekst → quick add)

### 6.2 Widgets

| Widget | Platform | Versie |
|--------|----------|--------|
| "Agenda vandaag" | Android | v1 |
| Agenda widget | iOS | v1 |

### 6.3 Push/Notificaties

- Local notifications op reminders
- Optioneel server push (later; complexer)

---

## 7. Permissions & Privacy

**Minimale permissies:**

| Permissie | Doel | Required |
|-----------|------|----------|
| Internet | CalDAV sync | ✓ |
| Notifications | Reminders | ✓ |
| Contacts | Contact picker | Optioneel |

**Privacy:**
- Geen analytics standaard
- Telemetry alleen opt-in (self-hostable endpoint)
- Logging lokaal + exporteerbaar (voor school-ICT)

---

## 8. Non-Functional Requirements

| Requirement | Target |
|-------------|--------|
| Startup time | < 1s voor cached view |
| Battery | WorkManager (Android) / BackgroundTasks (iOS) |
| Storage | Encrypt local storage (Keychain/Keystore) |
| Accessibility | WCAG-ish: grote tekst, contrast, screenreader labels |

---

## 9. Releaseplan

### MVP (eerste release)

- [x] One-time onboarding (URL/user/app-password)
- [x] Agenda views: dag/week/maand
- [x] Create/edit event: titel, tijd, locatie, omschrijving, kalender, kleur
- [x] Invite attendees + send updates on changes
- [x] Reminders (1)
- [x] Basissync + offline read cache

### v1 (na MVP)

- [ ] Multi-account
- [ ] Search
- [ ] Multiple reminders
- [ ] Recurring events full edit
- [ ] Widgets
- [ ] Contact autocomplete (device + CardDAV)
- [ ] Resource calendars (rooms)

### v2 (optioneel)

- [ ] Natural language input ("Morgen 14:00 teamoverleg lokaal 2.13")
- [ ] Attachments (links)
- [ ] Advanced scheduling assistant (free/busy)

---

## 10. UX-schets (Onboarding in 30 seconden)

```
┌─────────────────────────────────────┐
│                                     │
│   🏫 OpenSchoolCloud Calendar       │
│                                     │
│   Verbind met je school             │
│                                     │
│   ┌───────────────────────────────┐ │
│   │ https://cloud.school.nl       │ │
│   └───────────────────────────────┘ │
│                                     │
│   ┌───────────────────────────────┐ │
│   │ m.jansen                      │ │
│   └───────────────────────────────┘ │
│                                     │
│   ┌───────────────────────────────┐ │
│   │ ••••••••••••                  │ │
│   └───────────────────────────────┘ │
│   ℹ️ Maak app-wachtwoord aan        │
│                                     │
│   ┌───────────────────────────────┐ │
│   │         Verbinden             │ │
│   └───────────────────────────────┘ │
│                                     │
└─────────────────────────────────────┘
           │
           ▼
┌─────────────────────────────────────┐
│   ✓ Agenda's gevonden              │
│                                     │
│   ☑️ Mijn agenda                    │
│   ☑️ Teamagenda groep 6             │
│   ☐ Vakanties (read-only)          │
│                                     │
│   ┌───────────────────────────────┐ │
│   │          Gereed               │ │
│   └───────────────────────────────┘ │
└─────────────────────────────────────┘
           │
           ▼
┌─────────────────────────────────────┐
│   Week 3 • Januari 2025      + ⚙️  │
│   ─────────────────────────────────│
│   Ma  Di  Wo  Do  Vr  Za  Zo       │
│   13  14  15  16  17  18  19       │
│   ─────────────────────────────────│
│   09:00 ┌──────────────────┐       │
│         │ Teamoverleg      │       │
│         │ Lokaal 2.13      │       │
│         └──────────────────┘       │
│   10:00                            │
│                                     │
│   11:00 ┌──────────────────┐       │
│         │ Oudergesprek     │       │
│         │ Familie De Vries │       │
│         └──────────────────┘       │
│                                     │
│   ─────────────────────────────────│
│   [Dag]  [Week]  [Maand]  [Vandaag]│
└─────────────────────────────────────┘
```

---

*Document versie 1.0 — Januari 2025*
