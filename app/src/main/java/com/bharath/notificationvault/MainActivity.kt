package com.bharath.notificationvault

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.graphics.drawable.toBitmap
import androidx.core.text.HtmlCompat

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import com.bharath.notificationvault.data.db.AppDatabase
import com.bharath.notificationvault.data.db.entity.CapturedNotification
import com.bharath.notificationvault.data.db.entity.FilterRule
import com.bharath.notificationvault.data.db.entity.IgnoredApp
import com.bharath.notificationvault.data.repository.NotificationRepository
import com.bharath.notificationvault.ui.theme.NotificationVaultTheme
import com.bharath.notificationvault.ui.viewmodel.NotificationListItem
import com.bharath.notificationvault.ui.viewmodel.NotificationViewModel
import com.bharath.notificationvault.ui.viewmodel.NotificationViewModelFactory

import java.text.SimpleDateFormat
import java.util.*


class MainActivity : ComponentActivity() {

    private val notificationViewModel: NotificationViewModel by viewModels {
        val database = AppDatabase.getDatabase(applicationContext)
        NotificationViewModelFactory(
            NotificationRepository(
                database.notificationDao(),
                database.ignoredAppDao(),
                database.filterRuleDao()
            )
        )
    }

    private val permissionCheckTrigger = mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NotificationVaultTheme {
                MainAppScreen(notificationViewModel, permissionCheckTrigger.value)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        permissionCheckTrigger.value++
        Log.d("MainActivity", "onResume triggered, permissionCheckTrigger new value: ${permissionCheckTrigger.value}")
    }
}

@Composable
fun MainAppScreen(viewModel: NotificationViewModel, permissionCheckKey: Int) {
    val context = LocalContext.current

    // Create a state holder for the permission status.
    var hasNotificationAccess by remember {
        mutableStateOf(isNotificationServiceEnabled(context))
    }

    // Use a LaunchedEffect to re-check the permission whenever the key changes.
    // This is a more explicit way to handle side-effects from state changes.
    LaunchedEffect(permissionCheckKey) {
        hasNotificationAccess = isNotificationServiceEnabled(context)
    }

    if (!hasNotificationAccess) {
        NotificationAccessScreen {
            requestNotificationAccess(context)
        }
    } else {
        NotificationScreenWithTabs(viewModel)
    }
}

fun isNotificationServiceEnabled(context: Context): Boolean {
    val pkgName = context.packageName
    val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
    if (!TextUtils.isEmpty(flat)) {
        val names = flat.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        for (name in names) {
            val cn = ComponentName.unflattenFromString(name)
            if (cn != null) {
                if (TextUtils.equals(pkgName, cn.packageName)) {
                    return true
                }
            }
        }
    }
    Log.d("MainActivity", "Notification service disabled for $pkgName")
    return false
}

fun requestNotificationAccess(context: Context) {
    Log.d("MainActivity", "Requesting notification access.")
    val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
    context.startActivity(intent)
}

