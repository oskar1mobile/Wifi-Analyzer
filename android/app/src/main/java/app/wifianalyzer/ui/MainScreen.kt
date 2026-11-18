package app.wifianalyzer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.wifianalyzer.UiState
import app.wifianalyzer.core.AnalysisResult
import app.wifianalyzer.core.Band
import app.wifianalyzer.core.ChannelScore
import app.wifianalyzer.core.Finding
import app.wifianalyzer.core.Level
import app.wifianalyzer.core.Network
import app.wifianalyzer.core.signalLabel
import java.text.DateFormat
import java.util.Date
import java.util.Locale

private enum class Tab(val label: String, val icon: ImageVector) {
    NETWORKS("Netzwerke", Icons.AutoMirrored.Filled.List),
    CHANNELS("Kanäle", Icons.Default.Search),
    ANALYSIS("Analyse", Icons.Default.Info),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    state: UiState,
    hasPermission: Boolean,
    onScan: () -> Unit,
    onRequestPermission: () -> Unit,
    onDemo: () -> Unit,
    onAutoRefresh: (Boolean) -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(Tab.NETWORKS) }
    var menuOpen by remember { mutableStateOf(false) }
    var showAbout by rememberSaveable { mutableStateOf(false) }

    if (showAbout) AboutDialog(onDismiss = { showAbout = false })

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("WLAN-Analyzer")
                        state.lastScan?.let {
                            Text(
                                (if (state.demo) "Demo · " else "Scan ") +
                                    DateFormat.getTimeInstance(DateFormat.MEDIUM, Locale.GERMANY).format(Date(it)),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                actions = {
                    if (state.scanning) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp).padding(2.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                    } else {
                        IconButton(onClick = onScan) { Icon(Icons.Default.Refresh, contentDescription = "Scannen") }
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Menü")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Automatisch scannen (30 s)") },
                                leadingIcon = { Checkbox(checked = state.autoRefresh, onCheckedChange = null) },
                                enabled = hasPermission,
                                onClick = {
                                    onAutoRefresh(!state.autoRefresh)
                                    menuOpen = false
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Demo-Daten anzeigen") },
                                onClick = {
                                    onDemo()
                                    menuOpen = false
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Über die App") },
                                leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                                onClick = {
                                    showAbout = true
                                    menuOpen = false
                                },
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Icon(t.icon, contentDescription = null) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            state.message?.let { MessageBanner(it) }
            val result = state.result
            when {
                result == null && !hasPermission -> PermissionHint(onRequestPermission, onDemo)
                result == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (state.scanning) CircularProgressIndicator() else Text("Noch kein Scan.")
                }
                else -> when (tab) {
                    Tab.NETWORKS -> NetworksTab(result)
                    Tab.CHANNELS -> ChannelsTab(result)
                    Tab.ANALYSIS -> AnalysisTab(result)
                }
            }
        }
    }
}

@Composable
private fun MessageBanner(text: String) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Text(text, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun PermissionHint(onRequest: () -> Unit, onDemo: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(16.dp))
        Text("Standortberechtigung benötigt", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "Android gibt WLAN-Scanergebnisse nur an Apps mit Standortberechtigung heraus. " +
                "Die App speichert oder versendet keine Daten.",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRequest) { Text("Berechtigung erteilen") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onDemo) { Text("Demo ansehen") }
    }
}

// --------------------------------------------------------------------------
// Netzwerke
// --------------------------------------------------------------------------

@Composable
private fun BandFilter(bands: List<Band>, selected: Band?, allowAll: Boolean, onSelect: (Band?) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (allowAll) FilterChip(selected = selected == null, onClick = { onSelect(null) }, label = { Text("Alle") })
        bands.forEach { b ->
            FilterChip(selected = selected == b, onClick = { onSelect(b) }, label = { Text(b.label) })
        }
    }
}

private fun presentBands(result: AnalysisResult): List<Band> =
    Band.entries.filter { it != Band.UNKNOWN && (result.bandCounts[it] ?: 0) > 0 }

@Composable
private fun NetworksTab(result: AnalysisResult) {
    var band by rememberSaveable { mutableStateOf<Band?>(null) }
    val list = result.networks.filter { band == null || it.band == band }
    Column {
        BandFilter(presentBands(result), band, allowAll = true) { band = it }
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(list, key = { it.bssid + it.frequency }) { NetworkCard(it) }
        }
    }
}

