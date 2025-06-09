package com.bharath.notificationvault.ui.viewmodel

import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asLiveData
import androidx.lifecycle.map
import androidx.lifecycle.switchMap
import androidx.lifecycle.viewModelScope
import com.bharath.notificationvault.data.db.entity.CapturedNotification
import com.bharath.notificationvault.data.db.entity.FilterRule
import com.bharath.notificationvault.data.db.entity.IgnoredApp
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

private enum class TimeOfDay {
    UNGODLY, MORNING, AFTERNOON, EVENING, NIGHT
}

sealed class NotificationListItem {
    data class NotificationItem(val notification: CapturedNotification) : NotificationListItem()
    data class HeaderItem(val date: String) : NotificationListItem()
    data class SubHeaderItem(val timeOfDay: String, val id: String) : NotificationListItem()
}


class NotificationViewModel(private val repository: NotificationRepository) : ViewModel() {

    private val _selectedAppNameFilter = MutableLiveData<String?>(null)
    private val _searchQuery = MutableLiveData<String?>(null)
    private val _selectedTab = MutableLiveData<Int>(0)
    private val _packageNameForQuery = MutableLiveData<String?>(null)

    // --- LiveData for Ignore Rules ---
    val ignoredApps: LiveData<List<IgnoredApp>> = repository.ignoredApps.asLiveData()
    val filterRules: LiveData<List<FilterRule>> = repository.filterRules.asLiveData()


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

    val notifications: LiveData<List<CapturedNotification>> = MediatorLiveData<List<CapturedNotification>>().apply {
        var rawList: List<CapturedNotification>? = null
        var ignoredList: List<IgnoredApp>? = null
        var rulesList: List<FilterRule>? = null

        fun update() {
            if (rawList != null && ignoredList != null && rulesList != null) {
                viewModelScope.launch(Dispatchers.Default) {
                    val searchFiltered = filterNotifications(rawList, _searchQuery.value)
                    val ignoreFiltered = applyIgnoreRules(searchFiltered, ignoredList!!, rulesList!!)
                    postValue(ignoreFiltered)
                }
            }
        }

        addSource(notificationsFromRepository) { rawList = it; update() }
        addSource(ignoredApps) { ignoredList = it; update() }
        addSource(filterRules) { rulesList = it; update() }
        addSource(_searchQuery) { update() }
    }

    val groupedNotifications: LiveData<List<NotificationListItem>> = notifications.map { flatList ->
        groupNotificationsByDate(flatList)
    }


    private val _isSelectionModeActive = MutableStateFlow(false)
    val isSelectionModeActive: StateFlow<Boolean> = _isSelectionModeActive.asStateFlow()

    private val _selectedNotificationIds = mutableStateListOf<Long>()
    val selectedNotificationIds: List<Long> get() = _selectedNotificationIds

