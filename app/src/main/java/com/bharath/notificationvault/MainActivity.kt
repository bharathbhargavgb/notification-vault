package com.bharath.notificationvault

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
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.bharath.notificationvault.data.db.AppDatabase
import com.bharath.notificationvault.data.db.entity.CapturedNotification
import com.bharath.notificationvault.data.repository.NotificationRepository
import com.bharath.notificationvault.ui.theme.NotificationVaultTheme
import com.bharath.notificationvault.ui.viewmodel.NotificationViewModel
import com.bharath.notificationvault.ui.viewmodel.NotificationViewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private val notificationViewModel: NotificationViewModel by viewModels {
        NotificationViewModelFactory(
            NotificationRepository(
                AppDatabase.getDatabase(applicationContext).notificationDao()
            )
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NotificationVaultTheme { // Replace with your app's theme
                MainAppScreen(notificationViewModel)
            }
        }
    }

    // Call this when the activity resumes to check if permission was granted
    // and refresh the UI accordingly.
    override fun onResume() {
        super.onResume()
        // This is a simple way to trigger a recomposition if the permission state might have changed.
        // For a more robust solution, you might use a StateFlow in the Activity/ViewModel
        // that observes the permission status.
        setContent {
            NotificationVaultTheme {
                MainAppScreen(notificationViewModel)
            }
        }
    }
}

@Composable
fun MainAppScreen(viewModel: NotificationViewModel) {
    val context = LocalContext.current
    var hasNotificationAccess by remember { mutableStateOf(isNotificationServiceEnabled(context)) }

    if (!hasNotificationAccess) {
        NotificationAccessScreen {
            requestNotificationAccess(context)
            // After attempting to request, we don't immediately know if it was granted.
            // The onResume in MainActivity will handle refreshing the state.
        }
    } else {
        NotificationListScreen(viewModel)
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
    // Add a flag to indicate that this intent is being launched from your app,
    // which can be useful if the settings app wants to highlight your listener.
    // intent.putExtra(":settings:fragment_args_key", context.packageName) // This key might vary by OEM/Android version
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
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Button(onClick = onRequestAccess) {
            Text(stringResource(id = R.string.open_settings_button))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationListScreen(viewModel: NotificationViewModel) {
    val notifications by viewModel.notifications.observeAsState(emptyList())
    val appNamesForFilter by viewModel.uniqueAppNamesForFilter.observeAsState(emptyList())
    var selectedAppNameForFilter by remember { mutableStateOf<String?>(null) }
    var filterDropdownExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(Unit) { // Use Unit to run only once on composition
        viewModel.cleanupOldNotifications()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = R.string.notifications_screen_title)) },
                actions = {
                    Box {
                        TextButton(onClick = { filterDropdownExpanded = true }) {
                            Text(selectedAppNameForFilter ?: stringResource(id = R.string.all_apps_filter))
                            Icon(Icons.Default.ArrowDropDown, contentDescription = stringResource(id = R.string.filter_by_app_description))
                        }
                        DropdownMenu(
                            expanded = filterDropdownExpanded,
                            onDismissRequest = { filterDropdownExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(id = R.string.all_apps_filter)) },
                                onClick = {
                                    selectedAppNameForFilter = null
                                    viewModel.setAppFilter(null)
                                    filterDropdownExpanded = false
                                }
                            )
                            appNamesForFilter.forEach { appName ->
                                DropdownMenuItem(
                                    text = { Text(appName) },
                                    onClick = {
                                        selectedAppNameForFilter = appName
                                        viewModel.setAppFilter(appName)
                                        filterDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        if (notifications.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(stringResource(id = R.string.no_notifications_message))
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 0.dp) // Let items handle their own padding
            ) {
                items(notifications, key = { it.id }) { notification ->
                    NotificationItem(notification = notification, context = context)
                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@Composable
fun NotificationItem(notification: CapturedNotification, context: Context) {
    var appIcon by remember { mutableStateOf<Drawable?>(null) }

    LaunchedEffect(notification.packageName) {
        withContext(Dispatchers.IO) { // Perform icon loading off the main thread
            try {
                appIcon = context.packageManager.getApplicationIcon(notification.packageName)
            } catch (e: PackageManager.NameNotFoundException) {
                Log.e("NotificationItem", "Icon not found for ${notification.packageName}", e)
                appIcon = null // Set to null if not found
            }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.Top // Align to top for multi-line text
    ) {
        // App Icon
        Box(modifier = Modifier
            .size(40.dp)
            .padding(end = 12.dp), contentAlignment = Alignment.Center) {
            appIcon?.let {
                Image(
                    bitmap = it.toBitmap().asImageBitmap(), // Convert Drawable to ImageBitmap
                    contentDescription = "${notification.appName} icon",
                    modifier = Modifier.size(36.dp)
                )
            } ?: Spacer(modifier = Modifier.size(36.dp)) // Placeholder if icon is null
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
                    text = notification.postTimeString.substringAfter(" "), // Show only time or customize
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(2.dp)) // Reduced space

            notification.title?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }
            notification.textContent?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (notification.title.isNullOrBlank() && notification.textContent.isNullOrBlank()){
                Text(
                    text = "(No content)", // Placeholder if both title and text are empty
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
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
            LocalContext.current
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
            LocalContext.current
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