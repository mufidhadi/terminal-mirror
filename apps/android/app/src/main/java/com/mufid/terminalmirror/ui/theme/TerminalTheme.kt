package com.mufid.terminalmirror.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Single source of truth for Terminal Mirror colors.
 *
 * All values below are the palette already proven on-device in screenshots;
 * this object only centralizes them so future dark/light/dynamic schemes can
 * swap one place instead of hunting hex literals across composables.
 * No composable outside this file may use a `Color(0xFF…)` literal.
 */
object TerminalColors {
    // Layout surfaces
    val ScreenBackground = Color(0xFF121212)
    val ViewportBackground = Color(0xFF0D1117)
    val TopBarBackground = Color(0xFF181818)
    val TabBackground = Color(0xFF202020)
    val CardBackground = Color(0xFF161B22)
    val InputBackground = Color(0xFF1F242C)
    val AccessoryBackground = Color(0xFF242424)
    val AccessoryButton = Color(0xFF383838)

    // Text
    val PrimaryText = Color.White
    val SubtitleText = Color(0xFFAAAAAA)
    val MutedText = Color(0xFF8B949E)
    val TerminalText = Color(0xFF58A6FF)

    // Accents & actions
    val Accent = Color(0xFF58A6FF)
    val AccentLight = Color(0xFF64B5F6)
    val Send = Color(0xFF238636)

    // Status semantics
    val Live = Color(0xFF1B5E20)
    val LiveBright = Color(0xFF4CAF50)
    val Offline = Color(0xFFB71C1C)
    val Warning = Color(0xFFFFB74D)
    val WarningBackground = Color(0xFF3A3325)
    val Unlocked = Color(0xFFFF5722)
    val Kill = Color(0xFFB71C1C)
    val TabInactive = Color(0xFF888888)
}
