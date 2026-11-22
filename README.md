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
- **Konfiguration per WhatsApp** exportieren und importieren – beim Import wird gefragt:
  ergänzen, ersetzen oder als neuen Verein anlegen

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

```bash
./gradlew :composeApp:assembleDebug      # Android-APK (braucht Android-SDK)
./gradlew :composeApp:run                # Desktop-Version starten
./gradlew :composeApp:desktopTest        # Tests + Bildschirmfotos nach composeApp/build/bildschirmfotos
```

### Später: iOS

Für iOS werden in `composeApp/build.gradle.kts` die Ziele `iosArm64()` und `iosSimulatorArm64()` ergänzt,
eine kleine `iosMain` (Speichern, Teilen, Dateiauswahl – analog zu `MainActivity.kt`) und ein Xcode-Projekt.
Dafür werden ein Mac und ein Apple-Developer-Account benötigt.
