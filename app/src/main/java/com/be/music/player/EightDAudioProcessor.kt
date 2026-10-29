package com.be.music.player

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import kotlin.coroutines.resume
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Madde 8: 8D ses işleyici.
 *
 * /home/bahadir/Documents/SW/PythonProjects/8D-Music/main.py projesinin
 * Android'e uyarlanmış halidir:
 *
 *  1. ffmpeg, kaynak dosyayı f32le stereo PCM'e decode eder (SAMPLE_RATE = 44100)
 *     ve pipe:1 üzerinden aktarır
 *  2. sin(2π·SPEED·t) ile sağ-sol eşit güç (equal-power) pan kazançları uygulanır
 *       left_gain = sqrt((1 - pan) / 2)
 *       right_gain = sqrt((1 + pan) / 2)
 *  3. işlenmiş PCM pipe:0 üzerinden ffmpeg'e verilir ve AAC (m4a, 256k, +faststart)
 *     olarak kaydedilir
 *
 * Uygulama paketinde zaten yer alan ffmpeg ikili dosyası (libffmpeg.so)
 * ProcessBuilder ile çalıştırılır; main.py'ın iş mantığı birebir korunur.
 */
object EightDAudioProcessor {

    private const val SAMPLE_RATE = 44100L
    private const val SPEED = 0.15 // sağ-sol hareket hızı (Hz)
    private const val CHANNELS = 2 // stereo

    /** ffmpeg çalıştırır. stdin'e [stdinProducer], stdout'u [onOutput] ile kullanır. */
    private fun runFfmpeg(
        context: Context,
        args: List<String>,
        stdinProducer: ((OutputStream) -> Unit)? = null,
        onOutput: ((InputStream) -> Unit)? = null
    ) {
        val ffmpegBin = File(context.applicationInfo.nativeLibraryDir, "libffmpeg.so")
        if (!ffmpegBin.exists()) {
            throw IOException("ffmpeg binary not found")
        }
        // ZipUtils açtığında usr/lib altında libav* / libsw* kütüphaneleri yer alır
        val ffmpegUsrLib = File(
            File(context.noBackupFilesDir, "youtubedl-android"),
            "packages/ffmpeg/usr/lib"
        )
        // Python paketi usr/lib altında libc++_shared.so taşır; ffmpeg'in bazı
        // filtre kütüphaneleri (ör. librubberband) buna bağlıdır. Ayrıca uygulamanın
        // nativeLibraryDir'i de eklenir (addLibrary ile paketlenmiş libc++ varsa).
        val pythonUsrLib = File(
            File(context.noBackupFilesDir, "youtubedl-android"),
            "packages/python/usr/lib"
        )
        val appNativeLibDir = File(context.applicationInfo.nativeLibraryDir)

        val ldLibraryPath = listOf(pythonUsrLib.absolutePath, ffmpegUsrLib.absolutePath, appNativeLibDir.absolutePath)
            .joinToString(":")

        val command = buildList {
            add(ffmpegBin.absolutePath)
            addAll(args)
        }

        val pb = ProcessBuilder(command)
        pb.environment().apply {
            put("LD_LIBRARY_PATH", ldLibraryPath)
            put("TMPDIR", context.cacheDir.absolutePath)
            put("HOME", context.cacheDir.absolutePath)
        }

        val process = try {
            pb.start()
        } catch (e: IOException) {
            throw IOException("ffmpeg başlatılamadı", e)
        }

        val errors = StringBuilder()
        val stderrThread = Thread {
            try {
                process.errorStream.bufferedReader(Charsets.UTF_8).useLines { lines ->
                    lines.forEach { errors.appendLine(it) }
                }
            } catch (_: Exception) {
            }
        }
        stderrThread.isDaemon = true
        stderrThread.start()

        var stdinWriterThread: Thread? = null
        if (stdinProducer != null) {
            stdinWriterThread = Thread {
                try {
                    process.outputStream.use { stdinProducer(it) }
                } catch (_: Exception) {
                }
            }
            stdinWriterThread.isDaemon = true
            stdinWriterThread.start()
        }

        try {
            onOutput?.invoke(process.inputStream)
        } finally {
            try {
                process.inputStream.close()
            } catch (_: Exception) {
            }
        }

        stdinWriterThread?.join()
        // Watchdog: ffmpeg herhangi bir nedenle kilitlenirse sonsuza kadar beklemek yerine
        // süre dolunca süreci yok et ve anlaşılır hata fırlat (Maksimum 30 dk).
        var exitCode = -1
        var waitedNanos = 0L
        val maxWaitNanos = MAX_FFMPEG_WAIT_MINUTES * 60L * 1_000_000_000L
        while (true) {
            val exited = process.waitFor(1, java.util.concurrent.TimeUnit.SECONDS)
            if (exited) {
                exitCode = process.exitValue()
                break
            }
            waitedNanos += 1_000_000_000L
            if (waitedNanos >= maxWaitNanos) {
                process.destroyForcibly()
                throw IOException("ffmpeg işlemi $MAX_FFMPEG_WAIT_MINUTES dakikada tamamlanamadı (zaman aşımı)")
            }
        }
        stderrThread.join()

        if (exitCode != 0) {
            throw IOException("ffmpeg hata verdi (exit $exitCode): ${errors.toString().trim()}")
        }
    }

