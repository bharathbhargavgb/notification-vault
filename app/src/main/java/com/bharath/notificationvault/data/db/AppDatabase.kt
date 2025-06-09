package com.bharath.notificationvault.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.bharath.notificationvault.data.db.dao.FilterRuleDao
import com.bharath.notificationvault.data.db.dao.IgnoredAppDao
import com.bharath.notificationvault.data.db.dao.NotificationDao
import com.bharath.notificationvault.data.db.entity.CapturedNotification
import com.bharath.notificationvault.data.db.entity.FilterRule
import com.bharath.notificationvault.data.db.entity.IgnoredApp

@Database(
    entities = [CapturedNotification::class, FilterRule::class, IgnoredApp::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun notificationDao(): NotificationDao
    abstract fun filterRuleDao(): FilterRuleDao
    abstract fun ignoredAppDao(): IgnoredAppDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "notification_logger_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}