"""Plattformabhängige WLAN-Scanner.

Unterstützt:
  * Linux   – ``nmcli`` (NetworkManager), Fallback ``iw``
  * Windows – ``netsh wlan show networks mode=bssid``
  * macOS   – ``system_profiler SPAirPortDataType``
"""

from __future__ import annotations

import json
import platform
import re
import shutil
import subprocess
from typing import Callable

from .models import Network, quality_to_dbm


class ScanError(RuntimeError):
    pass


def _run(cmd: list[str], timeout: int = 30) -> str:
    try:
        result = subprocess.run(
            cmd, capture_output=True, text=True, timeout=timeout, errors="replace"
        )
    except FileNotFoundError as exc:
        raise ScanError(f"Befehl nicht gefunden: {cmd[0]}") from exc
    except subprocess.TimeoutExpired as exc:
        raise ScanError(f"Zeitüberschreitung bei: {' '.join(cmd)}") from exc
    if result.returncode != 0:
        raise ScanError(
            f"'{' '.join(cmd)}' fehlgeschlagen ({result.returncode}): "
            f"{result.stderr.strip() or result.stdout.strip()}"
        )
    return result.stdout


# --------------------------------------------------------------------------- #
# Linux: nmcli
# --------------------------------------------------------------------------- #

def _split_nmcli(line: str) -> list[str]:
    """Teilt eine nmcli-Terse-Zeile an ':' und beachtet ``\\:``-Escapes."""
    parts, buf, escape = [], [], False
    for ch in line:
        if escape:
            buf.append(ch)
            escape = False
        elif ch == "\\":
            escape = True
        elif ch == ":":
            parts.append("".join(buf))
            buf = []
        else:
            buf.append(ch)
    parts.append("".join(buf))
    return parts


def _normalize_security(raw: str) -> str:
    raw = raw.strip().upper()
    if not raw or raw in {"--", "NONE", "OPEN", "OFFEN"}:
        return "Offen"
    if "WPA3" in raw or "SAE" in raw:
        return "WPA3" if "WPA2" not in raw else "WPA2/WPA3"
    if "WPA2" in raw or "RSN" in raw:
        return "WPA2"
    if "WPA" in raw:
        return "WPA"
    if "WEP" in raw:
        return "WEP"
    return raw


def parse_nmcli(output: str) -> list[Network]:
    networks = []
    for line in output.splitlines():
        if not line.strip():
            continue
        fields = _split_nmcli(line)
        if len(fields) < 7:
            continue
        in_use, ssid, bssid, chan, freq, signal, security = fields[:7]
        rate_width = fields[7] if len(fields) > 7 else ""
        width = int(m.group(1)) if (m := re.search(r"(\d+)\s*MHz", rate_width)) else 20
        freq_num = int(m.group(1)) if (m := re.search(r"(\d+)", freq)) else 0
        try:
            networks.append(
                Network(
                    ssid=ssid,
                    bssid=bssid.upper(),
                    signal_dbm=quality_to_dbm(int(signal or 0)),
                    channel=int(chan or 0),
                    frequency=freq_num,
                    security=_normalize_security(security),
                    width_mhz=width,
                    connected=in_use.strip() == "*",
                )
            )
        except ValueError:
            continue
    return networks


def scan_nmcli(rescan: bool = True) -> list[Network]:
    cmd = [
        "nmcli", "-t", "-f",
        "IN-USE,SSID,BSSID,CHAN,FREQ,SIGNAL,SECURITY,BANDWIDTH",
        "device", "wifi", "list",
    ]
    if rescan:
        cmd += ["--rescan", "yes"]
    try:
        return parse_nmcli(_run(cmd))
    except ScanError:
        # Ältere nmcli-Versionen kennen BANDWIDTH nicht
        cmd[3] = "IN-USE,SSID,BSSID,CHAN,FREQ,SIGNAL,SECURITY"
        return parse_nmcli(_run(cmd))


# --------------------------------------------------------------------------- #
# Linux: iw
# --------------------------------------------------------------------------- #

def parse_iw(output: str) -> list[Network]:
    networks: list[Network] = []
    cur: dict | None = None

    def flush() -> None:
        if cur and cur.get("bssid"):
            networks.append(
                Network(
                    ssid=cur.get("ssid", ""),
                    bssid=cur["bssid"],
                    signal_dbm=int(cur.get("signal", -100)),
                    channel=cur.get("channel", 0),
                    frequency=cur.get("freq", 0),
                    security=cur.get("security", "Offen"),
                    width_mhz=cur.get("width", 20),
                    connected=cur.get("connected", False),
                )
            )

    for raw in output.splitlines():
        line = raw.strip()
        if m := re.match(r"^BSS ([0-9a-fA-F:]{17})", line):
            flush()
            cur = {"bssid": m.group(1).upper(), "connected": "associated" in line}
            continue
        if cur is None:
            continue
        if m := re.match(r"^freq:\s*([\d.]+)", line):
            cur["freq"] = int(float(m.group(1)))
        elif m := re.match(r"^signal:\s*(-?[\d.]+)", line):
            cur["signal"] = int(float(m.group(1)))
        elif line.startswith("SSID:"):
            cur["ssid"] = line[5:].strip()
        elif m := re.match(r"^\* primary channel:\s*(\d+)", line) or re.match(
            r"^DS Parameter set: channel (\d+)", line
        ):
            cur["channel"] = int(m.group(1))
        elif m := re.match(r"^\* (?:STA )?channel width:.*?(\d+) MHz", line):
            cur["width"] = max(cur.get("width", 20), int(m.group(1)))
        elif line.startswith("RSN:"):
            cur["security"] = "WPA2"
        elif line.startswith("WPA:") and cur.get("security") != "WPA2":
            cur["security"] = "WPA"
        elif "SAE" in line and "Authentication suites" in line:
            cur["security"] = "WPA3" if "PSK" not in line else "WPA2/WPA3"
        elif line.startswith("capability:") and "Privacy" in line and "security" not in cur:
            cur["security"] = "WEP"
    flush()
    return networks


