package com.be.music.premium

import android.app.Activity
import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.be.music.BeMusicApplication
import com.be.music.MainActivity
import com.be.music.R
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@HiltViewModel
class PremiumViewModel @Inject constructor(
    application: Application,
    private val premiumManager: PremiumManager
) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "PremiumViewModel"
        private const val ICON_FILENAME = "custom_launcher_icon.png"
        private const val ICON_SIZE = 192
        private const val SHORTCUT_ID = "custom_be_music_icon"
    }

    val premiumState: StateFlow<PremiumState> = premiumManager.state

    private val _iconUri = MutableStateFlow<String?>(null)
    val iconUri: StateFlow<String?> = _iconUri.asStateFlow()

    private val _iconMessage = MutableStateFlow<String?>(null)
    val iconMessage: StateFlow<String?> = _iconMessage.asStateFlow()

    private val _customColor = MutableStateFlow<Int?>(null)
    val customColor: StateFlow<Int?> = _customColor.asStateFlow()

    private val _keepAliveEnabled = MutableStateFlow(false)
    val keepAliveEnabled: StateFlow<Boolean> = _keepAliveEnabled.asStateFlow()

    init {
        ensureLauncherEnabled()
        loadCustomIcon()
        loadCustomColor()
        loadKeepAlive()
    }

    private fun ensureLauncherEnabled() {
        val context = getApplication<Application>()
        val componentName = ComponentName(context, MainActivity::class.java)
        val state = context.packageManager.getComponentEnabledSetting(componentName)
        if (state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED) {
            enableOriginalLauncher(context)
        }
    }

    fun clearIconMessage() {
        _iconMessage.value = null
    }

    fun watchRewardedAd(activity: Activity) {
        BeMusicApplication.rewardedAdManager.showAd(activity) {
            viewModelScope.launch {
                premiumManager.onRewardedAdCompleted()
            }
        }
    }

    fun refreshPremiumState() {
        viewModelScope.launch {
            premiumManager.checkAndRefreshPremium()
        }
    }

    fun setCustomIcon(uri: Uri) {
        viewModelScope.launch {
            try {
                val context = getApplication<Application>()

                val fileSize = withContext(Dispatchers.IO) {
                    context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: 0L
                }
                if (fileSize > 10 * 1024 * 1024) {
                    _iconMessage.value = context.getString(R.string.icon_error_too_large)
                    return@launch
                }

                val bitmap = withContext(Dispatchers.IO) {
                    decodeAndResizeIcon(uri)
                }
                if (bitmap == null) {
                    _iconMessage.value = context.getString(R.string.icon_error_decode)
                    return@launch
                }

                withContext(Dispatchers.IO) {
                    saveIconBitmap(bitmap)
                }
                bitmap.recycle()

                val success = withContext(Dispatchers.IO) {
                    replaceLauncherIcon()
                }
                loadCustomIcon()

                _iconMessage.value = if (success) {
                    context.getString(R.string.icon_success)
                } else {
                    context.getString(R.string.icon_success_saved)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to set custom icon: ${e.message}")
                _iconMessage.value = getApplication<Application>().getString(R.string.icon_error_generic)
            }
        }
    }

    fun clearCustomIcon() {
        viewModelScope.launch {
            try {
                val context = getApplication<Application>()
                withContext(Dispatchers.IO) {
                    restoreOriginalIcon()
                }
                val file = File(context.filesDir, ICON_FILENAME)
                if (file.exists()) file.delete()
                loadCustomIcon()
                _iconMessage.value = context.getString(R.string.icon_cleared)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clear icon: ${e.message}")
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun decodeAndResizeIcon(uri: Uri): Bitmap? {
        val context = getApplication<Application>()
        return try {
            val original = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = android.graphics.ImageDecoder.createSource(context.contentResolver, uri)
                android.graphics.ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                    decoder.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
                    decoder.isMutableRequired = true
                }
            } else {
                MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
            }
            Bitmap.createScaledBitmap(original, ICON_SIZE, ICON_SIZE, true)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decode icon: ${e.message}")
            null
        }
    }

    private fun saveIconBitmap(bitmap: Bitmap) {
        val context = getApplication<Application>()
        val file = File(context.filesDir, ICON_FILENAME)
        file.outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }

    private fun replaceLauncherIcon(): Boolean {
        val context = getApplication<Application>()
        val file = File(context.filesDir, ICON_FILENAME)
        if (!file.exists()) return false

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                return createPinnedShortcut(context, file)
            }
            return false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to replace launcher icon: ${e.message}")
            return false
        }
    }

    private fun disableOriginalLauncher(context: Context) {
        val componentName = ComponentName(context, MainActivity::class.java)
        context.packageManager.setComponentEnabledSetting(
            componentName,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        )
    }

    private fun enableOriginalLauncher(context: Context) {
        val componentName = ComponentName(context, MainActivity::class.java)
        context.packageManager.setComponentEnabledSetting(
            componentName,
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP
        )
    }

    private fun createPinnedShortcut(context: Context, file: File): Boolean {
        return try {
            val shortcutManager = context.getSystemService(Context.SHORTCUT_SERVICE) as android.content.pm.ShortcutManager
            if (!shortcutManager.isRequestPinShortcutSupported) return false

            val bitmap = android.graphics.BitmapFactory.decodeFile(file.absolutePath) ?: return false
            val icon = android.graphics.drawable.Icon.createWithBitmap(bitmap)

            val shortcut = android.content.pm.ShortcutInfo.Builder(context, SHORTCUT_ID)
                .setShortLabel("BE Music")
                .setLongLabel("BE Music")
                .setIcon(icon)
                .setIntent(
                    Intent(context, MainActivity::class.java).apply {
                        action = Intent.ACTION_MAIN
                        addCategory(Intent.CATEGORY_LAUNCHER)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                )
                .build()

            shortcutManager.requestPinShortcut(shortcut, null)
            bitmap.recycle()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create pinned shortcut: ${e.message}")
            false
        }
    }

    private fun restoreOriginalIcon() {
        val context = getApplication<Application>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val shortcutManager = context.getSystemService(Context.SHORTCUT_SERVICE) as android.content.pm.ShortcutManager
                try {
                    shortcutManager.disableShortcuts(listOf(SHORTCUT_ID))
                } catch (_: Exception) {}
                try {
                    shortcutManager.removeAllDynamicShortcuts()
                } catch (_: Exception) {}
            } catch (e: Exception) {
                Log.e(TAG, "Failed to remove shortcut: ${e.message}")
            }
        }
    }

    private fun loadCustomIcon() {
        val context = getApplication<Application>()
        val file = File(context.filesDir, ICON_FILENAME)
        _iconUri.value = if (file.exists()) file.absolutePath else null
    }

    fun setCustomColor(color: Int) {
        _customColor.value = color
        viewModelScope.launch {
            val context = getApplication<Application>()
            val prefs = context.getSharedPreferences("premium_prefs", Context.MODE_PRIVATE)
            prefs.edit().putInt("custom_theme_color", color).apply()
        }
    }

    fun clearCustomColor() {
        _customColor.value = null
        viewModelScope.launch {
            val context = getApplication<Application>()
            val prefs = context.getSharedPreferences("premium_prefs", Context.MODE_PRIVATE)
            prefs.edit().remove("custom_theme_color").apply()
        }
    }

    private fun loadCustomColor() {
        val context = getApplication<Application>()
        val prefs = context.getSharedPreferences("premium_prefs", Context.MODE_PRIVATE)
        val saved = prefs.getInt("custom_theme_color", Int.MIN_VALUE)
        _customColor.value = if (saved != Int.MIN_VALUE) saved else null
    }

    private fun loadKeepAlive() {
        viewModelScope.launch {
            premiumManager.keepAliveEnabled.collect { enabled ->
                _keepAliveEnabled.value = enabled
            }
        }
    }

    fun toggleKeepAlive(enabled: Boolean) {
        viewModelScope.launch {
            premiumManager.setKeepAliveEnabled(enabled)
            _keepAliveEnabled.value = enabled
        }
    }
}
