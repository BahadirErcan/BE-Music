package com.be.music.youtube

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem

class YoutubeSearcher {

    data class SearchResult(
        val items: List<YoutubeVideo>,
        /** Sonraki sayfa; null ise veri bitti demektir. */
        val nextPage: Page?
    )

    /**
     * Madde: İndirme sayfasında aşağı kaydırıldıkça YouTube sayfalarının
     * devamı da yüklenebilsin diye sayfalama destekli arama.
     */
    suspend fun searchPage(query: String, page: Page? = null): SearchResult =
        withContext(Dispatchers.IO) {
            try {
                val service = ServiceList.YouTube
                val queryHandler = service.searchQHFactory.fromQuery(query)
                val itemsPage = if (page == null) {
                    // ilk sayfa
                    SearchInfo.getInfo(service, queryHandler).let { info ->
                        org.schabi.newpipe.extractor.ListExtractor.InfoItemsPage(info.relatedItems, info.nextPage, emptyList())
                    }
                } else {
                    SearchInfo.getMoreItems(service, queryHandler, page)
                }

                val items = itemsPage.items
                    .filterIsInstance<StreamInfoItem>()
                    .mapNotNull { item -> item.toYoutubeVideo() }

                SearchResult(items.filter { it.id.isNotEmpty() }, itemsPage.nextPage)
            } catch (e: Exception) {
                android.util.Log.e("YoutubeSearcher", "Search error for query: $query (page: $page)", e)
                SearchResult(emptyList(), null)
            }
        }

    /** Eski API uyumluluğu: sadece ilk sayfayı döndürür (söz araması vb. kullanır). */
    suspend fun search(query: String): List<YoutubeVideo> = searchPage(query, null).items

    private fun StreamInfoItem.toYoutubeVideo(): YoutubeVideo? {
        return try {
            val url = url
            val videoId = when {
                url.contains("v=") -> url.split("v=").last().split("&").first()
                url.contains("youtu.be/") -> url.split("youtu.be/").last().split("?").first()
                else -> url.split("/").last()
            }

            YoutubeVideo(
                id = videoId,
                title = name ?: "Unknown",
                author = uploaderName ?: "Unknown",
                thumbnailUrl = thumbnails?.firstOrNull()?.url ?: "",
                durationSeconds = duration
            )
        } catch (ex: Exception) {
            null
        }
    }
}
