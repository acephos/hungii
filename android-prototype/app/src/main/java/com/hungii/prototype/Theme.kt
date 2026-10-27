package com.hungii.prototype

import android.provider.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily

internal val Charcoal = Color(0xFF09090C)
internal val Surface = Color(0xFF171317)
internal val Raised = Color(0xFF221A20)
internal val Accent = Color(0xFFFF526F)
internal val AccentShade = Color(0xFFE93D60)
internal val Crimson = Color(0xFFB91D40)
internal val AccentWash = Color(0xFF3B1825)
internal val White = Color(0xFFFFF2F5)
internal val Muted = Color(0xFFC0ADB5)
internal val Line = Color(0xFF41303A)
internal val SoftCrimson = Color(0xFFFF8A9D)
internal val Rose = Color(0xFFEAB7C8)
internal val Display = FontFamily.SansSerif

private val HungiiColors =
    darkColorScheme(
        primary = Accent,
        onPrimary = Charcoal,
        background = Charcoal,
        surface = Surface,
        onSurface = White,
        onBackground = White,
        onSurfaceVariant = Muted,
        surfaceVariant = Raised,
        secondary = Rose,
        onSecondary = Charcoal,
        primaryContainer = AccentWash,
        onPrimaryContainer = White,
        secondaryContainer = Raised,
        onSecondaryContainer = Rose,
        surfaceTint = Accent,
        error = SoftCrimson,
        onError = Charcoal,
        outline = Line,
    )

@Composable
internal fun HungiiTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = HungiiColors, content = content)

/** Honors the system "remove animations" setting. */
@Composable
internal fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
}
