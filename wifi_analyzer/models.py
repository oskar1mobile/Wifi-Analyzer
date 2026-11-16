"""Datenmodelle für gefundene WLAN-Netzwerke."""

from __future__ import annotations

from dataclasses import asdict, dataclass, field


def channel_to_frequency(channel: int) -> int:
    """Liefert die Mittenfrequenz (MHz) zu einem Kanal."""
    if channel == 14:
        return 2484
    if 1 <= channel <= 13:
        return 2407 + channel * 5
    if 32 <= channel <= 177:
        return 5000 + channel * 5
    return 0


def frequency_to_channel(freq: int) -> int:
    """Liefert den Kanal zu einer Frequenz (MHz)."""
    if freq == 2484:
        return 14
    if 2412 <= freq <= 2472:
        return (freq - 2407) // 5
    if 5150 <= freq <= 5895:
        return (freq - 5000) // 5
    if 5955 <= freq <= 7115:  # 6 GHz
        return (freq - 5950) // 5
    return 0


def band_for_frequency(freq: int) -> str:
    if 2400 <= freq < 2500:
        return "2.4 GHz"
    if 5000 <= freq < 5925:
        return "5 GHz"
    if 5925 <= freq <= 7125:
        return "6 GHz"
    return "unbekannt"


def quality_to_dbm(quality: int) -> int:
    """Wandelt Signalqualität in Prozent (0–100) näherungsweise in dBm um."""
    quality = max(0, min(100, quality))
    return int(round(quality / 2 - 100))


def dbm_to_quality(dbm: int) -> int:
    return max(0, min(100, 2 * (dbm + 100)))


@dataclass
class Network:
    ssid: str
    bssid: str
    signal_dbm: int
    channel: int
    frequency: int = 0
    security: str = "Offen"
    width_mhz: int = 20
    connected: bool = False
    extra: dict = field(default_factory=dict)

    def __post_init__(self) -> None:
        if not self.frequency and self.channel:
            self.frequency = channel_to_frequency(self.channel)
        if not self.channel and self.frequency:
            self.channel = frequency_to_channel(self.frequency)

    @property
    def band(self) -> str:
        return band_for_frequency(self.frequency)

    @property
    def quality(self) -> int:
        return dbm_to_quality(self.signal_dbm)

    @property
    def display_ssid(self) -> str:
        return self.ssid if self.ssid else "<versteckt>"

    def to_dict(self) -> dict:
        data = asdict(self)
        data["band"] = self.band
        data["quality"] = self.quality
        return data
