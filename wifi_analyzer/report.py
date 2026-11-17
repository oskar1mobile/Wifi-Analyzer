"""Ausgabe der Analyse: Terminal-Tabelle, JSON und eigenständiger HTML-Report."""

from __future__ import annotations

import html
import json
import os
import sys
from datetime import datetime

from .analyzer import AnalysisResult, ChannelScore, signal_label, span
from .models import Network

# --------------------------------------------------------------------------- #
# Terminal
# --------------------------------------------------------------------------- #

_COLORS = {"reset": "\033[0m", "bold": "\033[1m", "dim": "\033[2m",
           "red": "\033[31m", "green": "\033[32m", "yellow": "\033[33m", "cyan": "\033[36m"}


def _use_color(stream) -> bool:
    return hasattr(stream, "isatty") and stream.isatty() and os.environ.get("NO_COLOR") is None


class _Painter:
    def __init__(self, enabled: bool):
        self.enabled = enabled

    def __call__(self, text: str, *styles: str) -> str:
        if not self.enabled or not styles:
            return text
        return "".join(_COLORS[s] for s in styles) + text + _COLORS["reset"]


def _signal_color(dbm: int) -> str:
    return "green" if dbm >= -60 else "yellow" if dbm >= -75 else "red"


def _bar(value: float, maximum: float, width: int = 20) -> str:
    if maximum <= 0:
        return ""
    filled = int(round(min(value, maximum) / maximum * width))
    return "█" * filled + "░" * (width - filled)


def _pad(text: str, width: int) -> str:
    return text[:width].ljust(width)


def format_terminal(result: AnalysisResult, color: bool | None = None, stream=None) -> str:
    stream = stream or sys.stdout
    p = _Painter(_use_color(stream) if color is None else color)
    lines: list[str] = []
    lines.append(p("WLAN-Analyse", "bold", "cyan") + p(f"  ({datetime.now():%d.%m.%Y %H:%M})", "dim"))
    lines.append("")

    header = f"  {'SSID':<26} {'BSSID':<17}  {'Band':<7} {'Kanal':>5} {'Breite':>6}  {'Signal':>8}  {'':<20} {'Sicherheit':<10}"
    lines.append(p(header, "bold"))
    for n in result.networks:
        mark = p("●", "green") if n.connected else " "
        sig = p(f"{n.signal_dbm:>4} dBm", _signal_color(n.signal_dbm))
        bar = p(_bar(n.quality, 100), _signal_color(n.signal_dbm))
        sec = n.security
        sec = p(_pad(sec, 10), "red") if sec in ("Offen", "WEP", "WPA") else _pad(sec, 10)
        lines.append(
            f"{mark} {_pad(n.display_ssid, 26)} {n.bssid:<17}  {n.band:<7} {n.channel:>5} "
            f"{str(n.width_mhz) + ' MHz':>6}  {sig}  {bar} {sec}"
        )
    lines.append("")

    def channel_block(title: str, scores: list[ChannelScore], best: list[ChannelScore]) -> None:
        if not scores:
            return
        lines.append(p(title, "bold"))
        best_ch = best[0].channel if best else None
        maximum = max(4.0, max(s.interference for s in scores))
        for s in scores:
            col = "green" if s.interference < 0.5 else "yellow" if s.interference < 3 else "red"
            tag = p(" ◀ empfohlen", "green", "bold") if s.channel == best_ch else ""
            dfs = p(" DFS", "dim") if s.dfs else "    "
            lines.append(
                f"  Kanal {s.channel:>3}{dfs}  {p(_bar(s.interference, maximum, 30), col)} "
                f"{s.interference:4.1f}  {s.networks_on_channel:>2} AP  {s.rating:<6}{tag}"
            )
        lines.append("")

    channel_block("Kanalauslastung 2.4 GHz", result.channels_24, result.best_24)
    if result.band_counts.get("5 GHz"):
        channel_block("Kanalauslastung 5 GHz", result.channels_5, result.best_5)

    lines.append(p("Ergebnisse & Empfehlungen", "bold"))
    icons = {"info": p("ℹ", "cyan"), "warnung": p("⚠", "yellow"), "kritisch": p("✖", "red")}
    for f in result.findings:
        lines.append(f"  {icons.get(f.level, '-')} {f.message}")
    return "\n".join(lines)


