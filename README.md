# Vereinskasse

Einfache Registrierkassen-App für Android – gedacht für Vereine bei Festen und Veranstaltungen.
Keine Rechnungen, kein Finanzamt, keine Cloud: Alle Daten bleiben auf dem Gerät.

## Funktionen

- **Mehrere Vereine** mit eigenen Artikelgruppen, Artikeln und Veranstaltungen
- **Artikelgruppen** mit Farben (z. B. Getränke, Essen, Kuchen) – Reihenfolge per Drag & Drop
- **Artikel** mit Preis und optionalem Pfand, ausblendbar (z. B. ausverkauft), sortierbar
- **Kasse** mit großen, farbigen Kacheln – auf dem Tablet mit Bestellung daneben
  - Pfand optional zubuchen (Schalter „Pfand: an/aus“, je Zeile umschaltbar) und Pfand-Rückgabe
  - Helferbuchungen (0 €, werden aber gezählt)
  - Letzte Bestellung wiederholen
  - Bezahlen mit Schnelltasten (Passend, 20 €, 50 € …) und großer Rückgeld-Anzeige
  - Letzte Bestellung stornieren
- **Auswertung** je Veranstaltung: Umsatz, Stückzahlen, Pfand, Helfer, Stornos
  - Auswahl eines Festtags (Tageswechsel um 6:00 Uhr – nach Mitternacht zählt noch zum Vortag)
  - Verkaufsverlauf auf Viertelstundenbasis: Hochlauf (aufsummiert) oder Stück pro Viertelstunde,
    bis zu 8 Artikel wählbar (Top 5, Alle, je Gruppe), Helferbuchungen ein-/ausblendbar,
    Antippen zeigt die Werte einer Viertelstunde, Grafik als Bild per WhatsApp teilen
  - Kassenstand: Anfangsbestand (Wechselgeld), Soll, gezählter Ist-Bestand, Differenz
  - Per WhatsApp als Text teilen
  - Mehrere Kassen zusammenführen (Abschluss-Datei von anderen Geräten importieren).
    Ab Version 1.1 enthält die Datei alle Bestellungen mit Uhrzeit, damit Tagesauswahl und Verlauf
    auch die anderen Kassen umfassen.
- **Artikel als CSV für Excel** exportieren und importieren (im Verein über ⋮):
  eine Zeile pro Artikel mit den Spalten `Gruppe;Artikel;Preis;Pfand;Sichtbar;Farbe`.
  Reihenfolge der Zeilen = Reihenfolge an der Kasse. Der Import erkennt Strichpunkt/Komma/Tab,
  UTF-8 und Windows-Kodierung, zeigt fehlerhafte Zeilen mit Zeilennummer und fragt: ergänzen oder ersetzen.
  Farben als Name (blau, rot, grün, orange, lila, türkis, gelb, braun, pink, grau, indigo, hellgrün) oder `#RRGGBB`.
- **Konfiguration per WhatsApp** exportieren und importieren – Gruppen, Artikel und Veranstaltungen
  (ohne Umsätze). Beim Import wird gefragt: ergänzen, ersetzen oder als neuen Verein anlegen.
  Vorhandene Veranstaltungen (gleicher Name) bleiben unangetastet, neue werden angelegt; die im
  Absender aktive Veranstaltung wird auch beim Empfänger aktiv. So haben alle Kassen dieselbe Veranstaltung.
- **Logo und Grundfarbe je Verein** (im Verein unter „Logo & Farbe“): Logo aus der Galerie
  (automatisch auf max. 512 px verkleinert), Grundfarbe aus Palette, als Farbcode oder aus dem Logo
  übernommen. Die Farbe färbt Kopfzeile, Knöpfe und Auswahl (hell und dunkel, mit lesbarem Kontrast);
  Gruppenfarben, Diagrammfarben und Signalfarben bleiben unverändert. Logo und Farbe werden beim
  Teilen an andere Handys und in der Datensicherung mitgenommen (nicht in CSV).
- **Datensicherung** (in der Vereinsliste): alle Daten des Geräts inkl. Bestellungen in eine Datei;
  Wiederherstellen ersetzt nach Rückfrage alle Daten (für Handywechsel oder Neuinstallation)

## Web-App für iPhone (und jeden Browser)

Dieselbe App läuft auch als Web-App: **https://gernot-st.github.io/vereinskasse/**

