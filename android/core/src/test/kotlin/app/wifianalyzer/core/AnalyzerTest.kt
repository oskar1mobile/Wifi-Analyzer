package app.wifianalyzer.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AnalyzerTest {
    @Test
    fun frequencyRoundTrip() {
        for (ch in listOf(1, 6, 11, 13, 36, 100, 149)) {
            assertEquals(ch, Frequencies.frequencyToChannel(Frequencies.channelToFrequency(ch)))
        }
        assertEquals(14, Frequencies.frequencyToChannel(2484))
        assertEquals(Band.GHZ_6, Frequencies.bandOf(5975))
        assertEquals(5, Frequencies.frequencyToChannel(5975))
    }

    @Test
    fun parsesAndroidCapabilities() {
        assertEquals(Security.WPA2, Security.fromCapabilities("[WPA2-PSK-CCMP][RSN-PSK-CCMP][ESS][WPS]"))
        assertEquals(Security.WPA2_WPA3, Security.fromCapabilities("[RSN-PSK+SAE-CCMP][ESS]"))
        assertEquals(Security.WPA3, Security.fromCapabilities("[RSN-SAE-CCMP][ESS][MFPR]"))
        assertEquals(Security.ENTERPRISE, Security.fromCapabilities("[WPA2-EAP/SHA1-CCMP][RSN-EAP/SHA1-CCMP][ESS]"))
        assertEquals(Security.WPA, Security.fromCapabilities("[WPA-PSK-TKIP][ESS]"))
        assertEquals(Security.WEP, Security.fromCapabilities("[WEP][ESS]"))
        assertEquals(Security.OWE, Security.fromCapabilities("[RSN-OWE-CCMP][ESS]"))
        assertEquals(Security.OPEN, Security.fromCapabilities("[ESS]"))
    }

    @Test
    fun recommendsFree24GhzChannel() {
        val nets = listOf(
            Network.onChannel("A", "1", -40, 1),
            Network.onChannel("B", "2", -45, 6),
            Network.onChannel("C", "3", -50, 1),
        )
        assertEquals(11, Analyzer.analyze(nets).best24.first().channel)
    }

    @Test
    fun adjacentChannelCountsAsInterference() {
        val nets = listOf(Network.onChannel("A", "1", -40, 3))
        assertTrue(Analyzer.channelInterference(nets, 1, Band.GHZ_2_4).interference > 0)
        assertEquals(0.0, Analyzer.channelInterference(nets, 11, Band.GHZ_2_4).interference)
    }

    @Test
    fun bonded5GhzSpan() {
        val n = Network.onChannel("A", "1", -50, 44, widthMhz = 80)
        assertEquals(5170.0 to 5250.0, Analyzer.span(n))
        assertTrue(Analyzer.channelInterference(listOf(n), 36, Band.GHZ_5).interference > 0)
        assertEquals(0.0, Analyzer.channelInterference(listOf(n), 52, Band.GHZ_5).interference)
    }

    @Test
    fun centerFrequencyIsPreferred() {
        // 40 MHz auf Kanal 6, Sekundärkanal oberhalb → Mitte bei Kanal 8 (2447 MHz)
        val n = Network("A", "1", -50, 2437, widthMhz = 40, centerFrequency = 2447)
        assertEquals(2427.0 to 2467.0, Analyzer.span(n))
    }

    @Test
    fun connectedApIsExcludedAndWeakSecurityFlagged() {
        val r = Analyzer.analyze(listOf(Network.onChannel("Mein", "AA", -50, 6, security = Security.WEP, connected = true)))
        assertNotNull(r.connected)
        assertEquals(0.0, r.channels24.first { it.channel == 6 }.interference)
        assertTrue(r.findings.any { it.level == Level.CRITICAL })
    }

    @Test
    fun emptyScan() {
        assertEquals(Level.WARNING, Analyzer.analyze(emptyList()).findings.single().level)
    }

    @Test
    fun demoDataProducesRecommendations() {
        val r = Analyzer.analyze(DemoData.networks)
        assertEquals(11, r.best24.first().channel)
        assertEquals(52, r.best5.first().channel)
        assertTrue(r.findings.any { "Empfehlung: Kanal 52" in it.message })
    }
}
