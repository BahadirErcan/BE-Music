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

private fun Color.toHsl(): FloatArray {
  val r = red
  val g = green
  val b = blue
  val max = maxOf(r, g, b)
  val min = minOf(r, g, b)
  val l = (max + min) / 2f
  val h: Float
  val s: Float
  if (max == min) {
    h = 0f; s = 0f
  } else {
    val d = max - min
    s = if (l > 0.5f) d / (2f - max - min) else d / (max + min)
    h = when (max) {
      r -> ((g - b) / d + if (g < b) 6f else 0f) / 6f
      g -> ((b - r) / d + 2f) / 6f
      else -> ((r - g) / d + 4f) / 6f
    }
  }
  return floatArrayOf(h * 360f, s * 100f, l * 100f)
}

private fun hslToColor(h: Float, s: Float, l: Float): Color {
  val hNorm = (h % 360f) / 360f
  val sNorm = (s.coerceIn(0f, 100f)) / 100f
  val lNorm = (l.coerceIn(0f, 100f)) / 100f
  if (sNorm == 0f) return Color(lNorm, lNorm, lNorm)
  fun hue2rgb(p: Float, q: Float, t: Float): Float {
    val t2 = when {
      t < 0f -> t + 1f
      t > 1f -> t - 1f
      else -> t
    }
    return when {
      t2 < 1f / 6f -> p + (q - p) * 6f * t2
      t2 < 1f / 2f -> q
      t2 < 2f / 3f -> p + (q - p) * (2f / 3f - t2) * 6f
      else -> p
    }
  }
  val q = if (lNorm < 0.5f) lNorm * (1f + sNorm) else lNorm + sNorm - lNorm * sNorm
  val p = 2f * lNorm - q
  return Color(
    hue2rgb(p, q, hNorm + 1f / 3f),
    hue2rgb(p, q, hNorm),
    hue2rgb(p, q, hNorm - 1f / 3f)
  )
}

private fun Color.deriveSecondary(): Color {
  val hsl = toHsl()
  return hslToColor((hsl[0] + 30f) % 360f, (hsl[1] * 0.7f).coerceAtLeast(20f), hsl[2])
}

private fun Color.deriveTertiary(): Color {
  val hsl = toHsl()
  return hslToColor((hsl[0] + 60f) % 360f, (hsl[1] * 0.8f).coerceAtLeast(25f), hsl[2])
}

private fun deriveOnColor(forColor: Color): Color {
  val hsl = forColor.toHsl()
  return if (hsl[2] > 55f) Color.Black else Color.White
}

private fun deriveContainerColor(forColor: Color): Color {
  val hsl = forColor.toHsl()
  return hslToColor(hsl[0], (hsl[1] * 0.3f).coerceAtLeast(10f), if (hsl[2] > 55f) 92f else 20f)
}

private fun deriveOnContainerColor(forColor: Color): Color {
  val hsl = forColor.toHsl()
  return if (hsl[2] > 55f) hslToColor(hsl[0], hsl[1], 15f) else hslToColor(hsl[0], hsl[1], 90f)
}

private fun deriveDarkBackground(primary: Color): Color {
  val hsl = primary.toHsl()
  return hslToColor(hsl[0], (hsl[1] * 0.08f).coerceAtMost(12f), 8f)
}

private fun deriveDarkSurface(primary: Color): Color {
  val hsl = primary.toHsl()
  return hslToColor(hsl[0], (hsl[1] * 0.10f).coerceAtMost(14f), 11f)
}

private fun deriveDarkSurfaceVariant(primary: Color): Color {
  val hsl = primary.toHsl()
  return hslToColor(hsl[0], (hsl[1] * 0.12f).coerceAtMost(16f), 16f)
}

private fun deriveLightBackground(primary: Color): Color {
  val hsl = primary.toHsl()
  return hslToColor(hsl[0], (hsl[1] * 0.12f).coerceAtMost(15f), 98f)
}

private fun deriveLightSurface(primary: Color): Color {
  val hsl = primary.toHsl()
  return hslToColor(hsl[0], (hsl[1] * 0.10f).coerceAtMost(12f), 96f)
}

private fun deriveLightSurfaceVariant(primary: Color): Color {
  val hsl = primary.toHsl()
  return hslToColor(hsl[0], (hsl[1] * 0.08f).coerceAtMost(10f), 93f)
}

private fun generateDarkColorScheme(primary: Color) = darkColorScheme(
  primary = primary,
  onPrimary = deriveOnColor(primary),
  primaryContainer = primary.copy(alpha = 0.15f),
  onPrimaryContainer = primary.copy(alpha = 0.9f),
  secondary = primary.deriveSecondary(),
  onSecondary = deriveOnColor(primary.deriveSecondary()),
  secondaryContainer = primary.deriveSecondary().copy(alpha = 0.15f),
  onSecondaryContainer = primary.deriveSecondary().copy(alpha = 0.9f),
  tertiary = primary.deriveTertiary(),
  onTertiary = deriveOnColor(primary.deriveTertiary()),
  tertiaryContainer = primary.deriveTertiary().copy(alpha = 0.15f),
  onTertiaryContainer = primary.deriveTertiary().copy(alpha = 0.9f),
  background = deriveDarkBackground(primary),
  onBackground = Color(0xFFE0E0E0),
  surface = deriveDarkSurface(primary),
  onSurface = Color(0xFFE0E0E0),
  surfaceVariant = deriveDarkSurfaceVariant(primary),
  onSurfaceVariant = Color(0xFFB0B0B0),
  outline = primary.copy(alpha = 0.25f)
)

private fun generateLightColorScheme(primary: Color) = lightColorScheme(
  primary = primary,
  onPrimary = deriveOnColor(primary),
  primaryContainer = deriveContainerColor(primary),
  onPrimaryContainer = deriveOnContainerColor(primary),
  secondary = primary.deriveSecondary(),
  onSecondary = deriveOnColor(primary.deriveSecondary()),
  secondaryContainer = deriveContainerColor(primary.deriveSecondary()),
  onSecondaryContainer = deriveOnContainerColor(primary.deriveSecondary()),
  tertiary = primary.deriveTertiary(),
  onTertiary = deriveOnColor(primary.deriveTertiary()),
  tertiaryContainer = deriveContainerColor(primary.deriveTertiary()),
  onTertiaryContainer = deriveOnContainerColor(primary.deriveTertiary()),
  background = deriveLightBackground(primary),
  onBackground = Color(0xFF1C1B1F),
  surface = deriveLightSurface(primary),
  onSurface = Color(0xFF1C1B1F),
  surfaceVariant = deriveLightSurfaceVariant(primary),
  onSurfaceVariant = Color(0xFF49454F),
  outline = primary.copy(alpha = 0.30f)
)

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
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
        if (darkTheme) {
          generateDarkColorScheme(primary)
        } else {
          generateLightColorScheme(primary)
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
