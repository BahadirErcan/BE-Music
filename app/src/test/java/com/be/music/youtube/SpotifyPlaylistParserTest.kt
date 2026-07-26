package com.be.music.youtube

import org.junit.Assert.assertEquals
import org.junit.Test

class SpotifyPlaylistParserTest {

    @Test
    fun `parseEmbedHtml extracts tracks from playlist row markup`() {
        val html = """
            <html>
              <body>
                <ol>
                  <li data-testid="tracklist-row-0">
                    <h3 class="TracklistRow_title__1RtS6">Song One</h3>
                    <h4 class="TracklistRow_subtitle___DhJK">Artist One</h4>
                  </li>
                  <li data-testid="tracklist-row-1">
                    <h3 class="TracklistRow_title__1RtS6">Song Two</h3>
                    <h4 class="TracklistRow_subtitle___DhJK">Artist Two</h4>
                  </li>
                </ol>
              </body>
            </html>
        """

        val tracks = SpotifyPlaylistParser.parseEmbedHtml(html)

        assertEquals(
            listOf(
                SpotifyTrack("Song One", "Artist One"),
                SpotifyTrack("Song Two", "Artist Two")
            ),
            tracks
        )
    }
}
