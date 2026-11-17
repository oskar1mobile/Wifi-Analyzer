from wifi_analyzer.scanner import parse_iw, parse_netsh, parse_nmcli, parse_system_profiler

NMCLI = r"""*:MeinWLAN:AA\:BB\:CC\:DD\:EE\:01:6:2437 MHz:80:WPA2:20 MHz
 :Nachbar 5G:AA\:BB\:CC\:DD\:EE\:02:36:5180 MHz:60:WPA2 WPA3:80 MHz
 ::AA\:BB\:CC\:DD\:EE\:03:11:2462 MHz:30:--:20 MHz
"""


def test_parse_nmcli():
    nets = parse_nmcli(NMCLI)
    assert len(nets) == 3
    a, b, c = nets
    assert a.ssid == "MeinWLAN" and a.connected and a.bssid == "AA:BB:CC:DD:EE:01"
    assert a.channel == 6 and a.frequency == 2437 and a.signal_dbm == -60
    assert b.band == "5 GHz" and b.width_mhz == 80 and b.security == "WPA2/WPA3"
    assert c.ssid == "" and c.security == "Offen" and c.display_ssid == "<versteckt>"


IW = """BSS 11:22:33:44:55:66(on wlan0) -- associated
\tfreq: 5180
\tsignal: -52.00 dBm
\tSSID: Heim
\tRSN:\t * Version: 1
\t\t * Authentication suites: PSK SAE
\tHT operation:
\t\t * primary channel: 36
\tVHT operation:
\t\t * channel width: 1 (80 MHz)
BSS 11:22:33:44:55:77(on wlan0)
\tfreq: 2412
\tsignal: -80.00 dBm
\tSSID: Offen
\tDS Parameter set: channel 1
"""


def test_parse_iw():
    nets = parse_iw(IW)
    assert len(nets) == 2
    h, o = nets
    assert h.connected and h.channel == 36 and h.signal_dbm == -52
    assert h.width_mhz == 80 and h.security == "WPA2/WPA3"
    assert not o.connected and o.channel == 1 and o.security == "Offen"


NETSH = """
SSID 1 : Buero
    Network type            : Infrastructure
    Authentication          : WPA2-Personal
    Encryption              : CCMP
    BSSID 1                 : aa:bb:cc:00:00:01
         Signal             : 90%
         Radio type         : 802.11ac
         Channel            : 44
    BSSID 2                 : aa:bb:cc:00:00:02
         Signal             : 40%
         Channel            : 6

SSID 2 : Gast
    Authentifizierung       : Offen
    BSSID 1                 : aa:bb:cc:00:00:03
         Signal             : 20%
         Kanal              : 11
"""


def test_parse_netsh():
    nets = parse_netsh(NETSH)
    assert [n.ssid for n in nets] == ["Buero", "Buero", "Gast"]
    assert nets[0].channel == 44 and nets[0].band == "5 GHz" and nets[0].signal_dbm == -55
    assert nets[1].channel == 6 and nets[1].band == "2.4 GHz"
    assert nets[2].security == "Offen" and nets[2].channel == 11


PROFILER = """{"SPAirPortDataType": [{"spairport_airport_interfaces": [{
  "_name": "en0",
  "spairport_current_network_information": {"_name": "Home", "spairport_network_channel": "149 (5GHz, 80MHz)",
     "spairport_security_mode": "spairport_security_mode_wpa3_personal", "spairport_signal_noise": "-50 dBm / -92 dBm"},
  "spairport_airport_other_local_wireless_networks": [
     {"_name": "Other", "spairport_network_channel": "1 (2GHz, 20MHz)",
      "spairport_security_mode": "spairport_security_mode_wpa2_personal", "spairport_signal_noise": "-70 dBm / -92 dBm"}
  ]}]}]}"""


def test_parse_system_profiler():
    nets = parse_system_profiler(PROFILER)
    other, home = nets
    assert home.connected and home.channel == 149 and home.width_mhz == 80 and home.security == "WPA3"
    assert other.channel == 1 and other.signal_dbm == -70 and other.security == "WPA2"
