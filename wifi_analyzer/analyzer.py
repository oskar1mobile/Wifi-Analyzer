"""Auswertung: Kanalauslastung, Interferenz, Sicherheit und Empfehlungen."""

from __future__ import annotations

from collections import defaultdict
from dataclasses import dataclass, field

from .models import Network

CHANNELS_24 = list(range(1, 14))
NON_OVERLAPPING_24 = [1, 6, 11]
# Gängige 5-GHz-Kanäle in der EU (UNII-1, UNII-2, UNII-2e)
CHANNELS_5 = [36, 40, 44, 48, 52, 56, 60, 64, 100, 104, 108, 112, 116, 120, 124, 128, 132, 136, 140]
DFS_CHANNELS_5 = set(range(52, 145))

SECURITY_RATING = {
    "WPA3": ("sehr gut", 0),
    "WPA2/WPA3": ("gut", 0),
    "WPA2": ("gut", 1),
    "WPA": ("unsicher", 2),
    "WEP": ("unsicher", 3),
    "Offen": ("keine Verschlüsselung", 3),
}


def signal_label(dbm: int) -> str:
    if dbm >= -50:
        return "Ausgezeichnet"
    if dbm >= -60:
        return "Sehr gut"
    if dbm >= -67:
        return "Gut"
    if dbm >= -75:
        return "Mäßig"
    if dbm >= -85:
        return "Schwach"
    return "Sehr schwach"


def _weight(dbm: int) -> float:
    """Gewichtet ein Netz nach Signalstärke: starke Nachbarn stören mehr.

    -30 dBm → ~1.0, -90 dBm → ~0.0 (linear interpoliert).
    """
    return max(0.0, min(1.0, (dbm + 90) / 60))


def span(net: Network) -> tuple[float, float]:
    """Belegter Frequenzbereich (MHz) eines Netzes.

    Für 2.4 GHz wird mindestens die 22 MHz-Maske angenommen; bei 40 MHz breiten
    Netzen nutzen wir die Mittenfrequenz des Primärkanals als Näherung.
    """
    width = net.width_mhz or 20
    if net.band == "2.4 GHz":
        width = max(22, width)
    elif net.band == "5 GHz" and width >= 40:
        # Gebündelte Kanäle liegen in festen Blöcken (36–40, 36–48, 36–64 …)
        block_start = _bonded_block_start(net.channel, width)
        if block_start is not None:
            low = 5000 + block_start * 5 - 10
            return low, low + width
    half = width / 2
    return net.frequency - half, net.frequency + half


# Erster 20-MHz-Kanal der 5-GHz-Blöcke je Kanalbreite
_BONDED_STARTS = {
    40: [36, 44, 52, 60, 100, 108, 116, 124, 132, 140, 149, 157, 165, 173],
    80: [36, 52, 100, 116, 132, 149, 165],
    160: [36, 100, 149],
}


def _bonded_block_start(channel: int, width: int) -> int | None:
    n_channels = width // 5  # Breite in Kanalnummern
    for start in _BONDED_STARTS.get(width, []):
        if start <= channel < start + n_channels:
            return start
    return None


def _overlap(a: tuple[float, float], b: tuple[float, float]) -> float:
    """Anteil (0..1) der Überlappung zweier Frequenzbereiche relativ zu a."""
    lo, hi = max(a[0], b[0]), min(a[1], b[1])
    if hi <= lo:
        return 0.0
    return (hi - lo) / (a[1] - a[0])


@dataclass
class ChannelScore:
    channel: int
    band: str
    networks_on_channel: int
    interference: float
    dfs: bool = False

    @property
    def rating(self) -> str:
        if self.interference < 0.5:
            return "frei"
        if self.interference < 1.5:
            return "gering"
        if self.interference < 3.0:
            return "mittel"
        return "hoch"


@dataclass
class Finding:
    level: str  # "info" | "warnung" | "kritisch"
    message: str