@Composable
private fun NetworkCard(n: Network) {
    val border = if (n.connected) CardDefaults.outlinedCardBorder() else null
    Card(modifier = Modifier.fillMaxWidth(), border = border) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (n.connected) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "Verbunden",
                            tint = SignalGood,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        n.displaySsid,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (n.connected) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    n.bssid,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Kanal ${n.channel} · ${n.band.label} · ${n.widthMhz} MHz",
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val weak = n.security.weak
                    Icon(
                        if (weak) Icons.Default.Warning else Icons.Default.Lock,
                        contentDescription = null,
                        tint = if (weak) SignalBad else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        n.security.label,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (weak) SignalBad else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.width(96.dp)) {
                Text(
                    "${n.signalDbm} dBm",
                    style = MaterialTheme.typography.titleMedium,
                    color = signalColor(n.signalDbm),
                    fontWeight = FontWeight.SemiBold,
                )
                Text(signalLabel(n.signalDbm), style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { n.quality / 100f },
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                    color = signalColor(n.signalDbm),
                    strokeCap = StrokeCap.Round,
                )
            }
        }
    }
}

// --------------------------------------------------------------------------
// Kanäle
// --------------------------------------------------------------------------

@Composable
private fun ChannelsTab(result: AnalysisResult) {
    val bands = presentBands(result).ifEmpty { listOf(Band.GHZ_2_4) }
    var selected by rememberSaveable { mutableStateOf(bands.first()) }
    val band = if (selected in bands) selected else bands.first()
    val (scores, best) = when (band) {
        Band.GHZ_2_4 -> result.channels24 to result.best24
        Band.GHZ_5 -> result.channels5 to result.best5
        else -> emptyList<ChannelScore>() to emptyList()
    }
    val bestChannel = best.firstOrNull()?.channel
    val maxScore = maxOf(4.0, scores.maxOfOrNull { it.interference } ?: 0.0)

    LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
        item { BandFilter(bands, band, allowAll = false) { if (it != null) selected = it } }
        item {
            Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Column(Modifier.padding(8.dp)) {
                    Text(
                        "Kanalbelegung ${band.label}",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 8.dp),
                    )
                    ChannelGraph(result.networks, band)
                }
            }
        }
        if (scores.isEmpty()) {
            item {
                Text(
                    "Für ${band.label} gibt es keine Kanalbewertung.",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        } else {
            item {
                Text(
                    "Interferenz je Kanal (niedriger ist besser)",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            items(scores, key = { it.channel }) { s ->
                ChannelRow(s, maxScore, recommended = s.channel == bestChannel)
            }
        }
    }
}

@Composable
private fun ChannelRow(s: ChannelScore, maxScore: Double, recommended: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.width(56.dp)) {
            Text("${s.channel}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            if (s.dfs) {
                Text("DFS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Box(
            Modifier.weight(1f).height(10.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(5.dp)),
        ) {
            val fraction = (s.interference / maxScore).toFloat().coerceIn(0f, 1f)
            if (fraction > 0f) {
                Box(
                    Modifier.fillMaxWidth(fraction).height(10.dp)
                        .background(interferenceColor(s.interference), RoundedCornerShape(5.dp)),
                )
            }
        }
        Text(
            "%.1f".format(Locale.GERMANY, s.interference),
            modifier = Modifier.width(40.dp),
            textAlign = TextAlign.End,
            style = MaterialTheme.typography.bodySmall,
        )
        Box(Modifier.width(92.dp).padding(start = 8.dp)) {
            if (recommended) {
                Text(
                    "empfohlen",
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.background(SignalGood, RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 2.dp),
                )
            } else {
                Text(s.rating.label, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

// --------------------------------------------------------------------------
// Analyse
// --------------------------------------------------------------------------

@Composable
private fun AnalysisTab(result: AnalysisResult) {
    val c = result.connected
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard("Access Points", "${result.networks.size}", Modifier.weight(1f))
                StatCard(
                    "2.4 / 5 GHz",
                    "${result.bandCounts[Band.GHZ_2_4] ?: 0} / ${result.bandCounts[Band.GHZ_5] ?: 0}",
                    Modifier.weight(1f),
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard("Bester Kanal 2.4", result.best24.firstOrNull()?.channel?.toString() ?: "–", Modifier.weight(1f))
                StatCard("Bester Kanal 5", result.best5.firstOrNull()?.channel?.toString() ?: "–", Modifier.weight(1f))
            }
        }
        if (c != null) {
            item {
                StatCard(
                    "Verbunden",
                    "${c.displaySsid} · ${c.signalDbm} dBm · Kanal ${c.channel}",
                    Modifier.fillMaxWidth(),
                )
            }
        }
        item {
            Text(
                "Ergebnisse & Empfehlungen",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        items(result.findings) { FindingRow(it) }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun FindingRow(f: Finding) {
    val (icon, tint) = when (f.level) {
        Level.INFO -> Icons.Default.Info to MaterialTheme.colorScheme.primary
        Level.WARNING -> Icons.Default.Warning to SignalMedium
        Level.CRITICAL -> Icons.Default.Warning to SignalBad
    }
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(f.message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
