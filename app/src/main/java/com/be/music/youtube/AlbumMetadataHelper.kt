package com.be.music.youtube

import android.util.Log
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import java.io.File

object AlbumMetadataHelper {

    /**
     * Indirilen dosyanin metadata'sindaki album bilgisini kontrol eder,
     * bossa veya "Music" ise gunceller.
     *
     * Oncelik:
     * 1. Dosyanin kendi metadata'sinda album varsa ve bossa degilse -> kullan
     * 2. playlistName parametresi varsa -> onu kullan
     * 3. Hicbiri yoksa -> "<Unknown>" yaz
     */
    fun fixAlbumMetadata(filePath: String, playlistName: String?) {
        try {
            val file = File(filePath)
            if (!file.exists()) return

            val audioFile = AudioFileIO.read(file)
            val tag = audioFile.tag ?: audioFile.createDefaultTag()

            val currentAlbum = try {
                tag.getFirst(FieldKey.ALBUM)
            } catch (_: Exception) { "" }

            val albumToSet = when {
                !currentAlbum.isNullOrBlank() && currentAlbum != "Music" && currentAlbum != "Unknown" -> {
                    Log.d("AlbumMetadataHelper", "Mevcut album kullaniliyor: $currentAlbum")
                    return // Zaten gecerli bir album var, dokunma
                }
                !playlistName.isNullOrBlank() -> {
                    Log.d("AlbumMetadataHelper", "Playlist adi album olarak kullaniliyor: $playlistName")
                    playlistName
                }
                else -> {
                    Log.d("AlbumMetadataHelper", "Album bilgisi yok, '<Unknown>' ayarlaniyor")
                    "<Unknown>"
                }
            }

            tag.setField(FieldKey.ALBUM, albumToSet)
            audioFile.commit()
            Log.d("AlbumMetadataHelper", "Album guncellendi: $albumToSet -> $filePath")
        } catch (e: Exception) {
            Log.e("AlbumMetadataHelper", "Album metadata duzeltme hatasi: $filePath", e)
        }
    }
}
