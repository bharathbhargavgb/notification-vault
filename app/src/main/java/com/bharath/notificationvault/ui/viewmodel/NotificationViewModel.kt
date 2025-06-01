package com.bharath.notificationvault.ui.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.switchMap
import androidx.lifecycle.viewModelScope
import com.bharath.notificationvault.data.db.entity.CapturedNotification
import com.bharath.notificationvault.data.repository.NotificationRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Locale

class NotificationViewModel(private val repository: NotificationRepository) : ViewModel() {

    private val _selectedAppNameFilter = MutableLiveData<String?>(null) // Filter by app name (display value)
    private val _searchQuery = MutableLiveData<String?>(null) // For search text

    // Helper LiveData to trigger the query based on package name
    private val _packageNameForQuery = MutableLiveData<String?>(null)

    // LiveData that fetches from the repository based on the app filter
    private val notificationsFromRepository: LiveData<List<CapturedNotification>> = _packageNameForQuery.switchMap { pkgName ->
        if (pkgName == null) {
            repository.allNotificationsLast7Days
        } else {
            repository.getNotificationsByAppLast7Days(pkgName)
        }
    }

    // MediatorLiveData to combine app filter and search filter
    val notifications: LiveData<List<CapturedNotification>> = MediatorLiveData<List<CapturedNotification>>().apply {
        addSource(notificationsFromRepository) { list ->
            value = filterNotifications(list, _searchQuery.value)
        }
        addSource(_searchQuery) { query ->
            value = filterNotifications(notificationsFromRepository.value, query)
        }
    }

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

    fun cleanupOldNotifications() {
        viewModelScope.launch {
            repository.deleteOldNotifications()
        }
    }

    fun deleteAllNotifications() {
        viewModelScope.launch(Dispatchers.IO) { // Perform database operations on a background thread
            repository.deleteAllNotifications()
            // You might not need to explicitly update the LiveData here if your LiveData
            // source from the repository automatically updates when the underlying data changes.
            // If it doesn't, you might need to re-fetch or clear the current list.
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