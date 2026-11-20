# Play Console checklist, OSC Calendar v2.1.0

Stappen voor Nick, in volgorde.

## LET OP: closed test is verplicht voor productie
Het Play-account is PERSOONLIJK en is aangemaakt na november 2023. Dan geldt:
- Eerst een closed test met minimaal 12 testers die de test minimaal 14 aaneengesloten dagen aan hebben staan.
- Pas daarna kun je toegang tot productie aanvragen (formulier in Play Console).
- Reken dus op minimaal 2 weken tussen eerste upload en publicatie. Begin vroeg met testers werven.
- De release-build is nog NIET ondertekend (geen echte sleutel). Eerst de upload-keystore regelen (zie punt 2).

## 1. App aanmaken
- Play Console > Alle apps > App maken.
- Naam: OSC Calendar. Standaardtaal: Nederlands (nl-NL). Type: App. Gratis.
- Package name (komt uit de eerste upload, moet kloppen): `nl.openschoolcloud.calendar`
- Verklaringen (Developer Program Policies, US export laws) aanvinken.

## 2. Ondertekening: Play App Signing aan
- Play App Signing laten aanstaan (standaard). Google bewaart de app-signing key.
- Maak een eigen upload-keystore (buiten git, bijvoorbeeld in `keystore.properties` zoals in de build staat) en bewaar die veilig met back-up.
- Verlies je de upload-key: dan kan via Play-support een reset, maar dat kost tijd.
- F-Droid ondertekent zijn eigen builds. Play- en F-Droid-versies updaten daarom niet over elkaar heen (staat in de store-tekst).

## 3. AAB bouwen en uploaden naar closed test
- Bouw: `cd android && JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew bundleRelease` (versionCode 14, versionName 2.1.0).
- Testen > Closed testing > Track aanmaken (bijvoorbeeld "Gezinstest") > Nieuwe release > AAB uploaden.
- Releasenotities komen uit `fastlane/metadata/android/nl-NL/changelogs/14.txt` (kopieer de tekst, max 500 tekens).

## 4. Minimaal 12 testers
- Testers > E-maillijst aanmaken (of Google-groep) met minimaal 12 Gmail/Google-accounts. Voeg er liever 15 tot 20 toe: afhakers tellen niet mee.
- Deel de opt-in-link met testers; zij moeten via die link meedoen en de app installeren via Play.
- De 14 dagen lopen door zolang het aantal testers >= 12 blijft. Houd de teller in Play Console in de gaten.
- Vraag testers de app echt te openen (niet alleen installeren) en feedback te geven via "Mis je iets?".

## 5. Gegevensbeveiliging invullen
- Beleid > App-content > Gegevensbeveiliging. Antwoorden staan in `docs/release/play-data-safety.md`.

## 6. Privacybeleid-URL
- Beleid > App-content > Privacybeleid: `https://openschoolcloud.nl/juridisch/privacy` (dezelfde URL als in de app-instellingen; `/privacy` stuurt hier ook naartoe)
- Verwijder-URL (bij Gegevensbeveiliging): `https://openschoolcloud.nl/account-verwijderen`
- De website is nog niet gedeployd (wacht op akkoord van Nick). Controleer NA de deploy en VOOR je de URL's in Play invult:
  - `curl -IL https://openschoolcloud.nl/juridisch/privacy` moet eindigen op 200
  - `curl -IL https://openschoolcloud.nl/account-verwijderen` moet eindigen op 200 (via 308 naar /juridisch/account-verwijderen)

## 7. Content rating
- Beleid > App-content > Beoordeling van de inhoud: vragenlijst invullen. Verwacht: geen geweld, geen seksuele inhoud, geen gokken, geen door gebruikers gegenereerde content die gedeeld wordt met anderen.
- Categorie: hulpprogramma's / productiviteit (agenda).

## 8. Doelgroep en inhoud
- Beleid > App-content > Doelgroep: kies de leeftijdsgroepen.
- Voorstel: NIET primair kinderen jonger dan 13. Het is een ouder-/gezinsapp: ouders beheren de agenda, kinderen staan als profiel in de agenda. Kies dus 18+ en eventueel 13 tot 17, en geen groepen onder 13.
- CHECK MET NICK: bij een gezinsapp met kindprofielen kan Play toch Families Policy toepassen. Bevestig de keuze voordat je indient. Kiest Nick wel voor kinderen onder 13, dan gelden extra eisen (Families Policy, geen advertentie-SDK's, strengere privacyverklaring).
- Advertenties: Nee. Overheidsapp: Nee. Nieuws-app: Nee. COVID-19/gezondheid: Nee.

## 9. Overige app-content
- Winkelvermelding: korte en volledige beschrijving, titel, screenshots en icoon komen uit `fastlane/metadata/android/{nl-NL,en-US}/`.
  - Telefoonscreenshots: 4 stuks, 1080x2400 (let op: gemaakt met de debug-build, zie rapport).
  - App-icoon: `images/icon.png` (512x512).
  - Feature graphic (1024x500): nog NIET gemaakt, is verplicht voor de vermelding.
- Contactgegevens: e-mail (support@openschoolcloud.nl), website https://openschoolcloud.nl.
- Doelgroepbeleid, Gezondheidsapps, Financiële functies: allemaal "Nee/niet van toepassing".
- Toegang tot app: alles zonder account bruikbaar, dus geen inloggegevens voor reviewers nodig. Het optionele OSC-account hoeft niet getest te worden.

## 10. Na 14 dagen: productie aanvragen
- Dashboard > "Toegang tot productie aanvragen": vragen over de test beantwoorden (feedback verwerkt, wat is verbeterd).
- Na goedkeuring: Productie > Nieuwe release > dezelfde of nieuwere AAB > landen kiezen (Nederland, België, rest van de EU/wereld) > uitrollen.
- Daarna: F-Droid-merge-request indienen met `docs/release/fdroid/nl.openschoolcloud.calendar.yml` (fdroiddata, branch met alleen dit bestand). De tag `v2.1.0` bestaat nog niet; die wordt bij de release gemaakt (op de release-commit) en moet op GitHub staan voordat je de merge-request indient.

## Open punten voor Nick
- Doelgroep (punt 8) bevestigen.
- Upload-keystore aanmaken en veilig bewaren.
- Feature graphic 1024x500 maken.
- Website deployen (privacy- en verwijderpagina) voor de privacy-URL.
