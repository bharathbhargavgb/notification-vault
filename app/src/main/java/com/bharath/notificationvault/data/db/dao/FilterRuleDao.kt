package com.bharath.notificationvault.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.bharath.notificationvault.data.db.entity.FilterRule
import kotlinx.coroutines.flow.Flow

@Dao
interface FilterRuleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rule: FilterRule)

    @Delete
    suspend fun delete(rule: FilterRule)

    @Query("SELECT * FROM filter_rules ORDER BY id DESC")
    fun getAll(): Flow<List<FilterRule>>
}