**iPhone (ab iOS 18.2):**
1. Link in **Safari** öffnen (einmal mit Internet).
2. Teilen-Symbol → **„Zum Home-Bildschirm“**.
3. Ab dann über das Symbol starten – die App läuft **komplett offline**. Updates kommen automatisch, sobald wieder Internet da ist.

Unterschiede zur Android-App:
- Die Daten liegen im Browser-Speicher. iOS kann ihn bei Speichermangel löschen –
  **nach jeder Veranstaltung eine Datensicherung erstellen** (Vereinsliste → Datensicherung).
- Dateien aus WhatsApp lassen sich nicht direkt öffnen: Datei in „Dateien“ sichern, dann in der App „Importieren“.
- Teilen (WhatsApp usw.) läuft über das Teilen-Menü von iOS.

Alle Dateien (Konfiguration, Abschluss, CSV, Sicherung) sind zwischen Android und Web austauschbar.

## App installieren

1. Auf GitHub unter **Releases** die neueste `Vereinskasse-….apk` auf dem Handy herunterladen.
2. Datei öffnen. Beim ersten Mal fragt Android, ob der Browser/Dateimanager Apps installieren darf → erlauben.
3. Updates: neue APK genauso installieren – die Daten bleiben erhalten
   (vorausgesetzt, der Signaturschlüssel ist eingerichtet, siehe unten).

Jeder Push auf `main` baut automatisch eine neue APK (GitHub Actions → „APK bauen“).

## Signaturschlüssel einrichten (einmalig, wichtig für Updates)

Android installiert ein Update nur, wenn es mit demselben Schlüssel signiert ist wie die installierte Version.
Ohne hinterlegten Schlüssel signiert GitHub jede APK mit einem anderen Test-Schlüssel – dann muss die App
vor einem Update deinstalliert werden, und die Daten gehen verloren.

1. Schlüssel erzeugen (auf einem Rechner mit Java):
   ```bash
   keytool -genkeypair -keystore vereinskasse.jks -alias vereinskasse -keyalg RSA -keysize 2048 -validity 20000 -dname "CN=Vereinskasse"
   ```
2. Auf GitHub: *Settings → Secrets and variables → Actions → New repository secret*:
   - `SIGNING_KEYSTORE_BASE64` – Inhalt von `base64 -w0 vereinskasse.jks`
   - `SIGNING_STORE_PASSWORD` – das Passwort
   - `SIGNING_KEY_PASSWORD` – dasselbe Passwort
   - `SIGNING_KEY_ALIAS` – `vereinskasse`
3. Die Datei `vereinskasse.jks` und das Passwort gut aufheben (nicht ins Repository!).

## Entwicklung

Technik: Kotlin Multiplatform + Compose Multiplatform. Fast der gesamte Code (Logik und Oberfläche)
liegt in `composeApp/src/commonMain` und ist plattformunabhängig.

| Ordner | Inhalt |
|---|---|
| `commonMain/.../model` | Datenmodell (Vereine, Artikel, Bestellungen …) |
| `commonMain/.../logik` | Kassenlogik, Auswertung, Import/Export, Speichern |
| `commonMain/.../ui` | Alle Bildschirme |
| `androidMain` | Android-Start, Speichern, Teilen, Datei öffnen |
| `desktopMain` | Desktop-Version zum Ausprobieren am PC |
| `wasmJsMain` | Web-App: Start, Speichern (IndexedDB), Teilen, Dateiauswahl; `resources/` mit index.html, Service Worker, Manifest |

```bash
./gradlew :composeApp:assembleDebug      # Android-APK (braucht Android-SDK)
./gradlew :composeApp:run                # Desktop-Version starten
./gradlew :composeApp:desktopTest        # Tests + Bildschirmfotos nach composeApp/build/bildschirmfotos
./gradlew :composeApp:webApp             # Web-App nach composeApp/build/dist/wasmJs/productionExecutable
./gradlew :composeApp:wasmJsBrowserDevelopmentRun   # Web-App lokal im Browser ausprobieren
```

### Einmalig: GitHub Pages einschalten

Damit die Web-App veröffentlicht wird: *Settings → Pages → Build and deployment → Source: **GitHub Actions***.
Danach baut jeder Push auf `main` die Web-App neu (Job „web“ im Workflow „APK bauen“).
