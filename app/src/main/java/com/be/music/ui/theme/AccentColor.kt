package com.be.music.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Kullanıcının ayarlardan seçebileceği accent renkler.
 * Sırası: GREEN, BLUE, RED, PURPLE, YELLOW, ACCENT
 * "ACCENT" kullanıcının isteği üzerine eklenen turuncu/vurgu rengi.
 */
enum class AccentColor(
    val displayKey: String,
    val primary: Color,
    val onPrimary: Color,
    val secondary: Color,
    val container: Color,
    val onContainer: Color
) {
    GREEN(
        displayKey = "GREEN",
        primary = GreenColor,
        onPrimary = Color.White,
        secondary = GreenColor,
        container = GreenColor.copy(alpha = 0.12f),
        onContainer = Color.White
    ),
    BLUE(
        displayKey = "BLUE",
        primary = BlueColor,
        onPrimary = Color.White,
        secondary = BlueColor,
        container = BlueColor.copy(alpha = 0.12f),
        onContainer = Color.White
    ),
    RED(
        displayKey = "RED",
        primary = RedColor,
        onPrimary = Color.White,
        secondary = RedColor,
        container = RedColor.copy(alpha = 0.12f),
        onContainer = Color.White
    ),
    PURPLE(
        displayKey = "PURPLE",
        primary = PurpleColor,
        onPrimary = Color.White,
        secondary = PurpleColor,
        container = PurpleColor.copy(alpha = 0.12f),
        onContainer = Color.White
    ),
    YELLOW(
        displayKey = "YELLOW",
        primary = YellowColor,
        onPrimary = Color.Black,
        secondary = YellowColor,
        container = YellowColor.copy(alpha = 0.12f),
        onContainer = Color.Black
    ),
    ACCENT(
        displayKey = "ACCENT",
        primary = DefaultAccentColor,
        onPrimary = Color.White,
        secondary = DefaultAccentColor,
        container = DefaultAccentColor.copy(alpha = 0.12f),
        onContainer = Color.White
    );

    companion object {
        fun fromKey(key: String?): AccentColor =
            values().firstOrNull { it.displayKey == key } ?: GREEN
    }
}
