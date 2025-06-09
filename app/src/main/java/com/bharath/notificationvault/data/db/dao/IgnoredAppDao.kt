package com.bharath.notificationvault.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.bharath.notificationvault.data.db.entity.IgnoredApp
import kotlinx.coroutines.flow.Flow

@Dao
interface IgnoredAppDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(ignoredApp: IgnoredApp)

    @Query("DELETE FROM ignored_apps WHERE packageName = :packageName")
    suspend fun delete(packageName: String)

    @Query("SELECT * FROM ignored_apps")
    fun getAll(): Flow<List<IgnoredApp>>
}