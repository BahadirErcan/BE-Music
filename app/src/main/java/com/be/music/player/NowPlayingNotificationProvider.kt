package com.be.music.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaStyleNotificationHelper
import androidx.media3.session.SessionCommand
import com.google.common.collect.ImmutableList
import com.be.music.R

/**
 * "Şu an çalınıyor" bildirimini üreten özel [MediaNotification.Provider].
 *
 * Varsayılan sağlayıcıdan farklı olarak bildirim özel bir görünüm kullanır: albüm kapağı
 * bulanıklaştırılıp bildirimin arka planı yapılır, şarkı adı ve sanatçı bu zeminin üzerine
 * bindirilir. Medya tuşları, oturum (session) ve kompakt görünüm yine sistem tarafından çizilir.
 */
@OptIn(UnstableApi::class)
class NowPlayingNotificationProvider(context: Context) : MediaNotification.Provider {

    private val appContext = context.applicationContext
    private val notificationManager = appContext.getSystemService(NotificationManager::class.java)
    private val artworkLoader = NotificationArtworkLoader(appContext)

    /** Kapak görseli hazır olduğunda, hâlâ aynı şarkı gösteriliyorsa bildirimi tazelemek için. */
    private var pendingArtworkKey: String? = null

    override fun createNotification(
        mediaSession: MediaSession,
        mediaButtonPreferences: ImmutableList<CommandButton>,
        actionFactory: MediaNotification.ActionFactory,
        callback: MediaNotification.Provider.Callback
    ): MediaNotification {
        ensureNotificationChannel()

        val player = mediaSession.player
        val metadata = player.mediaMetadata
        val builder = NotificationCompat.Builder(appContext, CHANNEL_ID)

        val compactActions = addActions(
            mediaSession, player, mediaButtonPreferences, builder, actionFactory
        )
        val mediaStyle = MediaStyleNotificationHelper.DecoratedMediaCustomViewStyle(mediaSession)
            .setShowActionsInCompactView(*compactActions)

        val views = NowPlayingViews(appContext.packageName, metadata.title, metadata.artist)

        builder
            .setStyle(mediaStyle)
            .setCustomContentView(views.collapsed)
            .setCustomBigContentView(views.expanded)
            .setContentTitle(metadata.title)
            .setContentText(metadata.artist)
            .setContentIntent(mediaSession.sessionActivity)
            .setDeleteIntent(
                actionFactory.createMediaActionPendingIntent(
                    mediaSession,
                    Player.COMMAND_STOP.toLong()
                )
            )
            .setSmallIcon(R.drawable.ic_notification)
            .setGroup(DefaultMediaNotificationProvider.GROUP_KEY)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(false)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        }

        loadArtwork(builder, views, callback, player.currentMediaItem)

