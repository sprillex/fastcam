# Zero-Latency RTSP Viewer Ecosystem: Project Roadmap

**Target:** `sprillex/android` | **Modules:** `rtsp-viewer` (Headless Player), `rtsp-config` (Manager) | **API Level:** Min 26 / Target 34

---

## System Architecture Overview

A decoupled two-app architecture engineered for sub-500ms cold-start camera streaming. The design enforces strict separation of concerns:

- **Headless Viewer (`com.sprillex.rtspviewer`):** Stripped of all UI chrome, toolbars, and configuration forms. Operates an unadorned hardware-accelerated Media3 `SurfaceView` with aggressive jitter buffers (200ms). Contains custom scheme routing (`fastcam://video`) and sub-millisecond asynchronous WireGuard tunnel orchestration for zero-latency remote failover.
- **Configurator (`com.sprillex.rtspconfig`):** Administrative console for parameter verification, TCP RTSP handshake testing, and pushing encrypted configuration payloads via signature-guarded broadcast intents directly to the viewer's disk cache.

---

## Phase 1: Barebones Headless RTSP Player Core [ ] Unstarted

Establish the foundation of the Viewer app with optimized Media3/ExoPlayer RTSP dependencies, fullscreen no-decor rendering, and tuned low-latency buffer thresholds.

### [x] Task 1.1: Project Scaffolding, Build Configuration & Manifest Hardening [EASY]
- **Target Files:** `settings.gradle.kts`, `rtsp-viewer/build.gradle.kts`, `rtsp-viewer/src/main/AndroidManifest.xml`
- **Dependencies:** `androidx.media3:media3-exoplayer:1.3.1`, `androidx.media3:media3-exoplayer-rtsp:1.3.1`, `androidx.media3:media3-ui:1.3.1`
- **Requirements:** Configure MinSDK 26, TargetSDK 34, JVM 17. Configure ProGuard/R8 minify/shrink directives. Add fullscreen, hardware acceleration flags, and network permissions.
- **Verification:** `./gradlew :rtsp-viewer:assembleDebug`

### [ ] Task 1.2: Raw Surface Layout & Media3 RTSP Player Pipeline [HARD]
- **Target Files:** `rtsp-viewer/src/main/res/layout/activity_main.xml`, `rtsp-viewer/src/main/java/com/sprillex/rtspviewer/MainActivity.kt`
- **Requirements:** Inflate layout with `app:use_controller="false"` and `app:surface_type="surface_view"`. Instantiate `ExoPlayer` using custom `DefaultLoadControl` (minBuffer=200ms, maxBuffer=500ms, startBuffer=100ms). Configure `RtspMediaSource.Factory().setForceUseRtpTcp(true)` to prevent NAT packet drops. Enforce `FLAG_KEEP_SCREEN_ON`.
- **Verification:** `./gradlew :rtsp-viewer:connectedAndroidTest` (Validate video surface allocation and absence of controller overlays)

---

## Phase 2: Dynamic IPC Configuration & Security Boundaries [ ] Unstarted

Implement secure out-of-process broadcast synchronization allowing credentials and endpoints to be updated without rebuilding or reinstalling the viewer.

### [x] Task 2.1: Signature-Guarded Broadcast Receiver & Flash Persistence [EASY]
- **Target Files:** `rtsp-viewer/src/main/AndroidManifest.xml`, `rtsp-viewer/src/main/java/com/sprillex/rtspviewer/ConfigReceiver.kt`
- **Requirements:** Declare custom signature permission `com.sprillex.rtspviewer.permission.WRITE_CONFIG`. Implement `ConfigReceiver` to extract `stream_uri` from `ACTION_UPDATE_STREAM` broadcasts and commit asynchronously to private `SharedPreferences` (`viewer_config.xml`).
- **Verification:** `adb shell am broadcast -a com.sprillex.rtspviewer.ACTION_UPDATE_STREAM -n com.sprillex.rtspviewer/.ConfigReceiver --es stream_uri "rtsp://test"`

