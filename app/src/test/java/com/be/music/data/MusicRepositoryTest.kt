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
    private val songOverrideDao: SongOverrideDao = mockk()
    private val context: android.content.Context = mockk()
    private val playHistoryManager: PlayHistoryManager = mockk()

    @Before
    fun setup() {
        every { songDao.getAllSongs() } returns flowOf(emptyList())
        every { artistDao.getAllArtists() } returns flowOf(emptyList())
        every { albumDao.getAllAlbums() } returns flowOf(emptyList())
        every { playlistDao.getAllPlaylists() } returns flowOf(emptyList())
        every { filterSettingsDao.getSettings() } returns flowOf(FilterSettings())
    }

    @Test
    fun `allSongs should return songs from songDao`() = runTest {
        // Given
        val mockSongs = listOf(
            Song(1, "Title", "Artist", "Album", 1000, "path", "uri", 0)
        )
        every { songDao.getAllSongs() } returns flowOf(mockSongs)

        // When
        val repository = MusicRepository(
            context,
            songDao,
            artistDao,
            albumDao,
            playlistDao,
            filterSettingsDao,
            songOverrideDao,
            playHistoryManager
        )

        // Then
        repository.allSongs.collect { songs ->
            assertEquals(1, songs.size)
            assertEquals("Title", songs[0].title)
        }
    }
}