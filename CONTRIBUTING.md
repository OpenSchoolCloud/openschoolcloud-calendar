# Contributing to OpenSchoolCloud Calendar

Bedankt voor je interesse in bijdragen aan OpenSchoolCloud Calendar! 🎉

## Code of Conduct

We verwachten dat iedereen respectvol en constructief communiceert. Dit project is bedoeld om het onderwijs te helpen — laten we dat samen doen.

---

## Hoe kun je bijdragen?

### 🐛 Bugs rapporteren

1. Check eerst of de bug al gemeld is in [Issues](https://github.com/NickAldewereld/openschoolcloud-calendar/issues)
2. Maak een nieuwe issue met:
   - Duidelijke titel
   - Stappen om te reproduceren
   - Verwacht gedrag vs. actueel gedrag
   - Device/OS versie
   - Screenshots indien relevant

### 💡 Features voorstellen

1. Check [SCOPE.md](SCOPE.md) of de feature al gepland is
2. Open een issue met label `enhancement`
3. Beschrijf:
   - Het probleem dat je wilt oplossen
   - Je voorgestelde oplossing
   - Alternatieven die je hebt overwogen

### 🔧 Code bijdragen

#### Setup

```bash
# Fork de repository
git clone https://github.com/JOUW_USERNAME/openschoolcloud-calendar.git
cd openschoolcloud-calendar

# Maak een feature branch
git checkout -b feature/jouw-feature-naam
```

#### Coding Standards

**Android (Kotlin):**
- Volg [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html)
- Gebruik Compose voor UI
- Schrijf unit tests voor business logic
- Format met ktlint

**iOS (Swift):**
- Volg [Swift API Design Guidelines](https://swift.org/documentation/api-design-guidelines/)
- Gebruik SwiftUI voor UI
- Schrijf unit tests voor business logic
- Format met SwiftLint

#### Commit Messages

Gebruik [Conventional Commits](https://www.conventionalcommits.org/):

```
feat: add week view navigation
fix: resolve sync conflict on offline events
docs: update CalDAV discovery documentation
refactor: extract calendar repository interface
test: add unit tests for iCal parser
```

#### Pull Requests

1. Zorg dat alle tests slagen
2. Update documentatie indien nodig
3. Beschrijf wat je PR doet en waarom
4. Link naar relevante issues

---

## Development Guidelines

### Architectuur

```
┌─────────────────┐
│   Presentation  │  ← UI, ViewModels
├─────────────────┤
│     Domain      │  ← UseCases, Models, Repository interfaces
├─────────────────┤
│      Data       │  ← Repository implementations, CalDAV, Database
└─────────────────┘
```

### CalDAV Implementation

- **Server is truth** — bij conflict wint de server
- **Offline-first** — altijd lokale cache tonen
- **Minimal requests** — gebruik CTag/sync-token voor differential sync

### Privacy

- **Geen analytics** zonder expliciete opt-in
- **Geen externe dependencies** die data lekken
- **Credentials** alleen in secure storage (Keystore/Keychain)

---

## Vertalingen

We zoeken vertalers voor:
- 🇩🇪 Duits
- 🇫🇷 Frans
- 🇪🇸 Spaans

Strings staan in:
- Android: `android/app/src/main/res/values-XX/strings.xml`
- iOS: `ios/OpenSchoolCloudCalendar/Resources/XX.lproj/Localizable.strings`

---

## Vragen?

- Open een [Discussion](https://github.com/NickAldewereld/openschoolcloud-calendar/discussions)
- Email: info@openschoolcloud.nl

---

## Licentie

Door bij te dragen ga je akkoord dat je bijdrage valt onder de [Apache 2.0 License](LICENSE).