@dataclass
class AnalysisResult:
    networks: list[Network]
    channels_24: list[ChannelScore]
    channels_5: list[ChannelScore]
    best_24: list[ChannelScore]
    best_5: list[ChannelScore]
    findings: list[Finding] = field(default_factory=list)
    connected: Network | None = None

    @property
    def band_counts(self) -> dict[str, int]:
        counts: dict[str, int] = defaultdict(int)
        for n in self.networks:
            counts[n.band] += 1
        return dict(counts)

    def to_dict(self) -> dict:
        def cs(c: ChannelScore) -> dict:
            return {
                "channel": c.channel,
                "band": c.band,
                "networks": c.networks_on_channel,
                "interference": round(c.interference, 2),
                "rating": c.rating,
                "dfs": c.dfs,
            }

        return {
            "networks": [n.to_dict() for n in self.networks],
            "band_counts": self.band_counts,
            "connected": self.connected.to_dict() if self.connected else None,
            "channels_24": [cs(c) for c in self.channels_24],
            "channels_5": [cs(c) for c in self.channels_5],
            "recommendation_24": [cs(c) for c in self.best_24],
            "recommendation_5": [cs(c) for c in self.best_5],
            "findings": [{"level": f.level, "message": f.message} for f in self.findings],
        }


def channel_interference(
    networks: list[Network], channel: int, band: str, width: int = 20, exclude_bssid: str | None = None
) -> ChannelScore:
    """Berechnet die gewichtete Interferenz, die ein Netz auf ``channel`` erfahren würde."""
    probe = Network(ssid="", bssid="", signal_dbm=0, channel=channel, width_mhz=width)
    if band == "6 GHz":
        probe.frequency = 5950 + channel * 5
    probe_span = span(probe)
    total = 0.0
    same = 0
    for n in networks:
        if n.band != band or (exclude_bssid and n.bssid == exclude_bssid):
            continue
        ov = _overlap(probe_span, span(n))
        if ov <= 0:
            continue
        if n.channel == channel:
            same += 1
            # Gleicher Kanal: Geräte teilen sich das Medium (CSMA/CA) – weniger
            # schädlich als teilweise Überlappung, aber trotzdem Airtime-Verlust.
            total += _weight(n.signal_dbm) * 1.0
        else:
            # Nachbarkanal-Überlappung erzeugt echtes Rauschen → stärker gewichtet.
            total += _weight(n.signal_dbm) * ov * 1.5
    return ChannelScore(channel, band, same, total, dfs=band == "5 GHz" and channel in DFS_CHANNELS_5)


def analyze(networks: list[Network]) -> AnalysisResult:
    networks = sorted(networks, key=lambda n: n.signal_dbm, reverse=True)
    connected = next((n for n in networks if n.connected), None)
    exclude = connected.bssid if connected else None

    ch24 = [channel_interference(networks, c, "2.4 GHz", exclude_bssid=exclude) for c in CHANNELS_24]
    ch5 = [channel_interference(networks, c, "5 GHz", exclude_bssid=exclude) for c in CHANNELS_5]

    best_24 = sorted(
        (c for c in ch24 if c.channel in NON_OVERLAPPING_24),
        key=lambda c: (c.interference, c.channel),
    )
    # Nicht-DFS-Kanäle leicht bevorzugen (kein Radar-bedingtes Umschalten)
    best_5 = sorted(ch5, key=lambda c: (c.interference + (0.25 if c.dfs else 0), c.channel))[:3]

    result = AnalysisResult(networks, ch24, ch5, best_24, best_5, connected=connected)
    result.findings = _findings(result)
    return result


