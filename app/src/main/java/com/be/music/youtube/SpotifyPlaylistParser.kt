package com.be.music.youtube

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

data class SpotifyTrack(
    val name: String,
    val artist: String
)

object SpotifyPlaylistParser {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    //Spotify playlist veya track URL'inden şarkı listesini çeker.
    suspend fun parseUrl(url: String): List<SpotifyTrack> = withContext(Dispatchers.IO) {
        val trimmedUrl = url.trim()
        val normalizedUrl = resolveSpotifyUrl(trimmedUrl)
        return@withContext when {
            normalizedUrl.contains("/playlist/") -> parsePlaylist(normalizedUrl)
            normalizedUrl.contains("/track/") -> parseTrack(normalizedUrl)
            else -> emptyList()
        }
    }

    private fun resolveSpotifyUrl(url: String): String {
        if (!url.contains("spotify.link/")) return url

        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36")
                .build()

            val response = client.newCall(request).execute()
            val resolvedUrl = response.request.url.toString()
            response.close()
            resolvedUrl
        } catch (e: Exception) {
            android.util.Log.w("SpotifyParser", "Spotify short link resolution failed, using original URL", e)
            url
        }
    }

    /**
     * Spotify playlist URL'inden şarkı listesini çeker.
     * Spotify web sayfasındaki meta tag'ler ve embed sayfasından parse eder.
     */
    suspend fun parsePlaylist(url: String): List<SpotifyTrack> = withContext(Dispatchers.IO) {
        val playlistId = extractPlaylistId(url) ?: return@withContext emptyList()
        val tracks = mutableListOf<SpotifyTrack>()

        // Yöntem 1: Spotify embed sayfasından parse et
        try {
            val embedUrl = "https://open.spotify.com/embed/playlist/$playlistId"
            val request = Request.Builder()
                .url(embedUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36")
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""
            response.close()

            // Embed sayfasındaki JSON verisini parse et
            val embedTracks = parseEmbedHtml(body)
            if (embedTracks.isNotEmpty()) {
                return@withContext embedTracks
            }
        } catch (e: Exception) {
            android.util.Log.e("SpotifyParser", "Embed parse error", e)
        }

        // Yöntem 2: Ana sayfadan meta tag'leri parse et
        try {
            val pageUrl = "https://open.spotify.com/playlist/$playlistId"
            val request = Request.Builder()
                .url(pageUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36")
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""
            response.close()

            val pageTracks = parseMainPageHtml(body)
            if (pageTracks.isNotEmpty()) {
                return@withContext pageTracks
            }
        } catch (e: Exception) {
            android.util.Log.e("SpotifyParser", "Page parse error", e)
        }

        tracks
    }

    suspend fun parseTrack(url: String): List<SpotifyTrack> = withContext(Dispatchers.IO) {
        val trackId = extractTrackId(url) ?: return@withContext emptyList()

        try {
            val embedUrl = "https://open.spotify.com/embed/track/$trackId"
            val request = Request.Builder()
                .url(embedUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36")
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""
            response.close()

            val embedTracks = parseEmbedHtml(body)
            if (embedTracks.isNotEmpty()) {
                return@withContext embedTracks
            }
        } catch (e: Exception) {
            android.util.Log.e("SpotifyParser", "Track embed parse error", e)
        }

        emptyList()
    }

    internal fun extractPlaylistId(url: String): String? {
        // https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M?si=...
        val regex = Regex("""playlist/([a-zA-Z0-9]+)""")
        return regex.find(url)?.groupValues?.getOrNull(1)
    }

    internal fun extractTrackId(url: String): String? {
        // https://open.spotify.com/track/11dFghVXANMlKmJXsNCbNl?si=...
        val regex = Regex("""track/([a-zA-Z0-9]+)""")
        return regex.find(url)?.groupValues?.getOrNull(1)
    }

    /**
     * Spotify embed HTML'inden resource JSON'unu çıkarıp track listesini parse eder.
     * Embed sayfasında `<script id="__NEXT_DATA__"` veya inline JSON bulunabilir.
     */
    fun parseEmbedHtml(html: String): List<SpotifyTrack> {
        val tracks = mutableListOf<SpotifyTrack>()

        // Playlist embed sayfasında track'ler genelde HTML satırları halinde gelir.
        val playlistRowRegex = Regex(
            """<li[^>]*data-testid="tracklist-row-[^\"]*"[^>]*>.*?<h3[^>]*>(.*?)</h3>.*?<h4[^>]*>(.*?)</h4>""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )
        playlistRowRegex.findAll(html).forEach { match ->
            val title = match.groupValues[1].stripHtml().trim()
            val artist = match.groupValues[2].stripHtml().trim()
            if (title.isNotBlank() && artist.isNotBlank()) {
                tracks.add(SpotifyTrack(title, artist))
            }
        }

        if (tracks.isNotEmpty()) {
            return tracks.distinctBy { "${it.name}_${it.artist}" }
        }

        // __NEXT_DATA__ script tag'ından JSON çıkar
        val nextDataRegex = Regex("""<script\s+id="__NEXT_DATA__"[^>]*>(.*?)</script>""", RegexOption.DOT_MATCHES_ALL)
        val nextDataMatch = nextDataRegex.find(html)

        if (nextDataMatch != null) {
            val jsonStr = nextDataMatch.groupValues[1]
            try {
                val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                val element = json.parseToJsonElement(jsonStr)
                val tracklist = findTracksInJson(element)
                if (tracklist.isNotEmpty()) return tracklist
            } catch (e: Exception) {
                android.util.Log.e("SpotifyParser", "JSON parse error in __NEXT_DATA__", e)
            }
        }

        // Alternatif: resource script tag'ından çıkar
        val resourceRegex = Regex("""<script[^>]*id="initial-state"[^>]*>(.*?)</script>""", RegexOption.DOT_MATCHES_ALL)
        val resourceMatch = resourceRegex.find(html)
        if (resourceMatch != null) {
            try {
                val decoded = String(android.util.Base64.decode(resourceMatch.groupValues[1], android.util.Base64.DEFAULT))
                val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                val element = json.parseToJsonElement(decoded)
                val tracklist = findTracksInJson(element)
                if (tracklist.isNotEmpty()) return tracklist
            } catch (e: Exception) {
                android.util.Log.e("SpotifyParser", "Base64/JSON parse error", e)
            }
        }

        // Fallback: title pattern'leri ile basit regex parsing
        // Embed sayfasında track title'ları genellikle belirli pattern'lerle bulunur
        val titleArtistRegex = Regex(""""name"\s*:\s*"([^"]+)"[^}]*?"artists"[^}]*?"name"\s*:\s*"([^"]+)"""")
        titleArtistRegex.findAll(html).forEach { match ->
            val name = match.groupValues[1].unescapeJson()
            val artist = match.groupValues[2].unescapeJson()
            if (name.isNotBlank() && artist.isNotBlank() && name.length < 200) {
                tracks.add(SpotifyTrack(name, artist))
            }
        }

        return tracks.distinctBy { "${it.name}_${it.artist}" }
    }

    /**
     * Spotify ana sayfa HTML'inden track bilgilerini parse eder.
     * og:description meta tag'ında genellikle şarkı listesi bulunur.
     */
    private fun parseMainPageHtml(html: String): List<SpotifyTrack> {
        val tracks = mutableListOf<SpotifyTrack>()

        // meta og:description'dan parse et
        // Format: "Playlist · Artist1 · Song1, Artist2 · Song2, ..."
        val descRegex = Regex("""<meta\s+(?:property|name)="og:description"\s+content="([^"]*)"[^>]*>""")
        val descMatch = descRegex.find(html)

        if (descMatch != null) {
            val desc = descMatch.groupValues[1].unescapeHtml()
            // Spotify playlist description formatı: "Song by Artist, Song by Artist, ..."
            // veya "Artist · Song, Artist · Song"
            val songEntries = desc.split(",").map { it.trim() }
            for (entry in songEntries) {
                // "Artist · Song" veya "Song by Artist" formatları
                when {
                    entry.contains(" · ") -> {
                        val parts = entry.split(" · ", limit = 2)
                        if (parts.size == 2) {
                            // İlk kısım genelde sanatçı adı, ikinci kısım playlist adı veya başka bilgi olabilir
                            // Spotify genelde "Song by Artist" veya düz liste verir
                            tracks.add(SpotifyTrack(name = parts[1].trim(), artist = parts[0].trim()))
                        }
                    }
                    entry.contains(" by ", ignoreCase = true) -> {
                        val parts = entry.split(" by ", ignoreCase = true, limit = 2)
                        if (parts.size == 2) {
                            tracks.add(SpotifyTrack(name = parts[0].trim(), artist = parts[1].trim()))
                        }
                    }
                }
            }
        }

        // Ayrıca sayfadaki JSON-LD verisinden de parse et
        val jsonLdRegex = Regex("""<script\s+type="application/ld\+json"[^>]*>(.*?)</script>""", RegexOption.DOT_MATCHES_ALL)
        jsonLdRegex.findAll(html).forEach { match ->
            try {
                val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                val element = json.parseToJsonElement(match.groupValues[1])
                val jsonTracks = findTracksInJson(element)
                if (jsonTracks.isNotEmpty()) return jsonTracks
            } catch (_: Exception) {}
        }

        // Son çare: inline JSON'dan track name+artist çıkar
        val inlineTracks = parseEmbedHtml(html)
        if (inlineTracks.isNotEmpty()) return inlineTracks

        return tracks.distinctBy { "${it.name}_${it.artist}" }
    }

    /**
     * Genel JSON yapısından track bilgilerini recursive olarak bul.
     */
    private fun findTracksInJson(element: kotlinx.serialization.json.JsonElement): List<SpotifyTrack> {
        val tracks = mutableListOf<SpotifyTrack>()

        when (element) {
            is kotlinx.serialization.json.JsonObject -> {
                // Track objesi mi kontrol et
                val name = element["name"]?.let {
                    if (it is kotlinx.serialization.json.JsonPrimitive) it.content else null
                }
                val artists = element["artists"]
                val type = element["type"]?.let {
                    if (it is kotlinx.serialization.json.JsonPrimitive) it.content else null
                }

                if (name != null && artists is kotlinx.serialization.json.JsonArray && type == "track") {
                    val artistName = artists.firstOrNull()?.let { artistObj ->
                        if (artistObj is kotlinx.serialization.json.JsonObject) {
                            artistObj["name"]?.let { n ->
                                if (n is kotlinx.serialization.json.JsonPrimitive) n.content else null
                            }
                        } else null
                    } ?: ""
                    tracks.add(SpotifyTrack(name, artistName))
                }

                // Recursive search
                element.values.forEach { value ->
                    tracks.addAll(findTracksInJson(value))
                }
            }
            is kotlinx.serialization.json.JsonArray -> {
                element.forEach { item ->
                    tracks.addAll(findTracksInJson(item))
                }
            }
            else -> {}
        }

        return tracks.distinctBy { "${it.name}_${it.artist}" }
    }

    private fun String.unescapeJson(): String {
        return this.replace("\\\"", "\"")
            .replace("\\\\", "\\")
            .replace("\\/", "/")
            .replace("\\n", "\n")
            .replace("\\t", "\t")
            .replace("\\u0026", "&")
    }

    private fun String.unescapeHtml(): String {
        return this.replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&#x27;", "'")
            .replace("&#x2F;", "/")
    }

    private fun String.stripHtml(): String {
        return this.replace(Regex("<[^>]+>"), "")
            .replace("&amp;", "&")
            .replace("&#x27;", "'")
            .replace("&nbsp;", " ")
            .unescapeHtml()
            .trim()
    }
}
