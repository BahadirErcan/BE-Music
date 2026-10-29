package com.be.music.player

import android.media.audiofx.Equalizer
import android.util.Log

/**
 * Ekolayzer yöneticisi.
 *
 * Android'in yerleşik [Equalizer]'ını oynatıcı (ExoPlayer) audio session'ına bağlar;
 * bant/gain sınırlarını sorgular, ayarları canlı uygular ve SharedPreferences'ta
 * ("equalizer_prefs") kalıcı hale getirir. Uygulama yeniden açıldığında ya da
 * session yeniden oluşturulduğunda ayarlar geri yüklenir.
 *
 * Tekil (singleton) olarak tasarlanmıştır: hem PlaybackService (bağlama) hem de
 * EqualizerScreen (arayüz) aynı instance üzerinden çalışır. Aynı audio session'ına
 * ikinci bir Equalizer instance'ı bağlanmaz (çakışmayı önlemek için).
 */
object EqualizerManager {

    private const val PREFS = "equalizer_prefs"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_LEVELS = "levels" // virgülle ayrılmış bant seviyeleri (milliBel)
    private const val KEY_PRESET = "preset" // cihaz preset indeksi veya -1 (Özel)

    private var equalizer: Equalizer? = null
    private var sessionId = Int.MIN_VALUE
    private var cachedBandCount = 0
    private var cachedGainRange = Pair(0, 0)   // milliBel (min, max)
    private var cachedBandFrequencies: IntArray = IntArray(0) // mHz merkez frekanslar

    /** Cihaz preset indeksi bağlı değilken UI'ın göstermesi için saklanır. */
    var selectedPreset: Int = -1 // -1 = Özel / yok
        private set

    /** Ekolayzer şu an session'a bağlı mı (oynatma session'ı var mı)? */
    val isAttached: Boolean get() = equalizer != null

    /**
     * Oynatıcı audio session'ına bağlanır. Zaten bağlıysa veya session id geçersizse işlem yapmaz.
     * Bağlanınca kayıtlı ayarlar geri yüklenir.
     */
    @Synchronized
    fun attach(sessionId: Int) {
        if (sessionId <= 0) return
        if (equalizer != null && this.sessionId == sessionId) return

        try {
            if (equalizer != null) {
                detach()
            }
            this.sessionId = sessionId
            val eq = Equalizer(0, sessionId)
            equalizer = eq

            cachedBandCount = eq.numberOfBands.toInt()
            val range = eq.bandLevelRange
            cachedGainRange = Pair(range[0].toInt(), range[1].toInt())
            cachedBandFrequencies = IntArray(cachedBandCount) { band ->
                eq.getCenterFreq(band.toShort())
            }

            restoreSettings(eq)
            Log.d("EqualizerManager", "Session $sessionId bağlandı, bant sayısı: $cachedBandCount")
        } catch (e: Exception) {
            Log.e("EqualizerManager", "Ekolayzer bağlanamadı (session=$sessionId)", e)
            equalizer = null
        }
    }

    @Synchronized
    fun detach() {
        try {
            equalizer?.enabled = false
            equalizer?.release()
        } catch (_: Exception) {}
        equalizer = null
        this.sessionId = Int.MIN_VALUE
    }

    // ---------- Durum / sorgular ----------

    /** Bağlı değilken arayüzün önizleme gösterebilmesi için varsayılan bant sayısı. */
    private const val FALLBACK_BANDS = 5

    private val fallbackFrequencies = intArrayOf(60000, 230000, 950000, 3600000, 14000000) // mHz
    private val fallbackGainRange = Pair(-1500, 1500) // mB

    val numberOfBands: Int get() = cachedBandCount.takeIf { it > 0 } ?: FALLBACK_BANDS

    /** Bant merkez frekansı (mHz). Bağlı değilse varsayılan değer döner. */
    fun bandCenterFreqMhz(band: Int): Int =
        cachedBandFrequencies.getOrNull(band) ?: fallbackFrequencies.getOrNull(band) ?: 0

    /** Gain aralığı (milliBel). Bağlı değilse tipik eşit aralık döner. */
    fun gainRange(): Pair<Int, Int> {
        if (cachedGainRange.first != 0 || cachedGainRange.second != 0) return cachedGainRange
        equalizer?.let {
            val r = it.bandLevelRange
            cachedGainRange = Pair(r[0].toInt(), r[1].toInt())
        }
        return cachedGainRange
    }

    var enabled: Boolean
        get() {
            return try {
                equalizer?.enabled ?: getPersistedEnabled()
            } catch (e: Exception) {
                getPersistedEnabled()
            }
        }
        set(value) {
            setPersistedEnabled(value)
            try {
                equalizer?.enabled = value
            } catch (e: Exception) {
                Log.e("EqualizerManager", "enabled yazılamadı", e)
            }
        }

    fun getBandLevel(band: Int): Int {
        return try {
            equalizer?.getBandLevel(band.toShort())?.toInt() ?: getBandLevelFromPrefs(band)
        } catch (e: Exception) {
            getBandLevelFromPrefs(band)
        }
    }

