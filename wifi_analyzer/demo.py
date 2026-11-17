"""Beispieldaten für den Demo-Modus (ohne echte WLAN-Hardware)."""

from .models import Network


def demo_networks() -> list[Network]:
    data = [
        ("ZuHause-5G", "A4:2B:B0:11:22:33", -48, 36, "WPA2/WPA3", 80, True),
        ("ZuHause", "A4:2B:B0:11:22:34", -45, 6, "WPA2/WPA3", 20, False),
        ("FRITZ!Box 7590 XY", "3C:A6:2F:01:02:03", -62, 6, "WPA2", 20, False),
        ("Vodafone-4A2C", "90:5C:44:AA:BB:CC", -71, 11, "WPA2", 20, False),
        ("o2-WLAN42", "D4:21:22:10:20:30", -66, 1, "WPA2", 20, False),
        ("Telekom_FON", "D4:21:22:10:20:31", -67, 1, "Offen", 20, False),
        ("Nachbar", "00:1A:2B:3C:4D:5E", -58, 4, "WPA2", 40, False),
        ("DIRECT-HP-Drucker", "F8:B4:6A:11:11:11", -55, 6, "WPA2", 20, False),
        ("Altes-Netz", "00:14:BF:00:00:01", -83, 9, "WEP", 20, False),
        ("", "AE:12:34:56:78:9A", -77, 11, "WPA2", 20, False),
        ("Vodafone-4A2C-5G", "90:5C:44:AA:BB:CD", -74, 44, "WPA2", 80, False),
        ("FRITZ!Box 7590 XY", "3C:A6:2F:01:02:04", -69, 100, "WPA3", 80, False),
        ("MeshNet", "B0:BE:76:12:34:56", -80, 36, "WPA2", 40, False),
        ("Cafe-Gast", "C8:3A:35:00:11:22", -86, 13, "Offen", 20, False),
    ]
    return [
        Network(ssid=s, bssid=b, signal_dbm=sig, channel=ch, security=sec, width_mhz=w, connected=c)
        for s, b, sig, ch, sec, w, c in data
    ]
