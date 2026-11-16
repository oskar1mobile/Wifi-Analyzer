package app.wifianalyzer.core

import kotlin.math.max
import kotlin.math.min

enum class Rating(val label: String) { FREE("frei"), LOW("gering"), MEDIUM("mittel"), HIGH("hoch") }

data class ChannelScore(
    val channel: Int,
    val band: Band,
    val networksOnChannel: Int,
    val interference: Double,
    val dfs: Boolean = false,
) {
    val rating: Rating
        get() = when {
            interference < 0.5 -> Rating.FREE
            interference < 1.5 -> Rating.LOW
            interference < 3.0 -> Rating.MEDIUM
            else -> Rating.HIGH
        }
}

enum class Level { INFO, WARNING, CRITICAL }

data class Finding(val level: Level, val message: String)

data class AnalysisResult(
    val networks: List<Network>,
    val channels24: List<ChannelScore>,
    val channels5: List<ChannelScore>,
    val best24: List<ChannelScore>,
    val best5: List<ChannelScore>,
    val findings: List<Finding>,
    val connected: Network?,
) {
    val bandCounts: Map<Band, Int> get() = networks.groupingBy { it.band }.eachCount()
}

object Analyzer {
    val CHANNELS_24 = (1..13).toList()
    val NON_OVERLAPPING_24 = listOf(1, 6, 11)
    val CHANNELS_5 = listOf(36, 40, 44, 48, 52, 56, 60, 64, 100, 104, 108, 112, 116, 120, 124, 128, 132, 136, 140)
    private val DFS_5 = 52..144

    /** Erster 20-MHz-Kanal der 5-GHz-Blöcke je Kanalbreite. */
    private val BONDED_STARTS = mapOf(
        40 to listOf(36, 44, 52, 60, 100, 108, 116, 124, 132, 140, 149, 157, 165, 173),
        80 to listOf(36, 52, 100, 116, 132, 149, 165),
        160 to listOf(36, 100, 149),
    )

    private fun bondedBlockStart(channel: Int, width: Int): Int? {
        val n = width / 5
        return BONDED_STARTS[width]?.firstOrNull { channel >= it && channel < it + n }
    }

    /** Belegter Frequenzbereich (MHz) eines Netzes. */
    fun span(n: Network): Pair<Double, Double> {
        var width = max(20, n.widthMhz)
        if (n.band == Band.GHZ_2_4) width = max(22, width)
        val half = width / 2.0
        if (width >= 40 && n.centerFrequency > 0) {
            return n.centerFrequency - half to n.centerFrequency + half
        }
        if (n.band == Band.GHZ_5 && width >= 40) {
            bondedBlockStart(n.channel, width)?.let { start ->
                val low = 5000 + start * 5 - 10.0
                return low to low + width
            }
        }
        return n.frequency - half to n.frequency + half
    }

    /** Starke Nachbarn stören mehr: -30 dBm → 1.0, -90 dBm → 0.0. */
    private fun weight(dbm: Int): Double = ((dbm + 90) / 60.0).coerceIn(0.0, 1.0)

    private fun overlap(a: Pair<Double, Double>, b: Pair<Double, Double>): Double {
        val lo = max(a.first, b.first)
        val hi = min(a.second, b.second)
        return if (hi <= lo) 0.0 else (hi - lo) / (a.second - a.first)
    }

    fun channelInterference(
        networks: List<Network>,
        channel: Int,
        band: Band,
        excludeBssid: String? = null,
    ): ChannelScore {
        val probe = Network.onChannel("", "", 0, channel, band = band)
        val probeSpan = span(probe)
        var total = 0.0
        var same = 0
        for (n in networks) {
            if (n.band != band || (excludeBssid != null && n.bssid == excludeBssid)) continue
            val ov = overlap(probeSpan, span(n))
            if (ov <= 0) continue
            if (n.channel == channel) {
                // Gleicher Kanal: Medium wird geteilt (CSMA/CA) → Airtime-Verlust
                same++
                total += weight(n.signalDbm)
            } else {
                // Teilweise Überlappung erzeugt echtes Rauschen → stärker gewichtet
                total += weight(n.signalDbm) * ov * 1.5
            }
        }
        return ChannelScore(channel, band, same, total, dfs = band == Band.GHZ_5 && channel in DFS_5)
    }

    fun analyze(input: List<Network>): AnalysisResult {
        val networks = input.sortedByDescending { it.signalDbm }
        val connected = networks.firstOrNull { it.connected }
        val exclude = connected?.bssid
        val ch24 = CHANNELS_24.map { channelInterference(networks, it, Band.GHZ_2_4, exclude) }
        val ch5 = CHANNELS_5.map { channelInterference(networks, it, Band.GHZ_5, exclude) }
        val best24 = ch24.filter { it.channel in NON_OVERLAPPING_24 }
            .sortedWith(compareBy({ it.interference }, { it.channel }))
        // Nicht-DFS-Kanäle leicht bevorzugen (kein Radar-bedingtes Umschalten)
        val best5 = ch5.sortedWith(compareBy({ it.interference + if (it.dfs) 0.25 else 0.0 }, { it.channel })).take(3)
        val partial = AnalysisResult(networks, ch24, ch5, best24, best5, emptyList(), connected)
        return partial.copy(findings = findings(partial))
    }

