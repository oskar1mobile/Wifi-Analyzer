package app.wifianalyzer.core

/** Beispieldaten zum Ausprobieren der App ohne Berechtigungen. */
object DemoData {
    val networks: List<Network> = listOf(
        Network.onChannel("ZuHause-5G", "A4:2B:B0:11:22:33", -48, 36, 80, Security.WPA2_WPA3, connected = true),
        Network.onChannel("ZuHause", "A4:2B:B0:11:22:34", -45, 6, 20, Security.WPA2_WPA3),
        Network.onChannel("FRITZ!Box 7590 XY", "3C:A6:2F:01:02:03", -62, 6, 20, Security.WPA2),
        Network.onChannel("Vodafone-4A2C", "90:5C:44:AA:BB:CC", -71, 11, 20, Security.WPA2),
        Network.onChannel("o2-WLAN42", "D4:21:22:10:20:30", -66, 1, 20, Security.WPA2),
        Network.onChannel("Telekom_FON", "D4:21:22:10:20:31", -67, 1, 20, Security.OPEN),
        Network.onChannel("Nachbar", "00:1A:2B:3C:4D:5E", -58, 4, 40, Security.WPA2),
        Network.onChannel("DIRECT-HP-Drucker", "F8:B4:6A:11:11:11", -55, 6, 20, Security.WPA2),
        Network.onChannel("Altes-Netz", "00:14:BF:00:00:01", -83, 9, 20, Security.WEP),
        Network.onChannel("", "AE:12:34:56:78:9A", -77, 11, 20, Security.WPA2),
        Network.onChannel("Vodafone-4A2C-5G", "90:5C:44:AA:BB:CD", -74, 44, 80, Security.WPA2),
        Network.onChannel("FRITZ!Box 7590 XY", "3C:A6:2F:01:02:04", -69, 100, 80, Security.WPA3),
        Network.onChannel("MeshNet", "B0:BE:76:12:34:56", -80, 36, 40, Security.WPA2),
        Network.onChannel("Cafe-Gast", "C8:3A:35:00:11:22", -86, 13, 20, Security.OPEN),
    )
}
