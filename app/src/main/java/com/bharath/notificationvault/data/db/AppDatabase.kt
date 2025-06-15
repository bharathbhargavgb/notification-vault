package com.bharath.notificationvault.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.bharath.notificationvault.data.db.dao.FilterRuleDao
import com.bharath.notificationvault.data.db.dao.IgnoredAppDao
import com.bharath.notificationvault.data.db.dao.NotificationDao
import com.bharath.notificationvault.data.db.entity.CapturedNotification
import com.bharath.notificationvault.data.db.entity.FilterRule
import com.bharath.notificationvault.data.db.entity.IgnoredApp

@Database(
    entities = [CapturedNotification::class, FilterRule::class, IgnoredApp::class],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun notificationDao(): NotificationDao
    abstract fun filterRuleDao(): FilterRuleDao
    abstract fun ignoredAppDao(): IgnoredAppDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notifications ADD COLUMN dismissalTimeMillis INTEGER")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "notification_logger_database"
                )
                    .addMigrations(MIGRATION_3_4)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}