        return MediaNotification(NOTIFICATION_ID, builder.build())
    }

    override fun handleCustomCommand(session: MediaSession, action: String, extras: Bundle): Boolean =
        false

    /**
     * Bildirim arka planı için albüm kapağını hazırlar. Kapak bellekteyse anında uygulanır;
     * değilse arka planda okunup hazır olduğunda bildirim bir kez daha gönderilir.
     */
    private fun loadArtwork(
        builder: NotificationCompat.Builder,
        views: NowPlayingViews,
        callback: MediaNotification.Provider.Callback,
        mediaItem: MediaItem?
    ) {
        val uri = mediaItem?.localConfiguration?.uri
        if (uri == null) {
            pendingArtworkKey = null
            return
        }
        val key = artworkKey(mediaItem, uri)
        pendingArtworkKey = key

        artworkLoader.cached(key)?.let { artwork ->
            views.setArtwork(artwork)
            builder.setLargeIcon(artwork.thumb)
            return
        }
        artworkLoader.load(key, uri) { artwork ->
            if (pendingArtworkKey != key) return@load
            views.setArtwork(artwork)
            builder.setLargeIcon(artwork.thumb)
            callback.onNotificationChanged(MediaNotification(NOTIFICATION_ID, builder.build()))
        }
    }

    private fun artworkKey(mediaItem: MediaItem, uri: Uri): String = "${mediaItem.mediaId}|$uri"

    /**
     * Önceki / oynat-duraklat / sonraki eylemlerini bildirime ekler ve kompakt görünümde
     * gösterilecek indekslerini döndürür.
     */
    private fun addActions(
        mediaSession: MediaSession,
        player: Player,
        mediaButtonPreferences: ImmutableList<CommandButton>,
        builder: NotificationCompat.Builder,
        actionFactory: MediaNotification.ActionFactory
    ): IntArray {
        val compactActions = IntArray(3)
        var actionCount = 0
        var compactActionCount = 0

        fun addCompact() {
            compactActions[compactActionCount++] = actionCount - 1
        }

        val previousCommand = when {
            player.isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM) ->
                Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM
            player.isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS) ->
                Player.COMMAND_SEEK_TO_PREVIOUS
            else -> Player.COMMAND_INVALID
        }
        if (previousCommand != Player.COMMAND_INVALID) {
            builder.addAction(
                actionFactory.createMediaAction(
                    mediaSession,
                    IconCompat.createWithResource(appContext, R.drawable.ic_notification_previous),
                    appContext.getString(R.string.now_playing_previous),
                    previousCommand
                )
            )
            actionCount++
            addCompact()
        }

        if (player.isCommandAvailable(Player.COMMAND_PLAY_PAUSE)) {
            val showPlayButton = Util.shouldShowPlayButton(
                player,
                mediaSession.getShowPlayButtonIfPlaybackIsSuppressed()
            )
            builder.addAction(
                actionFactory.createMediaAction(
                    mediaSession,
                    IconCompat.createWithResource(
                        appContext,
                        if (showPlayButton) R.drawable.ic_notification_play
                        else R.drawable.ic_notification_pause
                    ),
                    appContext.getString(
                        if (showPlayButton) R.string.now_playing_resume
                        else R.string.now_playing_pause
                    ),
                    Player.COMMAND_PLAY_PAUSE
                )
            )
            actionCount++
            addCompact()
        }

        val nextCommand = when {
            player.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM) ->
                Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM
            player.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT) -> Player.COMMAND_SEEK_TO_NEXT
            else -> Player.COMMAND_INVALID
        }
        if (nextCommand != Player.COMMAND_INVALID) {
            builder.addAction(
                actionFactory.createMediaAction(
                    mediaSession,
                    IconCompat.createWithResource(appContext, R.drawable.ic_notification_next),
                    appContext.getString(R.string.now_playing_next),
                    nextCommand
                )
            )
            actionCount++
            addCompact()
        }

        mediaButtonPreferences.forEach { button ->
            if (button.sessionCommand?.commandCode == SessionCommand.COMMAND_CODE_CUSTOM &&
                button.isEnabled
            ) {
                builder.addAction(
                    actionFactory.createCustomActionFromCustomCommandButton(mediaSession, button)
                )
                actionCount++
            }
        }

        return compactActions.copyOf(compactActionCount)
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = notificationManager ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            appContext.getString(R.string.notification_channel_playback_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply { setShowBadge(false) }
        manager.createNotificationChannel(channel)
    }

    /**
     * Bildirimin özel görünümleri. Kompakt görünüm tek satır, geniş görünüm daha yüksektir;
     * ikisinde de arka plan albüm kapağıdır.
     */
    private class NowPlayingViews(
        packageName: String,
        title: CharSequence?,
        artist: CharSequence?
    ) {

        val collapsed = RemoteViews(packageName, R.layout.notification_now_playing_collapsed)
        val expanded = RemoteViews(packageName, R.layout.notification_now_playing)

        init {
            collapsed.setTextViewText(R.id.now_playing_title, title)
            expanded.setTextViewText(R.id.now_playing_title, title)
            if (artist.isNullOrBlank()) {
                collapsed.setViewVisibility(R.id.now_playing_artist, View.GONE)
                expanded.setViewVisibility(R.id.now_playing_artist, View.GONE)
            } else {
                collapsed.setTextViewText(R.id.now_playing_artist, artist)
                expanded.setTextViewText(R.id.now_playing_artist, artist)
            }
        }

        fun setArtwork(artwork: NotificationArtwork) {
            collapsed.setImageViewBitmap(R.id.now_playing_artwork, artwork.background)
            expanded.setImageViewBitmap(R.id.now_playing_artwork, artwork.background)
        }
    }

    private companion object {
        const val NOTIFICATION_ID = DefaultMediaNotificationProvider.DEFAULT_NOTIFICATION_ID
        const val CHANNEL_ID = "playback_channel"
    }

    /** Servis yok edilirken bekleyen kapak yükleme işini iptal eder. */
    fun release() {
        artworkLoader.cancel()
    }
}
