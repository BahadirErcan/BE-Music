package com.be.music.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SongOverrideDao {
    @Query("SELECT * FROM song_overrides")
    suspend fun getAllOnce(): List<SongOverride>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(override: SongOverride)

    @Query("DELETE FROM song_overrides WHERE songId = :songId")
    suspend fun deleteBySongId(songId: Long)
}