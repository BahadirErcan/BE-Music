package com.be.music.youtube

import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadFormatResolverTest {
    @Test
    fun resolveVideoFormat_returnsBestFormat_whenQualityIsHighest() {
        val format = DownloadFormatResolver.resolveVideoFormat("mp4", "Highest")

        assertEquals(
            "bestvideo[ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4]/best",
            format
        )
    }

    @Test
    fun resolveVideoFormat_returnsBestFormat_whenQualityIsTurkishHighest() {
        val format = DownloadFormatResolver.resolveVideoFormat("mp4", "En Yüksek")

        assertEquals(
            "bestvideo[ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4]/best",
            format
        )
    }
}
