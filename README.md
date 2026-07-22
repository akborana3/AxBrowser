# AxBrowser

A fast, secure, privacy-first Android browser with built-in smart media detection and local download engine.

## Features

- **Lightning Fast** — Hardware-accelerated WebView, smart cache, low RAM usage
- **Privacy First** — Ad/tracker blocker, incognito mode, HTTPS upgrade
- **Smart Media Detection** — Auto-detect downloadable media with floating download button
- **Built-in Download Engine** — yt-dlp for video sites + OkHttp for direct files
- **Paste Link Download** — Like 1DM/ADM, paste any URL to download
- **Dev Console** — Network Inspector + Eruda JavaScript console
- **Material 3 Design** — Glassmorphism UI, premium feel
- **Tab Management** — Multiple tabs, incognito mode, tab switcher
- **Bookmarks & History** — Save and organize your favorite sites
- **Built-in Video Player** — Play downloaded videos with ExoPlayer
- **File Manager** — Browse and manage downloaded files

## Download

[**Download Latest APK**](https://github.com/akborana3/AxBrowser/releases/latest)

## Screenshots

<!-- Screenshots will be added after first release -->

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
- **Download Engine:** yt-dlp + OkHttp

## Project Structure

```
AxBrowser/
├── app/                    # App entry point
├── core/
│   ├── core-ui/           # Theme, components, utilities
│   ├── core-domain/       # Models, repositories, use cases
│   ├── core-data/         # Database, network, datastore
│   └── core-testing/      # Test helpers and fakes
├── feature/
│   ├── feature-browser/   # WebView, tabs, navigation, dev console
│   ├── feature-downloads/ # Download engine (yt-dlp + OkHttp), manager UI
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

## CI/CD

This project uses GitHub Actions for continuous integration:

- **CI Workflow** — Builds on every push, runs tests and lint
- **Release Workflow** — Creates GitHub Release with APK when a version tag is pushed

### Creating a Release

```bash
git tag v1.0.0
git push --tags
```

The release workflow will automatically:
1. Build the release APK
2. Sign the APK
3. Create a GitHub Release
4. Attach the APK as a downloadable artifact

## License

MIT License
