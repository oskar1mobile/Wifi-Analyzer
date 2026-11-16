"""Kommandozeilen-Oberfläche des WiFi-Analyzers."""

from __future__ import annotations

import argparse
import sys
import time
from pathlib import Path

from . import __version__
from .analyzer import analyze
from .demo import demo_networks
from .report import format_html, format_json, format_terminal
from .scanner import ScanError, scan


def build_parser() -> argparse.ArgumentParser:
    p = argparse.ArgumentParser(
        prog="wifi-analyzer",
        description="Scannt WLAN-Netzwerke und analysiert Kanalauslastung, Signal und Sicherheit.",
    )
    p.add_argument("--demo", action="store_true", help="Beispieldaten statt echtem Scan verwenden")
    p.add_argument("--json", metavar="DATEI", help="Ergebnis als JSON speichern ('-' für stdout)")
    p.add_argument("--html", metavar="DATEI", help="HTML-Report mit Kanaldiagramm speichern")
    p.add_argument("--band", choices=["2.4", "5", "6"], help="Nur ein Frequenzband anzeigen")
    p.add_argument("--min-signal", type=int, metavar="DBM", help="Netze schwächer als DBM ausblenden (z. B. -80)")
    p.add_argument("--watch", type=int, metavar="SEK", help="Scan alle SEK Sekunden wiederholen")
    p.add_argument("--no-color", action="store_true", help="Farbige Ausgabe deaktivieren")
    p.add_argument("--version", action="version", version=f"%(prog)s {__version__}")
    return p


def _collect(args) -> list:
    if args.demo:
        networks = demo_networks()
    else:
        backend, networks = scan()
        print(f"Scan über '{backend}': {len(networks)} Einträge", file=sys.stderr)
    if args.band:
        networks = [n for n in networks if n.band.startswith(args.band)]
    if args.min_signal is not None:
        networks = [n for n in networks if n.signal_dbm >= args.min_signal or n.connected]
    return networks


def run_once(args) -> int:
    try:
        networks = _collect(args)
    except ScanError as exc:
        print(f"Fehler: {exc}", file=sys.stderr)
        return 2
    result = analyze(networks)

    if args.json == "-":
        print(format_json(result))
    else:
        print(format_terminal(result, color=False if args.no_color else None))
        if args.json:
            Path(args.json).write_text(format_json(result), encoding="utf-8")
            print(f"\nJSON gespeichert: {args.json}")
    if args.html:
        Path(args.html).write_text(format_html(result), encoding="utf-8")
        print(f"HTML-Report gespeichert: {args.html}", file=sys.stderr if args.json == "-" else sys.stdout)
    return 0


def main(argv: list[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    if not args.watch:
        return run_once(args)
    try:
        while True:
            print("\033[2J\033[H", end="")
            code = run_once(args)
            if code:
                return code
            print(f"\nNächster Scan in {args.watch}s – Strg+C zum Beenden")
            time.sleep(args.watch)
    except KeyboardInterrupt:
        return 0


if __name__ == "__main__":
    sys.exit(main())
