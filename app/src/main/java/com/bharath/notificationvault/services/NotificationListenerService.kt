package com.bharath.notificationvault.services

import android.app.Notification
import android.content.pm.PackageManager
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.bharath.notificationvault.data.db.AppDatabase
import com.bharath.notificationvault.data.db.dao.NotificationDao
import com.bharath.notificationvault.data.db.entity.CapturedNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NotificationListenerService : NotificationListenerService() {

    private val TAG = "NotificationListener"

    private val job = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + job)
    private lateinit var notificationDao: NotificationDao

    // Cache for the last processed notification's details
    private var lastNotificationAppName: String? = null
    private var lastNotificationTitle: String? = null
    private var lastNotificationText: String? = null
    private var lastNotificationPostTime: Long = 0L
    private val DEBOUNCE_PERIOD_MS = 500

    override fun onCreate() {
        super.onCreate()
        notificationDao = AppDatabase.getDatabase(applicationContext).notificationDao()
        Log.i(TAG, "NotificationListenerService Created")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        sbn?.let { statusBarNotification ->
            val packageName = statusBarNotification.packageName
            // Ignore notifications from this app itself to avoid feedback loops or clutter
            if (packageName == applicationContext.packageName) {
                return
            }

            val notification = statusBarNotification.notification
            val extras = notification.extras

            val appName = getAppName(packageName)
            val title = extras.getString(Notification.EXTRA_TITLE)
            val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            val postTimeMillis = statusBarNotification.postTime
            val notificationKey = statusBarNotification.key
            val systemNotificationId = statusBarNotification.id


            if (title.isNullOrBlank() && text.isNullOrBlank()) {
                Log.d(TAG, "Skipping notification with no title or text from $appName (Key: $notificationKey)")
                return
            }

            // Debounce logic using appName and time
            if (appName == lastNotificationAppName &&
                title == lastNotificationTitle &&
                text == lastNotificationText &&
                Math.abs(postTimeMillis - lastNotificationPostTime) < DEBOUNCE_PERIOD_MS) {
                Log.i(TAG, "Duplicate (debounced) notification ignored: $appName - $title (Key: $notificationKey)")
                return
            }
            lastNotificationAppName = appName
            lastNotificationTitle = title
            lastNotificationText = text
            lastNotificationPostTime = postTimeMillis

            val postTimeString = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(postTimeMillis))

            val capturedNotification = CapturedNotification(
                appName = appName,
                packageName = packageName,
                title = title,
                textContent = text,
                postTimeMillis = postTimeMillis,
                postTimeString = postTimeString,
                key = notificationKey
            )

            serviceScope.launch {
                try {
                    notificationDao.insert(capturedNotification)
                    Log.d(TAG, "Notification saved: $appName - $title")
                } catch (e: Exception) {
                    Log.e(TAG, "Error saving notification", e)
                }
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?, rankingMap: RankingMap?, reason: Int) {
        super.onNotificationRemoved(sbn, rankingMap, reason)

        if (reason == REASON_CANCEL || reason == REASON_CANCEL_ALL) {
            sbn?.let { statusBarNotification ->
                serviceScope.launch {
                    try {
                        notificationDao.markAsDismissed(statusBarNotification.key, System.currentTimeMillis())
                        Log.d(
                            TAG,
                            "Notification marked as user-dismissed: ${statusBarNotification.packageName}"
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Error marking notification as dismissed", e)
                    }
                }
            }
        }
    }

    private fun getAppName(packageName: String): String {
        return try {
            val packageManager = applicationContext.packageManager
            val applicationInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getApplicationInfo(packageName, 0)
            }
            packageManager.getApplicationLabel(applicationInfo).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            Log.w(TAG, "App name not found for package: $packageName")
            packageName // Fallback to package name
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel() // Cancel all coroutines when the service is destroyed
        Log.i(TAG, "NotificationListenerService Destroyed")
    }
}