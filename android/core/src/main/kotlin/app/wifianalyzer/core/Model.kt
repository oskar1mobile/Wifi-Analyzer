package app.wifianalyzer.core

enum class Band(val label: String, val baseFrequency: Int) {
    GHZ_2_4("2.4 GHz", 2407),
    GHZ_5("5 GHz", 5000),
    GHZ_6("6 GHz", 5950),
    UNKNOWN("unbekannt", 0),
}

object Frequencies {
    fun channelToFrequency(channel: Int, band: Band? = null): Int = when {
        band == Band.GHZ_6 -> 5950 + channel * 5
        channel == 14 -> 2484
        channel in 1..13 -> 2407 + channel * 5
        channel in 32..177 -> 5000 + channel * 5
        else -> 0
    }

    fun frequencyToChannel(freq: Int): Int = when {
        freq == 2484 -> 14
        freq in 2412..2472 -> (freq - 2407) / 5
        freq in 5150..5895 -> (freq - 5000) / 5
        freq in 5955..7115 -> (freq - 5950) / 5
        else -> 0
    }

    fun bandOf(freq: Int): Band = when (freq) {
        in 2400 until 2500 -> Band.GHZ_2_4
        in 5000 until 5925 -> Band.GHZ_5
        in 5925..7125 -> Band.GHZ_6
        else -> Band.UNKNOWN
    }
}

enum class Security(val label: String, val weak: Boolean, val description: String) {
    OPEN("Offen", true, "keine Verschlüsselung"),
    OWE("OWE", false, "verschlüsselt ohne Passwort"),
    WEP("WEP", true, "unsicher"),
    WPA("WPA", true, "veraltet"),
    WPA2("WPA2", false, "gut"),
    WPA2_WPA3("WPA2/WPA3", false, "gut"),
    WPA3("WPA3", false, "sehr gut"),
    ENTERPRISE("Enterprise", false, "802.1X");

    companion object {
        /** Interpretiert den Android-Capabilities-String, z. B. "[RSN-PSK+SAE-CCMP][ESS]". */
        fun fromCapabilities(capabilities: String): Security {
            val c = capabilities.uppercase()
            return when {
                "EAP" in c -> ENTERPRISE
                "SAE" in c && "PSK" in c -> WPA2_WPA3
                "SAE" in c -> WPA3
                "OWE" in c -> OWE
                "RSN" in c || "WPA2" in c -> WPA2
                "WPA" in c -> WPA
                "WEP" in c -> WEP
                else -> OPEN
            }
        }
    }
}

data class Network(
    val ssid: String,
    val bssid: String,
    val signalDbm: Int,
    val frequency: Int,
    val widthMhz: Int = 20,
    /** Mittenfrequenz des gesamten (gebündelten) Kanals, 0 = unbekannt. */
    val centerFrequency: Int = 0,
    val security: Security = Security.OPEN,
    val connected: Boolean = false,
) {
    val channel: Int get() = Frequencies.frequencyToChannel(frequency)
    val band: Band get() = Frequencies.bandOf(frequency)
    val quality: Int get() = ((signalDbm + 100) * 2).coerceIn(0, 100)
    val displaySsid: String get() = ssid.ifBlank { "<versteckt>" }

    companion object {
        fun onChannel(
            ssid: String,
            bssid: String,
            signalDbm: Int,
            channel: Int,
            widthMhz: Int = 20,
            security: Security = Security.WPA2,
            connected: Boolean = false,
            band: Band? = null,
        ) = Network(
            ssid = ssid,
            bssid = bssid,
            signalDbm = signalDbm,
            frequency = Frequencies.channelToFrequency(channel, band),
            widthMhz = widthMhz,
            security = security,
            connected = connected,
        )
    }
}

fun signalLabel(dbm: Int): String = when {
    dbm >= -50 -> "Ausgezeichnet"
    dbm >= -60 -> "Sehr gut"
    dbm >= -67 -> "Gut"
    dbm >= -75 -> "Mäßig"
    dbm >= -85 -> "Schwach"
    else -> "Sehr schwach"
}
