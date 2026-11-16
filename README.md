# WiFi-Analyzer

Kommandozeilen-Tool, das WLAN-Netzwerke in der Umgebung scannt und auswertet:

- **Netzwerkliste** mit SSID, BSSID, Band, Kanal, Kanalbreite, Signal (dBm + Qualität) und Verschlüsselung
- **Kanalauslastung** für 2.4 GHz und 5 GHz – gewichtet nach Signalstärke, inkl. Überlappung
  benachbarter Kanäle und gebündelter 40/80/160-MHz-Blöcke
- **Kanalempfehlung** (2.4 GHz: 1/6/11; 5 GHz: Nicht-DFS-Kanäle werden leicht bevorzugt)
- **Sicherheitsprüfung**: offene Netze, WEP/WPA, Hinweis auf WPA3
- **Bewertung der eigenen Verbindung** (Signalqualität, Kanalwahl, 5-GHz-Tipp)
- Ausgabe im Terminal, als **JSON** oder als eigenständiger **HTML-Report** mit Kanaldiagramm

Keine externen Abhängigkeiten – nur Python ≥ 3.9.

## Unterstützte Systeme

| System  | Werkzeug                                    |
|---------|---------------------------------------------|
| Linux   | `nmcli` (NetworkManager), Fallback `iw`     |
| Windows | `netsh wlan show networks mode=bssid`       |
| macOS   | `system_profiler SPAirPortDataType -json`   |

> Hinweis: `iw scan` benötigt unter Linux root-Rechte. Unter Windows 11 / macOS Sonoma+ muss
> ggf. der Standortzugriff für das Terminal erlaubt sein, sonst werden SSIDs/BSSIDs ausgeblendet.

## Installation

```bash
pip install .
# oder ohne Installation:
python -m wifi_analyzer
```

## Verwendung

```bash
wifi-analyzer                       # scannen und im Terminal auswerten
wifi-analyzer --html bericht.html   # zusätzlich HTML-Report mit Diagramm
wifi-analyzer --json ergebnis.json  # zusätzlich JSON speichern
wifi-analyzer --json -              # nur JSON auf stdout
wifi-analyzer --band 5              # nur 5-GHz-Netze
wifi-analyzer --min-signal -80      # schwache Netze ausblenden
wifi-analyzer --watch 10            # alle 10 s neu scannen
wifi-analyzer --demo                # Beispieldaten, ohne WLAN-Hardware
```

## Wie die Kanalbewertung funktioniert

Für jeden Kanal wird geprüft, welche gefundenen Netze seinen Frequenzbereich überlappen.
Jedes Netz trägt mit einem Gewicht von 0 (−90 dBm) bis 1 (−30 dBm) bei. Netze auf **demselben**
Kanal teilen sich das Medium (Faktor 1,0); **teilweise** überlappende Netze erzeugen echtes
Rauschen und zählen stärker (Faktor 1,5 × Überlappungsanteil). Das eigene, verbundene Netz wird
nicht mitgezählt.

| Score   | Bewertung |
|---------|-----------|
| < 0,5   | frei      |
| < 1,5   | gering    |
| < 3,0   | mittel    |
| ≥ 3,0   | hoch      |

## Tests

```bash
pip install pytest
python -m pytest
```
