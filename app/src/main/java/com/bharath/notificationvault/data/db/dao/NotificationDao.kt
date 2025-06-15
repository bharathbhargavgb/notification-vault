package com.bharath.notificationvault.data.db.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.bharath.notificationvault.data.db.entity.CapturedNotification

@Dao
interface NotificationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(notification: CapturedNotification)

    @Query("SELECT * FROM notifications WHERE postTimeMillis >= :sevenDaysAgoMillis ORDER BY postTimeMillis DESC")
    fun getNotificationsLast7Days(sevenDaysAgoMillis: Long): LiveData<List<CapturedNotification>>

    @Query("SELECT * FROM notifications WHERE isDismissed = 1 AND postTimeMillis >= :sevenDaysAgoMillis ORDER BY dismissalTimeMillis")
    fun getDismissedNotificationsLast7Days(sevenDaysAgoMillis: Long): LiveData<List<CapturedNotification>>

    @Query("SELECT * FROM notifications WHERE packageName = :packageName AND postTimeMillis >= :sevenDaysAgoMillis ORDER BY postTimeMillis DESC")
    fun getNotificationsByAppLast7Days(packageName: String, sevenDaysAgoMillis: Long): LiveData<List<CapturedNotification>>

    @Query("SELECT DISTINCT appName FROM notifications ORDER BY appName COLLATE NOCASE ASC") // Using appName for filter display
    fun getUniqueAppNames(): LiveData<List<String>>

    // Get package name for a given app name (assumes app names are unique enough for this purpose)
    // For more robust filtering, you might store and filter by packageName directly in the UI if app names can collide.
    @Query("SELECT DISTINCT packageName FROM notifications WHERE appName = :appName LIMIT 1")
    suspend fun getPackageNameByAppName(appName: String): String?

    @Query("DELETE FROM notifications WHERE postTimeMillis < :olderThanMillis")
    suspend fun deleteOldNotifications(olderThanMillis: Long)

    @Query("DELETE FROM notifications WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("DELETE FROM notifications") // Make sure table name matches your entity
    suspend fun deleteAll()

    @Query("UPDATE notifications SET isDismissed = 1, dismissalTimeMillis = :dismissalTime WHERE key = :key")
    suspend fun markAsDismissed(key: String, dismissalTime: Long)
}