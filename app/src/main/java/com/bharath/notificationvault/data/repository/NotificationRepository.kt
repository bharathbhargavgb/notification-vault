package com.bharath.notificationvault.data.repository

import androidx.lifecycle.LiveData
import com.bharath.notificationvault.data.db.dao.FilterRuleDao
import com.bharath.notificationvault.data.db.dao.IgnoredAppDao
import com.bharath.notificationvault.data.db.dao.NotificationDao
import com.bharath.notificationvault.data.db.entity.CapturedNotification
import com.bharath.notificationvault.data.db.entity.FilterRule
import com.bharath.notificationvault.data.db.entity.IgnoredApp
import kotlinx.coroutines.flow.Flow
import java.util.Calendar

class NotificationRepository(
    private val notificationDao: NotificationDao,
    private val ignoredAppDao: IgnoredAppDao,
    private val filterRuleDao: FilterRuleDao
) {

    private fun getSevenDaysAgoMillis(): Long {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, -7)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    val allNotificationsLast7Days: LiveData<List<CapturedNotification>> =
        notificationDao.getNotificationsLast7Days(getSevenDaysAgoMillis())

    val dismissedNotificationsLast7Days: LiveData<List<CapturedNotification>> =
        notificationDao.getDismissedNotificationsLast7Days(getSevenDaysAgoMillis())

    fun getNotificationsByAppLast7Days(packageName: String): LiveData<List<CapturedNotification>> {
        return notificationDao.getNotificationsByAppLast7Days(packageName, getSevenDaysAgoMillis())
    }

    val uniqueAppNamesForFilter: LiveData<List<String>> = notificationDao.getUniqueAppNames()

    val ignoredApps: Flow<List<IgnoredApp>> = ignoredAppDao.getAll()
    val filterRules: Flow<List<FilterRule>> = filterRuleDao.getAll()

    suspend fun addIgnoredApp(packageName: String) {
        ignoredAppDao.insert(IgnoredApp(packageName = packageName))
    }

    suspend fun removeIgnoredApp(packageName: String) {
        ignoredAppDao.delete(packageName)
    }

    suspend fun addFilterRule(rule: FilterRule) {
        filterRuleDao.insert(rule)
    }

    suspend fun deleteFilterRule(rule: FilterRule) {
        filterRuleDao.delete(rule)
    }

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
        notificationDao.deleteOldNotifications(getSevenDaysAgoMillis())
    }

    suspend fun deleteAllNotifications() {
        notificationDao.deleteAll()
    }
}