    private fun applyIgnoreRules(
        notifications: List<CapturedNotification>,
        ignoredApps: List<IgnoredApp>,
        filterRules: List<FilterRule>
    ): List<CapturedNotification> {
        if (ignoredApps.isEmpty() && filterRules.isEmpty()) {
            return notifications
        }

        val ignoredPackageNames = ignoredApps.map { it.packageName }.toSet()

        return notifications.filter { notification ->
            if (notification.packageName in ignoredPackageNames) {
                return@filter false
            }

            val matchesAnyRule = filterRules.any { rule ->
                val appMatch = rule.packageName == null || rule.packageName == notification.packageName
                if (!appMatch) return@any false

                val title = notification.title ?: ""
                val content = notification.textContent ?: ""

                val titleMatch = rule.titleKeyword.isNullOrBlank() || title.contains(rule.titleKeyword, ignoreCase = true)
                val contentMatch = rule.contentKeyword.isNullOrBlank() || content.contains(rule.contentKeyword, ignoreCase = true)

                val hasKeywords = !rule.titleKeyword.isNullOrBlank() || !rule.contentKeyword.isNullOrBlank()

                hasKeywords && titleMatch && contentMatch
            }

            !matchesAnyRule
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

    private fun getTimeOfDay(calendar: Calendar): TimeOfDay {
        return when (calendar.get(Calendar.HOUR_OF_DAY)) {
            in 0..5 -> TimeOfDay.UNGODLY
            in 6..11 -> TimeOfDay.MORNING
            in 12..15 -> TimeOfDay.AFTERNOON
            in 16..20 -> TimeOfDay.EVENING
            else -> TimeOfDay.NIGHT
        }
    }

    private fun groupNotificationsByDate(notifications: List<CapturedNotification>?): List<NotificationListItem> {
        if (notifications.isNullOrEmpty()) return emptyList()
        val items = mutableListOf<NotificationListItem>()
        val today = Calendar.getInstance()
        val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
        val dateFormat = SimpleDateFormat("MMMM d, YYYY", Locale.getDefault())
        var lastHeaderDay = -1
        var lastHeaderYear = -1
        var lastTimeOfDay: TimeOfDay? = null
        notifications.forEach { notification ->
            val currentCal = Calendar.getInstance().apply { timeInMillis = notification.postTimeMillis }
            val currentDay = currentCal.get(Calendar.DAY_OF_YEAR)
            val currentYear = currentCal.get(Calendar.YEAR)
            val currentTimeOfDay = getTimeOfDay(currentCal)
            if (currentDay != lastHeaderDay || currentYear != lastHeaderYear) {
                val headerText = when {
                    currentDay == today.get(Calendar.DAY_OF_YEAR) && currentYear == today.get(Calendar.YEAR) -> "Today"
                    currentDay == yesterday.get(Calendar.DAY_OF_YEAR) && currentYear == yesterday.get(Calendar.YEAR) -> "Yesterday"
                    else -> dateFormat.format(currentCal.time)
                }
                items.add(NotificationListItem.HeaderItem(headerText))
                lastHeaderDay = currentDay
                lastHeaderYear = currentYear
                lastTimeOfDay = null
            }
            if (currentTimeOfDay != lastTimeOfDay) {
                val subHeaderText = when (currentTimeOfDay) {
                    TimeOfDay.UNGODLY -> "Ungodly Hours"
                    TimeOfDay.MORNING -> "Morning"
                    TimeOfDay.AFTERNOON -> "Afternoon"
                    TimeOfDay.EVENING -> "Evening"
                    TimeOfDay.NIGHT -> "Night"
                }
                val subHeaderId = "$currentYear-$currentDay-${currentTimeOfDay.name}"
                items.add(NotificationListItem.SubHeaderItem(subHeaderText, subHeaderId))
                lastTimeOfDay = currentTimeOfDay
            }
            items.add(NotificationListItem.NotificationItem(notification))
        }
        return items
    }

    // The list of unique app names for the filter is now derived from the final,
    // filtered list of notifications, ensuring it's always up-to-date.
    val uniqueAppNamesForFilter: LiveData<List<String>> = notifications.map { notificationList ->
        notificationList.map { it.appName }.distinct().sortedWith(String.CASE_INSENSITIVE_ORDER)
    }

    fun setAppFilter(appName: String?) {
        _selectedAppNameFilter.value = appName
        if (appName == null) {
            _packageNameForQuery.value = null
        } else {
            viewModelScope.launch { _packageNameForQuery.value = repository.getPackageNameByAppName(appName) }
        }
    }

    fun setSearchQuery(query: String?) { _searchQuery.value = query }
    fun setSelectedTab(index: Int) { _selectedTab.value = index }
    fun cleanupOldNotifications() { viewModelScope.launch { repository.deleteOldNotifications() } }
    fun deleteAllNotifications() { viewModelScope.launch(Dispatchers.IO) { repository.deleteAllNotifications() } }

    fun ignoreApp(packageName: String) = viewModelScope.launch { repository.addIgnoredApp(packageName) }
    fun unignoreApp(packageName: String) = viewModelScope.launch { repository.removeIgnoredApp(packageName) }
    fun saveFilterRule(rule: FilterRule) = viewModelScope.launch { repository.addFilterRule(rule) }
    fun deleteFilterRule(rule: FilterRule) = viewModelScope.launch { repository.deleteFilterRule(rule) }

    fun toggleSelectionMode() {
        _isSelectionModeActive.update { !it }
        if (!_isSelectionModeActive.value) clearSelection()
    }

    fun activateSelectionMode(initialSelectedId: Long) {
        if (!_isSelectionModeActive.value) _isSelectionModeActive.value = true
        toggleNotificationSelection(initialSelectedId)
    }

    fun toggleNotificationSelection(notificationId: Long) {
        if (_selectedNotificationIds.contains(notificationId)) _selectedNotificationIds.remove(notificationId)
        else _selectedNotificationIds.add(notificationId)
    }

    fun clearSelection() { _selectedNotificationIds.clear() }

    fun selectAllVisible(visibleNotificationIds: List<Long>) {
        _selectedNotificationIds.clear()
        _selectedNotificationIds.addAll(visibleNotificationIds.filterNot { _selectedNotificationIds.contains(it) })
        if (_selectedNotificationIds.isNotEmpty() && !_isSelectionModeActive.value) _isSelectionModeActive.value = true
    }

    fun deleteSelectedNotifications() {
        if (_selectedNotificationIds.isNotEmpty()) {
            viewModelScope.launch(Dispatchers.IO) {
                repository.deleteNotificationsByIds(_selectedNotificationIds.toList())
                _selectedNotificationIds.clear()
                _isSelectionModeActive.value = false
            }
        }
    }

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