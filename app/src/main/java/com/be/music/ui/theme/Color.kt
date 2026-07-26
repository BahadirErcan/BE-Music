package com.be.music.ui.theme

import androidx.compose.ui.graphics.Color

// Dark Theme Palette
val Primary = Color(0xFF1DB954)
val DarkBg = Color(0xFF121212)
val DarkSurface = Color(0xFF181818)
val DarkSurfaceVariant = Color(0xFF232323)
val DarkTextPrimary = Color(0xFFFFFFFF)
val DarkTextSecondary = Color(0xFFB3B3B3)
val DarkBorder = Color(0xFF3E3E3E)

val DarkPrimary = Color(0xFF1DB954)
val DarkOnPrimary = Color(0xFF000000)
val DarkSecondary = Color(0xFF535353)
val DarkOnSecondary = Color(0xFFFFFFFF)
val DarkTertiary = Color(0xFF1ED760)
val DarkOnTertiary = Color(0xFF000000)

// Light Theme Palette (Optional)
val LightPrimary = Color(0xFF1DB954)
val LightOnPrimary = Color(0xFFFFFFFF)
val LightSecondary = Color(0xFF535353)
val LightOnSecondary = Color(0xFFFFFFFF)
val LightTertiary = Color(0xFF1ED760)
val LightOnTertiary = Color(0xFFFFFFFF)
val LightBg = Color(0xFFFFFFFF)
val LightSurface = Color(0xFFF5F5F5)
val LightSurfaceVariant = Color(0xFFEEEEEE)
val LightTextPrimary = Color(0xFF000000)
val LightTextSecondary = Color(0xFF666666)
val LightBorder = Color(0xFFDDDDDD)

// Additional color options
val DefaultAccentColor = Color(0xFF1DB954)
val GreenColor = Color(0xFF1DB954)
val BlueColor = Color(0xFF2196F3)
val RedColor = Color(0xFFF44336)
val PurpleColor = Color(0xFF9C27B0)
val YellowColor = Color(0xFFFFC107)

fun colorFromName(name: String): Color {
	return when (name.uppercase()) {
		"GREEN" -> GreenColor
		"BLUE" -> BlueColor
		"RED" -> RedColor
		"PURPLE" -> PurpleColor
		"YELLOW" -> YellowColor
		else -> DefaultAccentColor
	}
}
