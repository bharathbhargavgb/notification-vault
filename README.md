# Notification Vault

Notification Vault is an Android application designed to capture and store all incoming notifications, allowing users to review them later. It provides a clean, modern interface to browse, search, and manage logged notifications with powerful grouping and filtering features.

## Features

* **Capture All Notifications:** Silently logs all incoming status bar notifications from all apps.
* **Grouped & Sticky List:**
    * Displays notifications grouped by date with "sticky" headers that float at the top as you scroll.
    * Within each day, notifications are sub-grouped by time of day (e.g., Morning, Afternoon, Evening).
    * Includes a fast scroller thumb with a date indicator bubble for quick navigation through the timeline.
* **Advanced Ignore Rules:**
    * **Ignore Entire Apps:** Easily ignore all future notifications from any application directly from a notification.
    * **Create Custom Rules:** Create powerful, fine-grained rules to automatically ignore notifications based on keywords in their title or content.
    * **Rule Management:** A dedicated screen to view, edit, and delete all your ignored apps and custom rules.
* **Smart Dismissal Tracking:**
    * Features separate tabs for "All" and "Dismissed" notifications.
    * Intelligently identifies and lists only notifications that were explicitly swiped away by the user, ignoring those cancelled by apps.
* **Search & Filter:**
    * Allows users to search through logged notifications by app name, title, or content.
    * Users can filter the list to show notifications from a single application.
* **Persistent & Local:**
    * All notifications are saved securely on your device using the Room database.
    * Older notifications are automatically deleted after 7 days to manage storage.
* **Selection Mode:**
    * Long-press any notification to enter selection mode.
    * Select multiple notifications to delete them in a batch.
    * When a single item is selected, you can quickly ignore the app or create a new filter rule based on that notification.
* **Duplicate Prevention:** Avoids storing identical notifications that arrive in quick succession.
* **Permission Guidance:** Guides the user to grant the necessary notification listener permission upon first launch.


## Tech Stack & Architecture

* **Language:** Kotlin
* **UI:** Jetpack Compose (Android's modern toolkit for building native UI)
* **Architecture:** MVVM (Model-View-ViewModel)
    * **ViewModel:** `NotificationViewModel` - Manages UI-related data and business logic.
    * **Repository:** `NotificationRepository` - Mediates between data sources (database) and the ViewModel.
    * **Database:** Room Persistence Library (SQLite object mapping library).
        * **Entities:** `CapturedNotification`, `IgnoredApp`, `FilterRule`
        * **DAOs:** `NotificationDao`, `IgnoredAppDao`, `FilterRuleDao`
* **Asynchronous Programming:** Kotlin Coroutines & Flow for managing background tasks and reactive data streams.
* **Service:** `NotificationListenerService` - To capture incoming notifications from the Android system.
* **Dependency Injection (Manual):** ViewModels are instantiated using a `NotificationViewModelFactory`.

## Setup & Build

1.  **Clone the repository:**
2.  **Open in Android Studio:**
    * Open Android Studio.
    * Click on "Open" or "Open an Existing Project".
    * Navigate to the cloned repository directory and select it.
3.  **Build the project:**
    * Android Studio should automatically sync Gradle files. If not, click "Sync Project with Gradle Files".
    * Click "Build" > "Make Project" or run the app on an emulator or physical device.

## How It Works

1.  **Permission:** The app first checks if it has "Notification Access" permission. If not, it prompts the user to grant it via system settings. This permission is essential for the `NotificationListenerService` to operate.
2.  **NotificationListenerService:** Once permission is granted, `NotificationListenerService` starts listening for new notifications posted to the system.
3.  **Data Extraction & Storage:**
    * When a notification is posted (`onNotificationPosted`), the service extracts relevant details: app name, package name, title, text content, and timestamp.
    * It includes logic to prevent storing exact duplicates that arrive very close to each other.
    * The extracted data is encapsulated in a `CapturedNotification` entity.
    * This entity is then inserted into the Room database via the `NotificationRepository` and `NotificationDao`.
4.  **Display & Interaction:**
    * `MainActivity` hosts the Jetpack Compose UI.
    * `NotificationViewModel` observes the database (via `NotificationRepository`) for changes. Before displaying the notifications, it applies all user-defined ignore rules (for ignored apps and custom keyword rules) to the data stream.
    * The UI (`NotificationListContent`) collects the final, filtered list and displays the notifications in a `LazyColumn`.
    * Users can interact with the list to search, filter, select, and delete notifications.

## Future Enhancements

* [ ] More advanced filtering options (e.g., by date range).
* [ ] Export/Import notifications.
* [ ] Detailed notification view screen.
* [ ] Theming and customization options.

## Contributing

Pull requests are welcome. For major changes, please open an issue first to discuss what you would like to change.

## License


`This project is licensed under the MIT License - see the LICENSE.md file for details.`