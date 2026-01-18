# Milestones — OpenSchoolCloud Calendar

## Overview

```
MVP (v0.1)  →  v1.0  →  v2.0
   ↓           ↓        ↓
 8 weken    +8 weken  +8 weken
```

---

## MVP (v0.1) — "Het Werkt"

**Doel:** Werkende app die agenda's toont en events kan aanmaken

**Doorlooptijd:** 6-8 weken

### Sprint 1 (Week 1-2): Foundation

**Android:**
- [ ] Project setup (Gradle, Hilt, Compose)
- [ ] CalDAV discovery implementatie
  - [ ] Well-known endpoint detection
  - [ ] Principal URL discovery
  - [ ] Calendar home set discovery
- [ ] Account storage (encrypted)
- [ ] Basic login UI

**iOS:**
- [ ] Project setup (SwiftUI, CoreData)
- [ ] CalDAV discovery implementatie
- [ ] Keychain integration
- [ ] Basic login UI

**Shared:**
- [ ] CalDAV protocol documentation
- [ ] Test server setup

### Sprint 2 (Week 3-4): Core Calendar

**Android:**
- [ ] Room database schema (Events, Calendars, Accounts)
- [ ] CalDAV sync engine (CTag-based)
- [ ] Calendar list screen
- [ ] Week view (basic)

**iOS:**
- [ ] CoreData schema
- [ ] CalDAV sync engine
- [ ] Calendar list screen
- [ ] Week view (basic)

### Sprint 3 (Week 5-6): Event Management

**Android:**
- [ ] Day view
- [ ] Month view
- [ ] Event detail screen
- [ ] Event create/edit screen
- [ ] iCal serialization (ical4j)

**iOS:**
- [ ] Day view
- [ ] Month view
- [ ] Event detail screen
- [ ] Event create/edit screen
- [ ] iCal serialization

### Sprint 4 (Week 7-8): Polish & Release

**Android:**
- [ ] Offline cache
- [ ] Basic reminders (local notifications)
- [ ] Attendee display
- [ ] Invite sending (iTIP)
- [ ] Error handling
- [ ] Play Store / F-Droid preparation

**iOS:**
- [ ] Offline cache
- [ ] Basic reminders
- [ ] Attendee display
- [ ] Invite sending
- [ ] Error handling
- [ ] App Store preparation

**Both:**
- [ ] Translation review (NL)
- [ ] Accessibility audit
- [ ] Beta testing
- [ ] Release notes

---

## v1.0 — "Feature Complete"

**Doel:** Volwaardig alternatief voor Google Calendar

**Doorlooptijd:** +6-8 weken na MVP

### Features

- [ ] Multi-account support
- [ ] Search (title, location, description)
- [ ] Multiple reminders per event
- [ ] Recurring events (full RRULE support)
- [ ] Widgets (today view)
- [ ] Contact picker (device + CardDAV)
- [ ] Resource calendars (rooms)
- [ ] RSVP response handling
- [ ] Deep links (Maps, share)
- [ ] German translation
- [ ] French translation

### Technical

- [ ] Background sync optimization
- [ ] Performance profiling
- [ ] Memory optimization
- [ ] Battery usage audit

---

## v2.0 — "Next Level"

**Doel:** Geavanceerde features en OpenSchoolCloud integratie

**Doorlooptijd:** +6-8 weken na v1.0

### Features

- [ ] Natural language input ("Morgen 14:00 teamoverleg")
- [ ] File attachments (links)
- [ ] Free/busy scheduling assistant
- [ ] 10-minutengesprekken integratie (OpenSchoolCloud)
- [ ] Spanish translation

### Technical

- [ ] Server push notifications (optional)
- [ ] Offline conflict resolution UI
- [ ] Advanced analytics (opt-in, self-hosted)

---

## GitHub Milestones Setup

Create these milestones in GitHub:

| Milestone | Due Date | Description |
|-----------|----------|-------------|
| `mvp-sprint-1` | Week 2 | Foundation: CalDAV discovery, login |
| `mvp-sprint-2` | Week 4 | Core: Database, sync, views |
| `mvp-sprint-3` | Week 6 | Events: CRUD, detail screens |
| `mvp-sprint-4` | Week 8 | Polish: Offline, notifications, release |
| `v1.0` | Week 16 | Feature complete |
| `v2.0` | Week 24 | Advanced features |

---

## Labels

Create these labels:

| Label | Color | Description |
|-------|-------|-------------|
| `android` | `#3DDC84` | Android-specific |
| `ios` | `#147EFB` | iOS-specific |
| `caldav` | `#7B68EE` | CalDAV protocol |
| `ui` | `#FF69B4` | User interface |
| `sync` | `#20B2AA` | Synchronization |
| `offline` | `#DDA0DD` | Offline support |
| `i18n` | `#FFD700` | Translations |
| `a11y` | `#00CED1` | Accessibility |
| `mvp` | `#FF4500` | MVP scope |
| `v1` | `#32CD32` | v1.0 scope |
| `v2` | `#4169E1` | v2.0 scope |
| `blocked` | `#DC143C` | Blocked by something |
| `help-wanted` | `#008B8B` | Help wanted |
| `good-first-issue` | `#7CFC00` | Good for newcomers |

---

## Definition of Done

An issue is "Done" when:

1. ✅ Code is written and works
2. ✅ Unit tests pass
3. ✅ UI tests pass (where applicable)
4. ✅ Code review approved
5. ✅ Translations updated (if UI text changed)
6. ✅ Accessibility checked
7. ✅ Documentation updated (if API/behavior changed)
8. ✅ Merged to main branch
