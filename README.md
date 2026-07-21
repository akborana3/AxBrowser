# AxBrowser

A fast, secure, privacy-first Android browser with built-in smart media detection and local download engine.

## Features

- **Lightning Fast** — Hardware-accelerated WebView, smart cache, low RAM usage
- **Privacy First** — Ad/tracker blocker, incognito mode, HTTPS upgrade
- **Smart Media Detection** — Auto-detect downloadable media with floating download button
- **Built-in Download Engine** — Pause/resume, background downloads, progress tracking
- **Material 3 Design** — Glassmorphism UI, premium feel
- **Tab Management** — Multiple tabs, incognito mode, tab switcher
- **Bookmarks & History** — Save and organize your favorite sites
- **Built-in Video Player** — Play downloaded videos with ExoPlayer
- **File Manager** — Browse and manage downloaded files

## Tech Stack

- **Language:** Kotlin
- **UI:** Jetpack Compose + Material 3
- **Architecture:** Clean Architecture + MVVM
- **DI:** Hilt
- **Database:** Room
- **Networking:** OkHttp
- **Async:** Kotlin Coroutines + Flow
- **Video:** Media3/ExoPlayer
- **Background:** WorkManager

## Project Structure

```
axbrowser/
├── app/                    # App entry point
├── core/
│   ├── core-ui/           # Theme, components, utilities
│   ├── core-domain/       # Models, repositories, use cases
│   ├── core-data/         # Database, network, datastore
│   └── core-testing/      # Test helpers and fakes
├── feature/
│   ├── feature-browser/   # WebView, tabs, navigation
│   ├── feature-downloads/ # Download engine, manager UI
│   ├── feature-bookmarks/ # Bookmark manager
│   ├── feature-history/   # History store
│   ├── feature-settings/  # App settings
│   ├── feature-filemanager/ # File browser
│   └── feature-videoplayer/ # Video player
└── build-logic/           # Convention plugins
```

## Getting Started

1. Clone the repository
2. Open in Android Studio
3. Sync Gradle
4. Run on device or emulator (minSdk 26)

## Building

```bash
# Debug build
./gradlew assembleDebug

# Release build
./gradlew assembleRelease
```

## License

MIT License
