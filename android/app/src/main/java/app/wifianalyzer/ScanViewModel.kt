package app.wifianalyzer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.wifianalyzer.core.AnalysisResult
import app.wifianalyzer.core.Analyzer
import app.wifianalyzer.core.DemoData
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class UiState(
    val result: AnalysisResult? = null,
    val scanning: Boolean = false,
    val lastScan: Long? = null,
    val message: String? = null,
    val demo: Boolean = false,
    val autoRefresh: Boolean = false,
)

class ScanViewModel(app: Application) : AndroidViewModel(app) {
    private val scanner = WifiScanner(app)
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()
    private var autoJob: Job? = null

    fun scan() {
        if (_state.value.scanning) return
        viewModelScope.launch { doScan() }
    }

    private suspend fun doScan() {
        _state.update { it.copy(scanning = true, message = null) }
        if (!scanner.isWifiEnabled) {
            _state.update { it.copy(scanning = false, message = "WLAN ist ausgeschaltet. Bitte einschalten und erneut scannen.") }
            return
        }
        try {
            val outcome = scanner.scan()
            val networks = outcome.networks
            _state.update {
                it.copy(
                    scanning = false,
                    demo = false,
                    result = Analyzer.analyze(networks),
                    lastScan = System.currentTimeMillis(),
                    message = when {
                        networks.isEmpty() ->
                            "Keine Netze gefunden. Ist der Standortdienst (GPS) eingeschaltet? Android liefert sonst keine Ergebnisse."
                        !outcome.fresh ->
                            "Android hat den Scan gedrosselt (max. 4 Scans in 2 Minuten) – angezeigt werden die zuletzt bekannten Ergebnisse."
                        else -> null
                    },
                )
            }
        } catch (e: SecurityException) {
            _state.update { it.copy(scanning = false, message = "Keine Berechtigung für den WLAN-Scan: ${e.message}") }
        }
    }

    fun loadDemo() {
        setAutoRefresh(false)
        _state.update {
            it.copy(
                result = Analyzer.analyze(DemoData.networks),
                demo = true,
                lastScan = System.currentTimeMillis(),
                message = "Demo-Daten – keine echten Messwerte.",
            )
        }
    }

    fun setAutoRefresh(enabled: Boolean) {
        autoJob?.cancel()
        autoJob = null
        _state.update { it.copy(autoRefresh = enabled) }
        if (enabled) {
            autoJob = viewModelScope.launch {
                while (isActive) {
                    if (!_state.value.scanning) doScan()
                    delay(AUTO_REFRESH_MS)
                }
            }
        }
    }

    companion object {
        /** 30 s Abstand → höchstens 4 Scans in 2 Minuten (Android-Limit). */
        const val AUTO_REFRESH_MS = 30_000L
    }
}