# --------------------------------------------------------------------------- #
# JSON
# --------------------------------------------------------------------------- #

def format_json(result: AnalysisResult) -> str:
    data = result.to_dict()
    data["generated_at"] = datetime.now().isoformat(timespec="seconds")
    return json.dumps(data, indent=2, ensure_ascii=False)


# --------------------------------------------------------------------------- #
# HTML
# --------------------------------------------------------------------------- #

_PALETTE = ["#2a78d6", "#d6532a", "#2aa876", "#a64ad6", "#d6a52a", "#2ab3c7",
            "#c72a74", "#6b8e23", "#8a5a44", "#5a6bd6", "#d67a2a", "#3d9970"]


def _channel_svg(networks: list[Network], band: str) -> str:
    nets = [n for n in networks if n.band == band]
    if band == "2.4 GHz":
        ch_min, ch_max, ticks = -1, 15, list(range(1, 14))
    else:
        if not nets:
            return ""
        edges = [(f - 5000) / 5 for n in nets for f in span(n)]
        ch_min, ch_max = min(32, int(min(edges)) - 4), max(68, int(max(edges)) + 4)
        ticks = [c for c in range(36, ch_max, 4) if c >= ch_min]
    W, H, L, R, T, B = 900, 300, 50, 15, 15, 35
    pw, ph = W - L - R, H - T - B
    db_min, db_max = -100, -20

    def x(ch: float) -> float:
        return L + (ch - ch_min) / (ch_max - ch_min) * pw

    def y(db: float) -> float:
        return T + (db_max - db) / (db_max - db_min) * ph

    cid = "clip" + band.split()[0].replace(".", "")
    parts = [f'<svg viewBox="0 0 {W} {H}" role="img" aria-label="Kanalbelegung {band}" class="chart">',
             f'<defs><clipPath id="{cid}"><rect x="{L}" y="0" width="{pw}" height="{T + ph}"/></clipPath></defs>']
    for db in range(db_min, db_max + 1, 10):
        parts.append(f'<line x1="{L}" x2="{W - R}" y1="{y(db):.1f}" y2="{y(db):.1f}" class="grid"/>')
        parts.append(f'<text x="{L - 6}" y="{y(db) + 4:.1f}" class="axis" text-anchor="end">{db}</text>')
    for c in ticks:
        parts.append(f'<text x="{x(c):.1f}" y="{H - B + 18}" class="axis" text-anchor="middle">{c}</text>')
    parts.append(f'<text x="{W - R}" y="{H - 4}" class="axis" text-anchor="end">Kanal</text>')

    for i, n in enumerate(sorted(nets, key=lambda n: n.signal_dbm)):
        color = _PALETTE[i % len(_PALETTE)]
        lo, hi = span(n)
        if band == "6 GHz":
            base = 5950
        else:
            base = 2407 if band == "2.4 GHz" else 5000
        x0, x1 = x((lo - base) / 5), x((hi - base) / 5)
        xc = (x0 + x1) / 2
        ytop, ybase = y(n.signal_dbm), y(db_min)
        sw = 3 if n.connected else 1.5
        # Kubische Bézierkurve erreicht nur 75 % der Kontrollpunkthöhe → kompensieren
        yctrl = (ytop - 0.25 * ybase) / 0.75
        parts.append(
            f'<path d="M{x0:.1f},{ybase:.1f} C{x0 + (xc - x0) * 0.35:.1f},{yctrl:.1f} '
            f'{x1 - (x1 - xc) * 0.35:.1f},{yctrl:.1f} {x1:.1f},{ybase:.1f}" '
            f'fill="{color}" fill-opacity="0.12" stroke="{color}" stroke-width="{sw}" clip-path="url(#{cid})">'
            f'<title>{html.escape(n.display_ssid)} – Kanal {n.channel}, {n.signal_dbm} dBm</title></path>'
        )
        parts.append(f'<text x="{xc:.1f}" y="{ytop - 4:.1f}" '
                     f'text-anchor="middle" class="label" fill="{color}">{html.escape(n.display_ssid[:18])}</text>')
    parts.append("</svg>")
    return "".join(parts)


