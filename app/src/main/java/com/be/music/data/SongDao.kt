package com.be.music.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SongDao {
    @Query("SELECT * FROM songs ORDER BY title ASC")
    fun getAllSongs(): Flow<List<Song>>

    @Query("SELECT * FROM songs")
    suspend fun getAllSongsOnce(): List<Song>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSongs(songs: List<Song>)

    @Query("DELETE FROM songs")
    suspend fun deleteAllSongs()

    @Query("SELECT * FROM songs WHERE id IN (:ids)")
    suspend fun getSongsByIds(ids: List<Long>): List<Song>
    
    @Delete
    suspend fun deleteSongs(songs: List<Song>)

    @Update
    suspend fun updateSong(song: Song)

    @Query("SELECT * FROM songs WHERE artistName = :artistName")
    fun getSongsByArtist(artistName: String): Flow<List<Song>>

    @Query("SELECT * FROM songs WHERE albumName = :albumName")
    fun getSongsByAlbum(albumName: String): Flow<List<Song>>
}