    private fun count(n: Int) = if (n == 1) "1 Netz" else "$n Netze"

    private fun findings(r: AnalysisResult): List<Finding> {
        if (r.networks.isEmpty()) return listOf(Finding(Level.WARNING, "Keine Netzwerke gefunden."))
        val out = mutableListOf<Finding>()
        val counts = r.bandCounts
        out += Finding(
            Level.INFO,
            "${r.networks.size} Access Points gefunden (" +
                counts.entries.sortedBy { it.key.ordinal }.joinToString(", ") { "${it.key.label}: ${it.value}" } + ")",
        )

        val open = r.networks.filter { it.security == Security.OPEN }
        if (open.isNotEmpty()) {
            out += Finding(
                Level.WARNING,
                "${count(open.size)} ohne Verschlüsselung in Reichweite: " +
                    open.map { it.displaySsid }.toSortedSet().joinToString(", "),
            )
        }
        val weak = r.networks.filter { it.security == Security.WEP || it.security == Security.WPA }
        if (weak.isNotEmpty()) {
            out += Finding(
                Level.WARNING,
                "${count(weak.size)} mit veralteter Verschlüsselung (WEP/WPA): " +
                    weak.map { it.displaySsid }.toSortedSet().joinToString(", "),
            )
        }
        val odd = r.networks.count { it.band == Band.GHZ_2_4 && it.channel !in NON_OVERLAPPING_24 }
        if (odd > 0) out += Finding(Level.INFO, "${count(odd)} im 2.4-GHz-Band auf überlappenden Kanälen (nicht 1/6/11).")
        val wide = r.networks.count { it.band == Band.GHZ_2_4 && it.widthMhz >= 40 }
        if (wide > 0) out += Finding(Level.INFO, "${count(wide)} mit 40 MHz im 2.4-GHz-Band – belegt fast das halbe Band.")

        val c = r.connected
        if (c != null) {
            val level = when {
                c.signalDbm >= -67 -> Level.INFO
                c.signalDbm >= -80 -> Level.WARNING
                else -> Level.CRITICAL
            }
            out += Finding(
                level,
                "Verbunden mit „${c.displaySsid}“ auf Kanal ${c.channel} (${c.band.label}), " +
                    "Signal ${c.signalDbm} dBm (${signalLabel(c.signalDbm)}).",
            )
            when {
                c.security.weak -> out += Finding(
                    Level.CRITICAL,
                    "Dein Netz verwendet ${c.security.label} (${c.security.description}). " +
                        "Umstellen auf WPA2-AES oder besser WPA3 empfohlen.",
                )
                c.security == Security.WPA2 -> out += Finding(
                    Level.INFO,
                    "Dein Netz nutzt WPA2. Falls Router und Geräte es unterstützen, ist WPA3 " +
                        "(oder der WPA2/WPA3-Mischmodus) sicherer.",
                )
                else -> Unit
            }
            val (scores, best) = when (c.band) {
                Band.GHZ_2_4 -> r.channels24 to r.best24
                Band.GHZ_5 -> r.channels5 to r.best5
                else -> emptyList<ChannelScore>() to emptyList()
            }
            val current = scores.firstOrNull { it.channel == c.channel }
                ?: if (c.band == Band.GHZ_5) channelInterference(r.networks, c.channel, Band.GHZ_5, c.bssid) else null
            val top = best.firstOrNull()
            if (current != null && top != null) {
                out += if (top.channel != c.channel && current.interference - top.interference >= 0.5) {
                    Finding(
                        Level.WARNING,
                        "Kanal ${c.channel} ist ${current.rating.label} belegt (Score %.1f). ".format(current.interference) +
                            "Empfehlung: Kanal ${top.channel} (Score %.1f).".format(top.interference),
                    )
                } else {
                    Finding(Level.INFO, "Kanal ${c.channel} ist eine gute Wahl (Auslastung: ${current.rating.label}).")
                }
            }
            if (c.band == Band.GHZ_2_4 && (counts[Band.GHZ_5] ?: 0) > 0 && c.signalDbm > -65) {
                out += Finding(
                    Level.INFO,
                    "Tipp: Wenn dein Router 5 GHz bietet, ist dort meist mehr Platz und höhere Geschwindigkeit möglich.",
                )
            }
        } else {
            out += Finding(Level.INFO, "Keine aktive Verbindung erkannt – Empfehlungen gelten allgemein.")
        }

        if ((counts[Band.GHZ_2_4] ?: 0) > 0) {
            r.best24.firstOrNull()?.let {
                out += Finding(Level.INFO, "Bester 2.4-GHz-Kanal: ${it.channel} (${it.rating.label}).")
            }
        }
        if ((counts[Band.GHZ_5] ?: 0) > 0) {
            r.best5.firstOrNull()?.let {
                out += Finding(
                    Level.INFO,
                    "Bester 5-GHz-Kanal: ${it.channel} (${it.rating.label}${if (it.dfs) ", DFS" else ""}).",
                )
            }
        }
        return out
    }
}
