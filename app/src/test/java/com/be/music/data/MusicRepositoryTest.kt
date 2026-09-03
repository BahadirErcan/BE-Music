package com.be.music.data

import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test



class MusicRepositoryTest {

    private val songDao: SongDao = mockk()
    private val artistDao: ArtistDao = mockk()
    private val albumDao: AlbumDao = mockk()
    private val playlistDao: PlaylistDao = mockk()
    private val filterSettingsDao: FilterSettingsDao = mockk()
    private val context: android.content.Context = mockk()
    private val playHistoryManager: PlayHistoryManager = mockk()

    private lateinit var repository: MusicRepository

    @Before
    fun setup() {
        repository = MusicRepository(
            context,
            songDao,
            artistDao,
            albumDao,
            playlistDao,
            filterSettingsDao,
            playHistoryManager
        )
    }

    @Test
    fun `allSongs should return songs from songDao`() = runTest {
        // Given
        val mockSongs = listOf(
            Song(1, "Title", "Artist", "Album", 1000, "path", "uri", 0)
        )
        every { songDao.getAllSongs() } returns flowOf(mockSongs)

        // When
        repository.allSongs.collect { songs ->
            // Then
            assertEquals(1, songs.size)
            assertEquals("Title", songs[0].title)
        }
    }
}
