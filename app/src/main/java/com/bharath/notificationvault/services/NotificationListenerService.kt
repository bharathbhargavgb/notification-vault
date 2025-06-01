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
            val postTimeString = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(postTimeMillis))

            // Basic check to avoid storing empty or system utility notifications if desired
            if (title.isNullOrBlank() && text.isNullOrBlank()) {
                Log.d(TAG, "Skipping notification with no title or text from $appName")
                return
            }

            val capturedNotification = CapturedNotification(
                appName = appName,
                packageName = packageName,
                title = title,
                textContent = text,
                postTimeMillis = postTimeMillis,
                postTimeString = postTimeString
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

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        // You could optionally log removed notifications or update their status
        // For this app's purpose (listing received notifications), we might not need to do much here.
        sbn?.let {
            Log.d(TAG, "Notification Removed: ${it.packageName} - ${it.notification.extras.getString(Notification.EXTRA_TITLE)}")
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