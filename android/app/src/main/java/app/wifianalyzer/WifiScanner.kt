package app.wifianalyzer

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import androidx.core.content.ContextCompat
import app.wifianalyzer.core.Network
import app.wifianalyzer.core.Security
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

data class ScanOutcome(val networks: List<Network>, val fresh: Boolean)

/** Kapselt den Android-WifiManager: Scan anstoßen, auf Ergebnis warten, umwandeln. */
class WifiScanner(context: Context) {
    private val appContext = context.applicationContext
    private val wifi = appContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    val isWifiEnabled: Boolean get() = wifi.isWifiEnabled

    /**
     * Startet einen Scan und wartet bis zu 15 s auf das Ergebnis. Android drosselt
     * Scans (4 pro 2 Minuten im Vordergrund); dann werden die zuletzt bekannten
     * Ergebnisse zurückgegeben und [ScanOutcome.fresh] ist false.
     */
    suspend fun scan(): ScanOutcome {
        val fresh = withTimeoutOrNull(15_000) { awaitScan() } ?: false
        return ScanOutcome(readResults(), fresh)
    }

    private suspend fun awaitScan(): Boolean {
        val result = CompletableDeferred<Boolean>()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                result.complete(intent?.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false) ?: false)
            }
        }
        ContextCompat.registerReceiver(
            appContext,
            receiver,
            IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        try {
            @Suppress("DEPRECATION")
            if (!wifi.startScan()) return false
            return result.await()
        } finally {
            runCatching { appContext.unregisterReceiver(receiver) }
        }
    }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    fun readResults(): List<Network> {
        val connectedBssid = wifi.connectionInfo?.bssid?.uppercase()
            ?.takeUnless { it == "02:00:00:00:00:00" }
        return wifi.scanResults.map { r ->
            val ssid = r.SSID.orEmpty().takeUnless { it == "<unknown ssid>" }.orEmpty()
            val bssid = r.BSSID.orEmpty().uppercase()
            Network(
                ssid = ssid,
                bssid = bssid,
                signalDbm = r.level,
                frequency = r.frequency,
                widthMhz = widthOf(r),
                centerFrequency = r.centerFreq0,
                security = Security.fromCapabilities(r.capabilities.orEmpty()),
                connected = connectedBssid != null && bssid == connectedBssid,
            )
        }
    }

    private fun widthOf(r: ScanResult): Int = when (r.channelWidth) {
        ScanResult.CHANNEL_WIDTH_40MHZ -> 40
        ScanResult.CHANNEL_WIDTH_80MHZ -> 80
        ScanResult.CHANNEL_WIDTH_160MHZ, ScanResult.CHANNEL_WIDTH_80MHZ_PLUS_MHZ -> 160
        5 -> 320 // ScanResult.CHANNEL_WIDTH_320MHZ (API 33)
        else -> 20
    }
}
