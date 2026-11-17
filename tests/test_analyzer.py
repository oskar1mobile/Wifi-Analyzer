import json

from wifi_analyzer.analyzer import analyze, channel_interference, span
from wifi_analyzer.cli import main
from wifi_analyzer.demo import demo_networks
from wifi_analyzer.models import Network, frequency_to_channel, channel_to_frequency
from wifi_analyzer.report import format_html, format_json, format_terminal


def test_frequency_conversion():
    for ch in (1, 6, 11, 13, 36, 100, 149):
        assert frequency_to_channel(channel_to_frequency(ch)) == ch
    assert channel_to_frequency(14) == 2484


def test_recommends_free_24ghz_channel():
    nets = [
        Network("A", "00:00:00:00:00:01", -40, 1),
        Network("B", "00:00:00:00:00:02", -45, 6),
        Network("C", "00:00:00:00:00:03", -50, 1),
    ]
    r = analyze(nets)
    assert r.best_24[0].channel == 11


def test_adjacent_channel_counts_as_interference():
    nets = [Network("A", "00:00:00:00:00:01", -40, 3)]
    assert channel_interference(nets, 1, "2.4 GHz").interference > 0
    assert channel_interference(nets, 11, "2.4 GHz").interference == 0


def test_bonded_5ghz_span():
    n = Network("A", "x", -50, 44, width_mhz=80)
    assert span(n) == (5170, 5250)  # Kanäle 36–48
    assert channel_interference([n], 36, "5 GHz").interference > 0
    assert channel_interference([n], 52, "5 GHz").interference == 0


def test_connected_ap_is_excluded_and_bad_security_flagged():
    nets = [Network("Mein", "AA", -50, 6, security="WEP", connected=True)]
    r = analyze(nets)
    assert r.connected is not None
    assert next(c for c in r.channels_24 if c.channel == 6).interference == 0
    assert any(f.level == "kritisch" for f in r.findings)


def test_empty_scan():
    r = analyze([])
    assert r.findings[0].level == "warnung"


def test_reports_render():
    r = analyze(demo_networks())
    assert "WLAN-Analyse" in format_terminal(r, color=False)
    data = json.loads(format_json(r))
    assert len(data["networks"]) == len(demo_networks())
    html = format_html(r)
    assert html.startswith("<!doctype html>") and "<svg" in html


def test_cli_demo(tmp_path, capsys):
    out = tmp_path / "r.html"
    assert main(["--demo", "--no-color", "--html", str(out)]) == 0
    assert out.exists()
    assert main(["--demo", "--json", "-"]) == 0