    /**
     * Verilen ses dosyasının 8D halini oluşturur.
     *
     * @param inputPath kaynak ses dosyası (mp3 / m4a / flac ...)
     * @param onProgress 0-100 arası ilerleme
     * @return oluşan "(8D).m4a" dosyası
     */
    suspend fun create8DVersion(
        context: Context,
        inputPath: String,
        songTitle: String,
        songArtist: String,
        onProgress: (Int) -> Unit = {}
    ): File = withContext(Dispatchers.IO) {

        val inputFile = File(inputPath)
        if (!inputFile.exists()) throw IOException("kaynak dosya yok: $inputPath")

        val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
        if (!musicDir.exists()) musicDir.mkdirs()
        var outputFile = File(musicDir, "${inputFile.nameWithoutExtension} (8D).m4a")
        var suffix = 2
        while (outputFile.exists()) {
            outputFile = File(musicDir, "${inputFile.nameWithoutExtension} (8D_x$suffix).m4a")
            suffix++
        }

        onProgress(2)

        // Decode → process → encode akışı (main.py'ın boru hattının birebir karşılığı).
        // Decode stdout'u doğrudan encode stdin'ine bağlanamaz (aynı proses içinde
        // iki ProcessBuilder çalışır), bu yüzden decode, bellek dostu bir ara dosyaya
        // akıtılır ve işlenerek encode stdin'ine beslenir.
        val decodedPcm = File.createTempFile("be8d_in_", ".pcm", context.cacheDir)
        val processedPcm = File.createTempFile("be8d_out_", ".pcm", context.cacheDir)
        // ffmpeg'in "Overwrite? [y/N]" prompt'unda kilitlenmemesi için dosyaları sil
        // (-y bayrağıyla birlikte çifte güvenlik)
        decodedPcm.delete()
        processedPcm.delete()
        try {
            // ---- 1. Decode ----
            runFfmpeg(
                context,
                listOf(
                    "-y",             // mevcut dosyanın üzerine yaz (prompt beklemesin)
                    "-nostdin",       // stdin'i asla okumaya çalışmasın (kilitlenme önlemi)
                    "-v", "error",
                    "-i", inputFile.absolutePath,
                    "-f", "f32le",
                    "-acodec", "pcm_f32le",
                    "-ac", CHANNELS.toString(),
                    "-ar", SAMPLE_RATE.toString(),
                    decodedPcm.absolutePath
                )
            )
            onProgress(10)

            // ---- 2. Panning (main.py mantığının akışlı hali) ----
            applyPan(decodedPcm, processedPcm, onProgress)
            onProgress(95)

            // ---- 3. Encode ----
            runFfmpeg(
                context,
                listOf(
                    "-y", "-v", "error",
                    "-f", "f32le",
                    "-ar", SAMPLE_RATE.toString(),
                    "-ac", CHANNELS.toString(),
                    "-i", "pipe:0",
                    "-c:a", "aac",
                    "-b:a", "256k",
                    "-movflags", "+faststart",
                    outputFile.absolutePath
                ),
                stdinProducer = { stdin ->
                    processedPcm.inputStream().use { pcmIn ->
                        pcmIn.copyTo(stdin, DEFAULT_PIPED_BUFFER_SIZE)
                    }
                }
            )
        } finally {
            decodedPcm.delete()
            processedPcm.delete()
        }

        if (!outputFile.exists() || outputFile.length() == 0L) {
            throw IOException("8D dosyası oluşamadı")
        }

        // ---- 4. Metadata ----
        // Orijinalin başlık/sanatçı/cover'ı korunur; albüm adı "8D" yapılır.
        writeMetadata(inputFile, outputFile, songTitle, songArtist)

        // ---- 5. MediaStore kaydı ----
        // Dosya File() ile yazıldığı için MediaStore'a kaydedilmeden scanMusic onu göremez.
        // Taramanın BİTMESİNİ bekleyip MediaStore uri'sini alıyoruz; aksi halde ViewModel'daki
        // scanMusic(force=true) taraması sırasında satır henüz hazır olmazdı.
        val scannedUri = awaitMediaScan(context, outputFile.absolutePath)
        if (scannedUri == null) {
            android.util.Log.w("EightDProcessor", "MediaStore taraması uri döndürmedi: ${outputFile.name}")
        }

        onProgress(100)
        outputFile
    }

