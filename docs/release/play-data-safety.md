# Play Console: Gegevensbeveiliging (Data safety), OSC Calendar v2.1.0

Antwoorden per Play-vraag. Gebaseerd op de code van v2.1.0 (versionCode 14). Houd dit gelijk aan de privacyverklaring: https://openschoolcloud.nl/privacy

## Samenvatting
- Geen analytics, advertenties of tracking-SDK's.
- Starten kan zonder account. Een OpenSchoolCloud-account is optioneel.
- Agendagegevens staan op het toestel, of op de eigen Nextcloud van de gebruiker (CalDAV). OpenSchoolCloud verzamelt die niet.
- Verkeer is versleuteld (HTTPS).
- Account verwijderen: https://openschoolcloud.nl/account-verwijderen

## Sectie "Gegevensverzameling en -beveiliging"
| Vraag | Antwoord |
|---|---|
| Verzamelt of deelt je app een van de vereiste soorten gebruikersgegevens? | Ja (alleen bij het optionele account en bij een crashrapport dat de gebruiker zelf verstuurt, zie hieronder) |
| Worden alle gebruikersgegevens tijdens het verzenden versleuteld? | Ja (HTTPS/TLS) |
| Bieden jullie gebruikers een manier om verwijdering van hun gegevens aan te vragen? | Ja |
| URL voor verwijderverzoek | https://openschoolcloud.nl/account-verwijderen |

## Gegevenstypen
Alleen aanvinken wat hieronder staat. Al het andere: niet verzameld.

### Persoonlijke gegevens, naam
- Verzameld: Ja, alleen als de gebruiker inlogt met een OpenSchoolCloud-account.
- Gedeeld met derden: Nee.
- Verwerking: de gebruiker kan kiezen of de gegevens worden verzameld (optioneel account). Niet tijdelijk verwerkt.
- Doel: App-functionaliteit, Accountbeheer.

### Persoonlijke gegevens, e-mailadres
- Verzameld: Ja, alleen bij het optionele account bij id.openschoolcloud.nl (EU-server, inloggen via OIDC).
- Gedeeld met derden: Nee.
- Optioneel: Ja.
- Doel: App-functionaliteit, Accountbeheer.

### Gebruikers-ID's (Apparaat- of andere ID's)
- Verzameld: Ja, alleen als de gebruiker inlogt met een OpenSchoolCloud-account. Het gaat om de OIDC-`sub` (account-ID) van id.openschoolcloud.nl.
- Gedeeld met derden: Nee. Optioneel: Ja.
- Doel: App-functionaliteit, Accountbeheer.
- Zonder account wordt geen ID verzameld.
- CalDAV-inloggegevens (bij "Koppel Nextcloud") gaan uitsluitend naar de eigen server van de gebruiker en nooit naar OpenSchoolCloud.

### Agenda-activiteiten / afspraken
- Verzameld: Nee. Afspraken staan lokaal op het toestel of op de eigen Nextcloud-server van de gebruiker. Ze gaan niet naar OpenSchoolCloud.
- Toelichting: gegevens die de app naar de eigen server van de gebruiker stuurt (CalDAV) vallen onder het beheer van die gebruiker.

### Crashlogs
- Verzameld: Ja (besluit Nick, 2026-11-20). Optioneel: een crashrapport wordt alleen verstuurd als de gebruiker dat zelf kiest, via de eigen e-mailapp naar support@openschoolcloud.nl (ACRA-maildialoog, geen automatische verzending). Inhoud: appversie, Android-versie, toesteltype, foutmelding en optionele opmerking.
- In Play: "Crashlogs" aanvinken. Verzameld: ja. Gedeeld: nee. Tijdelijk verwerkt (ephemeral): nee. Verplicht of optioneel: optioneel. Doel: Analyse.

### Alles hieronder: niet verzameld
Locatie, financiële gegevens, gezondheid en fitness, berichten (let op: "Mis je iets?" opent alleen de eigen mailapp, niets wordt automatisch verstuurd), foto's en video's, audio, bestanden en documenten, contacten (de app vraagt geen toegang tot contacten), app-activiteit, webbrowsegeschiedenis.

## Beveiligingspraktijken
- Gegevens worden versleuteld tijdens verzending: Ja.
- Verwijderverzoek mogelijk: Ja (URL hierboven).
- Play Families-beleid: niet van toepassing, doelgroep is 18+ (besluit Nick, 2026-11-20).
- Onafhankelijke beveiligingsreview: Nee (niet aanvinken).

## Verklaringen en let op
- Wijzigt de app (analytics, nieuwe SDK, betaalde gezinssync die nu uit staat): dit formulier en de privacyverklaring eerst bijwerken.
- Gebruikers die naar de eigen Nextcloud-server synchroniseren: dat is een door de gebruiker gekozen bestemming. OpenSchoolCloud is daar geen verwerker van.
