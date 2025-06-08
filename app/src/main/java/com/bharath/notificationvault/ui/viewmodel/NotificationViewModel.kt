package com.bharath.notificationvault.ui.viewmodel

import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.map
import androidx.lifecycle.switchMap
import androidx.lifecycle.viewModelScope
import com.bharath.notificationvault.data.db.entity.CapturedNotification
import com.bharath.notificationvault.data.repository.NotificationRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

// Sealed class to represent the different types of items in our list
sealed class NotificationListItem {
    data class NotificationItem(val notification: CapturedNotification) : NotificationListItem()
    data class HeaderItem(val date: String) : NotificationListItem()
}


class NotificationViewModel(private val repository: NotificationRepository) : ViewModel() {

    private val _selectedAppNameFilter = MutableLiveData<String?>(null) // Filter by app name (display value)
    private val _searchQuery = MutableLiveData<String?>(null) // For search text
    private val _selectedTab = MutableLiveData<Int>(0) // 0 for All, 1 for Dismissed

    // Helper LiveData to trigger the query based on package name
    private val _packageNameForQuery = MutableLiveData<String?>(null)

    // LiveData that fetches from the repository based on the app filter and tab
    private val notificationsFromRepository: LiveData<List<CapturedNotification>> = _selectedTab.switchMap { tab ->
        _packageNameForQuery.switchMap { pkgName ->
            val source = when (tab) {
                0 -> if (pkgName == null) repository.allNotificationsLast7Days else repository.getNotificationsByAppLast7Days(pkgName)
                1 -> if (pkgName == null) repository.dismissedNotificationsLast7Days else repository.getNotificationsByAppLast7Days(pkgName).map { list -> list.filter { it.isDismissed } }
                else -> repository.allNotificationsLast7Days
            }
            source
        }
    }

    // Original LiveData for a flat, filtered list. Still used for search and selection logic.
    val notifications: LiveData<List<CapturedNotification>> = MediatorLiveData<List<CapturedNotification>>().apply {
        addSource(notificationsFromRepository) { list ->
            value = filterNotifications(list, _searchQuery.value)
        }
        addSource(_searchQuery) { query ->
            value = filterNotifications(notificationsFromRepository.value, query)
        }
    }

    // New LiveData that transforms the flat list into a list with date headers.
    val groupedNotifications: LiveData<List<NotificationListItem>> = notifications.map { flatList ->
        groupNotificationsByDate(flatList)
    }


    private val _isSelectionModeActive = MutableStateFlow(false)
    val isSelectionModeActive: StateFlow<Boolean> = _isSelectionModeActive.asStateFlow()

    // Using SnapshotStateList for efficient recomposition when items are added/removed
    private val _selectedNotificationIds = mutableStateListOf<Long>()
    val selectedNotificationIds: List<Long> get() = _selectedNotificationIds

    private fun filterNotifications(notifications: List<CapturedNotification>?, query: String?): List<CapturedNotification> {
        val currentList = notifications ?: emptyList()
        if (query.isNullOrBlank()) {
            return currentList
        }
        val lowerCaseQuery = query.lowercase(Locale.getDefault())
        return currentList.filter { notification ->
            (notification.appName.lowercase(Locale.getDefault()).contains(lowerCaseQuery)) ||
                    (notification.title?.lowercase(Locale.getDefault())?.contains(lowerCaseQuery) == true) ||
                    (notification.textContent?.lowercase(Locale.getDefault())?.contains(lowerCaseQuery) == true)
        }
    }
    private fun groupNotificationsByDate(notifications: List<CapturedNotification>?): List<NotificationListItem> {
        if (notifications.isNullOrEmpty()) {
            return emptyList()
        }

        val items = mutableListOf<NotificationListItem>()
        val today = Calendar.getInstance()
        val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
        val dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())

        var lastHeaderDay = -1
        var lastHeaderYear = -1

        notifications.forEach { notification ->
            val currentCal = Calendar.getInstance().apply { timeInMillis = notification.postTimeMillis }
            val currentDay = currentCal.get(Calendar.DAY_OF_YEAR)
            val currentYear = currentCal.get(Calendar.YEAR)

            if (currentDay != lastHeaderDay || currentYear != lastHeaderYear) {
                val headerText = when {
                    currentDay == today.get(Calendar.DAY_OF_YEAR) && currentYear == today.get(Calendar.YEAR) -> "Today"
                    currentDay == yesterday.get(Calendar.DAY_OF_YEAR) && currentYear == yesterday.get(Calendar.YEAR) -> "Yesterday"
                    else -> dateFormat.format(currentCal.time)
                }
                items.add(NotificationListItem.HeaderItem(headerText))
                lastHeaderDay = currentDay
                lastHeaderYear = currentYear
            }
            items.add(NotificationListItem.NotificationItem(notification))
        }
        return items
    }

    val uniqueAppNamesForFilter: LiveData<List<String>> = repository.uniqueAppNamesForFilter

    fun setAppFilter(appName: String?) {
        _selectedAppNameFilter.value = appName
        if (appName == null) {
            _packageNameForQuery.value = null
        } else {
            viewModelScope.launch {
                // Fetch the package name associated with the selected app name for the query
                _packageNameForQuery.value = repository.getPackageNameByAppName(appName)
            }
        }
    }

    fun setSearchQuery(query: String?) {
        _searchQuery.value = query
    }

    fun setSelectedTab(index: Int) {
        _selectedTab.value = index
    }

    fun cleanupOldNotifications() {
        viewModelScope.launch {
            repository.deleteOldNotifications()
        }
    }

    fun deleteAllNotifications() {
        viewModelScope.launch(Dispatchers.IO) { // Perform database operations on a background thread
            repository.deleteAllNotifications()
        }
    }

    fun toggleSelectionMode() {
        _isSelectionModeActive.update { !it }
        if (!_isSelectionModeActive.value) {
            clearSelection() // Clear selection when exiting selection mode
        }
    }

    fun activateSelectionMode(initialSelectedId: Long) {
        if (!_isSelectionModeActive.value) {
            _isSelectionModeActive.value = true
        }
        toggleNotificationSelection(initialSelectedId)
    }

    fun toggleNotificationSelection(notificationId: Long) {
        if (_selectedNotificationIds.contains(notificationId)) {
            _selectedNotificationIds.remove(notificationId)
        } else {
            _selectedNotificationIds.add(notificationId)
        }
    }

    fun clearSelection() {
        _selectedNotificationIds.clear()
    }

    fun selectAllVisible(visibleNotificationIds: List<Long>) {
        // Clear current selection and add all visible ones
        _selectedNotificationIds.clear()
        _selectedNotificationIds.addAll(visibleNotificationIds.filterNot { _selectedNotificationIds.contains(it) })
        if (_selectedNotificationIds.isNotEmpty() && !_isSelectionModeActive.value) {
            _isSelectionModeActive.value = true
        }
    }


    fun deleteSelectedNotifications() {
        if (_selectedNotificationIds.isNotEmpty()) {
            viewModelScope.launch(Dispatchers.IO) {
                repository.deleteNotificationsByIds(_selectedNotificationIds.toList()) // Pass a copy
                _selectedNotificationIds.clear()
                _isSelectionModeActive.value = false // Exit selection mode after deletion
            }
        }
    }

    // Initialize with no filter and trigger initial load
    init {
        setAppFilter(null)
        setSearchQuery(null)
    }
}

class NotificationViewModelFactory(private val repository: NotificationRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(NotificationViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return NotificationViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}