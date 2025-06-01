package com.bharath.notificationvault.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notifications")
data class CapturedNotification(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val appName: String,
    val packageName: String, // Store package name for filtering and icon loading
    val title: String?,
    val textContent: String?,
    val postTimeMillis: Long,
    val postTimeString: String // Formatted string for display
)