def _findings(r: AnalysisResult) -> list[Finding]:
    out: list[Finding] = []
    if not r.networks:
        return [Finding("warnung", "Keine Netzwerke gefunden.")]

    out.append(
        Finding(
            "info",
            f"{len(r.networks)} Access Points gefunden "
            + ", ".join(f"{b}: {c}" for b, c in sorted(r.band_counts.items())),
        )
    )

    open_nets = [n for n in r.networks if n.security == "Offen"]
    weak_nets = [n for n in r.networks if n.security in ("WEP", "WPA")]
    if open_nets:
        out.append(Finding("warnung", f"{len(open_nets)} unverschlüsselte Netze in Reichweite: "
                           + ", ".join(sorted({n.display_ssid for n in open_nets}))))
    if weak_nets:
        out.append(Finding("warnung", f"{len(weak_nets)} Netze mit veralteter Verschlüsselung (WEP/WPA): "
                           + ", ".join(sorted({n.display_ssid for n in weak_nets}))))

    # Netze, die 2.4 GHz-Kanäle außerhalb von 1/6/11 nutzen, verursachen Überlappung
    odd = [n for n in r.networks if n.band == "2.4 GHz" and n.channel not in NON_OVERLAPPING_24]
    if odd:
        out.append(Finding("info", f"{len(odd)} Netze im 2.4-GHz-Band nutzen überlappende Kanäle "
                           "(nicht 1/6/11)."))

    wide24 = [n for n in r.networks if n.band == "2.4 GHz" and n.width_mhz >= 40]
    if wide24:
        out.append(Finding("info", f"{len(wide24)} Netze nutzen 40 MHz im 2.4-GHz-Band – "
                           "das belegt fast das halbe Band."))

    c = r.connected
    if c:
        label = signal_label(c.signal_dbm)
        level = "info" if c.signal_dbm >= -67 else ("warnung" if c.signal_dbm >= -80 else "kritisch")
        out.append(Finding(level, f"Verbunden mit '{c.display_ssid}' auf Kanal {c.channel} ({c.band}), "
                           f"Signal {c.signal_dbm} dBm ({label})."))
        sec_label, sev = SECURITY_RATING.get(c.security, ("unbekannt", 1))
        if sev >= 2:
            out.append(Finding("kritisch", f"Dein Netz verwendet {c.security} ({sec_label}). "
                               "Umstellen auf WPA2-AES oder besser WPA3 empfohlen."))
        elif c.security == "WPA2":
            out.append(Finding("info", "Dein Netz nutzt WPA2. Falls Router und Geräte es unterstützen, "
                               "ist WPA3 (oder WPA2/WPA3-Mischmodus) sicherer."))

        scores = r.channels_24 if c.band == "2.4 GHz" else r.channels_5 if c.band == "5 GHz" else []
        best = r.best_24 if c.band == "2.4 GHz" else r.best_5 if c.band == "5 GHz" else []
        current = next((s for s in scores if s.channel == c.channel), None)
        if current is None and c.band == "5 GHz":
            current = channel_interference(r.networks, c.channel, "5 GHz", exclude_bssid=c.bssid)
        if current and best:
            top = best[0]
            if top.channel != c.channel and current.interference - top.interference >= 0.5:
                out.append(Finding("warnung", f"Kanal {c.channel} ist {current.rating} belegt "
                                   f"(Score {current.interference:.1f}). Empfehlung: Kanal {top.channel} "
                                   f"(Score {top.interference:.1f})."))
            else:
                out.append(Finding("info", f"Kanal {c.channel} ist eine gute Wahl "
                                   f"(Auslastung: {current.rating})."))
        if c.band == "2.4 GHz" and r.band_counts.get("5 GHz", 0) and c.signal_dbm > -65:
            out.append(Finding("info", "Tipp: Wenn dein Router 5 GHz bietet, ist dort meist mehr Platz "
                               "und höhere Geschwindigkeit möglich."))
    else:
        out.append(Finding("info", "Keine aktive Verbindung erkannt – Empfehlungen gelten allgemein."))

    if r.best_24 and r.band_counts.get("2.4 GHz"):
        out.append(Finding("info", f"Bester 2.4-GHz-Kanal: {r.best_24[0].channel} "
                           f"({r.best_24[0].rating})."))
    if r.best_5 and r.band_counts.get("5 GHz"):
        b = r.best_5[0]
        out.append(Finding("info", f"Bester 5-GHz-Kanal: {b.channel} ({b.rating}"
                           + (", DFS" if b.dfs else "") + ")."))
    return out
