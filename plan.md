# 🌐 AxBrowser — High-Performance Android Media Browser
## Complete Development Plan

---

## 📋 Table of Contents

1. [Project Overview](#1-project-overview)
2. [Architecture](#2-architecture)
3. [Module Structure](#3-module-structure)
4. [Tech Stack — What & How](#4-tech-stack--what--how)
5. [Feature Implementation Plan](#5-feature-implementation-plan)
6. [UI/UX Design Plan](#6-uiux-design-plan)
7. [Download Engine Design](#7-download-engine-design)
8. [Security Implementation](#8-security-implementation)
9. [Database Schema](#9-database-schema)
10. [CI/CD Pipeline](#10-cicd-pipeline)
11. [Testing Strategy](#11-testing-strategy)
12. [Build & Delivery Phases](#12-build--delivery-phases)
13. [Folder Structure](#13-folder-structure)
14. [Key Implementation Notes](#14-key-implementation-notes)

---

## 1. Project Overview

**App Name:** AxBrowser  
**Platform:** Android (minSdk 26 / targetSdk 34)  
**Language:** Kotlin  
**Pattern:** Clean Architecture + MVVM  
**Primary Goal:** A fast, secure, privacy-first Android browser with a built-in smart media detection and local download engine.

### Core Pillars

| Pillar | Description |
|---|---|
| **Speed** | Hardware-accelerated WebView, smart cache, low RAM usage |
| **Privacy** | Ad/tracker blocker, incognito mode, HTTPS upgrade |
| **Media** | Auto-detect downloadable media, local download engine |
| **Design** | Material 3 + Glassmorphism, premium feel |
| **Maintainability** | Modular, SOLID, documented, CI/CD |

---

## 2. Architecture

### Layer Overview

```
┌─────────────────────────────────────────────────┐
│                  Presentation Layer              │
│   Jetpack Compose UI + ViewModels (MVVM)        │
├─────────────────────────────────────────────────┤
│                   Domain Layer                   │
│   UseCases + Repository Interfaces + Models     │
├─────────────────────────────────────────────────┤
│                    Data Layer                    │
│   Room DB + DataStore + Network + File System   │
└─────────────────────────────────────────────────┘
```

### Pattern: Clean Architecture + MVVM

- **Presentation** → Compose Screens observe StateFlow from ViewModel
- **ViewModel** → calls UseCases, never touches data sources directly
- **UseCase** → single-responsibility business logic, calls Repository interface
- **Repository Interface** → defined in domain, implemented in data layer
- **Repository Implementation** → decides: Room, network, DataStore, or file

### Data Flow

```
User Action
    ↓
Compose UI (Event)
    ↓
ViewModel (processes via UseCase)
    ↓
UseCase (pure business logic)
    ↓
Repository Interface
    ↓
Repository Implementation
    ↓
Room / Network / File / DataStore
    ↑ (StateFlow / Flow emits back up the chain)
```

### Dependency Direction (Dependency Rule)

```
Presentation → Domain ← Data
```

Domain never imports from Presentation or Data. Data depends on Domain interfaces only.

---

## 3. Module Structure

The project is split into Gradle modules for compile-time isolation and build speed.

```
axbrowser/
├── app/                        ← App entry point, DI graph root, MainActivity
├── core/
│   ├── core-ui/                ← Shared Compose components, theme, typography
│   ├── core-domain/            ← Base UseCase class, common domain models
│   ├── core-data/              ← Base Repository, network client, DataStore utils
│   └── core-testing/           ← Shared test helpers, fakes, fixtures
├── feature/
│   ├── feature-browser/        ← WebView, tabs, navigation, reader mode
│   ├── feature-downloads/      ← Download engine, manager UI, notifications
│   ├── feature-bookmarks/      ← Bookmark manager, import/export
│   ├── feature-history/        ← History store + UI
│   ├── feature-settings/       ← App settings, permissions, privacy controls
│   ├── feature-filemanager/    ← Built-in file manager for downloaded files
│   └── feature-videoplayer/    ← Built-in video player (Media3/ExoPlayer)
└── build-logic/                ← Convention plugins, shared Gradle config
```

### Why Modules?

- **Parallel compilation** → faster builds
- **Feature isolation** → changes in `feature-downloads` don't recompile `feature-bookmarks`
- **Enforced architecture** → module boundaries prevent accidental layer violations
- **Easier testing** → each module testable in isolation

---

## 4. Tech Stack — What & How

### 4.1 Kotlin

**What:** Primary language.  
**How:**
- Coroutines for all async work (no callbacks, no RxJava)
- Flow for reactive streams (DB, network, download progress)
- Sealed classes for UI state (`Loading`, `Success`, `Error`)
- Data classes for domain/data models
- Extension functions for clean utility code
- `object` for singletons (via Hilt)

---

### 4.2 Jetpack Compose

**What:** Entire UI layer. No XML layouts.  
**How:**
- `@Composable` functions for every screen and component
- `State<T>` / `collectAsStateWithLifecycle()` to observe ViewModel StateFlow
- `LazyColumn` / `LazyRow` for lists (tabs, downloads, bookmarks)
- `AnimatedContent`, `AnimatedVisibility` for smooth transitions
- `NavHost` + `NavController` for in-app navigation
- Custom `BottomSheet` for download options
- Custom `FloatingActionButton` overlay for detected media
- Theming via `MaterialTheme` with `DarkColorScheme` / `LightColorScheme`
- `ComposeView` to embed Compose inside the WebView host (if needed for overlays)
- Separate `Scaffold` per tab container

---

### 4.3 MVVM Architecture

**What:** Presentation pattern.  
**How:**
- One `ViewModel` per screen/feature
- `UiState` sealed class per screen exposed as `StateFlow<UiState>`
- `UiEvent` sealed class for one-shot events (navigation, toast) via `SharedFlow`
- ViewModels scoped to NavBackStackEntry or Activity where appropriate
- `viewModelScope` for coroutines that die with ViewModel
- No `LiveData` — use `StateFlow` exclusively

```kotlin
// Pattern used everywhere:
data class BrowserUiState(
    val isLoading: Boolean = false,
    val url: String = "",
    val title: String = "",
    val progress: Int = 0,
    val error: String? = null
)

sealed class BrowserUiEvent {
    object NavigateBack : BrowserUiEvent()
    data class ShowSnackbar(val message: String) : BrowserUiEvent()
}
```

---

### 4.4 Clean Architecture

**What:** Code organization enforcing Separation of Concerns.  
**How:**

**Domain layer contains:**
- `UseCase` classes (e.g., `GetTabsUseCase`, `StartDownloadUseCase`)
- Repository interfaces (e.g., `DownloadRepository`, `BookmarkRepository`)
- Domain models (e.g., `Tab`, `Download`, `Bookmark`, `MediaItem`)

**Data layer contains:**
- Repository implementations
- Room entities + DAOs
- Network data sources
- DataStore preferences
- Mappers: Entity → Domain Model

**Rule:** Domain models are used throughout. Data entities never leave the data layer.

---

### 4.5 Hilt (Dependency Injection)

**What:** Compile-time safe DI framework by Google.  
**How:**
- `@HiltAndroidApp` on `Application` class
- `@AndroidEntryPoint` on Activities, Fragments, and Services
- `@HiltViewModel` on all ViewModels
- `@Module` + `@InstallIn` for providing:
  - `OkHttpClient` (singleton)
  - `Room` database (singleton)
  - `DataStore` (singleton)
  - Repository implementations bound to their interfaces
  - Download engine adapter (bound to interface for swappability)
- `@Binds` preferred over `@Provides` where possible (less overhead)
- Scopes: `SingletonComponent`, `ActivityRetainedComponent`, `ViewModelComponent`

```kotlin
@Module
@InstallIn(SingletonComponent::class)
abstract class DownloadModule {
    @Binds
    abstract fun bindDownloadEngine(
        impl: OkHttpDownloadEngine
    ): DownloadEngine  // ← bound to interface, easily swapped
}
```

---

### 4.6 Room Database

**What:** Local SQLite ORM for bookmarks, history, downloads, tabs.  
**How:**
- One `@Database` class with version migration strategy
- Separate `@Entity` per domain concept
- `@Dao` interfaces with `Flow<List<T>>` return types for reactive UI
- `@TypeConverter` for enums, lists
- Migrations: numbered `Migration(old, new)` objects, never `fallbackToDestructiveMigration` in production
- Accessed only from Repository implementations, never from ViewModels directly

**Tables:**
- `tabs` — open tabs state
- `bookmarks` — URL, title, favicon, timestamp, folder
- `history` — URL, title, visit count, last visited
- `downloads` — full download record (see Section 9)
- `media_detections` — cached media detections per page
- `blocked_domains` — ad/tracker blocklist

---

### 4.7 Kotlin Coroutines + Flow

**What:** Async programming and reactive data streams.  
**How:**
- `viewModelScope.launch` for ViewModel-scoped work
- `Dispatchers.IO` for all DB, file, and network calls
- `Dispatchers.Main` for UI updates
- `withContext(Dispatchers.IO)` inside UseCases for data operations
- `Flow` from Room DAOs for reactive lists
- `StateFlow` in ViewModels for UI state
- `SharedFlow` in ViewModels for one-time events (navigation, toast)
- `callbackFlow` to wrap callback-based APIs (WebView callbacks → Flow)
- `channelFlow` for download progress streams
- Structured concurrency: parent scope cancels children automatically

---

### 4.8 WorkManager

**What:** Background task execution, especially for background downloads.  
**How:**
- `CoroutineWorker` subclass per task type
- `OneTimeWorkRequest` for individual downloads
- `PeriodicWorkRequest` for cache cleanup (weekly)
- Constraints: `NetworkType.CONNECTED` for downloads
- Chained work: detect → enqueue → download → notify
- Progress reported via `setProgress()` → observed in UI via `WorkInfo`
- Notification shown during background download via `ForegroundInfo`
- Retry policy: `BackoffPolicy.LINEAR` with max attempts

---

### 4.9 OkHttp

**What:** HTTP client for media detection, downloads, Safe Browsing checks.  
**How:**
- Single `OkHttpClient` singleton via Hilt
- Custom `Interceptor` for:
  - Adding `User-Agent` header
  - HTTPS upgrade (intercepting HTTP → redirect to HTTPS)
  - Request/response logging (debug only, stripped in release)
- `Cache` enabled for static assets (`10 MB` cache)
- Custom `Dispatcher` with separate thread pool for downloads vs API calls
- `EventListener` for download speed tracking (bytes transferred / time)
- Separate `OkHttpClient` instance for downloads to avoid interfering with browser traffic

---

### 4.10 Chromium WebView (Android WebView)

**What:** Core browser rendering engine.  
**How:**
- `WebView` embedded inside a Compose `AndroidView`
- Custom `WebViewClient`:
  - `shouldInterceptRequest` → scan every request for media URLs
  - `onPageStarted` / `onPageFinished` → update progress bar
  - `onReceivedError` → show custom error page
  - HTTPS upgrade in `shouldOverrideUrlLoading`
- Custom `WebChromeClient`:
  - `onProgressChanged` → progress bar
  - `onConsoleMessage` → filtered crash logging
  - `onPermissionRequest` → permission manager integration
- JavaScript injection for:
  - Reader Mode (inject custom CSS + strip non-content elements)
  - Media detection (scan DOM for `<video>`, `<audio>`, `<source>` tags)
  - Ad blocking (inject element-hiding CSS rules)
- Tab management: each tab owns its own `WebView` instance in a pool
- `WebSettings` configured for:
  - `javaScriptEnabled = true`
  - `domStorageEnabled = true`
  - `mediaPlaybackRequiresUserGesture = false`
  - Hardware acceleration via `setLayerType(HARDWARE)`
  - User agent switching for Desktop/Mobile mode

---

### 4.11 Material 3 Design

**What:** Google's latest design system.  
**How:**
- `MaterialTheme` with custom `ColorScheme`
- Dynamic color on Android 12+ (`dynamicLightColorScheme` / `dynamicDarkColorScheme`)
- Fallback to custom palette on older Android
- Custom `Typography` using **Inter** (body) + **Space Grotesk** (headers)
- `Surface`, `Card`, `BottomAppBar`, `NavigationBar`, `FloatingActionButton` from M3
- `ModalBottomSheet` for media download options
- Ripple, elevation, and shadow from M3 tokens
- Shape tokens: `RoundedCornerShape(16.dp)` as default card shape

---

### 4.12 Media3 / ExoPlayer (Built-in Video Player)

**What:** Plays downloaded or streamed video/audio files.  
**How:**
- `ExoPlayer` instance managed in a ViewModel (`PlayerViewModel`)
- `PlayerView` embedded via `AndroidView` in Compose
- Supports: MP4, WEBM, HLS, MP3, AAC, M4A
- Controls: play/pause, seek, fullscreen, speed, quality selection
- `MediaSession` for background audio playback
- Picture-in-Picture (PiP) mode for video
- Remembers playback position via Room DB

---

## 5. Feature Implementation Plan

### 5.1 Tab Manager

**Storage:** `tabs` table in Room  
**UI:** Horizontal scrollable tab strip + Grid tab switcher overlay  
**Logic:**
- Each tab = `Tab(id, url, title, favicon, scrollPosition, isIncognito)`
- Active tab tracked in `BrowserViewModel`
- WebView pool: create on open, destroy on close
- Max concurrent WebViews: 5 (oldest background tabs hibernated/serialized)
- Incognito tabs: separate `WebView` with no cache, no cookies, no history persistence

### 5.2 Ad & Tracker Blocker

**How it works:**
- Bundled blocklist: EasyList + EasyPrivacy (plain text domain lists, shipped in `assets/`)
- Loaded at startup into a `HashSet<String>` in memory
- `WebViewClient.shouldInterceptRequest` checks every request URL against blocklist
- Matching request → return empty `WebResourceResponse` (effectively blocked)
- CSS injection for cosmetic filtering (hiding ad elements that loaded from allowed CDNs)
- Update blocklist: periodic `WorkManager` task fetches updated list, stores in Room `blocked_domains` table

### 5.3 Reader Mode

**How it works:**
- Detect article-like pages: check for `<article>`, `<main>`, high text density
- On activation: inject `reader.js` (Mozilla Readability port) via `WebView.evaluateJavascript`
- Readability extracts main content, title, byline
- Inject `reader.css` with clean typography (large Inter font, max-width, comfortable line height)
- Toggle button in browser toolbar

### 5.4 Smart Media Detection

**Detection methods (layered):**
1. **Request interception** — `shouldInterceptRequest` scans every URL for `.mp4`, `.m3u8`, `.webm`, `.mp3`, etc.
2. **DOM scanning** — inject `media_scan.js` after page load; scans `<video>`, `<audio>`, `<source>`, `<a>` tags
3. **Network header inspection** — OkHttp intercept of media content-type headers (`video/*`, `audio/*`)
4. **HLS manifest parsing** — detect `.m3u8` URLs and parse quality variants

**When detected:**
- Add `MediaItem` to `detectedMedia: StateFlow<List<MediaItem>>` in `BrowserViewModel`
- Floating download button appears via `AnimatedVisibility`
- Tapping shows `ModalBottomSheet` with:
  - Filename, estimated size, quality options (if available)
  - Format selector (for HLS: resolution options)
  - Destination folder picker
  - "Download" button → enqueues to Download Engine

### 5.5 Download Manager UI

**Tabs:**
- `Active` — live progress, pause/resume/cancel
- `Completed` — open, share, rename, delete
- `Failed` — error reason, retry, delete

**Per-item card shows:**
- Filename + icon
- Progress bar
- Speed (KB/s or MB/s)
- ETA
- Action buttons

**Implementation:**
- `DownloadViewModel` observes `Flow<List<Download>>` from Room
- Progress updates pushed via `StateFlow` from Download Engine
- `DownloadNotificationService` shows persistent notification during active downloads

---

## 6. UI/UX Design Plan

### Design Identity

**Style:** Glassmorphism + Material 3  
**Mood:** Fast, premium, technical — like a browser built by engineers who care about aesthetics

### Color Palette

| Token | Hex | Usage |
|---|---|---|
| `primary` | `#6C63FF` | Accent, FAB, active states |
| `primaryContainer` | `#1A1A2E` | Dark nav bar, glass base |
| `surface` | `#0F0F1A` | Background (dark mode) |
| `surfaceVariant` | `#16213E` | Cards, bottom sheet |
| `onPrimary` | `#FFFFFF` | Text on accent |
| `glass` | `rgba(255,255,255,0.08)` | Glass card surfaces |
| `glassStroke` | `rgba(255,255,255,0.12)` | Glass card borders |
| `error` | `#FF6B6B` | Error states |

> Light mode: inverted with warm white `#F8F9FF` surface and `#5A52D5` primary.

### Typography

- **Display / Headers:** Space Grotesk (700 weight) — distinctive, modern
- **Body / UI:** Inter (400/500) — clean, legible at small sizes
- **Monospace (URLs):** JetBrains Mono — for URL bar

### Glassmorphism Implementation

```kotlin
// Reusable glass card modifier in core-ui
fun Modifier.glassCard(
    blur: Dp = 16.dp,
    alpha: Float = 0.08f
): Modifier = this
    .background(
        color = Color.White.copy(alpha = alpha),
        shape = RoundedCornerShape(16.dp)
    )
    .border(
        width = 1.dp,
        color = Color.White.copy(alpha = 0.12f),
        shape = RoundedCornerShape(16.dp)
    )
// Note: True backdrop blur on Android requires RenderEffect (API 31+)
// Fallback: frosted overlay for API < 31
```

### Screen List

| Screen | Key Components |
|---|---|
| **Onboarding** | Animated logo, feature carousel, permission request flow |
| **Home / New Tab** | Search bar, quick dial (speed dial), recent bookmarks |
| **Browser** | WebView, URL bar, progress bar, tab strip, FAB |
| **Tab Switcher** | Grid of tab cards with favicon + screenshot |
| **Download Manager** | Tabbed list (Active/Completed/Failed) |
| **Bookmark Manager** | Folder tree + list/grid toggle |
| **History** | Grouped by date, search, bulk delete |
| **Settings** | Sectioned list (Privacy, Downloads, Appearance, About) |
| **File Manager** | Tree + list, preview, share, delete |
| **Video Player** | Full screen, controls overlay, PiP |

### Navigation Structure

```
BottomNavigationBar
├── 🌐 Browser (main WebView)
├── ⬇️  Downloads
├── 📑 Bookmarks
├── 🕐 History
└── ⚙️  Settings
```

### Key Animations

- Tab open/close: shared element transition + scale
- Download button: spring animation appear/disappear
- Progress bar: smooth lerp animation
- Page load: fade in WebView content
- Bottom sheet: spring-based slide up
- Floating media button: pulse animation when new media detected

---

## 7. Download Engine Design

### Core Principle

**Everything runs on-device. No server. No proxy. No cloud.**

### Pluggable Architecture

```
┌─────────────────────────────────────────┐
│           Download Manager UI           │
│         (feature-downloads)             │
└──────────────────┬──────────────────────┘
                   │ calls
┌──────────────────▼──────────────────────┐
│         DownloadRepository              │  ← interface in domain
│    (domain/repository/DownloadRepository.kt)   │
└──────────────────┬──────────────────────┘
                   │ implemented by
┌──────────────────▼──────────────────────┐
│       DownloadRepositoryImpl            │  ← data layer
│  - delegates to DownloadEngine          │
│  - stores progress to Room              │
└──────────────────┬──────────────────────┘
                   │ uses
┌──────────────────▼──────────────────────┐
│         DownloadEngine (interface)       │  ← swappable
│  + start(request): Flow<DownloadProgress>│
│  + pause(id)                            │
│  + resume(id)                           │
│  + cancel(id)                           │
│  + retry(id)                            │
└──────────────────┬──────────────────────┘
                   │
       ┌───────────┴────────────┐
       │                        │
┌──────▼──────┐       ┌─────────▼──────────────┐
│ OkHttp      │       │ ExternalToolAdapter     │
│ DownloadEngine│     │ (e.g., yt-dlp binary)  │
│ (default)   │       │ (optional/future)       │
└─────────────┘       └────────────────────────┘
```

### DownloadEngine Interface

```kotlin
// In :core-domain
interface DownloadEngine {
    fun download(request: DownloadRequest): Flow<DownloadProgress>
    suspend fun pause(downloadId: String)
    suspend fun resume(downloadId: String)
    suspend fun cancel(downloadId: String)
    suspend fun retry(downloadId: String)
}

data class DownloadRequest(
    val id: String,
    val url: String,
    val destinationPath: String,
    val headers: Map<String, String> = emptyMap(),
    val expectedMimeType: String? = null
)

sealed class DownloadProgress {
    data class Running(val bytesDownloaded: Long, val totalBytes: Long, val speedBps: Long) : DownloadProgress()
    data class Paused(val bytesDownloaded: Long) : DownloadProgress()
    object Completed : DownloadProgress()
    data class Failed(val reason: String) : DownloadProgress()
}
```

### OkHttpDownloadEngine (Default Implementation)

**How it works:**
1. `OkHttpClient.newCall(request).execute()` streams response body
2. `response.body.byteStream()` piped to `FileOutputStream` in chunks (8 KB buffer)
3. Byte count tracked → emitted as `DownloadProgress.Running`
4. Speed = bytes / elapsed time since last emission (updated every 500ms)
5. Pause: `Job.cancel()` stores byte offset in Room, marks as `PAUSED`
6. Resume: new request with `Range: bytes=<offset>-` header (if server supports it)
7. Retry: re-enqueue with same request + reset offset

### HLS Download

- Fetch `.m3u8` manifest via OkHttp
- Parse segments (`.ts` files) using custom `M3u8Parser`
- Download all segments sequentially/in parallel (configurable)
- Concat/mux segments: use `ProcessBuilder` to run bundled `ffmpeg` binary (or use `MediaMuxer` API for simpler streams)
- Show overall progress: `(segmentsDownloaded / totalSegments) * 100`

### ExternalToolAdapter (Future / Optional)

```kotlin
// Adapter pattern — keeps browser code clean
class ExternalToolAdapter(
    private val toolPath: String  // e.g., path to yt-dlp binary on device
) : DownloadEngine {
    override fun download(request: DownloadRequest): Flow<DownloadProgress> = flow {
        val process = ProcessBuilder(toolPath, request.url, "-o", request.destinationPath)
            .redirectErrorStream(true)
            .start()
        // parse stdout for progress, emit DownloadProgress
    }
}
```

The browser code only sees `DownloadEngine`. Swapping to `ExternalToolAdapter` = one line change in Hilt module.

### Download Queue

- `DownloadQueue`: `LinkedList<DownloadRequest>` managed in `DownloadRepositoryImpl`
- Max concurrent: 3 downloads (configurable in settings)
- Queue persisted in Room (survives app restart)
- WorkManager enqueues background workers for queued items

---

## 8. Security Implementation

### HTTPS Upgrade

- `WebViewClient.shouldOverrideUrlLoading`: rewrite `http://` → `https://`
- OkHttp interceptor: same for API calls
- Show warning dialog if HTTPS unavailable and user insists on HTTP

### Cookie Controls

- Per-site cookie settings stored in `DataStore`
- `CookieManager` controlled per WebView instance
- Incognito: `CookieManager.removeAllCookies()` on tab close
- Third-party cookie blocking: configurable in Settings

### Permission Manager

- `PermissionManager` class tracks what each site has requested
- `WebChromeClient.onPermissionRequest` → check stored permission, prompt if unknown
- Per-site permissions stored in Room: `site_permissions` table
- UI in Settings → Site Permissions

### Secure Storage

- **Passwords / sensitive prefs:** `EncryptedSharedPreferences` (Jetpack Security)
- **Session tokens:** Android Keystore
- **WebView cache:** stored in `cacheDir` (not external storage)
- **Downloads:** stored in user-selected folder via `StorageAccessFramework` (SAF)

### Safe Browsing

- `WebView.setSafeBrowsingEnabled(true)` — uses Google Safe Browsing API
- Fallback: custom list of known malicious domains checked on navigation

### Crash Logging

- `Thread.setDefaultUncaughtExceptionHandler` for unhandled exceptions
- Logs stored in Room `crash_logs` table (last 50 entries)
- No remote crash reporting (privacy-first) — user can opt-in to send report

### Network Error Handling

- Custom error page injected via `loadDataWithBaseURL` on `onReceivedError`
- Error types: no connection, SSL error, timeout, DNS failure — each with specific message
- Auto-retry on timeout (1 attempt)

---

## 9. Database Schema

```sql
-- Tabs
CREATE TABLE tabs (
    id TEXT PRIMARY KEY,
    url TEXT NOT NULL,
    title TEXT,
    favicon_url TEXT,
    scroll_position INTEGER DEFAULT 0,
    is_incognito INTEGER DEFAULT 0,
    created_at INTEGER NOT NULL,
    last_accessed INTEGER NOT NULL,
    is_active INTEGER DEFAULT 0
);

-- Bookmarks
CREATE TABLE bookmarks (
    id TEXT PRIMARY KEY,
    url TEXT NOT NULL,
    title TEXT NOT NULL,
    favicon_url TEXT,
    folder_id TEXT,
    created_at INTEGER NOT NULL
);

CREATE TABLE bookmark_folders (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    parent_id TEXT,
    created_at INTEGER NOT NULL
);

-- History
CREATE TABLE history (
    id TEXT PRIMARY KEY,
    url TEXT NOT NULL,
    title TEXT,
    visit_count INTEGER DEFAULT 1,
    last_visited INTEGER NOT NULL
);

-- Downloads
CREATE TABLE downloads (
    id TEXT PRIMARY KEY,
    url TEXT NOT NULL,
    filename TEXT NOT NULL,
    destination_path TEXT NOT NULL,
    mime_type TEXT,
    total_bytes INTEGER DEFAULT 0,
    downloaded_bytes INTEGER DEFAULT 0,
    status TEXT NOT NULL,          -- QUEUED, RUNNING, PAUSED, COMPLETED, FAILED
    error_message TEXT,
    speed_bps INTEGER DEFAULT 0,
    enqueued_at INTEGER NOT NULL,
    completed_at INTEGER,
    retry_count INTEGER DEFAULT 0
);

-- Blocked Domains (Ad/Tracker Blocker)
CREATE TABLE blocked_domains (
    domain TEXT PRIMARY KEY,
    list_source TEXT NOT NULL,     -- easylist, easyprivacy, custom
    added_at INTEGER NOT NULL
);

-- Site Permissions
CREATE TABLE site_permissions (
    id TEXT PRIMARY KEY,
    origin TEXT NOT NULL,
    permission_type TEXT NOT NULL, -- CAMERA, MICROPHONE, LOCATION, NOTIFICATIONS
    granted INTEGER NOT NULL,
    updated_at INTEGER NOT NULL
);
```

---

## 10. CI/CD Pipeline

### Git Strategy

- **Branch:** `main` → production builds  
- **Branch:** `develop` → integration  
- **Branch:** `feature/*` → individual features  
- Push to `main` triggers release build; push to `develop` triggers debug build

### GitHub Actions Workflows

#### `.github/workflows/ci.yml` — Runs on every push & PR

```
Steps:
1. Checkout code
2. Set up JDK 17 (Temurin)
3. Set up Android SDK (actions/setup-android)
4. Cache Gradle dependencies
5. Run ktlint (lint check)
6. Run unit tests (./gradlew test)
7. Run Android lint (./gradlew lint)
8. Upload test results as artifact
9. Upload lint report as artifact
```

#### `.github/workflows/release.yml` — Runs on push to main

```
Steps:
1. All steps from ci.yml
2. Decode keystore from GitHub Secret (BASE64_KEYSTORE)
3. Build release APK (./gradlew assembleRelease)
4. Sign APK using keystore
5. Build release AAB (./gradlew bundleRelease)
6. Upload APK + AAB as release artifacts
7. Create GitHub Release with artifacts
8. Upload mapping.txt (ProGuard) as artifact for crash symbolication
```

### Required GitHub Secrets

| Secret Name | Description |
|---|---|
| `KEYSTORE_BASE64` | Base64-encoded release keystore |
| `KEYSTORE_PASSWORD` | Keystore password |
| `KEY_ALIAS` | Key alias |
| `KEY_PASSWORD` | Key password |

### Gradle Configuration

- `build-logic/` module with convention plugins:
  - `axbrowser.android.library` — applied to all feature/core modules
  - `axbrowser.android.application` — applied to `:app`
  - `axbrowser.compose` — Compose setup shared across modules
- Version catalog: `gradle/libs.versions.toml` for all dependency versions
- ProGuard rules: `proguard-rules.pro` per module

---

## 11. Testing Strategy

### Unit Tests (`test/`)

| What | How |
|---|---|
| UseCases | JUnit 5 + MockK, fake repositories |
| ViewModels | JUnit 5 + MockK + Turbine (Flow testing) |
| Repository implementations | JUnit 5 + MockK, mock DAOs/network |
| DownloadEngine | JUnit 5 + MockWebServer (OkHttp) |
| M3u8Parser | JUnit 5, test with sample manifests |
| Blocklist matcher | JUnit 5, test with known blocked/allowed URLs |

### Instrumented Tests (`androidTest/`)

| What | How |
|---|---|
| Room DB | `@RunWith(AndroidJUnit4::class)`, in-memory DB |
| DataStore | `@RunWith(AndroidJUnit4::class)` |

### UI Tests (optional / future)

- Compose `createComposeRule()` for individual screen tests
- End-to-end: Espresso for critical flows (download a file, add bookmark)

### Test Coverage Target

- Domain layer: **90%+**
- Data layer: **80%+**
- ViewModel: **85%+**
- UI: **key flows covered**

### Libraries Used for Testing

```toml
[libraries]
junit5 = "org.junit.jupiter:junit-jupiter:5.10.0"
mockk = "io.mockk:mockk:1.13.8"
turbine = "app.cash.turbine:turbine:1.0.0"
coroutines-test = "org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3"
mockwebserver = "com.squareup.okhttp3:mockwebserver:4.12.0"
androidx-test-runner = "androidx.test:runner:1.5.2"
room-testing = "androidx.room:room-testing:2.6.1"
```

---

## 12. Build & Delivery Phases

### Phase 1 — Foundation (Week 1)
- [ ] Create Android project with all Gradle modules
- [ ] Set up `build-logic` with convention plugins
- [ ] Configure `libs.versions.toml`
- [ ] Set up Hilt across modules
- [ ] Implement Material 3 theme + glassmorphism tokens in `core-ui`
- [ ] Set up Room database with all schemas + migrations
- [ ] Set up DataStore for preferences
- [ ] Configure OkHttp client
- [ ] Initialize Git + push to GitHub
- [ ] Configure CI workflow (`ci.yml`)

### Phase 2 — Core Browser (Week 2)
- [ ] WebView integration in `feature-browser`
- [ ] Tab model + Room persistence
- [ ] Tab manager ViewModel + UI
- [ ] URL bar + navigation controls
- [ ] Progress bar + page loading states
- [ ] Incognito mode WebView
- [ ] HTTPS upgrade interceptor
- [ ] WebView settings (hardware acceleration, JS, DOM storage)
- [ ] Custom error page

### Phase 3 — Privacy Features (Week 2–3)
- [ ] Blocklist loader from assets
- [ ] Request interceptor in WebViewClient
- [ ] CSS injection for cosmetic filtering
- [ ] Cookie controls + per-site settings
- [ ] Permission manager (site-level)
- [ ] Safe Browsing integration
- [ ] Reader mode (Readability.js injection)
- [ ] Desktop/Mobile mode toggle

### Phase 4 — Media Detection + Download Engine (Week 3–4)
- [ ] Media URL pattern detection in `shouldInterceptRequest`
- [ ] DOM scanner JS injection (`media_scan.js`)
- [ ] `MediaItem` model + detection ViewModel state
- [ ] Floating download button (Compose + animation)
- [ ] Download options bottom sheet (quality, format, destination)
- [ ] `DownloadEngine` interface
- [ ] `OkHttpDownloadEngine` implementation
- [ ] Pause/resume with Range header
- [ ] Download queue management
- [ ] WorkManager integration for background downloads
- [ ] Download notification (persistent + progress)
- [ ] M3u8 parser + HLS segment downloader

### Phase 5 — Download Manager UI + File Manager (Week 4)
- [ ] Download Manager screen (Active/Completed/Failed tabs)
- [ ] Per-download card with progress, speed, ETA
- [ ] Retry, rename, delete, share, open actions
- [ ] Built-in file manager (browse download folder)
- [ ] File preview (images, text)
- [ ] Launch video player from file manager

### Phase 6 — Bookmarks, History, Settings (Week 4–5)
- [ ] Bookmark add/edit/delete
- [ ] Bookmark folder management
- [ ] History with grouping + search
- [ ] History bulk delete / clear all
- [ ] Settings screen (all sections)
- [ ] Search engine selection
- [ ] Font size / zoom level
- [ ] Cache clear functionality

### Phase 7 — Video Player + Polish (Week 5)
- [ ] ExoPlayer integration in `feature-videoplayer`
- [ ] Full-screen controls
- [ ] PiP support
- [ ] Background audio playback via MediaSession
- [ ] Onboarding screens (3 screens + permission flow)
- [ ] Smooth animations throughout
- [ ] Performance pass (memory profiling, layout inspection)
- [ ] Dark/Light mode complete

### Phase 8 — Testing + CI/CD Release (Week 6)
- [ ] Unit tests for all UseCases
- [ ] Unit tests for all ViewModels
- [ ] Unit tests for DownloadEngine
- [ ] Room DB instrumented tests
- [ ] Configure release signing workflow (`release.yml`)
- [ ] ProGuard rules finalized
- [ ] Final documentation (README, SETUP.md, ARCHITECTURE.md)
- [ ] Final GitHub push → CI/CD full pipeline run ✅

---

## 13. Folder Structure

```
axbrowser/
│
├── app/
│   ├── src/main/
│   │   ├── kotlin/com/akay/axbrowser/
│   │   │   ├── AxBrowserApp.kt           ← @HiltAndroidApp
│   │   │   ├── MainActivity.kt           ← @AndroidEntryPoint, NavHost root
│   │   │   └── di/
│   │   │       └── AppModule.kt
│   │   └── res/
│   └── build.gradle.kts
│
├── core/
│   ├── core-ui/
│   │   └── src/main/kotlin/com/akay/core/ui/
│   │       ├── theme/
│   │       │   ├── AxTheme.kt            ← MaterialTheme wrapper
│   │       │   ├── Color.kt
│   │       │   ├── Type.kt
│   │       │   └── Shape.kt
│   │       ├── components/
│   │       │   ├── GlassCard.kt
│   │       │   ├── AxButton.kt
│   │       │   ├── ProgressBar.kt
│   │       │   └── FloatingMediaButton.kt
│   │       └── util/
│   │           └── ModifierExt.kt        ← glassCard() modifier etc.
│   │
│   ├── core-domain/
│   │   └── src/main/kotlin/com/akay/core/domain/
│   │       ├── model/
│   │       │   ├── Tab.kt
│   │       │   ├── Download.kt
│   │       │   ├── Bookmark.kt
│   │       │   ├── HistoryItem.kt
│   │       │   └── MediaItem.kt
│   │       ├── repository/
│   │       │   ├── TabRepository.kt
│   │       │   ├── DownloadRepository.kt
│   │       │   ├── BookmarkRepository.kt
│   │       │   └── HistoryRepository.kt
│   │       ├── usecase/
│   │       │   ├── BaseUseCase.kt
│   │       │   └── ... (each feature's UseCases)
│   │       └── engine/
│   │           ├── DownloadEngine.kt     ← interface
│   │           ├── DownloadRequest.kt
│   │           └── DownloadProgress.kt
│   │
│   ├── core-data/
│   │   └── src/main/kotlin/com/akay/core/data/
│   │       ├── db/
│   │       │   ├── AxDatabase.kt
│   │       │   ├── entity/
│   │       │   └── dao/
│   │       ├── datastore/
│   │       │   └── AxPreferences.kt
│   │       ├── network/
│   │       │   └── OkHttpProvider.kt
│   │       └── di/
│   │           └── DataModule.kt
│   │
│   └── core-testing/
│       └── src/main/kotlin/com/akay/core/testing/
│           ├── FakeDownloadRepository.kt
│           ├── FakeBookmarkRepository.kt
│           └── TestCoroutineRule.kt
│
├── feature/
│   ├── feature-browser/
│   │   └── src/main/kotlin/com/akay/feature/browser/
│   │       ├── ui/
│   │       │   ├── BrowserScreen.kt
│   │       │   ├── TabSwitcherScreen.kt
│   │       │   ├── UrlBar.kt
│   │       │   └── ReaderModeView.kt
│   │       ├── viewmodel/
│   │       │   └── BrowserViewModel.kt
│   │       ├── webview/
│   │       │   ├── AxWebViewClient.kt
│   │       │   ├── AxWebChromeClient.kt
│   │       │   ├── MediaDetector.kt
│   │       │   ├── AdBlocker.kt
│   │       │   └── WebViewPool.kt
│   │       └── di/
│   │           └── BrowserModule.kt
│   │
│   ├── feature-downloads/
│   │   └── src/main/kotlin/com/akay/feature/downloads/
│   │       ├── ui/
│   │       │   ├── DownloadManagerScreen.kt
│   │       │   ├── DownloadCard.kt
│   │       │   └── MediaOptionsSheet.kt
│   │       ├── viewmodel/
│   │       │   └── DownloadViewModel.kt
│   │       ├── engine/
│   │       │   ├── OkHttpDownloadEngine.kt
│   │       │   ├── HlsDownloader.kt
│   │       │   ├── M3u8Parser.kt
│   │       │   └── ExternalToolAdapter.kt
│   │       ├── worker/
│   │       │   └── DownloadWorker.kt
│   │       ├── notification/
│   │       │   └── DownloadNotificationManager.kt
│   │       └── di/
│   │           └── DownloadModule.kt
│   │
│   ├── feature-bookmarks/
│   ├── feature-history/
│   ├── feature-settings/
│   ├── feature-filemanager/
│   └── feature-videoplayer/
│
├── build-logic/
│   └── convention/
│       ├── AxAndroidLibrary.kt
│       ├── AxAndroidApplication.kt
│       └── AxCompose.kt
│
├── gradle/
│   └── libs.versions.toml
│
├── .github/
│   └── workflows/
│       ├── ci.yml
│       └── release.yml
│
├── docs/
│   ├── ARCHITECTURE.md
│   ├── SETUP.md
│   └── DOWNLOAD_ENGINE.md
│
├── README.md
└── settings.gradle.kts
```

---

## 14. Key Implementation Notes

### WebView Performance

- Use `LAYER_TYPE_HARDWARE` on WebView for GPU rendering
- Pre-warm WebView instance at app startup (cold start optimization)
- Limit DOM storage to prevent memory bloat
- Serialize/deserialize background tab state rather than keeping all WebViews live
- Enable `setRenderPriority(RenderPriority.HIGH)` for active tab

### Memory Management

- Tab limit: warn user at 10+ tabs
- Background tabs: pause JavaScript execution via `onPause()`
- WebView pool: destroy LRU tab WebView when over limit
- Bitmap caching for favicons: `LruCache<String, Bitmap>`

### Download File Naming

- Sanitize filename: strip illegal characters, limit to 200 chars
- Collision handling: append `(1)`, `(2)` etc. if file exists
- MIME type → extension mapping for files with no extension in URL

### Blocklist Loading

- Load blocklist from `assets/blocklist/` on background thread at startup
- Use `BufferedReader` line-by-line for memory efficiency (list is 100k+ lines)
- Store in `HashSet<String>` for O(1) lookup
- Domain extraction from full URL before lookup (strip `https://`, path, query)

### HLS / m3u8 Support

- Parse `#EXT-X-STREAM-INF` tags for quality variants
- Present quality list in download options sheet
- Download selected quality's segment list sequentially
- Use `FileDescriptor` + `MediaMuxer` if no FFmpeg binary available

### StorageAccessFramework (SAF)

- Use `ACTION_OPEN_DOCUMENT_TREE` for download folder picker
- Persist `UriPermission` via `takePersistableUriPermission`
- All file writes go through `DocumentFile` API
- MediaStore insertion after download completes (for gallery visibility)

### Incognito Mode Checklist

- Separate `WebView` instance (not from pool)
- `CookieManager` disabled for incognito WebView
- No history writes
- No cache writes (`setCacheMode(WebSettings.LOAD_NO_CACHE)`)
- No tab state persistence to Room
- Clear on close: `WebView.clearCache(true)`, `WebView.clearHistory()`

### ProGuard / R8 Rules

- Keep Room entity classes
- Keep Hilt generated code
- Keep OkHttp (for OkHttp reflection in interceptors)
- Keep WebView JS interface classes
- Keep Parcelable implementations

---

## Quick Reference: Dependency ↔ Usage Map

| Library | Module | Used For |
|---|---|---|
| `compose-ui` | core-ui, all features | All UI |
| `hilt-android` | app, all features | DI graph |
| `room-runtime` | core-data | Local DB |
| `datastore-preferences` | core-data | Settings storage |
| `okhttp` | core-data, feature-downloads | HTTP client |
| `work-runtime-ktx` | feature-downloads | Background downloads |
| `media3-exoplayer` | feature-videoplayer | Video/audio playback |
| `media3-ui` | feature-videoplayer | Player UI controls |
| `navigation-compose` | app | Screen routing |
| `kotlinx-coroutines` | all | Async, Flow |
| `kotlinx-serialization` | core-data | JSON parsing |
| `accompanist-*` | core-ui | Compose extras |
| `junit5` | core-testing | Unit tests |
| `mockk` | core-testing | Mocking |
| `turbine` | core-testing | Flow testing |

---

*Plan Version: 1.0 — AxBrowser*  
*Author: AKAY*  
*Status: Ready for Implementation*

