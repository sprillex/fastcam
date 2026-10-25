# Implementation Plan: Zero-Latency RTSP Viewer Ecosystem

This document outlines the atomic execution steps for implementing the Zero-Latency RTSP Viewer Ecosystem. Each task is designed for single-PR automated execution and ordered strictly by sequential dependency path.

---

## Phase 1: Barebones Headless RTSP Player Core

### Task 1.1: Project Scaffolding, Build Configuration & Manifest Hardening [RESOLVED] [EASY]
- **Target Files:**
  - Modify: `settings.gradle.kts`
  - Create: `rtsp-viewer/build.gradle.kts`, `rtsp-viewer/src/main/AndroidManifest.xml`
- **Dependencies:**
  - `androidx.media3:media3-exoplayer:1.3.1`
  - `androidx.media3:media3-exoplayer-rtsp:1.3.1`
  - `androidx.media3:media3-ui:1.3.1`
- **Core Technical Requirements:**
  - Configure `minSdk = 26`, `targetSdk = 34`, and JVM target 17 across module gradle configurations.
  - Setup ProGuard/R8 minify and shrinkResources directives for release builds.
  - Declare system permissions in `AndroidManifest.xml`: `INTERNET`, `ACCESS_NETWORK_STATE`, and `CHANGE_NETWORK_STATE`.
  - Configure activity manifest directives: hardware acceleration enabled, no-title fullscreen theme (`Theme.NoTitleBar.Fullscreen`), and leanback launcher category where appropriate.
- **Verification Criteria:**
  - `./gradlew :rtsp-viewer:assembleDebug`

### Task 1.2: Raw Surface Layout & Media3 RTSP Player Pipeline [HARD]
- **Target Files:**
  - Create: `rtsp-viewer/src/main/res/layout/activity_main.xml`
  - Create: `rtsp-viewer/src/main/java/com/sprillex/rtspviewer/MainActivity.kt`
- **Dependencies:**
  - Task 1.1
- **Core Technical Requirements:**
  - Layout: Inflate `androidx.media3.ui.PlayerView` occupying match_parent dimensions with attributes `app:use_controller="false"` and `app:surface_type="surface_view"`.
  - Player Setup: Instantiate `ExoPlayer` with custom `DefaultLoadControl` tuned for low latency (`minBufferMs = 200`, `maxBufferMs = 500`, `bufferForPlaybackMs = 100`, `bufferForPlaybackAfterRebufferMs = 200`).
  - MediaSource Configuration: Build `RtspMediaSource.Factory().setForceUseRtpTcp(true)` to prevent NAT packet drops and stream truncation.
  - Display Flags: Set `WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON` on window attach.
- **Verification Criteria:**
  - `./gradlew :rtsp-viewer:connectedAndroidTest`

---

## Phase 2: Dynamic IPC Configuration & Security Boundaries

### Task 2.1: Signature-Guarded Broadcast Receiver & Flash Persistence [RESOLVED] [EASY]
- **Target Files:**
  - Modify: `rtsp-viewer/src/main/AndroidManifest.xml`
  - Create: `rtsp-viewer/src/main/java/com/sprillex/rtspviewer/ConfigReceiver.kt`
- **Dependencies:**
  - Task 1.1
- **Core Technical Requirements:**
  - Permission Boundary: Declare custom signature permission `com.sprillex.rtspviewer.permission.WRITE_CONFIG` with `android:protectionLevel="signature"`.
  - Receiver Declaration: Register `ConfigReceiver` in manifest for action `com.sprillex.rtspviewer.ACTION_UPDATE_STREAM` requiring the signature permission.
  - Storage Logic: Parse `stream_uri` extra from intent and commit asynchronously (using `apply()`) to private `SharedPreferences` (`viewer_config`). Notify `MainActivity` if active to update playback URL.
- **Verification Criteria:**
  - `adb shell am broadcast -a com.sprillex.rtspviewer.ACTION_UPDATE_STREAM -n com.sprillex.rtspviewer/.ConfigReceiver --es stream_uri "rtsp://test"`

### Task 2.2: Configurator Companion Application Core [HARD]
- **Target Files:**
  - Create: `rtsp-config/build.gradle.kts`
  - Create: `rtsp-config/src/main/AndroidManifest.xml`
  - Create: `rtsp-config/src/main/java/com/sprillex/rtspconfig/MainActivity.kt`
- **Dependencies:**
  - Task 2.1