    /** Dosyayı MediaScanner'a bildirir ve tarama bitene kadar bekler. */
    private suspend fun awaitMediaScan(context: Context, filePath: String): Uri? =
        suspendCancellableCoroutine { cont ->
            try {
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(filePath),
                    arrayOf("audio/mp4")
                ) { _, uri ->
                    cont.resume(uri)
                }
            } catch (e: Exception) {
                cont.resume(null)
            }
        }

    /**
     * 8D çıktı dosyasına metadata yazar:
     * - Başlık ve sanatçı orijinalden (UI'da görünen değerlerden) korunur
     * - Albüm adı sabit "8D" olur (önceki albüm bilgisi çekilmez)
     * - Cover Art orijinal dosyadaki gömülü kapaktan kopyalanır (varsa)
     */
    private fun writeMetadata(
        sourceFile: File,
        outputFile: File,
        songTitle: String,
        songArtist: String
    ) {
        try {
            val outAudio = org.jaudiotagger.audio.AudioFileIO.read(outputFile)
            val outTag = outAudio.tag ?: outAudio.createDefaultTag() ?: return

            if (songTitle.isNotBlank()) outTag.setField(org.jaudiotagger.tag.FieldKey.TITLE, songTitle)
            if (songArtist.isNotBlank()) outTag.setField(org.jaudiotagger.tag.FieldKey.ARTIST, songArtist)
            outTag.setField(org.jaudiotagger.tag.FieldKey.ALBUM, "8D")
            outTag.setField(org.jaudiotagger.tag.FieldKey.ALBUM_ARTIST, songArtist)

            // Orijinaldeki gömülü cover'ı kopyala (varsa)
            val artBytes = try {
                val srcAudio = org.jaudiotagger.audio.AudioFileIO.read(sourceFile)
                srcAudio.tag?.firstArtwork?.binaryData
            } catch (_: Exception) {
                null
            } ?: run {
                // jaudiotagger okuyamazsa MediaMetadataRetriever ile dene
                try {
                    val retriever = android.media.MediaMetadataRetriever()
                    retriever.setDataSource(sourceFile.absolutePath)
                    val bytes = retriever.embeddedPicture
                    try { retriever.release() } catch (_: Exception) {}
                    bytes
                } catch (_: Exception) {
                    null
                }
            }

            if (artBytes != null && artBytes.isNotEmpty()) {
                try {
                    val artwork = org.jaudiotagger.tag.images.StandardArtwork()
                    artwork.binaryData = artBytes
                    artwork.mimeType = guessImageMime(artBytes)
                    outTag.deleteArtworkField()
                    outTag.setField(artwork)
                } catch (e: Exception) {
                    android.util.Log.w("EightDProcessor", "Cover art gömülemedi", e)
                }
            }

            outAudio.commit()
            android.util.Log.d("EightDProcessor", "Metadata yazıldı: ${outputFile.name} (albüm=8D)")
        } catch (e: Exception) {
            // Metadata yazılamazsa dosya yine de geçerlidir; sadece günlüğe yaz
            android.util.Log.e("EightDProcessor", "Metadata yazma hatası", e)
        }
    }

    private fun guessImageMime(bytes: ByteArray): String {
        return when {
            bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() -> "image/jpeg"
            bytes.size >= 8 && bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() &&
                bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte() -> "image/png"
            bytes.size >= 6 && bytes[0] == 0x47.toByte() && bytes[1] == 0x49.toByte() && bytes[2] == 0x46.toByte() -> "image/gif"
            else -> "image/jpeg"
        }
    }

    /**
     * Gelen f32le stereo PCM'i parça-parça okuyup işler ve hedef dosyaya yazar.
     * main.py'daki eş it hesaplamasının sürekli akış karşılığıdır.
     */
    private fun applyPan(
        src: File,
        dst: File,
        onProgress: (Int) -> Unit
    ) {
        val bytesPerSample = 4 // float32
        val bytesPerFrame = bytesPerSample * CHANNELS

        val totalFrames = src.length() / bytesPerFrame
        if (totalFrames == 0L) throw IOException("PCM verisi boş")

        var framesProcessed = 0L
        var streakCounter = 0

        val buffer = ByteArray(PREFERRED_CHUNK_FRAMES * bytesPerFrame)
        val out = dst.outputStream()
        src.inputStream().use { input ->
            while (true) {
                // tam çerçeve hizalı okuma
                var filled = 0
                while (filled < buffer.size) {
                    val n = input.read(buffer, filled, buffer.size - filled)
                    if (n <= 0) break
                    filled += n
                }
                if (filled < bytesPerFrame) break
                val frames = filled / bytesPerFrame

                // RAM'de dizin → örnekleme çözümü
                val byteBuf = java.nio.ByteBuffer.wrap(buffer, 0, frames * bytesPerFrame)
                    .order(java.nio.ByteOrder.LITTLE_ENDIAN)
                    .asFloatBuffer()
                val floatBuf = FloatArray(frames * CHANNELS)
                byteBuf.get(floatBuf)

                val sampleRateAsDouble = SAMPLE_RATE.toDouble()
                var frameFloatIdx = 0
                for (f in 0 until frames) {
                    val t = (framesProcessed + f) / sampleRateAsDouble
                    val pan = sin(2 * PI * SPEED * t)
                    val leftGain = sqrt((1.0 - pan) / 2.0)
                    val rightGain = sqrt((1.0 + pan) / 2.0)

                    floatBuf[frameFloatIdx] = (floatBuf[frameFloatIdx] * leftGain).toFloat()
                    floatBuf[frameFloatIdx + 1] = (floatBuf[frameFloatIdx + 1] * rightGain).toFloat()
                    frameFloatIdx += CHANNELS
                }

                // float dizisini geri f32le byte'larına çevirip yaz
                val outBytes = ByteArray(frames * bytesPerFrame)
                val outBuf = java.nio.ByteBuffer.wrap(outBytes)
                    .order(java.nio.ByteOrder.LITTLE_ENDIAN)
                    .asFloatBuffer()
                outBuf.put(floatBuf, 0, frames * CHANNELS)
                out.write(outBytes)

                framesProcessed += frames
                streakCounter++
                if (streakCounter % PROGRESS_REPORT_EVERY == 0) {
                    onProgress(10 + ((framesProcessed * 85.0) / totalFrames).toInt().coerceIn(0, 85))
                }
            }
        }
        out.flush()
        out.close()
    }

    private const val DEFAULT_PIPED_BUFFER_SIZE = 64 * 1024
    // ~1 saniyelik işleme başına: 44100 çerçeve
    private const val PREFERRED_CHUNK_FRAMES = 44100
    private const val PROGRESS_REPORT_EVERY = 10 // ~10 saniyede bir rapor
    private const val MAX_FFMPEG_WAIT_MINUTES = 30L // tek ffmpeg çağrısı için üst sınır
}