### [ ] Task 2.2: Configurator Companion Application Core [HARD]
- **Target Files:** `rtsp-config/build.gradle.kts`, `rtsp-config/src/main/AndroidManifest.xml`, `rtsp-config/src/main/java/com/sprillex/rtspconfig/MainActivity.kt`
- **Requirements:** Build admin UI for host, port, credentials, and path input. Implement background raw TCP/RTSP handshake test socket targeting port 554. On commit, dispatch explicit broadcast targeting `com.sprillex.rtspviewer` guarded by the shared signature permission.
- **Verification:** `./gradlew :rtsp-config:assembleRelease :rtsp-viewer:assembleRelease` (Verify shared APK signing keys)

---

## Phase 3: Deep Linking & External Action Integration [ ] Unstarted

Enable sub-millisecond invocation from notification handlers (Home Assistant, Pushover, ntfy) and ensure robust single-instance lifecycle management.

### [x] Task 3.1: Custom Scheme Intent Filter & Deep Link Routing [EASY]
- **Target Files:** `rtsp-viewer/src/main/AndroidManifest.xml`, `rtsp-viewer/src/main/java/com/sprillex/rtspviewer/MainActivity.kt`
- **Requirements:** Register `android:scheme="fastcam"` and `android:host="video"` under `MainActivity` intent filters. Implement `onNewIntent()` to handle incoming intents during active playback without destroying the surface or triggering re-buffering.
- **Verification:** `adb shell am start -a android.intent.action.VIEW -d "fastcam://video"`

---

## Phase 4: Zero-Latency Remote Failover & WireGuard Automation [ ] Unstarted

Introduce non-blocking kernel network probing and asynchronous WireGuard tunnel orchestration to allow remote viewing without introducing latency while at home.

### [ ] Task 4.1: Non-Blocking Network Probe & Asynchronous WireGuard Orchestration [HARD]
- **Target Files:** `rtsp-viewer/src/main/AndroidManifest.xml`, `rtsp-viewer/src/main/java/com/sprillex/rtspviewer/MainActivity.kt`
- **Requirements:** Query `ConnectivityManager.getNetworkCapabilities()` during `onCreate()` (<0.5ms in-memory read). If transport is non-Wi-Fi, immediately dispatch broadcast `com.wireguard.android.action.SET_TUNNEL_UP` with tunnel name `HomeLab`. Simultaneously initialize RTSP player in parallel. Implement backoff retry mechanism (150ms interval, max 4 attempts) on `PlaybackException` to catch delayed tunnel initialization.
- **Verification:** Simulate cellular interface via `adb shell cmd connectivity` and verify intent broadcast via `logcat -s WireGuard`

### [ ] Task 4.2: Resource Teardown, Codec Release & Tunnel State Restoration [EASY]
- **Target Files:** `rtsp-viewer/src/main/java/com/sprillex/rtspviewer/MainActivity.kt`
- **Requirements:** Override `onStop()` and `onDestroy()` to cancel coroutine scopes, immediately release Media3 player surface to free hardware decoders, and dispatch `SET_TUNNEL_DOWN` if and only if the tunnel was initiated by the current viewer session.
- **Verification:** Run activity finish loop and monitor hardware decoder allocation via `adb shell dumpsys media.player`

---

## Phase 5: Profiling, Optimization & Final Release Packaging [ ] Unstarted

Benchmark cold-start launch times against local and cellular routes, audit ProGuard keep-rules, and assemble signed release packages.

### [ ] Task 5.1: Latency Profiling & R8 ProGuard Minification Rules [EASY]
- **Target Files:** `rtsp-viewer/proguard-rules.pro`, `rtsp-config/proguard-rules.pro`
- **Requirements:** Retain Media3 ExoPlayer RTSP internals while aggressively stripping all unused UI controllers, styling assets, and metadata. Execute cold-start launch benchmark with `logcat -s ActivityTaskManager` to verify `displayedTime < 500ms`.
- **Verification:** `adb shell am start-W -n com.sprillex.rtspviewer/.MainActivity`
