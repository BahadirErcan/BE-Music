package com.be.music.player

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.Log
import android.util.LruCache
import android.util.Size
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Collections

/**
 * "Şu an çalınıyor" bildiriminde kullanılmak üzere hazırlanmış albüm kapağı görselleri.
 *
 * @param thumb SystemUI'nin küçülttüğü kompakt görünümde kullanılan keskin küçük kapak.
 * @param background Bildirimin tüm arka planını kaplayan bulanıklaştırılmış kapak.
 */
internal class NotificationArtwork(
    val thumb: Bitmap,
    val background: Bitmap
)

/**
 * Albüm kapağını bildirim için gerekli iki farklı boyutta hazırlar.
 *
 * Kapak görseli [MediaMetadataRetriever] ile ses dosyasının içinden okunur. Bildirim tek bir
 * karede iki ayrı bitmap taşıdığı için ikisi de küçültülür, aksi halde bildirim Binder'ın
 * 1 MB'lık sınırını aşabilir.
 */
internal class NotificationArtworkLoader(context: Context) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val cache = LruCache<String, NotificationArtwork>(CACHE_SIZE)
    private val pendingKeys: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())

    /** Bellekte hazırsa döndürür, henüz yüklenmediyse `null` döner. */
    fun cached(key: String): NotificationArtwork? = cache.get(key)

    /** Bekleyen yüklemeleri iptal eder. */
    fun cancel() {
        scope.coroutineContext.cancel()
    }

    /**
     * Kapak görselini arka planda hazırlar. [onReady] her zaman uygulama iş parçacığında çağrılır.
     * Aynı [key] için bir yükleme zaten sürüyorsa yeni bir istek başlatılmaz.
     */
    fun load(key: String, uri: Uri, onReady: (NotificationArtwork) -> Unit) {
        cache.get(key)?.let {
            onReady(it)
            return
        }
        if (!pendingKeys.add(key)) return
        scope.launch {
            val artwork = extractAndPrepare(uri)
            pendingKeys.remove(key)
            if (artwork != null) {
                cache.put(key, artwork)
                withContext(Dispatchers.Main) { onReady(artwork) }
            }
        }
    }

    private fun extractAndPrepare(uri: Uri): NotificationArtwork? {
        val source = extractArtwork(uri) ?: return null
        val thumb = scaleCenterCrop(source, THUMB_SIZE, THUMB_SIZE, blurred = false)
        val background = scaleCenterCrop(source, BACKGROUND_WIDTH, BACKGROUND_HEIGHT, blurred = true)
        if (thumb !== source) source.recycle()
        return NotificationArtwork(thumb, background)
    }

    private fun extractArtwork(uri: Uri): Bitmap? {
        extractEmbeddedPicture(uri)?.let { return it }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        return runCatching {
            appContext.contentResolver.loadThumbnail(uri, Size(THUMB_SIZE, THUMB_SIZE), null)
        }.onFailure { Log.w(TAG, "Thumbnail could not be loaded for $uri", it) }.getOrNull()
    }

    private fun extractEmbeddedPicture(uri: Uri): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(appContext, uri)
            val bytes = retriever.embeddedPicture
            bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
        } catch (t: Throwable) {
            Log.w(TAG, "Embedded picture could not be read for $uri", t)
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    /**
     * Kaynağı hedef en boy oranına göre merkezden kırparak [targetWidth] x [targetHeight]
     * boyutuna ölçekler.
     *
     * `blurred` true ise görsel önce çok küçük bir boyuta indirilip geri büyütülerek yumuşak bir
     * bulanıklık oluşturulur; böylece düşük çözünürlüklü kapak arka plan olarak
     * kullanıldığında bulanık görünür. `RGB_565` de bu sayede yarı yarıya az yer kaplar.
     */
    private fun scaleCenterCrop(
        source: Bitmap,
        targetWidth: Int,
        targetHeight: Int,
        blurred: Boolean
    ): Bitmap {
        val config = if (blurred) Bitmap.Config.RGB_565 else Bitmap.Config.ARGB_8888
        val output = Bitmap.createBitmap(targetWidth, targetHeight, config)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val canvas = Canvas(output)

        if (blurred) {
            val sampleHeight = (BLUR_SAMPLE_WIDTH * targetHeight / targetWidth).coerceAtLeast(1)
            val sample = Bitmap.createScaledBitmap(source, BLUR_SAMPLE_WIDTH, sampleHeight, true)
            canvas.drawColor(Color.BLACK)
            canvas.drawBitmap(
                sample,
                null,
                Rect(0, 0, targetWidth, targetHeight),
                Paint(paint).apply { alpha = BACKGROUND_ALPHA }
            )
            if (sample !== source) sample.recycle()
            return output
        }

        val sourceRatio = source.width.toFloat() / source.height
        val targetRatio = targetWidth.toFloat() / targetHeight
        var cropLeft = 0
        var cropTop = 0
        var cropWidth = source.width
        var cropHeight = source.height
        if (sourceRatio > targetRatio) {
            cropWidth = (source.height * targetRatio).toInt()
            cropLeft = (source.width - cropWidth) / 2
        } else {
            cropHeight = (source.width / targetRatio).toInt()
            cropTop = (source.height - cropHeight) / 2
        }
        canvas.drawBitmap(
            source,
            Rect(cropLeft, cropTop, cropLeft + cropWidth, cropTop + cropHeight),
            Rect(0, 0, targetWidth, targetHeight),
            paint
        )
        return output
    }

    private companion object {
        const val TAG = "NotificationArtwork"
        const val CACHE_SIZE = 8
        const val THUMB_SIZE = 128
        const val BACKGROUND_WIDTH = 400
        const val BACKGROUND_HEIGHT = 160
        const val BLUR_SAMPLE_WIDTH = 24
        const val BACKGROUND_ALPHA = 190
    }
}
