package com.be.music.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface FilterSettingsDao {
    @Query("SELECT * FROM filter_settings WHERE id = 1 LIMIT 1")
    fun getSettings(): Flow<FilterSettings?>

    @Query("SELECT * FROM filter_settings WHERE id = 1 LIMIT 1")
    suspend fun getSettingsOnce(): FilterSettings?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSettings(settings: FilterSettings)
}
