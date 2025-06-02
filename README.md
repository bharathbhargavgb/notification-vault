# Notification Vault

Notification Vault is an Android application designed to capture and store all incoming notifications, allowing users to review them later. It provides a clean interface to browse, search, and manage logged notifications.

## Features

*   **Capture All Notifications:** Silently logs all incoming status bar notifications from all apps.
*   **Persistent Storage:** Notifications are saved locally using Room database.
*   **Notification Listing:** Displays captured notifications in a chronological, scrollable list.
*   **App Icon Display:** Shows the icon of the app that generated the notification.
*   **Search Functionality:** Allows users to search through logged notifications by app name, title, or content.
*   **Filter by App:** Users can filter the notification list to show notifications from a specific application.
*   **Duplicate Prevention:** Avoids storing identical notifications that arrive in quick succession.
*   **Selection Mode:**
    *   Long-press to enter selection mode.
    *   Select multiple notifications.
    *   Delete selected notifications.
    *   Select all visible notifications.
*   **Delete All Notifications:** Option to clear all stored notifications.
*   **Automatic Cleanup:** Older notifications are automatically deleted after 7 days.
*   **Notification Access Permission Handling:** Guides the user to grant necessary notification listener permission.

## Tech Stack & Architecture

*   **Language:** Kotlin
*   **UI:** Jetpack Compose (Android's modern toolkit for building native UI)
*   **Architecture:** MVVM (Model-View-ViewModel)
    *   **ViewModel:** `NotificationViewModel` - Manages UI-related data and business logic.
    *   **Repository:** `NotificationRepository` - Mediates between data sources (database) and the ViewModel.
    *   **Database:** Room Persistence Library (SQLite object mapping library).
        *   Entity: `CapturedNotification`
        *   DAO: `NotificationDao`
*   **Asynchronous Programming:** Kotlin Coroutines & Flow for managing background tasks and reactive data streams.
*   **Service:** `NotificationListenerService` - To capture incoming notifications from the Android system.
*   **Dependency Injection (Manual):** ViewModels are instantiated using a `NotificationViewModelFactory`.

## Setup & Build

1.  **Clone the repository:**
2.  **Open in Android Studio:**
    *   Open Android Studio.
    *   Click on "Open" or "Open an Existing Project".
    *   Navigate to the cloned repository directory and select it.
3.  **Build the project:**
    *   Android Studio should automatically sync Gradle files. If not, click "Sync Project with Gradle Files".
    *   Click "Build" > "Make Project" or run the app on an emulator or physical device.

## How It Works

1.  **Permission:** The app first checks if it has "Notification Access" permission. If not, it prompts the user to grant it via system settings. This permission is essential for the `NotificationListenerService` to operate.
2.  **NotificationListenerService:** Once permission is granted, `MyNotificationListenerService` (or your named service) starts listening for new notifications posted to the system.
3.  **Data Extraction & Storage:**
    *   When a notification is posted (`onNotificationPosted`), the service extracts relevant details: app name, package name, title, text content, and timestamp.
    *   It includes logic to prevent storing exact duplicates that arrive very close to each other.
    *   The extracted data is encapsulated in a `CapturedNotification` entity.
    *   This entity is then inserted into the Room database via the `NotificationRepository` and `NotificationDao`.
4.  **Display & Interaction:**
    *   `MainActivity` hosts the Jetpack Compose UI.
    *   `NotificationViewModel` observes the database (via `NotificationRepository`) for changes using Kotlin Flow.
    *   The UI (`NotificationListScreen`) collects these flows and displays the notifications in a `LazyColumn`.
    *   Users can interact with the list to search, filter, select, and delete notifications.

## Future Enhancements

*   [ ] Per-app notification blocking/allowing within the app.
*   [ ] More advanced filtering options (e.g., by date range).
*   [ ] Export/Import notifications.
*   [ ] Detailed notification view screen.
*   [ ] Theming and customization options.

## Contributing

Pull requests are welcome. For major changes, please open an issue first to discuss what you would like to change.

## License


`This project is licensed under the MIT License - see the LICENSE.md file for details.`
