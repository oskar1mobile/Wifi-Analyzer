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

- Dokumentierter Release: [WLAN-Analyzer 0.1.0](https://github.com/oskar1mobile/Wifi-Analyzer/releases/tag/v0.1.0).
- Veröffentlichung: 16. November 2026.
- Die Funktionsliste basiert auf der Release-Beschreibung und ist keine unabhängige Prüfung der APK.
- Zum Zeitpunkt dieser Dokumentation lag auf `main` nur die README; eine Anleitung zum Selbstbauen wird daher hier noch nicht angegeben.
- Aussagen zu Telemetrie, Datenspeicherung oder Netzwerkübertragungen werden ohne Prüfung des passenden Quellcodes nicht zugesichert.

## Fehler melden

[Ein Issue erstellen](https://github.com/oskar1mobile/Wifi-Analyzer/issues) und möglichst folgende Angaben ergänzen:

- App-Version, Gerätemodell und Android-Version.
- Schritte zum Reproduzieren.
- Erwartetes und tatsächliches Verhalten.
- Fehlermeldung oder Screenshot, soweit vorhanden.

Vor dem Teilen WLAN-Namen, BSSIDs/MAC-Adressen, IP-Adressen und andere persönliche Angaben bei Bedarf unkenntlich machen. Niemals WLAN-Passwörter oder Zugangsdaten veröffentlichen.

## Lizenz

Diese Dokumentationsänderung legt keine Softwarelizenz fest. Aus der Verfügbarkeit des Repositorys oder der APK ergibt sich hier keine ausdrückliche Erlaubnis zur Weiterverwendung oder Weiterverbreitung.
