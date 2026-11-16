package app.wifianalyzer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.wifianalyzer.core.Analyzer
import app.wifianalyzer.core.Band
import app.wifianalyzer.core.Network

private const val DB_MIN = -100f
private const val DB_MAX = -20f

/**
 * Zeichnet jedes Netz als Kurve über seinem belegten Frequenzbereich;
 * die Höhe entspricht der Signalstärke in dBm.
 */
@Composable
fun ChannelGraph(networks: List<Network>, band: Band, modifier: Modifier = Modifier) {
    val nets = remember(networks, band) { networks.filter { it.band == band }.sortedBy { it.signalDbm } }
    val measurer = rememberTextMeasurer()
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val axisStyle = TextStyle(color = axisColor, fontSize = 10.sp)

    // Sichtbarer Kanalbereich und Achsenbeschriftung
    val (range, ticks) = remember(nets, band) {
        when (band) {
            Band.GHZ_2_4 -> (-1f to 15f) to (1..13).toList()
            else -> {
                val base = band.baseFrequency
                val edges = nets.flatMap { Analyzer.span(it).toList() }.map { ((it - base) / 5).toFloat() }
                val defaultLo = if (band == Band.GHZ_5) 32f else 1f
                val defaultHi = if (band == Band.GHZ_5) 68f else 49f
                val lo = minOf(defaultLo, (edges.minOrNull() ?: defaultLo) - 4f)
                val hi = maxOf(defaultHi, (edges.maxOrNull() ?: defaultHi) + 4f)
                val step = if (hi - lo > 80) 16 else if (hi - lo > 40) 8 else 4
                val first = if (band == Band.GHZ_5) 36 else 1
                (lo to hi) to generateSequence(first) { it + step }.takeWhile { it < hi }.filter { it > lo }.toList()
            }
        }
    }

    Canvas(modifier = modifier.fillMaxWidth().height(240.dp)) {
        val left = 36.dp.toPx()
        val right = size.width - 8.dp.toPx()
        val top = 8.dp.toPx()
        val bottom = size.height - 22.dp.toPx()
        val (chMin, chMax) = range
        fun x(ch: Float) = left + (ch - chMin) / (chMax - chMin) * (right - left)
        fun y(db: Float) = top + (DB_MAX - db) / (DB_MAX - DB_MIN) * (bottom - top)

        // Raster & Achsen
        var db = DB_MIN
        while (db <= DB_MAX) {
            drawLine(gridColor, Offset(left, y(db)), Offset(right, y(db)), strokeWidth = 1f)
            val label = measurer.measure("${db.toInt()}", axisStyle)
            drawText(label, topLeft = Offset(left - label.size.width - 4.dp.toPx(), y(db) - label.size.height / 2f))
            db += 10f
        }
        for (ch in ticks) {
            val label = measurer.measure("$ch", axisStyle)
            drawText(label, topLeft = Offset(x(ch.toFloat()) - label.size.width / 2f, bottom + 4.dp.toPx()))
        }

        // Kurven
        val base = band.baseFrequency
        clipRect(left = left, top = 0f, right = right, bottom = bottom) {
            nets.forEachIndexed { i, n ->
                val color = CurvePalette[i % CurvePalette.size]
                val (lo, hi) = Analyzer.span(n)
                val x0 = x(((lo - base) / 5).toFloat())
                val x1 = x(((hi - base) / 5).toFloat())
                val xc = (x0 + x1) / 2f
                val yTop = y(n.signalDbm.toFloat().coerceIn(DB_MIN, DB_MAX))
                // Eine kubische Bézierkurve erreicht nur 75 % der Kontrollpunkthöhe
                val yCtrl = (yTop - 0.25f * bottom) / 0.75f
                val path = Path().apply {
                    moveTo(x0, bottom)
                    cubicTo(x0 + (xc - x0) * 0.35f, yCtrl, x1 - (x1 - xc) * 0.35f, yCtrl, x1, bottom)
                }
                drawPath(path, color.copy(alpha = 0.14f))
                drawPath(path, color, style = Stroke(width = if (n.connected) 3.dp.toPx() else 1.5.dp.toPx()))
                val label = measurer.measure(
                    n.displaySsid.take(16),
                    TextStyle(color = color, fontSize = 9.sp),
                )
                drawText(label, topLeft = Offset(xc - label.size.width / 2f, yTop - label.size.height - 2f))
            }
        }
    }
}