    fun setBandLevel(band: Int, levelMillibel: Int) {
        // Kullanıcı slider'ı oynattı -> Özel moda geç
        markPreset(-1)
        try {
            equalizer?.setBandLevel(band.toShort(), levelMillibel.toShort())
        } catch (e: Exception) {
            Log.e("EqualizerManager", "Bant seviyesi uygulanamadı (band=$band)", e)
        }
        persistBandLevel(band, levelMillibel)
    }

    fun applyPreset(presetIndex: Int) {
        selectedPreset = presetIndex
        setPersistedPreset(presetIndex)
        try {
            equalizer?.usePreset(presetIndex.toShort())
            // preset uygulanınca bant seviyelerini prefs'e senkronize et
            if (equalizer != null && cachedBandCount > 0) {
                val levels = IntArray(cachedBandCount) { b -> getBandLevel(b) }
                setPersistedLevels(levels)
            }
        } catch (e: Exception) {
            Log.e("EqualizerManager", "Preset uygulanamadı: $presetIndex", e)
        }
    }

    fun presetCount(): Int = try {
        (equalizer?.numberOfPresets ?: 0).toInt()
    } catch (e: Exception) {
        0
    }

    fun presetName(index: Int): String = try {
        equalizer?.getPresetName(index.toShort()) ?: ""
    } catch (e: Exception) {
        ""
    }

    /** Tüm bantları 0 dB'e getirir ve "Özel" moduna geçer. */
    fun resetBandLevels() {
        try {
            if (equalizer != null) {
                for (b in 0 until cachedBandCount) {
                    equalizer!!.setBandLevel(b.toShort(), 0)
                }
            }
        } catch (e: Exception) {
            Log.e("EqualizerManager", "Reset hatası", e)
        }
        setPersistedLevels(IntArray(cachedBandCount.coerceAtLeast(5)))
        markPreset(-1)
    }

    // ---------- Kalıcılık ----------

    private fun prefs(): android.content.SharedPreferences? {
        val ctx = appContext ?: return null
        return ctx.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
    }

    // Uygulama context'i PlaybackService/EqualizerScreen tarafından beslenir
    @Volatile
    var appContext: android.content.Context? = null

    private fun getPersistedEnabled(): Boolean = try {
        prefs()?.getBoolean(KEY_ENABLED, false) ?: false
    } catch (e: Exception) {
        false
    }

    private fun setPersistedEnabled(value: Boolean) {
        try {
            prefs()?.edit()?.putBoolean(KEY_ENABLED, value)?.apply()
        } catch (_: Exception) {}
    }

    private fun getBandLevelFromPrefs(band: Int): Int {
        val csv = try {
            prefs()?.getString(KEY_LEVELS, null)
        } catch (e: Exception) {
            null
        } ?: return 0
        val parts = csv.split(",")
        return parts.getOrNull(band)?.toIntOrNull() ?: 0
    }

    private fun persistBandLevel(band: Int, level: Int) {
        val p = prefs() ?: return
        val existing = try {
            p.getString(KEY_LEVELS, null)
        } catch (_: Exception) {
            null
        }
        val size = if (cachedBandCount > 0) cachedBandCount else 5
        val levels = existing?.split(",")?.map { it.toIntOrNull() ?: 0 }?.toIntArray()?.toMutableList()
            ?: MutableList(size) { 0 }
        while (levels.size <= band) levels.add(0)
        levels[band] = level
        setPersistedLevels(levels.toIntArray())
    }

    private fun setPersistedLevels(levels: IntArray) {
        try {
            prefs()?.edit()?.putString(KEY_LEVELS, levels.joinToString(","))?.apply()
        } catch (_: Exception) {}
    }

    private fun setPersistedPreset(index: Int) {
        try {
            prefs()?.edit()?.putInt(KEY_PRESET, index)?.apply()
        } catch (_: Exception) {}
    }

    private fun markPreset(index: Int) {
        selectedPreset = index
        setPersistedPreset(index)
    }

    /** Bağlandığında kayıtlı ayarları instance'a uygular. */
    private fun restoreSettings(eq: Equalizer) {
        try {
            val p = prefs() ?: return
            val savedPreset = p.getInt(KEY_PRESET, -1)
            selectedPreset = savedPreset
            if (savedPreset >= 0) {
                try {
                    eq.usePreset(savedPreset.toShort())
                } catch (_: Exception) {
                    // cihazda o preset yok -> kayıtlı seviyelere dön
                    applySavedLevels(eq, p)
                }
            } else {
                applySavedLevels(eq, p)
            }
            eq.enabled = p.getBoolean(KEY_ENABLED, false)
        } catch (e: Exception) {
            Log.e("EqualizerManager", "Ayarlaryı geri yükleyemedi", e)
        }
    }

    private fun applySavedLevels(eq: Equalizer, p: android.content.SharedPreferences) {
        val csv = p.getString(KEY_LEVELS, null) ?: return
        val levels = csv.split(",").mapNotNull { it.toIntOrNull() }
        levels.forEachIndexed { band, level ->
            try {
                eq.setBandLevel(band.toShort(), level.toShort())
            } catch (_: Exception) {}
        }
    }
}
