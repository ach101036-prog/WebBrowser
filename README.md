# FlowBrowser — Video-Detecting Android Web Browser with FFmpeg MP4 Export

**FlowBrowser** is a Kotlin & Jetpack Compose Android web browser that automatically detects videos playing on web pages and lets you download them in `.mp4` format at your chosen resolution using FFmpeg in a background Foreground Service.

## Key Features

- **Full WebView Browser**: URL/search omnibox, multi-tab management, bookmarks (Room), Desktop Site toggle, and fullscreen HTML5 `<video>` playback.
- **Dual-Layer Video Detection**:
  - **DOM & Player Hooks**: Injected JavaScript monitors HTML5 `<video>` elements (`play`, `playing`, `loadedmetadata`), `HTMLMediaElement.prototype.play`, `fetch`, and `XMLHttpRequest` calls.
  - **Network Stream Sniffer**: Intercepts `.mp4`, `.m4v`, `.webm`, `.mov`, `.mkv`, `.m3u8` (HLS), and `.mpd` (DASH) streams with page cookies, `User-Agent`, and `Referer`.
- **Resolution Selector**: Choose **Original Quality**, **1080p Full HD**, **720p HD**, **480p SD**, or **360p Compact** before queuing the download.
- **Background FFmpeg Processing**: Runs `FFmpegKit` / `FFprobeKit` in a `dataSync` foreground service with live progress notifications, cancellation support, and automatic `MediaStore` saving to `Movies/FlowBrowser`.
- **Offline Library & In-App Player**: Browse, search, share, delete, and watch saved MP4 videos with Jetpack Media3 (`ExoPlayer`).

## Project Structure

```text
FlowBrowser/
├── .github/workflows/android-apk.yml   # GitHub Actions CI/CD APK builder
├── app/
│   ├── build.gradle.kts                # Module Gradle config & dependencies
│   ├── proguard-rules.pro
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/example/
│       │   │   ├── FlowBrowserApp.kt
│       │   │   ├── MainActivity.kt
│       │   │   ├── data/browser/       # Video models, URL classifier, JS sniffer
│       │   │   ├── data/ffmpeg/        # FFmpeg command builder, OkHttp & FFmpeg processor
│       │   │   ├── data/local/         # Room database, DAOs, repositories
│       │   │   ├── data/recorder/      # MediaStore MP4 saver
│       │   │   ├── service/            # Foreground DownloadService, coordinator, notifications
│       │   │   └── ui/                 # Compose screens (Browser, Downloads, Player, Navigation, Theme)
│       │   └── res/                    # Drawables, adaptive launcher icons, fonts, strings, xml
│       └── test/                       # Unit & Robolectric tests
├── gradle/libs.versions.toml           # Version catalog
├── build.gradle.kts
├── settings.gradle.kts
└── README.md
```

## Building

```bash
gradle :app:testDebugUnitTest
gradle :app:assembleDebug
```
