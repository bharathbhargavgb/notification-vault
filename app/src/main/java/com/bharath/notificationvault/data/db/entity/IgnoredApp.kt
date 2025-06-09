package com.bharath.notificationvault.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ignored_apps")
data class IgnoredApp(
    @PrimaryKey val packageName: String
)