def _score_rows(scores: list[ChannelScore], best: list[ChannelScore]) -> str:
    best_ch = best[0].channel if best else None
    maximum = max([4.0] + [s.interference for s in scores])
    rows = []
    for s in scores:
        pct = min(100, s.interference / maximum * 100)
        cls = "ok" if s.interference < 0.5 else "mid" if s.interference < 3 else "bad"
        badge = '<span class="badge">empfohlen</span>' if s.channel == best_ch else ""
        dfs = ' <span class="muted">DFS</span>' if s.dfs else ""
        rows.append(
            f"<tr><td>{s.channel}{dfs}</td><td class='barcell'><div class='bar {cls}' style='width:{pct:.0f}%'></div></td>"
            f"<td class='num'>{s.interference:.1f}</td><td class='num'>{s.networks_on_channel}</td>"
            f"<td>{s.rating} {badge}</td></tr>"
        )
    return "".join(rows)


def format_html(result: AnalysisResult) -> str:
    esc = html.escape
    net_rows = []
    for n in result.networks:
        sec_cls = " class='bad-text'" if n.security in ("Offen", "WEP", "WPA") else ""
        sig_cls = "ok" if n.signal_dbm >= -60 else "mid" if n.signal_dbm >= -75 else "bad"
        net_rows.append(
            f"<tr{' class=connected' if n.connected else ''}>"
            f"<td>{'● ' if n.connected else ''}{esc(n.display_ssid)}</td><td class='mono'>{esc(n.bssid)}</td>"
            f"<td>{n.band}</td><td class='num'>{n.channel}</td><td class='num'>{n.width_mhz}</td>"
            f"<td class='num'>{n.signal_dbm}</td>"
            f"<td class='barcell'><div class='bar {sig_cls}' style='width:{n.quality}%'></div></td>"
            f"<td>{signal_label(n.signal_dbm)}</td><td{sec_cls}>{esc(n.security)}</td></tr>"
        )
    findings = "".join(
        f"<li class='f-{f.level}'><span class='lvl'>{f.level}</span>{esc(f.message)}</li>"
        for f in result.findings
    )
    c = result.connected
    stats = [
        ("Access Points", str(len(result.networks))),
        ("2.4 GHz / 5 GHz", f"{result.band_counts.get('2.4 GHz', 0)} / {result.band_counts.get('5 GHz', 0)}"),
        ("Bester Kanal 2.4", str(result.best_24[0].channel) if result.best_24 else "–"),
        ("Bester Kanal 5", str(result.best_5[0].channel) if result.best_5 else "–"),
        ("Verbunden", f"{esc(c.display_ssid)} ({c.signal_dbm} dBm)" if c else "–"),
    ]
    stat_html = "".join(f"<div class='stat'><div class='k'>{k}</div><div class='v'>{v}</div></div>" for k, v in stats)
    svg5 = _channel_svg(result.networks, "5 GHz")
    sec5 = ""
    if svg5:
        sec5 = (f"<section><h2>Kanalbelegung 5 GHz</h2>{svg5}"
                f"<table class='scores'><thead><tr><th>Kanal</th><th>Interferenz</th><th class=\'num\'>Score</th><th class=\'num\'>APs</th><th>Bewertung</th></tr></thead>"
                f"<tbody>{_score_rows(result.channels_5, result.best_5)}</tbody></table></section>")

    return f"""<!doctype html>
<html lang="de"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>WLAN-Analyse</title>
<style>
:root {{ --bg:#f7f8fa; --card:#fff; --fg:#1b1f24; --muted:#6a737d; --line:#e3e6ea;
  --ok:#2aa876; --mid:#d6a52a; --bad:#d64545; --accent:#2a78d6; }}
@media (prefers-color-scheme: dark) {{ :root {{ --bg:#0f1216; --card:#181c22; --fg:#e6e9ed; --muted:#8b949e; --line:#2a3038; }} }}
* {{ box-sizing:border-box; }}
body {{ margin:0; background:var(--bg); color:var(--fg); font:14px/1.5 system-ui,-apple-system,Segoe UI,Roboto,sans-serif; }}
main {{ max-width:1100px; margin:0 auto; padding:24px 16px 48px; }}
h1 {{ font-size:24px; margin:0 0 4px; }} h2 {{ font-size:16px; margin:0 0 12px; }}
.sub {{ color:var(--muted); margin-bottom:20px; }}
section {{ background:var(--card); border:1px solid var(--line); border-radius:10px; padding:16px; margin-bottom:16px; overflow-x:auto; }}
.stats {{ display:grid; grid-template-columns:repeat(auto-fit,minmax(160px,1fr)); gap:12px; margin-bottom:16px; }}
.stat {{ background:var(--card); border:1px solid var(--line); border-radius:10px; padding:12px 14px; }}
.stat .k {{ color:var(--muted); font-size:12px; }} .stat .v {{ font-size:18px; font-weight:600; }}
table {{ width:100%; border-collapse:collapse; }} th,td {{ padding:6px 8px; border-bottom:1px solid var(--line); text-align:left; white-space:nowrap; }}
th {{ color:var(--muted); font-weight:500; font-size:12px; }}
.num {{ text-align:right; font-variant-numeric:tabular-nums; }} .mono {{ font-family:ui-monospace,monospace; font-size:12px; }}
.barcell {{ width:160px; min-width:100px; }} .bar {{ height:8px; border-radius:4px; }}
.bar.ok {{ background:var(--ok); }} .bar.mid {{ background:var(--mid); }} .bar.bad {{ background:var(--bad); }}
tr.connected td {{ font-weight:600; }} .bad-text {{ color:var(--bad); font-weight:600; }}
.badge {{ background:var(--ok); color:#fff; border-radius:999px; padding:1px 8px; font-size:11px; margin-left:4px; }}
.muted {{ color:var(--muted); font-size:11px; }}
ul.findings {{ list-style:none; padding:0; margin:0; }} ul.findings li {{ padding:8px 0; border-bottom:1px solid var(--line); }}
.lvl {{ display:inline-block; min-width:72px; font-size:11px; text-transform:uppercase; letter-spacing:.04em; color:var(--muted); }}
.f-warnung .lvl {{ color:var(--mid); }} .f-kritisch .lvl {{ color:var(--bad); }}
svg.chart {{ width:100%; min-width:600px; height:auto; display:block; margin-bottom:12px; }}
svg .grid {{ stroke:var(--line); }} svg .axis {{ fill:var(--muted); font-size:11px; }} svg .label {{ font-size:10px; font-weight:600; }}
</style></head>
<body><main>
<h1>WLAN-Analyse</h1>
<div class="sub">Erstellt am {datetime.now():%d.%m.%Y um %H:%M Uhr}</div>
<div class="stats">{stat_html}</div>
<section><h2>Ergebnisse &amp; Empfehlungen</h2><ul class="findings">{findings}</ul></section>
<section><h2>Kanalbelegung 2.4 GHz</h2>{_channel_svg(result.networks, "2.4 GHz")}
<table class="scores"><thead><tr><th>Kanal</th><th>Interferenz</th><th class="num">Score</th><th class="num">APs</th><th>Bewertung</th></tr></thead>
<tbody>{_score_rows(result.channels_24, result.best_24)}</tbody></table></section>
{sec5}
<section><h2>Gefundene Netzwerke</h2>
<table><thead><tr><th>SSID</th><th>BSSID</th><th>Band</th><th class="num">Kanal</th><th class="num">MHz</th><th class="num">dBm</th><th>Qualität</th><th>Signal</th><th>Sicherheit</th></tr></thead>
<tbody>{''.join(net_rows)}</tbody></table></section>
</main></body></html>
"""