@Composable
fun NotificationAccessScreen(onRequestAccess: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Filled.Notifications,
            contentDescription = "Notification Icon",
            modifier = Modifier
                .size(64.dp)
                .padding(bottom = 16.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            text = stringResource(id = R.string.notification_access_required_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        Text(
            text = stringResource(id = R.string.notification_access_required_message),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = 24.dp),
            textAlign = TextAlign.Center
        )
        Button(onClick = onRequestAccess) {
            Text(stringResource(id = R.string.open_settings_button))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
@Composable
fun NotificationScreenWithTabs(viewModel: NotificationViewModel) {
    val notifications by viewModel.notifications.observeAsState(emptyList())
    val groupedItems by viewModel.groupedNotifications.observeAsState(emptyList())
    val ignoredApps by viewModel.ignoredApps.observeAsState(emptyList())
    val filterRules by viewModel.filterRules.observeAsState(emptyList())

    val appNamesForFilter by viewModel.uniqueAppNamesForFilter.observeAsState(emptyList())
    var selectedAppNameForFilter by remember { mutableStateOf<String?>(null) }
    var filterDropdownExpanded by remember { mutableStateOf(false) }

    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }

    val isSelectionModeActive by viewModel.isSelectionModeActive.collectAsState()
    val selectedNotificationIds = viewModel.selectedNotificationIds

    var showDeleteSelectedDialog by remember { mutableStateOf(false) }
    var showDeleteAllDialog by remember { mutableStateOf(false) }

    var showManageRulesScreen by remember { mutableStateOf(false) }
    var notificationToCreateRuleFor by remember { mutableStateOf<CapturedNotification?>(null) }
    var ruleToEdit by remember { mutableStateOf<FilterRule?>(null) }


    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    val tabTitles = listOf("All", "Dismissed")
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val pagerState = rememberPagerState { tabTitles.size }
    val coroutineScope = rememberCoroutineScope()

    val selectedNotification = remember(selectedNotificationIds.size, notifications) {
        if (selectedNotificationIds.size == 1) {
            val selectedId = selectedNotificationIds.first()
            notifications.find { it.id == selectedId }
        } else {
            null
        }
    }

    BackHandler(enabled = isSelectionModeActive) {
        viewModel.toggleSelectionMode()
    }

    LaunchedEffect(Unit) { viewModel.cleanupOldNotifications() }
    LaunchedEffect(searchQuery) {
        // Wait for 300ms of inactivity before triggering the search.
        // If the user types again, this coroutine is cancelled and restarted.
        delay(300L)
        viewModel.setSearchQuery(searchQuery.ifBlank { null })
    }
    LaunchedEffect(isSearchActive) {
        if (isSearchActive) {
            try {
                focusRequester.requestFocus()
                keyboardController?.show()
            } catch (e: Exception) {
                println("Focus request failed: ${e.message}")
            }
        } else {
            keyboardController?.hide()
            focusManager.clearFocus()
        }
    }
    LaunchedEffect(pagerState.currentPage, pagerState.isScrollInProgress) {
        if (!pagerState.isScrollInProgress) {
            selectedTabIndex = pagerState.currentPage
            viewModel.setSelectedTab(pagerState.currentPage)
        }
    }

    if (showDeleteAllDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAllDialog = false },
            title = { Text(stringResource(id = R.string.confirm_delete_all_title)) },
            text = { Text(stringResource(id = R.string.confirm_delete_all_message)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteAllNotifications()
                    showDeleteAllDialog = false
                }) { Text(stringResource(id = R.string.delete_all_button_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllDialog = false }) { Text(stringResource(id = R.string.cancel_button)) }
            }
        )
    }
    if (showDeleteSelectedDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteSelectedDialog = false },
            title = { Text(stringResource(id = R.string.confirm_delete_selected_title)) },
            text = { Text(stringResource(R.string.confirm_delete_selected_message, selectedNotificationIds.size)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteSelectedNotifications()
                    showDeleteSelectedDialog = false
                }) { Text(stringResource(id = R.string.delete_button_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteSelectedDialog = false }) { Text(stringResource(id = R.string.cancel_button)) }
            }
        )
    }
    if (notificationToCreateRuleFor != null || ruleToEdit != null) {
        CreateOrEditRuleDialog(
            notificationForNewRule = notificationToCreateRuleFor,
            existingRule = ruleToEdit,
            onDismiss = {
                notificationToCreateRuleFor = null
                ruleToEdit = null
            },
            onSaveRule = {
                viewModel.saveFilterRule(it)
                notificationToCreateRuleFor = null
                ruleToEdit = null
            }
        )
    }

    if (showManageRulesScreen) {
        ManageIgnoreRulesScreen(
            ignoredApps = ignoredApps,
            filterRules = filterRules,
            onClose = { showManageRulesScreen = false },
            onUnignoreApp = { viewModel.unignoreApp(it.packageName) },
            onDeleteRule = { viewModel.deleteFilterRule(it) },
            onEditRule = { ruleToEdit = it }
        )
    } else {
        Scaffold(
            topBar = {
                if (isSelectionModeActive) {
                    SelectionModeTopAppBar(
                        selectedItemCount = selectedNotificationIds.size,
                        selectedNotification = selectedNotification,
                        onCloseSelectionMode = { viewModel.toggleSelectionMode() },
                        onDeleteSelected = { showDeleteSelectedDialog = true },
                        onSelectAll = {
                            val allVisibleIds = notifications.map { it.id }
                            viewModel.selectAllVisible(allVisibleIds)
                        },
                        onIgnoreApp = { notification ->
                            viewModel.ignoreApp(notification.packageName)
                            viewModel.toggleSelectionMode()
                        },
                        onCreateRule = { notification ->
                            notificationToCreateRuleFor = notification
                            viewModel.toggleSelectionMode()
                        }
                    )
                } else {
                    DefaultTopAppBar(
                        viewModel = viewModel,
                        isSearchActive = isSearchActive,
                        searchQuery = searchQuery,
                        onSearchQueryChange = { searchQuery = it },
                        onSearchActiveChange = { isSearchActive = it },
                        appNamesForFilter = appNamesForFilter,
                        selectedAppNameForFilter = selectedAppNameForFilter,
                        onSelectedAppNameForFilterChange = { selectedAppNameForFilter = it },
                        filterDropdownExpanded = filterDropdownExpanded,
                        onFilterDropdownExpandedChange = { filterDropdownExpanded = it },
                        onShowDeleteAllDialog = { showDeleteAllDialog = true },
                        onShowManageRules = { showManageRulesScreen = true },
                        focusRequester = focusRequester
                    )
                }
            }
        ) { paddingValues ->
            Column(modifier = Modifier.padding(paddingValues)) {
                TabRow(selectedTabIndex = selectedTabIndex) {
                    tabTitles.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTabIndex == index,
                            onClick = {
                                selectedTabIndex = index
                                viewModel.setSelectedTab(index)
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(index)
                                }
                            },
                            text = { Text(text = title) }
                        )
                    }
                }
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) {
                    NotificationListContent(
                        groupedItems = groupedItems,
                        notifications = notifications,
                        viewModel = viewModel,
                        searchQuery = searchQuery,
                        isSelectionModeActive = isSelectionModeActive,
                        selectedNotificationIds = selectedNotificationIds
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun NotificationListContent(
    groupedItems: List<NotificationListItem>,
    notifications: List<CapturedNotification>,
    viewModel: NotificationViewModel,
    searchQuery: String?,
    isSelectionModeActive: Boolean,
    selectedNotificationIds: List<Long>
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    if (groupedItems.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
            Text(stringResource(id = R.string.no_notifications_message))
        }
    } else {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            LazyColumn(modifier = Modifier.fillMaxSize(), state = listState) {
                groupedItems.forEach { listItem ->
                    when (listItem) {
                        is NotificationListItem.HeaderItem -> {
                            stickyHeader(key = listItem.date) { DateHeader(text = listItem.date) }
                        }
                        is NotificationListItem.SubHeaderItem -> {
                            item(key = listItem.id) { TimeOfDayHeader(text = listItem.timeOfDay) }
                        }
                        is NotificationListItem.NotificationItem -> {
                            val notification = listItem.notification
                            item(key = notification.id) {
                                val isSelected = selectedNotificationIds.contains(notification.id)
                                NotificationItem(
                                    notification = notification,
                                    context = context,
                                    searchQuery = if (searchQuery?.isNotBlank() == true) searchQuery else null,
                                    isSelected = isSelected,
                                    isSelectionModeActive = isSelectionModeActive,
                                    onItemClick = {
                                        if (isSelectionModeActive) {
                                            viewModel.toggleNotificationSelection(notification.id)
                                        }
                                    },
                                    onItemLongClick = {
                                        viewModel.activateSelectionMode(notification.id)
                                    }
                                )
                                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }
                    }
                }
            }
            FastScroller(
                listState = listState,
                notifications = notifications,
                modifier = Modifier.align(Alignment.TopEnd).fillMaxHeight()
            ) { targetIndex ->
                coroutineScope.launch { listState.scrollToItem(targetIndex) }
            }
        }
    }
}

@Composable
fun DateHeader(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.95f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

@Composable
fun TimeOfDayHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 8.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectionModeTopAppBar(
    selectedItemCount: Int,
    selectedNotification: CapturedNotification?,
    onCloseSelectionMode: () -> Unit,
    onDeleteSelected: () -> Unit,
    onSelectAll: () -> Unit,
    onIgnoreApp: (CapturedNotification) -> Unit,
    onCreateRule: (CapturedNotification) -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    TopAppBar(
        title = { Text(stringResource(R.string.selected_items_count, selectedItemCount)) },
        navigationIcon = {
            IconButton(onClick = onCloseSelectionMode) {
                Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.close_selection_mode_description))
            }
        },
        actions = {
            IconButton(onClick = onSelectAll) {
                Icon(Icons.Filled.SelectAll, contentDescription = stringResource(R.string.select_all_description))
            }
            IconButton(onClick = onDeleteSelected, enabled = selectedItemCount > 0) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.delete_selected_description))
            }

            // New "more options" menu for ignore actions
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = stringResource(id = R.string.more_actions_description))
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(id = R.string.ignore_app_menu_item, selectedNotification?.appName ?: "")) },
                        onClick = {
                            selectedNotification?.let(onIgnoreApp)
                            showMenu = false
                        },
                        enabled = selectedItemCount == 1 && selectedNotification != null,
                        leadingIcon = { Icon(Icons.Default.Block, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(id = R.string.create_filter_rule_menu_item)) },
                        onClick = {
                            selectedNotification?.let(onCreateRule)
                            showMenu = false
                        },
                        enabled = selectedItemCount == 1 && selectedNotification != null,
                        leadingIcon = { Icon(Icons.Default.FilterAlt, contentDescription = null) }
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateOrEditRuleDialog(
    notificationForNewRule: CapturedNotification?,
    existingRule: FilterRule?,
    onDismiss: () -> Unit,
    onSaveRule: (FilterRule) -> Unit
) {
    val isEditMode = existingRule != null
    val initialTitle = existingRule?.titleKeyword ?: notificationForNewRule?.title ?: ""
    val initialContent = existingRule?.contentKeyword ?: notificationForNewRule?.textContent ?: ""
    val appName = existingRule?.appName ?: notificationForNewRule?.appName ?: ""

    var titleKeyword by remember { mutableStateOf(initialTitle) }
    var contentKeyword by remember { mutableStateOf(initialContent) }

    val formattedAppName = HtmlCompat.fromHtml(
        stringResource(id = R.string.rule_based_on_notification_from, appName),
        HtmlCompat.FROM_HTML_MODE_COMPACT
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(id = if (isEditMode) R.string.edit_rule_dialog_title else R.string.create_rule_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!isEditMode) {
                    Text(text = AnnotatedString(formattedAppName.toString()))
                }
                OutlinedTextField(
                    value = titleKeyword,
                    onValueChange = { titleKeyword = it },
                    label = { Text(stringResource(id = R.string.title_contains_label)) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = contentKeyword,
                    onValueChange = { contentKeyword = it },
                    label = { Text(stringResource(id = R.string.content_contains_label)) },
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Ignoring is case-insensitive. Leaving a field blank means it won't be used for matching.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val ruleToSave = FilterRule(
                        id = existingRule?.id ?: 0,
                        appName = existingRule?.appName ?: notificationForNewRule?.appName,
                        packageName = existingRule?.packageName ?: notificationForNewRule?.packageName,
                        titleKeyword = titleKeyword.takeIf { it.isNotBlank() },
                        contentKeyword = contentKeyword.takeIf { it.isNotBlank() }
                    )
                    onSaveRule(ruleToSave)
                },
                enabled = titleKeyword.isNotBlank() || contentKeyword.isNotBlank()
            ) {
                Text(stringResource(id = R.string.save_rule_button))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(id = R.string.cancel_button))
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageIgnoreRulesScreen(
    ignoredApps: List<IgnoredApp>,
    filterRules: List<FilterRule>,
    onClose: () -> Unit,
    onUnignoreApp: (IgnoredApp) -> Unit,
    onDeleteRule: (FilterRule) -> Unit,
    onEditRule: (FilterRule) -> Unit
) {
    BackHandler { onClose() }

    val context = LocalContext.current
    val appInfoCache = remember { mutableStateMapOf<String, Pair<String, Drawable?>>() }
    LaunchedEffect(ignoredApps) {
        val pm = context.packageManager
        ignoredApps.forEach { app ->
            if (!appInfoCache.containsKey(app.packageName)) {
                try {
                    val appInfo = pm.getApplicationInfo(app.packageName, 0)
                    val appName = pm.getApplicationLabel(appInfo).toString()
                    val appIcon = pm.getApplicationIcon(app.packageName)
                    appInfoCache[app.packageName] = Pair(appName, appIcon)
                } catch (e: PackageManager.NameNotFoundException) {
                    appInfoCache[app.packageName] = Pair(app.packageName, null)
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = R.string.manage_rules_screen_title)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(id = R.string.close_screen_description))
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(paddingValues),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            item {
                Text(
                    text = stringResource(id = R.string.ignored_apps_header),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            if (ignoredApps.isEmpty()) {
                item {
                    Text(
                        text = stringResource(id = R.string.no_ignored_apps_message),
                        modifier = Modifier.padding(horizontal = 16.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                items(ignoredApps, key = { it.packageName }) { app ->
                    val (appName, appIcon) = appInfoCache[app.packageName] ?: Pair(app.packageName, null)
                    ListItem(
                        headlineContent = { Text(appName) },
                        leadingContent = {
                            appIcon?.let { Image(bitmap = it.toBitmap().asImageBitmap(), contentDescription = null, modifier = Modifier.size(40.dp)) }
                        },
                        trailingContent = {
                            IconButton(onClick = { onUnignoreApp(app) }) {
                                Icon(Icons.Default.Delete, contentDescription = stringResource(id = R.string.unignore_app_description))
                            }
                        }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = stringResource(id = R.string.custom_rules_header),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            if (filterRules.isEmpty()) {
                item {
                    Text(
                        text = stringResource(id = R.string.no_custom_rules_message),
                        modifier = Modifier.padding(horizontal = 16.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                items(filterRules, key = { it.id }) { rule ->
                    val description = buildAnnotatedString {
                        val appName = rule.appName ?: stringResource(id = R.string.any_app_option)
                        append(stringResource(id = R.string.rule_details_specific_app, appName))
                        if (!rule.titleKeyword.isNullOrBlank() && !rule.contentKeyword.isNullOrBlank()) {
                            append(" "); append(HtmlCompat.fromHtml(stringResource(id = R.string.rule_details_title_contains, rule.titleKeyword), 0))
                            append(HtmlCompat.fromHtml(stringResource(id = R.string.rule_details_and), 0))
                            append(HtmlCompat.fromHtml(stringResource(id = R.string.rule_details_content_contains, rule.contentKeyword), 0))
                        } else if (!rule.titleKeyword.isNullOrBlank()) {
                            append(" "); append(HtmlCompat.fromHtml(stringResource(id = R.string.rule_details_title_contains, rule.titleKeyword),0))
                        } else if (!rule.contentKeyword.isNullOrBlank()) {
                            append(" "); append(HtmlCompat.fromHtml(stringResource(id = R.string.rule_details_content_contains, rule.contentKeyword), 0))
                        }
                    }
                    ListItem(
                        headlineContent = { Text(description) },
                        trailingContent = {
                            Row {
                                IconButton(onClick = { onEditRule(rule) }) {
                                    Icon(Icons.Default.Edit, contentDescription = stringResource(id = R.string.edit_rule_description))
                                }
                                IconButton(onClick = { onDeleteRule(rule) }) {
                                    Icon(Icons.Default.Delete, contentDescription = stringResource(id = R.string.delete_rule_description))
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DefaultTopAppBar(
    viewModel: NotificationViewModel,
    isSearchActive: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSearchActiveChange: (Boolean) -> Unit,
    appNamesForFilter: List<String>,
    selectedAppNameForFilter: String?,
    onSelectedAppNameForFilterChange: (String?) -> Unit,
    filterDropdownExpanded: Boolean,
    onFilterDropdownExpandedChange: (Boolean) -> Unit,
    onShowDeleteAllDialog: () -> Unit,
    onShowManageRules: () -> Unit,
    focusRequester: FocusRequester
) {
    var showOverflowMenu by remember { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    TopAppBar(
        title = {
            if (!isSearchActive) {
                Text(stringResource(id = R.string.notifications_screen_title))
            }
        },
        actions = {
            if (isSearchActive) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester)
                        .padding(end = 8.dp),
                    placeholder = { Text("Search...") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        keyboardController?.hide()
                        focusManager.clearFocus()
                    }),
                    trailingIcon = {
                        IconButton(onClick = {
                            if (searchQuery.isNotEmpty()) {
                                onSearchQueryChange("")
                            } else {
                                onSearchActiveChange(false)
                            }
                        }) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = if (searchQuery.isNotEmpty()) stringResource(id = R.string.clear_search_description) else stringResource(
                                    id = R.string.close_search_description
                                )
                            )
                        }
                    },
                    colors = TextFieldDefaults.colors()
                )
            }

            if (!isSearchActive) {
                IconButton(onClick = { onSearchActiveChange(true) }) {
                    Icon(
                        Icons.Filled.Search,
                        contentDescription = stringResource(id = R.string.open_search_description)
                    )
                }
            }

            Box {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.small)
                        .clickable { onFilterDropdownExpandedChange(true) }
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                ) {
                    if (!isSearchActive) {
                        Text(
                            text = selectedAppNameForFilter?.take(10)
                                ?: stringResource(id = R.string.all_apps_filter_short),
                            fontSize = 12.sp,
                            maxLines = 1,
                            modifier = Modifier.padding(end = 2.dp)
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = stringResource(id = R.string.filter_by_app_description)
                    )
                }
                DropdownMenu(
                    expanded = filterDropdownExpanded,
                    onDismissRequest = { onFilterDropdownExpandedChange(false) }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(id = R.string.all_apps_filter)) },
                        onClick = {
                            onSelectedAppNameForFilterChange(null)
                            viewModel.setAppFilter(null)
                            onFilterDropdownExpandedChange(false)
                        }
                    )
                    appNamesForFilter.forEach { appName ->
                        DropdownMenuItem(
                            text = { Text(appName) },
                            onClick = {
                                onSelectedAppNameForFilterChange(appName)
                                viewModel.setAppFilter(appName)
                                onFilterDropdownExpandedChange(false)
                            }
                        )
                    }
                }
            }

            if (!isSearchActive) {
                Box {
                    IconButton(onClick = { showOverflowMenu = true }) {
                        Icon(
                            Icons.Filled.MoreVert,
                            contentDescription = stringResource(id = R.string.more_options_description)
                        )
                    }
                    DropdownMenu(
                        expanded = showOverflowMenu,
                        onDismissRequest = { showOverflowMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(id = R.string.manage_ignore_rules_menu_item)) },
                            onClick = {
                                showOverflowMenu = false
                                onShowManageRules()
                            },
                            leadingIcon = {
                                Icon(Icons.Default.FilterList, contentDescription = null)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(id = R.string.delete_all_notifications_menu_item)) },
                            onClick = {
                                showOverflowMenu = false
                                onShowDeleteAllDialog()
                            },
                            leadingIcon = {
                                Icon(Icons.Filled.DeleteSweep, contentDescription = null)
                            }
                        )
                    }
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primary,
            titleContentColor = MaterialTheme.colorScheme.onPrimary,
            actionIconContentColor = MaterialTheme.colorScheme.onPrimary
        )
    )
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectionModeTopAppBar(
    selectedItemCount: Int,
    onCloseSelectionMode: () -> Unit,
    onDeleteSelected: () -> Unit,
    onSelectAll: () -> Unit
) {
    TopAppBar(
        title = { Text(stringResource(R.string.selected_items_count, selectedItemCount)) },
        navigationIcon = {
            IconButton(onClick = onCloseSelectionMode) {
                Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.close_selection_mode_description))
            }
        },
        actions = {
            IconButton(onClick = onSelectAll) {
                Icon(Icons.Filled.SelectAll, contentDescription = stringResource(R.string.select_all_description))
            }
            IconButton(onClick = onDeleteSelected, enabled = selectedItemCount > 0) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.delete_selected_description))
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NotificationItem(
    notification: CapturedNotification,
    context: Context,
    searchQuery: String?,
    isSelected: Boolean,
    isSelectionModeActive: Boolean,
    onItemClick: () -> Unit,
    onItemLongClick: () -> Unit
) {
    var appIcon by remember { mutableStateOf<Drawable?>(null) }

    LaunchedEffect(notification.packageName) {
        withContext(Dispatchers.IO) {
            try {
                appIcon = context.packageManager.getApplicationIcon(notification.packageName)
            } catch (e: PackageManager.NameNotFoundException) {
                Log.e("NotificationItem", "Icon not found for ${notification.packageName}", e)
                appIcon = null
            }
        }
    }

    fun highlightText(text: String?, query: String?): AnnotatedString {
        if (text.isNullOrBlank()) return AnnotatedString("")
        if (query.isNullOrBlank()) return AnnotatedString(text)

        val annotatedString = buildAnnotatedString {
            var startIndex = 0
            val lowerText = text.lowercase()
            val lowerQuery = query.lowercase()

            while (startIndex < text.length) {
                val foundIndex = lowerText.indexOf(lowerQuery, startIndex)
                if (foundIndex == -1) {
                    append(text.substring(startIndex))
                    break
                }
                append(text.substring(startIndex, foundIndex))
                withStyle(style = SpanStyle(fontWeight = FontWeight.Bold, background = Color.Yellow.copy(alpha = 0.5f))) {
                    append(text.substring(foundIndex, foundIndex + query.length))
                }
                startIndex = foundIndex + query.length
            }
        }
        return annotatedString
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onItemClick,
                onLongClick = onItemLongClick
            )
            .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent)
            .padding(vertical = 8.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.Top
    ) {
        if (isSelectionModeActive) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onItemClick() },
                modifier = Modifier.padding(end = 12.dp).align(Alignment.CenterVertically),
                colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
            )
        } else {
            Box(modifier = Modifier.size(40.dp).padding(end = 8.dp), contentAlignment = Alignment.Center) {
                appIcon?.let {
                    Image(
                        bitmap = it.toBitmap().asImageBitmap(),
                        contentDescription = "${notification.appName} icon",
                        modifier = Modifier.size(36.dp)
                    )
                } ?: Box(modifier = Modifier.size(36.dp).align(Alignment.Center))
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = notification.appName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = notification.postTimeString.substringAfter(" "),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(2.dp))

            notification.title?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = highlightText(it, searchQuery),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }
            notification.textContent?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = highlightText(it, searchQuery),
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (notification.title.isNullOrBlank() && notification.textContent.isNullOrBlank()){
                Text(
                    text = "(No content)",
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun FastScroller(
    listState: LazyListState,
    notifications: List<CapturedNotification>,
    modifier: Modifier = Modifier,
    onScroll: (targetIndex: Int) -> Unit
) {
    val isVisible = listState.canScrollForward || listState.canScrollBackward
    if (!isVisible || listState.layoutInfo.totalItemsCount == 0) return

    BoxWithConstraints(modifier = modifier) {
        var isDragging by remember { mutableStateOf(false) }
        var dragOffset by remember { mutableStateOf(0f) }
        val density = LocalDensity.current
        val firstVisibleItem by remember { derivedStateOf { listState.firstVisibleItemIndex } }

        val maxHeightPx = with(density) { maxHeight.toPx() }
        val thumbHeightDp = 64.dp
        val thumbHeightPx = with(density) { thumbHeightDp.toPx() }
        val scrollableTrackHeightPx = maxHeightPx - thumbHeightPx

        fun getThumbOffsetY(): Float {
            val layoutInfo = listState.layoutInfo
            val totalItemsCount = layoutInfo.totalItemsCount
            val visibleItems = layoutInfo.visibleItemsInfo
            if (visibleItems.isEmpty()) return 0f

            // Estimate the total content height based on the average height of visible items
            val averageItemHeight = visibleItems.sumOf { it.size } / visibleItems.size.toFloat()
            val estimatedTotalContentHeight = totalItemsCount * averageItemHeight

            // Estimate the current scroll offset in pixels
            val currentPixelOffset = listState.firstVisibleItemIndex * averageItemHeight + listState.firstVisibleItemScrollOffset

            // Calculate the total scrollable range in pixels
            val viewportHeight = layoutInfo.viewportSize.height
            val totalScrollablePixelRange = (estimatedTotalContentHeight - viewportHeight).coerceAtLeast(0f)
            if (totalScrollablePixelRange == 0f) return 0f

            // Calculate the scroll proportion and the final thumb offset
            val scrollProportion = currentPixelOffset / totalScrollablePixelRange
            return (scrollableTrackHeightPx * scrollProportion).coerceIn(0f, scrollableTrackHeightPx)
        }

        val thumbOffsetY by remember { derivedStateOf { getThumbOffsetY() } }

        if (isDragging && firstVisibleItem < notifications.size) {
            val dateString = formatDateForIndicator(notifications[firstVisibleItem].postTimeString)
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(
                        x = (-56).dp,
                        y = with(density) { thumbOffsetY.toDp() }
                    ),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary,
                tonalElevation = 8.dp
            ) {
                Text(
                    text = dateString,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    fontSize = 14.sp,
                    maxLines = 1
                )
            }
        }

        // The touchable area for the scroller
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .width(48.dp)
                .fillMaxHeight()
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragStart = {
                            isDragging = true
                            dragOffset = getThumbOffsetY()
                        },
                        onDragEnd = { isDragging = false },
                        onDragCancel = { isDragging = false }
                    ) { change, dragAmount ->
                        val newOffset = (dragOffset + dragAmount).coerceIn(0f, scrollableTrackHeightPx)
                        val targetProportion = newOffset / scrollableTrackHeightPx
                        val totalItems = listState.layoutInfo.totalItemsCount
                        val targetIndex = ((totalItems - 1) * targetProportion)
                            .toInt()
                            .coerceIn(0, totalItems - 1)

                        if (targetIndex >= 0) {
                            onScroll(targetIndex)
                        }
                        dragOffset = newOffset
                        change.consume()
                    }
                }
        ) {
            // The visual representation of the thumb
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 8.dp)
                    .offset(y = with(density) { thumbOffsetY.toDp() })
                    .size(width = 12.dp, height = thumbHeightDp)
                    .background(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = if (isDragging) 0.7f else 0.4f),
                        shape = CircleShape
                    )
            )
        }
    }
}

private fun formatDateForIndicator(postTimeString: String): String {
    return try {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val outputFormat = SimpleDateFormat("MMM d", Locale.getDefault())
        val date = inputFormat.parse(postTimeString)
        date?.let { outputFormat.format(it) } ?: ""
    } catch (e: Exception) {
        ""
    }
}

// --- Previews ---
@Preview(showBackground = true)
@Composable
fun NotificationAccessScreenPreview() {
    NotificationVaultTheme {
        NotificationAccessScreen {}
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
fun NotificationItemPreview() {
    NotificationVaultTheme {
        NotificationItem(
            CapturedNotification(
                id = 1,
                appName = "Important News App",
                packageName = "com.sample.app",
                title = "Breaking News: Something Happened!",
                textContent = "This is the detailed content of the sample notification. It can be a bit longer to see how it wraps and if the layout holds up.",
                postTimeMillis = System.currentTimeMillis(),
                postTimeString = "2023-10-27 10:00:00"
            ),
            LocalContext.current,
            null,
            isSelected = false,
            isSelectionModeActive = false,
            onItemClick = {},
            onItemLongClick = {}
        )
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
fun NotificationItemPreviewSelectionModeActive() {
    NotificationVaultTheme {
        NotificationItem(
            CapturedNotification(
                id = 1,
                appName = "Important News App",
                packageName = "com.sample.app",
                title = "Breaking News: Something Happened!",
                textContent = "This is the detailed content of the sample notification. It can be a bit longer to see how it wraps and if the layout holds up.",
                postTimeMillis = System.currentTimeMillis(),
                postTimeString = "2023-10-27 10:00:00"
            ),
            LocalContext.current,
            null,
            isSelected = false,
            isSelectionModeActive = true,
            onItemClick = {},
            onItemLongClick = {}
        )
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
fun NotificationItemPreviewSelected() {
    NotificationVaultTheme {
        NotificationItem(
            CapturedNotification(
                id = 1,
                appName = "Important News App",
                packageName = "com.sample.app",
                title = "Breaking News: Something Happened!",
                textContent = "This is the detailed content of the sample notification. It can be a bit longer to see how it wraps and if the layout holds up.",
                postTimeMillis = System.currentTimeMillis(),
                postTimeString = "2023-10-27 10:00:00"
            ),
            LocalContext.current,
            null,
            isSelected = true,
            isSelectionModeActive = true,
            onItemClick = {},
            onItemLongClick = {}
        )
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
fun NotificationItemNoContentPreview() {
    NotificationVaultTheme {
        NotificationItem(
            CapturedNotification(
                id = 2,
                appName = "Silent App",
                packageName = "com.silent.app",
                title = null,
                textContent = null,
                postTimeMillis = System.currentTimeMillis(),
                postTimeString = "2023-10-27 10:05:00"
            ),
            LocalContext.current,
            null,
            isSelected = false,
            isSelectionModeActive = false,
            onItemClick = {},
            onItemLongClick = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun FullNotificationListScreenPreview() {
    // This preview is more complex as it needs a ViewModel.
    // For a simple preview, you can pass fake data directly.
    // Or mock the ViewModel.
    NotificationVaultTheme {
        // In a real preview for a screen with a ViewModel, you'd typically
        // create a fake ViewModel or pass static preview data.
        // For simplicity, here's a placeholder.
        Text("Notification List Screen Preview (ViewModel dependent)")
    }
}