package com.be.music

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import androidx.core.view.WindowCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.be.music.ui.AppNavigation
import com.be.music.ui.MusicViewModel
import com.be.music.ui.ThemeStyle
import com.be.music.ui.theme.MyApplicationTheme
import com.be.music.ui.NotificationHelper
import dagger.hilt.android.AndroidEntryPoint
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var notificationHelper: NotificationHelper

    override fun attachBaseContext(newBase: Context) {
        val locale = getSavedLocale(newBase)
        Locale.setDefault(locale)
        val config = Configuration(newBase.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    @SuppressLint("ContextCastToActivity")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        notificationHelper.createNotificationChannels()

        setContent {
            val viewModel: MusicViewModel = hiltViewModel()
            val themeStyle by viewModel.themeStyle.collectAsState()
            val filterSettings by viewModel.filterSettingsState.collectAsState()

            val useDarkTheme = when (themeStyle) {
                ThemeStyle.LIGHT -> false
                ThemeStyle.DARK -> true
                ThemeStyle.SYSTEM -> isSystemInDarkTheme()
            }

            val selectedColorName = filterSettings.themeColor
            val useDynamicAccent = selectedColorName.uppercase() == "ACCENT"
            val customColorValue = if (selectedColorName == "CUSTOM") {
                val prefs = getSharedPreferences("premium_prefs", MODE_PRIVATE)
                val saved = prefs.getInt("custom_theme_color", Int.MIN_VALUE)
                if (saved != Int.MIN_VALUE) saved else null
            } else null

            val navBarBottomDp = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            val isThreeButtonNav = navBarBottomDp > 40.dp
            val density = LocalDensity.current
            val navBarHeightPx = remember(density, navBarBottomDp) {
                with(density) { navBarBottomDp.roundToPx() }
            }

            MyApplicationTheme(darkTheme = useDarkTheme, dynamicColor = useDynamicAccent, colorName = selectedColorName, customColor = customColorValue) {
                val primaryColor = MaterialTheme.colorScheme.primary.toArgb()
                val window = this@MainActivity.window
                val controller = WindowCompat.getInsetsController(window, window.decorView)

                SideEffect {
                    viewModel.updateNavigationBarHeight(navBarHeightPx)
                    viewModel.updateIsThreeButtonNav(isThreeButtonNav)

                    if (!useDarkTheme) {
                        window.statusBarColor = Color.TRANSPARENT
                        controller.isAppearanceLightStatusBars = true
                        window.navigationBarColor = Color.TRANSPARENT
                        controller.isAppearanceLightNavigationBars = true
                    } else {
                        val darkColor = ColorUtils.blendARGB(primaryColor, Color.BLACK, 0.25f)
                        window.statusBarColor = darkColor
                        val isLightBg = ColorUtils.calculateLuminance(darkColor) > 0.5
                        controller.isAppearanceLightStatusBars = isLightBg
                        window.navigationBarColor = Color.TRANSPARENT
                        controller.isAppearanceLightNavigationBars = !isLightBg
                    }
                }

                AppNavigation(viewModel = viewModel)
            }
        }
    }

    private fun getSavedLocale(context: Context): Locale {
        val prefs = context.getSharedPreferences("app_prefs", MODE_PRIVATE)
        val savedLang = prefs.getString("app_language", "SYSTEM") ?: "SYSTEM"
        if (savedLang == "SYSTEM") return Locale.getDefault()
        return Locale(savedLang)
    }
}
