# Implementation Plan: Zero-Latency RTSP Viewer Ecosystem

This document outlines the atomic execution steps for implementing the remaining unstarted milestones of the Zero-Latency RTSP Viewer Ecosystem, specifically migrating the application from LibVLC to Media3 (ExoPlayer) and finalizing the WireGuard integration. Each task is sized for single-PR automated execution and strictly ordered by sequential dependency path.

### Task 1.1: Project Scaffolding, Build Configuration & Manifest Hardening [EASY]
- **Target Files:** `rtsp-viewer/build.gradle.kts`
- **Core Technical Requirements:** Remove the existing `org.videolan.android:libvlc-all` dependency. Add the required Media3 dependencies: `androidx.media3:media3-exoplayer:1.3.1`, `androidx.media3:media3-exoplayer-rtsp:1.3.1`, and `androidx.media3:media3-ui:1.3.1`. Ensure MinSDK is 26, TargetSDK is 34, and JVM target is 17.
- **Dependency Boundaries:** None.
- **Verification Criteria:** `./gradlew :rtsp-viewer:assembleDebug`

### Task 1.2: Raw Surface Layout & Media3 RTSP Player Pipeline [HARD]
- **Target Files:** `rtsp-viewer/src/main/res/layout/activity_main.xml`, `rtsp-viewer/src/main/java/com/sprillex/rtspviewer/MainActivity.kt`
- **Core Technical Requirements:**
  - Update `activity_main.xml` to replace `org.videolan.libvlc.util.VLCVideoLayout` with `androidx.media3.ui.PlayerView`, specifying `app:use_controller="false"` and `app:surface_type="surface_view"`.
  - In `MainActivity.kt`, eliminate all LibVLC objects and `MediaPlayer` usages. Instantiate `ExoPlayer` using a custom `DefaultLoadControl` tuned for zero latency (minBuffer=200ms, maxBuffer=500ms, startBuffer=100ms). Configure the RTSP media source using `RtspMediaSource.Factory().setForceUseRtpTcp(true)`.
- **Dependency Boundaries:** Depends on Task 1.1.
- **Verification Criteria:** `./gradlew :rtsp-viewer:connectedAndroidTest`

### Task 4.1: Non-Blocking Network Probe & Asynchronous WireGuard Orchestration [HARD]
- **Target Files:** `rtsp-viewer/src/main/java/com/sprillex/rtspviewer/MainActivity.kt`
- **Core Technical Requirements:** Enable the `checkNetworkAndStartTunnelIfNeeded()` call in `onCreate()`. Utilize `ConnectivityManager.getNetworkCapabilities()` to verify the active network. If the transport is non-Wi-Fi, dispatch `com.wireguard.android.action.SET_TUNNEL_UP` with extra `name="HomeLab"`. Implement an exponential backoff retry mechanism responding to `PlaybackException` from `ExoPlayer` (150ms interval, max 4 attempts) to ensure seamless fallback while the tunnel initializes.
- **Dependency Boundaries:** Depends on Task 1.2.
- **Verification Criteria:** `./gradlew :rtsp-viewer:assembleDebug`

### Task 4.2: Resource Teardown, Codec Release & Tunnel State Restoration [EASY]
- **Target Files:** `rtsp-viewer/src/main/java/com/sprillex/rtspviewer/MainActivity.kt`
- **Core Technical Requirements:** Adapt `onStop()` and `onDestroy()` lifecycle methods to properly tear down `ExoPlayer`, immediately release the surface to free hardware video decoders, and dispatch `SET_TUNNEL_DOWN` if the session originally initiated the WireGuard tunnel.
- **Dependency Boundaries:** Depends on Task 4.1.
- **Verification Criteria:** `./gradlew :rtsp-viewer:assembleDebug`

### Task 5.1: Latency Profiling & R8 ProGuard Minification Rules [EASY]
- **Target Files:** `rtsp-viewer/proguard-rules.pro`
- **Core Technical Requirements:** Replace `org.videolan.libvlc.**` keep-rules with appropriate retention rules for `androidx.media3` ExoPlayer RTSP internals. Strip unused UI controllers and styling metadata to optimize the cold-start launch benchmark (<500ms displayed time).
- **Dependency Boundaries:** Depends on Task 1.1.
- **Verification Criteria:** `./gradlew :rtsp-viewer:assembleRelease`
