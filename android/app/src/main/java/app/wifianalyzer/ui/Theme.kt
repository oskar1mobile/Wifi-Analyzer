package app.wifianalyzer.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

val SignalGood = Color(0xFF2AA876)
val SignalMedium = Color(0xFFD6A52A)
val SignalBad = Color(0xFFD64545)

/** Farben für die Kurven im Kanaldiagramm. */
val CurvePalette = listOf(
    Color(0xFF2A78D6), Color(0xFFD6532A), Color(0xFF2AA876), Color(0xFFA64AD6),
    Color(0xFFD6A52A), Color(0xFF2AB3C7), Color(0xFFC72A74), Color(0xFF6B8E23),
    Color(0xFF8A5A44), Color(0xFF5A6BD6), Color(0xFFD67A2A), Color(0xFF3D9970),
)

fun signalColor(dbm: Int): Color = when {
    dbm >= -60 -> SignalGood
    dbm >= -75 -> SignalMedium
    else -> SignalBad
}

fun interferenceColor(score: Double): Color = when {
    score < 0.5 -> SignalGood
    score < 3.0 -> SignalMedium
    else -> SignalBad
}

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme(primary = Color(0xFF8AB4F8))
        else -> lightColorScheme(primary = Color(0xFF1F5FAE))
    }
    MaterialTheme(colorScheme = colors, content = content)
}
