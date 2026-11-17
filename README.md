# WLAN-Analyzer für Android

Android-App zur Übersicht und Analyse von WLAN-Netzen: Signalstärke, Kanalbelegung und Informationen zur eigenen Verbindung.

## APK herunterladen

[Zur aktuellen Veröffentlichung](https://github.com/oskar1mobile/Wifi-Analyzer/releases/latest)

Unter **Assets** die Datei `WLAN-Analyzer.apk` auswählen. Die angebotenen Quellcode-Archive sind nicht die installierbare App. Bei einem privaten Repository ist eine Anmeldung mit einem berechtigten GitHub-Konto erforderlich.

## Funktionen

Die Release-Beschreibung von Version 0.1.0 nennt folgende Funktionen:

- Netzwerkliste mit Signalstärke, Kanal, Kanalbreite und Verschlüsselung.
- Kanaldiagramm für 2,4-, 5- und 6-GHz-Netze.
- Interferenz-Score und Kanalempfehlung als Orientierung bei der Kanalwahl.
- Analyse der eigenen WLAN-Verbindung und Sicherheitswarnungen.
- Auto-Scan mit einem vorgesehenen Intervall von 30 Sekunden.
- Demo-Modus zum Erkunden der Oberfläche.

Welche Netze und Messwerte verfügbar sind, hängt vom Android-Gerät, den erteilten Berechtigungen und den Scan-Ergebnissen ab. Ein Kanalvorschlag ist keine Garantie für eine bessere Verbindung. Sicherheitswarnungen ersetzen keine umfassende Sicherheitsprüfung.

## Installation

1. Die Release-Seite auf dem Android-Handy öffnen.
2. Unter **Assets** `WLAN-Analyzer.apk` herunterladen.
3. Die heruntergeladene APK über die Dateien-App öffnen.
4. Falls Android nachfragt, der verwendeten Browser- oder Dateien-App die Installation aus dieser Quelle erlauben. Nur fortfahren, wenn die APK aus dem erwarteten Repository stammt.
5. Installation abschließen und WLAN-Analyzer starten.
6. Für echte WLAN-Scans den angefragten Standortzugriff erteilen; wenn angeboten, den genauen Standort erlauben. WLAN und Standortdienste einschalten.

Die Erlaubnis zum Installieren aus dieser Quelle kann nach der Installation wieder entzogen werden.

## Nutzung

- In der Netzwerkliste die gefundenen Zugangspunkte vergleichen.
- Im Kanaldiagramm die Belegung des gewünschten Frequenzbands ansehen.
- Die Verbindungsanalyse für Informationen zum eigenen WLAN öffnen.
- Den Demo-Modus verwenden, um die Darstellung ohne echte Messung zu erkunden. Demo-Daten beschreiben nicht die tatsächliche WLAN-Umgebung.

## Häufige Fragen

### Es werden keine WLAN-Netze angezeigt

WLAN, Standortdienste und die Standortberechtigung der App prüfen. Anschließend erneut scannen. Falls die Berechtigung dauerhaft abgelehnt wurde, kann sie in den Android-App-Einstellungen angepasst werden.

### Die Messwerte aktualisieren sich nicht sofort

Android begrenzt WLAN-Scans. Das vorgesehene Auto-Scan-Intervall garantiert deshalb keine neuen Messwerte alle 30 Sekunden.

### Es werden keine 6-GHz-Netze angezeigt

Die Anzeige setzt voraus, dass das Gerät entsprechende Netze erfassen kann und passende Netze in Reichweite sind.

### Wo finde ich die APK?

Unter [Releases](https://github.com/oskar1mobile/Wifi-Analyzer/releases) den gewünschten Release öffnen und den Bereich **Assets** aufklappen. `WLAN-Analyzer.apk` herunterladen, nicht `Source code (zip)` oder `Source code (tar.gz)`.

## Projektstand

- Aktueller Release: [WLAN-Analyzer 0.1.0](https://github.com/oskar1mobile/Wifi-Analyzer/releases/tag/v0.1.0), veröffentlicht am 16. November 2026.
- Der Quellcode der App liegt im Ordner [`android/`](android) (Kotlin, Jetpack Compose, ab Android 8.0).
- Die App fordert **keine Internet-Berechtigung** an (siehe `android/app/src/main/AndroidManifest.xml`) und kann daher keine Messdaten übertragen. Scan-Ergebnisse werden nur im Arbeitsspeicher gehalten und nicht gespeichert.
- Die Analyse-Logik ist mit Unit-Tests abgedeckt; die App selbst wird bei jedem Push von GitHub Actions gebaut. Ein Test auf echten Geräten steht noch aus.

## Wie die Kanalbewertung funktioniert

Für jeden Kanal wird geprüft, welche gefundenen Netze seinen Frequenzbereich überlappen – inklusive gebündelter 40/80/160-MHz-Kanäle. Jedes Netz trägt mit einem Gewicht von 0 (−90 dBm) bis 1 (−30 dBm) bei. Netze auf **demselben** Kanal teilen sich das Medium (Faktor 1,0); **teilweise** überlappende Netze erzeugen echtes Rauschen und zählen stärker (Faktor 1,5 × Überlappungsanteil). Das eigene, verbundene Netz wird nicht mitgezählt. Im 2,4-GHz-Band werden nur die überlappungsfreien Kanäle 1, 6 und 11 empfohlen; im 5-GHz-Band werden Nicht-DFS-Kanäle leicht bevorzugt.

| Score | Bewertung |
|-------|-----------|
| < 0,5 | frei      |
| < 1,5 | gering    |
| < 3,0 | mittel    |
| ≥ 3,0 | hoch      |

## Selbst bauen

```bash
cd android
./gradlew :app:assembleRelease   # benötigt Android SDK (ANDROID_HOME oder local.properties)
./gradlew :core:test             # Analyse-Logik testen – funktioniert auch ohne Android SDK
```

Alternativ den Ordner `android/` in Android Studio öffnen.

### Release-Signing

Die Release-APK wird mit einem eigenen Schlüssel signiert, sobald diese Umgebungsvariablen gesetzt sind; Keystore und Passwort gehören **nie** ins Repository (`*.keystore`, `*.jks` und `*.p12` sind in `.gitignore`).

| Variable   | Bedeutung                                                    |
|------------|--------------------------------------------------------------|
| `KS_PASS`  | Keystore-Passwort (bei PKCS12 zugleich das Schlüssel-Passwort) |
| `KS_FILE`  | Pfad zum Keystore (Standard: `android/release.keystore`)     |
| `KS_ALIAS` | Schlüssel-Alias (Standard: `wlan-analyzer`)                  |

Fehlt `KS_PASS` oder der Keystore, wird mit dem Debug-Schlüssel signiert und eine Warnung ausgegeben. Mit `-PrequireReleaseSigning=true` bricht der Build stattdessen ab.

**Keystore einmalig erzeugen (Windows PowerShell):**

```powershell
$env:KS_PASS = Read-Host "Keystore-Passwort"   # oder auf eigenem Weg setzen
keytool -genkeypair -v -keystore wlan-analyzer-release.keystore -storetype PKCS12 `
  -alias wlan-analyzer -keyalg RSA -keysize 4096 -validity 10000 `
  -storepass:env KS_PASS -dname "CN=WLAN-Analyzer"
```

`keytool` liegt jedem JDK bei, z. B. unter `C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe`. Keystore und Passwort sicher aufbewahren (Passwortmanager plus Backup): Geht der Schlüssel verloren, lassen sich installierte Apps nicht mehr aktualisieren.

**Lokal signiert bauen (benötigt Android SDK):**

```powershell
cd android
$env:KS_FILE = "C:\Pfad\zu\wlan-analyzer-release.keystore"
.\gradlew.bat :app:assembleRelease -PrequireReleaseSigning=true
# Ergebnis: android\app\build\outputs\apk\release\app-release.apk
```

**Signiert in GitHub Actions bauen:** Unter *Settings → Secrets and variables → Actions* zwei Repository-Secrets anlegen:

- `KEYSTORE_BASE64` – Inhalt des Keystores als Base64:
  `[Convert]::ToBase64String([IO.File]::ReadAllBytes("wlan-analyzer-release.keystore")) | Set-Clipboard`
- `KS_PASS` – das Keystore-Passwort

Danach signiert jeder Build mit diesem Schlüssel; Releases werden ohne die Secrets gar nicht erst gebaut. Der Schritt „Signatur prüfen“ zeigt den SHA-256-Fingerabdruck des Zertifikats im Log.

> Version 0.1.0 wurde noch mit dem Debug-Schlüssel signiert. Vor der Installation einer mit dem neuen Schlüssel signierten Version muss die alte App einmal deinstalliert werden.

### Neues Release erstellen

1. `versionName` (und `versionCode`) in `android/app/build.gradle.kts` erhöhen und `CHANGELOG.md` ergänzen.
2. Auf GitHub **Actions → Android-APK → Run workflow** öffnen, Haken bei **release** setzen und starten.
3. Der Workflow baut die APK und legt Tag `v<versionName>` samt Release an. Alternativ entsteht ein Release auch durch Pushen eines Tags `v*`.

## Python-Kommandozeilenwerkzeug

Zusätzlich enthält das Repository unter [`wifi_analyzer/`](wifi_analyzer) ein Kommandozeilenwerkzeug für Linux (`nmcli`/`iw`), Windows (`netsh`) und macOS (`system_profiler`) mit derselben Analyse. Es benötigt nur Python ≥ 3.9.

```bash
python -m wifi_analyzer                       # scannen und im Terminal auswerten
python -m wifi_analyzer --html bericht.html   # zusätzlich HTML-Report mit Kanaldiagramm
python -m wifi_analyzer --json -              # nur JSON auf stdout
python -m wifi_analyzer --band 5 --watch 10   # nur 5 GHz, alle 10 s neu scannen
python -m wifi_analyzer --demo                # Beispieldaten ohne WLAN-Hardware
```

Unter Linux benötigt `iw scan` root-Rechte; unter Windows 11 und aktuellem macOS muss das Terminal ggf. Standortzugriff haben, sonst werden Netzwerknamen ausgeblendet. Tests: `pip install pytest && python -m pytest`.

## Fehler melden

[Ein Issue erstellen](https://github.com/oskar1mobile/Wifi-Analyzer/issues) und möglichst folgende Angaben ergänzen:

- App-Version, Gerätemodell und Android-Version.
- Schritte zum Reproduzieren.
- Erwartetes und tatsächliches Verhalten.
- Fehlermeldung oder Screenshot, soweit vorhanden.

Vor dem Teilen WLAN-Namen, BSSIDs/MAC-Adressen, IP-Adressen und andere persönliche Angaben bei Bedarf unkenntlich machen. Niemals WLAN-Passwörter oder Zugangsdaten veröffentlichen.

## Lizenz

Diese Dokumentationsänderung legt keine Softwarelizenz fest. Aus der Verfügbarkeit des Repositorys oder der APK ergibt sich hier keine ausdrückliche Erlaubnis zur Weiterverwendung oder Weiterverbreitung.
