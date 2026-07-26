package com.be.music.ui.theme

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

private val DarkColorScheme =
  darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkSurface,
    onPrimaryContainer = DarkTextPrimary,
    secondary = DarkSecondary,
    onSecondary = DarkOnSecondary,
    secondaryContainer = DarkSurface,
    onSecondaryContainer = DarkTextPrimary,
    tertiary = DarkTertiary,
    onTertiary = DarkOnTertiary,
    background = DarkBg,
    onBackground = DarkTextPrimary,
    surface = DarkBg,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkTextSecondary,
    outline = DarkBorder
  )

private val LightColorScheme =
  lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightSurfaceVariant,
    onPrimaryContainer = LightTextPrimary,
    secondary = LightSecondary,
    onSecondary = LightOnSecondary,
    secondaryContainer = LightSurface,
    onSecondaryContainer = LightTextPrimary,
    tertiary = LightTertiary,
    onTertiary = LightOnTertiary,
    background = LightBg,
    onBackground = LightTextPrimary,
    surface = LightBg,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightTextSecondary,
    outline = LightBorder
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Dynamic color is available on Android 12+
  dynamicColor: Boolean = false,
  colorName: String = "ACCENT",
  customColor: Int? = null,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      else -> {
        val primary = if (colorName == "CUSTOM" && customColor != null) {
          Color(customColor)
        } else {
          colorFromName(colorName)
        }
        val onPrimary = if (colorName.uppercase() == "YELLOW" || (colorName == "CUSTOM" && customColor != null && isColorLight(customColor))) {
          androidx.compose.ui.graphics.Color.Black
        } else {
          androidx.compose.ui.graphics.Color.White
        }
        if (darkTheme) {
          darkColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = DarkSurface,
            onPrimaryContainer = DarkTextPrimary,
            secondary = DarkSecondary,
            onSecondary = DarkOnSecondary,
            secondaryContainer = DarkSurface,
            onSecondaryContainer = DarkTextPrimary,
            tertiary = DarkTertiary,
            onTertiary = DarkOnTertiary,
            background = DarkBg,
            onBackground = DarkTextPrimary,
            surface = DarkBg,
            onSurface = DarkTextPrimary,
            surfaceVariant = DarkSurfaceVariant,
            onSurfaceVariant = DarkTextSecondary,
            outline = DarkBorder
          )
        } else {
          lightColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = LightSurfaceVariant,
            onPrimaryContainer = LightTextPrimary,
            secondary = LightSecondary,
            onSecondary = LightOnSecondary,
            secondaryContainer = LightSurface,
            onSecondaryContainer = LightTextPrimary,
            tertiary = LightTertiary,
            onTertiary = LightOnTertiary,
            background = LightBg,
            onBackground = LightTextPrimary,
            surface = LightBg,
            onSurface = LightTextPrimary,
            surfaceVariant = LightSurfaceVariant,
            onSurfaceVariant = LightTextSecondary,
            outline = LightBorder
          )
        }
      }
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}

private fun isColorLight(colorInt: Int): Boolean {
  val r = (colorInt shr 16) and 0xFF
  val g = (colorInt shr 8) and 0xFF
  val b = colorInt and 0xFF
  val luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
  return luminance > 0.5
}
