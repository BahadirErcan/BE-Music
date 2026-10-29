package com.be.music.player

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.be.music.MainActivity
import com.be.music.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private var notificationProvider: NowPlayingNotificationProvider? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()

        // Bildirimin arka planı albüm kapağı olan özel "şu an çalınıyor" görünümü.
        notificationProvider = NowPlayingNotificationProvider(this).also {
            setMediaNotificationProvider(it)
        }

        val db = AppDatabase.getDatabase(this)
        val settings = db.filterSettingsDao().getSettings()

        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

        serviceScope.launch {
            settings.collect { currentSettings ->
                mainHandler.post {
                    player.setSkipSilenceEnabled(currentSettings?.skipSilenceEnabled == true)
                }
            }
        }

        val sessionActivityIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                action = Intent.ACTION_MAIN
                addCategory(Intent.CATEGORY_LAUNCHER)
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_NEW_TASK
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivityIntent)
            .build()

        // Ekolayzer: yerleşik equalizer'ı oynatıcı audio session'ına bağla.
        // Session id player'ın ses motoru başlatılana kadar UNSET kalabilir -> kısa aralıklarla tekrar dene.
        // Not: ExoPlayer'a SADECE main thread'den erişilebilir (media3 thread kontrolü).
        EqualizerManager.appContext = applicationContext
        serviceScope.launch(Dispatchers.Main) {
            for (attempt in 1..20) {
                val id = player.audioSessionId
                if (id != C.AUDIO_SESSION_ID_UNSET && id != 0) {
                    EqualizerManager.attach(id)
                    break
                }
                delay(500)
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    // Madde 1: Uygulama tamamen kapatıldığında sesi durdur (keep-alive hariç)
    override fun onTaskRemoved(rootIntent: Intent?) {
        val prefs = getSharedPreferences("premium_prefs", MODE_PRIVATE)
        val keepAlive = prefs.getBoolean("keep_alive_enabled", false)
        val player = mediaSession?.player

        if (keepAlive) {
            // Keep-alive açıksa sadece foreground'u kaldır, player'ı durdurma
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_DETACH)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        } else {
            player?.pause()
            player?.stop()
            player?.clearMediaItems()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
            stopSelf()
        }
    }

    override fun onDestroy() {
        // Madde 4: Servis yok edilirken son oynatma durumunu kaydet (mini player restore)
        try {
            mediaSession?.player?.let { player ->
                val id = player.currentMediaItem?.mediaId?.toLongOrNull()
                if (id != null && id > 0L) {
                    getSharedPreferences("playback_prefs", MODE_PRIVATE).edit()
                        .putLong("last_song_id", id)
                        .putLong("last_position", player.currentPosition.coerceAtLeast(0L))
                        .apply()
                }
            }
        } catch (_: Exception) {}
        notificationProvider?.release()
        notificationProvider = null
        EqualizerManager.detach()
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
