# Versionsübersicht

## 0.2.0 – 18. November 2026

Veröffentlichung als `WLAN-Analyzer.apk` unter dem Tag `v0.2.0`.

- Neuer Dialog „Über die App“ im Menü: Version, Build-Typ, Git-Commit, Signatur sowie Android-Version und Gerät; per „Kopieren“ für Fehlermeldungen in die Zwischenablage.
- Release-Signing mit eigenem Keystore (Umgebungsvariablen `KS_PASS`/`KS_FILE`, GitHub-Secrets `KS_PASS`/`KEYSTORE_BASE64`).

Hinweis: Ab dieser Version wird mit dem eigenen Release-Schlüssel signiert. Version 0.1.0 war mit dem Debug-Schlüssel signiert und muss vor der Installation von 0.2.0 einmal deinstalliert werden.

## 0.1.0 – 16. November 2026

Veröffentlichung als `WLAN-Analyzer.apk` unter dem Tag `v0.1.0`.

### Funktionen laut Release-Beschreibung

- WLAN-Netzwerkliste mit Signalstärke, Kanal, Kanalbreite und Verschlüsselung.
- Kanaldiagramme für 2,4, 5 und 6 GHz.
- Interferenz-Score und Kanalempfehlung.
- Analyse der eigenen Verbindung und Sicherheitswarnungen.
- Auto-Scan mit vorgesehenem 30-Sekunden-Intervall.
- Demo-Modus.

Diese Übersicht dokumentiert die Release-Angaben; sie ersetzt keinen Funktions- oder Sicherheitstest.
