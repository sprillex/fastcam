# FastCam Project Roadmap & Technical Debt Audit

## 1. Executive Summary & Product Vision

FastCam is a light, high-performance Android RTSP camera viewer suite designed for near-instantaneous camera feed startup, sub-second latency, and maximum reliability on embedded displays, Android TV (Leanback), and mobile devices.

This document details the complete technical debt inventory, security assessment, performance benchmarks, and phased strategic roadmap for the FastCam ecosystem (`rtsp-viewer` and `rtsp-config`).

---

## 2. Technical Debt & Code Quality Assessment

### Technical Debt Inventory

| Category | Issue Description | Impact | Remediation Status |
| :--- | :--- | :--- | :--- |
| **Security** | Plaintext camera credentials (`admin:pass`) logged to Logcat via `Log.d` | Credential leakage to system logs / debug tools | **Resolved (Phase 1)** |
| **Security** | Unsanitized intent extra and deep link URI ingestion into `SharedPreferences` | Potential injection of malformed or invalid URIs | **Resolved (Phase 1)** |
| **Security** | Plaintext credential exposure on status UI text views | Screen exposure of camera secrets | **Resolved (Phase 1)** |
| **Reliability**| Static 150ms tight-loop retry strategy failing on slow RTSP handshakes | Reconnection failure on transient network drops | **Resolved (Phase 1)** |
| **Reliability**| Buffering timeout reset loop without error propagation | Unresponsive infinite buffering state | **Resolved (Phase 1)** |
| **Reliability**| Commented-out network state checking logic | Missing dynamic network adaptation | **Resolved (Phase 1)** |
| **Testing**    | Missing unit test coverage across codebase | Lack of automated regression checks | **Resolved (Phase 1)** |
| **Architecture**| Direct `SharedPreferences` access in UI components | Tight coupling between storage and views | Planned (Phase 2) |
| **Architecture**| Monolithic `MainActivity` handling UI, player, and VPN intents | Harder maintainability and unit testing | Planned (Phase 2) |

---

## 3. Strategic Strategic Roadmap

```
+-------------------------------------------------------------------------------+
|                        FastCam Roadmap Execution                              |
+-------------------------------------------------------------------------------+
  [X] Phase 1: Security Hardening & Core Reliability (COMPLETED)
   ├── RTSP URI Validation & Credential Masking (`RtspUriUtils`)
   ├── Exponential Backoff Reconnection Logic
   ├── Dynamic Network Monitoring (`NetworkCallback`)
   └── Unit Test Suite Implementation (`RtspUriUtilsTest`)

  [ ] Phase 2: Architecture Modernization (PLANNED)
   ├── Media3 / ExoPlayer RTSP Extension Migration Evaluation
   ├── Coroutine-based Reactive Preferences & State Flow
   └── Dependency Injection (Hilt) Integration

  [ ] Phase 3: Advanced Features & Multi-Stream Capabilities (PLANNED)
   ├── Multi-Camera Stream Grid View
   ├── ONVIF Subnet Discovery & Auto-Configuration
   └── Android TV / Leanback Native Remote Controls & Daydream Saver Mode

  [ ] Phase 4: Telemetry, Metrics & Stream Analytics (PLANNED)
   ├── Real-time FPS, Bitrate & Resolution Overlay
   └── Stream Quality Diagnostic Metrics
```

---

## 4. Detailed Roadmap Phases

### Phase 1: Security Hardening, Reliability & Debt Clearance (Status: COMPLETED)
- [x] **RTSP URI Parser & Sanitizer (`RtspUriUtils`):** Centralized URI parsing, validation, building, and password masking.
- [x] **Credential Protection:** Mask passwords in Logcat logs and status text views across both `rtsp-viewer` and `rtsp-config`.
- [x] **Input Validation:** Enforce RTSP/RTSPS scheme and hostname validation before storing broadcast updates or deep links in `SharedPreferences`.
- [x] **Resilient Exponential Backoff:** Replaced static 150ms retry loop with progressive exponential backoff (500ms, 1s, 2s, 4s).
- [x] **Active Network Callback Listener:** Integrated `ConnectivityManager.NetworkCallback` for live network topology detection and automatic WireGuard VPN tunnel triggering.
- [x] **Unit Testing Infrastructure:** Added unit tests covering URI validation, parsing, building, and credential masking.

### Phase 2: Architecture Modernization (Status: PLANNED)
- [ ] **Reactive Repository Pattern:** Abstract `SharedPreferences` behind a clean `StreamRepository` interface with `StateFlow` updates.
- [ ] **Media3 / ExoPlayer RTSP Evaluation:** Benchmark ExoPlayer RTSP extension against LibVLC for reduced APK size and native Android media session integration.
- [ ] **Dependency Injection:** Introduce Hilt or Koin for player lifecycle and preference injection.

### Phase 3: Multi-Camera Grid & Smart Features (Status: PLANNED)
- [ ] **Multi-Camera Grid Layout:** Support 2x2 and 3x3 simultaneous stream playback grid.
- [ ] **ONVIF Subnet Discovery:** WS-Discovery ONVIF scanner in `rtsp-config` for auto-detecting IP camera streams on local network.
- [ ] **Android TV Integration:** Custom TV channel support, picture-in-picture (PiP) mode, and Leanback navigation overlay.

### Phase 4: Stream Telemetry & Diagnostic Analytics (Status: PLANNED)
- [ ] **On-Screen Telemetry Overlay:** Toggleable debug HUD displaying codec, FPS, bitrate, jitter, and dropped frames.
- [ ] **Enhanced Out-of-Process Crash Analytics:** Extended diagnostic telemetry attached to crash reports.
