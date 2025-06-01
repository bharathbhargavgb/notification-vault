package com.bharath.notificationvault.ui.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.switchMap
import androidx.lifecycle.viewModelScope
import com.bharath.notificationvault.data.db.entity.CapturedNotification
import com.bharath.notificationvault.data.repository.NotificationRepository
import kotlinx.coroutines.launch

class NotificationViewModel(private val repository: NotificationRepository) : ViewModel() {

    private val _selectedAppNameFilter = MutableLiveData<String?>(null) // Filter by app name (display value)

    val notifications: LiveData<List<CapturedNotification>> = _selectedAppNameFilter.switchMap { appName ->
        if (appName == null) {
            repository.allNotificationsLast7Days
        } else {
            // This requires a bit of a workaround if we want to filter by appName but the DAO filters by packageName.
            // A more direct approach would be to get packageName from appName first.
            // For simplicity in this switchMap, we'd ideally enhance repository or DAO.
            // Let's assume for now the repository method can handle appName or we adjust.
            // For a cleaner LiveData transformation, it's better if repository directly supports filtering by appName
            // or we do a more complex transformation here (e.g., using MediatorLiveData).

            // Simpler: ViewModel decides which repository method to call.
            // The LiveData will update when _selectedPackageNameForQuery changes.
            _packageNameForQuery.switchMap { pkgName ->
                if (pkgName == null) repository.allNotificationsLast7Days
                else repository.getNotificationsByAppLast7Days(pkgName)
            }
        }
    }
    // Helper LiveData to trigger the query based on package name
    private val _packageNameForQuery = MutableLiveData<String?>(null)


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

    fun cleanupOldNotifications() {
        viewModelScope.launch {
            repository.deleteOldNotifications()
        }
    }

    // Initialize with no filter and trigger initial load
    init {
        setAppFilter(null)
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