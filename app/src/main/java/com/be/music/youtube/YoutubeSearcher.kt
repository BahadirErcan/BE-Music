package com.be.music.youtube

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem

class YoutubeSearcher {
    suspend fun search(query: String): List<YoutubeVideo> = withContext(Dispatchers.IO) {
        try {
            val service = ServiceList.YouTube
            // Arama
            val searchInfo = SearchInfo.getInfo(service, service.searchQHFactory.fromQuery(query))

            // Sonuçları filtrele (sadece video)
            searchInfo.relatedItems
                .filterIsInstance<StreamInfoItem>()
                .map { item ->
                    val videoId = try {
                        val url = item.url
                        when {
                            url.contains("v=") -> url.split("v=").last().split("&").first()
                            url.contains("youtu.be/") -> url.split("youtu.be/").last().split("?").first()
                            else -> url.split("/").last()
                        }
                    } catch (ex: Exception) {
                        ""
                    }

                    YoutubeVideo(
                        id = videoId,
                        title = item.name ?: "Unknown",
                        author = item.uploaderName ?: "Unknown",
                        thumbnailUrl = item.thumbnails?.firstOrNull()?.url ?: "",
                        duration = item.duration.toString()
                    )
                }
                .filter { it.id.isNotEmpty() }
        } catch (e: Exception) {
            android.util.Log.e("YoutubeSearcher", "Search error for query: $query", e)
            emptyList()
        }
    }
}