def _wireless_interfaces() -> list[str]:
    out = _run(["iw", "dev"])
    return re.findall(r"Interface\s+(\S+)", out)


def scan_iw() -> list[Network]:
    interfaces = _wireless_interfaces()
    if not interfaces:
        raise ScanError("Keine WLAN-Schnittstelle gefunden.")
    return parse_iw(_run(["iw", "dev", interfaces[0], "scan"]))


# --------------------------------------------------------------------------- #
# Windows: netsh
# --------------------------------------------------------------------------- #

def parse_netsh(output: str) -> list[Network]:
    networks: list[Network] = []
    ssid = ""
    security = "Offen"
    cur: dict | None = None

    def flush() -> None:
        if cur:
            networks.append(
                Network(
                    ssid=ssid,
                    bssid=cur["bssid"],
                    signal_dbm=quality_to_dbm(cur.get("signal", 0)),
                    channel=cur.get("channel", 0),
                    security=security,
                )
            )

    for raw in output.splitlines():
        line = raw.strip()
        if m := re.match(r"^SSID \d+\s*:\s*(.*)$", line):
            flush()
            cur = None
            ssid = m.group(1).strip()
            security = "Offen"
        elif m := re.match(r"^(?:Authentication|Authentifizierung)\s*:\s*(.*)$", line):
            security = _normalize_security(m.group(1))
        elif m := re.match(r"^BSSID \d+\s*:\s*([0-9a-fA-F:]{17})", line):
            flush()
            cur = {"bssid": m.group(1).upper()}
        elif cur is not None and (m := re.match(r"^Signal\s*:\s*(\d+)%", line)):
            cur["signal"] = int(m.group(1))
        elif cur is not None and (m := re.match(r"^(?:Channel|Kanal)\s*:\s*(\d+)", line)):
            cur["channel"] = int(m.group(1))
    flush()
    return networks


def scan_netsh() -> list[Network]:
    return parse_netsh(_run(["netsh", "wlan", "show", "networks", "mode=bssid"]))


# --------------------------------------------------------------------------- #
# macOS: system_profiler
# --------------------------------------------------------------------------- #

def parse_system_profiler(output: str) -> list[Network]:
    data = json.loads(output)
    networks: list[Network] = []
    for entry in data.get("SPAirPortDataType", []):
        for iface in entry.get("spairport_airport_interfaces", []):
            current = iface.get("spairport_current_network_information") or {}
            candidates = list(iface.get("spairport_airport_other_local_wireless_networks", []))
            if current:
                candidates.append({**current, "_connected": True})
            for net in candidates:
                chan_raw = str(net.get("spairport_network_channel", ""))
                chan = int(m.group(1)) if (m := re.match(r"(\d+)", chan_raw)) else 0
                width = int(m.group(1)) if (m := re.search(r"(\d+)MHz", chan_raw)) else 20
                sig_raw = str(net.get("spairport_signal_noise", ""))
                sig = int(m.group(1)) if (m := re.search(r"(-\d+)\s*dBm", sig_raw)) else -100
                sec = str(net.get("spairport_security_mode", "")).replace("spairport_security_mode_", "")
                band = "6 GHz" if "6GHz" in chan_raw else None
                n = Network(
                    ssid=net.get("_name", ""),
                    bssid=str(net.get("spairport_network_bssid", "")).upper(),
                    signal_dbm=sig,
                    channel=chan,
                    security=_normalize_security(sec.replace("_", " ")),
                    width_mhz=width,
                    connected=bool(net.get("_connected")),
                )
                if band:
                    n.frequency = 5950 + chan * 5
                networks.append(n)
    return networks


def scan_macos() -> list[Network]:
    return parse_system_profiler(_run(["system_profiler", "SPAirPortDataType", "-json"], timeout=60))


# --------------------------------------------------------------------------- #

def available_backends() -> list[tuple[str, Callable[[], list[Network]]]]:
    system = platform.system()
    backends: list[tuple[str, Callable[[], list[Network]]]] = []
    if system == "Linux":
        if shutil.which("nmcli"):
            backends.append(("nmcli", scan_nmcli))
        if shutil.which("iw"):
            backends.append(("iw", scan_iw))
    elif system == "Windows":
        backends.append(("netsh", scan_netsh))
    elif system == "Darwin":
        backends.append(("system_profiler", scan_macos))
    return backends


def scan() -> tuple[str, list[Network]]:
    """Führt einen Scan mit dem ersten funktionierenden Backend aus."""
    errors = []
    backends = available_backends()
    if not backends:
        raise ScanError(
            f"Kein unterstütztes Scan-Werkzeug für {platform.system()} gefunden "
            "(benötigt nmcli/iw, netsh oder system_profiler). Tipp: --demo verwenden."
        )
    for name, fn in backends:
        try:
            return name, fn()
        except ScanError as exc:
            errors.append(f"{name}: {exc}")
    raise ScanError("Alle Scan-Backends fehlgeschlagen:\n  " + "\n  ".join(errors))
