package com.bharath.notificationvault.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "filter_rules")
data class FilterRule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val appName: String?, // Nullable to allow rule for "Any App"
    val packageName: String?, // Nullable to allow rule for "Any App"
    val titleKeyword: String?, // Keyword to match in the notification title
    val contentKeyword: String? // Keyword to match in the notification content
)