- **Core Technical Requirements:**
  - UI Design: Form inputs for target RTSP Host, Port (default 554), Path, Username, and Password.
  - Validation: Implement background TCP socket connection test targeting the host/port before persisting settings.
  - Synchronization: Construct complete RTSP URI and dispatch explicit broadcast targeting package `com.sprillex.rtspviewer` with action `com.sprillex.rtspviewer.ACTION_UPDATE_STREAM`. Ensure shared signing key across modules.
- **Verification Criteria:**
  - `./gradlew :rtsp-config:assembleRelease :rtsp-viewer:assembleRelease`

---

## Phase 3: Deep Linking & External Action Integration

### Task 3.1: Custom Scheme Intent Filter & Deep Link Routing [RESOLVED] [EASY]
- **Target Files:**
  - Modify: `rtsp-viewer/src/main/AndroidManifest.xml`
  - Modify: `rtsp-viewer/src/main/java/com/sprillex/rtspviewer/MainActivity.kt`
- **Dependencies:**
  - Task 1.2
- **Core Technical Requirements:**
  - Intent Filter: Configure `MainActivity` with `android:launchMode="singleTop"` and intent-filter for scheme `fastcam` and host `video`.
  - Routing Handler: Override `onNewIntent()` to extract dynamic parameters or stream overrides from deep link URIs without re-instantiating the activity or clearing video surface state.
- **Verification Criteria:**
  - `adb shell am start -a android.intent.action.VIEW -d "fastcam://video"`

---

## Phase 4: Zero-Latency Remote Failover & WireGuard Automation

### Task 4.1: Non-Blocking Network Probe & Asynchronous WireGuard Orchestration [HARD]
- **Target Files:**
  - Modify: `rtsp-viewer/src/main/AndroidManifest.xml`
  - Modify: `rtsp-viewer/src/main/java/com/sprillex/rtspviewer/MainActivity.kt`
- **Dependencies:**
  - Task 1.2, Task 3.1
- **Core Technical Requirements:**
  - Connectivity Probe: Use `ConnectivityManager.getNetworkCapabilities()` during `onCreate()` (<0.5ms non-blocking check).
  - Tunnel Automation: If network transport is non-Wi-Fi (cellular or external), broadcast intent `com.wireguard.android.action.SET_TUNNEL_UP` with extra `name = "HomeLab"`.
  - Parallel Player Init: Start RTSP player setup immediately in parallel without blocking on tunnel completion.
  - Failover Resilience: Attach `PlaybackException` listener to ExoPlayer with exponential backoff retry logic (150ms interval, maximum 4 attempts) to seamlessly connect once WireGuard tunnel completes handshake.
- **Verification Criteria:**
  - Simulate cellular interface via `adb shell cmd connectivity` and verify intent broadcast via `logcat -s WireGuard`

### Task 4.2: Resource Teardown, Codec Release & Tunnel State Restoration [EASY]
- **Target Files:**
  - Modify: `rtsp-viewer/src/main/java/com/sprillex/rtspviewer/MainActivity.kt`
- **Dependencies:**
  - Task 4.1
- **Core Technical Requirements:**
  - Lifecycle Cleanup: Override `onStop()` and `onDestroy()` to immediately stop player, detach surface, and release ExoPlayer hardware video decoders.
  - Tunnel Teardown: Track session state; if WireGuard tunnel was initiated by this viewer session, send broadcast `com.wireguard.android.action.SET_TUNNEL_DOWN` with extra `name = "HomeLab"`.
- **Verification Criteria:**
  - Run activity finish loop and monitor hardware decoder allocation via `adb shell dumpsys media.player`

---

## Phase 5: Profiling, Optimization & Final Release Packaging

### Task 5.1: Latency Profiling & R8 ProGuard Minification Rules [EASY]
- **Target Files:**
  - Create: `rtsp-viewer/proguard-rules.pro`
  - Create: `rtsp-config/proguard-rules.pro`
- **Dependencies:**
  - Task 1.1, Task 1.2, Task 2.2, Task 4.2
- **Core Technical Requirements:**
  - ProGuard Rules: Maintain keep-rules for Media3 ExoPlayer RTSP reflection classes while stripping all unreferenced UI dependencies and debug logging.
  - Performance Benchmark: Ensure cold start launch time to first rendered frame is under 500ms.
- **Verification Criteria:**
  - `adb shell am start-W -n com.sprillex.rtspviewer/.MainActivity`
