package com.bharath.notificationvault.data.repository

import androidx.lifecycle.LiveData
import com.bharath.notificationvault.data.db.dao.NotificationDao
import com.bharath.notificationvault.data.db.entity.CapturedNotification
import java.util.Calendar

class NotificationRepository(private val notificationDao: NotificationDao) {

    private fun getSevenDaysAgoMillis(): Long {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, -7)
        calendar.set(Calendar.HOUR_OF_DAY, 0) // Start of the day, 7 days ago
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    val allNotificationsLast7Days: LiveData<List<CapturedNotification>> =
        notificationDao.getNotificationsLast7Days(getSevenDaysAgoMillis())

    fun getNotificationsByAppLast7Days(packageName: String): LiveData<List<CapturedNotification>> {
        return notificationDao.getNotificationsByAppLast7Days(packageName, getSevenDaysAgoMillis())
    }

    val uniqueAppNamesForFilter: LiveData<List<String>> = notificationDao.getUniqueAppNames()

    suspend fun getPackageNameByAppName(appName: String): String? {
        return notificationDao.getPackageNameByAppName(appName)
    }

    suspend fun insert(notification: CapturedNotification) {
        notificationDao.insert(notification)
    }

    suspend fun deleteNotificationsByIds(ids: List<Long>) {
        if (ids.isNotEmpty()) {
            notificationDao.deleteByIds(ids)
        }
    }

    suspend fun deleteOldNotifications() {
        // Delete notifications older than 7 days (from the start of that 7th day)
        notificationDao.deleteOldNotifications(getSevenDaysAgoMillis())
    }

    suspend fun deleteAllNotifications() {
        notificationDao.deleteAll()
    }
}